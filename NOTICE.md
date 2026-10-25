# 第三方组件与素材说明

本项目采用 MIT License，但包含/衍生自以下第三方内容：

1. **管理端前端** `ysd-admin-web/`：基于
   [vue-typescript-admin](https://github.com/Armour/vue-typescript-admin-template)
   （Copyright (c) 2018 Chong Guo，MIT）二次改造。原始许可证保留在
   `ysd-admin-web/LICENSE`。

2. **用户端小程序** `ysd-miniapp/`：由课程配套的 uni-app 工程迁移并改造，
   使用 `@dcloudio/uni-ui`（Apache-2.0）。**当前开源发布包未包含该目录**：课程版源码的
   再分发授权尚未确认，`uni_modules/uni-ui` 的 Apache-2.0 许可不能覆盖课程源码。
   取得课程作者或权利人的书面许可后，方可将其纳入发布，并在此处补充许可与归属信息。

3. **业务原型**：项目最初参考黑马程序员《苍穹外卖》教学项目，当前已在业务语义、
   架构与技术栈上大幅重构。课程资料、PPT、视频不包含在本仓库中。
   **说明**：本条为来源与归属披露，予以保留以确保来源说明完整；它不是当前功能描述，
   当前项目定位、业务模型与技术实现以 [README.md](README.md) 为准。

4. 其他依赖（Spring、Nacos、Redis、RabbitMQ、Element UI、ECharts 等）遵循各自许可证。
