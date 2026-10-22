-- ============================================================
-- 数据库深水区演练 · 最终数据清理（2026-10-21）
-- 原则：
--   1) 只删除本次演练产生的购物车行（user_id=4, drug_id=46）
--   2) 只删除 remark 明确标记为测试的订单及其明细
--   3) 不触碰 drug 表（保留 100,025 行压测数据）
--   4) 不删除 remark 为空的历史演示订单
-- ============================================================
USE ysd_take_out;

-- 清理前快照
SELECT COUNT(*) AS cart_rows FROM shopping_cart;
SELECT COUNT(*) AS orders_total FROM orders;
SELECT COUNT(*) AS order_detail_total FROM order_detail;
SELECT COUNT(*) AS orphan_details
FROM order_detail od LEFT JOIN orders o ON od.order_id = o.id
WHERE o.id IS NULL;
SELECT id, number, user_id, remark, order_time
FROM orders WHERE remark IN ('测试', 'regression-test', 'T09 rollback test')
ORDER BY id;
SELECT id, user_id, drug_id, number, amount
FROM shopping_cart WHERE user_id = 4 AND drug_id = 46;

START TRANSACTION;

-- T22/T09 预置的购物车测试行
DELETE FROM shopping_cart
WHERE user_id = 4 AND drug_id = 46;

-- 测试订单明细（先删子表）
DELETE od
FROM order_detail od
JOIN orders o ON od.order_id = o.id
WHERE o.remark IN ('测试', 'regression-test', 'T09 rollback test');

-- 测试订单主表
DELETE FROM orders
WHERE remark IN ('测试', 'regression-test', 'T09 rollback test');

-- 历史孤儿明细（父订单已不存在，如 order_id=5）
DELETE od
FROM order_detail od
LEFT JOIN orders o ON od.order_id = o.id
WHERE o.id IS NULL;

COMMIT;

-- 清理后核对
SELECT COUNT(*) AS cart_rows_after FROM shopping_cart;
SELECT COUNT(*) AS orders_total_after FROM orders;
SELECT COUNT(*) AS order_detail_total_after FROM order_detail;
SELECT COUNT(*) AS orphan_details_after
FROM order_detail od LEFT JOIN orders o ON od.order_id = o.id
WHERE o.id IS NULL;
SELECT COUNT(*) AS drug_rows_after FROM drug;
SELECT COUNT(*) AS dup_cart_after
FROM (
    SELECT user_id, drug_id
    FROM shopping_cart
    WHERE drug_id IS NOT NULL
    GROUP BY user_id, drug_id
    HAVING COUNT(*) > 1
) t;
