# Zipkin 切换 Elasticsearch 存储

> 记录时间：2026-10-12（阶段 5.5 链路追踪收尾）。
> 当前各服务上报地址：`management.zipkin.tracing.endpoint: http://localhost:9411/api/v2/spans`。

## 1. 为什么换 ES

Zipkin 默认存储是**内存（`STORAGE_TYPE=mem`）**：进程一重启，历史 trace 全部丢失，
只能看「重启后到现在」这一段。排障时最需要的恰恰是**已发生、但刚重启过服务**的那条链路，
mem 模式下无法回溯。

Elasticsearch 提供持久化 + 全文/标签检索 + 时间范围聚合，是演示 / 预发 / 生产环境的推荐存储。

## 2. 前置条件

- 本地或 Docker 已有一个可用的 **Elasticsearch，端口 9200**。
- 连通性自检：

  ```bash
  curl http://localhost:9200
  # 期望返回 cluster_name / version 等 JSON；返回连接拒绝则需先起 ES。
  ```

- 不需要预先建索引：Zipkin 首次启动会自动创建 `zipkin-*` 索引（默认 `zipkin-span` 等）。

## 3. Docker 启动 Zipkin（ES 存储）

```bash
docker run -d --name zipkin \
  -p 9411:9411 \
  -e STORAGE_TYPE=elasticsearch \
  -e ES_HOSTS=http://host.docker.internal:9200 \
  openzipkin/zipkin
```

**`ES_HOSTS` 取值与运行环境有关**：

| 环境 | 应填地址 | 说明 |
| --- | --- | --- |
| Windows / WSL（Docker Desktop） | `http://host.docker.internal:9200` | 容器经此名访问宿主机上的 ES |
| Linux（原生 Docker） | `http://172.17.0.1:9200` 或宿主机实际 IP | 默认 bridge 网关地址；ES 在别的机器填其 IP |
| 与 Zipkin 同在一个 docker network | `http://<es容器名>:9200` | 用容器名互访 |

**常用可选环境变量**：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `ES_INDEX` | `zipkin` | 索引前缀 |
| `ES_USERNAME` / `ES_PASSWORD` | 空 | ES 开启鉴权时填写 |
| `ES_HTTP_LOGGING` | `BASIC` | 需要排障时改 `BODY` |

若已有同名容器需先清理：`docker rm -f zipkin`。

## 4. 验证方法（确认持久化生效）

1. 浏览器访问 <http://localhost:9411>。
2. 随便打一个会经链路的请求（例如经网关 9000 打一个 `/admin/...` 接口），确保产生了 trace。
3. 在 Zipkin UI 搜索，能看到该 trace，记下它的 **traceId**。
4. 重启 Zipkin：`docker restart zipkin`。
5. 再次打开 UI，按同一 traceId 搜索 —— **trace 仍在，即 ES 持久化生效**。
   （对照：`STORAGE_TYPE=mem` 下重启后该 trace 会查不到。）

## 5. 内存存储 vs ES 存储

| 维度 | 内存（mem） | Elasticsearch |
| --- | --- | --- |
| 持久性 | 进程重启即丢 | 持久，可长期保留 |
| 查询能力 | 仅当前窗口 | 全文 / 标签 / 时间范围检索 + 聚合 |
| 写入性能 | 极高（无外部 IO） | 受 ES 分片、refresh 间隔影响 |
| 部署复杂度 | 零依赖，随 Zipkin 起停 | 需额外维护 ES |
| 资源占用 | trace 越多越吃 Zipkin 堆内存 | 存储/内存压力转嫁给 ES |
| 适用场景 | 本地开发、一次性排障 | 演示、预发、生产 |

> 结论：本地开发可继续用 mem；一旦需要「跨重启回溯」或对外演示，切 ES。
