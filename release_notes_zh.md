[English](https://github.com/KiteMC/VerifyMC/releases/tag/v1.2.0) | 简体中文 | [📚 官方文档](https://kitemc.com/docs/verifymc/)

## 1.2.0 更新日志

### 🎉 新功能

#### AuthMe 集成支持
- **密码管理**: 支持在Web注册时设置密码，与AuthMe插件无缝集成
- **自动注册**: 审核通过的用户可自动注册到AuthMe系统
- **自动注销**: 删除用户时可自动从AuthMe系统注销
- **密码修改**: 管理员可在管理面板中修改用户密码
- **密码验证**: 支持自定义密码正则表达式验证规则

### 🐛 问题修复

- 修复了通过`/vmc add`添加的用户状态被覆盖的问题