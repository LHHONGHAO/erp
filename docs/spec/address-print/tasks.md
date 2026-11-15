# 实现任务清单 · 地址打印页关闭与模板加载修复

> 对应规格：[spec.md](file:///D:/erp/docs/spec/address-print/spec.md)
> 状态：待实现

## 任务

- [x] T1 模板加载失败可见化（`LabelPrintModal.vue`）
  - [x] data 新增 `templateLoadError`
  - [x] `openByType` 复位 `templateLoadError`
  - [x] `loadRouteTemplate` config 分支：非 200 / 解析失败 / 异常分别记录原因，成功时写 `localStorage['jsh.uiConfig.<key>']`
  - [x] 请求异常时回退本地缓存（校验 v2）
  - [x] 空状态文案按 `templateLoadError` 分支 +「重新加载」入口
  - [x] 新增 `reloadRouteTemplate()`
  - [x] 失败时 `console.warn` 输出 key 与原因

- [x] T2 关闭行为确定化（`MobileModalPage.vue`）
  - [x] 新增 `historyGuardPushed`
  - [x] `pushHistoryGuard` 区分「自己压栈」与「接管遗留哨兵」
  - [x] `releaseHistoryGuard` 仅在 `historyGuardPushed` 时 `history.back()`
  - [x] `onSwipeGuardPopState` 复位 `historyGuardPushed`
  - [x] `.mmp-close` 改为 `@click.stop`

- [x] T3 关闭时复位子状态（`LabelPrintModal.vue`）
  - [x] `handleCancel` 复位 `showPrinterSheet / wifiAddStep / printerSettingsVisible / printerEditVisible / templateLoadError` 后再置 `visible=false`

- [x] T4 编辑保存写本地缓存（`LabelEditMobile.vue`）
  - [x] `postSnapshot` 成功（code=200）后写 `localStorage['jsh.uiConfig.<key>']`

- [x] T5 构建与部署
  - [x] `jshERP-web` 生产构建（电脑端 `dist` + 手机端 `dist/mobile`，2026-11-15 14:33 完成）
  - [x] 备份远端 `frontend-dist` 后上传新产物到 192.168.100.1（备份 `frontend-dist-bak-20261115-1437`）
  - [x] 验证入口 `index.html` 与新 hash 资源 200（`app.3ccfe88e.js`、`mobile/index.html`、`mobile/js/home.f4e62931.js` 均 200）

- [ ] T6 现场验证（手机端）
  - [ ] 强刷后地址识别 → 打印：模板正常预览
  - [ ] 点 × 回到地址识别页、数据保留、hash 不变
  - [ ] 连续开关 3 次不倒退
  - [ ] 断网时命中本地缓存模板
  - [ ] 回归：会员标签打印页、打印机列表页的关闭与 iOS 侧滑
