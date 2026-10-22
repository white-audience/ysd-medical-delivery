-- T09 / T06 测试后清理（仅在确认测试数据是今晚造的之后执行）
USE ysd_take_out;

-- 删除为 T09 预置的购物车行（user 4 / drug 46）
DELETE FROM shopping_cart WHERE user_id = 4 AND drug_id = 46;

-- 如果 T09 误产生了订单（正常情况下回滚后不会），按 remark 定向删除
DELETE od FROM order_detail od
JOIN orders o ON od.order_id = o.id
WHERE o.user_id = 4 AND o.remark = 'T09 rollback test';

DELETE FROM orders WHERE user_id = 4 AND remark = 'T09 rollback test';

-- 核对：应回到 0 行 / 33 单 / 40 明细（以今晚记录为准）
SELECT COUNT(*) AS cart_user4 FROM shopping_cart WHERE user_id = 4;
SELECT COUNT(*) AS orders_user4 FROM orders WHERE user_id = 4;
