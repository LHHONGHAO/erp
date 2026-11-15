# 手机 Web / PWA

## 定位

`jshERP-mobile` 是部署在 `/mobile/` 下的手机 Web 入口，可由手机浏览器直接访问，也可以通过 PWA 添加到主屏幕。它不是独立原生 App。

手机端负责登录、菜单首页、个人中心、导航和安装提示。业务菜单优先复用现有 Web 页面：站内地址在手机端路由中由 `WebView.vue` 使用 iframe 打开；绝对 HTTP(S) 地址由首页另开浏览器窗口。移动业务页需要逐步独立适配时，再按业务模块添加到本目录。

## 目录职责

```text
jshERP-mobile/
├─ public/                  HTML 模板、PWA manifest、Service Worker、图标
├─ scripts/                 调用 Web 端 Vue CLI 的 serve/build 包装脚本
├─ src/
│  ├─ api/                  登录、验证码、菜单接口
│  ├─ components/           导航栏、底部栏、PWA 安装提示
│  ├─ layouts/              主页面布局
│  ├─ router/               Hash 路由和登录态守卫
│  ├─ store/                Vuex 登录状态、用户信息和存储键
│  ├─ utils/                请求、存储、PWA 生命周期工具
│  ├─ views/                登录、首页、个人中心、业务 WebView
│  ├─ App.vue               应用容器和全局基础样式
│  └─ main.js               Vue 初始化入口
└─ vue.config.js            手机端构建、资源路径和开发服务器配置
```

## 入口与构建

- 手机端没有单独的 `package.json`，依赖和 Vue CLI 由 `jshERP-web` 统一维护。
- `npm run serve:mobile` 在 `jshERP-web` 目录启动手机端开发服务，默认端口为 `3001`。
- `npm run build:mobile` 单独构建手机端；`npm run build` 会先构建电脑端，再构建手机端。
- 手机端产物位于 `jshERP-web/dist/mobile`，`publicPath` 为 `/mobile/`。部署时应与电脑端静态资源一起发布。
- 路由使用 Hash 模式，手机端入口地址形如 `/mobile/#/home`，刷新子页面不依赖服务器 history fallback。

## 登录、接口和缓存

- 手机端通过同源 API 请求后端，`utils/request.js` 统一附加访问令牌。
- `utils/storage.js` 使用 `vue-ls` 的 `pro__` 命名空间，与电脑端共享同源登录状态；修改该命名空间会影响两端登录互通。
- 首页菜单使用 `sessionStorage` 做首屏缓存，键按用户 ID 区分。它是临时展示缓存，不是菜单数据的权威来源；页面启动后会请求后端刷新。
- `public/sw.js` 当前只完成 PWA 所需注册，不缓存或拦截网络请求。更新策略应以入口 HTML 和 Service Worker 及时重新验证、带 hash 的构建资源可长期缓存为准。

## 维护约定

1. 保持本目录作为手机 Web/PWA 外壳，不为原生 App 单独引入一套依赖和发布流程。
2. 手机端通用 UI 放入 `components/`，路由级页面放入 `views/`；当业务页数量增长时，再按业务域在 `views/` 下分目录。
3. 需要复用电脑端页面时，通过菜单路由进入 `WebView.vue`；需要手机端特有交互或移动布局时，直接在手机端实现对应页面，不把业务代码塞进布局组件。
4. 保持 WebView 地址来源可追踪。业务菜单 URL 属于可信配置；若未来允许用户输入或外部传入 URL，应增加同源/允许列表校验。
5. 手机端私有缓存通过 `utils/` 管理并按用户隔离；账号共享数据沿用现有 `pro__` 命名空间，退出登录时清理对应数据。
6. 变更入口路径、PWA scope、图标或缓存规则时，同步检查 `vue.config.js`、`public/index.html`、`public/manifest.json` 和部署静态目录。
