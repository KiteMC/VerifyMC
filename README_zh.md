# ✅ VerifyMC

[English](README.md) | 简体中文 | [📚 官方文档](https://kitemc.com/zh/docs/verifymc/) | [🚀 更新日志](release_notes_zh.md)

---

## 🚀 项目简介

**VerifyMC** 是一款功能强大的 Minecraft 服务器白名单管理插件，支持网页注册、自动/手动审核、封禁、主题切换、AuthMe 集成与高度自定义，助力服务器安全与社区管理。

---

## 📝 主要功能

1. 🖥️ **网页注册与审核**：玩家可通过网页提交白名单申请，管理员可在线审核、封禁、管理玩家。
2. 🔒 **自动/手动审核**：支持自动通过或管理员手动审核，灵活适配不同服务器需求。
3. 🚫 **封禁系统**：支持对违规玩家进行封禁，保障服务器安全。
4. 🎨 **GlassX 主题**：精美的玻璃拟态设计，流畅动画与现代化界面。
5. 📨 **邮件验证与域名白名单**：集成 SMTP 邮箱验证码，支持邮箱域名白名单与别名限制。
6. 🔐 **自托管图形验证码**：内置图形验证码（数学题/文字）- 无需外部服务。
7. 🎮 **Discord 集成**：OAuth2 Discord 账户绑定，支持可选/强制模式。
8. 📋 **注册问卷**：可自定义问卷系统，支持多语言。
9. 📧 **用户通知**：白名单审核通过/拒绝时自动发送邮件通知。
10. 🌐 **多语言支持**：支持中英文界面与消息。
11. ⚙️ **高度定制化**：自定义单邮箱账号上限、玩家ID正则、免白名单IP等。
12. 🔄 **自动更新与备份**：配置文件自动升级，升级前自动备份数据。
13. 🧩 **灵活白名单模式**：支持 Bukkit 原生白名单同步、插件自管理，以及 MySQL 存储。
14. 💾 **MySQL 与本地文件存储**：可在配置中自由切换本地文件和 MySQL 存储，支持自动迁移。
15. 📝 **审核日志多存储**：审核日志可存储于本地文件或 MySQL。
16. 🌍 **自定义多语言国际化**：自动加载任意 messages_xx.properties 文件，用户可自定义任意语言。
17. 🔐 **AuthMe 集成**：与 AuthMe 插件无缝集成，支持密码管理和自动注册。
18. 🎮 **基岩版支持**：支持 Geyser/Floodgate 玩家前缀，实现跨平台服务器兼容。
19. 🔗 **代理支持**：BungeeCord/Velocity 代理插件，实现群组级白名单管控。
20. 🤖 **LLM 问答评分**：AI 驱动的文本问答自动评分，支持 DeepSeek/Google，内置熔断器与并发控制。
21. 🛠️ **游戏内管理命令**：新增 `/vmc` 指令集，支持在游戏内快速审核、封禁、解封玩家。
22. 🛡️ **增强安全性**：采用 SHA-256 + 盐值加密存储密码。

---

## 🖼️ 截图预览（GlassX 主题）

### 首页

![首页 GlassX](docs/zh/screenshot-home-glassx.png)

### 注册页

![注册页 GlassX](docs/zh/screenshot-register-glassx.png)

### 管理后台

![后台 GlassX](docs/zh/screenshot-admin-glassx.png)

---

## 🛠️ 技术栈

- Java（Bukkit/Spigot/Paper/Folia 插件）
- 前端：Vue3 + Tailwind CSS（支持自定义主题，背景统一由全局样式维护；历史背景组件 `BackgroundView.vue` / `PageBackground.vue` 已移除）
- WebSocket 实时通信
- 邮件服务：SMTP

---

## 📊 Bstats

![Bstats](https://bstats.org/signatures/bukkit/verifymc.svg)

---

## 📦 安装与配置

1. 下载最新版 `VerifyMC.jar`，放入服务器 `plugins` 目录。
2. 启动服务器自动生成配置文件，按需编辑 `config.yml`（见下方完整示例）。
3. 重启服务器，访问 `http://你的服务器IP:8080` 进入管理后台。

### ✅ 建议最低环境

- Java 17+
- Bukkit/Spigot/Paper/Folia 1.20+
- 可公网访问并启用 HTTPS 的域名（生产环境强烈建议）
- SMTP 邮箱账号（当使用 `email` 验证方式时必需）

### ⚡ 5 分钟快速开始

1. 在 `config.yml` 中设置 `auth_methods: [captcha]`（最快启动方式，无需 SMTP）。
2. 设置 `whitelist_mode: plugin` 和 `web_register_url: https://your-domain.com/`。
3. 通过网页注册账号，然后在服务器中给自己 OP 权限（`op <用户名>`）即可访问管理后台。
4. （可选）小型私服可开启 `register.auto_approve: true`。
5. 重启服务器并访问 `http://你的服务器IP:8080`。

### 🧪 从源码构建

```bash
cd plugin
mvn clean package
```

产物 jar：`plugin/target/verifymc-1.7.2.jar`

---

## 💬 官方社区

- **QQ 群**: 1041540576 ([点击加入](https://qm.qq.com/q/F7zuhZ7Mze))
- **Discord**: [https://discord.gg/TCn9v88V](https://discord.gg/TCn9v88V)

---

> ❤️ 如果你喜欢本项目，欢迎 Star、分享与反馈！
