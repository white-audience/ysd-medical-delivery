-- ============================================================
-- 药速达 批次2：餐饮命名 -> 药品命名（实际执行版）
-- 执行前必须：停写、mysqldump 备份、确认无新旧混合状态
-- MySQL 8.0+；DDL 隐式提交，失败请用备份恢复
-- 说明：当前库只存在 idx_dish_name / idx_setmeal_name 两个非主键索引，
--       旧版本脚本中其余索引重命名会因索引不存在而失败，已删除。
-- ============================================================
USE ysd_take_out;
SET NAMES utf8mb4;

SELECT GET_LOCK('ysd_rename_drug_v1', 10) AS got_lock;

-- 0. 前置检查：记录行数（执行后对比）
SELECT 'dish' AS t, COUNT(*) AS c FROM dish
UNION ALL SELECT 'dish_flavor', COUNT(*) FROM dish_flavor
UNION ALL SELECT 'setmeal', COUNT(*) FROM setmeal
UNION ALL SELECT 'setmeal_dish', COUNT(*) FROM setmeal_dish
UNION ALL SELECT 'order_detail', COUNT(*) FROM order_detail
UNION ALL SELECT 'shopping_cart', COUNT(*) FROM shopping_cart;

-- 1. 混合状态检查：应只返回 4 张旧表，或只返回 4 张新表
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'ysd_take_out'
  AND table_name IN ('dish','drug','dish_flavor','drug_spec',
                     'setmeal','drug_combo','setmeal_dish','drug_combo_item');

-- 2. 迁移记录表
CREATE TABLE IF NOT EXISTS schema_migrations (
    version     VARCHAR(64) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    applied_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 重命名表（保留数据、索引、自增、注释）
RENAME TABLE dish            TO drug,
             dish_flavor     TO drug_spec,
             setmeal         TO drug_combo,
             setmeal_dish    TO drug_combo_item;

-- 4. 重命名现有唯一索引
ALTER TABLE drug
    RENAME INDEX idx_dish_name TO idx_drug_name;
ALTER TABLE drug_combo
    RENAME INDEX idx_setmeal_name TO idx_drug_combo_name;

-- 5. 重命名代码依赖的列
ALTER TABLE drug_spec
    RENAME COLUMN dish_id TO drug_id;

ALTER TABLE drug_combo_item
    RENAME COLUMN setmeal_id TO combo_id,
    RENAME COLUMN dish_id    TO drug_id,
    RENAME COLUMN copies     TO quantity;

ALTER TABLE order_detail
    RENAME COLUMN dish_id     TO drug_id,
    RENAME COLUMN setmeal_id  TO combo_id,
    RENAME COLUMN dish_flavor TO spec;

ALTER TABLE shopping_cart
    RENAME COLUMN dish_id     TO drug_id,
    RENAME COLUMN setmeal_id  TO combo_id,
    RENAME COLUMN dish_flavor TO spec;

-- 6. 表注释医药化
ALTER TABLE drug            COMMENT='药品';
ALTER TABLE drug_spec       COMMENT='药品属性关系表';
ALTER TABLE drug_combo      COMMENT='药品组合';
ALTER TABLE drug_combo_item COMMENT='药品组合明细';

-- 7. 记录迁移
INSERT INTO schema_migrations (version, description)
VALUES ('001_rename_dish_to_drug',
        'dish/setmeal/dish_flavor/setmeal_dish -> drug/drug_combo/drug_spec/drug_combo_item; rename drug_id/combo_id/spec/quantity');

SELECT RELEASE_LOCK('ysd_rename_drug_v1');
