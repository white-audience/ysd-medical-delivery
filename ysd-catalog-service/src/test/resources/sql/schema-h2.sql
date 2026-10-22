-- ============================================================================
-- 测试用 H2 建表脚本（MODE=MySQL）
--
-- ⚠ 重要说明：本脚本刻意「只建主键，不建任何二级索引」。
--   原因：被测仓库中不存在任何 .sql 文件，没有建表/建索引脚本，
--   因此无法证明生产库在 status / order_time / user_id 上有索引。
--   为了让 SqlExplainTest 能够客观暴露「缺少索引」这一事实，
--   测试 schema 严格按实体与 Mapper 推断字段，不额外补索引。
--   如果生产库实际上有索引，请在真实 MySQL 上复核（见 test-plan SQL-01/04/06）。
--
-- ⚠ H2 与 MySQL 差异：类型、自增、保留字处理均做了近似，不等价。
--   - H2 2.x 保留字 VALUE / USER 靠连接串 NON_KEYWORDS 解除
--   - H2 无 ENGINE / CHARSET / COLLATE 概念
--   - H2 的 DATETIME 精度与 MySQL 不完全一致
-- ============================================================================

DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS order_detail;
DROP TABLE IF EXISTS shopping_cart;
DROP TABLE IF EXISTS address_book;
DROP TABLE IF EXISTS drug_spec;
DROP TABLE IF EXISTS drug_combo_item;
DROP TABLE IF EXISTS drug;
DROP TABLE IF EXISTS drug_combo;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS employee;
DROP TABLE IF EXISTS user;

-- 员工表
CREATE TABLE employee (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(32)  NOT NULL,
    name        VARCHAR(32)  NOT NULL,
    password    VARCHAR(64)  NOT NULL,
    phone       VARCHAR(11),
    sex         VARCHAR(2),
    id_number   VARCHAR(18),
    status      INT          DEFAULT 1,
    create_time DATETIME,
    update_time DATETIME,
    create_user BIGINT,
    update_user BIGINT
);

-- 分类表
CREATE TABLE category (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    type        INT,
    name        VARCHAR(32),
    sort        INT,
    status      INT,
    create_time DATETIME,
    update_time DATETIME,
    create_user BIGINT,
    update_user BIGINT
);

-- 药品表
CREATE TABLE drug (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(32),
    category_id BIGINT,
    price       DECIMAL(10, 2),
    image       VARCHAR(255),
    description VARCHAR(255),
    status      INT,
    create_time DATETIME,
    update_time DATETIME,
    create_user BIGINT,
    update_user BIGINT
);

-- 药品规格表（value 为 H2 保留字，连接串已用 NON_KEYWORDS 解除）
CREATE TABLE drug_spec (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_id BIGINT,
    name    VARCHAR(32),
    value   VARCHAR(255)
);

-- 药品组合表
CREATE TABLE drug_combo (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT,
    name        VARCHAR(32),
    price       DECIMAL(10, 2),
    status      INT,
    description VARCHAR(255),
    image       VARCHAR(255),
    create_time DATETIME,
    update_time DATETIME,
    create_user BIGINT,
    update_user BIGINT
);

-- 药品组合明细表
CREATE TABLE drug_combo_item (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    combo_id BIGINT,
    drug_id  BIGINT,
    name     VARCHAR(32),
    price    DECIMAL(10, 2),
    quantity INT
);

-- 用户表（user 为 H2 保留字，连接串已用 NON_KEYWORDS 解除）
CREATE TABLE user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    openid      VARCHAR(45),
    name        VARCHAR(32),
    phone       VARCHAR(11),
    sex         VARCHAR(2),
    id_number   VARCHAR(18),
    avatar      VARCHAR(500),
    create_time DATETIME
);

-- 地址簿表
CREATE TABLE address_book (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT,
    consignee     VARCHAR(50),
    phone         VARCHAR(11),
    sex           VARCHAR(2),
    province_code VARCHAR(12),
    province_name VARCHAR(32),
    city_code     VARCHAR(12),
    city_name     VARCHAR(32),
    district_code VARCHAR(12),
    district_name VARCHAR(32),
    detail        VARCHAR(200),
    label         VARCHAR(100),
    is_default    INT
);

-- 订单表
CREATE TABLE orders (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    number                  VARCHAR(50),
    status                  INT,
    user_id                 BIGINT,
    address_book_id         BIGINT,
    order_time              DATETIME,
    checkout_time           DATETIME,
    pay_method              INT,
    pay_status              INT,
    amount                  DECIMAL(10, 2),
    remark                  VARCHAR(100),
    user_name               VARCHAR(32),
    phone                   VARCHAR(11),
    address                 VARCHAR(255),
    consignee               VARCHAR(32),
    cancel_reason           VARCHAR(255),
    rejection_reason        VARCHAR(255),
    cancel_time             DATETIME,
    estimated_delivery_time DATETIME,
    delivery_status         INT,
    delivery_time           DATETIME,
    pack_amount             INT,
    tableware_number        INT,
    tableware_status        INT
);

-- 订单明细表
CREATE TABLE order_detail (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(32),
    image    VARCHAR(255),
    order_id BIGINT,
    drug_id  BIGINT,
    combo_id BIGINT,
    spec     VARCHAR(50),
    number   INT,
    amount   DECIMAL(10, 2)
);

-- 购物车表
CREATE TABLE shopping_cart (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(32),
    image       VARCHAR(255),
    user_id     BIGINT,
    drug_id     BIGINT,
    combo_id    BIGINT,
    spec        VARCHAR(50),
    number      INT,
    amount      DECIMAL(10, 2),
    create_time DATETIME
);
