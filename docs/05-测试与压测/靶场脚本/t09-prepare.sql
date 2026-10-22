-- T09 前置数据：user_id = 4 的购物车放 1 件真实药品（drug_id = 46）
-- 地址簿已存在：address_book.id = 2（user_id = 4）
-- 可重复执行：先清空 user 4 购物车再插入，保证只有 1 行
USE ysd_take_out;

DELETE FROM shopping_cart WHERE user_id = 4;

INSERT INTO shopping_cart (name, image, user_id, drug_id, combo_id, spec, number, amount, create_time)
SELECT name, image, 4, id, NULL, NULL, 1, price, NOW()
FROM drug
WHERE id = 46;

-- 核对：应返回 1 行，number = 1，amount = 6.00
SELECT id, user_id, drug_id, name, number, amount FROM shopping_cart WHERE user_id = 4;

-- 核对地址簿：应返回 id = 2
SELECT id, user_id, consignee, phone, detail FROM address_book WHERE user_id = 4;
