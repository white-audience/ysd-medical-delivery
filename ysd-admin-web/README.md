# ysd-admin-web — 药速达管理端

管理端前端：Vue 2 + TypeScript + Element UI，基于 [vue-typescript-admin](https://github.com/Armour/vue-typescript-admin-template)（MIT）二次改造。

## 开发

```bash
npm install
npm run serve
```

默认地址 `http://localhost:8888`。开发服务器会把 `/api` 代理到 `.env.development` 中的
`VUE_APP_URL`（默认 `http://localhost:9000/admin`，即网关），WebSocket 走 `VUE_APP_SOCKET_URL`。

先启动后端网关（见仓库根目录 README），否则登录与数据接口不可用。

## 构建

```bash
npm run build
```

产物在 `dist/`。生产构建不生成 source map。

## Nginx 部署

参考 [docker/nginx/ysd-admin.conf](../docker/nginx/ysd-admin.conf)：`/api` 反代到网关的 `/admin`，
`/ws` 反代到网关的 WebSocket 路由。

## 演示账号

- 账号：`admin`
- 密码：`123456`（MD5，仅初始化脚本中的演示数据；**部署到可访问环境前必须修改**）

## 说明

- 仓库不含 `node_modules`、`dist`、构建报告和任何真实域名/密钥。
- `.env.production` 中的 `VUE_APP_SOCKET_URL` 是占位符，部署时改成实际域名（建议 `wss://`）。
- 本目录保留上游 MIT `LICENSE`，二次改造部分同样以 MIT 发布。
