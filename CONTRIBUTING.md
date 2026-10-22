# 贡献指南

## 分支与提交

1. 从 `main` 切出功能分支：`feat/xxx`、`fix/xxx`、`docs/xxx`。
2. 一个 PR 只做一件事，提交信息说明“为什么”而不是“改了什么”。
3. 提交前确认 `git status` 中没有 `.env`、日志、构建产物或个人配置。

## 后端

```bash
mvn -q -DskipTests package
# 或只构建某个模块
mvn -q -pl ysd-order-service -am -DskipTests package
```

- Java 21 / Spring Boot 3.5.x，保持现有模块边界与 `Result` 返回风格。
- 新增接口请补充 OpenAPI 注解；涉及鉴权的路径要确认网关白名单是否需要调整。
- 不要提交真实密钥；本地配置写进 `.env`（已被忽略）。

## 管理端（ysd-admin-web）

```bash
cd ysd-admin-web
npm install
npm run serve   # http://localhost:8888，代理到网关 9000
npm run build
```

## 小程序（ysd-miniapp）

当前开源发布包**不包含** `ysd-miniapp/`：课程版源码的再分发授权尚未确认。
取得书面许可后再纳入；在此之前不要向公开仓库提交该目录。
（原始开发副本的启动方式见其自带的 `ysd-miniapp/README.md`。）

## 文档

- 公开文档放在 `docs/`，架构与排障文档优先。
- 个人笔记、面试材料、内部项目记录不进入开源仓库。
- 截图与日志请先脱敏（域名、令牌、手机号、地址）。

## 安全

发现安全问题请不要开公开 Issue，按 [SECURITY.md](SECURITY.md) 私密上报。
