-- T08 死锁证据与现场检查
USE ysd_take_out;

-- 1) 死锁现场：找 LATEST DETECTED DEADLOCK 段
--    关注：TRANSACTION A/B 各持有什么锁、等待什么锁、最终回滚了哪个事务
SHOW ENGINE INNODB STATUS\G

-- 2) MySQL 8 锁等待视图（死锁已解除后可能为空，可在阻塞阶段查看）
SELECT * FROM performance_schema.data_lock_waits;
SELECT * FROM performance_schema.data_locks;

-- 3) 谁还在占着事务
SHOW FULL PROCESSLIST;

-- 4) 兜底解锁：找到长时间 Sleep 且 trx_state = RUNNING 的会话，
--    在对应客户端执行 ROLLBACK; 不要 KILL 业务连接
SELECT trx_id, trx_state, trx_started, trx_mysql_thread_id, trx_query
FROM information_schema.innodb_trx;
