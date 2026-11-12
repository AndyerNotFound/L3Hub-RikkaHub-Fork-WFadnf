<div align="center">
  <img src="docs/icon.png" alt="App 图标" width="100" />
  <h1>RikkaHub</h1>

一个原生Android LLM 聊天客户端，支持切换不同的供应商进行聊天 🤖💬

[English](README.md) | [繁體中文](README_ZH_TW.md) | 简体中文

点击链接加入群聊 👉 [【RikkaHub】](https://qm.qq.com/q/I8MSU0FkOu)

</div>

<div align="center">
  <img src="docs/img/poster.png" alt="RikkaHub" width="800" />
</div>


## 🚀 下载

🔗 [前往官网下载](https://rikka-ai.com/download)（推荐）

🔗 [前往 Google Play 下载](https://play.google.com/store/apps/details?id=me.rerere.rikkahub)

> [!WARNING]
> RikkaHub 存在许多 fork 版本，fork 版本出现问题与 RikkaHub 无关，请谨慎使用 fork 版本，避免隐私泄露或者过度索要权限问题。

## ✨ 功能特色

- 🎨 现代化安卓APP设计（Material 3 Expressive / 预测性返回）
- 📦 工作区：基于 proot 的 Linux 智能体环境
- 📱 Phone Use：让 AI 操控手机
- 🎬 媒体生成（图片、视频）
- 🔄 多种类型的供应商支持，自定义 API / URL / 模型（目前支持 OpenAI、Google、Anthropic）
- 🖥️ Web多端访问支持
- 🛠️ MCP 与 Skills 支持
- 📝 Markdown 渲染（支持代码高亮、数学公式、表格、Mermaid）
- 🔍 搜索功能（Exa、Tavily、Zhipu、LinkUp、Brave、Perplexity、..）
- 🧩 Prompt 变量（模型名称、时间等）
- 🤳 二维码导出和导入提供商
- 🤖 智能体自定义
- 🧠 基于文件的记忆
- 📝 AI翻译
- 🌐 自定义HTTP请求头和请求体
- 💌 SillyTavern 角色卡导入

# ✨ Fork 功能
- 💬 更详细的上下文管理
- 🖌 `不完全`的 Miuix 组件支持
- 🔑 多个 API 密钥管理与路由
- 🔒 可用生物识别应用锁
- ✉️ 支持自定义 TLS/HTTPS 证书
- 📦 更详细的工作区工具
- 🛡 为助手单独设置工作区权限
- 🎨 更全面的模糊和玻璃效果
- 📱 一个截然不同的界面布局
- 🧐 为模型单独设置 OCR 辅助模型
- 📖 RE 不会做但我做了的子代理功能
- 📊 统计用量看板的饼状图展示
- 📱 对横屏界面的适配
- 🗄️ 未完成的 SSH 控制功能
- 💬 `消息队列`可关闭
- 🛜 自定义网络错误重试
- 🧠 提供商分组和单个提供商的代理配置
- 🛠 覆写工具描述和工具定义
- 💓 心跳功能

## ✨ 开发

> [!IMPORTANT]
> 本项目不接受 Pull Request（PR）。

本项目使用[Android Studio](https://developer.android.com/studio)开发。

技术栈文档:

- [Kotlin](https://kotlinlang.org/) (开发语言)
- [Koin](https://insert-koin.io/) (依赖注入)
- [Jetpack Compose](https://developer.android.com/jetpack/compose) (UI 框架)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore?hl=zh-cn#preferences-datastore) (
  偏好数据存储)
- [Room](https://developer.android.com/training/data-storage/room) (数据库)
- [Coil](https://coil-kt.github.io/coil/) (图片加载)
- [Material 3 Expressive](https://m3.material.io/) (UI 设计)
- [Navigation 3](https://developer.android.com/guide/navigation/navigation-3) (导航)
- [Okhttp](https://square.github.io/okhttp/) (HTTP 客户端)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) (Json序列化)

> [!TIP]
> 你需要在 `app` 文件夹下添加 `google-services.json` 文件才能构建应用。

## 💰 捐赠 (该软件原作者 re-ovo)

* [Ko-fi](https://ko-fi.com/reovodev)
* [爱发电](https://afdian.com/a/reovo)

## ⭐ Star History

如果喜欢这个项目，请给个Star ⭐

<a href="https://www.star-history.com/?type=date&repos=re-ovo%2Frikkahub">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=re-ovo/rikkahub&type=date&theme=dark&legend=top-left&sealed_token=qSytWeq7LkzQQViTjK0MYlvvA_qkfuwjOxOqgbRpLUZZwok5rO6LXhpVL7Mq-q3o89BfKpzE7g66BCy18H6eiqTsD8czD0J-HejLqmHy-npcvCTHu11wZw" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=re-ovo/rikkahub&type=date&legend=top-left&sealed_token=qSytWeq7LkzQQViTjK0MYlvvA_qkfuwjOxOqgbRpLUZZwok5rO6LXhpVL7Mq-q3o89BfKpzE7g66BCy18H6eiqTsD8czD0J-HejLqmHy-npcvCTHu11wZw" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=re-ovo/rikkahub&type=date&legend=top-left&sealed_token=qSytWeq7LkzQQViTjK0MYlvvA_qkfuwjOxOqgbRpLUZZwok5rO6LXhpVL7Mq-q3o89BfKpzE7g66BCy18H6eiqTsD8czD0J-HejLqmHy-npcvCTHu11wZw" />
 </picture>
</a>

## 📄 许可证

本项目基于 [GNU Affero General Public License v3.0](LICENSE) (AGPL-3.0) 开源。
