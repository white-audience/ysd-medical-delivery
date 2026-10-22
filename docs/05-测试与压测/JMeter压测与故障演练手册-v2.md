# 药速达智慧配送平台 · JMeter 压测与故障演练手册（v2）

> **生成时间**：2026-10-13。基于仓库**实际源码、配置与数据库转储**编写，关键结论标注来源 `文件:行号`。
> **本文档只描述「如何测、预期什么现象、如何验证」，不含任何已执行的压测结果。** 未执行的测试不填数字。
> ⚠️ **归档说明（2026-10-21）**：原引用的数据库转储 `docker/mysql/init/01-schema.sql` 已随版本清理移除。文中对该转储的引用已统一改指当前建表脚本 `docker/mysql/init/01-schema.sql`（配套迁移见 `docker/mysql/migrations/`）；**原行号为旧转储定位，仅作历史参考**，以当前建表脚本为准。
> **v1 保留**：原 `docs/05-测试与压测/JMeter压测指南-v1.md`（v1）原样保留，便于对照；本文件为 v2。
> **分批交付**：本手册按 4 批生成，每批在文末「相对 v1 的变更说明」记录进度与未完成项。
>
> | 批次 | 范围 | 状态 |
> |---|---|---|
> | 第一批 | 第 0 章、第 1 章、第 1.5 章、第 3 章、第 9 章、附录 E/F | ✅ 已完成 |
> | 第二批 | 第 2 章、第 4 章 | ✅ 已完成 |
> | 第三批 | 第 5 章、第 6 章 | ✅ 已完成 |
> | 第四批 | 第 7、8、10、11 章、附录 A~D、G | ✅ 已完成 |
>
> 相关文档：`docs/09-项目记录/遗留事项总表.md`、`docs/09-项目记录/微服务拆分记录/阶段5-4-缓存改造-实施记录.md`、`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel改造-准备.md`、`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md`、`docs/09-项目记录/微服务拆分记录/阶段5-2-MQ改造-总结.md`、`docs/05-测试与压测/测试清单.md`。

---

## 0. 三条重要前提 + 测试地图 + 测试索引

### 0.1 三条重要前提（先读）

1. **接口路径 / 参数 / header 必须标注源码来源（`文件:行号`）**。凡仓库内查不到的（Nacos 规则、生产库真实索引、真实压测数值），一律标 `<!-- TODO: 待确认 -->`，不写死、不猜测。
2. **不编造任何已执行的压测结果**。本文档只描述「如何测、预期什么现象、如何验证」。**没有真实测量或配置依据时，不得把示例值写成标准、结论或生产建议。**
3. **Sentinel 阈值、网关限流规则、真实库结构与索引一律指向「读取步骤」**，不写具体数值。凡示例值均注明「示例 / 以实际为准」。

### 0.2 三条硬性约束

- **不使用 `View Results Tree` 做高并发压测**。调试阶段（单线程、低并发）可用，正式压测前必须删掉 / 禁用，否则施压机自身先 OOM，结果失真。
- **Redis 业务库是 db10**，所有清理命令必须带 `-n 10`，**禁止 `FLUSHDB` / `FLUSHALL`**，只按经核实的目标前缀清理（安全规则见 §1.4 与第 4 章）。
- **不引导生产环境做破坏性实验**。本地 / 独立测试 / 生产三环境分层（§1.1），生产只允许观测。凡修改代码验证的，必须显式写「**验证后务必还原**」。

### 0.3 四类测试的区别（全文叙事骨架）

| 类型 | 回答的问题 | 典型手段 |
|---|---|---|
| **性能测试** | 系统能处理多少请求、响应多快？ | 基准 / 阶梯 / 突发 / 长稳 |
| **正确性测试** | 并发下订单、库存、缓存、DB 数据对不对？ | 并发施压 + 数据核对 |
| **故障测试** | 依赖失效时系统会不会级联故障？ | 故障注入（停服务 / 延迟 / 断 Redis） |
| **恢复测试** | 故障排除后能否自动恢复、积压能否处理完？ | 恢复观察 + 数据一致性核对 |

> 「接口 200」不等于「业务正确」；「错误率 0%」不等于「数据一致」。每条结论都必须能落到**具体证据**。

### 0.4 测试地图（五类 × v1 覆盖情况）

| # | 类别 | 覆盖内容 | v1 状态 |
|---|---|---|---|
| 1 | 性能与容量 | 基准、阶梯、突发、长稳、资源耗尽、性能退化 | v1 部分覆盖 |
| 2 | 数据库与缓存 | 连接池耗尽、慢 SQL、锁等待、缓存雪崩、并发写、一致性 | 需补充 |
| 3 | 微服务与 MQ | 实例异常、Feign 超时、MQ 堆积、重复消费、消息丢失、服务恢复 | 需补充 |
| 4 | 业务正确性与安全 | 超卖、重复下单、越权、幂等、事务回滚、异常数据 | 需补充 |
| 5 | 可观测性与恢复 | 日志、指标、Trace、JVM、重启、配置中心异常、数据恢复 | 需补充 |

### 0.5 测试索引 T01 ~ T30（主编号 · 别名 · 类别 · 一句话）

> 主编号 `T01~T30` 唯一稳定；`别名`保留原编号（性能组的 `A~E`、扩展建议的 `6~30`），便于与 v1 / 《扩展建议》对照。
> 类别标识：**PERF** 性能容量 / **DB** 数据库 / **REDIS** 缓存 / **MQ** 消息队列 / **SC** 微服务 / **BIZ** 业务正确性 / **JVM** 运行环境。

| 主编号 | 别名 | 类别 | 一句话说明 |
|---|---|---|---|
| T01 | A | PERF | 基准测试：4 个代表接口，低 / 中 / 稳定并发 + 缓存命中 / 未命中分开测 |
| T02 | B | PERF | 阶梯加压：5/10/20/40/60/80 找**性能拐点**而非最大 TPS |
| T03 | C | PERF | 突发流量：低负载→爆发→回落，重点看**恢复期** |
| T04 | D | PERF | 长时间稳定性：30~60 分钟起步，看 GC / 线程 / 连接 / MQ 差 |
| T05 | E | PERF | JMeter 自身瓶颈：非 GUI、监控施压机 |
| T06 | 测试6 | DB | 数据库连接池耗尽（本项目为 **Druid**） |
| T07 | 测试7 | DB | 慢 SQL 与索引失效（10 万+ 数据、EXPLAIN、N+1、深分页） |
| T08 | 测试8 | DB | 事务锁等待与死锁（两会话示意 + INNODB STATUS） |
| T09 | 测试9 | DB | 事务回滚与部分失败（下单四步人为让第 3 步失败） |
| T10 | 测试10 | REDIS | 缓存击穿与热点 key 失效（先核实 `getWithLock` 真实实现） |
| T11 | 测试11 | REDIS | 缓存雪崩与 TTL 集中到期（验证 TTL 抖动有效性） |
| T12 | 测试12 | REDIS | Redis 不可用与故障放大链（停服 / 超时 / 恢复后空缓存） |
| T13 | 测试13 | REDIS | 延迟双删在多实例下是否仍正确（本地锁失效） |
| T14 | 测试14 | MQ | 消费者停止与消息堆积（Ready/Unacked/生产速率/消费速率） |
| T15 | 测试15 | MQ | 重复消费与幂等（处理成功后、确认前杀消费者） |
| T16 | 测试16 | MQ | 发送成功但业务未提交（普通事务 + 发消息不天然原子） |
| T17 | 测试17 | MQ | 顺序、死信、毒消息（区分生产确认 / 持久化 / ACK / 重试 / DLQ） |
| T18 | 测试18 | SC | 下游变慢而非挂掉（注入延迟 → 线程堆积拖垮健康接口） |
| T19 | 测试19 | SC | 实例退出与重启（503 不一定是 Sentinel） |
| T20 | 测试20 | SC | Nacos 不可用（区分服务发现 vs 配置中心） |
| T21 | 测试21 | SC | 限流 + 熔断 + 重试叠加（验证重试放大流量） |
| T22 | 测试22 | BIZ | 并发扣库存 / 超卖（**本项目无库存字段，见 §7 说明**） |
| T23 | 测试23 | BIZ | 重复提交与幂等（前端禁用按钮 ≠ 后端幂等） |
| T24 | 测试24 | BIZ | 事务边界与并发更新（需程序化并发控制时序） |
| T25 | 测试25 | BIZ | 参数异常与边界值 |
| T26 | 测试26 | BIZ | 鉴权边界（鉴权成功 ≠ 权限正确） |
| T27 | 测试27 | BIZ | 异常输入资源消耗（设输入上限） |
| T28 | 测试28 | JVM | 内存与 GC（不凭一次高内存判泄漏） |
| T29 | 测试29 | JVM | 线程池耗尽（作为独立故障场景） |
| T30 | 测试30 | JVM | 磁盘与日志（不要真写满磁盘） |

### 0.6 v1 原有测试保留对照

> 编号以【骨架】为准（T06~T30 为扩展建议的 25 个测试，T01~T05 为性能 5 项），因此 v1 的 5 个「计划」**不另占 T 编号**，而是作为对应章节内的**保留小节**存在，`别名`继续使用「计划N」。

| v1 原名 | 类别 | v2 中的位置 | 与 T 编号的关系 |
|---|---|---|---|
| 计划1 缓存命中率验证 | REDIS | §4.1（保留小节） | 与 T01 基准「命中 / 未命中分开测」共用观测口径 |
| 计划2 缓存穿透验证 | REDIS | §4.2（保留小节） | T10 缓存击穿是它的「反面」（存在但失效 vs 不存在） |
| 计划3 延迟双删验证 | REDIS | §4.3（保留小节） | T13 为其**多实例扩展** |
| 计划4 Sentinel 降级验证 | SC | §6.x（保留小节） | T18「下游变慢」是其扩展（挂掉 vs 变慢） |
| 计划4.1 网关 QPS 限流 | SC | §6.x（保留小节） | T21「限流+熔断+重试」引用其规则 |

---

## 1. 通用前提

### 1.1 环境分层（**所有故障注入只在独立测试环境执行**）

| 环境 | 可以做什么 | 禁止做什么 |
|---|---|---|
| **本地环境** | 快速复现、单元 / 并发测试、事务与边界实验、改代码验证（**验证后还原**）、单实例压测 | 把本地结果当成容量结论 |
| **独立测试环境** | JMeter 压测、故障注入、服务重启、MQ 积压、批量造数、连接池调低复现 | 影响他人共享环境；不设停止条件盲目加压 |
| **生产环境** | **仅观测与诊断**：看日志、Trace、连接池 / DB / Redis / MQ 指标 | **停服务、删缓存、锁表、批量删除、故障注入、压力冲击**一律禁止 |

> 本文档中所有「kill 服务 / 停 Redis / 停消费者 / 注入延迟 / 批量造数」步骤，默认都指**独立测试环境**。生产环境只保留「观测步骤」。

### 1.2 工具版本

| 项 | 要求 | 说明 |
|---|---|---|
| JMeter | 5.5 及以上 | 需要 `JSON Assertion`、`JSON Extractor` |
| JDK | 17+（工程实际 Java 21 / Spring Boot 3.5.16） | `java -version` 先确认 |
| JMeter Plugins | 可选 | `Response Times Over Time` / `Percentiles`（jmeter-plugins，Plugins Manager 安装） |
| MySQL 客户端 | 8.x | 用于 EXPLAIN / PROCESSLIST / INNODB STATUS |
| redis-cli | 6.x+ | 用于 SCAN / INFO / TTL（**务必带 `-n 10`**） |

### 1.3 目标服务与端口

| 服务 | 端口 | 压测相关职责 | 源码 |
|---|---|---|---|
| ysd-gateway | 9000 | **统一入口**：鉴权 + 路由 + 网关限流 | `ysd-gateway/src/main/resources/application.yaml:2` |
| ysd-user-service | 8081 | 用户 / 地址簿 | `ysd-user-service/src/main/resources/application.yml:3` |
| ysd-admin-service | 8082 | 员工 / 店铺状态 | `ysd-admin-service/src/main/resources/application.yml:3` |
| ysd-report-service | 8083 | 报表 / 工作台 | `ysd-report-service/src/main/resources/application.yml` |
| ysd-catalog-service | 8084 | 分类 / 药品 / 药品组合（**缓存改造重点**） | `ysd-catalog-service/src/main/resources/application.yml:4` |
| ysd-order-service | 8085 | 订单 / 购物车（**Feign 熔断降级挂载点**） | `ysd-order-service/src/main/resources/application.yml:5` |
| ysd-push-service | 8086 | WebSocket 推送（**MQ 消费**） | `ysd-push-service/src/main/resources/application.yml:4` |

中间件端口：Nacos `8848` / MySQL `3306`（库 `ysd_take_out`）/ Redis `6379`（**业务缓存 db10**）/ RabbitMQ `5672` / Sentinel Dashboard `8718` / Zipkin `9411`。
> Redis db10 来源：`ysd-catalog-service/application.yml:23-27`、`ysd-order-service/application.yml:50-55`、`ysd-user-service/application.yml:21-25`、`ysd-admin-service/application.yml:22-26`。

> ⚠️ **数据源是 Druid，不是 HikariCP**。`spring.datasource.druid`（各服务 `application.yml`），依赖 `druid-spring-boot-3-starter`（`pom.xml:31,88-92`）。因此第 3 章的连接池指标是 Druid 的 `ActiveCount / PoolingCount / WaitThreadCount / MaxActive`，**不是** Hikari 的 Active/Idle/Pending。做连接池实验前先改正这个认知，否则会找错监控面板。

### 1.4 鉴权与 token（务必先做）

**header 规则**（来源 `ysd-gateway/src/main/java/com/ysd/filter/AuthGlobalFilter.java:39-83`）：

| 路径 | 是否要 token | header 名 |
|---|---|---|
| 白名单 `/admin/employee/login`、`/user/user/login`、`/user/shop/status`、`/notify/`、`/ws/`、`/doc.html`、`/webjars/` | 否 | — |
| `/admin/**` | 是 | `token` |
| 其余（含 `/user/**`） | 是 | `authentication` |

- 网关会**剥离**客户端自带的 `X-Identity-Id`，伪造身份头无效（`AuthGlobalFilter.java:52-56`）。
- `/inner/**` 一律 **403**，**压测不要打 `/inner`**（`AuthGlobalFilter.java:63-65`）——内部接口用 Feign 直连服务，不过网关。
- `/actuator/**` 被网关 `ActuatorBlockWebFilter` 拦截 **403**（`ysd-gateway/src/main/java/com/ysd/filter/ActuatorBlockWebFilter.java:17`）。

#### 1.4.1 获取管理端 token（本地可用，推荐）

```bash
curl -s -X POST http://localhost:9000/admin/employee/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
# 响应 data.token 即管理端 token
```
> 来源：`ysd-admin-service/src/main/java/com/ysd/controller/admin/EmployeeController.java:43-66`（响应字段 `token`）。默认账号来自种子数据 `ysd-catalog-service/src/test/resources/sql/data-h2.sql:10-12`（admin / <your-password>）。**真实库账号以实际为准**。

#### 1.4.2 获取用户端 token（手写 JWT，**不得把密钥贴到第三方网站**）

`POST /user/user/login` 走**微信真实 code**，本地拿不到，因此用户端 token 只能自签。**生成前必须先从源码核实以下 5 点**，再动手：

| 需核实项 | 结论（已据源码核实） | 来源 |
|---|---|---|
| 算法 | `HS256`（HMAC-SHA256） | `ysd-common/src/main/java/com/ysd/utils/JwtUtil.java:52` |
| 密钥编码 | **不直接用明文密钥**，而是 `SHA-256(secret)` 得到 32 字节做 HMAC key | `JwtUtil.java:25-33` |
| 用户 ID 字段 | claim 键名 `userId` | `ysd-common/src/main/java/com/ysd/constant/JwtClaimsConstant.java:6` |
| 过期时间字段 | 标准声明 `exp`（秒） | `JwtUtil.java:45-46`（生成用毫秒偏移 → 组装为 `exp`） |
| header 名 | `authentication`（用户端） | `AuthGlobalFilter.java:82` |

> **密钥来源**：用户端 / 管理端密钥均为明文 `<JWT_ADMIN_SECRET>`（`ysd-gateway/src/main/resources/application.yaml:73,76`）。这是**仓库内已存在的测试环境配置**，非生产密钥；即便如此，也**不要**把它粘到 jwt.io 等第三方网站（会被缓存 / 索引）。用下面的**本地脚本**生成。
>
> ⚠️ 若该密钥曾在真实环境使用，应按**凭据泄露风险**处理（轮换密钥）。用户端 token 只用**独立测试环境**的测试密钥。

**第 1 步：查库拿真实 user id**
```sql
SELECT id FROM user LIMIT 1;   -- 真实库转储 docker/mysql/init/01-schema.sql:359-369，实际值以现场为准
```

**第 2 步：本地脚本生成（纯标准库，无第三方依赖）**
```python
# save as gen_token.py; 用法: python gen_token.py <userId>
import hmac, hashlib, base64, json, time, sys
secret = "<JWT_ADMIN_SECRET>"                       # ysd-gateway/application.yaml:73,76 —— 仅测试环境
key = hashlib.sha256(secret.encode()).digest()   # 对齐 JwtUtil.hmacKey (JwtUtil.java:25-33)
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=")
uid = int(sys.argv[1]) if len(sys.argv) > 1 else 4
h = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
p = b64(json.dumps({"userId": uid, "exp": int(time.time()) + 7200}, separators=(",", ":")).encode())
signing = h + b"." + p
sig = b64(hmac.new(key, signing, hashlib.sha256).digest())
print((signing + b"." + sig).decode())
```
把输出放进 `authentication` 头。

> 自检：`401` 时先看网关日志 `请求缺少 token`（`AuthGlobalFilter.java:87`）/ `JWT 校验失败`（`:113`）。
> `<!-- TODO: 待确认 -->` 本项目是否对用户端 token 校验 `Bearer` 前缀——网关按 `authentication` 整串解析，未见前缀剥离逻辑（`AuthGlobalFilter.java:82,99`），故**不加** `Bearer`。

### 1.5 测试数据预置 SQL（**以真实库为准**）

> ⚠️ **仓库内无建表 / 建索引脚本**（`ysd-catalog-service/src/test/resources/sql/schema-h2.sql:4-9` 明确说明）；真实结构以现场库为准。以下字段来自**真实库转储** `docker/mysql/init/01-schema.sql`。

**真实库现状（转储时点，仅供参考，现场以查询为准）：**
```sql
SELECT COUNT(*) FROM category;   -- 转储 AUTO_INCREMENT=23
SELECT COUNT(*) FROM drug;       -- 转储 AUTO_INCREMENT=73（含药品 24 条）
SELECT COUNT(*) FROM orders;     -- 转储 AUTO_INCREMENT=38
SELECT id, type, name FROM category WHERE type = 1;   -- 药品分类：11,12,16,17,18,19,20,21
```
> ⚠️ **真实库里 `category_id = 1` 不存在**（药品分类是 11/12/16…，来源 `docker/mysql/init/01-schema.sql:82`）。v1 硬编码的 `categoryId=1` 只对 H2 种子数据成立。**压测前先 `SELECT id FROM category WHERE type=1 LIMIT 1;` 取真实 id**，再用它替换下面的 `{categoryId}`。

**预置 10 万条药品（挂在真实分类下；`drug.name` 有唯一索引，用编号保证唯一）：**
```sql
SET @cid = (SELECT id FROM category WHERE type = 1 LIMIT 1);   -- 真实药品分类 id
-- 生成 1..100000；用递归 CTE（MySQL 8）或应用层循环
INSERT INTO drug (name, category_id, price, image, description, status, create_time, update_time, create_user, update_user)
WITH RECURSIVE seq(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 100000)
SELECT CONCAT('压测药品', n), @cid, 9.99, 'test.jpg', '压测数据', 1, NOW(), NOW(), 1, 1 FROM seq;
```
> ⚠️ **字段以真实库为准**：真实 `drug` 表列见 `docker/mysql/init/01-schema.sql:93-107`。而当前工作区的 `DrugMapper.xml:7-12` 的 insert 额外引用了 `prescription_type / manufacturer / approval_number / indications / contraindications` ——**真实转储的 drug 表里没有这些列**（工作区正在改 `Drug.java / DrugDTO.java / DrugVO.java / DrugMapper.xml`，尚未提交）。`<!-- TODO: 待确认 -->` 现场执行前先 `DESC drug;` 核对，若列不存在，`POST /admin/drug`（新增药品）会报 `Unknown column`。

**清缓存（保证「从 miss 开始」观测）—— 安全规则见 §1.4、第 4 章：**
```bash
# 1) 先只读计数，确认前缀与数量（不要直接删！）
redis-cli -n 10 --scan --pattern 'ysd:catalog:drug:*' | wc -l
# 2) 数量在可接受范围内（示例上限 5000）、且确属测试库，再分批删
redis-cli -n 10 --scan --pattern 'ysd:catalog:drug:*' | head -n 1000 | xargs -r -n 100 redis-cli -n 10 del
# 3) 复核
redis-cli -n 10 --scan --pattern 'ysd:catalog:drug:*' | wc -l
```
> ⚠️ **禁止**：`FLUSHDB` / `FLUSHALL`；`redis-cli -n 10 eval "...KEYS..."`（KEYS 阻塞）。**必须**：显式 `-n 10`；先 `--scan` 计数确认；单次删除设上限；生产环境只观察不删。

### 1.6 监听器建议

| 用 | 用途 |
|---|---|
| **Aggregate Report** | TPS、平均 / P90 / P95 / P99、错误率 |
| **Response Times Over Time**（插件） | 时间序列曲线（限流 / 熔断拐点） |
| **Response Times Percentiles**（插件） | P95 / P99 分布 |
| **Backend Listener**（可选） | 写到 InfluxDB / Prometheus，看持续压测曲线 |

> **不要用 `View Results Tree` 做正式压测**（见 §0.2）。需要看单个响应时，用 `Save Responses to a file` 只存失败样本，或用 Debug Sampler 在低并发下单独验证。

### 1.7 可观测性现状（**重要：不要假设有现成的指标端点**）

| 能力 | 现状 | 影响 |
|---|---|---|
| Actuator | 各服务引了 `spring-boot-starter-actuator`，但**只为 tracing 自动装配载体**，未配置 `management.endpoints.web.exposure` | **默认只暴露 `/actuator/health`**，拿不到 `/metrics`、`/threaddump`、`/heapdump` |
| 网关 | `ActuatorBlockWebFilter` 拦截 `/actuator/**` → 403 | 通过网关访问不到 actuator |
| Druid 监控页 | 仓库内**无 `StatViewServlet` 配置** | 连接池指标默认**不可见**，需另找观测手段（§3.1） |
| Trace | Zipkin `http://localhost:9411`，全采样（`application.yml` `management.tracing.sampling.probability: 1.0`） | 可查同一 traceId 的调用链；**但看不到未埋点的资源等待** |

> 结论：**JVM / 线程池 / 连接池指标默认没有现成端点**，第 3、8 章会用 `jcmd / jstat / jstack / SHOW PROCESSLIST` 等**进程级工具**替代。若想长期观测，需「建议补充」暴露 Actuator 端点或 Druid StatViewServlet —— 这属于**待补充的验证能力**，非现状。

---

## 1.5 JMeter 零基础操作手册

> 本章学完你会：**从没打开过 JMeter**，也能装好、建第一个计划、跑通百度、看懂命令行、避开 8 个最常见的坑。新手只看本章 + 每个测试的【操作】【验证】即可。

### 1.5.1 下载与启动
1. 打开 https://jmeter.apache.org/download_jmeter.cgi ，下 **Binaries** 的 zip（5.x），**不要**下 Source。
2. 需要 **JDK 17+**，先 `java -version` 确认。
3. Windows 双击 `bin/jmeter.bat`；Mac/Linux `cd bin && ./jmeter`。
4. 首次启动要十几秒，**别重复点**。
5. 记住层级：**测试计划 → 线程组 → 取样器 → 监听器**。

### 1.5.2 第一个计划：访问一次百度（10 分钟跑通）
1. 改语言：`Options → Choose Language → Chinese (Simplified)`。
2. 建线程组：右键 `Test Plan → Add → Threads (Users) → Thread Group`；填 `Threads=1`、`Ramp-up=1`、`Loop=1`。
   - `Threads`=并发用户数；`Ramp-up`=多少秒内把线程都启动完；`Loop`=每个线程循环几次。
3. 加 HTTP 请求：右键 `Thread Group → Add → Sampler → HTTP Request`；`Protocol=https`、`Server Name=www.baidu.com`、`Path=/`、`Method=GET`。
   - ⚠️ **新手最常错**：`Path` 只填 `/` 或 `/xxx`，**别填完整 URL**。
4. 加查看结果树：右键 `Thread Group → Add → Listener → View Results Tree`。⚠️ 只在练习里用，**正式压测绝对不用**。
5. 保存：`File → Save`，存 `demo.jmx`。
6. 运行：点绿色三角。看结果：绿色对勾 + `Response Data` 有 HTML。
7. 清空：扫帚图标。
> 跑不通先查：JMeter 能否上网、`Server Name` 拼写、`Path` 是否填了完整 URL。

### 1.5.3 必会的 6 个元件

| 元件 | 在哪个菜单 | 干什么 | 什么时候用 |
|---|---|---|---|
| Thread Group | Threads (Users) | 定义并发数、加压节奏 | 每个计划都要 |
| HTTP Request | Sampler | 发一个 HTTP 请求 | 每次测接口都要 |
| HTTP Request Defaults | Config Element | 统一设 host/port | 请求多、host 相同时 |
| HTTP Header Manager | Config Element | 加请求头（如 token） | 需要鉴权的接口 |
| JSON Extractor | Post Processors | 从响应提取值给下一个请求 | 登录拿 token 再调别的接口 |
| JSON Assertion | Assertions | 断言响应字段值 | 验证业务正确性 |

> ⚠️ 都是右键 `Thread Group` 或 `HTTP Request → Add` 里找。**加错层级是第二大坑**。

### 1.5.4 参数化
- `${__time()}`：内置函数，返回毫秒时间戳，保证每次值不同。
- **CSV 数据文件**：建 `data.csv` → 右键 `Thread Group → Add → Config Element → CSV Data Set Config` → 填 `Filename`、`Variable Names` → 请求里用 `${drugId}`。
- **函数助手**：`Tools → Function Helper Dialog`。

### 1.5.5 命令行跑压测（正式必用）
```bash
# Windows（在 jmeter\bin 目录）
jmeter.bat -n -t test.jmx -l result.jtl -e -o report
# Mac/Linux
./jmeter -n -t test.jmx -l result.jtl -e -o report
```
`-n` 非 GUI；`-t` 计划文件；`-l` 结果文件；`-e -o` 自动生成 HTML 报告。
- ⚠️ `-o` 目录**必须不存在或为空**，否则报错。
- 跑完看 `report/index.html`。
- **命令行跑前先删掉 / 禁用 `View Results Tree` 等重型监听器**。

### 1.5.6 GUI 按钮速查

| 按钮 | 作用 |
|---|---|
| 绿色三角 | 启动 |
| 红色停止 | 停止（尽量用「停止」而非强杀） |
| 扫帚 | 清空结果 |
| 右上角 `0/0` | 活跃线程数 / 已启动线程数 |
| `Run → Remote Start` | 分布式压测 |
| `Options → Log Viewer` | 看 JMeter 自身报错 |

### 1.5.7 新手 8 个最常见的坑
1. `Path` 填了完整 URL。
2. 监听器加错层级。
3. 忘了加 Header Manager → 全 401。
4. `JSON Extractor` 的 `$.` 路径写错。
5. 高并发还开 `View Results Tree` → OOM。
6. 命令行 `-o` 目录已存在 → 报错。
7. **线程数 = 并发数理解错**（见下方 ⚠️）。
8. 没保存就关 → `.jmx` 白做，养成 `Ctrl+S`。

> ⚠️ **线程数 ≠ 实际并发请求数**。`Threads=50` 表示「最多 50 个线程」，**不代表服务器始终有 50 个正在执行的请求**：
> - `Ramp-up=10` 秒 → JMeter 在 10 秒内**逐步**启动线程，不是瞬间 50 个请求；
> - 响应时间、定时器、连接等待、思考时间、错误重试都会改变**实际请求速率**；
> - 最终总请求数还取决于线程组内取样器数量、控制器与循环结构（`Threads × Loop × 取样器数`）。
> 因此每个方案都要写清「**配置参数 → 预期请求量 → 实际请求量核对方式**」，并在报告里分开看**吞吐量、并发线程数、响应时间**三个不同概念。

### 1.5.8 示范：【操作】块的标准颗粒度（以 v1 计划 1 为例）

> 其余每个测试的【操作】都按这 **5 步**写，步骤里所有「示例值」都注明，依赖现场的值用占位符 `{...}` 并说明去哪查。

**第 1 步：确认环境和数据**
- 确认 JMeter 能访问目标环境；确认本次只在**本地 / 独立测试环境**执行。
- 查库拿真实 id：`SELECT id FROM category WHERE type=1 LIMIT 1;`（**不要**用 H2 的 `categoryId=1`）。

**第 2 步：准备 token、测试数据和配置**
- 按 §1.4.2 本地脚本生成用户端 token（形如 `eyJ...`），**不贴第三方网站**。
- 清缓存前先 `--scan` 计数确认地址、db、前缀、匹配数量（§1.5）。

**第 3 步：创建 JMeter 元件（逐项填）**
- 右键 `Test Plan → Add → Threads → Thread Group`：`Threads=50`、`Ramp-up=10`、`Loop=100`（**示例值**）。
- 右键 `Thread Group → Add → Config Element → HTTP Request Defaults`：`Server Name=localhost`、`Port=9000`（**示例值**）。
- 右键 `Thread Group → Add → Config Element → HTTP Header Manager`：`Name=authentication`、`Value=<第 2 步生成的 token>`。
- 右键 `Thread Group → Add → Sampler → HTTP Request`：`Method=GET`、`Path=/user/drug/list`，`Parameters` 加 `categoryId={真实分类id}`。
- 右键 `Thread Group → Add → Listener → Aggregate Report`；调试阶段临时加 `View Results Tree`，**正式跑前删掉**。

**第 4 步：先低负载调试，再命令行执行**
- 调试：点绿色三角，看 `View Results Tree` 是否 `200`。
- 正式：`jmeter.bat -n -t plan1.jmx -l plan1.jtl -e -o plan1-report`。

**第 5 步：停止条件及中止命令**
- 错误率持续升高 / P99 急剧恶化 / 资源接近上限 → 停止施压（红色停止或 `Ctrl+C`）。
- 记录停止时的线程数、时长、指标，作为**拐点证据**。

---

## 2. 性能与容量测试

> 本章学完你会：先量出「系统正常时是什么水平」（基线），再找出「从哪一刻开始变差」（拐点），并确认「变差的锅不在 JMeter 自己」。
>
> ⚠️ 本章所有并发数字（5/10/20/40/60/80、50 线程…）都是**示例阶梯 / 示例值**，**不是**本项目应该达到的并发标准；具体取决于机器、DB 配置、请求复杂度和**施压机能力**。

### T01 基准测试

#### 【原理】
- **目的**：建立可比对的基线（吞吐、P50/P95/P99、错误率、资源），以后改代码 / 加缓存 / 调连接池都**和它比**，才知道优化有没有效果。
- **4 个代表接口**：

  | 接口 | 路由来源 | 主要观察点 |
  |---|---|---|
  | `GET /user/drug/list?categoryId={cid}` | `ysd-catalog-service/.../controller/user/DrugController.java` → `DrugServiceImpl.listWithSpec:208-228` | 缓存命中、DB 查询、序列化、**N+1** |
  | `POST /user/shoppingCart/add` | `ShoppingCartController.java:23-29` → `ShoppingCartServiceImpl.java:55,62` | DB 写入、**Feign**、购物车并发 |
  | `POST /user/order/submit` | `OrderController.java:29-37` → `OrderServiceImpl.submitOrder:69-128` | **事务**、多步写、正确性 |
  | `PUT /admin/drug` | `ysd-catalog-service/.../controller/admin/DrugController.java:88-94` → `DrugServiceImpl.updateWithSpec:148-173` | 写入耗时、**缓存失效**、并发读写 |

- **每种接口分别测**：低并发（1~5 线程，建基线）、中等并发（逐步加线程）、稳定并发（固定负载看 P50/P95/P99）；**缓存命中与未命中必须分开测**，否则两者混在一起没有意义。
- **前置条件/风险**：独立测试环境；造数会产生数据，需能定向清理。
- **预期现象**：稳定并发下 P95/P99 相对平稳；命中段的 P95 明显低于回源段。
  > ⚠️ **P95 < 50ms 只是本机「缓存命中」场景的参考目标**，**不是**生产标准。没有真实测量就不写「达标 / 不达标」。

#### 【操作】
**第 1 步：确认环境和数据**
- 独立测试环境；`SELECT id FROM category WHERE type=1 LIMIT 1;` 取真实分类 id（**不要用 v1 的 `categoryId=1`**）；`SELECT id FROM drug LIMIT 1;` 取真实 drugId。

**第 2 步：准备 token / 数据**
- 用户端 token（§1.4.2）、管理端 token（§1.4.1）。
- 造数据与清缓存见 §1.5（清缓存**先 `--scan` 计数**）。

**第 3 步：创建 JMeter 元件**（每个接口一份计划，或一个计划多线程组）
- Thread Group：`Threads=5`、`Ramp-up=5`、`Loop=50`（**示例值**，低并发）。
- HTTP Request Defaults：`localhost:9000`。
- HTTP Header Manager：命中/回源用的 header 按接口区分（`/admin/**`→`token`，其余→`authentication`）。
- HTTP Request：`GET /user/drug/list`，`Parameters: categoryId={cid}`。
- Listener：`Aggregate Report`。

**第 4 步：低→中→稳定，命令行执行**
- 调试：5 线程跑通。
- 正式：`jmeter.bat -n -t t01.jmx -l t01.jtl -e -o t01-report`。分三次：低并发 / 中并发 / 稳定并发；缓存**先清后测（miss）**再**预热后测（hit）**。

**第 5 步：停止条件**
- 出现错误率持续升高 / P99 急剧恶化 / 资源接近上限 → 停止。
- 记录停止时的线程数、时长、指标。

#### 【验证】
- **JMeter**：每个接口 × 每种并发 × 命中/未命中，分别记录吞吐、P50/P95/P99、错误率。
- **服务端**：接口耗时占比（回源 vs 命中）；DB `Slow_queries` 增量；Redis 命中率。
- **数据层**：`redis-cli -n 10 info stats | grep -E 'keyspace_hits|keyspace_misses'` 前后差值。
- **根因确认**：本测试只**建立基线**，不下结论。
- **基线记录表**（复制多份填写）：

  | 接口 | 环境/版本 | 数据规模 | 并发 | 时长 | 吞吐 | P50 | P95 | P99 | 错误率 | JVM | MySQL | Redis | 机器 |
  |---|---|---|---|---|---|---|---|---|---|---|---|---|---|
  | | | | | | | | | | | 堆/GC | 活跃连接/慢查询 | 命中率 | CPU/内存 |

> ⚠️ 压测结论必须注明**环境、软件版本、数据规模、负载配置、时长、错误率、延迟分位数、资源指标**。未执行的测试**只写预期现象，不填结果**。

---

### T02 阶梯加压（找拐点）

#### 【原理】
- **目的**：找出**性能拐点**——从哪个并发开始 P95/P99 加速恶化、资源接近上限。**不是**找最大 TPS。
- **示例阶梯**：A=5 / B=10 / C=20 / D=40 / E=60 / F=80（**示例值**，每阶段 2~5 分钟）。
- **前置条件/风险**：独立测试环境；只在上一阶段稳定的前提下才进入下一阶段。
- **预期现象**（拐点示例，**非本项目实测**）：

  | 阶段 | 现象 |
  |---|---|
  | 20 并发 | P95 ≈ 80ms |
  | 40 并发 | P95 ≈ 120ms |
  | 60 并发 | P95 突然 → 900ms，且 MySQL 活跃连接接近上限 |

  > 拐点出现时，**去查连接池 / SQL / DB 负载**，而不是继续加线程硬压 TPS。

#### 【操作】
**第 1 步**：确认独立测试环境；固定数据规模与接口。
**第 2 步**：准备 token / 数据（同 T01）。
**第 3 步**：建 6 份计划或用 `Loop Controller`+`Ultimate Thread Group`（插件）实现阶梯；每阶段 `Duration` 控制 2~5 分钟。
**第 4 步**：依次 `jmeter.bat -n -t t02-A.jmx -l a.jtl -e -o reportA`，A→F 逐段执行；每段开始前**等系统回到基线**。
**第 5 步**：任一段出现「错误率持续升高 / P99 急剧恶化 / 资源接近上限」→ **立即停止，不再加段**。

#### 【验证】
- **JMeter**：把 6 段的 P95/P99/吞吐画成一条曲线，找**斜率突变点**。
- **服务端/数据层**：同步记录 MySQL 活跃连接、CPU、GC，看拐点与哪个资源同时到顶。
- **根因确认**：拐点 → 用 T06/T07/T29 的方法进一步定位（是连接池？慢 SQL？线程池？）。
- **恢复与回归**：停止后确认系统回到基线（P95 回落、连接数回落），再做下一轮。

---

### T03 突发流量

#### 【原理】
- **目的**：模拟促销 / 客户端重试导致的**瞬时集中流量**，重点看**突发结束后系统多久恢复**。
- **手段**：`Synchronizing Timer`（集合点，让线程攒够再一起发）+ 多线程组。
- **前置条件/风险**：独立测试环境；爆发瞬间会冲击下游，需有人盯盘。
- **预期现象**：突发期间错误率 / 延迟上升；**真问题常在突发之后**——积压请求、阻塞线程、未释放连接可能让系统**不立即恢复**。
- **重点看**：网关是否按预期限流；是否出现大量 502/503/429/超时；连接池是否占满；Redis 是否大量 miss；MQ 是否堆积；**流量回落后服务是否仍然很慢**。

#### 【操作】
**第 1 步**：确认环境；记基线 P95/P99。
**第 2 步**：准备 token / 数据。
**第 3 步**：建两个线程组——「低负载组」（如 5 线程，`Duration=120s`）+「突发组」（如 80 线程 + `Synchronizing Timer` 设 `Number of Simulated Users to Group=80`）。
**第 4 步**：命令行执行；时间线设计为「低负载 2 分钟 → 爆发 → 回落低负载 2 分钟」。
**第 5 步**：错误率飙升 / 资源打满 / 大量 429 持续不止 → 停止。

#### 【验证】
- **JMeter**：`Response Times Over Time` 看**恢复期曲线**（多久回到基线）。
- **服务端**：恢复期线程数、连接池、GC。
- **数据层**：恢复期 Redis miss、MQ Ready 增量、DB 活跃连接。
- **根因确认**：若「突发已结束但延迟仍高」，查**积压线程 / 未释放连接 / MQ 积压**，而非只看突发瞬间。
- **恢复与回归**：确认系统**自行回到基线**再收工；否则记录为「未恢复」并进入对应故障章节。

---

### T04 长时间稳定性

#### 【原理】
- **目的**：暴露**慢性**问题（泄漏、堆积），先跑 30~60 分钟，稳了再考虑 2~4 小时，**不要一上来跑一整天**。
- **前置条件/风险**：独立测试环境；长跑会产生大量日志 / 数据。
- **预期现象**：正常应「P95/P99 长时间平稳」。
- **重点观察**：老年代是否持续增长；GC 暂停是否变长；活跃线程是否不断增加；DB / Redis 连接是否泄漏；日志是否快速增长占满磁盘；MQ 消费是否长期低于生产；P95/P99 是否随运行时间恶化。
  > 「开始正常、20 分钟后越来越慢」→ 怀疑**连接泄漏 / 内存泄漏 / 任务堆积 / 慢查询累积**。

#### 【操作】
**第 1 步**：确认环境；`jcmd <pid> GC.heap_info` 记初始堆（无 actuator，用 JDK 工具，见第 8 章）。
**第 2 步**：准备 token / 数据；准备好定时采样（每 1~2 分钟采一次指标）。
**第 3 步**：建固定负载线程组（`Threads=20`、`Duration=3600s`，**示例值**）。
**第 4 步**：命令行执行；用 `jstat -gcutil <pid> 1000` 持续观察。
**第 5 步**：内存持续增长且 GC 后不回落 / 错误率上升 → 停止并进入 T28。

#### 【验证】
- **JMeter**：P95/P99 随时间的趋势（是否恶化）。
- **服务端**：`jstat -gcutil` 的老年代 / Full GC 次数；`jstack` 线程数趋势。
- **数据层**：DB `Threads_connected` 趋势（是否只增不减）；Redis 连接数；MQ Ready 与生产/消费速率差。
- **根因确认**：区分「内存泄漏（GC 后不回）」与「正常缓存占用」——**不要凭一次高内存就下结论**（§8 T28）。
- **恢复与回归**：停止压测后确认资源回落；若泄漏，记录为待修问题。

---

### T05 JMeter 自身瓶颈

#### 【原理】
- **目的**：排除「测出来的是施压机的极限，不是服务端的极限」。
- **前置条件/风险**：无破坏性（只是改 JMeter 用法）。
- **预期现象**：若施压机 CPU 已很高 / 施压线程不足 → 结果偏悲观（虚低）。

#### 【操作】
**第 1 步**：确认施压机与服务端**不同机**（或至少能区分）。
**第 2 步**：准备一份最小计划（单接口）。
**第 3 步**：
- **用非 GUI 模式**跑正式压测；
- **不要开 `View Results Tree`**；
- 用 `Aggregate Report` / `Backend Listener` / 结果文件记数据；
- **同步监控施压机** CPU、内存、网络。
**第 4 步**：命令行执行，同时开 `jconsole`/任务管理器看施压机负载。
**第 5 步**：施压机 CPU 逼近 100% / GC 频繁 → 停止，改用分布式施压或减少监听器。

#### 【验证】
- **施压机**：CPU、内存、网络是否饱和。
- **服务端**：此时服务端资源是否还有余量（有余量→指标失真）。
- **先确认施压端能稳定产生预期请求速率**，再谈服务端容量。
- **根因确认**：若施压机是瓶颈，改用多台 / 分布式（`Run → Remote Start`）重测。

---

## 3. 数据库深水区（重点）

> 本章学完你会：制造并定位「连接池耗尽 / 慢 SQL / 锁等待 / 事务只回滚一半」四类最典型的线上 DB 问题，并知道每一步的证据在哪。
>
> ⚠️ **本章前置认知（已据源码核实）**：
> 1. 本项目数据源是 **Druid**（`druid-spring-boot-3-starter`，`pom.xml:31,88-92`），**不是 HikariCP**。连接池指标用 `ActiveCount / PoolingCount / WaitThreadCount / MaxActive`。
> 2. 各服务 `application.yml` 的 `spring.datasource.druid` **只配了 url/username/password**，**未显式配连接池参数**（`ysd-catalog-service/application.yml:16-21`、`ysd-order-service/application.yml:44-49`）→ 运行时很可能是 **Druid 默认值**（默认 `maxActive=8`、`initialSize=0`、`maxWait=-1`），**但请以现场实测为准**，不要直接把默认值当结论。
> 3. **5 个业务服务共用同一个库 `ysd_take_out`**，各自一个连接池 → **连接总数是 5 个池之和**，容易在 DB 侧先撞上限。

---

### T06 数据库连接池耗尽

#### 【原理】
- **测试目的**：请求量上去后接口越来越慢、大量超时，但 **CPU 可能并不高**——瓶颈在「拿不到数据库连接」而非「CPU 算不过来」。
- **测试对象与调用链**：任意走 DB 的接口。建议 `POST /user/shoppingCart/add`（`ysd-order-service/.../controller/user/ShoppingCartController.java`）或 `GET /user/drug/list`（回源时打 DB，`ysd-catalog-service/.../service/impl/DrugServiceImpl.java:211`）。
- **源码依据**：Druid 数据源 `ysd-order-service/src/main/resources/application.yml:44-49`、`ysd-catalog-service/src/main/resources/application.yml:16-21`；`<druid>` 版本 `pom.xml:33`。
- **前置条件 / 风险**：**只在独立测试环境**；会短时拖慢该环境所有接口；若共用开发库会影响他人。**不改动共享 / 生产库**。
- **预期现象及可能原因**：

  | 现象 | 优先怀疑 |
  |---|---|
  | Active 长期 ≈ Max，WaitThreadCount 持续增加 | 连接池饱和 / 连接被长时间占用 |
  | Active 高，MySQL 查询也慢 | SQL、锁等待、DB 资源瓶颈 |
  | Active 高，但 DB 负载不高 | 事务过长、连接使用不当、慢网络或连接泄漏 |
  | 调大池后 DB 更慢 | DB 并发能力不足，连接数增加反而加重争用 |

#### 【操作】
**第 1 步：确认环境和数据**
- 只在独立测试环境；确认无其他人在压同一环境。
- 记录基线：连接数、慢查询数、各接口 P95。

**第 2 步：准备 token / 数据 / 观察通道**
- 用户端 token（§1.4.2）；确保 `drugId` 真实存在（`SELECT id FROM drug LIMIT 1;`）。
- 打开 DB 侧观察（无需改代码）：
  ```sql
  SHOW STATUS LIKE 'Threads_connected';               -- DB 侧总连接
  SHOW FULL PROCESSLIST;                               -- 当前连接与状态
  SHOW GLOBAL STATUS LIKE 'Slow_queries';
  ```
- 连接池指标默认**没有端点**（§1.7）。可选观测方案（三选一）：
  - **A. 不改代码**：`SHOW PROCESSLIST` 按 `db` 过滤，用 DB 侧连接数近似池占满程度；再用 `jstack <pid>` 找阻塞在 `DruidDataSource.getConnection` 的线程。
  - **B.（建议补充）** 在**测试环境**为 Druid 开 `StatViewServlet`（改配置，**验证后还原**），访问 `/druid` 看 `ActiveCount/PoolingCount/WaitThreadCount/MaxActive`。
  - **C.（建议补充）** 暴露 Actuator `/metrics`（`hikaricp` 无、Druid 需 Micrometer 桥），属待补充能力。

**第 3 步：创建 JMeter 元件**
- Thread Group：`Threads=100`、`Ramp-up=20`、`Loop=200`（**示例值**，按目标环境调）。
- HTTP Request Defaults：`localhost:9000`。
- HTTP Header Manager：`authentication: <token>`、`Content-Type: application/json`。
- HTTP Request：`POST /user/shoppingCart/add`，Body `{"drugId":{真实drugId}}`。
- Listener：`Aggregate Report`（+ 插件曲线）。

**第 4 步：先低负载调试，再命令行执行**
- 低并发（5 线程）确认 200。
- 正式：`jmeter.bat -n -t pool.jmx -l pool.jtl -e -o pool-report`。
- **复现连接等待**（改配置，**验证后务必还原**）：在**独立测试环境**临时把 `spring.datasource.druid.max-active` 调小（如 `5`），重启该服务；压完改回并重启。

**第 5 步：停止条件及中止命令**
- Active ≈ Max 且 WaitThreadCount 持续上升 / 错误率持续升高 / P99 急剧恶化 → 停止（红色停止 / `Ctrl+C`）。
- 中止后**立即**把 max-active 改回原值并重启（见【验证】恢复）。

#### 【验证】
- **JMeter**：错误率、TPS、P95/P99 随时间的变化（拐点出现在哪一刻）。
- **服务端**：`jstack <pid>` 看是否有大量线程卡在 `DruidDataSource.getConnection`；日志里 `GetConnectionTimeoutException` / `wait millis`。
- **数据层**：`SHOW PROCESSLIST` 的 `db`/`State`；`Slow_queries` 增量。若开启 Druid 页，看 `ActiveCount / WaitThreadCount / MaxActive`。
- **根因确认**：**不能只凭「池调大了变快」就下结论**。要区分：是**池太小**（DB 有余力）、还是**连接被某段慢 SQL / 长事务占用**（DB 有压力）、还是**连接泄漏**（Active 只增不降）。
- **恢复与回归（连接池恢复闭环）**：
  1. 还原 `max-active` 配置并重启；确认接口恢复。
  2. 确认 **Active 回落到正常范围、WaitThreadCount 归零**。
  3. 确认**没有残留未提交事务**（`SHOW PROCESSLIST` 无长 `Sleep in transaction`）。
  4. 确认**没有因重试导致新的流量放大**（对比恢复前后 QPS）。
- ⚠️ **修复不能只停留在「调大池」**：要找到连接被占用的原因（慢 SQL / 长事务 / 泄漏），再复测 P95、Pending、DB 负载是否真的改善。

---

### T07 慢 SQL 与索引失效

#### 【原理】
- **测试目的**：在**足够大的数据量**下暴露「全表扫描 / filesort / N+1 / 深分页」问题，并练习用 `EXPLAIN` + 实际耗时综合判断。
- **测试对象与调用链**：
  - 药品列表：`GET /user/drug/list?categoryId={cid}` → `DrugServiceImpl.listWithSpec:208-228` → `drugMapper.list`（`ysd-catalog-service/src/main/resources/mapper/DrugMapper.xml:85-99`）+ **对每条 drug 再查一次规格**（N+1，`DrugServiceImpl.java:220`）→ `specMapper.getByDrugId`。
  - 订单分页：`GET /user/order/page` / `GET /admin/order/conditionSearch` → `OrderMapper.pageQuery`（`ysd-order-service/src/main/resources/mapper/OrderMapper.xml:31-54`，含 `order by order_time desc`、`phone/number like '%...%'`）。
- **源码依据 / 真实库结构**：
  - 真实 `drug` 表**只有主键 + `name` 唯一索引**，**没有 `category_id / status / create_time` 索引**（`docker/mysql/init/01-schema.sql:105-107`）。
  - 真实 `orders` 表**只有主键**，**没有 `user_id / status / order_time` 索引**（`docker/mysql/init/01-schema.sql:244`）。
  - `schema-h2.sql:4-9` 明确：仓库无建表脚本、无法证明生产库有索引。
- **前置条件 / 风险**：独立测试环境；**造 10 万+ 数据会显著增大表**；改索引/删索引**会短暂影响该表所有查询**，**验证后务必还原**。
- **预期现象及可能原因**：`type=ALL`、`rows` 接近总行数、`Using filesort`、实际耗时随数据量线性上升。
  > ⚠️ **防误判（技术修正 4）**：**不能只凭 `EXPLAIN` 的 `type=ALL` 就判定有问题**。还要看**数据量、过滤比例、实际执行耗时**。数据量小的时候 `ALL` 可能比走索引还快。

#### 【操作】
**第 1 步：确认环境和数据**
- 独立测试环境；`SELECT COUNT(*) FROM drug;` 记录造数前后行数。
- 取真实 `categoryId`（`SELECT id FROM category WHERE type=1 LIMIT 1;`）。

**第 2 步：造数与准备**
- 按 §1.5 造 10 万条 `drug`（挂在真实分类下）。
- 记录**造数前**的目标 SQL 耗时（用 `SELECT BENCHMARK` 或直接计时）。

**第 3 步：取执行计划与耗时（在 MySQL 客户端）**
```sql
-- ① 药品按分类列表（对应 drugMapper.list）
EXPLAIN ANALYZE
SELECT * FROM drug WHERE category_id = @cid AND status = 1 ORDER BY create_time DESC;
-- ② 订单按用户分页（对应 orderMapper.pageQuery）
EXPLAIN ANALYZE
SELECT * FROM orders WHERE user_id = {uid} ORDER BY order_time DESC LIMIT 0, 20;
-- ③ 深分页对比
EXPLAIN ANALYZE SELECT * FROM orders ORDER BY order_time DESC LIMIT 100000, 20;
-- ④ 低选择性条件（大量同值 status）
EXPLAIN ANALYZE SELECT * FROM orders WHERE status = 2 ORDER BY order_time DESC LIMIT 20;
```
- 记录：`type`、`rows`、`key`、`Extra`（filesort/temporary）、**实际耗时**。

**第 4 步：对比实验（改索引，验证后还原）**
- 在**独立测试环境**给 `drug(category_id, status)`、`orders(user_id, order_time)` 临时加索引：
  ```sql
  CREATE INDEX idx_test_drug_cat ON drug(category_id, status);
  CREATE INDEX idx_test_orders_user_time ON orders(user_id, order_time);
  ```
- 重复第 3 步的 SQL，比较 `type/rows/耗时`。
- **测试完 DROP 掉这两个索引**（它们是临时验证用，**不是要上线**）：
  ```sql
  DROP INDEX idx_test_drug_cat ON drug;
  DROP INDEX idx_test_orders_user_time ON orders;
  ```

**第 5 步：停止条件与清理**
- 观察 `SHOW GLOBAL STATUS LIKE 'Slow_queries'` 增量；若造数导致磁盘/写入异常，立即停止并清理测试数据（按 `name like '压测药品%'` 定向删除）。
- ⚠️ 新增索引会**增加写入成本与存储**，评估后再决定是否保留。

#### 【验证】
- **JMeter**：对 `GET /user/drug/list` 压测，对比「加索引前 / 后」的 P95/P99；对 `GET /user/order/page` 同理。
- **数据层**：`EXPLAIN ANALYZE` 的 `type/rows/Extra/耗时` 前后对比。
- **N+1 观察**：开 `mybatis` DEBUG 日志或 `SHOW GLOBAL STATUS LIKE 'Com_select'`，看**一次** `drug/list` 请求触发的 SQL 条数是否 ≈ `1 + 药品数`（`DrugServiceImpl.java:211,220`）。
- **根因确认**：综合「数据量 + 过滤比例 + 实际耗时 + 执行计划」判断，**不要只看 `type`**。
- **恢复与回归**：DROP 临时索引 → 重新 `EXPLAIN` 确认回到原计划 → 对比接口 P95 是否回到基线 → 清理测试数据并按 `COUNT(*)` 确认行数恢复。

---

### T08 事务锁等待与死锁

#### 【原理】
- **测试目的**：两个并发事务争抢同一行业务数据时，观察**锁等待**与**死锁**的区别，以及业务层如何表现为「接口变慢 / 报错」。
- **测试对象**：本项目**没有库存表**（见 §7），因此用**真实存在且会被并发更新**的行做示意：`orders`（状态流转）、`shopping_cart`（同一用户同一行）、或 `drug`（管理端改价）。
- **源码依据**：`orders` 表结构 `docker/mysql/init/01-schema.sql:219-245`；`OrderMapper.update`（`ysd-order-service/src/main/resources/mapper/OrderMapper.xml:16-29`）；`ShoppingCartMapper`（`ysd-order-service/src/main/resources/mapper/ShoppingCartMapper.xml`）。
- **前置条件 / 风险**：**只在独立测试环境**。⚠️ **绝不要在共享 / 生产库为了练习而故意持有事务不提交**，会阻塞真实业务。
- **预期现象及可能原因**：
  - **锁等待**：B 请求卡住直到 A 提交/回滚（延迟升高，不报错）。
  - **死锁**：A、B 互相等待，InnoDB 选一个回滚（一方报 `Deadlock found`）。
  - **业务竞争**：DB 不报错，但两个请求基于**相同旧值**做出错误决策（等价于「超卖」的思路）。

#### 【操作】
**第 1 步：确认环境和数据**
- 独立测试环境；记录 `SHOW ENGINE INNODB STATUS` 的初始 `LATEST DETECTED DEADLOCK` 段（作为对照）。
- 选一行真实数据（示例：`SELECT id, status FROM orders ORDER BY id DESC LIMIT 1;`）。

**第 2 步：用两个会话验证基本现象（示意 SQL，表名/字段以实际为准）**
```sql
-- 会话 A
START TRANSACTION;
UPDATE orders SET status = 3 WHERE id = {oid};   -- 持有该行排他锁
-- 暂不 COMMIT

-- 会话 B（另开一个客户端）
UPDATE orders SET status = 5 WHERE id = {oid};   -- 可能等待 A 释放锁
```
- 观察 B 是否阻塞，`SHOW FULL PROCESSLIST` 里 B 的 `State` 是否为 `Waiting for ... lock`。

**第 3 步：通过 JMeter 施加真实并发**
- 对同一订单/购物车行的接口（如 `POST /user/order/repetition` 或更新类接口）持续并发。
- 观察接口耗时分布中是否出现**长尾**（锁等待信号）。

**第 4 步：取锁与死锁证据**
```sql
SHOW FULL PROCESSLIST;
SHOW ENGINE INNODB STATUS;                 -- 看 LATEST DETECTED DEADLOCK 与 LOCK WAIT
-- MySQL 8：也看 performance_schema 锁等待视图
SELECT * FROM performance_schema.data_lock_waits;
SELECT * FROM performance_schema.data_locks;
```

**第 5 步：停止与清理**
- 死锁/等待持续放大 → 停止 JMeter，**`ROLLBACK` / `COMMIT` 掉所有手动会话**（否则锁不释放）。
- 确认 `SHOW FULL PROCESSLIST` 无长事务残留。

#### 【验证】
- **JMeter**：接口 P95/P99 的长尾变化；错误里是否出现 `Deadlock found when trying to get lock`。
- **数据层**：`SHOW ENGINE INNODB STATUS` 的 `LATEST DETECTED DEADLOCK` 段（有事务、等待的锁、回滚的受害者）；`data_lock_waits`。
- **根因确认**：区分「**锁等待**（持有时间长）」「**死锁**（循环等待 + 回滚受害者）」「**业务竞争**（无 DB 错误但数据错）」三者，用不同证据。
- **恢复与回归**：缩短事务、统一加锁顺序、补合适索引、加**有限次**重试（重试必须保证**不重复扣减 / 不重复建单**）。复测同一并发下死锁是否消失，并核对数据未被重复处理。

---

### T09 事务回滚与部分失败

#### 【原理】
- **测试目的**：下单是「多步写」，中途某步失败时，前面已写的步骤**是否整体回滚**；以及**跨服务**时单个 `@Transactional` 是否真的能兜住。
- **测试对象与调用链（据源码核实，与《扩展建议》示例不同）**：
  `POST /user/order/submit` → `OrderServiceImpl.submitOrder`（`ysd-order-service/src/main/java/com/ysd/service/impl/OrderServiceImpl.java:69-128`），事务注解 `@Transactional(rollbackFor = Exception.class)`（`:69`）。真实步骤：
  1. **Feign 查地址簿**（`userClient.getAddressBookById`，`:74`）——**跨服务调用**
  2. 查购物车（`:84`）
  3. `orderMapper.insert`（`:103`）
  4. `orderDetailMapper.insertBatch`（`:115`）← **人为让这一步失败**
  5. `shoppingCartMapper.deleteByUserId`（`:117`）
  > ⚠️ **重要修正**：本项目下单**没有扣库存**、**没有在事务内发 MQ**。MQ 推送发生在 `paySuccess`（`:175-199`，`orderMapper.update` 后 `wsPushProducer.push`），**且 `paySuccess` 无 `@Transactional`** —— DB 更新与发消息**不在同一事务**。所以「DB 回滚了但消息已发」的风险点，本项目在**支付成功**这一步，不在下单。
- **源码依据**：`OrderServiceImpl.java:69-128`（submit）、`:175-199`（paySuccess）、`:539-544`（Feign 失败 fail-fast）。
- **前置条件 / 风险**：**只在独立测试环境**；注入失败需改代码/测试，**验证后务必还原**。
- **预期现象**：第 4 步失败 → 整个 `submitOrder` 回滚 → `orders` **无新记录**、`order_detail` 无新记录、`shopping_cart` **未被清空**。

#### 【操作】
**第 1 步：确认环境和数据**
- 独立测试环境。记录 `SELECT COUNT(*) FROM orders; SELECT COUNT(*) FROM order_detail;`（用户 4）。
- 确保该用户购物车有数据（`SELECT * FROM shopping_cart WHERE user_id={uid};`）。

**第 2 步：准备 token 与 body**
- 用户端 token（§1.4.2）；`addressBookId` 用真实存在的（`SELECT id FROM address_book WHERE user_id={uid} LIMIT 1;`）。
- Body（`OrdersSubmitDTO`）示例：
  ```json
  { "addressBookId": {真实地址id}, "amount": 6.00, "payMethod": 1,
    "remark": "回滚测试", "estimatedDeliveryTime": "2026-10-13T12:00:00",
    "deliveryStatus": 1, "packAmount": 1, "tablewareNumber": 1, "tablewareStatus": 1 }
  ```

**第 3 步：注入失败（推荐用测试，不必改生产代码）**
- **方式 A（推荐，不改主代码）**：新增/复用 JUnit 集成测试，用 `@MockBean OrderDetailMapper` 让 `insertBatch` 抛异常，断言事务回滚。
  > 参考 `docs/05-测试与压测/测试清单.md:75-77`（规划中的 `OrderSubmitRollbackTest`，ORD-16/ORD-16a）。`<!-- TODO: 待确认 -->` 该测试类当前是否已在仓库落地（工作区未看到 `com.ysd.order.OrderSubmitRollbackTest`）。
- **方式 B（改代码，**验证后务必还原**）**：在**独立测试环境**临时在 `insertBatch` 前 `throw new RuntimeException("fault-inject")`，压一次，然后**立即还原**。

**第 4 步：执行与观察**
- 发一次 `POST /user/order/submit`，观察响应为业务错误。
- 立即查库核对（见【验证】）。

**第 5 步：恢复**
- 还原被改的代码/测试配置，重新编译重启；确认正常下单可成功且数据正确。

#### 【验证】
- **必须核对（不是看接口报错就算数）**：
  ```sql
  SELECT COUNT(*) FROM orders WHERE user_id = {uid};        -- 不应新增
  SELECT COUNT(*) FROM order_detail WHERE order_id = ...;   -- 不应新增
  SELECT COUNT(*) FROM shopping_cart WHERE user_id = {uid}; -- 应仍存在（未被清）
  ```
- **MQ 是否存在错误消息**：本项目 `submitOrder` 不发 MQ；若要验证「DB 提交成功但消息发送失败」，请到 **`paySuccess`（`:175-199`）** 场景，确认 `orderMapper.update` 与 `wsPushProducer.push` **不在同一事务**（无 `@Transactional`）——这正是「普通事务 + 发消息不天然原子」的实例（第 5 章 T16）。
- **跨服务一致性**：`submitOrder` 第 1 步是**跨服务 Feign**。**不能**假设一个本地 `@Transactional` 能同时回滚 order 与 user 的数据——它只能回滚**本服务、本数据源**的事务。跨服务一致性需要幂等 / 补偿 / 事务消息（标注为**待补充能力**）。
- **重试是否重复建单**：再发一次相同请求，确认是否产生**第二个订单**（这正是 T23 幂等要测的）。
- **恢复与回归**：还原代码 → 编译重启 → 正常下单成功 → 三条 `COUNT` 符合预期 → 购物车状态正确。

---

## 4. Redis 缓存深水区

> 本章学完你会：从「缓存有效」进阶到「缓存故障」——命中的正常态、穿透、双删、击穿、雪崩、Redis 整体不可用、多实例一致性。
>
> **缓存结构与源码依据（先读）**：
> - key 前缀：`ysd:catalog:drug:{categoryId}`、`ysd:catalog:combo:{categoryId}`、`ysd:catalog:category:{type}`（`ysd-catalog-service/src/main/java/com/ysd/cache/CatalogCacheHelper.java:35-51`）。
> - 读缓存防击穿：`getWithLock` 按 key 加**本地** `ReentrantLock` + 二次检查（`CatalogCacheHelper.java:91-113`）。
> - 写缓存 TTL：药品 10min+[0,120]s、药品组合 15min+[0,180]s、分类 30min+[0,300]s；空值 60+[0,10]s（`CatalogCacheHelper.java:132-140,166-169`）。
> - 空值缓存存 `new ArrayList<>()`（**不是** `Collections.emptyList()`，后者 default-typing 反序列化会崩）（`CatalogCacheHelper.java:157-164`）。
> - 失效走 SCAN 非 KEYS（`CatalogCacheHelper.java:177-186`）。
> - 延迟双删：写库前清一次 + 事务提交后延时 500ms 清一次（`DrugServiceImpl.java:153,172` → `CatalogCacheHelper.scheduleDelayedEvict:192-211`）。
> - ⚠️ **所有 Redis 命令必须带 `-n 10`；禁止 `FLUSHDB`/`FLUSHALL`；只按核实过的前缀清理（§1.5）**。

### §4.1 缓存命中率验证（保留 v1 计划1）

#### 【原理】
- **目的**：验证 `GET /user/drug/list` 走缓存后，首次回源（miss）→ 后续命中（hit），命中段吞吐显著高于回源段。
- **源码**：`ysd-catalog-service/.../controller/user/DrugController.java` → `DrugServiceImpl.listWithSpec:208-228`（key=`drugKey(categoryId)`，走 `getWithLock`）。
- **预期**：首请求 miss → 回源（`drugMapper.list` + **每条**规格 `getByDrugId`）→ 写缓存（TTL 600~720s）；其后全部命中，P95 显著下降（**本机命中参考目标 P95<50ms，非生产标准**）。

#### 【操作】
1. 确认环境/数据：独立测试环境；取真实 `categoryId`。
2. 准备 token（§1.4.2）；清缓存前 `redis-cli -n 10 --scan --pattern 'ysd:catalog:drug:*' | wc -l` 确认。
3. 建 Thread Group `Threads=50/Ramp=10/Loop=100`（示例）+ HTTP Request `GET /user/drug/list?categoryId={cid}` + Header `authentication`。
4. 先 5 线程调试 → `jmeter.bat -n -t t410.jmx -l a.jtl -e -o reportA`。**先清缓存跑一遍（miss）**，再**预热后跑一遍（hit）**。
5. 错误率升高 / P99 恶化 → 停止。

#### 【验证】
- **JMeter**：hit 段与 miss 段的吞吐/P95 对比。
- **数据层**：`redis-cli -n 10 info stats | grep -E 'keyspace_hits|keyspace_misses'`（前后差值）；`redis-cli -n 10 ttl ysd:catalog:drug:{cid}`（应落在 600~720s）。
- **根因确认**：确认 P95 下降确实来自缓存（miss 就慢、hit 就快），而非别的原因。
- **恢复**：压测后按前缀定向清理测试产生的 key（如需）。

---

### §4.2 缓存穿透验证（保留 v1 计划2）

#### 【原理】
- **目的**：验证「查**不存在**的分类」时，**空值缓存**生效：首请求回源返回空 → `put()` 判定空集合 → `setEmpty()` 写空值缓存（短 TTL）→ 后续不再打 DB。
- **源码**：`CatalogCacheHelper.java:115-129`（`put` 判空）、`:162-164`（`setEmpty`）。
- **区别**（防误判）：**穿透 = 查不存在的数据**；**击穿 = 查存在但刚好失效的热点数据**（§T10）。二者不同。
- **预期**：`EXISTS ysd:catalog:drug:{不存在id}` = 1，值为 `[]`，TTL 60~70s。
  > ⚠️ **先确认空值策略真实存在**：本项目**有**空值缓存（`setEmpty`），但**没有**布隆过滤器。不要假定有布隆过滤器。

#### 【操作】
1. 确认环境/数据；取一个**远大于现有 id 的不存在值**（如 `999999`）。
2. 准备 token；清缓存前先计数。
3. 建 HTTP Request `GET /user/drug/list?categoryId=999999`（`Threads=50/Loop=20` 示例）。
4. 先 `redis-cli -n 10 del ysd:catalog:drug:999999`（定向，确认无误删）→ 低并发调试 → 命令行执行。
5. 停止条件同 §4.1。

#### 【验证】
- **数据层**：
  ```bash
  redis-cli -n 10 exists ysd:catalog:drug:999999   # 期望 1
  redis-cli -n 10 get    ysd:catalog:drug:999999   # 期望 []
  redis-cli -n 10 ttl    ysd:catalog:drug:999999   # 期望 60~70
  ```
- **DB 对比**：`SHOW GLOBAL STATUS LIKE 'Com_select';` 增量应**远小于**请求数（空值缓存挡住绝大多数）。
- **根因确认**：确认 `CatalogCacheHelper.java:162-164` 仍是 `new ArrayList<>()`（改过就会反序列化崩）。
- **恢复**：清理 `ysd:catalog:drug:999999`。

---

### §4.3 延迟双删验证（保留 v1 计划3，单实例）

#### 【原理】
- **目的**：验证 `PUT /admin/drug` 写路径的「延迟双删」——写后 **500ms 窗口期内可能读到旧值**，窗口期后缓存被清、回源拿新值，最终**缓存 == DB**。
- **源码**：写 `PUT /admin/drug`（`admin/DrugController.java:88-94`）→ `DrugServiceImpl.updateWithSpec:148-173`：**写库前** `evictByPattern(DISH_PATTERN)`（`:153`），**事务提交后**延时 500ms 再清一次（`:172`）。
- **关键认知**：**
  - 清的是 **`DISH_PATTERN`（全部药品缓存）**，不是单个 key（`:153,172`）——「第二次删除范围偏大」是已知遗留。
  - **本地锁 / 延时任务仅单实例生效**；多实例场景见 §T13。
  - **延迟双删 ≠ 强一致**：即使延时删除成功，也不能凭一次测试证明「任意并发顺序下永不出现旧值」。
- **预期**：窗口内读可能读到旧值；「写完成 + 静置 >1s」后的读，`name` 必须 == 该次写值。

#### 【操作】
1. 确认环境/数据：**catalog 单实例**；取真实 drugId（示例 `id=46`）。
2. 准备 admin token（§1.4.1）。
3. 建**两个线程组**：

   | 线程组 | 线程 | 循环 | 请求 |
   |---|---|---|---|
   | 读 | 20 | 200 | `GET /user/drug/list?categoryId={cid}`，header `authentication` |
   | 写 | 1 | 50 | `PUT /admin/drug`，header `token`；Body 见下 |
4. 先低并发调试 → 命令行执行。写 Body（`DrugDTO`，`name` 用 `${__time()}` 保证不同）：
   ```json
   { "id": 46, "name": "压测药品A-改${__time()}", "categoryId": {cid}, "price": 12.00,
     "image": "a.jpg", "description": "压测", "status": 1, "specs": [] }
   ```
5. 异常即停。

#### 【验证】
- **读端断言**：`$.data[?(@.id==46)].name` ∈ {旧值, 最新写值}（窗口内允许两种）。
- **专项断言（推荐）**：一次写之后 `sleep 1000ms` 再单独发一次读，断言 `name == 该次写值`。
- **缓存观察**：`redis-cli -n 10 ttl ysd:catalog:drug:{cid}`（写后被清 → 读回源后重建）。
- **根因确认**：确认最终一致性（缓存==DB）；多次写 + 多轮读反复验证，而不是只跑一轮。
- **恢复**：把测试改动的药品名改回、清理测试数据。

---

### T10 缓存击穿与热点 key 失效

#### 【原理】
- **目的**：验证「大量请求同时访问**一个存在、但刚好失效**的热点 key」时，**回源是否被合并**（只有 1 个请求打 DB，而不是 N 个）。
- **源码依据（已核实 `getWithLock` 真实实现）**：`CatalogCacheHelper.java:91-109`——miss → `lockFor(key)` 按 key 加**本地** `ReentrantLock` → 锁内**二次查** → `loader` → `put`。`lockFor` 用 `ConcurrentHashMap.computeIfAbsent` 维护按 key 的锁（`:111-113`）。
  > ⚠️ **验证锁粒度/等待策略**：锁**按 key**（不同分类不互相阻塞）；`lock.lock()` **无超时**——若 `loader`（回源）卡住，同 key 的其它线程会**一直等**。这是需要重点确认的风险点。
- **前置条件/风险**：独立测试环境；删除热点 key 属于**定向清理**，勿动其它 key。
- **预期现象**：单实例内，同一 key 的并发 miss **应只触发 1 次回源**（`Com_select` 增量 ≈ 1）；而不是「每个请求轮流查库」。

#### 【操作】
**第 1 步：确认环境和数据**
- 独立测试环境；取真实 `categoryId`；确认 catalog **单实例**（多实例见 T13）。

**第 2 步：准备 token / 预热 / 计数**
- 准备用户端 token。
- **预热**：先发一次 `GET /user/drug/list?categoryId={cid}` 让 key 存在（`redis-cli -n 10 exists ysd:catalog:drug:{cid}` 应为 1）。
- 记录 `SHOW GLOBAL STATUS LIKE 'Com_select';` 基线。

**第 3 步：创建 JMeter 元件**
- Thread Group：`Threads=50`、`Ramp-up=1`（**尽快集中**）、`Loop=1`。
- HTTP Request：`GET /user/drug/list?categoryId={cid}` + Header。
- 加 `Synchronizing Timer`，`Number of Simulated Users to Group=50`（制造「同时」）。

**第 4 步：制造失效并立即施压**
- 先 `redis-cli -n 10 del ysd:catalog:drug:{cid}`（定向、确认）。
- **立即**运行计划（或让计划在运行时删 key）：`jmeter.bat -n -t t10.jmx -l t10.jtl -e -o t10-report`。

**第 5 步：停止条件**
- 若回源请求数 ≈ 线程数（未合并）→ 记录为「防击穿未生效」，停止并查 §验证。

#### 【验证】
- **数据层**：`SHOW GLOBAL STATUS LIKE 'Com_select';` 增量——**期望接近 1**（单 key）；若 ≈ 50，说明锁没把回源合并。
- **服务端**：日志里 `drugMapper.list` 的调用次数。
- **根因确认**：若未合并，检查 `getWithLock` 是否被绕过（有人直接 `get` 后查库）、或锁是否被 `computeIfAbsent` 语义影响。
- **恢复与回归**：压完恢复缓存（一次读即重建）；确认 `getWithLock` 实现未被改动。

---

### T11 缓存雪崩与 TTL 集中到期

#### 【原理】
- **目的**：验证「带随机抖动的 TTL」能否**错开**大批 key 的过期时间，避免同一刻集中失效→集中回源→打爆 DB。
- **源码依据**：TTL = 基础时长 + `ThreadLocalRandom` 抖动（`CatalogCacheHelper.java:132-140,166-169`）：药品 `600 + rand(0..120)`s。
- **前置条件/风险**：独立测试环境；构造「过期时间接近」的 key 需要批量写，注意数量上限。
- **预期现象**：抖动**有效** → 一批 key 的 TTL 应**均匀散布**在某区间（不是同一个值）；抖动**无效** → `TTL` 大量相等。

#### 【操作】
**第 1 步**：确认环境/数据；取多个真实分类 id（或造一组）。
**第 2 步**：准备 token；记录 `keyspace_misses`、`Com_select` 基线。
**第 3 步**：批量预热多个分类的 drug key（可用脚本或 JMeter 循环），随后**删掉它们**（定向前缀、先计数）。
**第 4 步**：对这批分类**同时施压**；命令行执行。
**第 5 步**：DB `Com_select`/连接数激增不止 → 停止。

#### 【验证】
- **数据层**：抽查多个 key 的 TTL：`redis-cli -n 10 --scan --pattern 'ysd:catalog:drug:*' | head | xargs -r -n1 redis-cli -n 10 ttl`——**期望值分散**而非全相等。
- **对比实验（改代码，验证后还原）**：临时把 `DISH_JITTER_SECONDS` 改为 `0`（`CatalogCacheHelper.java:133`），重复实验，观察是否出现集中回源——**验证后务必还原**。
- **根因确认**：确认 miss 峰值是否与「TTL 集中到期」时刻吻合。
- **恢复与回归**：还原 jitter 配置；清理测试 key。

---

### T12 Redis 不可用与故障放大链

#### 【原理】
- **目的**：验证 Redis **整体不可用**时系统的真实行为，以及是否出现**故障放大**。
- **源码依据（关键，决定预期！）**：
  - `CatalogCacheHelper.get`（`:83-85`）直接 `redisTemplate.opsForValue().get(key)`，**没有 try/catch** → Redis 故障时**异常向上抛**，`/user/drug/list` 会 **500**（不会 fallback 查 DB）。
  - `ShopController.getStatus`（`:40-44`）里的 `get(KEY)`（`:51`）在 try **之外** → Redis 故障时 `/user/shop/status` 也 **500**。
  - **仓库未见 `spring.data.redis.timeout` / 连接池配置**（`ysd-order-service/application.yml:50-55`）→ 连接失败是**快速失败**还是**等到默认命令超时**，**必须实测**（不要假定）。
- **三种故障**（分别测）：① Redis 服务停止；② Redis 网络延迟 / 连接建立超时；③ Redis 恢复后缓存全空。
- **预期现象与放大链**：
  - 当前实现下，Redis 挂 → 请求**快速失败**（500），**不是**「无限回源」（因为 `get` 抛异常、走不到 loader）。
  - ⚠️ **但要注意「改进后的风险」**：若有人把 `get` 改成 catch 后回源，就会变成 `Redis 不可用 → 缓存失效 → 大量回源 → 连接池耗尽 → 全链路超时`。**这是要防的放大链，不是当前行为**——验证时要**分别说明「现状」与「假设改进后」**。
  - 「Redis 挂了就必须查 DB」**不是**普遍正确：对高流量系统，无限制回源可能比直接失败更危险。

#### 【操作】
**第 1 步：确认环境和数据**
- **独立测试环境**。记录基线：`/user/drug/list` 与 `/user/shop/status` 的 P95、错误率；`SHOW STATUS LIKE 'Threads_connected';`。

**第 2 步：准备 token / 观察通道**
- 用户端 token。
- 记录 Redis 正常时 `/user/shop/status` 返回（营业/打烊）。

**第 3 步：建 JMeter 元件**
- 两个 HTTP Request：`GET /user/drug/list?categoryId={cid}` 和 `GET /user/shop/status`（后者是**白名单，无需 token**）。
- `Threads=20`、`Duration=120s`（示例）。

**第 4 步：注入故障（**仅独立测试环境**）**
- 停 Redis：`redis-cli -n 10 shutdown nosave`（或 `docker stop <redis容器>`）——**这会清掉未落盘数据，仅限测试环境**。
- 或注入延迟：用 `tc`/`clumsy`/`toxiproxy` 在**测试环境**加网络延迟。
- 观察 2 分钟；然后启动 Redis（`redis-server ...` / `docker start`）。

**第 5 步：停止条件与恢复**
- 错误率持续 100% / 请求大面积长时间挂起 → 停止施压。
- **恢复 Redis 后**继续观察（见【验证】）。

#### 【验证】
- **JMeter**：Redis 挂期间两个接口的**状态码与耗时**（是「快速 500」还是「长时间挂起」——这决定有没有超时配置）。
- **服务端**：日志里的 Redis 异常类型（`RedisConnectionFailureException` / `TimeoutException`）。
- **数据层**：Redis 恢复后 `keyspace_misses` 是否低（缓存逐步重建）；`SHOW STATUS LIKE 'Threads_connected';` 是否被回源冲击。
- **根因确认**：明确写出「**当前** Redis 故障 = 请求失败；**是否会回源冲垮 DB，取决于是否有人改了 `get` 的异常处理**」。
- **恢复与回归**：Redis 恢复 → 缓存重建（`keyspace_misses` 回落）→ `Threads_connected` 回落 → 两个接口恢复正常 → 服务健康检查。

> ⚠️ **数据恢复不只是「接口恢复」**：还要确认**缓存重建未冲垮 DB**、其它服务未受牵连（§技术修正 4「数据恢复」）。

---

### T13 延迟双删在多实例下是否仍正确

#### 【原理】
- **目的**：验证 `§4.3` 的延迟双删在**多实例**下是否失效。
- **源码依据**：`CatalogCacheHelper` 的锁是**本地** `ConcurrentHashMap`（`:59,111-113`），延时任务用**本地** `ScheduledExecutorService`（`:62-68`）——**只在发起写入的那个 JVM 内生效**。`-Dserver.port` 起第二实例即可暴露。
- **前置条件/风险**：独立测试环境；两实例**共用同一 Redis db10**（否则测的不是同一份缓存）。
- **预期现象**：A 实例写入 → A 的延时任务只清自己记得的缓存；B 实例若有并发的旧值回写，**A 清不到 B 的写**→ 多实例下仍可能残留旧值。
- **关键认知**：**延迟双删 ≠ 强一致**。多轮并发 + 故障注入才能评估，不能凭一次成功下定论。

#### 【操作】
**第 1 步：确认环境/数据**
- 独立测试环境；确认两实例都能无冲突启动。
- 取真实 drugId。

**第 2 步：起第二实例**
- 复制启动：`java -jar ysd-catalog-service.jar --server.port=8184`（示例端口），或 IDE 里开第二份、改端口。
- 确认两实例都注册到 Nacos、都连 `db10`。

**第 3 步：构造「A 写、B 读」并发**
- JMeter 两个线程组：一个持续 `PUT /admin/drug`（经网关会轮询到 A/B），一个持续 `GET /user/drug/list`。
- 或直接分别打两个实例端口，保证「A 写、B 读」。

**第 4 步：写后静置观察**
- 一次写后 `sleep` **超过 500ms 延时窗口**（如 1500ms），再核对 DB 与缓存。

**第 5 步：停止与恢复**
- 异常的旧值持续出现 → 记录；停止 JMeter；关掉第二实例。

#### 【验证】
- **数据层**：写后 >1s，`GET` 返回的 `name` 是否**等于最新 DB 值**；`redis-cli -n 10 get ysd:catalog:drug:{cid}` 与 DB 对比。
- **多轮**：反复跑多轮，统计「出现旧值」的比例（而非单次）。
- **根因确认**：确认旧值来自**本地锁/本地延时任务跨实例失效**，而非别的原因。
- **恢复与回归**：关闭第二实例 → 确认单实例下仍正确 → 记录「多实例一致性」为**待改进/待补充能力**（分布式失效通知 / 可靠延时消息）。

---

## 5. RabbitMQ 消息链路

> 本章学完你会：把 MQ 当**独立故障链路**测试，而不是只确认「消息能发出去」。
>
> ⚠️ **先读：本项目 MQ 的真实架构（据源码核实，与常见"业务队列"假设不同）**
> - **交换器**：`ws.broadcast` **fanout**（durable=true, autoDelete=false）（`ysd-push-service/.../config/RabbitConfig.java:22,25-27`）。
> - **队列**：每个 push-service 实例用 **`AnonymousQueue`（exclusive、auto-delete、随机名、非持久化）**，绑定到该 fanout（`RabbitConfig.java:29-37`）。目的：**每个实例都收到全量消息**（否则固定队列名会变成竞争消费，只有一个实例收到，见 `RabbitConfig.java:12-17`）。
> - **消费者**：`WsPushListener`（`ysd-push-service/.../mq/WsPushListener.java:24-28`），**自动 ack**、**不重试**、**无 DLQ**（注释见 `:12`）。
> - **生产端**：`WsPushProducer.push` → `convertAndSend(EXCHANGE, "", json)`（`ysd-order-service/.../mq/WsPushProducer.java:17,25-27`）。
> - **谁发消息**：只在下单**支付成功后**（`OrderServiceImpl.paySuccess:175-199`，`orderMapper.update` 后 `wsPushProducer.push`，**无 `@Transactional`**）和**催单**（`reminder:551-565`）。消息体 `{"type":1|2,"orderId":..,"content":".."}`。
>
> **由此推出的关键结论（决定 T14~T17 的正确预期）**：
> 1. 这是**广播**语义，**不是**可靠的业务队列。**实例下线 → 它的 exclusive 队列立即删除 → fanout 无绑定队列 → 该段时间的消息被「丢弃」**，而**不会堆积**。
> 2. **没有 DLQ、没有重试、没有手动 ack** → 经典的「死信 / 毒消息 / 无限重试」问题在本项目**不适用**（属**建议补充的验证能力**）。
> 3. 真正的**幂等 / 一致性**风险不在 MQ 消费端（广播重复推送无害），而在**业务写接口**（见第 7 章 T23）。
> 4. `paySuccess` 里「DB 更新」与「发消息」**不在同一事务** → 这是「发送成功但业务未提交 / 业务提交但发送失败」的真实触发点（T16）。

### T14 消费者停止与消息行为

#### 【原理】
- **目的**：确认「停止消费者」在本项目架构下的**真实后果**——是**堆积**还是**丢弃**。
- **源码依据**：`RabbitConfig.java:29-37`（`AnonymousQueue`）、`WsPushListener.java:12,24-28`（auto-ack / no DLQ）。
- **前置条件/风险**：独立测试环境；停止 push-service 会中断该实例的 WebSocket 广播。
- **预期现象（防误判！）**：停止**唯一/所有** push-service 实例后，**fanout 没有绑定队列** → 期间生产的消息**被丢弃**（Ready/Unacked 都不会累积），**不是**「消息堆积」。**要看到「堆积」，必须先把队列换成持久化具名队列——那是「建议补充」的改造，不是现状。**
- **记录指标（能拿到就记）**：消息生产速率、消费速率、Ready、Unacked、消费失败/重试次数、端到端延迟。
  > 若用固定具名队列（需改造）才谈得上「生产速率>消费速率→队列增长」的估算（示例：200/s 生产、150/s 消费 → 净增 50/s）。

#### 【操作】
**第 1 步：确认环境和数据**
- 独立测试环境；确认 push-service 进程与端口 8086。
- 打开 RabbitMQ 观测（二选一）：
  ```bash
  rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
  # 或管理界面 http://localhost:15672 （guest/guest，若已启用 management 插件）
  ```

**第 2 步：准备「会发消息」的触发路径**
- 需要真正下发消息：走 `PUT /user/order/payment`（→ `paySuccess`，`OrderController.java:45-52`）或 `GET /user/order/reminder/{id}`（`:112-117`）。
- 准备用户端 token。

**第 3 步：建 JMeter 元件**
- Thread Group：对**支付/催单**接口持续施压（`Threads=10`、`Duration=120s`，示例）。

**第 4 步：停消费者 → 施压 → 恢复**
- 停 push-service：`taskkill /PID <pid> /F`（Windows）或 `docker stop ysd-push-service`。
- 记录此时 `rabbitmqctl list_queues`（**注意绑定队列已随实例消失**）。
- 施压 2 分钟；观察是否出现 Ready/Unacked 增长。
- 恢复 push-service，再施压。

**第 5 步：停止与恢复**
- 停止施压；确认 push-service 恢复为 1 个健康实例、能收到消息。

#### 【验证】
- **MQ 侧**：`rabbitmqctl list_queues` —— 消费者停止期间**期望没有该实例的队列**（`AnonymousQueue` 已删除），故**无堆积**。
- **服务端**：push-service 日志（恢复正常后能打印 `收到广播消息`，`WsPushListener.java:26`）。
- **端到端**：恢复后新产生的消息能否推送到 WebSocket 客户端。
- **根因确认**：明确写出「**现状 = 丢弃（不堆积）**；若要可靠堆积需改造为持久化队列 + 手动 ack + 死信」。
- **恢复与回归**：push-service 重启后确认队列重新声明、消息重新到达（见 §恢复闭环：不仅「重启消费者」，还要验证消息正确处理、无遗漏）。

---

### T15 重复消费与幂等（**本项目不适用于 MQ 消费端**）

#### 【原理】
- **目的**：区分「**MQ 消费端幂等**」与「**业务写接口幂等**」，不要把两者混为一谈。
- **源码依据**：`WsPushListener` 自动 ack、无重试、无 DLQ（`:12,24-28`）；广播消息重复推送**无害**（注释 `:12`「广播类消息重复推送无害」）。
- **关键结论（防误判！）**：本项目的唯一 MQ 消费者是 **WebSocket 广播**，其业务是「把消息转发给本实例客户端」，**重复执行没有副作用**。因此：
  - 的「重复消费导致订单状态/库存重复处理」在本项目**不成立**（消费端不写业务库）。
  - **真正的幂等问题在业务写接口**：`POST /user/order/submit`、`POST /user/shoppingCart/add`（见 T23）。
- **预期现象**：即使广播消息被重复投递/重复推送，也**不应**改变任何业务数据（因为没有业务写入）。
- ⚠️ **`session` / 手动 ack 的重复处理窗口**：本项目用**自动 ack**，无法在「处理成功后、确认前杀消费者」这个经典窗口做实验（那是**建议补充**的手动 ack 改造）。

#### 【操作】
**第 1 步**：独立测试环境；准备用户端 token。
**第 2 步**：准备触发消息的接口（支付/催单）。
**第 3 步**：建 JMeter 元件，对触发接口重复施压（制造多条广播 + 可能的重投）。
**第 4 步**：制造「同一业务动作重复触发」：
  - 同一订单反复 `PUT /user/order/payment`；或对同一 `orderId` 反复 `GET /user/order/reminder/{id}`。
**第 5 步**：停止并核对业务数据未被重复修改（见【验证】）。

#### 【验证】
- **业务库**：`SELECT * FROM orders WHERE id={oid};` 状态/支付状态是否被重复改写（应幂等或至少可解释）。
- **WebSocket**：客户端是否收到重复推送（**无害**，属预期）。
- **根因确认**：明确「MQ 消费端无业务写入 → 无幂等问题；幂等要求落在业务接口」。
- **恢复与回归**：把测试订单/购物车状态恢复；核对无残留。

> ⚠️ 若将来把业务写入下沉到 MQ 消费者（改造），则**必须**补：业务唯一键 / 消费记录表 / 状态条件更新 + **手动 ack 与业务在同一可靠事务边界**（否则「已处理」标记与业务不一致会留重复窗口）——**属建议补充能力**。

---

### T16 消息发送成功但业务未提交

#### 【原理】
- **目的**：验证「**DB 更新**」与「**发 MQ 消息**」是否原子——本项目**不原子**。
- **源码依据**：`OrderServiceImpl.paySuccess:175-199`：`orderMapper.update(orders)`（`:188`）之后 `wsPushProducer.push(json)`（`:198`），**整个方法没有 `@Transactional`**。因此可能出现：
  - DB 更新成功、消息发送失败（push 抛异常）；
  - 或服务在两者之间崩溃。
- **前置条件/风险**：独立测试环境；注入失败需造异常，**验证后还原**。
- **预期现象**：两者**可能不一致**（这正是要验证的脆弱点）。

#### 【操作】
**第 1 步**：独立测试环境；准备 token；取一个真实待支付订单号。
**第 2 步**：记录基线（订单状态、MQ 是否有对应消息）。
**第 3 步**：**方式 A（不改代码）**：让 RabbitMQ 短暂不可用（停 broker，仅测试环境），再调 `PUT /user/order/payment`，观察：DB 是否已更新、消息是否丢失。
**第 4 步**：**方式 B（改代码，验证后务必还原）**：临时让 `wsPushProducer.push` 抛异常，观察 `orderMapper.update` 是否**已提交**（因为无事务）。
**第 5 步**：恢复 broker / 还原代码；核对 DB 与 MQ 是否一致。

#### 【验证】
- **DB**：`SELECT status, pay_status, checkout_time FROM orders WHERE number = {orderNumber};` 是否已更新（说明 DB 先提交了）。
- **MQ**：对应广播消息是否存在/被消费。
- **根因确认**：结论是「**普通事务 + 发消息不天然原子**」。修复方向（**建议补充**）：事务消息 / Transactional Outbox（事务发件箱）/ 可靠重试 + 补偿。
- **恢复与回归**：恢复 broker、还原代码；业务数据一致（必要时人工补偿）。

---

### T17 顺序、死信、毒消息（**本项目多数不适用，属建议补充**）

#### 【原理】
- **目的**：区分 6 件事：**生产端确认 / 消息持久化 / 队列持久化 / 消费者 ACK / 重试 / 死信**。它们**互相独立**，缺一即不可靠。
- **源码现状（据核实）**：
  - 生产端：`convertAndSend`，**未见 publisher confirms 配置**（`ysd-order-service/application.yml:56-59` 只有 observation）。
  - 队列：`AnonymousQueue` **非持久化**（`RabbitConfig.java:31`）；交换器 durable 但**无持久队列绑定**。
  - 消费：**自动 ack、无重试、无 DLQ**（`WsPushListener.java:12`）。
- **关键结论**：**顺序保证 / 死信 / 毒消息 在本项目不构成业务问题**（广播、无业务写入、无 DLQ）。以下测试**主要以「观察现状 + 说明改造需求」为目的**，不要把它们当成「已有能力的验证」。
- **预期现象**：一条持续失败的消息**不会**无限重试（无重试机制），也**不会**进死信（无 DLQ）；RabbitMQ 短暂不可用时，**生产端 `convertAndSend` 会抛异常**（是否重试取决于调用方，`paySuccess` 未捕获则整个支付流程报错）。

#### 【操作】
**第 1 步**：独立测试环境；准备 token。
**第 2 步**：确认当前 declaration（交换器/队列属性）：
```bash
rabbitmqctl list_exchanges name type durable auto_delete | grep ws.broadcast
rabbitmqctl list_queues name durable auto_delete exclusive
```
**第 3 步**：**顺序**——对同一 `orderId` 快速连续发「支付」「催单」消息，观察 push 端到达顺序（广播 + 多实例无全局顺序保证）。
**第 4 步**：**RabbitMQ 短暂不可用**——停 broker，调用 `paySuccess` 路径，观察生产端报错与调用方行为；恢复 broker 后再试。
**第 5 步**：停止与恢复；确认 broker 恢复后消息链路通。

#### 【验证】
- **MQ 侧**：`rabbitmqctl list_queues`（确认队列属性 = 非持久 / exclusive / auto-delete）；无 DLQ 队列。
- **服务端**：生产端异常日志；恢复后能否继续发送。
- **业务状态**：消息堆积（若有）清空后业务状态是否正确。
- **根因确认**：结论写「**现状：广播、非持久、无 DLQ、无重试**；可靠投递（持久化队列 + 手动 ack + 死信 + 有界重试 + 顺序键）属**建议补充能力**」。
- **恢复与回归**：broker 恢复 → 新消息链路通 → 服务健康。

---
<!-- 说明：不要依赖「全局顺序」——不同队列类型、消费者数量、预取数量、重试配置都会影响顺序与重投行为（本项目为广播，更无全局顺序保证）。 -->

## 6. Spring Cloud 微服务故障

> 本章学完你会：从「一个服务被关闭」扩展到「各种故障下系统的边界是否有效」——变慢、退出、重启、注册中心失联、限流熔断重试叠加。
>
> **前置源码依据**：
> - Feign 熔断开关：`spring.cloud.openfeign.circuitbreaker.enabled: true`（`ysd-order-service/application.yml:36-39`）。
> - Sentinel 数据源（降级规则存 Nacos）：`dataId: ysd-order-service-sentinel-degrade`，`groupId: SENTINEL_GROUP`（`ysd-order-service/application.yml:21-35`）；Dashboard transport 端口 `8719`。
> - Feign 客户端：`CatalogClient`（`ysd-api/.../client/CatalogClient.java:17-32`，path=`/inner`，`fallbackFactory = CatalogClientFallbackFactory.class`）。
> - 降级内容：**fail-fast，不编造数据** → 抛 `ShoppingCartBusinessException("商品服务繁忙，请稍后重试")`（`ysd-api/.../client/fallback/CatalogClientFallbackFactory.java:23-35`）。
> - ⚠️ **`GET /user/order/orderDetail/{id}` 不调用 catalog Feign**（只查本服务库，`OrderServiceImpl.details:244-257`）。**真正触发 `CatalogClient` 的是「加购物车」**（`ShoppingCartServiceImpl.java:55,62`）。
> - ⚠️ **加购仅在「购物车中没有该商品」时才调 Feign**（`ShoppingCartServiceImpl.java:44-48` 命中则只 `updateNumberById`）。因此压测时若商品已在购物车，**触发不了 Feign**——需用**每次不同的 drugId** 或**先清空购物车**。

### §6.1 Feign 熔断降级验证（保留 v1 计划4）

#### 【原理】
- **目的**：验证下游（catalog）挂掉时，order 通过 Feign 触发**熔断降级** → **快速失败**（不阻塞到超时）+ 返回降级内容。
- **调用链**：`POST /user/shoppingCart/add`（`ShoppingCartController.java:23-29`）→ `ShoppingCartServiceImpl.addShoppingCart:35-71` → `catalogClient.getDrugById`（`:55`）。
- **预期**：kill catalog 后，order 侧 Feign 触发熔断降级 → 抛 `ShoppingCartBusinessException` → `GlobalExceptionHandler` 转成 `Result.error`（`code != 1`）；**快速失败**（延迟不堆积到超时）。
- **fallback 生效的 3 个前提（缺一即静默失效）**：① `circuitbreaker.enabled: true`（✅ `application.yml:36-39`）；② 类路径有 CircuitBreaker 实现（Sentinel starter）；③ `@FeignClient(fallbackFactory=...)`（✅ `CatalogClient.java:17-19`）。

#### 【操作】
1. 确认环境/数据：独立测试环境；取一个**真实存在**的 drugId（否则命中业务异常而非熔断）。
2. 准备用户端 token；**清空或换新 drugId 确保走 Feign 分支**（`DELETE /user/shoppingCart/clean` 或每次用不同 drugId）。
3. 建 HTTP Request `POST /user/shoppingCart/add`，Body `{"drugId": {真实drugId}}`，Header `authentication` + `Content-Type`。
4. 先低并发调试（5 线程）→ 正式 `jmeter.bat -n -t t61.jmx -l a.jtl -e -o report`。**压测中途 kill catalog**：
   ```bash
   jps -l                                  # 找 YsdCatalogServiceApplication 的 pid
   taskkill /PID <pid> /F                  # Windows
   # 或 docker stop ysd-catalog-service
   ```
5. 停止条件：错误率上升但 **P95 不飙升**（快速失败特征）；若 P95 飙升说明没走 fallback。

#### 【验证】
- **响应体**：`$.code != 1`、`$.msg == "商品服务繁忙，请稍后重试"`（或统一错误文案）。
- **JMeter**：kill 后**错误率上升、P95 不飙升**。
- **Sentinel Dashboard**（order-service，transport `8719`）：资源 `CatalogClient#getDrugById(Long)` 熔断器进入 **OPEN**。
- **示例降级规则（示例值，以 Nacos 实际为准）**：`CatalogClient#getDrugById(Long)` 异常比例 >50%、窗口 10s（来源 `docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel改造-准备.md`；**以 Nacos `SENTINEL_GROUP / ysd-order-service-sentinel-degrade` 实际为准**）。
- **根因确认**：区分「**熔断降级**（fast-fail + fallback）」与「**只是超时**」（P95 飙升）——不能仅凭出现 503 就认定 Sentinel 生效。
- **恢复与回归**：重启 catalog → 确认 Feign 调用恢复、加购成功（§恢复闭环）。

---

### §6.2 网关 QPS 限流验证（保留 v1 计划4.1）

#### 【原理】
- **目的**：验证网关对指定路由的 QPS 限流生效。
- **规则位置（不在仓库）**：Nacos `dataId: ysd-gateway-sentinel-gw-flow`，`groupId: SENTINEL_GROUP`，`rule-type: gw-flow`（`ysd-gateway/src/main/resources/application.yaml:21-30`）。**阈值仓库内不可见**。
- **预期**：超过阈值返回 **HTTP 429**，`blockQps` 上升。

#### 【操作】
1. **读取规则（不写死）**：Nacos 控制台 → 配置管理 → 打开 `ysd-gateway-sentinel-gw-flow`，看 `count` 与 `grade`。
   - ⚠️ **`grade` 必须 = 1（QPS）**。`grade=0` 是线程数模式，而网关 `GatewayFlowSlot` 不维护线程计数 → `0+1<=count` 恒成立 → **规则恒放行**（`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md`）。
   - 压测前**连续观察几分钟**确认 `count` 不再变化（历史上出现过被第二个写入方在 5↔100 间反复覆盖）。
2. 准备 token；选一个路由（如 `POST /user/shoppingCart/add`）。
3. 用**真并发**持续施压，逐步加大线程数直到超过 `count`。
   > ⚠️ **顺序发请求测不出限流**（QPS 是「每秒配额」，发太慢到不了阈值、发太快又可能撞上配错规则，两种都会误判）。
4. 命令行执行，观察是否出现 429。
5. 停止条件：出现 429 且比例随并发上升即达到目的。

#### 【验证】
- **网关 metrics**（transport `8720`，`application.yaml:20`）：`curl http://127.0.0.1:8720/gateway/getRules` 看规则是否加载、`grade/count` 是否与 Nacos 一致。
- **JMeter**：429 比例随并发上升。
- **日志**：应用侧出现 `~/logs/csp/sentinel-block.log`。
- **根因确认**：**先在 Nacos 侧确认配置稳定**，否则测的可能是「被覆盖后的错误规则」。

---

### T18 下游变慢而非挂掉

#### 【原理】
- **目的**：真实生产中下游更常见的是「**活着但很慢**」（如 catalog 某查询 50ms → 5s）。验证上游**线程是否被占用堆积**，进而拖垮**本不依赖该下游**的接口。
- **源码依据**：order → catalog 的 Feign（`CatalogClient.java:17-32`）；`unwrap` fail-fast（`ShoppingCartServiceImpl.java:95-103`，`OrderServiceImpl.java:539-544`）。
- **前置条件/风险**：**延迟必须注入专用测试接口或隔离环境**，**不要**为造延迟在共享环境改真实业务代码。
- **预期现象**：下游慢 → 上游 **Tomcat 工作线程被阻塞**在 Feign 调用上 → 若无合理超时与并发隔离，**线程池被占满** → 连 `/user/order/historyOrders` 这类不依赖 catalog 的接口也变慢。
- **要检查**：Feign 连接/读取超时；熔断慢调用阈值、滑动窗口、恢复条件；是否存在多层重试；超时后后台业务是否仍在跑；下游恢复后上游是否仍积压。

#### 【操作】
**第 1 步**：独立测试环境；记录基线（加购接口 + 一个**不依赖 catalog** 的接口的 P95）。
**第 2 步**：准备 token。
**第 3 步**：注入延迟（**三选一，仅在测试环境**）：
  - A.（推荐）在**测试环境**给 catalog 的 `/inner/drug/{id}` 加一个**测试专用 delay 参数/接口**（改代码，**验证后还原**）；
  - B. 用 `toxiproxy` / `clumsy` 给 order→catalog 的网络加固定延迟；
  - C. 用 `tc qdisc add ... netem delay 3000ms` 在测试环境限速。
**第 4 步**：对加购接口施压（`Threads=50`、`Duration=120s`），观察：
  - 加购接口 P99；
  - **不依赖 catalog** 的接口 P95 是否也上升（线程被拖垮的信号）。
  - `jstack <order-pid>` 看是否有大量线程 BLOCKED/WAITING 在 Feign/Socket。
**第 5 步**：去掉延迟（还原/关闭 toxiproxy）→ 立即施压 → 观察恢复。停止条件：健康接口也大面积超时。

#### 【验证】
- **JMeter**：加购 P99 与「健康接口」P95 的联动。
- **服务端**：`jstack` 线程状态；Tomcat 线程池活跃数（无 actuator，用 jstack 数线程）。
- **数据层**：连接池是否被「慢调用」连带占满（T06）。
- **根因确认**：区分「下游慢」vs「上游无超时/无隔离」。检查 Feign 超时是否配置：`<!-- TODO: 待确认 -->` 仓库未见 `spring.cloud.openfeign.client.config.default.connectTimeout/readTimeout`（`ysd-order-service/application.yml:36-43` 只有 `loggerLevel: FULL`）→ **需实测**是否用了默认超时。
- **恢复与回归**：下游恢复 → 上游 P95 回落 → 无积压残留线程。

---

### T19 实例退出与重启

#### 【原理】
- **目的**：不只测「停止后接口报错」，还要测**恢复过程**。
- **场景**：catalog / order 分别做：正常停止 / 强制 kill / 重启 / **启动期间接收请求** / **多实例中仅一个退出** / 重新注册 Nacos 后继续接流量。
- **重点观察**：Nacos 实例状态；网关是否仍路由到失效实例；Feign 是否出现重试与延迟；启动期因 DB/Redis/MQ 未就绪而持续报错；恢复后缓存、消费者、定时任务是否正常。
- **预期**：**503 不一定是 Sentinel 问题**——可能是 Nacos 注册状态、网关路由、实例健康或调用超时。

#### 【操作】
1. 独立测试环境；记录基线。
2. 准备 token。
3. 持续对网关施压（`Threads=20`、`Duration=180s`）。
4. 依次做：`taskkill /F`（强杀）→ 观察；再 `java -jar` 重启 → 观察启动期与恢复。
5. 停止条件：错误长时间不恢复。

#### 【验证】
- **Nacos 控制台**：实例上下线时间与状态。
- **网关日志**：`503` / `Service Unavailable` / 路由到失效实例。
- **Feign**：重启期是否报错、恢复后是否自动恢复调用。
- **根因确认**：503 归因要落到**具体层**（注册/路由/健康/超时），**不能直接判定 Sentinel**。
- **恢复与回归（完整闭环）**：重启后确认 **Nacos 已注册 → 网关能路由 → 缓存/消费者/ShedLock 定时任务正常**（`ShedLockConfiguration`，`ysd-order-service/.../config/`）→ 业务接口正常。

---

### T20 Nacos 不可用

#### 【原理】
- **目的**：区分**服务发现故障**与**配置中心故障**（表现可能完全不同）。
- **源码依据**：各服务 Nacos 注册 `ysd-*.application.yml` `spring.cloud.nacos.discovery`；Sentinel 规则来自 Nacos（`ysd-order-service/application.yml:26-35`、`ysd-gateway/application.yaml:21-30`）。
- **预期**：已启动服务通常**仍能用缓存的注册表**处理已有请求；**重启**则可能拿不到配置/注册而失败；配置**更新**无法推送。
- ⚠️ **每次实验都要记录「实际加载的 Sentinel 规则」**，不能仅凭配置文件推断行为。

#### 【操作】
1. 独立测试环境；记录当前 Nacos 已加载的 Sentinel 规则（Dashboard 或 `/gateway/getRules`）。
2. 准备 token。
3. 停 Nacos（`shutdown.cmd` / `docker stop nacos`，仅测试环境）。
4. 分别测试：① 已启动服务能否继续处理请求；② 重启一个服务能否成功；③ 改一条 Nacos 配置能否生效；④ 恢复 Nacos 后注册/配置是否同步。
5. 停止与恢复：重启 Nacos，确认服务恢复注册。

#### 【验证】
- **服务发现**：停 Nacos 后，新实例注册失败、老实例能否被发现。
- **配置中心**：配置更新是否推送；Sentinel 规则是否仍为「停之前的版本」。
- **根因确认**：明确是「发现」还是「配置」故障。
- **恢复与回归**：Nacos 恢复 → 实例重新注册 → 配置同步 → **重新记录实际加载的 Sentinel 规则**并与停之前对比。

---

### T21 限流 + 熔断 + 重试叠加

#### 【原理】
- **目的**：逼近真实事故——**重试放大流量**，把「部分请求变慢」演变成「全链路拥塞」。
- **前提**：网关限 QPS（§6.2）；order 通过 Feign 调 catalog；catalog 变慢；上游**若配了重试**。
- ⚠️ **先确认是否真有重试**：Spring Cloud OpenFeign 默认 `Retryer.NEVER_RETRY`；`<!-- TODO: 待确认 -->` 仓库未见显式重试配置（`ysd-order-service/application.yml:36-43`）。**若默认不重试，则「重试放大」需先配置重试才能测**（属**建议补充的验证能力**）。
- **预期**：重试开启且下游慢时，调用次数被放大 → 线程/连接更快耗尽。

#### 【操作】
1. 独立测试环境；记录基线（调用次数、429/5xx/超时占比、线程池、DB 连接）。
2. 准备 token；准备「下游变慢」的注入（同 T18）。
3. 分别测：**只开网关限流** / **只模拟下游变慢** / **开熔断** / **（若已配重试）重试+熔断组合**。
4. 命令行执行，逐步叠加变量。
5. 停止条件：出现全链路拥塞 / 资源打满。

#### 【验证】
- **JMeter**：429、5xx、超时、**Feign 实际调用次数**（对比请求数）是否放大。
- **服务端/数据层**：线程池、DB 连接。
- **根因确认**：确认「重试 ×N」导致调用放大。
- **修复方向**：让**超时、重试次数、熔断策略、并发隔离互相匹配**；**非幂等写请求尤其谨慎重试**。
- **恢复与回归**：还原各阈值/配置 → 复测拥塞消失 → 业务数据正确。

---
<!-- 说明：不要把「所有阈值调高」当修复；那只是延后问题。 -->

## 7. 业务正确性与安全

> 本章学完你会：**不相信「HTTP 200」**——很多严重业务 Bug 返回的仍是 200。正确性必须靠**订单 / 购物车 / 数据库数据核对**来验证。
>
> ⚠️ **本章最重要的事实（据源码核实，勿臆测）**：**本项目没有库存字段、没有 Redisson、没有 Lua 脚本**（`docs/05-测试与压测/测试清单.md:105-108` 明确：技术点 4/5/6 无对应实现，STK 测试 `@Disabled`）。因此**经典的「并发扣库存 / 超卖」实验在本项目无法直接做**，必须按下方 T22 的方式改写。

### T22 并发扣库存 / 超卖（**本项目无库存，改为等价的数据正确性实验**）

#### 【原理】
- **目的**：验证并发写入下**数据不会重复/错乱**。
- **现状说明（关键）**：`drug / drug_combo / orders / shopping_cart` 等表**均无库存字段**（真实库转储 `docker/mysql/init/01-schema.sql` 全表结构可查）；源码中也无扣减逻辑。**超卖不是「已有能力的验证」，而是「建议补充的验证能力」。**
- **等价实验（本项目可做）**：`POST /user/shoppingCart/add` 的「先查后插」逻辑（`ShoppingCartServiceImpl.java:42-70`）在**并发**下可能产生**重复购物车行**：
  - 逻辑是 `list()` 查是否已存在 → 不存在才 `insert`（`:44-70`）——**典型的 check-then-act 竞态**。
  - 并发时多个请求可能都查到「不存在」，都走 insert → **同一用户同一商品出现多行**。
- **前置条件/风险**：独立测试环境；会产生数据，需定向清理（按 `user_id`）。
- **预期现象**：
  - 若最终 `shopping_cart` 出现**同一 (user_id, drug_id) 多行** → 并发正确性问题（等价于「超卖」的竞态本质）。
  - 若只有一行 → 该路径在该并发下安全（**但不代表任意并发都安全**，需多轮）。

#### 【操作】
**第 1 步：确认环境和数据**
- 独立测试环境；取真实 `user_id` 与 `drug_id`。
- **清空该用户购物车**（`DELETE /user/shoppingCart/clean`），确保从「不存在」开始。

**第 2 步：准备 token**
- 用户端 token（§1.4.2）。

**第 3 步：创建 JMeter 元件**
- Thread Group：`Threads=50`、`Ramp-up=1`、`Loop=1` + `Synchronizing Timer`（`Group=50`，制造「同时」）。
- HTTP Request：`POST /user/shoppingCart/add`，Body `{"drugId": {同一drugId}}`，Header `authentication`。

**第 4 步：执行**
- 先 1 线程调试 → 清空购物车 → 正式 `jmeter.bat -n -t t22.jmx -l t22.jtl -e -o t22-report`。

**第 5 步：停止与清理**
- 压完记录结果；用 `DELETE FROM shopping_cart WHERE user_id={uid} AND drug_id={did};` 定向清理（**只在测试库**）。

#### 【验证】
- **数据核对（核心）**：
  ```sql
  SELECT COUNT(*) FROM shopping_cart WHERE user_id = {uid} AND drug_id = {did};   -- 期望 1
  ```
- **JMeter**：成功请求数 vs 最终行数——**「成功次数」不等于「正确数据条数」**（`扩展建议`原话）。
- **根因确认**：多行 → 确认是 check-then-act 竞态（`ShoppingCartServiceImpl.java:42-70`）。
- **对比不同实现（建议补充，非现状）**：数据库条件更新 / 乐观锁版本号 / Redis 原子操作 / 分布式锁。⚠️ **不要预设分布式锁一定最优**——DB 条件更新可能更简单可靠，取决于权威存储与事务边界。
- **恢复与回归**：清理测试数据，核对 `COUNT(*)` 恢复。

---

### T23 重复提交与幂等

#### 【原理】
- **目的**：模拟「页面卡顿 / 网络超时导致连续提交两次」，验证**服务端**是否识别重复请求。
- **源码依据（真实风险点）**：`OrderServiceImpl.submitOrder:69-128` **无幂等键**；订单号用 `System.currentTimeMillis()`（`:98`）——**两次接近的提交会产生两个订单**，且无「请求唯一标识」判别。
- **前置条件/风险**：独立测试环境；会创建订单，需清理。
- **预期现象**：连续/并发提交 → **可能生成两个订单、两次扣（本项目无库存）/两次发消息**。
- ⚠️ **简单禁用前端按钮不能代替后端幂等**：请求可能来自重试、重复消息或其它客户端。

#### 【操作】
**第 1 步**：独立测试环境；取真实 `addressBookId`、用户 token。
**第 2 步**：准备购物车有数据（否则 `submitOrder` 抛「购物车为空」）。
**第 3 步**：建 HTTP Request `POST /user/order/submit`（body 见 T09），用**同一 body**。
**第 4 步**：用 `Synchronizing Timer` 让 **2~5 个线程同时**发同一请求（或客户端连续快速点两次）。
**第 5 步**：停止；核对订单表。

#### 【验证】
- **数据核对**：
  ```sql
  SELECT COUNT(*) FROM orders WHERE user_id = {uid} AND order_time > {开始时间};   -- 期望 1（若无幂等则为 N）
  ```
- **是否重复发消息**：MQ 是否出现多条同一订单广播；是否重复扣减。
- **客户端超时后重提**：服务端能否识别重复（现状：**不能**，无幂等键）。
- **根因确认**：结论 = 「无幂等键 / 无唯一约束 → 重复提交产生重复订单」。
- **修复方向（建议补充）**：幂等键、业务唯一约束、请求状态管理。
- **恢复与回归**：清理测试订单。

---

### T24 事务边界与并发更新

#### 【原理】
- **目的**：构造并发更新场景，验证**最终状态符合业务规则**、**无非法状态跳转**、**可恢复**。
- **源码依据**：`orders` 状态流转 `OrderServiceImpl`（`confirm:408-415`、`rejection:422-455`、`cancel:462-489`、`userCancelById:263-298`、`complete:518-534`）——**有状态校验但无乐观锁/版本号**；购物车 `updateNumberById`（`ShoppingCartServiceImpl.java:44-48`）是**读后写**（可能丢更新）。
- **场景表**：

  | 场景 | 应验证的结果 | 本项目关注点 |
  |---|---|---|
  | 两用户同时改同一购物车记录 | 最终数量符合业务规则 | 读后写丢更新 |
  | 订单更新与取消同时发生 | 状态**不能非法跳转** | 状态校验是否够 |
  | DB 更新成功后缓存删除失败 | 有可验证的修复路径 | 延迟双删失败只 WARN（`CatalogCacheHelper.java:197`） |
  | 消费者重复处理订单消息 | 不重复扣减/发放 | **本项目无此消费者**（广播） |
  | 请求处理中服务崩溃 | 重启后状态可解释、可恢复 | 事务 + ShedLock |

- ⚠️ **不是所有问题都适合只用 JMeter 测**：涉及**精确时序**的，最好用**程序化并发测试**（JUnit + `CountDownLatch` + 线程池）控制执行顺序。
- **前置条件/风险**：独立测试环境。

#### 【操作】
**第 1 步**：独立测试环境；准备一个待处理订单与购物车数据。
**第 2 步**：准备 token。
**第 3 步**：写**程序化并发测试**（推荐）或用 JMeter 并发触发两个不同接口：
  - 线程组 A：`PUT /user/order/cancel/{id}`；
  - 线程组 B：管理端 `POST /admin/order/confirm`（`ysd-order-service/.../controller/admin/OrderController.java`）。
**第 4 步**：用 `CountDownLatch` 让两请求尽量同时；重复多轮。
**第 5 步**：停止；核对 `orders.status` 是否出现非法值。

#### 【验证】
- **数据核对**：`SELECT id, status, cancel_time, pay_status FROM orders WHERE id={id};` 是否为合法状态之一。
- **购物车**：并发 `updateNumberById` 后数量是否符合规则。
- **缓存删除失败**：断点/注入让 `evictByPattern` 抛错，确认是否只 WARN（`CatalogCacheHelper.java:196-198`）→ 留下旧值窗口 → 下次写或过期后自愈。
- **根因确认**：区分「无乐观锁导致丢更新」与「校验漏洞导致跳转」。
- **恢复与回归**：还原订单状态；确认接口正常。

---

### T25 参数异常与边界值

#### 【原理】
- **目的**：确认服务端**有参数校验与业务规则检查**，异常输入被**合理拒绝**，而不是 `500` / SQL 异常 / **部分修改**。
- **覆盖**：必填缺失、ID 不存在、数量为 0/负数/极大值、超长字符串、非法 JSON、分页异常、不支持状态值、价格精度/金额边界、空数组、重复数组元素。
- **前置条件/风险**：独立测试环境；异常输入不应产生脏数据。

#### 【操作】
1. 独立测试环境；准备 token。
2. 准备一张「异常用例表」，逐条构造请求。
3. 用 JMeter 的 `CSV Data Set Config` 或 Postman 逐条发（**低并发**，目的是看响应与副作用，不是压吞吐）。
4. 每条都**同时**记录：HTTP 状态码、body `code/msg`、**是否改了数据**。
5. 停止：出现 `500` 或数据被非预期修改即标记缺陷。

#### 【验证】
- **响应**：应为业务错误（`code != 1`）或 4xx，**不应**是未捕获的 `500`。
- **日志**：`GlobalExceptionHandler`（`ysd-order-service/.../handler/GlobalExceptionHandler.java`）是否兜住。
- **数据副作用**：`INSERT/UPDATE` 计数是否变化（失败请求**不应**改数据）。
- **根因确认**：区分「缺校验」与「校验后仍写了部分数据」。
- **恢复与回归**：清理被误改的数据。

> ⚠️ 对订单、价格等关键字段，**不要只依赖前端校验**；服务端应有参数校验 + 业务规则 + DB 约束。

---

### T26 鉴权边界

#### 【原理】
- **目的**：验证**鉴权**（有没有 token）之外，还要验证**授权/越权**（能不能访问**别人的**数据）。
- **源码依据（真实越权候选）**：`GET /user/order/orderDetail/{id}`（`OrderController.java:75-80`）→ `OrderServiceImpl.details(id):244-257`：`orderMapper.getById(id)` **未校验该订单是否属于当前用户** → **用户 A 用自己合法 token 可读用户 B 的订单**（IDOR / 水平越权）。**这是需要实测确认并记录的安全问题。**
- **header 规则**见 §1.4。
- **测试项**：无 token / 过期 / 签名错 / 管理端 token 打用户端接口 / 用户端 token 打管理端接口 / 用户 A 访问用户 B 数据 / 伪造身份头 / 直接访问内部接口。
- **预期**：**鉴权成功 ≠ 权限正确**。越权访问**应被拒绝**。

#### 【操作】
1. 独立测试环境；准备两个不同用户的合法 token（用户 A、B）。
2. 逐条测试：

   | 用例 | 预期 |
   |---|---|
   | 无 token 访问 `/user/order/historyOrders` | 401 |
   | 过期 token | 401 |
   | 篡改签名 token | 401 |
   | `token`(admin) 访问 `/user/...` | 401（用户端只认 `authentication`） |
   | `authentication`(user) 访问 `/admin/...` | 401 |
   | 用户 A 的 token 访问 `GET /user/order/orderDetail/{B的orderId}` | **应拒绝；现状可能返回 B 的数据**（记录证据） |
   | 伪造 `X-Identity-Id` 头 | 无效（网关剥离，`AuthGlobalFilter.java:52-56`） |
   | 直接访问 `/inner/drug/1` | **403**（`AuthGlobalFilter.java:63-65`） |
   | 直接访问 `/actuator/...` | 403（`ActuatorBlockWebFilter.java:17`） |
3. 低并发执行；逐条记录。
4. 停止：出现越权读/写即记为缺陷。

#### 【验证】
- **响应**：401/403 vs 200(越权)。
- **数据**：越权请求是否真的返回了他人数据。
- **根因确认**：**以权限规则为基准**，而不是只检查「是否 401」。
- **恢复与回归**：无需改数据；把越权用例固化为回归用例。

---

### T27 异常输入资源消耗

#### 【原理】
- **目的**：确认系统对「超长参数 / 异常分页 / 超大请求体 / 过多筛选条件」能**合理拒绝**，而不是大量内存分配 / 全表扫描 / 线程阻塞。
- **前置条件/风险**：**必须设输入上限**，避免在测试环境制造不必要的资源耗尽（不要真把服务打挂）。
- **预期**：超出上限的输入被**快速拒绝**（4xx），资源占用可观测且可控。

#### 【操作】
1. 独立测试环境；准备 token。
2. 构造受控的超限输入（如：分页 `pageSize=1000000`、超长 `name`（如 10 万字符）、超大 JSON body、`IN` 列表上千项）。
3. **低并发**逐条发；同时监控 DB 与 JVM。
4. **明确上限**：例如 body ≤ 1MB、`pageSize` ≤ 100——**超过即视为异常输入**。
5. 停止：资源（连接/内存/CPU）异常飙升即停止。

#### 【验证】
- **响应**：是否为 4xx，而非长时间占用。
- **数据层**：是否触发全表扫描（`EXPLAIN` / `Slow_queries`）。
- **服务端**：内存/线程是否异常。
- **根因确认**：确认「无输入上限」导致资源被放大。
- **恢复与回归**：确认服务资源回落。

---

## 8. JVM、线程池、OS 资源

> 本章学完你会：在**没有现成监控端点**的情况下，用 JDK 自带工具定位「接口越来越慢」的 JVM / 线程 / 磁盘根因。
>
> ⚠️ **本项目现状（据源码核实）**：
> - **没有可用的 actuator 指标端点**（§1.7）：`management.endpoints.web.exposure` **未配置** → 默认只有 `/actuator/health`；网关还会 403 掉 `/actuator/**`。**因此 JVM / 线程 / 连接池指标要靠进程级工具**：`jcmd / jstat / jstack / jmap`。
> - **没有自定义业务线程池**：全仓 grep `ThreadPoolExecutor/Executors/@Async` 仅命中 `CatalogCacheHelper`（延迟删除的 **2 线程** daemon 调度器，`CatalogCacheHelper.java:62-68`）与测试类。业务侧真正会「耗尽」的是 **Tomcat 请求线程池（默认 max=200）** 与 Spring 默认 `@Scheduled` 调度线程（**默认 1 个**，`OrderTask` 用 `@Scheduled` + ShedLock，`ysd-order-service/.../task/OrderTask.java:24-26,45-46`）。
> - **没有 logback 配置**（仓库无 `logback*.xml`）→ 日志走 Spring Boot 默认（console）；**是否写文件、是否轮转取决于部署方式的输出重定向**。

### T28 内存与 GC

#### 【原理】
- **目的**：持续负载下记录堆 / 老年代 / GC 趋势，判断是否**内存泄漏**（GC 后不回落）。
- **预期**：正常长跑下，GC 后老年代应**回落到稳态**；P99 不应与 Full GC 暂停同步上升。
- **防误判**：**不要仅凭一次堆内存较高就认定内存泄漏**——要看**多次 GC 后的趋势**，再结合堆转储与对象引用分析。

#### 【操作】
**第 1 步**：独立测试环境；找到目标服务 pid：`jps -l`。
**第 2 步**：记录基线：`jcmd <pid> GC.heap_info`、`jstat -gcutil <pid> 1000 5`。
**第 3 步**：对目标接口施压（同 T04）。
**第 4 步**：持续采集：
```bash
jcmd <pid> GC.heap_info
jstat -gcutil <pid> 1000          # 每秒一次，看 O(老年代) 与 FGC
# 需要时导出堆（会 STW，慎用，仅在独立测试环境）：
jcmd <pid> GC.heap_dump /path/heap.hprof
```
**第 5 步**：停止条件：老年代持续增长且 **GC 后不回落**、Full GC 频繁 → 停止并分析堆转储。

#### 【验证】
- **JVM**：`jstat -gcutil` 的 `O`（老年代 %）、`FGC`（Full GC 次数）、`FGCT`（暂停时间）趋势。
- **JMeter**：P99 是否与 GC 暂停**同时上升**。
- **根因确认**：区分「内存泄漏（GC 后不回）」与「正常缓存占用」；用 `jmap -histo` / 堆转储找增长对象。
- **恢复与回归**：停止压测后资源回落；若泄漏则记录为待修。

---

### T29 线程池耗尽

#### 【原理】
- **目的**：把「线程被占满」当作**独立故障场景**验证，而不是只从接口延迟间接观察。
- **本项目可被耗尽的池**：**Tomcat 请求线程**（默认 max=200）。典型触发是 T18——某 Feign 调用持续等待下游，占满业务线程，连不依赖该下游的请求也受影响。
- **前置条件/风险**：独立测试环境。
- **预期**：活跃线程逼近上限 → 请求排队 → 新请求超时/被拒绝。

#### 【操作】
**第 1 步**：独立测试环境；`jps -l` 取 pid。
**第 2 步**：记录基线线程数：`jstack <pid> | grep -c "http-nio"`（Tomcat 线程数）。
**第 3 步**：制造线程占用——复用 T18 的「下游变慢」注入。
**第 4 步**：施压 + 观察：
```bash
jstack <pid> | grep -E "http-nio|BLOCKED|WAITING" | sort | uniq -c | sort -rn
```
**第 5 步**：停止条件：活跃线程 ≈ 上限、大量请求排队 → 停止并去掉延迟注入。

#### 【验证】
- **JVM**：`jstack` 的线程状态分布（多少 BLOCKED/WAITING 在 Feign/Socket）。
- **JMeter**：健康接口是否也变慢（线程耗尽的标志）。
- **数据层**：连接池是否被连带占满（T06）。
- **根因确认**：确认是「下游慢 + 无超时/无并发隔离」占满线程，而非 CPU 打满。
- **恢复与回归**：去掉延迟 → 线程数回落 → 健康接口恢复。
- **相关测试**：`ysd-order-service/src/test/java/com/ysd/pool/OrderTimeoutThreadPoolTest.java`（存在，可作为线程池行为的参考测试）。

---

### T30 磁盘与日志

#### 【原理】
- **目的**：确认日志不会**无限增长占满磁盘**，且异常不重复刷屏。
- **本项目现状**：无 `logback.xml`；日志级别与格式见各服务 `application.yml` `logging.*`（含 traceId，`ysd-order-service/application.yml:89-93`）。**是否落盘、是否轮转取决于部署方式**。
- **前置条件/风险**：⚠️ **不要真的把磁盘写满**。先检查轮转与告警配置，再在**隔离环境**做**受控**的磁盘空间不足实验。
- **预期**：持续运行下日志增长可控；轮转正常；服务崩溃能被 systemd 按预期重启（本项目使用 systemd **以实际部署为准**）。

#### 【操作】
1. 独立测试环境；确认服务的日志输出目标（console / 文件 / systemd journal）。
2. 持续压测（同 T04）并观察日志文件大小增速：
   ```bash
   # 若以文件输出（示例路径以实际为准）
   ls -lh <log-dir> ; tail -f <log-file> | wc -l
   ```
3. 观察是否有**异常重复打印**（同一异常每秒刷屏）。
4. **受控**磁盘实验（可选，隔离环境）：用一个小容量挂载点或 `fallocate` 造软限制，**不要**动系统盘。
5. 停止：日志增速异常 / 磁盘告警 → 停止。

#### 【验证】
- **系统**：日志大小增速、是否轮转、剩余空间。
- **服务端**：异常是否被限流（重复打印）。
- **恢复与回归**：服务重启后日志/临时文件正常（§恢复闭环：重启不只「起得来」，还要状态可恢复）。
- **根因确认**：区分「日志级别过低刷屏」与「异常风暴」。

---
<!-- 说明：不要真把磁盘写满作为第一步；先查轮转与告警配置。 -->

## 9. 排障方法论（核心章节）

> 本章学完你会：把「一次压测/故障演练」变成可复用的**排障闭环**，并知道线上出问题时**先看什么、怎么缩小范围、怎么确认根因**。

### 9.1 五步闭环

| 步骤 | 做什么 | 关键要求 |
|---|---|---|
| ① 记基线 | 记录正常时 P95/P99、吞吐、错误率、CPU、JVM、DB、Redis 指标 | **没有基线，后续无法判断「是否改善」** |
| ② 只造一种故障 | 例如仅让 catalog 变慢，或仅让 Redis 不可用 | **一次只改一个变量**，否则无法归因 |
| ③ 收证据缩范围 | 结合错误日志、Trace、线程栈、连接池、慢 SQL、资源指标 | 判断瓶颈在**哪一层** |
| ④ 修复并重测 | 记录改了什么、为什么改，**用相同负载与故障条件重测** | 不是「随便再压一次」 |
| ⑤ 确认恢复与回归 | 性能是否改善、业务数据是否正确、服务是否恢复、旧功能是否受影响 | 三者都要确认 |

### 9.2 线上速查表

| 线上现象 | 第一批检查对象 | 下一步 |
|---|---|---|
| P95/P99 突然上升 | CPU、线程池、连接池、Trace | 找耗时最长的调用和资源等待 |
| 错误率突然升高 | 错误码分布、应用日志、网关日志 | 区分业务错误 / 超时 / 限流 / 服务不可用 |
| CPU 很高 | 线程栈、GC、热点方法、SQL 负载 | 判断是计算密集、频繁 GC 还是资源争用 |
| CPU 不高但请求卡住 | 线程栈、连接池、锁等待、网络调用 | **查阻塞和等待，不要只盯 CPU** |
| 数据库负载升高 | 慢 SQL、执行计划、连接数、锁等待 | 先定位最耗资源的查询或事务 |
| Redis 恢复后仍然很慢 | 缓存命中率、回源流量、数据库连接池 | 检查**缓存重建是否冲垮数据库** |
| MQ 积压越来越多 | 生产速率、消费速率、消费错误 | 定位消费瓶颈和失败重试 |
| 重启后数据异常 | 事务、消息、缓存、启动顺序 | 检查重复执行、状态恢复与一致性 |
| 只有部分用户失败 | 用户权限、请求参数、特定数据、路由 | 比较**成功与失败请求的差异** |

### 9.3 从一个具体请求入手（最重要的技巧）

排查时**始终从一个具体请求开始**，拿到四要素：**请求时间 + 接口 + 错误码 + traceId**，再沿链路向下追：
> 网关（`ysd-gateway`）→ 业务服务（order/catalog/…）→ Feign 调用 → 数据库 / Redis / MQ。

本项目接了 Zipkin（`http://localhost:9411`），可把同一 `traceId` 的 Trace 与各服务日志、DB 指标、JMeter 时间曲线**对齐看**。日志格式已带 traceId：`logging.pattern.level: ... %X{traceId:-} %X{spanId:-}`（`ysd-order-service/application.yml:89-93`）。

### 9.4 Trace 的边界

- Trace 只能显示**被正确埋点**的调用及其耗时，**不能自动给出所有根因**。
- 例：**数据库连接池等待**若没有对应 Span / 指标，Trace 上看不出来 → 必须结合**连接池指标 + 线程栈（jstack）+ DB 状态**判断。
- 经**未接入追踪**的路径（如历史上经 `ysd-server` 的请求）本就断链。`<!-- TODO: 待确认 -->` 当前是否仍有断链路径（`docs/09-项目记录/遗留事项总表.md` P3）。

### 9.5 区分「改善」与「根因」

| 说法 | 含义 | 是否可当根因 |
|---|---|---|
| 症状消失 | 现象不再出现（可能只是**绕过**） | ❌ |
| 性能改善 | 指标变好（可能只是**缓解**） | ❌（除非有机制解释） |
| 根因已确认 | 有**机制 + 证据**解释「为什么」 | ✅ |

> 例：「调大连接池后接口变快了」只说明**这个改动改善了当时表现**；还要确认原先是否真存在连接等待、DB 是否有足够并发能力、池调大后是否**产生新副作用**。证据不足时，明确写「**尚未确认**」。

---

## 10. 7 天实战清单

> **最建议先做的一项**：**T06 MySQL 连接池饱和 + T07 慢 SQL 定位**。它一次锻炼 JMeter、Spring Boot、数据库监控、日志分析和性能判断，且最容易延伸到真实工作。
> 清单可勾选；每项标注对应 T 编号。**不必 7 天内把所有底层原理研究透**，先形成可重复的实验与排障流程，每个实验先做 1~2 小时的最小闭环。

**阶段 1 · 性能基线（第 1 天）**
- [ ] 记录 3~4 个核心接口的正常响应时间、吞吐量和错误率（T01）
- [ ] 做阶梯加压，记录性能拐点（T02）
- [ ] 确认 JMeter 自身没有成为瓶颈（T05）

**阶段 2 · MySQL 与 Redis（第 2~3 天）**
- [ ] 模拟连接池饱和，练习定位连接等待（T06）⭐ **优先**
- [ ] 通过 `EXPLAIN` 分析一个真实业务查询（T07）⭐ **优先**
- [ ] 测试热点 Key 过期和 Redis 不可用（T10 / T11 / T12）
- [ ] 验证延迟双删在两个 catalog 实例下的表现（T13）

**阶段 3 · MQ 与微服务故障（第 4~5 天）**
- [ ] 停止 push 消费者，观察消息行为与恢复（T14；**注意本项目为广播，消息会被丢弃而非堆积**）
- [ ] 验证重复消费与业务幂等性（T15 → 落到 T23）
- [ ] 测试 Feign 下游变慢、超时与熔断（T18 / §6.1）
- [ ] 验证服务重启和 Nacos 恢复后的行为（T19 / T20）

**阶段 4 · 业务正确性（第 6 天）**
- [ ] 并发写核对应最终数据状态（T22；**无库存，用加购/下单替代**）
- [ ] 重复提交下单请求，检查幂等性（T23）
- [ ] 测试事务失败、缓存删除失败和消息发送失败（T09 / T24 / T16）

**阶段 5 · 稳定性与排障复盘（第 7 天）**
- [ ] 运行 30~60 分钟稳定性测试（T04）
- [ ] 检查 JVM、线程池、连接池和磁盘指标（T28 / T29 / T30）
- [ ] 选一个故障，完成**从复现、定位、修复到回归**的完整记录（第 11 章）

---

## 11. 故障记录模板

> 建议在项目中创建 `docs/testing/incident-drills/` 目录，把测试结果与修复过程积累下来。每次测试都写一份**故障记录**，而不是只有一张 JMeter 截图。

```markdown
# 故障测试与排障记录

## 1. 基本信息
- 测试名称：
- 测试日期：
- 测试环境：
- 涉及服务：
- 代码版本 / Git Commit：
- 测试数据规模：

## 2. 故障假设
- 我怀疑的问题是什么：
- 为什么怀疑：
- 预计会出现什么现象：
- 哪些指标可以证明或否定这个假设：

## 3. 测试过程
- 初始状态：
- 负载设置：
- 故障注入方式：
- 复现步骤：
- 实际现象：

## 4. 测试结果
- 成功请求数 / 失败请求数：
- TPS：
- P50 / P95 / P99：
- JVM / CPU / 内存：
- MySQL / Redis / RabbitMQ 指标：
- 关键日志与 traceId：

## 5. 根因分析
- 直接原因：
- 根本原因：
- 排除了哪些其他可能：
- 支持结论的证据：

## 6. 修复方案
- 修改了什么：
- 为什么采用这个方案：
- 可能引入哪些副作用：
- 是否需要数据修复或补偿：

## 7. 回归验证
- 相同条件下是否复现：
- 修复后指标变化：
- 业务数据是否正确：
- 服务是否恢复：
- 是否存在新的问题：

## 8. 结论与后续行动
- 本次验证的结论：
- 尚未验证的边界：
- 后续需要补充的监控、告警或自动化测试：
```

> ⚠️ **最值得坚持的是第 5 段：不要把「修改后正常了」直接写成根因。** 例：「调大连接池后接口变快了」只说明该改动改善了当时表现；还要确认先前是否真存在连接等待、DB 是否有足够并发能力、池增大后是否产生新副作用——证据不足就写「**尚未确认**」。

---

## 附录

### 附录 A · 来源索引（全文主要依据）

| 结论 | 来源（仓库相对路径:行号） |
|---|---|
| 网关白名单 / token header / 身份头剥离 / `/inner` 403 | `ysd-gateway/src/main/java/com/ysd/filter/AuthGlobalFilter.java:39-83,52-56,63-65` |
| 网关路由 / 网关限流数据源（**无兜底路由**） | `ysd-gateway/src/main/resources/application.yaml:21-70` |
| 网关拦截 `/actuator/**` | `ysd-gateway/src/main/java/com/ysd/filter/ActuatorBlockWebFilter.java:17` |
| 药品列表走缓存 / N+1 回源 | `ysd-catalog-service/.../service/impl/DrugServiceImpl.java:208-228`（N+1 在 `:220`） |
| 更新药品 = 延迟双删 | `ysd-catalog-service/.../controller/admin/DrugController.java:88-94`、`DrugServiceImpl.java:148-173` |
| 缓存 key / TTL / 空值 / 本地锁 / 延迟删除 | `ysd-catalog-service/.../cache/CatalogCacheHelper.java:35-51,91-113,132-140,162-164,177-186,192-211` |
| 加购触发 `CatalogClient`（**非订单详情**） | `ysd-order-service/.../service/impl/ShoppingCartServiceImpl.java:55,62`、`OrderServiceImpl.java:244-257` |
| Feign 契约 / fallback | `ysd-api/.../client/CatalogClient.java:17-32`、`.../client/fallback/CatalogClientFallbackFactory.java:23-35` |
| Feign 熔断开关 | `ysd-order-service/src/main/resources/application.yml:36-39` |
| 下单事务边界（无库存 / 事务内无 MQ） | `ysd-order-service/.../service/impl/OrderServiceImpl.java:69-128` |
| `paySuccess`：DB 更新 + 发 MQ **非同事务** | `OrderServiceImpl.java:175-199` |
| MQ：fanout / 匿名队列 / 自动 ack / 无 DLQ | `ysd-push-service/.../config/RabbitConfig.java:22,25-37`、`.../mq/WsPushListener.java:12,24-28`、`ysd-order-service/.../mq/WsPushProducer.java:17,25-27` |
| 店铺状态读 Redis `SHOP_STATUS`（get 未兜异常） | `ysd-catalog-service/.../controller/user/ShopController.java:30,40-44,51` |
| 真实库结构（`drug`/`orders` **仅主键，无二级索引**） | `docker/mysql/init/01-schema.sql:93-107,219-245` |
| 仓库无建表/建索引脚本 | `ysd-catalog-service/src/test/resources/sql/schema-h2.sql:4-9` |
| **无库存 / 无 Redisson / 无 Lua** | `docs/05-测试与压测/测试清单.md:105-108` |
| 数据源为 **Druid**（非 Hikari） | `pom.xml:31,88-92`；`ysd-catalog-service/application.yml:16-21` |
| 订单超时定时任务 + ShedLock | `ysd-order-service/.../task/OrderTask.java:24-26,45-46` |
| 唯一非 Tomcat 的业务调度器（延迟删除，2 线程） | `ysd-catalog-service/.../cache/CatalogCacheHelper.java:62-68` |
| 越权候选：订单详情未校验归属 | `ysd-order-service/.../service/impl/OrderServiceImpl.java:244-257` |
| 加购 check-then-act 竞态 | `ysd-order-service/.../service/impl/ShoppingCartServiceImpl.java:42-70` |
| Sentinel 规则位置 / 坑（grade=1、count 抖动） | `docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel改造-准备.md`、`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md` |

### 附录 B · 常见问题排查表

| 现象 | 可能原因 | 排查 |
|---|---|---|
| 压测大量 **401** | token 问题 | ① header 名对不对（`/admin/**`→`token`，其余→`authentication`）；② token 是否过期；③ 手写 token 是否用了 `SHA-256("<JWT_ADMIN_SECRET>")` 而非明文（`JwtUtil.java:25-33`）；④ 看网关日志 `请求缺少 token`/`JWT 校验失败`（`AuthGlobalFilter.java:87,113`） |
| 压测大量 **403** | 打了内网 / actuator | `/inner/**` 与 `/actuator/**` 一律 403（`AuthGlobalFilter.java:63-65`、`ActuatorBlockWebFilter.java:17`）——**压测不要打这两个前缀** |
| 压测大量 **503** | 服务未注册 / 路由不匹配 / 实例不可用 | ① Nacos 服务列表是否有目标服务；② 路由 `Path` 是否匹配（`ysd-gateway/application.yaml:34-70`）；③ 目标服务是否真的起了（8081~8086）；④ **注意**：v1 提到的 `/** → ysd-server` 兜底路由**已移除**，请求打不到路由会 404/503 而非被带偏 |
| **响应慢但无错误** | Sentinel 限流/熔断，或缓存全 miss，或连接池等待 | ① 网关 `blockQps` 是否 >0；② order 熔断器是否 OPEN；③ `redis-cli -n 10 info stats` 看命中率；④ `SHOW PROCESSLIST` / Druid 池指标看连接等待 |
| **P95/P99 长尾**（DB 侧） | 慢 SQL / 锁等待 / 连接不足 | `EXPLAIN ANALYZE`、`SHOW ENGINE INNODB STATUS`、`Slow_queries`、Druid `WaitThreadCount`（§3） |
| **MQ 停止消费者后没有堆积** | 本项目用**匿名 exclusive 队列**，实例下线即删队列 → 消息被丢弃 | 这是**预期**行为，不是 Bug；可靠堆积需改持久化队列 + 手动 ack（§5） |
| **消息重复/丢失** | 广播 + 自动 ack + 无 DLQ + 生产端无 confirms | 本项目消费端无业务写入；可靠性属**建议补充**（§5 T16/T17） |
| **CPU 不高但请求卡住** | 线程/连接/锁等待 | `jstack` 看阻塞在线程在哪一层（Feign/Socket/DB）；`SHOW PROCESSLIST`（§8 T29、§9） |
| **JVM 内存高** | 可能是正常缓存占用 | 用 `jstat -gcutil` 看**多次 GC 后**趋势，而非单次读数（§8 T28） |
| **trace 断链** | observation 未开 / 经未追踪路径 | ① order 生产端需 `spring.rabbitmq.template.observation-enabled: true`（已开，`ysd-order-service/application.yml:56-59`）；② push 消费端需 `listener.simple.observation-enabled: true`（已开，`ysd-push-service/application.yml:14-17`）；③ `<!-- TODO: 待确认 -->` 是否仍有经未追踪路径的断链（`docs/09-项目记录/遗留事项总表.md` P3） |

### 附录 C · 环境分层表

| 环境 | 主要用途 | 允许 | 禁止 |
|---|---|---|---|
| **本地环境** | 快速复现、单元/并发测试、事务与边界实验 | 改代码验证（**验证后还原**）、单实例压测、定向清缓存 | 把本地结果当容量结论；连生产库 |
| **独立测试环境** | JMeter 压测、故障注入、服务重启、MQ 积压、批量造数、连接池调低复现 | 上述全部（**带停止条件与恢复步骤**） | 无停止条件盲目加压；影响他人共享环境 |
| **生产环境** | 观测与诊断 | 看日志 / Trace / DB / Redis / MQ 指标；限制内的只读查询 | **停服务、删缓存、锁表、批量删除、故障注入、压力冲击、关闭鉴权/限流** |

> 生产若确需容量验证，须先设定**负载上限、停止条件、监控与回滚方案**并获授权。**未经授权，本节不得执行任何破坏性操作。**

### 附录 D · 待确认清单（全文 `<!-- TODO: 待确认 -->` 汇总）

| # | 待确认项 | 位置 | 确认方式 |
|---|---|---|---|
| D1 | 用户端 token 是否需 `Bearer` 前缀 | §1.4.2 | 读网关 `AuthGlobalFilter.java:82,99`（当前按整串解析，未见前缀剥离）；用脚本 token 实测 |
| D2 | 真实 `drug` 是否含 `prescription_*` 列 | §1.5、§3 T07 | `DESC drug;`；工作区 `DrugMapper.xml:7-12` 已引用、但转储无该列（未提交改动） |
| D3 | `OrderSubmitRollbackTest` 是否已落地 | §3 T09 | `docs/05-测试与压测/测试清单.md:75-77` 为规划；工作区未见该类 |
| D4 | 是否仍有 trace 断链路径 | §9.4、附录 B | 压测叠 Trace，看 gateway→service→MQ 是否同 traceId |
| D5 | Feign 连接/读取超时是否配置 | §6 T18 | 仓库未见 `openfeign.client.config` 超时项（`application.yml:36-43` 仅 loggerLevel）→ 实测 |
| D6 | Feign 是否启用重试 | §6 T21 | 默认 `Retryer.NEVER_RETRY`；未见显式配置 → 实测调用次数 |
| D7 | Nacos 中 Sentinel 限流/降级规则的真实阈值 | §6.1/§6.2 | 打开 Nacos 对应 dataId 读取 `count/grade` |
| D8 | 是否启用 RabbitMQ 管理插件（15672） | §5 T14 | 访问 `http://localhost:15672` 或用 `rabbitmqctl` |
| D9 | 生产日志是否落盘/轮转 | §8 T30 | 查看部署方式与 systemd 输出重定向；仓库无 logback 配置 |
| D10 | 生产库 `orders/drug` 当前索引 | §3 T07 | 现场 `SHOW INDEX FROM orders;`（转储显示仅主键） |
| D11 | 是否部署了 systemd 服务管理 | §1.1、§8 T30 | 由部署环境确认（`docs/04-运维与故障排查/环境变量配置说明.md` 为准） |
| D12 | 真实库管理员账号密码 | §1.4.1 | 种子数据为 admin/<your-password>，**真实库以现场为准** |

### 附录 E · JMeter 菜单速查

| 想加什么 | 右键哪里 | 菜单路径 |
|---|---|---|
| 线程组 | Test Plan | `Add → Threads (Users) → Thread Group` |
| HTTP 请求 | Thread Group | `Add → Sampler → HTTP Request` |
| 统一 host/port | Thread Group | `Add → Config Element → HTTP Request Defaults` |
| 请求头 | Thread Group 或 HTTP Request | `Add → Config Element → HTTP Header Manager` |
| CSV 参数 | Thread Group | `Add → Config Element → CSV Data Set Config` |
| 提取响应字段 | HTTP Request | `Add → Post Processors → JSON Extractor` |
| 断言字段值 | HTTP Request | `Add → Assertions → JSON Assertion` |
| 查看结果树 | Thread Group | `Add → Listener → View Results Tree` |
| 聚合报告 | Thread Group | `Add → Listener → Aggregate Report` |
| 定时器（集合点） | Thread Group | `Add → Timer → Synchronizing Timer` |

### 附录 F · 完整元件树示例

```text
Test Plan
└── Thread Group (50 / 10 / 100)
    ├── HTTP Request Defaults (localhost:9000)
    ├── HTTP Header Manager (authentication: <USER_TOKEN>)
    ├── HTTP Request (GET /user/drug/list?categoryId={真实分类id})
    └── Aggregate Report
```
> 以后所有计划都是这个骨架的变体。

### 附录 G · 统一验收清单（交付前逐项核对）

- [ ] 章节、测试编号（T01~T30）与附录 A~G 完整。
- [ ] 所有计划中的测试均有【原理】【操作】【验证】三段。
- [ ] 所有测试都有前置条件、可执行步骤、预期现象和故障排查路径。
- [ ] 所有需要源码依据的内容均有可核验的 `文件:行号`，或明确标记 `<!-- TODO: 待确认 -->`。
- [ ] 所有示例参数均标注「示例 / 以实际为准」。
- [ ] 所有有风险的实验均包含停止、恢复和回归验证。
- [ ] 所有 Redis 清理命令均带 `-n 10`，且只清理经核实的目标前缀（无 `FLUSHDB/FLUSHALL`、无 `KEYS`）。
- [ ] 所有正式高负载测试均使用非 GUI 模式，不使用 `View Results Tree`。
- [ ] 附录 A~F、7 天实战清单、故障记录模板、相对 v1 的变更说明均已核对。
- [ ] 文末「未完成事项清单」区分「已据源码核实」与「需用户在真实环境确认」。

---

## 相对 v1 的变更说明

### 第一批（本批）

**新增**
- 全文改为 **【原理】→【操作】→【验证】** 三段式；新增**第 1.5 章 JMeter 零基础操作手册**；新增**附录 E（菜单速查）/ F（元件树）**。
- 第 0 章：新增**测试地图**（五类 × v1 覆盖）与 **T01~T30 测试索引**，并给出 v1 五个「计划」的保留对照。
- 第 1 章：新增**环境分层**（本地 / 独立测试 / 生产）；新增**可观测性现状**（Actuator 仅 health、无 Druid 监控页）。
- 第 3 章：**全新**（T06~T09）。
- 第 9 章：**全新**排障方法论（五步闭环 + 速查表 + 从请求入手 + Trace 边界 + 区分改善与根因）。

**修正（相对 v1 与《扩展建议》）**
- **连接池是 Druid，不是 HikariCP**：v1 / 扩展建议按 Hikari 描述，v2 改为 Druid（`pom.xml:31,88-92`）。
- **下单流程**：删去「扣库存」「事务内发 MQ」的假设；改为真实的 地址簿(Feign)→购物车→订单→明细→清车 五步；MQ 推送实际在 `paySuccess`（无事务）。
- **无库存能力**：明确本项目**无库存字段 / 无 Redisson / 无 Lua**（`docs/05-测试与压测/测试清单.md:105-108`），为 T22 的改写埋下依据。
- **真实库无二级索引**：据真实转储 `docker/mysql/init/01-schema.sql:105-107,244` 说明 `drug/orders` 缺索引，纠正 v1 对 `categoryId=1` 的硬编码（真实分类是 11/12/16…）。
- **网关兜底路由已移除**：v1 §7 提到的 `/** → ysd-server` 兜底路由**现已不存在**（`ysd-gateway/application.yaml:31-70` 无该路由），v2 更正。
- **JWT 生成**：不再引导去 jwt.io；改为**本地纯标准库 Python 脚本**，并强调密钥处理与核实步骤。

**保留未改**
- Redis db10、`-n 10` 原则；v1 计划 1/2/3 的缓存观测口径；不编造压测结果；`View Results Tree` 禁用原则。

**未完成项（本批）**
- 第 2、4、5、6、7、8、10、11 章与附录 A~D、G 为占位，待后续批次。
- `<!-- TODO: 待确认 -->` 项：用户端 token 是否需 `Bearer` 前缀；`drug` 真实列是否含 `prescription_*`（工作区未提交改动）；`OrderSubmitRollbackTest` 是否落地；当前是否仍有断链路径。

### 第二批：第 2 章 + 第 4 章

**新增**
- 第 2 章性能与容量：T01 基准 / T02 阶梯 / T03 突发 / T04 长稳 / T05 JMeter 自身瓶颈（全新）。
- 第 4 章 Redis：**保留 v1 计划1/2/3**（命中 / 穿透 / 延迟双删单实例）并三段化；**新增** T10 缓存击穿 / T11 雪崩 / T12 Redis 不可用 / T13 延迟双删多实例。

**修正**
- 核实 `getWithLock` 真实实现（按 key 的**本地** `ReentrantLock`、**无超时**、二次检查）——「击穿合并回源」只在**单 JVM**成立。
- `ShopController.getStatus` 的 `get(KEY)` 在 try **之外** → Redis 故障时 `/user/shop/status` **500**（不是兜底默认值）；`CatalogCacheHelper.get` 亦未兜异常。
- 明确「**延迟双删 ≠ 强一致**」，多实例用本地锁/本地延时任务**会失效**。
- TTL 抖动数值、空值 `new ArrayList<>()` 的坑，均标注准确行号。

### 第三批：第 5 章 + 第 6 章

**新增 / 改写**
- 第 5 章 RabbitMQ：T14~T17（全新），并**据真实 MQ 架构改写测试假设**。
- 第 6 章微服务故障：T18~T21（全新）；**保留 v1 计划4（Feign 熔断）/ 计划4.1（网关限流）**并三段化。

**修正（重要，防误判）**
- **MQ 是 fanout 广播 + 每个实例 `AnonymousQueue`（exclusive/auto-delete/非持久）+ 自动 ack + 无 DLQ**：**停止消费者时消息被「丢弃」而非「堆积」**；「死信 / 毒消息 / 无限重试」在本项目**不适用**（T14/T15/T17 已改写）。
- 真正的一致性风险在 `paySuccess`：**DB 更新与发消息不在同一事务**（T16）。
- **加购仅当购物车无该商品时才走 Feign**（`ShoppingCartServiceImpl.java:44-55`）——压测要换 drugId 或先清车；订单详情**不**调 catalog。
- Feign **默认不重试**，仓库未见重试配置 → T21「重试放大」属**建议补充**。

### 第四批：第 7、8、10、11 章 + 附录 A~D、G

**新增**
- 第 7 章业务正确性与安全：T22~T27（全新）。
- 第 8 章 JVM/线程池/OS：T28~T30（全新）。
- 第 10 章 7 天实战清单（5 阶段 17 项，可勾选，标注 T 编号）。
- 第 11 章故障记录模板（8 段）+ `docs/testing/incident-drills/`。
- 附录 A 来源索引 / B 常见问题排查表 / C 环境分层表 / D 待确认清单 / G 统一验收清单。

**修正（防误判）**
- **T22 超卖**：本项目**无库存字段 / 无 Redisson / 无 Lua**（`docs/05-测试与压测/测试清单.md:105-108`）→ 改写为「并发加购 check-then-act 竞态」的**等价数据正确性实验**；真正超卖属**建议补充能力**。
- **T23 幂等**：`submitOrder` **无幂等键**，订单号用 `System.currentTimeMillis()` → 重复提交可能产生重复订单。
- **T26 越权**：`GET /user/order/orderDetail/{id}` **未校验订单归属**（IDOR 候选）。
- **T28~T30**：无 actuator 指标端点、**无自定义业务线程池**（仅 Tomcat + 2 线程缓存调度器）、**无 logback 配置**——据此改写观测手段为 `jcmd/jstat/jstack`，并警示「不要真写满磁盘」。

### 相对 v1 总结

- **保留**：Redis db10 与 `-n 10` 原则；v1 计划 1/2/3/4/4.1 的**覆盖**（作为保留小节 + 别名）；不编造压测结果；`View Results Tree` 禁用；`/inner`、`/actuator` 不可打。
- **删除 / 更正**：HikariCP 表述（→ Druid）；下单含扣库存/事务内发 MQ（→ 真实五步 + `paySuccess` 无事务）；`/** → ysd-server` 兜底路由（**已移除**）；`categoryId=1` 硬编码（→ 取真实分类 id）；jwt.io 生成 token（→ 本地脚本）。
- **新增零基础操作层，目标是让没用过 JMeter 的人能照着文档跑通全部计划**：第 1.5 章 + 每个测试的五步【操作】块 + 附录 E/F。
- **全文三段式**（原理 → 操作 → 验证），30 个测试 T01~T30 均有前置条件、可执行步骤、预期现象、排障路径、停止与恢复闭环。

### 未完成事项清单（交付时）

**已据源码 / 转储核实（可信）**
- 接口路径、HTTP 方法、参数、header、鉴权规则、白名单、`/inner` 与 `/actuator` 403。
- 缓存 key / TTL / 阈值 / `getWithLock` / 延迟双删实现与行号。
- 下单事务边界、`paySuccess` 无事务、加购 Feign 触发条件。
- MQ fanout / 匿名队列 / 自动 ack / 无 DLQ；`paySuccess` 发消息点。
- 数据源为 Druid；**无库存 / 无 Redisson / 无 Lua**；真实库 `drug/orders` 仅主键无二级索引。
- 无 actuator 指标端点、无自定义业务线程池、无 logback 配置。

**需用户在真实环境确认（见附录 D）**
- D1~D12：`Bearer` 前缀、`drug` 是否含 `prescription_*`、`OrderSubmitRollbackTest` 是否落地、trace 断链、Feign 超时/重试配置、Nacos 真实阈值、RabbitMQ 管理插件、日志落盘/轮转、生产索引、systemd、真实库账号。

> 说明：本手册**不含任何已执行的压测结果**；所有并发/阈值/耗时数字均为**示例或参考目标**，需以真实测量与运行时配置为准。查询类 SQL（`EXPLAIN`、`SHOW`）请在生产环境以**只读**方式执行，破坏性实验仅在本地 / 独立测试环境进行。
