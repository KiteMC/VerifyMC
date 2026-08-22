# VerifyMC

[English](README_en.md) | 简体中文

VerifyMC 是面向 Minecraft Bukkit、Spigot、Paper、Folia 服务器的白名单验证与用户管理插件，默认使用中文，支持切换英文。插件提供网页注册、邮箱验证、审核管理、封禁、AuthMe 集成、问卷审核、Discord 绑定以及 BungeeCord/Velocity 代理支持。

## 功能特性

- 网页注册与白名单申请审核
- 自动审核和管理员手动审核
- 邮箱验证码与邮箱域名白名单
- 内置数学题/文字验证码，无需外部验证码服务
- 用户封禁、解封、密码修改和资料管理
- AuthMe 密码同步与用户同步
- 可选的注册问卷与 LLM 自动评分
- Discord OAuth2 账号绑定
- Bukkit 原生白名单、插件自管理和 MySQL 存储模式
- BungeeCord、Waterfall、Velocity 代理端支持
- GlassX 管理前端与 WebSocket 实时审核通知
- 配置、语言文件和资源自动更新及备份

## 环境要求

- Java 17 或更高版本
- Bukkit、Spigot、Paper 或 Folia 1.20+
- 使用邮箱验证时需要可用的 SMTP 邮箱
- 生产环境建议为网页服务配置 HTTPS 域名

## 安装与配置

1. 从 [GitHub Releases](https://github.com/KiteMC/VerifyMC/releases) 下载最新的 `verifymc-版本.jar`。
2. 将主插件放入服务器的 `plugins` 目录；使用代理时同时安装对应的 `verifymc-proxy-版本.jar`。
3. 启动服务器，让插件生成默认配置文件。
4. 编辑 `plugins/VerifyMC/config.yml`，设置网页端口、注册地址、验证方式和存储方式。
5. 重启服务器并访问 `http://你的服务器地址:8080`。

### 管理员登录

管理员面板使用服务器 OP 身份认证，不使用单独的默认管理员密码：

1. 先让管理员账号完成一次网页注册，记住注册时的用户名（或邮箱）和密码。
2. 在服务器控制台或游戏内执行 `/op 玩家名`，将该 Minecraft 用户加入 OP 列表。
3. 打开网页右上角的“登录”，输入该账号和密码。登录成功后会进入控制面板，管理员菜单会自动显示。

如果登录时报“没有管理员权限”，请确认用户名与 `ops.json` 中的 OP 名称一致，并重试登录。

默认配置语言为中文。管理员可以在网页端使用语言切换器切换为英文，也可以在配置文件中设置：

```yaml
language: zh # 可选 zh 或 en
```

### 快速开始

不使用 SMTP 时，可以先使用内置验证码：

```yaml
auth_methods:
  - captcha
whitelist_mode: plugin
web_register_url: https://你的域名/
```

小型私服可以启用自动审核：

```yaml
register:
  auto_approve: true
```

## 构建项目

### 前端

```bash
cd frontend/glassx
npm ci
npm run build
```

### 主插件

```bash
cd plugin
mvn clean package
```

产物位于 `plugin/target/verifymc-版本.jar`。

### 代理插件

```bash
cd plugin-proxy
mvn clean package
```

产物位于 `plugin-proxy/target/verifymc-proxy-版本.jar`。

## 测试与质量检查

```bash
cd frontend/glassx
npm run lint
npm run type-check
npm run test
```

```bash
cd plugin
mvn test

cd ../plugin-proxy
mvn test
```

## Release

构建工作流位于 `.github/workflows/plugin.yml`，只负责构建和上传产物。

发布 Release 请在 GitHub Actions 中手动运行 `.github/workflows/release.yml`。Release 正文统一从 `version.yml` 读取，中文内容在前，英文内容在后，不再维护单独的中文更新日志文件。

## 文档与社区

- [官方中文文档](https://kitemc.com/zh/docs/verifymc/)
- [官方英文文档](https://kitemc.com/docs/VerifyMC/)
- [GitHub Releases](https://github.com/KiteMC/VerifyMC/releases)
- QQ 群：1041540576
- Discord：[https://discord.gg/TCn9v88V](https://discord.gg/TCn9v88V)

## 许可证与反馈

欢迎提交 Issue、改进建议和 Pull Request。喜欢这个项目可以在 GitHub 上 Star 支持。
