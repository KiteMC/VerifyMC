# VerifyMC GlassX Frontend

## 更新内容

本项目已成功集成了以下组件：

1. **HeroGeometric 主页组件** - 来自 `@/hero-geometric` 的几何动画主页
2. **AnimatedMenuBar 选择栏** - 来自 `@/animated-menu` 的动画菜单栏
3. **LoginForm 登录界面** - 来自 `@/login` 的登录表单设计
4. **RegisterForm 注册界面** - 基于 `@/login` 设计的注册表单

## 主要变更

### 主页
- 移除了原有的上方导航栏
- 将 animated-menu 的选择栏放置在页面顶部中央
- 集成了 hero-geometric 的几何动画背景和标题
- 更新了菜单项以符合 VerifyMC 项目需求：
  - Home (首页)
  - Register (注册)
  - Login (登录)
  - Admin (管理)

### 登录界面
- 完全替换为 `@/login` 项目的设计风格
- 使用简洁的卡片式布局
- 包含邮箱和密码字段
- 支持 Google 登录选项
- 提供忘记密码链接
- 包含注册页面跳转链接

### 注册界面
- 基于 `@/login` 的设计风格
- 包含用户名、邮箱、密码和确认密码字段
- 支持 Google 注册选项
- 包含登录页面跳转链接

## 安装依赖

```bash
npm install
```

## 运行项目

```bash
npm run dev
```

## 新增依赖

- `framer-motion` - 动画库
- `clsx` - CSS类名工具
- `tailwind-merge` - Tailwind CSS类合并工具

## 组件结构

### 主页组件
- `src/components/HeroGeometric.vue` - 几何动画主页组件
- `src/components/ElegantShape.vue` - 优雅形状动画组件
- `src/components/AnimatedMenuBar.vue` - 动画菜单栏组件

### UI组件
- `src/components/ui/Card.vue` - 卡片组件
- `src/components/ui/CardHeader.vue` - 卡片头部组件
- `src/components/ui/CardTitle.vue` - 卡片标题组件
- `src/components/ui/CardDescription.vue` - 卡片描述组件
- `src/components/ui/CardContent.vue` - 卡片内容组件
- `src/components/ui/Button.vue` - 按钮组件
- `src/components/ui/Input.vue` - 输入框组件
- `src/components/ui/Label.vue` - 标签组件

### 表单组件
- `src/components/LoginForm.vue` - 登录表单组件
- `src/components/RegisterForm.vue` - 注册表单组件

### 工具
- `src/lib/utils.ts` - 工具函数 