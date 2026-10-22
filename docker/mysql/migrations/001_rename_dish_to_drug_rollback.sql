-- ============================================================
-- 001_rename_dish_to_drug 的回滚脚本
-- 仅在迁移后没有新写入时直接反向重命名；否则请恢复备份并回退旧版本。
-- MySQL 8.0+；DDL 隐式提交，无法事务回滚。
-- 注意：若已执行 002 补索引脚本，回滚后这些索引会保留在旧表上，可另行清理。
-- ============================================================
USE ysd_take_out;
SET NAMES utf8mb4;

SELECT GET_LOCK('ysd_rename_drug_v1', 10) AS got_lock;

-- 1. 列名回退
ALTER TABLE drug_spec
    RENAME COLUMN drug_id TO dish_id;

ALTER TABLE drug_combo_item
    RENAME COLUMN combo_id TO setmeal_id,
    RENAME COLUMN drug_id  TO dish_id,
    RENAME COLUMN quantity TO copies;

ALTER TABLE order_detail
    RENAME COLUMN drug_id TO dish_id,
    RENAME COLUMN combo_id TO setmeal_id,
    RENAME COLUMN spec TO dish_flavor;

ALTER TABLE shopping_cart
    RENAME COLUMN drug_id TO dish_id,
    RENAME COLUMN combo_id TO setmeal_id,
    RENAME COLUMN spec TO dish_flavor;

-- 2. 索引名回退
ALTER TABLE drug
    RENAME INDEX idx_drug_name TO idx_dish_name;
ALTER TABLE drug_combo
    RENAME INDEX idx_drug_combo_name TO idx_setmeal_name;

-- 3. 表名回退
RENAME TABLE drug            TO dish,
             drug_spec       TO dish_flavor,
             drug_combo      TO setmeal,
             drug_combo_item TO setmeal_dish;

-- 4. 表注释回退
ALTER TABLE drug_combo      COMMENT='套餐';
ALTER TABLE drug_combo_item COMMENT='套餐药品关系';

-- 5. 迁移记录回退
DELETE FROM schema_migrations WHERE version = '001_rename_dish_to_drug';

SELECT RELEASE_LOCK('ysd_rename_drug_v1');
