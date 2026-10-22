-- T08 死锁复现 · 会话 A
-- 前置：两个独立的 MySQL 会话（两个客户端窗口 / 两个 mysql 连接）
-- 顺序：A 执行第 1 段 → B 执行第 1 段 → A 执行第 2 段（阻塞）→ B 执行第 2 段（死锁触发）
-- 说明：使用 SELECT ... FOR UPDATE，只加锁不改数据，最后 ROLLBACK 即可，不会污染业务数据。
USE ysd_take_out;

-- ===== 第 1 段：锁住订单 6 =====
START TRANSACTION;
SELECT id, number, status FROM orders WHERE id = 6 FOR UPDATE;

-- ===== 第 2 段：B 锁住 7 之后再执行；此处会阻塞 =====
SELECT id, number, status FROM orders WHERE id = 7 FOR UPDATE;

-- ===== 收尾：死锁发生后 A 会解除阻塞，执行 ROLLBACK 释放锁 =====
ROLLBACK;
