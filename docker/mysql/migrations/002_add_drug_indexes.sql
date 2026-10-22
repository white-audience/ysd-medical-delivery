-- ============================================================
-- 002：补齐药品域缺失索引
-- 背景：001 只重命名了既有唯一索引；当前库缺少 init 脚本中的
--       category_id / status / 关联列索引，分类查询与关联查询走全表扫描。
-- 可独立执行，不影响 001 主流程。
-- ============================================================
USE ysd_take_out;
SET NAMES utf8mb4;

SELECT GET_LOCK('ysd_add_drug_indexes_v1', 10) AS got_lock;

ALTER TABLE drug
    ADD INDEX idx_drug_category_id (category_id),
    ADD INDEX idx_drug_status (status);

ALTER TABLE drug_spec
    ADD INDEX idx_drug_spec_drug_id (drug_id);

ALTER TABLE drug_combo
    ADD INDEX idx_drug_combo_category_id (category_id);

ALTER TABLE drug_combo_item
    ADD INDEX idx_drug_combo_item_combo_id (combo_id);

INSERT INTO schema_migrations (version, description)
VALUES ('002_add_drug_indexes',
        'add idx_drug_category_id/idx_drug_status/idx_drug_spec_drug_id/idx_drug_combo_category_id/idx_drug_combo_item_combo_id');

SELECT RELEASE_LOCK('ysd_add_drug_indexes_v1');
