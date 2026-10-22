-- ============================================================================
-- 药速达 ysd_take_out 初始化脚本
-- 仅在 MySQL 数据卷首次创建时由 /docker-entrypoint-initdb.d 自动执行。
-- 表结构与 ysd-pojo 实体 / Mapper 对齐，并包含近期新增的药品字段。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS ysd_take_out
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ysd_take_out;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- 员工表
CREATE TABLE IF NOT EXISTS employee (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(32)  NOT NULL,
    name        VARCHAR(32)  NOT NULL,
    password    VARCHAR(64)  NOT NULL,
    phone       VARCHAR(11)  DEFAULT NULL,
    sex         VARCHAR(2)   DEFAULT NULL,
    id_number   VARCHAR(18)  DEFAULT NULL,
    status      INT          DEFAULT 1,
    create_time DATETIME     DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    create_user BIGINT       DEFAULT NULL,
    update_user BIGINT       DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 分类表
CREATE TABLE IF NOT EXISTS category (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    type        INT         DEFAULT NULL COMMENT '1 药品分类 2 药品组合分类',
    name        VARCHAR(32) DEFAULT NULL,
    sort        INT         DEFAULT NULL,
    status      INT         DEFAULT NULL,
    create_time DATETIME    DEFAULT NULL,
    update_time DATETIME    DEFAULT NULL,
    create_user BIGINT      DEFAULT NULL,
    update_user BIGINT      DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_category_type (type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 药品表
CREATE TABLE IF NOT EXISTS drug (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    name              VARCHAR(32)   DEFAULT NULL,
    category_id       BIGINT        DEFAULT NULL,
    price             DECIMAL(10, 2) DEFAULT NULL,
    image             VARCHAR(255)  DEFAULT NULL,
    description       VARCHAR(255)  DEFAULT NULL,
    status            INT           DEFAULT NULL COMMENT '0 停售 1 在售',
    prescription_type INT           DEFAULT 0 COMMENT '0 OTC 1 处方药',
    manufacturer      VARCHAR(100)  DEFAULT NULL,
    approval_number   VARCHAR(64)   DEFAULT NULL,
    indications       VARCHAR(1000) DEFAULT NULL,
    contraindications VARCHAR(1000) DEFAULT NULL,
    create_time       DATETIME      DEFAULT NULL,
    update_time       DATETIME      DEFAULT NULL,
    create_user       BIGINT        DEFAULT NULL,
    update_user       BIGINT        DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_drug_category_id (category_id),
    KEY idx_drug_status (status),
    KEY idx_drug_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 药品规格表
CREATE TABLE IF NOT EXISTS drug_spec (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    drug_id BIGINT       DEFAULT NULL,
    name    VARCHAR(32)  DEFAULT NULL,
    value   VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_drug_spec_drug_id (drug_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 药品组合表
CREATE TABLE IF NOT EXISTS drug_combo (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    category_id BIGINT        DEFAULT NULL,
    name        VARCHAR(32)   DEFAULT NULL,
    price       DECIMAL(10, 2) DEFAULT NULL,
    status      INT           DEFAULT NULL,
    description VARCHAR(255)  DEFAULT NULL,
    image       VARCHAR(255)  DEFAULT NULL,
    create_time DATETIME      DEFAULT NULL,
    update_time DATETIME      DEFAULT NULL,
    create_user BIGINT        DEFAULT NULL,
    update_user BIGINT        DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_drug_combo_category_id (category_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 药品组合明细表
CREATE TABLE IF NOT EXISTS drug_combo_item (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    combo_id BIGINT        DEFAULT NULL,
    drug_id    BIGINT        DEFAULT NULL,
    name       VARCHAR(32)   DEFAULT NULL,
    price      DECIMAL(10, 2) DEFAULT NULL,
    quantity     INT           DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_drug_combo_item_combo_id (combo_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    openid      VARCHAR(45)  DEFAULT NULL,
    name        VARCHAR(32)  DEFAULT NULL,
    phone       VARCHAR(11)  DEFAULT NULL,
    sex         VARCHAR(2)   DEFAULT NULL,
    id_number   VARCHAR(18)  DEFAULT NULL,
    avatar      VARCHAR(500) DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_openid (openid)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 地址簿表
CREATE TABLE IF NOT EXISTS address_book (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       DEFAULT NULL,
    consignee     VARCHAR(50)  DEFAULT NULL,
    phone         VARCHAR(11)  DEFAULT NULL,
    sex           VARCHAR(2)   DEFAULT NULL,
    province_code VARCHAR(12)  DEFAULT NULL,
    province_name VARCHAR(32)  DEFAULT NULL,
    city_code     VARCHAR(12)  DEFAULT NULL,
    city_name     VARCHAR(32)  DEFAULT NULL,
    district_code VARCHAR(12)  DEFAULT NULL,
    district_name VARCHAR(32)  DEFAULT NULL,
    detail        VARCHAR(200) DEFAULT NULL,
    label         VARCHAR(100) DEFAULT NULL,
    is_default    INT          DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_address_book_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id                      BIGINT        NOT NULL AUTO_INCREMENT,
    number                  VARCHAR(50)   DEFAULT NULL,
    status                  INT           DEFAULT NULL,
    user_id                 BIGINT        DEFAULT NULL,
    address_book_id         BIGINT        DEFAULT NULL,
    order_time              DATETIME      DEFAULT NULL,
    checkout_time           DATETIME      DEFAULT NULL,
    pay_method              INT           DEFAULT NULL,
    pay_status              INT           DEFAULT NULL,
    amount                  DECIMAL(10, 2) DEFAULT NULL,
    remark                  VARCHAR(100)  DEFAULT NULL,
    user_name               VARCHAR(32)   DEFAULT NULL,
    phone                   VARCHAR(11)   DEFAULT NULL,
    address                 VARCHAR(255)  DEFAULT NULL,
    consignee               VARCHAR(32)   DEFAULT NULL,
    cancel_reason           VARCHAR(255)  DEFAULT NULL,
    rejection_reason        VARCHAR(255)  DEFAULT NULL,
    cancel_time             DATETIME      DEFAULT NULL,
    estimated_delivery_time DATETIME      DEFAULT NULL,
    delivery_status         INT           DEFAULT NULL,
    delivery_time           DATETIME      DEFAULT NULL,
    pack_amount             INT           DEFAULT NULL,
    tableware_number        INT           DEFAULT NULL,
    tableware_status        INT           DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_status (status),
    KEY idx_orders_order_time (order_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 订单明细表
CREATE TABLE IF NOT EXISTS order_detail (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)   DEFAULT NULL,
    image       VARCHAR(255)  DEFAULT NULL,
    order_id    BIGINT        DEFAULT NULL,
    drug_id     BIGINT        DEFAULT NULL,
    combo_id    BIGINT        DEFAULT NULL,
    spec        VARCHAR(50)   DEFAULT NULL,
    number      INT           DEFAULT NULL,
    amount      DECIMAL(10, 2) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_order_detail_order_id (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------- 购物车表
CREATE TABLE IF NOT EXISTS shopping_cart (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)   DEFAULT NULL,
    image       VARCHAR(255)  DEFAULT NULL,
    user_id     BIGINT        DEFAULT NULL,
    drug_id     BIGINT        DEFAULT NULL,
    combo_id    BIGINT        DEFAULT NULL,
    spec        VARCHAR(50)   DEFAULT NULL,
    number      INT           DEFAULT NULL,
    amount      DECIMAL(10, 2) DEFAULT NULL,
    create_time DATETIME      DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_shopping_cart_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
-- 演示数据
-- ============================================================================

-- 管理端初始账号（仅本地演示；首次部署后必须修改密码）
INSERT IGNORE INTO employee
    (id, username, name, password, phone, sex, id_number, status, create_time, update_time, create_user, update_user)
VALUES
    (1, 'admin', '管理员', 'e10adc3949ba59abbe56e057f20f883e', '13800000000', '1',
     '110101199001011234', 1, NOW(), NOW(), 1, 1);

INSERT IGNORE INTO category
    (id, type, name, sort, status, create_time, update_time, create_user, update_user)
VALUES
    (11, 1, '感冒发热', 1, 1, NOW(), NOW(), 1, 1),
    (12, 1, '止痛消炎', 2, 1, NOW(), NOW(), 1, 1),
    (13, 1, '肠胃用药', 3, 1, NOW(), NOW(), 1, 1),
    (14, 1, '慢病用药', 4, 1, NOW(), NOW(), 1, 1),
    (21, 2, '家庭常备药箱', 1, 1, NOW(), NOW(), 1, 1);

INSERT IGNORE INTO drug
    (id, name, category_id, price, image, description, status,
     prescription_type, manufacturer, approval_number, indications, contraindications,
     create_time, update_time, create_user, update_user)
VALUES
    (51, '阿莫西林胶囊', 12, 18.00, NULL, '青霉素类抗生素', 1,
     1, '石药集团中诺药业(石家庄)有限公司', '国药准字H13023964',
     '适用于敏感菌所致的上呼吸道感染（中耳炎、鼻窦炎、咽炎、扁桃体炎）、泌尿生殖道感染、皮肤软组织感染及急性支气管炎、肺炎等下呼吸道感染。',
     '青霉素过敏及青霉素皮肤试验阳性患者禁用。',
     NOW(), NOW(), 1, 1),
    (52, '头孢克肟片', 12, 26.00, NULL, '第三代口服头孢菌素', 1,
     1, '成都倍特药业股份有限公司', '国药准字H20040588',
     '适用于对头孢克肟敏感的链球菌属、肺炎球菌、淋球菌、大肠杆菌、克雷伯杆菌属、变形杆菌属及流感杆菌等引起的支气管炎、肺炎、肾盂肾炎、膀胱炎、胆囊炎、猩红热、中耳炎等感染。',
     '对本品及其他头孢菌素类抗生素过敏者禁用。',
     NOW(), NOW(), 1, 1),
    (53, '布洛芬缓释胶囊', 12, 24.00, NULL, '非甾体抗炎药，解热镇痛', 1,
     0, '中美天津史克制药有限公司', '国药准字H10900089',
     '用于缓解轻至中度疼痛，如头痛、关节痛、偏头痛、牙痛、肌肉痛、神经痛、痛经；也用于普通感冒或流行性感冒引起的发热。',
     '对其他非甾体抗炎药过敏者禁用；孕妇及哺乳期妇女禁用；对阿司匹林过敏的哮喘患者禁用；活动性消化道溃疡/出血患者禁用。',
     NOW(), NOW(), 1, 1),
    (54, '感冒灵颗粒', 11, 15.80, NULL, '中成药复方感冒制剂', 1,
     0, '华润三九医药股份有限公司', '国药准字Z44021940',
     '解热镇痛。用于感冒引起的头痛、发热、鼻塞、流涕、咽痛。',
     '严重肝肾功能不全者禁用；对本品过敏者禁用。',
     NOW(), NOW(), 1, 1);

INSERT IGNORE INTO drug_combo
    (id, category_id, name, price, status, description, image,
     create_time, update_time, create_user, update_user)
VALUES
    (31, 21, '家庭常备药箱', 88.00, 1, '感冒、止痛、消炎三类常备用药组合', NULL,
     NOW(), NOW(), 1, 1);

INSERT IGNORE INTO drug_combo_item (id, combo_id, drug_id, name, price, quantity)
VALUES
    (1, 31, 53, '布洛芬缓释胶囊', 24.00, 1),
    (2, 31, 54, '感冒灵颗粒', 15.80, 1),
    (3, 31, 51, '阿莫西林胶囊', 18.00, 1);
