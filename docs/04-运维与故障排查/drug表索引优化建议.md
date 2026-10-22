# drug 表索引优化建议（业务场景驱动 · 慢 SQL 排查演练）

> 演练日期：2026-10-20
> 数据库：MySQL 8.0.42，容器 `Study`，库 `ysd_take_out`
> 表规模：`drug` 100,025 行（100,000 行 `测试药品_N` + 25 行真实药品）
> 结论性质：**测试库实测**，不代表线上生产负载。所有数字均来自本次实际执行，未做模拟或伪造。

---

## 1. 背景与范围

### 1.1 背景

`drug` 表在本次演练前只有主键、`name` 唯一索引和两个单列索引（`category_id`、`status`），且 `status` 全部为 1、`category_id` 每个分类约 1.2 万行，索引选择性很差。业务侧同时存在：

- C 端按分类浏览药品；
- 管理端按名称/分类/状态分页；
- AI 问诊按名称或适应症模糊搜索；
- 组合详情关联查询；
- 工作台状态计数。

本次演练从业务入口出发，完整走一遍“业务场景 → SQL → 慢日志/计时 → EXPLAIN → 判断 → 候选方案 → 加索引 → 验证 → 回归”。

### 1.2 覆盖范围

| 覆盖 | 不覆盖 |
|---|---|
| `drug` 表及其直接关联的 `drug_combo_item` 查询 | `orders` / `order_detail` / `shopping_cart` 的索引优化 |
| 等值、排序、模糊、关联、分页、计数六类查询形态 | Java / XML / YML 业务代码修改 |
| 索引新增、删除、INVISIBLE、FULLTEXT 实验 | 生产环境变更 |
| 写路径 100 次插入计时 | 压测 / 并发一致性 |

### 1.3 最终索引状态（2026-10-20）

| 索引 | 列 | 类型 | 状态 |
|---|---|---|---|
| `PRIMARY` | `id` | BTREE | 可见 |
| `idx_drug_name` | `name` | BTREE UNIQUE | 可见 |
| `idx_drug_status` | `status` | BTREE | **INVISIBLE** |
| `idx_drug_cat_status_time` | `category_id, status, create_time` | BTREE | 可见（新增） |
| `idx_drug_category_id` | `category_id` | BTREE | **已删除** |

> T5 收尾时按用户决策删除了 `ft_drug_name_indications` 实验索引。S3 的 FULLTEXT 数据保留为实验记录；如后续修改 Mapper，可按第 4 节 S3 的 DDL 重建。
> 注意：DROP FULLTEXT 后 InnoDB 仍保留隐藏的 `FTS_DOC_ID_INDEX`（2.52 MB）；彻底清除需重建表（`OPTIMIZE TABLE drug`），本次未执行。

---

## 2. 环境与数据基线

### 2.1 慢日志配置（演练期间）

```sql
slow_query_log = ON
log_queries_not_using_indexes = ON
long_query_time = 0.01
log_output = FILE
slow_query_log_file = /var/lib/mysql/3747f82517a7-slow.log
ngram_token_size = 2
```

慢日志里同时存在 order-service 的定时查询（每分钟一次，`orders where status = ...`，Rows_examined=33）。按 SQL 片段筛选时不要被它干扰。

### 2.2 数据分布

| 项目 | 实测值 | 对索引判断的影响 |
|---|---|---|
| 总行数 | 100,025 | — |
| 测试数据 | 100,000 行 `测试药品_N`，名称唯一 | 模糊查询命中率可控 |
| 真实药品 | 25 行（id 46-69、73） | 适应症文本只在这部分存在 |
| `status` | 全部为 1 | `idx_drug_status` 基数=1，等于无过滤能力 |
| `category_id` | 11/12/16/17/18/19/20/21 八个值，各 12,296~12,668 行 | 单分类约占 12%，选择性低 |
| `create_time` | 31 个不同值；10 万测试行几乎同一时刻 | ORDER BY 存在大量并列值 |
| 初始表大小 | 数据 21.56 MB，索引 11.55 MB | — |

### 2.3 演练前索引基数

| 索引 | Cardinality |
|---|---|
| `PRIMARY(id)` | 98,775 |
| `idx_drug_name(name)` | 98,775 |
| `idx_drug_category_id(category_id)` | 7 |
| `idx_drug_status(status)` | **1** |

> 这两个低基数索引是后面 S1/S2/S3/S5 反复出现问题的根源。

### 2.4 中文关键词的客户端注意事项

容器 MySQL 客户端直接传中文会因字符集不匹配导致 `LIKE '测试药品%'` 返回 0。复现时必须加：

```bash
docker exec Study mysql --default-character-set=utf8mb4 -uroot -proot ...
```

否则会出现“明明有数据却查不到”的假象。

---

## 3. 场景清单与对应 SQL

| # | 业务场景 | 实际 SQL（与代码一致） | 来源 | 涉及列 |
|---|---|---|---|---|
| S1 | C 端按分类浏览在售药品 | `select * from drug where category_id = ? and status = 1 order by create_time desc` | `ysd-catalog-service/src/main/java/com/ysd/controller/user/DrugController.java:31-41` → `service/impl/DrugServiceImpl.java:210-230` → `resources/mapper/DrugMapper.xml:85-99`；文档 `docs/05-测试与压测/JMeter压测与故障演练手册-v2.md:648` | `drug(category_id, status, create_time, *)` |
| S2 | 管理端药品分页查询 | `select d.*, c.name categoryName from drug d left outer join category c on d.category_id=c.id where d.name like concat('%',?,'%') and d.category_id=? and d.status=? order by d.create_time desc limit ?,?`（PageHelper 另发 count） | `ysd-catalog-service/.../controller/admin/DrugController.java:48-54` → `DrugServiceImpl.java:86-91` → `DrugMapper.xml:22-37`；文档 `docs/02-架构设计与开发/微服务改造方案.md:341` | `drug(name, category_id, status, create_time)`、`category(id, name)` |
| S3 | AI 问诊按名称/症状搜索 | `SELECT id,name,prescription_type,manufacturer,approval_number,indications,contraindications,status FROM drug WHERE status=1 AND (name LIKE CONCAT('%',?,'%') OR indications LIKE CONCAT('%',?,'%')) ORDER BY prescription_type ASC LIMIT 10` | `ysd-catalog-service/.../controller/inner/InnerDrugController.java:33-37` → `DrugServiceImpl.java:232-245` → `DrugMapper.xml:109-118`；`ysd-ai-service/.../tool/DrugQueryTool.java:24-37` | `drug(status, name, indications, prescription_type)` |
| S4 | 药品组合详情（组合含哪些药） | `select sd.name, sd.quantity, d.image, d.description from drug_combo_item sd left join drug d on sd.drug_id=d.id where sd.combo_id=?` | `ysd-catalog-service/.../controller/user/DrugComboController.java:47-52` → `DrugComboServiceImpl.java:200-202` → `DrugComboMapper.java:75-78` | `drug_combo_item(combo_id)`、`drug(id)` |
| S5 | 管理端工作台药品总览 | `select count(id) from drug where status = 1`（再执行 `status = 0`） | `ysd-report-service/.../service/impl/WorkspaceServiceImpl.java:131-143` → `ysd-report-service/.../mapper/DrugMapper.xml:84-90` | `drug(status)` |
| S6（未演练） | 批量删除药品前的组合关联校验 | `select combo_id from drug_combo_item where drug_id in (...)` | `DrugServiceImpl.java:114` → `DrugComboItemMapper.xml:6-11` | `drug_combo_item(drug_id)` 无索引 |
| S7（范围外） | 加购物车前查同款 | `select * from shopping_cart where user_id=? and drug_id=? and spec=? ...` | `ysd-order-service/.../resources/mapper/ShoppingCartMapper.xml:7-23`；`docs/03-源码学习/03-核心业务链路.md:367-378` | `shopping_cart` 仅主键，表为空 |

---

## 4. 逐场景排查记录

### S1. C 端按分类浏览在售药品

#### 业务描述

用户在 C 端点开某个分类，前端一次性拉取该分类下所有在售药品及规格。真实链路有 Redis 缓存（`ysd:catalog:drug:{categoryId}`），本场景计时的是缓存未命中时的回源 SQL。

#### SQL

```sql
select * from drug
where category_id = 12 and status = 1
order by create_time desc;
```

#### 当前表现

| 次数 | 耗时 |
|---|---|
| 1 | 125.7 ms |
| 2 | 69.5 ms |
| 3 | 71.9 ms |
| 平均 | **89.2 ms** |

慢日志三条均记录：`Query_time 0.125214 / 0.069515 / 0.071865`，`Rows_sent=12668`，`Rows_examined=12668`。

#### EXPLAIN

| type | possible_keys | key | rows | filtered | Extra |
|---|---|---|---|---|---|
| index_merge | idx_drug_category_id, idx_drug_status | idx_drug_category_id, idx_drug_status | 11279 | 100.00 | Using intersect; Using where; **Using filesort** |

#### EXPLAIN ANALYZE

```text
-> Sort: drug.create_time DESC  (actual time=55.4..57 rows=12668)
    -> Filter: status=1 and category_id=12  (actual time=0.4..45.5 rows=12668)
        -> Intersect rows sorted by row ID  (actual time=0.237..44 rows=12668)
            -> Index range scan idx_drug_category_id (category_id=12)  rows=12668
            -> Index range scan idx_drug_status (status=1)            rows=100014
```

#### 判断依据

1. 优化器选择 **index_merge intersect**；`idx_drug_status` 基数=1，却扫了约 10 万行（100,014），对结果没有过滤贡献。
2. `category_id=12` 只筛掉 87%，仍返回 12,668 行；`select *` 需要 12,668 次随机回表，回表阶段约 44 ms。
3. filesort 约 10 ms，不是主因。
4. 结构性问题是接口无分页，一次返回 1.2 万行宽记录；索引无法减少回表和返回行数。

#### 候选方案

| 方案 | 操作 | 预期 | 风险 |
|---|---|---|---|
| A | `(category_id, status, create_time)` | 消除 index_merge 和 filesort | 新增约 3.5 MB；`idx_drug_category_id` 变成冗余 |
| B | `(category_id, create_time)` | 消除 filesort，索引更小 | 对“同分类按状态筛选”帮助较小 |
| C | 接口加分页 | 直接减少返回行数 | 需改 Java/前端，超出本次范围 |

#### 最终选择与验证过程

1. 先执行方案 A：

```sql
ALTER TABLE drug
  ADD INDEX idx_drug_cat_status_time (category_id, status, create_time),
  ALGORITHM=INPLACE, LOCK=NONE;
```

2. **效果未达预期**：`possible_keys` 已包含新索引，但 `key` 仍是两个单列索引，`Using filesort` 仍在；耗时 63.4/65.3/63.7 ms。89→64 ms 的改善主要来自 buffer pool 预热，不是索引生效。
3. 诊断：
   - `FORCE INDEX (idx_drug_cat_status_time)`：实际 **16.5 ms**，`Backward index scan`；
   - `SET optimizer_switch='index_merge=off'`：优化器改选新索引，`type=range`；
   - 原因：index_merge 估算 `cost=2360`，新索引 `cost=2446`，相差 86；优化器低估了 status 分支扫 10 万行 + intersect + 回表的代价。
4. 调整：删除被复合索引最左前缀覆盖的旧索引：

```sql
ALTER TABLE drug DROP INDEX idx_drug_category_id, ALGORITHM=INPLACE, LOCK=NONE;
ANALYZE TABLE drug;
```

5. 删除后优化器改选复合索引。

#### 优化后

| 指标 | 优化前 | 优化后 |
|---|---|---|
| 客户端 3 次平均 | 89.2 ms | **33.9 ms**（30.5/36.4/34.6） |
| T3 复测 | — | 38.2 ms（35.8/44.6/34.1） |
| 慢日志 | 0.125 / 0.070 / 0.072 | 0.030 / 0.036 / 0.035 |
| Rows_examined | 12,668 | 12,668 |
| EXPLAIN | index_merge + filesort | `type=ref, key=idx_drug_cat_status_time, Extra=Backward index scan` |
| EXPLAIN ANALYZE 服务端 | 57 ms | 16.6 ms |

#### 副作用与学习点

- 删除 `idx_drug_category_id` 后，所有按分类的查询由复合索引最左前缀承接，回归无异常。
- 优化器成本估算会“选错索引”。判断时不要只看 `possible_keys`，要对比 `key` 和实际耗时；必要时用 `FORCE INDEX` / `IGNORE INDEX` 做**诊断**，但不要把它当最终方案。
- 接口无分页仍是遗留问题，见第 8 节。

---

### S2. 管理端药品分页查询

#### 业务描述

管理员打开药品管理页，按名称关键词/分类/状态筛选并翻页。PageHelper 会自动追加 `LIMIT`，并额外执行一条 count。

#### SQL

```sql
select d.*, c.name as categoryName
from drug d left outer join category c on d.category_id = c.id
where d.name like concat('%','测试药品_1000','%') and d.status = 1
order by d.create_time desc
limit 0,10;
```

count：

```sql
select count(0)
from drug d left outer join category c on d.category_id = c.id
where d.name like concat('%','测试药品_1000','%') and d.status = 1;
```

#### 当前表现

| 查询 | 3 次耗时 | 平均 | 慢日志 |
|---|---|---|---|
| 关键词分页 | 106.5 / 99.1 / 99.3 ms | **101.6 ms** | 0.106 / 0.099 / 0.099，Rows_examined=100,045 |
| count | 83.7 ms | 83.7 ms | 0.0836，Rows_examined=100,110 |
| 默认首页（无筛选） | 53.9 / 51.1 / 53.3 ms | **52.8 ms** | type=ALL，扫 100,025 行 |
| 默认首页 count | 15.7 ms | 15.7 ms | — |
| 选择性关键词 `阿莫西林`（1 条） | 97.1 / 98.3 / 96.9 ms | **97.4 ms** | — |

#### EXPLAIN（关键词分页）

| table | type | possible_keys | key | rows | filtered | Extra |
|---|---|---|---|---|---|---|
| d | ref | idx_drug_status | idx_drug_status | 49454 | 11.11% | Using where; **Using filesort** |
| c | eq_ref | PRIMARY | PRIMARY | 1 | 100% | NULL |

#### EXPLAIN ANALYZE

```text
-> Limit: 10 row(s)  (actual time=104..104 rows=10)
    -> Sort: d.create_time DESC  (actual time=104..104 rows=10)
        -> Filter: d.name like '%测试药品_1000%'  (actual time=1.58..103 rows=85)
            -> Index lookup on d using idx_drug_status (status=1)
               actual rows=100025, time≈90.8ms
    -> Single-row index lookup on c using PRIMARY  loops=10, time≈0.02ms
```

#### 判断依据

1. 前导通配 `%关键词%` 让 `idx_drug_name` 完全失效，name 只能在回表后逐行过滤。
2. `idx_drug_status` 基数=1，实际扫 100,025 行；优化器估算 49,454 行，且低估 10 万次随机回表的代价。
3. 实测对照：`IGNORE INDEX (idx_drug_status)` 走全表扫描只需 **56.2 ms**，比走 status 索引快近一倍。
4. join 不是瓶颈：category 只有 10 行，10 次 `eq_ref` 约 0.02 ms。
5. 默认首页唯一能被 B-Tree 优化的是 `ORDER BY create_time DESC LIMIT 10`（理论上 create_time 索引可把 55.7 ms 降到 1 ms 级），但本次没有采纳。
6. count 与分页各扫一遍 10 万行，一次翻页合计约 185 ms；count 无法被普通索引优化。

#### 候选方案与最终选择

| 方案 | 操作 | 预期 | 风险 |
|---|---|---|---|
| A | `idx_drug_create_time(create_time)` | 默认首页大幅提升；关键词页不确定 | 选择性关键词可能扫更多行 |
| B | `(status, create_time)` | 仅覆盖带 status 的翻页 | 默认首页无 status 时无法利用排序 |
| C | 不加索引 | 记录代码级问题 | 本次无索引收益 |
| D | FULLTEXT / ES + count 去 join | 根治关键词搜索 | 需改 SQL/代码 |

**最终选择：C，不加索引。** 本场景的结论是“该不该加索引”的反例：在 `%x%` 面前，B-Tree 没有可用起点；强行加 create_time 索引只对默认首页有效，对选择性关键词可能更差。

#### 副作用

无 DDL。后续建议见第 8 节。

---

### S3. AI 问诊按名称/症状搜索

#### 业务描述

用户在 AI 问诊里输入“头痛”“布洛芬”等关键词，AI 工具通过 Feign 调 `GET /inner/drug/search`，返回最多 10 条在售药品。

#### SQL

```sql
SELECT id, name, prescription_type, manufacturer, approval_number,
       indications, contraindications, status
FROM drug
WHERE status = 1
  AND (name LIKE CONCAT('%','头痛','%') OR indications LIKE CONCAT('%','头痛','%'))
ORDER BY prescription_type ASC
LIMIT 10;
```

#### 当前表现

| 次数 | 耗时 |
|---|---|
| 1 | 118.8 ms |
| 2 | 98.8 ms |
| 3 | 96.5 ms |
| 平均 | **104.7 ms** |

返回 1 行（id=73 布洛芬缓释胶囊）。慢日志：`0.118633 / 0.098718 / 0.096443`，`Rows_examined=100,026`。

#### EXPLAIN

| type | possible_keys | key | rows | filtered | Extra |
|---|---|---|---|---|---|
| ref | idx_drug_status | idx_drug_status | 49454 | 20.99% | Using where; **Using filesort** |

#### EXPLAIN ANALYZE

```text
-> Limit: 10 row(s)  (actual time=105..105 rows=1)
    -> Sort: prescription_type  (actual time=105..105 rows=1)
        -> Filter: name LIKE '%头痛%' OR indications LIKE '%头痛%'
           (actual time=0.124..105 rows=1)
            -> Index lookup on drug using idx_drug_status (status=1)
               actual rows=100025, time≈84.6ms
```

#### 判断依据

1. `name` 和 `indications` 都是前导通配 + OR，任何单列/复合 B-Tree 都无法定位起点。
2. 与 S2 同因：`idx_drug_status` 基数=1，让优化器做 10 万次索引查找 + 回表，只为过滤出 1 行。
3. 实测对照：`IGNORE INDEX (idx_drug_status)` 全表扫描平均 **41.2 ms**，比当前计划快 2.5 倍。
4. `ORDER BY prescription_type` 只对 1 行排序，不是瓶颈。

#### 候选方案

| 方案 | 操作 | 预期 | 说明 |
|---|---|---|---|
| A | 不加索引 | 维持 105 ms | B-Tree 无解 |
| B | FULLTEXT + ngram + `MATCH ... AGAINST` | 毫秒级 | 需改 Mapper |
| C | `idx_drug_status` INVISIBLE | 退回全表扫描约 41 ms | 影响 S5，需回归 |
| D | ES / 搜索表 | 根治 | 超出范围 |

#### FULLTEXT 实验（方案 B）

DDL 首次失败，记录如下：

```text
ERROR 1846 (0A000): LOCK=NONE is not supported.
Reason: Fulltext index creation requires a lock. Try LOCK=SHARED.
```

改用 `LOCK=SHARED` 后 7.77 s 建成：

```sql
ALTER TABLE drug
  ADD FULLTEXT INDEX ft_drug_name_indications (name, indications)
  WITH PARSER ngram,
  ALGORITHM=INPLACE, LOCK=SHARED;
```

验证 SQL：

```sql
-- 自然语言模式
SELECT id, name FROM drug
WHERE status = 1
  AND MATCH(name, indications) AGAINST('头痛' IN NATURAL LANGUAGE MODE)
ORDER BY prescription_type LIMIT 10;

-- 布尔短语模式（推荐，保持子串语义）
SELECT id, name FROM drug
WHERE status = 1
  AND MATCH(name, indications) AGAINST('"头痛"' IN BOOLEAN MODE)
ORDER BY prescription_type LIMIT 10;
```

#### 优化后

| 方案 | 耗时 | EXPLAIN |
|---|---|---|
| LIKE（现状） | 104.7 ms | ref idx_drug_status + filesort |
| FULLTEXT 自然语言 | 2.49 / 0.32 / 0.47 ms（约 1.1 ms） | `type=fulltext, key=ft_drug_name_indications, rows=1` |
| FULLTEXT 布尔短语 | 1.03 / 0.64 / 0.28 ms（**0.65 ms**） | 同上 |
| FULLTEXT EXPLAIN ANALYZE | 0.034 ms | `Full-text index search ... rows=1` |

#### 正确性对照（重要）

| 关键词 | LIKE 命中 | 自然语言模式 | 布尔短语模式 |
|---|---|---|---|
| 头痛 | 1 | 1 | 1 |
| 阿莫西林胶囊 | 1 | **4**（51,53,57,73） | **1** |
| 感冒 | 2 | 2 | 2 |
| 头痛关节 | 0 | **1** | **0** |

自然语言模式会把查询拆成 ngram 二元词后 OR，召回变宽、精度下降；布尔短语模式 `"关键词"` 与 LIKE 的命中在本次 4 组样例中完全一致。

#### 结论与采纳状态

- 性能上 FULLTEXT 完胜（105 ms → 亚毫秒），但**当前 Mapper 仍用 LIKE，索引不会被业务使用**。
- 本次保留索引作为实验结果；是否上线取决于是否修改 `DrugMapper.xml:109-118` 为 `MATCH ... AGAINST(... IN BOOLEAN MODE)`。
- 副作用：索引体积约 +1 MB（另有 `FTS_DOC_ID_INDEX` 2.52 MB），写入成本在本次 100 次插入中未测出明显差异；FULLTEXT 建索引需要 `LOCK=SHARED`，会短暂阻塞写入。

---

### S4. 药品组合详情（关联查询对照组）

#### 业务描述

用户打开药品组合详情，查看组合内包含的药品及数量。另有组合上架前的反向校验。

#### SQL

```sql
-- 详情
select sd.name, sd.quantity, d.image, d.description
from drug_combo_item sd left join drug d on sd.drug_id = d.id
where sd.combo_id = 33;

-- 上架前校验
select a.* from drug a left join drug_combo_item b on a.id = b.drug_id
where b.combo_id = 33;
```

#### 当前表现

| 查询 | 3 次耗时 | 平均 | 慢日志 |
|---|---|---|---|
| 详情 | 1.37 / 0.25 / 0.35 ms | **0.66 ms** | 无记录（低于 10 ms 且走索引） |
| 上架校验 | 0.54 / 0.24 / 0.21 ms | **0.33 ms** | 无记录 |

#### EXPLAIN

| SQL | table | type | key | rows | Extra |
|---|---|---|---|---|---|
| 详情 | sd | ref | idx_drug_combo_item_combo_id | 3 | NULL |
| 详情 | d | eq_ref | PRIMARY | 1 | NULL |
| 校验 | b | ref | idx_drug_combo_item_combo_id | 3 | Using where |
| 校验 | a | eq_ref | PRIMARY | 1 | NULL |

EXPLAIN ANALYZE：整体 0.237 ms；驱动表 3 行，被驱动表 3 次主键查找，每次约 0.007 ms。

#### 判断依据与结论

- 这是正确索引下的理想 join：驱动侧 `combo_id` 用索引定位 3 行，被驱动侧走主键 `eq_ref`。
- 需要索引的是驱动侧的 `combo_id`；`drug_id` 侧走 `drug.id` 主键，不要求 `drug_combo_item.drug_id` 有索引。
- 002 迁移已加 `idx_drug_combo_item_combo_id`，本场景**不需要新增索引**。
- 数据量只有 3 行，即使缺索引也跑不出慢 SQL，说明索引问题要在数据量增长后才会暴露。

---

### S5. 管理端工作台药品总览

#### 业务描述

管理端首页显示在售/停售药品数，对同一个 SQL 执行两次。

#### SQL

```sql
select count(id) from drug where status = 1;
select count(id) from drug where status = 0;
```

#### 当前表现

| 查询 | 3 次耗时 | 平均 | 慢日志 |
|---|---|---|---|
| status=1 | 15.2 / 13.2 / 12.9 ms | **13.8 ms** | 0.012771，Rows_examined=100,025 |
| status=0 | 0.34 / 0.30 / 0.21 ms | **0.29 ms** | 低于 0.01 s，未记录 |
| status=1 忽略 status 索引 | 14.8 / 13.4 / 13.8 ms | 14.0 ms | 0.0133~0.0146 |

#### EXPLAIN

| 查询 | type | possible_keys | key | rows | Extra |
|---|---|---|---|---|---|
| status=1 | ref | idx_drug_status, idx_drug_cat_status_time | idx_drug_status | 49343 | **Using index** |
| status=0 | ref | 同上 | idx_drug_status | 1 | **Using index** |
| 忽略 status 索引 | index | idx_drug_cat_status_time | idx_drug_cat_status_time | 98686 | Using where; **Using index** |

EXPLAIN ANALYZE：Aggregate 18.5 ms；覆盖索引扫描 100,025 行约 15.3 ms。

#### 判断依据与结论

1. `Rows_examined=100,025` 很大，但查询不慢（约 14 ms），因为 `Extra=Using index`，只扫二级索引、不回表。
2. `idx_drug_status` 基数=1，对过滤没有帮助；它的价值只是索引比聚簇索引窄。
3. 忽略 status 索引后，优化器改用复合索引全索引扫描，同样 `Using index`，14.0 ms——两个索引在 count 上的差别可以忽略。
4. 这为 T3 的 `idx_drug_status` INVISIBLE 决策提供了依据：它对 S5 几乎无贡献。
5. S5 无需新增索引。

---

## 5. 索引优化建议汇总表

| 业务场景 | 建议索引 / 操作 | 列顺序 | 预期收益 | 实测收益 | 副作用 | 是否采纳 |
|---|---|---|---|---|---|---|
| S1 分类浏览 | `idx_drug_cat_status_time` | `category_id, status, create_time` | 消除 index_merge 与 filesort | 89.2 → 33.9 ms；服务端 57 → 16.6 ms | +3.52 MB；写入差异测不出 | **采纳** |
| S1 分类浏览 | 删除 `idx_drug_category_id` | — | 让优化器改选复合索引 | 计划从 index_merge 改为 ref | 该索引能力被最左前缀覆盖 | **采纳** |
| S2 关键词分页 | 不加 B-Tree | — | 无 | 无 | — | **不采纳** |
| S2 默认首页 | `idx_drug_create_time`（候选） | `create_time` | 55.7 ms → 1 ms 级 | 未实施 | 选择性关键词可能变差 | **本次不采纳** |
| S3 模糊搜索 | `ft_drug_name_indications` + `MATCH ... BOOLEAN MODE` | `name, indications` | 105 ms → 亚毫秒 | 手工 SQL 104.7 → 0.65 ms | 需改 Mapper；+约 1 MB | **实验后删除（T5）；改代码后可重建** |
| S4 组合详情 | 沿用 `idx_drug_combo_item_combo_id` | `combo_id` | 已有索引 | 0.66 ms | 无 | 无需变更 |
| S5 状态计数 | 沿用现有索引 | — | 覆盖扫描 | 13.8 ms | 无 | 无需变更 |
| 全局 | `idx_drug_status` 设为 INVISIBLE | — | 避免低基数索引误导优化器 | S2 117 → 63.9 ms；S3 100 → 55.3 ms；S5 持平 | 索引仍占空间、仍维护写入 | **实验采纳，待决定是否 DROP** |

---

## 6. 回归与写入副作用（T3）

### 6.1 全场景回归

当前索引集：`PRIMARY`、`idx_drug_name`、`idx_drug_status(INVISIBLE)`、`idx_drug_cat_status_time`、FULLTEXT。

| 场景 | 场景内记录值 | T3 复测 | 计划是否变化 | 结论 |
|---|---|---|---|---|
| S1 分类浏览 | 33.9 ms | 38.2 ms | 否，仍走复合索引 | 正常波动 |
| S2 关键词分页（status 可见时） | 101.6 ms | 117.2 ms | 否，仍 idx_drug_status + filesort | 计划未变，环境波动 |
| S2 默认首页 | 52.8 ms | 约 59.8 ms | 否，type=ALL + filesort | 正常波动 |
| S3 LIKE 搜索（status 可见时） | 104.7 ms | 100.5 ms | 否 | 正常 |
| S4 组合详情 | 0.66 ms | 0.26 ms（预热后） | 否 | 正常 |
| S5 状态计数（status 可见时） | 13.8 ms | 12.8 ms | 否 | 正常 |

没有发现计划级回归；S2 的 10~15 ms 差异未伴随计划和 `key` 变化，判断为环境波动。

### 6.2 `idx_drug_status` INVISIBLE 实验

```sql
ALTER TABLE drug ALTER INDEX idx_drug_status INVISIBLE;
```

| 场景 | 可见 | INVISIBLE | 变化 | 计划 |
|---|---|---|---|---|
| S1 分类浏览 | 38 ms | 37.5 ms | 不变 | 复合索引 |
| S2 关键词分页 | 117 ms | **63.9 ms** | -45% | type=ALL |
| S2 默认首页 | 59 ms | 59 ms | 不变 | type=ALL |
| S3 AI 搜索 | 100 ms | **55.3 ms** | -45% | type=ALL |
| S4 组合详情 | 0.26 ms | 0.26 ms | 不变 | ref + eq_ref |
| S5 状态计数 | 12.8 ms | 13.1 ms | 持平 | 复合索引 Using index |

INVISIBLE 只是让优化器看不见，索引仍然维护写入、仍占 2.52 MB；若确认长期不需要，可再 DROP。

### 6.3 写入耗时

对 100,025 行的克隆表各执行 3 轮、每轮 100 次单行插入。客户端启动开销实测约 0.435 s（`SELECT 1`）。

| 索引集 | 3 轮总耗时 | 平均 | 相对基线 |
|---|---|---|---|
| 基线：PRIMARY + name + status | 0.830 / 0.805 / 0.800 s | 0.812 s | — |
| + 复合索引 | 0.855 / 0.802 / 0.775 s | 0.811 s | ±0（噪声内） |
| + FULLTEXT | 0.837 / 0.772 / 0.750 s | 0.786 s | ±0（噪声内） |

扣除客户端开销后单行插入约 3.5~3.7 ms；新增索引的写入差异小于 0.05 s / 100 行，被测量噪声淹没。该结论只适用于 100k 行、短文本场景。

### 6.4 ANALYZE 前后

| 查询 | ANALYZE 前 | ANALYZE 后 |
|---|---|---|
| S1 | key=idx_drug_cat_status_time, rows=23720 | 相同 |
| S2 关键词 | key=idx_drug_status, rows=49343 | key 相同，rows=49307 |
| S3 | key=idx_drug_status, rows=49343 | key 相同，rows=49307 |
| S5 | key=idx_drug_status, Extra=Using index | 相同 |

优化器没有更换索引选择，只是估算行数微调。

### 6.5 索引副作用清单

| 索引 | 对查询的影响 | 对写入/空间的影响 | 可接受性 |
|---|---|---|---|
| `idx_drug_cat_status_time` | S1：89 → 38 ms；替代已删除的 `idx_drug_category_id` | +3.52 MB；写入差异测不出 | 可接受，建议保留 |
| `ft_drug_name_indications` | 实验期手工 MATCH 0.65 ms；代码未改，已无读收益 | 已删除；隐藏 `FTS_DOC_ID_INDEX` 仍占 2.52 MB | T5 已回滚，待代码改造后重建 |
| `idx_drug_status`（INVISIBLE） | S2/S3 因它多扫 10 万行；S5 与复合索引全扫持平 | 2.52 MB 仍占用；仍维护写入 | 建议保持 INVISIBLE 或 DROP |
| 已删除 `idx_drug_category_id` | 能力被复合索引覆盖，回归无异常 | 释放约 1.6 MB | 可接受 |

空间实测（`mysql.innodb_index_stats`）：

| 索引 | 大小 |
|---|---|
| PRIMARY | 25.55 MB |
| idx_drug_name | 4.50 MB |
| idx_drug_cat_status_time | 3.52 MB |
| idx_drug_status | 2.52 MB |
| FTS_DOC_ID_INDEX | 2.52 MB |

> FULLTEXT 索引已在 T5 删除，但 `FTS_DOC_ID_INDEX` 是 InnoDB 的隐藏列索引，DROP 后仍保留。本次测量值来自实验期间；最终状态没有 FULLTEXT 检索能力。

---

## 7. EXPLAIN 学习要点（从本次实践提炼）

### 7.1 本次实际出现的 type

| type | 本次出现场景 | 含义 |
|---|---|---|
| `index_merge` | S1 优化前 | 合并多个单列索引（intersect）；本例实际比单索引更慢 |
| `ref` | S1/S2/S3/S5 | 用非唯一索引做等值查找 |
| `eq_ref` | S2/S4 的 category、drug 主键查找 | join 中被驱动表按主键/唯一键查找，每次最多 1 行 |
| `ALL` | S2/S3 全表扫描（含 INVISIBLE 后） | 全表扫描；在低基数索引存在时反而更快 |
| `index` | S5 忽略 status 索引后 | 扫整个索引，不做回表 |
| `fulltext` | S3 FULLTEXT | 全文索引检索 |
| `range` | S1 关闭 index_merge 的诊断 | 索引范围扫描 |

### 7.2 Rows_examined 与 actual rows 的差异实例

| 场景 | Rows_examined | 实际返回 | 说明 |
|---|---|---|---|
| S1 | 12,668 | 12,668 | 扫描即返回，行数一致但回表成本高 |
| S2 关键词分页 | 100,045 | 10 | 扫 10 万行只为取 10 行；前导通配是根因 |
| S3 AI 搜索 | 100,026 | 1 | 扫 10 万行命中 1 行 |
| S5 状态计数 | 100,025 | 1（聚合结果） | 扫描大但 14 ms，因为 `Using index` 不回表 |

结论：**Rows_examined 大不等于慢**；要结合 `Extra` 判断是否回表、是否排序，以及 `Rows_sent` 判断返回量。

### 7.3 复合索引列顺序为什么这么定

`(category_id, status, create_time)`：

1. `category_id` 和 `status` 都是等值条件，放在最左；
2. `create_time` 是排序/范围列，放在等值列之后，索引天然按 `create_time` 有序；
3. 查询 `where category_id=? and status=? order by create_time desc` 可以走索引反向扫描，消除 filesort；
4. 删除 `idx_drug_category_id` 是因为它已经是复合索引的最左前缀，单独存在只会给 index_merge 提供“诱饵”。

### 7.4 filesort、回表、index_merge 在本次的表现

- **filesort**：S1 优化前、S2 默认首页、S3 都有；S1 约 10 ms，S2/S3 与 10 万行扫描相比占比小。
- **回表**：S1 返回 12,668 行宽记录，回表约 44 ms，是主要成本；S2/S3 因低基数索引做 10 万次回表，比全表扫描还慢。
- **index_merge**：S1 的 intersect 计划成本估算 `2360`，低于复合索引 `2446`；实际复合索引 16.6 ms vs index_merge 57 ms，说明成本模型不可全信。

### 7.5 filtered 与 LIKE

- S2 的 `filtered=11.11%`、S3 的 `filtered=20.99%` 是优化器对 `LIKE '%x%'` 的固定估算，并不反映真实命中率（S2 实际 85/100,025≈0.085%，S3 实际 1/100,025≈0.001%）。
- 这就是优化器会误判“索引扫描很快”的原因之一。

### 7.6 ANALYZE 的作用边界

本次 ANALYZE 只把估算行数从 49,343 调到 49,307，没有改变索引选择。统计信息能修正行数估计，但修正不了“LIKE 无索引可用”“成本模型低估回表”这类结构性问题。

---

## 8. 未解决问题与后续建议（本次不处理）

| # | 问题 | 现状 | 建议 | 本次是否处理 |
|---|---|---|---|---|
| 1 | `LIKE '%关键词%'` 无法使用 B-Tree | S2/S3 必扫 10 万行 | 管理端可改前缀搜索；AI 搜索改 FULLTEXT 布尔短语或 ES | 否 |
| 2 | FULLTEXT 实验索引已回滚 | Mapper 仍用 LIKE | 先把 `DrugMapper.xml:109-118` 改为 `MATCH ... AGAINST('"关键词"' IN BOOLEAN MODE)`，再按 S3 的 DDL 重建 | 否（需改代码） |
| 3 | C 端分类列表无分页 | 一次返回 12,668 行 | 接口增加 `page/pageSize`；索引只能省 filesort | 否（需改代码） |
| 4 | PageHelper count 带 join | 每次翻页多扫 10 万行 | 自定义 count SQL，去掉 `left join category`；或前端改为懒加载总数 | 否（需改代码） |
| 5 | `idx_drug_status` 低基数且 INVISIBLE | 仍占 2.52 MB、仍维护写入 | 观察一段时间后 DROP；若未来 status 分布变化，再评估重建 | 否（待决策） |
| 6 | `drug_combo_item.drug_id` 无索引 | 删除药品前的 `where drug_id in (...)` 会全表扫描 | 数据量增长后补 `idx_drug_combo_item_drug_id` | 否（当前仅 3 行） |
| 7 | `shopping_cart` 只有主键 | 加购物车前按 `user_id + drug_id + spec` 查询；表当前 0 行 | 生产前补复合索引，并考虑 `(user_id, drug_id, spec)` 唯一索引 | 否（属于 order-service） |
| 8 | 测试数据 `create_time` 几乎相同 | ORDER BY 有大量并列值，默认首页排序结果不稳定 | 生产数据分布下重新验证 create_time 索引收益 | 否 |
| 9 | `status` 全为 1 | 索引选择性结论只对当前数据集成立 | 生产环境按真实 status 分布重新评估 | 否 |

---

## 9. 恢复清单与复现步骤

### 9.1 当前索引状态与回滚 DDL

当前状态见 1.3（FULLTEXT 已按 T5 决策删除）。完整回滚为演练前状态：

```sql
-- 1) 删除 S1 复合索引，恢复单列分类索引
ALTER TABLE drug DROP INDEX idx_drug_cat_status_time, ALGORITHM=INPLACE, LOCK=NONE;
ALTER TABLE drug ADD INDEX idx_drug_category_id (category_id), ALGORITHM=INPLACE, LOCK=NONE;

-- 2) 恢复 status 索引可见
ALTER TABLE drug ALTER INDEX idx_drug_status VISIBLE;

-- 3) 刷新统计信息
ANALYZE TABLE drug;
```

如果后续修改了 Mapper、需要重新启用 FULLTEXT 检索：

```sql
ALTER TABLE drug
  ADD FULLTEXT INDEX ft_drug_name_indications (name, indications)
  WITH PARSER ngram,
  ALGORITHM=INPLACE, LOCK=SHARED;
```

### 9.2 慢日志恢复

演练期间临时开启。学习结束后由用户确认再执行：

```sql
SET GLOBAL slow_query_log = OFF;
SET GLOBAL log_queries_not_using_indexes = OFF;
SET GLOBAL long_query_time = 10;   -- 默认值；如原环境是其他值，请按实际恢复
```

### 9.3 测试数据清理（默认不执行）

本次写入基准的 100 行已按唯一前缀 `wb_*` 删除，克隆表 `drug_wb_*` 已 DROP；`drug` 行数已恢复 100,025。100,000 行 `测试药品_N` 是演练前已有数据，**未删除**。如需清理：

```sql
-- 先确认没有业务数据依赖这些测试行
SELECT COUNT(*) FROM drug WHERE name LIKE '测试药品\_%';
-- 再删除（注意下划线转义）
DELETE FROM drug WHERE name LIKE '测试药品\_%';
```

### 9.4 用户可照着复现的步骤

1. 连接数据库并确认环境：

```bash
docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
docker exec Study mysql -uroot -proot -N -e "
USE ysd_take_out;
SELECT COUNT(*) FROM drug;
SHOW INDEX FROM drug;
SHOW VARIABLES LIKE 'slow_query_log';
SHOW VARIABLES LIKE 'long_query_time';"
```

2. 清空慢日志，便于按场景对应：

```bash
docker exec Study mysql -uroot -proot -e "FLUSH SLOW LOGS;"
```

3. 计时执行 SQL（每条跑 3 次）：

```bash
docker exec Study mysql --default-character-set=utf8mb4 -uroot -proot -e "
SET profiling=1;
USE ysd_take_out;
<SQL>;
<SQL>;
<SQL>;
SHOW PROFILES;"
```

`SHOW PROFILES` 的 `Duration` 单位是秒。

4. 读慢日志（按 SQL 片段筛选）：

```bash
docker exec Study sh -c "grep -B4 -A1 '<SQL 片段>' /var/lib/mysql/*slow.log"
```

5. 看执行计划：

```sql
EXPLAIN <SQL>;
EXPLAIN ANALYZE <SQL>;
```

建议统一记录：`type / possible_keys / key / rows / filtered / Extra`，EXPLAIN ANALYZE 追加 `actual time / actual rows`。

6. DDL 前预检：

```sql
SELECT index_name, seq_in_index, column_name, cardinality
FROM information_schema.statistics
WHERE table_schema='ysd_take_out' AND table_name='drug'
ORDER BY index_name, seq_in_index;
```

7. 一次只加一个索引，`ANALYZE TABLE` 后重跑 SQL 和 EXPLAIN，对比 `key` 是否真的变了。**possible_keys 出现新索引不代表它被使用。**

8. 若需要诊断优化器为什么不用某个索引，可用会话级 `IGNORE INDEX` / `FORCE INDEX` / `SET optimizer_switch='index_merge=off'`，但这些只用于诊断，不作为最终方案。

---

## 10. 一句话总结

本表最重要的两次收益分别来自：**用 `(category_id, status, create_time)` 消除 index_merge 和 filesort（89 → 34 ms）**，以及**把低基数 `idx_drug_status` 设为 INVISIBLE，让模糊查询回到全表扫描（S2 117 → 64 ms，S3 100 → 55 ms）**。`LIKE '%x%'` 本身仍然无解，FULLTEXT 能把 105 ms 降到 0.65 ms，但必须改 Mapper 才能上线；实验索引已在 T5 删除，代码改造后可重建。
