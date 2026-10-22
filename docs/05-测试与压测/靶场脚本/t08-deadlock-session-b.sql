-- T08 死锁复现 · 会话 B
-- 顺序：A 执行第 1 段 → B 执行第 1 段 → A 执行第 2 段（阻塞）→ B 执行第 2 段（死锁触发）
USE ysd_take_out;

-- ===== 第 1 段：锁住订单 7 =====
START TRANSACTION;
SELECT id, number, status FROM orders WHERE id = 7 FOR UPDATE;

-- ===== 第 2 段：A 请求 7 之后执行；InnoDB 检测到循环等待，回滚本会话 =====
-- 预期报错：ERROR 1213 (40001): Deadlock found when trying to get lock; try restarting transaction
SELECT id, number, status FROM orders WHERE id = 6 FOR UPDATE;

-- ===== 收尾：被选为牺牲者后事务已回滚；若未报错则手动 ROLLBACK =====
ROLLBACK;
