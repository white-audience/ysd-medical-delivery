-- ============================================================================
-- 测试基础数据（H2）
-- schema-h2.sql 每次都 DROP + CREATE，所以本脚本无需先 DELETE，天然可重复执行。
--
-- ⚠ 显式插入主键后，H2 的 identity 序列不会自动前移，可能导致后续自增冲突，
--   因此每个插入了显式主键的表后面都跟一句 ALTER ... RESTART WITH。
-- ============================================================================

-- 测试员工账号（仅 H2 测试库）
INSERT INTO employee (id, username, name, password, phone, sex, id_number, status, create_time, update_time)
VALUES (1, 'admin', '管理员', 'e10adc3949ba59abbe56e057f20f883e', '13800000000', '1',
        '110101199001010011', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
ALTER TABLE employee ALTER COLUMN id RESTART WITH 1000;

-- 用户
INSERT INTO user (id, openid, name, phone, sex, create_time)
VALUES (1, 'test-openid-0001', '测试用户', '13900000000', '1', CURRENT_TIMESTAMP);
ALTER TABLE user ALTER COLUMN id RESTART WITH 1000;

-- 地址簿（user_id = 1，供下单测试使用）
INSERT INTO address_book (id, user_id, consignee, phone, sex, province_code, province_name,
                          city_code, city_name, district_code, district_name, detail, label, is_default)
VALUES (1, 1, '测试收货人', '13900000000', '1', '110000', '北京市', '110100', '北京市',
        '110101', '东城区', '测试地址 1 号', '公司', 1);
ALTER TABLE address_book ALTER COLUMN id RESTART WITH 1000;

-- 分类
INSERT INTO category (id, type, name, sort, status, create_time, update_time)
VALUES (1, 1, '测试分类', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
ALTER TABLE category ALTER COLUMN id RESTART WITH 1000;

-- 药品
INSERT INTO drug (id, name, category_id, price, image, description, status, create_time, update_time)
VALUES (1, '测试药品A', 1, 12.00, 'a.jpg', '描述A', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       (2, '测试药品B', 1, 15.50, 'b.jpg', '描述B', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
ALTER TABLE drug ALTER COLUMN id RESTART WITH 1000;

-- 药品规格
INSERT INTO drug_spec (drug_id, name, value) VALUES (1, '规格', '0.25g');
INSERT INTO drug_spec (drug_id, name, value) VALUES (1, '规格', '0.5g');

-- 药品组合
INSERT INTO drug_combo (id, category_id, name, price, status, description, image, create_time, update_time)
VALUES (1, 1, '测试药品组合', 20.00, 1, '药品组合描述', 's.jpg', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
ALTER TABLE drug_combo ALTER COLUMN id RESTART WITH 1000;
