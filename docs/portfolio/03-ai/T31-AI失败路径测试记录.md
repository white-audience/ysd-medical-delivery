# T31 — AI 失败路径与下单拦截测试记录

> 日期：2026-10-21  
> 范围：`ysd-ai-service`（入参校验、工具降级、RAG 空召回、记忆隔离、审核规则）与 `ysd-order-service`（下单处方药闸门）  
> 目标：证明 AI 依赖异常时**不会 500、不会瞎编、不会绕过审核**，并验证处方药下单的 fail-closed 行为。

---

## 1. 测试结论

| 模块 | 新增用例 | 结果 |
| --- | --- | --- |
| `ysd-ai-service` | 14 | **14/14 通过** |
| `ysd-order-service` | 6 | **6/6 通过** |
| 本地真实链路 E2E | 5 个场景 | **5/5 符合预期** |

关键结论：

1. 空问题、超长问题在进入模型前就被拦截，**不消耗 Token**；
2. 下游 `catalog-service` 抛异常时，工具层返回兜底话术，不会把 500 抛给用户；
3. RAG 召回为空时不再调用大模型，直接返回“未检索到规则”；
4. 相同 `conversationId` 下，不同用户的记忆键不同，上下文互不可见；
5. 处方药下单：未上传处方直接拦截；AI 拒绝/超时/宕机/catalog 不可用全部 **fail-closed**，不写订单。

---

## 2. 单元测试设计

> 说明：测试使用纯 Mockito（`@ExtendWith(MockitoExtension.class)`），不启动 Spring 上下文。  
> 原因：AI 服务启动依赖 Nacos、Redis Stack、Ollama、DeepSeek Key；失败路径测试关注的是**代码分支与断言**，用 mock 更稳定、更快。真实 Feign/Nacos 链路由第 4 节的 E2E 覆盖。

### 2.1 `AiDrugControllerValidationTest`（2 个）

| 用例 | 输入 | 断言 |
| --- | --- | --- |
| CHAT-01 空问题 | `"   "` | HTTP 400；`code=0`；`msg` 包含“请输入”；`chatClient.prompt()` **从未调用** |
| CHAT-02 超长问题 | 2001 个字符 | HTTP 400；`msg` 包含“2000”；`chatClient.prompt()` 从未调用 |

### 2.2 `DrugQueryToolTest`（3 个）

| 用例 | 模拟 | 断言 |
| --- | --- | --- |
| TOOL-01 下游工具挂掉 | `CatalogClient.searchDrugByName` 抛 `RuntimeException("Read timed out")` | 方法**不抛异常**；返回文本包含“暂不可用”“请稍后重试”；不包含异常类名/超时原文 |
| TOOL-02 空关键词 | `"  "` | 返回“请输入药品名称或症状关键词。”，不调用下游 |
| TOOL-03 处方药 | 返回 `prescriptionType=1` | 文本包含“【处方药】”“需凭处方购买” |

### 2.3 `AiPrescriptionControllerTest`（2 个）

| 用例 | 模拟 | 断言 |
| --- | --- | --- |
| RAG-01 检索为空 | `vectorStore.similaritySearch` 返回空列表 | `code=0`；`msg` 包含“未检索到平台处方药购买规则”；`chatClient.prompt()` **从未调用** |
| RAG-02 检索到规则 | 返回 1 个规则片段 | 模型被调用；捕获 `user()` 入参，断言 prompt 同时包含规则原文和药品名 |

> 代码层保证：`AiPrescriptionController.retrievePrescriptionRules()` 返回 `null` 时，`check()` 直接返回错误，不进入 LLM 调用。这样“没有依据”与“编造”在代码路径上被切断，而不是只靠 system prompt 约束。

### 2.4 `PrescriptionAuditServiceTest`（5 个）

| 用例 | 模拟 | 断言 |
| --- | --- | --- |
| AUDIT-01 纯 OTC | 药品 `prescriptionType=0` | `approved=true`；reason 含“不含处方药”；处方药列表为空 |
| AUDIT-02 处方药无凭证 | `prescriptionType=1` + `prescriptionUrl=null` | `approved=false`；reason 含“请先上传处方”和药品名 |
| AUDIT-03 处方药有凭证 | `prescriptionType=1` + URL | `approved=true`；`prescriptionDrugs` 包含药品名；reason 含“待药师复核” |
| AUDIT-04 下游异常 | `getDrugInfoById` 抛异常 | `approved=false`；reason 含“不可用” |
| AUDIT-05 药品不存在 | 返回 `Result.success(null)` | `approved=false`；reason 含“查询失败” |

### 2.5 `ConversationMemoryIsolationTest`（2 个）

| 用例 | 断言 |
| --- | --- |
| MEM-01 记忆键 | `memoryKey("1001","conv-same") = "1001:conv-same"`，与 `"1002:conv-same"` 不相等 |
| MEM-02 内容隔离 | A 写入“我对青霉素过敏”，B 写入“我头痛”；`memory.get(keyA)` 只包含 A 的消息，且不包含 B 的消息 |

### 2.6 `OrderPrescriptionAuditTest`（6 个）

| 用例 | 场景 | 断言 |
| --- | --- | --- |
| ORDER-01 | 购物车只有 OTC | 正常写订单；`AiPrescriptionClient` **零调用** |
| ORDER-02 | 处方药、未传 `prescriptionUrl` | 抛 `OrderBusinessException`，消息含“请先上传处方”；不写订单；AI 零调用 |
| ORDER-03 | 处方药 + AI approved | 下单成功；捕获 Feign 请求，断言 `drugIds=[51]`、`prescriptionUrl`、`userId` 正确 |
| ORDER-04 | AI 返回 `approved=false` | 抛业务异常，消息等于 AI 返回的 reason；不写订单 |
| ORDER-05 | AI 超时/抛异常 | 抛业务异常，消息含“处方审核服务暂时不可用”；不写订单 |
| ORDER-06 | catalog 查询失败 | 抛业务异常，消息含“药品信息服务暂时不可用”；AI 零调用；不写订单 |

---

## 3. 执行命令与结果

```bash
# AI 模块
mvn -pl ysd-ai-service -am test

# 下单处方审核
mvn -pl ysd-order-service -am test \
  -Dtest=OrderPrescriptionAuditTest -Dsurefire.failIfNoSpecifiedTests=false
```

AI 模块输出：

```text
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

下单模块输出：

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试日志：

- `logs/ai-test.log`
- `logs/order-test.log`

`ysd-order-service` 全量回归（排除已登记的 `@Tag("bug-evidence")` 类）：

```text
Tests run: 20, Failures: 0, Errors: 0, Skipped: 5
BUILD SUCCESS
```

> 直接跑全量仍会看到 `OrderStateMachineTest` 的 3 个预期失败（已完成订单重复 confirm/cancel、重复支付），这是仓库里已登记的 BUG-4/BUG-5 证据用例，与本次改动无关。

---

## 4. 真实链路 E2E

本地启动 `catalog-service:8084`、`order-service:8085`、`ai-service:8088`、Nacos、MySQL、Redis，使用用户 4 的 JWT 直接调用 `/user/order/submit`。测试数据：OTC `drugId=46`（999 感冒灵颗粒），处方药 `drugId=51`（阿莫西林胶囊）。

| # | 场景 | 请求 | 实际结果 |
| --- | --- | --- | --- |
| E2E-01 | OTC 正常下单 | OTC + 无处方 | `code=1`，订单号 `1792584796280`，成功写入订单并清空购物车 |
| E2E-02 | 处方药未上传处方 | Rx + `prescriptionUrl=null` | `code=0`，提示“购物车包含处方药，请先上传处方后再下单”，无订单写入 |
| E2E-03 | 处方药 + AI 审核通过 | Rx + 处方 URL | `code=1`，订单号 `1792584797749`，成功写入订单 |
| E2E-04 | AI 服务宕机 | 停掉 `ai-service`，Rx + 处方 URL | **0.11 秒**返回 `code=0`，提示“处方审核服务暂时不可用，为保障用药安全暂不能下单，请稍后重试”，无订单写入 |
| E2E-05 | 省略可选字段 | OTC，请求体省略 `packAmount` / `tablewareNumber` / `tablewareStatus` | `code=1`，订单号 `1792585589556`，`tableware_status` 按默认值 1 落库 |

原始结果：`docs/05-测试与压测/T31-day4-e2e-results.json`。

E2E-04 说明：即使 AI 服务完全不可用，下单接口也不会挂起等待 3 秒；连接拒绝立即触发异常，`OrderServiceImpl` 捕获后 fail-closed。若 AI 服务可连接但响应慢，则由 Feign `readTimeout=3000ms` 触发超时后同样拦截。

---

## 5. 失败路径矩阵

| 失败点 | 预期行为 | 验证方式 |
| --- | --- | --- |
| 用户输入为空/超长 | 400，直接返回提示，不调模型 | CHAT-01/02 |
| catalog-service 超时/异常 | 工具返回兜底话术，不抛 500 | TOOL-01 |
| 向量库为空 | 返回错误，不调模型，不编造 | RAG-01 |
| 跨用户相同 conversationId | 记忆键不同，内容互不可见 | MEM-01/02 |
| AI 审核返回不通过 | 下单被拦截，不写订单 | ORDER-04 |
| AI 审核超时/抛异常 | fail-closed，不写订单 | ORDER-05 + E2E-04 |
| catalog 不可用 | 无法判断处方属性，fail-closed | ORDER-06 |
| 处方药未上传凭证 | 下单前直接拦截 | ORDER-02 + E2E-02 |
| OTC 订单 | 不调用 AI，正常下单 | ORDER-01 + E2E-01 |
| 下单可选字段缺省 | 使用默认值落库，不报 500 | ORDER-01 + E2E-05 |

---

## 6. 测试过程中发现并修复的问题

1. **下单可选字段缺省导致 500**：`Orders` 实体的 `packAmount` / `tablewareNumber` 原为基本类型，`BeanUtils.copyProperties` 从可空的 `OrdersSubmitDTO` 复制会触发 NPE；同时数据库 `orders.tableware_status` 为 `NOT NULL`，客户端不传时显式插入 null 会触发约束异常，最终被全局处理器统一成“未知错误”。已把两个实体字段改为 `Integer`，并在 `submitOrder` 中为 `tablewareStatus` 补默认值 1（按餐量提供）。E2E-05 已验证。
2. **增量构建残留资源**：此前 `target/classes/rag/README.md` 未清理，导致 RAG 仍灌入旧 README；已改用 `clean package` 并验证只加载 8 份业务文档。
3. **只构建 AI 模块会使用本地仓库旧 `ysd-api`**：Feign 路径仍是旧的 `/inner/dish/drug/search`。构建命令统一使用 `-am`，E2E 已验证 `/inner/drug/{id}/info` 可达。

---

## 7. 当前测试边界

- **没有真实调用大模型**：单测断言的是“失败时不进入模型/上下文正确”，不评价模型回答质量；模型质量由 Day 3 的 20 题 RAG 评测覆盖。
- **AI 审核是确定性规则**：只校验药品属性与处方凭证是否上传，不做 OCR、签章验真和处方内容审核；真实上线需接入药师人工复核或第三方处方审核服务。
- **药品组合未覆盖**：购物车行只有 `combo_id` 时不会触发处方审核，需要后续扩展组合明细。
- **E2E 为本地单实例**：未验证多实例注册刷新、网络分区、Sentinel 规则真正触发熔断等场景；这些需要在压测/故障演练环境补充。
