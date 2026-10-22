# 药速达智慧配送平台 · JMeter 压测指南

> ⚠️ **归档说明**：本文（v1）记录于 2026-10-12，**已被 [JMeter压测与故障演练手册-v2](JMeter压测与故障演练手册-v2.md) 取代**，此处原样保留仅供对照。最新口径与路径请以 v2 为准。

> 生成时间：2026-10-12。基于仓库**实际源码与配置**编写，每个关键结论标注来源 `文件:行号`。
> 本文档只描述**如何压测**，不含任何已执行的压测结果。
> 相关文档：`docs/09-项目记录/遗留事项总表.md`、`docs/09-项目记录/微服务拆分记录/阶段5-4-缓存改造-实施记录.md`、`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel改造-准备.md`、`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md`、`docs/09-项目记录/微服务拆分记录/阶段5-5-链路追踪改造-记录.md`。

---

## 0. 三条重要前提（先读）

1. **接口路径/参数均从源码查证**，正文标注来源。凡仓库内看不到的（Nacos 规则、真实库结构），一律标 `<!-- TODO: 待确认 -->`，不写死。
2. **Sentinel 阈值不在仓库里** —— 网关限流规则与 Feign 熔断规则都存在 **Nacos**（见 §5）。它们给的是**读取步骤/示例值**，实际数值以运行时为准。
3. **仓库内无 MySQL 初始化脚本** —— 只有 H2 测试脚本（`*/src/test/resources/sql/data-h2.sql`）。§1.4 的预置 SQL **按 H2 脚本列名推断**，字段以实际库为准。

---

## 1. 通用前提

### 1.1 工具版本

| 项 | 要求 | 说明 |
|---|---|---|
| JMeter | 5.5 及以上 | 需要 `JSON Assertion`、`JSON Extractor` |
| JDK | 17+ | 与项目（Spring Boot 3.5.16）一致 |
| JMeter Plugins | 可选 | `Response Times Over Time` / `Percentiles` 来自 jmeter-plugins，需用 Plugins Manager 安装 |

### 1.2 目标服务与端口

| 服务 | 端口 | 压测相关职责 |
|---|---|---|
| ysd-gateway | 9000 | **统一入口**，鉴权 + 路由 + 网关限流 |
| ysd-catalog-service | 8084 | 分类 / 药品 / 药品组合（**缓存改造重点**） |
| ysd-order-service | 8085 | 订单 / 购物车（**Feign 熔断降级挂载点**） |
| ysd-push-service | 8086 | WebSocket 推送（MQ 消费） |

中间件：Nacos `8848` / MySQL `3306`（库 `ysd_take_out`）/ Redis `6379`（**业务缓存用 db10**）/ RabbitMQ `5672` / Sentinel Dashboard `8718` / Zipkin `9411`。
> Redis db10 来源：`ysd-catalog-service/src/main/resources/application.yml:23-27`、`ysd-order-service/src/main/resources/application.yml:50-55`。

### 1.3 鉴权与 token（务必先做）

**header 规则**（来源 `ysd-gateway/src/main/java/com/ysd/filter/AuthGlobalFilter.java:74-83`）：

| 路径前缀 | 是否要 token | header 名 |
|---|---|---|
| `/admin/employee/login`、`/user/user/login`、`/user/shop/status`、`/notify/`、`/ws/`、`/doc.html`、`/webjars/` | 否（白名单） | — |
| `/admin/**` | 是 | `token` |
| 其余（含 `/user/**`） | 是 | `authentication` |

> 白名单来源：`AuthGlobalFilter.java:39-47`。网关会**剥离**客户端自带的 `X-Identity-Id`（`:52-56`），所以伪造身份头无效；`/inner/**` 一律 403（`:63-65`），**压测不要打 `/inner`**。

**获取管理端 token**（本地可用）：

```bash
curl -s -X POST http://localhost:9000/admin/employee/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
# 响应 data.token 即管理端 token
```
> 来源：`ysd-admin-service/.../controller/admin/EmployeeController.java:43-66`（响应字段 `token`，见 `ysd-pojo/.../vo/EmployeeLoginVO.java`）。默认账号密码来自种子数据 `data-h2.sql:10-12`（admin / <your-password>）。

**获取用户端 token**（本地**不能**走登录接口）：

`POST /user/user/login` 需要**微信真实 code**（`ysd-user-service/.../controller/user/UserController.java:42-61`，body `{"code":"..."}`，见 `ysd-pojo/.../dto/UserLoginDTO.java`），本地拿不到。

按决策：**不要用 `TempTokenGenerator`**。改为**手写 JWT**，步骤如下：

1. **先查库确认真实 user id**：
   ```sql
   SELECT id FROM user LIMIT 1;
   ```
2. 用该 id 生成 token。⚠️ **算法/密钥有个坑**：`JwtUtil` 并不直接用配置里的明文密钥签名，而是先对密钥做 **SHA-256**，用其 32 字节摘要做 HMAC-SHA256 的 key（`ysd-common/.../utils/JwtUtil.java:25-33`、`createJWT:44-54`）。密钥从环境变量 `JWT_ADMIN_SECRET` / `JWT_USER_SECRET` 读取（见 `.env.example`），仓库内不保存真实值。
   - 签名密钥 = `SHA-256(本地密钥)`，请用本地脚本计算；**不要把真实密钥或派生结果写进文档，也不要贴到 jwt.io 等在线工具**（会被缓存/索引）。
3. **本地生成 token**（推荐）：
   - 优先用 `python jmeter/gen-user-token.py <id>`，脚本从 `JWT_USER_SECRET` 环境变量读取密钥。
   - 手工构造时：Header `{"alg":"HS256","typ":"JWT"}`；Payload `{"userId": <id>, "exp": <未来的 Unix 秒>}`；HMAC key 使用 `SHA-256(密钥)` 的 32 字节摘要。
   - claim 键名 `userId` 来源：`ysd-common/.../constant/JwtClaimsConstant.java:6`（`USER_ID = "userId"`），网关按此键取 id（`AuthGlobalFilter.java:100`）。
4. 把生成的 token 放进 `authentication` 头。

> 自检：若 token 被网关判 401，先看网关日志 `JWT 校验失败` / `请求缺少 token`（`AuthGlobalFilter.java:87,113`）。

### 1.4 测试数据预置 SQL

> ⚠️ **醒目提示**：**仓库内无真实库 init 脚本**，以下 SQL 的字段/约束**按 H2 脚本列名推断**（来源 `ysd-catalog-service/src/test/resources/sql/data-h2.sql:28-45`），**字段以实际 MySQL 库为准**（真实库可能有额外 NOT NULL 列，如 create_user / update_user 等）。

**先查现有数据量：**
```sql
SELECT COUNT(*) AS category_cnt FROM category;
SELECT COUNT(*) AS drug_cnt     FROM drug;
SELECT COUNT(*) AS drug_combo_cnt  FROM drug_combo;
```

**H2 种子数据量参考**（`data-h2.sql:28-45`）：category **1** 条、drug **2** 条（`category_id=1, status=1`）、drug_combo **1** 条。

**预置 100 条 drug（挂到 category_id=1）：**
```sql
-- 若 category_id=1 不存在，先插一条：
-- INSERT INTO category (type, name, sort, status, create_time, update_time)
--   VALUES (1, '压测分类', 1, 1, NOW(), NOW());

INSERT INTO drug (name, category_id, price, image, description, status, create_time, update_time)
SELECT CONCAT('压测药品', n), 1, 9.99, 'test.jpg', '压测数据', 1, NOW(), NOW()
FROM (
  SELECT (a.n + b.n*10 + 1) AS n
  FROM (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a
  CROSS JOIN
       (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
        UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b
) t;
-- 生成 n = 1..100，共 100 行
```

**预置药品组合（可选，供 drug_combo 缓存计划）：**
```sql
INSERT INTO drug_combo (category_id, name, price, status, description, image, create_time, update_time)
VALUES (1, '压测药品组合', 20.00, 1, '压测', 's.jpg', NOW(), NOW());
```

**清缓存（保证"从 miss 开始"观测）—— Redis db10：**
```bash
# 方式 A（跨平台，用 EVAL，避免 Windows 无 xargs）
redis-cli -n 10 eval "local k=redis.call('keys','ysd:catalog:drug:*') if #k>0 then return redis.call('del',unpack(k)) end return 0" 0

# 方式 B（Linux/macOS）
redis-cli -n 10 --scan --pattern 'ysd:catalog:drug:*' | xargs -r redis-cli -n 10 del
```

### 1.5 监听器建议

| 用 | 用途 |
|---|---|
| **Aggregate Report** | TPS、平均/P90/P95/P99、错误率 |
| **Response Times Over Time**（插件） | 观察时间序列曲线（限流/熔断的拐点） |
| **Response Times Percentiles**（插件） | P95/P99 分布 |

**不要用 `View Results Tree`** —— 它记录每个请求的完整请求/响应体，在数百 TPS 下会迅速耗尽内存并成为压测机瓶颈，导致施压能力虚低、结果失真。需要看单个响应时，用 `Save Responses to a file` 只存失败样本，或用 Debug Sampler 在低并发下单独验证。

---

## 2. 计划 1：缓存命中率验证

**目的**：验证 `GET /user/drug/list` 走缓存后，首次回源（miss）→ 后续命中（hit），命中段吞吐显著高于回源段。

**接口来源**：`ysd-catalog-service/.../controller/user/DrugController.java:31-41` → `DrugServiceImpl.listWithSpec:208-228`（缓存 key = `drugKey(categoryId)`，走 `getWithLock`，`cache/CatalogCacheHelper.java:91-113`）。

**JMeter 配置表：**

| 项 | 值 |
|---|---|
| 线程数 | 50 |
| Ramp-up | 10 s |
| 循环次数 | 100（总 5000 请求） |
| 请求 | `GET http://localhost:9000/user/drug/list?categoryId=1` |

**HTTP 请求配置：**
- Method：`GET`
- Header：`authentication: ${userToken}`（用户端 header 名见 §1.3）
- Query：`categoryId=1`
- （可选）`HTTP Request Defaults` 里设 host=localhost、port=9000

**前置动作**：先按 §1.4 清空 `ysd:catalog:drug:*`，保证首请求是 miss。

**预期结果：**
- 首次请求 miss → 回源查 DB（`drugMapper.list` + 每条规格 `getByDrugId`）→ 写缓存 `ysd:catalog:drug:1`（TTL 600–720 s）。
- 其后请求全部命中缓存，P95 应显著下降（命中段建议目标 **P95 < 50 ms**，需以本机基线为准）。
- TPS 曲线：开头一小段偏低（miss），随后抬升并稳定（hit）。

**验证方法：**
```bash
# 命中/未命中计数（压测前后各读一次，看差值）
redis-cli -n 10 info stats | grep -E 'keyspace_hits|keyspace_misses'
# 缓存确已写入且 TTL 落在区间内（DISH = 600~720s）
redis-cli -n 10 ttl ysd:catalog:drug:1
```
> TTL 区间来源：`CatalogCacheHelper.java:132-133`（drug base 10min + jitter 0–120s）。

---

## 3. 计划 2：缓存穿透验证

**目的**：验证「查不存在的 categoryId」时，**空值缓存**生效 —— 首次回源（返回空集合）→ 写入空值缓存（短 TTL）→ 后续不再打 DB。

**接口**：同计划 1，但 `categoryId` 用一个**不存在的值**（建议 `999999`，远大于种子/预置 id）。

**JMeter 配置表：**

| 项 | 值 |
|---|---|
| 线程数 | 50 |
| Ramp-up | 5 s |
| 循环次数 | 20（总 1000 请求） |
| 请求 | `GET http://localhost:9000/user/drug/list?categoryId=999999` |

**HTTP 请求配置**：Method `GET`；Header `authentication: ${userToken}`。

**前置动作**：清空 `ysd:catalog:drug:*`（含 999999 的空值缓存）。

**预期结果：**
- 首请求 miss → `loader` 返回**空 List** → `put()` 判定空集合 → 走 `setEmpty()` 写空值缓存（`CatalogCacheHelper.java:115-129, 158-164`）。
- 其后请求命中空值缓存，不再回源。

**验证方法：**
```bash
redis-cli -n 10 exists ysd:catalog:drug:999999      # 期望 1
redis-cli -n 10 get    ysd:catalog:drug:999999      # 期望 []（JSON 空数组）
redis-cli -n 10 ttl    ysd:catalog:drug:999999      # 期望 60~70（EMPTY base 60s + jitter 0~10s）
```
> 空值 TTL 来源：`CatalogCacheHelper.java:139-140`。

**DB 查询次数对比（加空值缓存前 vs 后）：**
```sql
-- 粗略对比（Com_select 含其它语句，仅看量级差异）
SHOW GLOBAL STATUS LIKE 'Com_select';
```
- 「后」：压测期间 DB `Com_select` 增量应远小于请求数（空值缓存挡住绝大多数）。
- 「前」：临时让 `setEmpty` 不写缓存（本地改代码验证后**务必还原**），或直接观察 Redis 无该 key 时每个请求都回源 —— 此时 `Com_select` 增量 ≈ 请求数。

**注意事项（关键）：** 空值缓存存放的是 **`new ArrayList<>()`，不是 `Collections.emptyList()`**。来源 `CatalogCacheHelper.java:158-164` 的注释与实现。
> 原因：序列化器开了 `activateDefaultTyping`，`Collections.emptyList()` 会被写成包私有类 `java.util.Collections$EmptyList`，**无默认构造、反序列化会崩**。这是已踩过的坑（见 `docs/09-项目记录/微服务拆分记录/阶段5-4-缓存改造-实施记录.md`）。压测前请确认该行仍是 `new ArrayList<>()`。

---

## 4. 计划 3：延迟双删验证

**目的**：验证 `PUT /admin/drug` 写路径的「延迟双删」—— 写后 **500ms 窗口期内可能读到旧值**，窗口期后缓存被清、回源拿到新值，最终**缓存 == DB**。

**路径（按扫描结论）：**
- 写：`PUT http://localhost:9000/admin/drug`（`controller/admin/DrugController.java:88-94` → `DrugServiceImpl.updateWithSpec:148-173`）
- 读：`GET http://localhost:9000/user/drug/list?categoryId={id}`（同计划 1）

**JMeter 配置：两个线程组并行**

| 线程组 | 线程 | 循环 | 节奏 | 请求 |
|---|---|---|---|---|
| 读 | 20 | 200 | 无限/尽快 | `GET /user/drug/list?categoryId=1`，header `authentication: ${userToken}` |
| 写 | 1 | 50 | 每 500ms~1s 一次 | `PUT /admin/drug`，header `token: ${adminToken}` |

**写请求 body（`DrugDTO`，来源 `ysd-pojo/.../dto/DrugDTO.java:11-29`）：**
```json
{
  "id": 1,
  "name": "压测药品A-改${__time()}",
  "categoryId": 1,
  "price": 12.00,
  "image": "a.jpg",
  "description": "压测",
  "status": 1,
  "specs": []
}
```
> `name` 用 `${__time()}` 保证每次不同，便于读端断言。

**预期结果：**
- `updateWithSpec` 内：**写库前**先 `evictByPattern(DISH_PATTERN)`（`DrugServiceImpl.java:153`），**事务提交后**延时 500ms 再清一次（`:172`）。
- 500ms 窗口期内，并发的读可能把**DB 旧值回写**进缓存（这正是双删要清掉的情况）；窗口期后缓存被清，下次读回源拿新值。
- **最终一致性**：任一「写完成 + 静置 > 1s」后的读，其 `name` 必须等于该次写入的 `name`。

**验证方法：**
- **读端 JSON Assertion**：`$.data[?(@.id==1)].name` 应 ∈ {旧值, 最新写值}（窗口内允许两种）。
- **专项断言（推荐）**：在一次写之后 `sleep 1000ms` 再单独发一次读，断言 `name == 该次写值`，缓存与 DB 应一致。
- **缓存观察**：
  ```bash
  redis-cli -n 10 ttl ysd:catalog:drug:1     # 写后被清 → 读回源后重建
  ```

**注意事项：**
- `updateWithSpec` 清的是 **`DISH_PATTERN`（全部药品缓存）**，不是单个 key（`DrugServiceImpl.java:153,172`）—— 即"第二次删除范围偏大"是已知遗留（见 `docs/09-项目记录/遗留事项总表.md` P2）。
- **本地锁 / 延时任务仅单实例生效**。本计划只在 catalog **单实例**下有意义；**多实例**会暴露本地锁失效（对应 TODO：多实例延时双删改 MQ 延时消息）。若要多实例压，需先接受该失真。
- 延迟失败不重试（仅 WARN，`CatalogCacheHelper.java:192-211`）。

---

## 5. 计划 4：Sentinel 降级验证

**目的**：验证 order-service 调 catalog 的 **Feign 熔断降级**生效 —— 下游挂掉时**快速失败**（fail-fast，不阻塞到超时），并返回降级内容。

> **重要（扫描发现）**：`GET /user/order/orderDetail/{id}` **不调用 catalog Feign**（只查本服务 `orders`/`order_detail`，`OrderServiceImpl.details:235-248`）。**真正触发 `CatalogClient` 的是「加购物车」**：`POST /user/shoppingCart/add` → `ShoppingCartServiceImpl` 调 `catalogClient.getDrugById`（`ysd-order-service/.../service/impl/ShoppingCartServiceImpl.java:55`）/ `getDrugComboById`（`:62`）。**所以本计划压的是加购接口，不是订单详情。**

**压测路径**：`POST http://localhost:9000/user/shoppingCart/add`

**JMeter 配置表：**

| 项 | 值 |
|---|---|
| 线程数 | 50 |
| 循环 | 用 Duration 控制（如 60s），或固定循环 |
| 请求 | `POST http://localhost:9000/user/shoppingCart/add` |

**HTTP 请求配置：**
- Header：`Content-Type: application/json`、`authentication: ${userToken}`
- Body（`ShoppingCartDTO`，来源 `ysd-pojo/.../dto/ShoppingCartDTO.java:7-13`）：
  ```json
  { "drugId": 1 }
  ```
  > 需要 `drugId` 在 catalog 中**真实存在**（否则命中的是业务异常而非熔断）。用 `SELECT id FROM drug LIMIT 1;` 取真实 drugId。

**故障注入（压测中途 kill catalog-service）：**
- **IDE / java -jar 运行**（Windows）：
  ```bash
  jps -l                                  # 找到 YsdCatalogServiceApplication 的 pid
  taskkill /PID <pid> /F
  ```
- **Docker 运行**（容器名以实际为准）：
  ```bash
  docker stop ysd-catalog-service
  ```

**预期结果：**
- kill 后，order-service 侧 Feign 调用触发熔断降级 → `CatalogClientFallbackFactory` 抛出 `ShoppingCartBusinessException("商品服务繁忙，请稍后重试")`（`ysd-api/.../client/fallback/CatalogClientFallbackFactory.java:25-35`）→ 由 `GlobalExceptionHandler` 转成 `Result.error`。
- **降级不编造数据（fail-fast）**：响应是**错误**（`code != 1`），而非伪造的 200。
- 响应**快速失败**：延迟**不应**出现超时堆积（对比"无熔断时直接等 Feign 超时"）。

**验证方法：**
- 响应体：`$.code != 1`、`$.msg == "商品服务繁忙，请稍后重试"`（或统一错误文案）。
- Aggregate Report：kill 后**错误率上升**，但 **P95 不飙升**（快速失败特征）。
- Sentinel Dashboard（order-service 应用，transport 端口 `8719`，`ysd-order-service/application.yml:21-25`）：资源 `CatalogClient#getDrugById(Long)` 熔断器进入 **OPEN**。

**示例降级规则（示例值，以 Nacos 实际为准）** —— dataId `ysd-order-service-sentinel-degrade`：

| 资源 | 策略 | 示例阈值 | 窗口 |
|---|---|---|---|
| `CatalogClient#getDrugById(Long)` | 异常比例 | > 50% | 10 s |
| `CatalogClient#getDrugComboById(Long)` | 异常比例 | > 50% | 10 s |
| `UserClient#getAddressBookById(Long)` | 慢调用比例 | RT > 800ms 且比例 > 50% | 10 s |

> 示例值来源：`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel改造-准备.md:298-302`。**以 Nacos `SENTINEL_GROUP / ysd-order-service-sentinel-degrade` 实际为准**。

**fallback 生效的三个前提（缺一即静默失效）：**
1. `spring.cloud.openfeign.circuitbreaker.enabled: true` —— 已开（`ysd-order-service/application.yml:36-39`）✅
2. 类路径有 CircuitBreaker 实现（Sentinel starter）—— 见 `docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md:164-167`
3. `@FeignClient(..., fallbackFactory = CatalogClientFallbackFactory.class)` —— 已有（`ysd-api/.../client/CatalogClient.java:17-19`）✅

### 5.1 附：网关 QPS 限流验证

**目的**：验证网关对指定路由的 QPS 限流生效。

**规则位置（不在仓库）**：Nacos dataId `ysd-gateway-sentinel-gw-flow`，group `SENTINEL_GROUP`，rule-type `gw-flow`（`ysd-gateway/src/main/resources/application.yaml:21-30`）。

**阈值**：`<!-- TODO: 待确认 -->`（仓库内不可见）。按决策不写死，**读取步骤**如下：
1. Nacos 控制台 → 配置管理 → 打开 `ysd-gateway-sentinel-gw-flow`，看 `count`（阈值）与 `grade`。
2. **`grade` 必须 = 1（QPS）**。`grade=0` 是线程数模式，而网关 `GatewayFlowSlot` 不维护线程计数 → `0+1<=count` 恒成立 → **规则恒放行**（`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md:28-46`）。
3. 压测前**连续观察几分钟**确认 `count` 不再变化（曾出现被第二个写入方在 **5↔100** 间反复覆盖，`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md:124-138`）。

**压测方式**：对某路由（如 `POST http://localhost:9000/user/shoppingCart/add`）用**真并发**持续施压，逐步加大线程数直到超过 `count`。
> ⚠️ 顺序发请求**测不出**限流（QPS 是"每秒配额"，发太慢到不了阈值、发太快又可能撞上配错规则，两种都会误判）——`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md:53-73`。

**预期结果**：超过阈值时返回 **HTTP 429**（`Too Many Requests`），`blockQps` 上升。

**验证方法：**
```bash
# 网关侧 metrics（transport 端口 8720，ysd-gateway/application.yaml:20）
curl http://127.0.0.1:8720/gateway/getRules   # 规则是否加载、grade/count 是否与 Nacos 一致
```
- Aggregate Report：出现 429 且比例随并发上升。
- 应用侧出现 `~/logs/csp/sentinel-block.log`。

> **先在 Nacos 侧确认配置稳定**，再压测；否则测的可能是"被覆盖后的一天规则"。

---

## 6. 结果记录模板

| 计划 | 测试日期 | 线程/时长 | TPS | P95 | 错误率 | 关键指标 | 结论 |
|---|---|---|---|---|---|---|---|
| 1 缓存命中率 | | | | | | `keyspace_hits/misses` 差值、`ttl ysd:catalog:drug:1` | 生效 / 未生效 / 需改进 |
| 2 缓存穿透 | | | | | | 空值缓存 `exists`/`get`/`ttl`、`Com_select` 增量 | 生效 / 未生效 / 需改进 |
| 3 延迟双删 | | | | | | 写后 1s 读 `name` 是否 == 最新写值；缓存被清次数 | 生效 / 未生效 / 需改进 |
| 4 Sentinel 降级 | | | | | | fallback `msg`、Dashboard 熔断器状态、P95 是否飙升 | 生效 / 未生效 / 需改进 |
| 4.1 网关限流 | | | | | | 429 比例、`blockQps`、Nacos `count/grade` | 生效 / 未生效 / 需改进 |

**链路追踪附加项**（可任选一次压测叠加）：
- 在 Zipkin `http://localhost:9411` 检索同一 traceId，确认 `gateway → order → (Feign) → catalog` 为**同一 traceId**。
- 涉及 MQ 的（`order → push`）：确认 `ysd-push-service` 消费日志里的 traceId 与 `ysd-order-service` 发起时一致（对应 `docs/09-项目记录/遗留事项总表.md` P3 遗留项）。

---

## 7. 附录：常见问题排查

| 现象 | 可能原因 | 排查 |
|---|---|---|
| 压测大量 **401** | token 问题 | ① header 名对不对（`/admin/**`→`token`，其余→`authentication`）；② token 是否过期（`exp`）；③ 手写 token 是否用了 `SHA-256("<JWT_ADMIN_SECRET>")` 而非明文（§1.3）；④ 看网关日志 `请求缺少 token` / `JWT 校验失败`（`AuthGlobalFilter.java:87,113`） |
| 压测大量 **503** | 服务未注册 / 路由不匹配 | ① Nacos 服务列表里目标服务是否在；② 路由 `Path` 是否匹配（`ysd-gateway/application.yaml:34-75`）；③ 目标服务是否真的起了（端口 8084/8085）；④ 兜底路由 `/**`→`ysd-server` 是否把请求带错（`application.yaml:72-75`） |
| **trace 断链** | observation 未开 / 未接追踪 | ① order-service 生产端需 `spring.rabbitmq.template.observation-enabled: true`（已开，`ysd-order-service/application.yml:56-59`）；② 经 `ysd-server`（未接入追踪）的请求本就断链（`docs/09-项目记录/遗留事项总表.md` P3）；③ Feign/网关注入依赖 tracing 三件套（详见 `docs/09-项目记录/微服务拆分记录/阶段5-5-链路追踪改造-记录.md`） |
| **响应慢但无错误** | Sentinel 可能在限流/熔断，或缓存全 miss | ① 看网关 metrics `blockQps` 是否 >0；② 看 order-service 熔断器是否 OPEN；③ `redis-cli -n 10 info stats` 看命中率；④ 确认 Nacos 规则 `count` 未被人为覆盖 |
| 计划 2 看不到空值缓存 | `setEmpty` 被改 / key 被清 | 确认 `CatalogCacheHelper.java:158-164` 仍是 `new ArrayList<>()`；确认没被 `evictByPattern` 提前清掉 |

---

## 附录 A · 来源索引（本文档主要依据）

| 结论 | 来源 |
|---|---|
| 网关白名单 / token header / 身份头剥离 / `/inner` 403 | `ysd-gateway/src/main/java/com/ysd/filter/AuthGlobalFilter.java:39-83` |
| 网关路由 / 限流数据源 | `ysd-gateway/src/main/resources/application.yaml:21-75` |
| 药品列表走缓存 | `ysd-catalog-service/.../controller/user/DrugController.java:31-41`、`DrugServiceImpl.java:208-228` |
| 更新药品 = 延迟双删 | `.../controller/admin/DrugController.java:88-94`、`DrugServiceImpl.java:148-173` |
| 缓存 key / TTL / 空值缓存 / 本地锁 / 延迟删除 | `ysd-catalog-service/.../cache/CatalogCacheHelper.java:35-51,91-113,132-140,158-164,192-211` |
| 加购触发 CatalogClient（非订单详情） | `ysd-order-service/.../service/impl/ShoppingCartServiceImpl.java:55,62`、`OrderServiceImpl.java:235-248` |
| Feign 契约 | `ysd-api/.../client/CatalogClient.java:25-32` |
| fallback 返回内容 | `ysd-api/.../client/fallback/CatalogClientFallbackFactory.java:25-35` |
| 熔断开关 | `ysd-order-service/src/main/resources/application.yml:36-39` |
| JWT 签名（SHA-256 密钥） | `ysd-common/.../utils/JwtUtil.java:25-33,44-54` |
| claim 键名 | `ysd-common/.../constant/JwtClaimsConstant.java:6` |
| 种子数据 / 表列 | `ysd-catalog-service/src/test/resources/sql/data-h2.sql:28-45` |
| Sentinel 规则/坑 | `docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel改造-准备.md`、`docs/09-项目记录/微服务拆分记录/阶段5-3-Sentinel踩坑记录.md` |
