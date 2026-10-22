# 药速达 · JMeter 测试计划 · 操作手册

> 参考手册：[JMeter压测与故障演练手册-v2](../docs/05-测试与压测/JMeter压测与故障演练手册-v2.md)（T01~T30 完整版）。本目录落地**精选 10 个**测试 + T06 Druid 连接池专项。
> **本目录只包含测试计划与脚本，不含任何已执行的压测结果**；所有数字都由你跑完后填写 [JMeter故障测试记录模板](../docs/05-测试与压测/JMeter故障测试记录模板.md)。
> 所有故障注入**只在独立测试环境 / 本地环境**执行，生产环境只允许观测。

---

## 1. 目录结构

```
jmeter/
├── plans/
│   ├── t01-baseline.jmx             # 计划 1  T01 基准测试
│   ├── t10-cache-breakdown.jmx      # 计划 2  T10 缓存击穿
│   ├── t11-cache-avalanche.jmx      # 计划 3  T11 缓存雪崩
│   ├── s61-feign-fallback.jmx       # 计划 4  §6.1 Feign 熔断降级
│   ├── s62-gateway-flow.jmx         # 计划 5  §6.2 网关 QPS 限流
│   ├── t13-double-delete-multi.jmx  # 计划 6  T13 延迟双删多实例（读 + 写两个线程组）
│   ├── t12-redis-down.jmx           # 计划 7  T12 Redis 不可用
│   ├── t14-consumer-stop.jmx        # 计划 8  T14 消费者停止
│   ├── t16-mq-no-transaction.jmx    # 计划 9  T16 发送成功但业务未提交
│   ├── t22-cart-race.jmx            # 计划 10 T22 并发加购竞态（Synchronizing Timer）
│   ├── t06-druid-pool.jmx           # T06 专项：50 线程 / 60 秒压 8085，把 Druid 池打满
│   └── categories.csv               # T11 用的分类 id 列表（8 个真实药品分类）
├── run-batch1.bat                   # 批次 1：T01 / T10 / T11 / S61 / S62
├── run-batch2.bat                   # 批次 2：T13 / T12 / T14 / T16 / T22
├── run-all.bat                      # 全部 10 个
├── parse-results.py                 # 结果解析（请求数 / 平均 / P50 / P95 / P99 / 错误率）
├── results/                         # 跑批后生成 .jtl（本目录初始不存在）
└── reports/                         # 跑批后生成 HTML 报告（本目录初始不存在）
```

**所有 .jmx 都用 `${__P(名,默认值)}` 参数化**，命令行用 `-J名=值` 覆盖，例如 `-Jcid=11 -Jtoken=eyJ...`。

> `t06-druid-pool.jmx` 依赖 order-service 临时把 Druid `max-active` 调小，属于专项靶场，**不在 `run-all.bat` 中**。操作与恢复步骤见 `docs/05-测试与压测/T06-T09-靶场操作手册-20261020.md`。

---

## 2. 前置条件

### 2.1 工具

| 项 | 要求 |
|---|---|
| JMeter | 5.6+（需要 `-e -o` 生成 HTML 报告、Synchronizing Timer、JSON Assertion） |
| JDK | 17+（工程实际 Java 21） |
| Python | 3.7+（只跑 `parse-results.py`，标准库即可） |
| redis-cli | 6.x+（**所有命令必须带 `-n 10`**，业务缓存在 db10） |
| MySQL 客户端 | 8.x |

跑批脚本定位 JMeter 的顺序：① 环境变量 `JMETER_HOME` 指向 JMeter 根目录；② PATH 里的 `jmeter.bat`。

```powershell
# 例：临时设置（仅当前会话）
$env:JMETER_HOME = "<jmeter-home>"
```

### 2.2 中间件（先起）

| 组件 | 端口 | 说明 |
|---|---|---|
| Nacos | 8848 | 服务注册 + Sentinel 规则源（`SENTINEL_GROUP`） |
| MySQL | 3306 | 库 `ysd_take_out` |
| Redis | 6379 | **业务缓存 db10** |
| RabbitMQ | 5672 | `ws.broadcast` fanout；管理界面 15672 |
| Sentinel Dashboard | 127.0.0.1:8718 | 容器内 8858，Docker 映射到 8718 |
| Zipkin | 9411 | 查询 traceId |

### 2.3 业务服务（怎么起）

> 可启动服务与端口：gateway 9000 / user 8081 / admin 8082 / report 8083 / catalog 8084 / order 8085 / push 8086 / **ai 8088**（共 8 个；本批 10 个测试不涉及 report 与 ai）。

三种起法任选：

```powershell
# 方式 A：IDE 运行各模块启动类（推荐，便于看日志与注入故障）
#   YsdGatewayApplication / YsdUserServiceApplication / YsdAdminServiceApplication
#   YsdCatalogServiceApplication / YsdOrderServiceApplication / YsdPushServiceApplication

# 方式 B：Maven 直接跑单个模块（在仓库根目录）
mvn -pl ysd-gateway        spring-boot:run
mvn -pl ysd-catalog-service spring-boot:run

# 方式 C：先打包再 java -jar
mvn -DskipTests clean package
java -jar ysd-catalog-service/target/ysd-catalog-service-1.0-SNAPSHOT.jar
```

**本次 10 个测试分别需要哪些服务：**

| 测试 | gateway | user | admin | catalog | order | push | RabbitMQ | Redis |
|---|---|---|---|---|---|---|---|---|
| T01 | ✅ | ✅ | ✅ | ✅ | ✅ | — | — | ✅ |
| T10 / T11 | ✅ | — | — | ✅ | — | — | — | ✅ |
| §6.1 | ✅ | — | — | ✅ | ✅ | — | — | ✅ |
| §6.2 | ✅ | — | — | ✅ | ✅ | — | — | ✅ |
| T13 | ✅ | — | — | ✅ ×2 | — | — | — | ✅ |
| T12 | ✅ | — | — | ✅ | — | — | — | ✅（要停） |
| T14 | ✅ | — | — | — | ✅ | ✅（要停） | ✅ | ✅ |
| T16 | ✅ | — | — | — | ✅ | — | ✅（要停） | ✅ |
| T22 | ✅ | — | — | — | ✅ | — | — | ✅ |

`report-service` 本批 10 个测试都用不到。

---

## 3. 准备 token 与测试数据（**必做**）

### 3.1 管理端 token（`token` 头）

```powershell
# 经网关调登录接口，取响应 data.token
curl.exe -s -X POST http://localhost:9000/admin/employee/login `
  -H "Content-Type: application/json" `
  -d '{\"username\":\"admin\",\"password\":\"123456\"}'
```

- 默认账号 `admin / <your-password>`（种子数据），**真实库账号以实际为准**。
- 管理端统一用 header 名 **`token`**（只用于 `/admin/**`）。

### 3.2 用户端 token（`authentication` 头）

`POST /user/user/login` 走微信真实 code，本地拿不到，只能**自己签**：

```powershell
# 1) 查真实 user id
#    SELECT id FROM user LIMIT 1;
# 2) 生成 token（纯标准库，密钥 <JWT_ADMIN_SECRET> 仅测试环境；切勿贴到第三方网站）
python gen_token.py 4
```

`gen_token.py`（放在任意目录，按 v2 §1.4.2）：

```python
import hmac, hashlib, base64, json, time, sys
secret = "<JWT_ADMIN_SECRET>"
key = hashlib.sha256(secret.encode()).digest()
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b"=")
uid = int(sys.argv[1]) if len(sys.argv) > 1 else 4
h = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
p = b64(json.dumps({"userId": uid, "exp": int(time.time()) + 7200}, separators=(",", ":")).encode())
signing = h + b"." + p
sig = b64(hmac.new(key, signing, hashlib.sha256).digest())
print((signing + b"." + sig).decode())
```

- 用户端统一用 header 名 **`authentication`**。
- **不要**加 `Bearer` 前缀（网关按整串解析）。
- 自检：401 时看网关日志 `请求缺少 token` / `JWT 校验失败`。密钥用的是 `SHA-256("<JWT_ADMIN_SECRET>")`，不是明文。

### 3.3 查真实 categoryId（**不要用 categoryId=1**）

```sql
SELECT id FROM category WHERE type = 1 LIMIT 1;   -- 真实药品分类，转储里是 11/12/16...
```

- 真实库里 `category_id = 1` 不存在；v1 硬编码的 `categoryId=1` 只对 H2 种子数据成立。
- 把这个值通过 `-Jcid=<值>` 传给计划。

### 3.4 其它数据

```sql
SELECT id FROM drug LIMIT 1;          -- -JdrugId=...   （T01/T13/T22）
SELECT id FROM address_book WHERE user_id = 4 LIMIT 1;  -- -JaddressBookId=...（T01 下单）限定 user_id
SELECT number, id FROM orders WHERE status = 1 LIMIT 1;  -- 待付款订单（T14/T16）
```

### 3.5 清缓存（保证从 miss 开始，安全规则）

```powershell
# 1) 先只读计数确认前缀与数量（不要直接删！）
redis-cli -n 10 --scan --pattern "ysd:catalog:drug:*" | Measure-Object -Line
# 2) 数量可接受（示例上限 5000）再分批删
redis-cli -n 10 --scan --pattern "ysd:catalog:drug:*" | Select-Object -First 1000 | ForEach-Object { redis-cli -n 10 del $_ }
# 3) 复核
redis-cli -n 10 --scan --pattern "ysd:catalog:drug:*" | Measure-Object -Line
```

> ⚠️ **禁止** `FLUSHDB` / `FLUSHALL`；**必须**显式 `-n 10`；先 `--scan` 计数确认再删；单次删除设上限。

---

## 4. 跑批次

```powershell
cd <repo-root>\jmeter

# 批次 1（正面性能/正确性 6 项里的前 5 项）
.\run-batch1.bat -Jtoken=<用户token> -JadminToken=<管理token> -Jcid=11 -JdrugId=46

# 批次 2（含需要手动注入故障的 4 项）
.\run-batch2.bat -Jtoken=<用户token> -JadminToken=<管理token> -Jcid=11 -JdrugId=46 -JorderNumber=<真实待支付订单号>

# 全部
.\run-all.bat -Jtoken=... -JadminToken=... -Jcid=11 -JdrugId=1
```

脚本行为：

- 非 GUI 模式：`jmeter -n -t plans\xxx.jmx -l results\xxx.jtl -e -o reports\xxx\`
- **每个计划前检查 `reports\xxx\` 不存在**；已存在会 `SKIP` 并提示 `rmdir /s /q reports\xxx`（JMeter `-e -o` 要求目标目录不存在）。
- 结束后打印每个 HTML 报告路径。

### 解析结果

```powershell
python .\parse-results.py          # 读 results\*.jtl
```

输出：`计划名 | 请求数 | 平均(ms) | P50(ms) | P95(ms) | P99(ms) | 错误率`。

---

## 5. 每个测试的关键操作点

> 命令中的 `<pid>` / `<容器名>` 需替换为实际值；容器名用 `docker ps` 查。

### 计划 1 · T01 基准测试（正面）
- 4 个接口：`GET /user/drug/list`、`POST /user/shoppingCart/add`、`POST /user/order/submit`、`PUT /admin/drug`。
- **缓存命中 / 未命中必须分开跑**：先清缓存跑一遍（miss），再预热一遍（hit）。
- 低/中/稳定三档并发分别跑（用 `-Jthreads=` `-Jramp=` `-Jloops=` 覆盖）。
- 前置：用户购物车非空、地址簿存在，否则下单接口会失败。

### 计划 2 · T10 缓存击穿（正面）
- **先删热点 key 再立即施压**（计划内 50 线程 + Synchronizing Timer 同时释放）：
  ```powershell
  # 预热（让 key 存在）
  curl.exe -s "http://localhost:9000/user/drug/list?categoryId=11" -H "authentication: <用户token>"
  redis-cli -n 10 exists ysd:catalog:drug:11        # 期望 1
  redis-cli -n 10 del ysd:catalog:drug:11           # 定向删除
  # 立即跑
  .\run-batch1.bat -Jcid=11 -Jtoken=<用户token>
  ```
- 验证：`SHOW GLOBAL STATUS LIKE 'Com_select';` 前后差值**应接近 1**（回源被合并）；若≈50 说明锁没生效。

### 计划 3 · T11 缓存雪崩（正面）
- 分类列表在 `plans/categories.csv`（8 个真实药品分类），可用 `-JcsvFile=` 覆盖。
- 先批量预热这些分类 → 定向前缀批量删除 → 施压。
- 验证：抽样 TTL `redis-cli -n 10 --scan --pattern "ysd:catalog:drug:*"` 逐个 `ttl`，**期望值分散**（抖动 600+rand(0,120)s 生效）。

### 计划 4 · §6.1 Feign 熔断降级（正面）
- 压的是 **加购** `POST /user/shoppingCart/add`（真正触发 `CatalogClient` 的是加购，不是订单详情）。
- **必须先清空购物车或用不重复的 drugId**：商品已在购物车时不会调 Feign。
- 中途 **kill catalog**：
  ```powershell
  jps -l                                # 找 YsdCatalogServiceApplication 的 pid
  taskkill /PID <pid> /F                # Windows
  # 或 docker stop ysd-catalog-service
  ```
- 验证：降级返回 `code != 1`、`msg=商品服务繁忙，请稍后重试`；**错误率上升但 P95 不飙升**（快速失败特征）。P95 飙升说明没走 fallback，只是超时。

### 计划 5 · §6.2 网关 QPS 限流（正面）
- **先读 Nacos 规则**：配置管理 → `ysd-gateway-sentinel-gw-flow`（`SENTINEL_GROUP`）→ 看 `count` 与 `grade`。
  - ⚠️ `grade` 必须 = 1（QPS）。`grade=0` 是线程数模式，网关不维护线程计数 → 规则恒放行。
  - 压测前连续观察几分钟确认 `count` 不再变化。
- 必须**真并发**（顺序发请求测不出限流）；逐步加大 `-Jthreads=` 直到超过 `count`。
- 验证：出现 **HTTP 429** 且比例随并发上升；`curl http://127.0.0.1:8720/gateway/getRules` 看规则；`~/logs/csp/sentinel-block.log`。

### 计划 6 · T13 延迟双删多实例（正面）
- **需要起第二个 catalog 实例**（两实例共用 Redis db10）：
  ```powershell
  # 方式 A：jar
  java -jar ysd-catalog-service\target\ysd-catalog-service-1.0-SNAPSHOT.jar --server.port=8184
  # 方式 B：IDE 复制一份启动配置，改 -Dserver.port=8184
  # 确认两实例都注册到 Nacos
  ```
- 计划含**两个线程组**：读线程组 `GET /user/drug/list`（20×200）+ 写线程组 `PUT /admin/drug`（1×50），并行。
- 验证：写后 **静置 >1s** 单独读一次，`name` 应等于最新写值；`redis-cli -n 10 get ysd:catalog:drug:{cid}` 与 DB 对比；多轮统计「出现旧值」比例。
- 收尾：关掉第二实例。

### 计划 7 · T12 Redis 不可用（诚实记录局限）
- **需要停 Redis**：
  ```powershell
  docker ps                                # 查 redis 容器名
  docker stop <redis容器名>
  # 或（会清掉未落盘数据，仅测试环境）
  redis-cli -n 10 shutdown nosave
  ```
- 起压后停 Redis，观察 2 分钟，再 `docker start <redis容器名>` 恢复。
- 验证：源码现状（`CatalogCacheHelper.get` 与 `ShopController` 的 get **无 try/catch**）→ 两个接口都 **500**，**不会回源查 DB**；用「状态码 + 耗时」判断是快速失败还是挂起（决定有没有超时配置）。恢复后看 `keyspace_misses` 回落、`Threads_connected` 回落。
- ⚠️ 要分开写「**现状**」与「**假设把 get 改成 catch 后回源**」两件事，后者才是要防的放大链。

### 计划 8 · T14 消费者停止（诚实记录局限）
- **需要停 push-service**：
  ```powershell
  taskkill /PID <push-service 的 pid> /F
  # 或 docker stop ysd-push-service
  ```
- 起压（触发 `PUT /user/order/payment`）→ 停 push-service → 观察 2 分钟 → 恢复。
- 监控队列：`rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers`（或管理界面 15672）。
- 验证（**防误判**）：本项目队列是每个实例的 `AnonymousQueue`（exclusive/auto-delete），停实例后 fanout 无绑定队列 → **消息被丢弃，不堆积**，看不到 Ready/Unacked 增长。

### 计划 9 · T16 发送成功但业务未提交（诚实记录局限）
- **需要停 RabbitMQ broker**（仅测试环境），或方式 B 让 `wsPushProducer.push` 抛异常（**验证后务必还原**）。
- 需要**一个真实待支付订单号**（`-JorderNumber=...`）。
- 调 `PUT /user/order/payment` → 观察 DB 是否已更新、消息是否丢失。
- 验证：`SELECT status, pay_status, checkout_time FROM orders WHERE number = {orderNumber};`（说明 DB 先提交了）；根因结论是「普通事务 + 发消息不天然原子」，修复方向（事务消息 / Outbox）属**建议补充**。

#### T16 前置条件（重要）

T16 压 `PUT /user/order/payment`，需要真实待支付订单号。
**当前库中无 status=1 的订单**（`SELECT number FROM orders WHERE status=1 LIMIT 1` 返回空），必须先手动创建：

1. 起服务：gateway + user + catalog + order
2. 加购物车：

   ```powershell
   curl.exe -s -X POST http://localhost:9000/user/shoppingCart/add ^
     -H "Content-Type: application/json" ^
     -H "authentication: <user-token>" ^
     -d "{\"drugId\": 46}"
   ```

3. 下单（不支付）：

   ```powershell
   curl.exe -s -X POST http://localhost:9000/user/order/submit ^
     -H "Content-Type: application/json" ^
     -H "authentication: <user-token>" ^
     -d "{\"addressBookId\": <真实地址id>, \"payMethod\": 1, \"remark\": \"T16\", \"estimatedDeliveryTime\": \"2026-10-16 20:00:00\", \"deliveryStatus\": 1, \"tablewareNumber\": 1, \"tablewareStatus\": 1, \"packAmount\": 1, \"amount\": 10}"
   ```

4. 查库拿订单号：

   ```sql
   SELECT number FROM orders ORDER BY id DESC LIMIT 1;
   ```

5. 传参：

   ```cmd
   run-batch2.bat -Jtoken=xxx -JorderNumber=<真实值>
   ```

   > 每轮 T16 会把这笔订单支付掉（status 变为 2），**重复跑需重新执行步骤 2~4** 造新订单。

### 计划 10 · T22 并发加购竞态（可选）
- **先清空该用户购物车**：
  ```powershell
  curl.exe -s -X DELETE http://localhost:9000/user/shoppingCart/clean -H "authentication: <用户token>"
  ```
- 计划内 50 线程 + Synchronizing Timer=50 同时加购**同一 drugId**。
- 验证（核心）：
  ```sql
  SELECT COUNT(*) FROM shopping_cart WHERE user_id = {uid} AND drug_id = {did};   -- 期望 1
  ```
  若 >1 → check-then-act 竞态（`ShoppingCartServiceImpl.addShoppingCart`）。
- 清理：`DELETE FROM shopping_cart WHERE user_id={uid} AND drug_id={did};`（只在测试库）。

#### T22 前置与结论（重要）

已确认 `shopping_cart` 表**只有 `PRIMARY(id)` 索引，无 `(user_id, drug_id)` 唯一索引**。

前置：

- 跑 T22 前必须清空购物车：

  ```powershell
  curl.exe -s -X DELETE http://localhost:9000/user/shoppingCart/clean -H "authentication: <user-token>"
  ```

跑完后核对：

```sql
SELECT COUNT(*) FROM shopping_cart WHERE user_id = 4 AND drug_id = 46;
```

预期结果：

- 若 `COUNT > 1` → 确认 check-then-act 竞态（**真 Bug**）
- 这是**已确认的竞态缺陷，不是偶发**

结论写法：

> 并发加购产生重复购物车行，根因是 `ShoppingCartServiceImpl` 的 check-then-act 竞态，且 DB 无 `(user_id, drug_id)` 唯一约束兜底。

---

## 6. 需要你手动确认 / 准备的项

| # | 项 | 怎么拿 / 怎么做 |
|---|---|---|
| 1 | **管理端 token** | `curl POST /admin/employee/login`（admin/<your-password>），取 `data.token`；传给 `-JadminToken=` |
| 2 | **用户端 token** | `python gen_token.py <userId>`（见 §3.2），传给 `-Jtoken=`；不要加 Bearer |
| 3 | **categoryId** | `SELECT id FROM category WHERE type=1 LIMIT 1;`，传给 `-Jcid=`（**不要用 1**） |
| 4 | **drugId** | `SELECT id FROM drug LIMIT 1;`，传给 `-JdrugId=` |
| 5 | **addressBookId** | `SELECT id FROM address_book WHERE user_id = 4 LIMIT 1;`，传给 `-JaddressBookId=`（T01 下单） |
| 6 | **orderNumber / orderId** | `SELECT number, id FROM orders WHERE status=1 LIMIT 1;`，传给 `-JorderNumber=` / `-JorderId=`（T14/T16） |
| 7 | **JMeter 安装路径** | 设 `JMETER_HOME`，或把 `jmeter.bat` 加入 PATH |
| 8 | **Nacos 限流阈值** | 打开 `ysd-gateway-sentinel-gw-flow`，确认 `grade=1` 且 `count` 稳定（§6.2） |
| 9 | **Redis / RabbitMQ / push 容器名** | `docker ps` 查实际容器名，填入 §5 的停止命令 |
| 10 | **第二 catalog 实例端口** | 默认示例 `8184`，与现有一致即可（避免端口冲突） |

### 需要手动注入故障的测试（**必须人工介入，脚本不会自动注入**）

| 测试 | 注入动作 |
|---|---|
| §6.1 | 起压中途 kill catalog（`jps -l` → `taskkill /PID <pid> /F`） |
| T13 | 先起第二 catalog 实例（`--server.port=8184`） |
| T12 | 停 Redis（`docker stop <redis容器>` / `redis-cli -n 10 shutdown nosave`） |
| T14 | 停 push-service（`taskkill /PID <pid> /F`） |
| T16 | 停 RabbitMQ broker（或改代码让 push 抛异常，**验证后还原**） |
| T22 | 先清空购物车（`DELETE /user/shoppingCart/clean`） |

---

## 7. 已知局限（诚实记录，别当成功）

- **T12 / T14 / T16 是「观察现状 + 说明改造需求」**，不是「验证已有能力」：
  - T12 现状 = Redis 挂 → 请求失败（不是无限回源）；是否会冲垮 DB 取决于是否有人改了 `get` 的异常处理。
  - T14 现状 = 广播 + 匿名队列 → **丢弃不堆积**；要可靠堆积需改造为持久化队列 + 手动 ack + 死信（属建议补充）。
  - T16 现状 = DB 更新与发消息不原子；事务消息 / Outbox 属建议补充。
- **T22 本项目无库存字段、无超卖逻辑**，是等价实验；单轮安全不代表任意并发都安全，需多轮。
- 本项目 **MQ 消费端无业务写入** → 「重复消费导致业务重复处理」不成立；真正的幂等风险在下单 / 加购等业务写接口。
- 所有并发数字（5/10/20/40/50/100…）都是**示例值**，不是本项目应该达到的标准。
- 「接口 200」≠「业务正确」，「错误率 0%」≠「数据一致」——结论必须落到具体证据。
