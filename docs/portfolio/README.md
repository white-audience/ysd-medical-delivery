# 作品集证据索引（Portfolio）

> 本目录把全项目**最核心、可复现的排障与验证证据**集中归档，作为简历附件 / 面试展示材料。
> 所有文件均来自 2026-10-20 ~ 10-21 的本地真实运行，不是事后补写。
> 完整过程与上下文见对应正式文档，本目录只保留“结论 + 原始输出”。

---

## 一、快速索引

| 主题 | 核心结论 | 证据入口 | 对应文档 |
|---|---|---|---|
| 数据库连接池 | 50 并发下 Druid 连接池成为瓶颈，等待线程与慢 SQL 可定位 | [T06-druid-datasource](01-database/T06-druid-datasource-20261020.json)、[Druid 监控截图](01-database/T06-druid-login-20261020.png) | [T06-T09 靶场手册](01-database/T06-T09-靶场操作手册-20261020.md) |
| 锁等待 / 死锁 | 双会话复现锁等待，`INNODB STATUS` 定位等待链 | [T08-innodb-status](01-database/T08-innodb-status-20261020.txt)、[T09 验证](01-database/T09-verify-20261020.txt) | [数据库深水区排障](01-database/数据库深水区排障实战记录.md) |
| 并发重复加购 | 50 并发产生 50 条重复行 → 唯一索引兜底后 `COUNT(*)=1` | [T22 修复前](01-database/T22-before.txt)、[T22 修复后](01-database/T22-after.txt)、[唯一索引回归](01-database/T22-unique-index-regression-20261021.txt)、[JMeter 计划](01-database/t22-cart-race.jmx) | [数据库深水区排障 · 战役二](01-database/数据库深水区排障实战记录.md) |
| 索引优化 | 10 万药品数据下补充复合索引，慢查询走索引 | [003 索引验证](01-database/003-index-verification-20261021.txt)、[优化 SQL](01-database/003_database_optimization_and_fixes.sql)、[索引建议](01-database/drug表索引优化建议.md) | [drug 表索引优化建议](01-database/drug表索引优化建议.md) |
| 水平越权（IDOR） | User B 传 User A 的 `addressBookId` 可下单 / 读收货信息；修复后返回无权限并回滚 | [T26 修复前](02-security/T26-before.txt)、[T26 修复后](02-security/T26-after.txt)、[完整记录](02-security/T26-越权测试记录.md) | [T26 越权测试记录](02-security/T26-越权测试记录.md) |
| AI 失败路径 | 空输入不耗 Token；工具/RAG 失败不返回 500；处方审核 fail-closed | [T31 记录](03-ai/T31-AI失败路径测试记录.md)、[E2E 原始结果](03-ai/T31-day4-e2e-results.json) | [T31](03-ai/T31-AI失败路径测试记录.md) |
| AI 网关与流式 | 直连 8088 返回 401；SSE 首字节耗时与增量输出可测 | [401 截图](03-ai/AI-day2-01-401-20261021.png)、[SSE 流式](03-ai/AI-day2-04-sse-stream-20261021.png)、[SSE 耗时](03-ai/AI-day2-05-sse-timing-20261021.txt)、[多轮记忆](03-ai/AI-day2-06-multiturn-20261021.png) | [AI 接入网关与 SSE 改造](../02-架构设计与开发/AI接入网关与SSE改造记录.md) |
| RAG 评测 | 20 题、15 道可回答题 Hit Rate@4 = 100%，回答准确率 95% | [评测汇总](04-rag-eval/README.md)、[完整结果](04-rag-eval/results-topk4-thr0.5.json) | [AI-RAG 检索设计与评测](../02-架构设计与开发/AI-RAG检索设计与评测.md) |

---

## 二、01-database：数据库深水区

### T06 · Druid 连接池

- 场景：50 并发加购 / 下单，观察连接池活跃数、等待线程、慢 SQL。
- 结论：连接池上限与等待队列是首要瓶颈；证据包含 Druid 监控接口原始 JSON 与登录页截图。
- 文件：`T06-druid-datasource-20261020.json`、`T06-druid-login-20261020.png`

### T08 / T09 · 锁等待与死锁

- 场景：两个 MySQL 会话按相反顺序更新同一批行，复现锁等待与死锁。
- 结论：`SHOW ENGINE INNODB STATUS` 可直接定位等待事务与持有锁；排查 SQL 已沉淀。
- 文件：`T08-innodb-status-20261020.txt`、`T09-baseline-20261020.txt`、`T09-verify-20261020.txt`

### T22 · 并发重复加购与唯一索引

- 复现：50 线程 + `Synchronizing Timer` 同时加购 `user_id=4, drug_id=46`，产生 50 条重复行。
- 修复：新增 `uk_user_drug (user_id, drug_id)`，不改业务代码先让数据库兜底。
- 回归：同样 50 并发后 `COUNT(*) = 1`；实测残留 `number=2`，说明后续请求走 `UPDATE number+1`。
- 复测：2026-10-21 再次验证索引存在且重复插入报 `ERROR 1062`。
- 文件：`T22-before.txt`、`T22-after.txt`、`T22-unique-index-regression-20261021.txt`、`t22-cart-race.jmx`

### 索引优化与收尾清理

- 10 万条 `drug` 数据保留为压测基线；补充查询复合索引并验证执行计划。
- 收尾清理：测试订单、测试购物车、测试账号已删除，孤儿明细 0，重复购物车 0。
- 文件：`003-index-verification-20261021.txt`、`003_database_optimization_and_fixes.sql`、
  `drug表索引优化建议.md`、`final-cleanup-20261021.txt`

---

## 三、02-security：水平越权（IDOR）

- 漏洞：下单接口直接信任客户端传入的 `addressBookId`，未校验地址归属。
- 复现：User B 传 User A 的 `addressBookId=2`，成功生成订单并拿到 A 的收货人 / 手机号。
- 修复：`AddressBookMapper` 查询增加 `user_id = 当前登录用户` 条件；`/inner/**` 由网关统一 403。
- 回归：越权请求返回 `地址不存在或无权限`，订单数不变、购物车保留；本人地址下单不受影响。
- 文件：`T26-before.txt`、`T26-after.txt`、`T26-越权测试记录.md`
- 说明：测试期间产生的订单 `id=40/41` 已在项目收尾时删除，原始响应仍保留在证据文件中。

---

## 四、03-ai：AI 失败路径与链路

### T31 · 失败路径矩阵

| 失败点 | 预期 | 结果 |
|---|---|---|
| 空问题 / 超长问题 | 400，不进模型 | 通过 |
| catalog 异常 | 工具兜底，不抛 500 | 通过 |
| RAG 空召回 | 直接返回“未检索到规则” | 通过 |
| 跨用户同 conversationId | 记忆隔离 | 通过 |
| 处方审核拒绝 / 超时 / 宕机 | fail-closed，不写订单 | 通过 |

- 单测：AI 14/14、订单 6/6；真实链路 E2E 5/5。
- 文件：`T31-AI失败路径测试记录.md`、`T31-day4-e2e-results.json`

### 网关与 SSE

- 直连 `:8088` 返回 401，必须经网关携带 JWT；网关剥离伪造身份头。
- SSE 流式接口逐块输出，`done` 事件携带 `conversationId`；截图与耗时日志见本目录。
- 多轮对话与跨用户隔离均有原始记录。

---

## 五、04-rag-eval：RAG 评测

- 评测集：20 题（15 道知识库可回答 + 5 道高风险边界题）。
- 环境：Redis Stack + Ollama `bge-m3` + DeepSeek `deepseek-chat`。
- 默认参数：`topK=4`、`similarityThreshold=0.5`。
- 结果：Hit Rate@4 = **15/15（100%）**；回答准确率 **95%（19/20）**。
- 调参：阈值 0.8 掉到 13/15，说明 0.5 的安全余量必要；`topK=5` 无增益且多 25% 上下文。
- 详见 [04-rag-eval/README.md](04-rag-eval/README.md)。

---

## 六、证据真实性与使用边界

1. 所有数字来自本地单实例环境，不是生产集群数据；简历中应表述为“本地压测 / 验证”。
2. T06/T09 的临时配置与故障注入代码已全部移除，仓库中不留“为测试而改坏”的代码。
3. 证据中的 JWT 为开发环境短期令牌（已过期），密钥为本地默认值；生产环境必须替换。
4. RAG 评测仅 20 题，样本量有限；结论只适用于当前知识库与 `bge-m3`，扩量后需重测。
5. 处方药审核是确定性规则（凭证是否上传），不含 OCR、签章验真与药师复核。
