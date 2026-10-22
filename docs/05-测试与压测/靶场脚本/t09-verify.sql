-- T09 回滚核对：先记录压测前的基线，再在注入失败后执行本脚本对比
USE ysd_take_out;

-- ① 用户 4 的订单总数：注入失败后不应增加
SELECT COUNT(*) AS orders_user4 FROM orders WHERE user_id = 4;

-- ② 用户 4 的订单明细总数：不应增加
SELECT COUNT(*) AS order_detail_user4
FROM order_detail od
JOIN orders o ON od.order_id = o.id
WHERE o.user_id = 4;

-- ③ 用户 4 的购物车：应保持 1 行，且没有被清空
SELECT COUNT(*) AS cart_user4 FROM shopping_cart WHERE user_id = 4;
SELECT id, user_id, drug_id, name, number, amount FROM shopping_cart WHERE user_id = 4;

-- ④ 最近 5 条订单：确认没有 remark = 'T09 rollback test' 的残留
SELECT id, number, user_id, status, amount, remark, order_time
FROM orders
ORDER BY id DESC
LIMIT 5;
