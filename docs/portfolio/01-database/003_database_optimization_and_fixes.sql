-- ============================================================
-- 003：数据库优化与并发修复（S1-S5 索引优化 + T22 并发兜底）
-- 依据：
--   docs/04-运维与故障排查/drug表索引优化建议.md（S1-S5 实测）
--   docs/05-测试与压测/数据库深水区排障实战记录.md（T22/T06/T08/T09）
-- 适用：已完成 001 / 002 的 MySQL 8.0+ 环境（库名 ysd_take_out）
--
-- 变更内容：
--   A. shopping_cart 增加唯一索引 uk_user_drug(user_id, drug_id)
--      T22 实测：50 并发加购因 check-then-act 产生 50 条重复行；
--      加唯一索引后重复数据无法落库，COUNT(*) = 1。
--      前置：先清理历史重复行（保留每组最小 id）。
--   B. drug 增加复合索引 idx_drug_cat_status_time(category_id, status, create_time)
--      S1 实测：index_merge + filesort，89.2 ms → 33.9 ms。
--   C. drug 删除冗余索引 idx_drug_category_id
--      其能力被 B 的最左前缀完全覆盖；保留会让优化器继续选 index_merge。
--   D. drug 将低基数索引 idx_drug_status 设为 INVISIBLE
--      status 基数=1；S2/S3 中会误导优化器扫 10 万行再回表。
--
-- 重要：
--   1) 当前开发库已于 2026-10-21 手工应用以上变更（见 schema_migrations）。
--      在新环境执行本脚本；已应用的环境不要重复执行 DDL。
--   2) DDL 隐式提交，不能事务回滚；执行前备份。
--   3) 00 段先查重复数据，确认后再执行唯一索引。
--   4) shopping_cart 的 (user_id, drug_id) 唯一索引对 drug_id IS NULL
--      （药品组合加购）不生效；组合并发如需保护，应另加
--      uk_user_combo(user_id, combo_id)，且需同步调整业务代码。
-- ============================================================
USE ysd_take_out;
SET NAMES utf8mb4;

SELECT GET_LOCK('ysd_db_optimization_003', 15) AS got_lock;

-- ------------------------------------------------------------
-- 0. 前置快照
-- ------------------------------------------------------------
SELECT COUNT(*) AS shopping_cart_rows FROM shopping_cart;
SELECT COUNT(*) AS drug_rows FROM drug;

-- 重复购物车行：有输出说明必须先走第 1 步去重
SELECT user_id, drug_id, COUNT(*) AS dup_count, GROUP_CONCAT(id ORDER BY id) AS ids
FROM shopping_cart
WHERE drug_id IS NOT NULL
GROUP BY user_id, drug_id
HAVING COUNT(*) > 1;

SELECT TABLE_NAME, INDEX_NAME,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_,
       MAX(NON_UNIQUE) AS non_unique,
       MIN(IS_VISIBLE) AS visible
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'ysd_take_out'
  AND TABLE_NAME IN ('drug', 'shopping_cart')
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- ------------------------------------------------------------
-- 1. shopping_cart 去重（保留每组最小 id）
--    注意：去重是为了让唯一索引可建立；被删除的行是并发竞态产生的脏数据。
-- ------------------------------------------------------------
DELETE sc1
FROM shopping_cart sc1
JOIN shopping_cart sc2
  ON sc1.user_id = sc2.user_id
 AND sc1.drug_id  = sc2.drug_id
 AND sc1.id > sc2.id
WHERE sc1.drug_id IS NOT NULL;

SELECT ROW_COUNT() AS dedup_deleted_rows;

-- ------------------------------------------------------------
-- 2. shopping_cart：唯一索引兜底并发加购
-- ------------------------------------------------------------
ALTER TABLE shopping_cart
    ADD UNIQUE KEY uk_user_drug (user_id, drug_id),
    ALGORITHM=INPLACE, LOCK=NONE;

-- ------------------------------------------------------------
-- 3. drug：复合索引（S1）
-- ------------------------------------------------------------
ALTER TABLE drug
    ADD INDEX idx_drug_cat_status_time (category_id, status, create_time),
    ALGORITHM=INPLACE, LOCK=NONE;

-- ------------------------------------------------------------
-- 4. drug：删除被复合索引最左前缀覆盖的冗余单列索引（S1）
-- ------------------------------------------------------------
ALTER TABLE drug
    DROP INDEX idx_drug_category_id,
    ALGORITHM=INPLACE, LOCK=NONE;

-- ------------------------------------------------------------
-- 5. drug：低基数索引设为 INVISIBLE（S2/S3/S5）
--    INVISIBLE 可逆；索引仍占空间、仍维护写入。
-- ------------------------------------------------------------
ALTER TABLE drug
    ALTER INDEX idx_drug_status INVISIBLE;

ANALYZE TABLE drug;
ANALYZE TABLE shopping_cart;

-- ------------------------------------------------------------
-- 6. 校验
-- ------------------------------------------------------------
-- 应看到：shopping_cart 有 uk_user_drug(user_id,drug_id)；
--         drug 有 idx_drug_cat_status_time；idx_drug_status 为 NO；
--         idx_drug_category_id 不存在。
SELECT TABLE_NAME, INDEX_NAME,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns_,
       MAX(NON_UNIQUE) AS non_unique,
       MIN(IS_VISIBLE) AS visible
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'ysd_take_out'
  AND TABLE_NAME IN ('drug', 'shopping_cart')
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

-- S1 执行计划：应使用 idx_drug_cat_status_time，且无 Using filesort
EXPLAIN SELECT *
FROM drug
WHERE category_id = 12
  AND status = 1
ORDER BY create_time DESC;

-- 并发重复检查：应返回 0 行
SELECT user_id, drug_id, COUNT(*) AS c
FROM shopping_cart
WHERE drug_id IS NOT NULL
GROUP BY user_id, drug_id
HAVING COUNT(*) > 1;

-- ------------------------------------------------------------
-- 7. 迁移登记
-- ------------------------------------------------------------
INSERT INTO schema_migrations (version, description)
VALUES ('003_database_optimization_and_fixes',
        'shopping_cart: dedup + unique uk_user_drug(user_id,drug_id); drug: add idx_drug_cat_status_time(category_id,status,create_time), drop idx_drug_category_id, idx_drug_status INVISIBLE');

SELECT RELEASE_LOCK('ysd_db_optimization_003');

-- ============================================================
-- 回滚（按相反顺序手工执行）：
-- ALTER TABLE shopping_cart DROP INDEX uk_user_drug;
-- ALTER TABLE drug ALTER INDEX idx_drug_status VISIBLE;
-- ALTER TABLE drug DROP INDEX idx_drug_cat_status_time, ALGORITHM=INPLACE, LOCK=NONE;
-- ALTER TABLE drug ADD INDEX idx_drug_category_id (category_id), ALGORITHM=INPLACE, LOCK=NONE;
-- ANALYZE TABLE drug;
-- DELETE FROM schema_migrations WHERE version = '003_database_optimization_and_fixes';
-- ============================================================
