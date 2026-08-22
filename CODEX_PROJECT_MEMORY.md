# VerifyMC 项目记忆

## 项目概况
- VerifyMC 是面向 Minecraft Bukkit/Spigot/Paper/Folia 的验证插件，包含代理端插件和 Vue 3 + TypeScript 管理前端。
- 主要目录：`plugin/`、`plugin-proxy/`、`frontend/glassx/`。

## 技术栈与入口
- 后端：Java 17 + Maven；主插件资源在 `plugin/src/main/resources`。
- 代理端：Java 17 + Maven；资源在 `plugin-proxy/src/main/resources`。
- 前端：Vue 3、TypeScript、Vite、vue-i18n。
- 版本源文件与中英文 Release 正文：`version.yml`；中文和英文统一维护在 Release 正文中。
- README 入口：`README.md` 为中文默认说明，`README_en.md` 为英文说明，两个文件互相链接。

## 常用命令
- `cd frontend/glassx; npm ci; npm run build`
- `cd frontend/glassx; npm run lint; npm run type-check; npm run test`
- `cd plugin; mvn clean package`
- `cd plugin-proxy; mvn clean package`

## 当前注意事项
- 默认语言已统一为中文，仍支持显式切换英文；涵盖主插件、代理端、前端和后端请求兜底。
- `.github/workflows/plugin.yml` 仅负责构建；`.github/workflows/release.yml` 通过 `workflow_dispatch` 独立发布，不再读取 `version.yml` 的 `release` 字段。
- Release 正文直接读取 `version.yml`，中文在前、英文在后。
- 工作区已有用户修改：`.trae/rules/git-commit-message.md`，不要覆盖或回滚。
- 发布/部署前必须先构建 `frontend/glassx`，再将 `dist/*` 复制到 `plugin/src/main/resources/static/glassx/` 后打包主插件；若 JAR 只有 `static/glassx/index.html`，浏览器会只显示背景而无法加载 Vue 应用。

## 已完成事项
- 已将中文设为默认文本并保留中英文切换。
- 已增加独立 Release workflow，发布不再由 `version.yml` 的 `release` 字段决定；Release 信息中文置前并包含英文。

## 验证状态
- 前端 `npm run build`、`npm run type-check`、`npm run test` 已通过（5 个测试通过）；ESLint 仅有既存 `Dashboard.vue` 未使用变量 warning。
- 主插件和代理端 `mvn clean package`、`mvn test` 已通过；两个模块当前没有 Java 测试源。
- 审查修复：语言切换组件现在会校验 localStorage，只接受 `zh`/`en`，避免非法 locale 污染界面。
- 2026-08-22：确认前端空白问题由主插件 JAR 未包含 Vite CSS/JS 资源导致；已同步完整 `dist` 并重新生成 `plugin/target/verifymc-1.7.2.jar`。
- 版本已推进到 `1.7.3`；版本检查改为 GitHub `releases/latest` API，仅使用正式版，不再读取分支 `pom.xml`。
- 管理员网页登录使用已注册账号的用户名/邮箱和密码，且该用户必须在服务器 `ops.json` 中（可通过 `/op <玩家名>` 添加）。
- 2026-08-22：代理插件已成功生成 `plugin-proxy/target/verifymc-proxy-1.7.3.jar`；主插件已重新同步完整 Vite `dist` 后生成 `plugin/target/verifymc-1.7.3.jar`，JAR 内包含 `static/glassx/index.html`、CSS 和 JS。
- 2026-08-22：后台状态卡、下载中心、个人资料和 Dialog 收敛为实体深色工作台样式，减少玻璃拟态、模糊和渐变；前端 lint、type-check、test（5 项）和 build 均通过。
- 本地仅启动 Vite 时 `/api/config`、`/api/version` 等请求会因没有 Bukkit 后端而返回 500/连接失败；这是开发环境缺少后端，不代表打包后的静态前端空白。
