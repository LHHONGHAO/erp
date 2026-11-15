# 地址识别 · 打印页「关闭无效 / 模板未加载」修复（规格说明）

> 状态：待确认（确认后进入实现）
> 涉及端：jshERP-web 前端（手机端 H5 与电脑端同一构建）；不涉及后端与数据库变更

## 1. 现象

地址识别页 → 点击「打印」→「地址打印」页：

1. 显示「暂无可用地址打印模板」+「创建地址打印模板」，已有模板未加载；
2. 点击右上角 × 无反应，页面不关闭，回不到地址识别页。

## 2. 已确认事实（排除项）

| 检查项 | 结论 |
| --- | --- |
| 服务器 `jsh_platform_config` | `platform_key='address_layout'`（id=23）存在，内容为合法 v2 JSON（`schemaVersion=2`，elements = contacts / telephone / address / goods / page_no） |
| 后端接口 | 运行中的 jar 含 `GET /platformConfig/getUiConfig`、`POST /platformConfig/saveUiConfig`；service 按 key 精确查询、无额外过滤 |
| 后端日志 | 14:11:45 有 `UPDATE jsh_platform_config SET platform_value=? WHERE id=23`（保存成功）；无 address_layout 相关 ERROR |
| 已部署产物 | 包含 `address_layout`、`getUiConfig` 代码 |
| 静态链路 | `openByType → loadRouteTemplate('address') → normalizeTemplate → applyTemplate` 正常；`template` 仅在 `openByType/open` 中置 null |

结论：模板数据、接口、渲染链路均无问题。打印页仍为空，只能是**请求失败/返回空被静默吞掉**后落入空状态，或**设备仍在跑旧缓存包**。当前代码不区分「加载失败」与「确实没有模板」，因此无任何可判断的提示。

## 3. 根因分析

### 3.1 模板未加载（主因）

`LabelPrintModal.vue` → `loadRouteTemplate`（1870-1913）config 分支：

- 非 200、解析失败、请求异常统一 `return null`，且 `catch` 吞掉错误（1894）；
- `openByType`（1336-1349）拿到 null 且 `outdatedTemplate=false` → `applySnapshot()`；地址页写入的快照 `selectedAttrs: []`，`normalizeTemplate` 再次返回 null → `template` 保持 null → 空状态。

即：任何一次失败（token 失效、网络异常、网关拦截、返回空串、设备旧包）都表现为「暂无可用地址打印模板」，且无提示、无重试入口，无法定位。

次因：设备可能仍加载旧 `index.html` 引用的旧 bundle（今日多次构建部署），旧包模板 key/加载逻辑与服务器数据不一致。

### 3.2 关闭无效

`handleCancel`（1850-1853）只置 `visible=false`，`MobileModalPage` 的 `visible` watcher（114-118）→ `toggleBodyLock(false)` → `applyBodySwipeLock(false)` → `releaseHistoryGuard()`（67-80）→ `history.replaceState` + `history.back()`。

- `pushHistoryGuard`（50-65）在发现 `history.state.__mmpSwipeGuard` 时走「接管」分支：**不 pushState**，但把 `historyGuardActive` 置 true；
- 关闭时 `releaseHistoryGuard` 仍无条件 `history.back()`。此时弹层没有属于自己的哨兵记录，back() 会把整页路由退到地址识别页之前的一页（或触发 popstate 使路由与弹层状态错位），表现为「关不掉 / 回不去」。
- 另外 `handleCancel` 未复位 `showPrinterSheet / wifiAddStep / printerSettingsVisible`，关闭瞬间若处于设备页，`visible` 与 `showPrinterSheet` 组合会让移动端页面再次渲染。

## 4. 修复方案

### F1 区分「加载失败」与「无模板」+ 缓存兜底

文件：`d:\erp\jshERP-web\src\views\material\modules\LabelPrintModal.vue`

1. data 新增 `templateLoadError: ''`。
2. `openByType(type, options)` 开头复位：`this.templateLoadError = ''`。
3. `loadRouteTemplate` 的 `cfgInfo` 分支改为显式记录失败原因并写/读本地缓存：

```js
return getAction('/platformConfig/getUiConfig', { key: cfgKey, _t: Date.now() }).then(function(res) {
  if (!res || res.code !== 200) {
    self.templateLoadError = '接口返回异常（code=' + (res && res.code) + '）'
    return null
  }
  var rawData = res.data
  var cfg = parseTemplateRaw(rawData)
  if (!cfg) {
    self.templateLoadError = '模板数据为空或格式无法解析'
    return null
  }
  if (!isLabelTemplateV2(cfg)) {
    self.outdatedTemplate = true
    return null
  }
  try {
    localStorage.setItem('jsh.uiConfig.' + cfgKey, typeof rawData === 'string' ? rawData : JSON.stringify(cfg))
  } catch (e) {}
  return cfg
}).catch(function(err) {
  self.templateLoadError = (err && (err.message || err.msg)) || '网络异常'
  // 弱网/离线兜底：读取编辑保存时写入的本地缓存
  try {
    var cached = parseTemplateRaw(localStorage.getItem('jsh.uiConfig.' + cfgKey))
    if (cached && isLabelTemplateV2(cached)) return cached
  } catch (e2) {}
  return null
})
```

4. 空状态提示按 `templateLoadError` 分支：失败时显示「模板加载失败：{原因}」+「重新加载」链接；确实无模板时保持「暂无可用地址打印模板」。改动点：现有 `emptyMessage` computed + 空状态 `a-alert`/`a` 区域。
5. 新增 `reloadRouteTemplate()`：清空 `templateLoadError`，重新走 `loadRouteTemplate → normalizeTemplate → applyTemplate → applySnapshotValues`，成功提示「模板已加载」。
6. 失败时 `console.warn('[LabelPrintModal] 模板加载失败', cfgKey, this.templateLoadError)` 便于现场取证。

### F2 关闭行为确定化

文件：`d:\erp\jshERP-web\src\components\mobile\MobileModalPage.vue`

1. 新增模块级 `historyGuardPushed = false`。
2. `pushHistoryGuard()`：真正执行 `pushState` 后 `historyGuardPushed = true`；「接管遗留哨兵」分支 `historyGuardPushed = false`。
3. `releaseHistoryGuard()`：

```js
function releaseHistoryGuard() {
  if (!historyGuardActive) return
  historyGuardActive = false
  var state = window.history.state
  if (state && state.__mmpSwipeGuard) {
    var next = Object.assign({}, state)
    delete next.__mmpSwipeGuard
    try { window.history.replaceState(next, '', window.location.href) } catch (e) {}
  }
  // 只有我们自己压入过哨兵，才回退；接管的历史记录不能退，否则会退到上一页
  if (!historyGuardPushed) return
  historyGuardPushed = false
  try { window.history.back() } catch (e) {}
}
```

4. `onSwipeGuardPopState()` 中同步 `historyGuardPushed = false`。
5. `.mmp-close` 改为 `@click.stop="$emit('close')"`，避免冒泡到页面级点击处理。

文件：`d:\erp\jshERP-web\src\views\material\modules\LabelPrintModal.vue`

```js
handleCancel() {
  this.showPrinterSheet = false
  this.wifiAddStep = ''
  this.printerSettingsVisible = false
  this.printerEditVisible = false
  this.templateLoadError = ''
  this.visible = false
}
```

### F3 编辑保存写本地缓存（供 F1 兜底）

文件：`d:\erp\jshERP-web\src\views\material\modules\LabelEditMobile.vue`

```js
postSnapshot(key, snap) {
  var value = JSON.stringify(snap)
  return postAction('/platformConfig/saveUiConfig', { key: key, value: value }).then(function(res) {
    if (res && res.code === 200) {
      try { localStorage.setItem('jsh.uiConfig.' + key, value) } catch (e) {}
    }
    return res
  })
}
```

（仅 platformConfig 系列模板使用该方法；缓存写入失败不影响保存结果。）

## 5. 验收标准

1. 地址识别页填信息 → 「打印」→ 打印页展示模板预览（服务器已有 `address_layout`），不再空状态。
2. 无模板时显示「暂无可用地址打印模板」+「创建地址打印模板」；加载失败时显示具体原因 +「重新加载」，有本地缓存时优先展示缓存。
3. 打印页点 × → 立即关闭并停留/回到地址识别页，hash 不变、无需再按浏览器返回，地址页已填数据保留。
4. 打印设备页打开时点 × → 同上，不出现「关掉又出现」。
5. 连续开关 3 次，路由历史不倒退（返回键仍回到工作台）。
6. 手机端与电脑端表现一致（同一构建）。

## 6. 验证步骤

1. 设备强刷（清站点缓存/关标签重开），确认加载的是最新 bundle。
2. 地址识别 → 打印：有预览即通过；仍为空则读取页面提示文案（「接口返回异常(code=401/500)」「网络异常」「模板数据为空…」），该文案即根因证据。
3. 点 ×：确认回到地址识别页且输入内容保留。
4. 重复开关 3 次，检查地址栏 hash 不变。
5. 飞行模式下打开打印页：应展示本地缓存模板并提示。

## 7. 风险

- `MobileModalPage` 的 history 守卫改动影响所有手机端弹层页（会员标签、发货模板、打印机列表等）：关闭不再做多余 back()，行为更安全，但需回归 2-3 个弹层页的关闭与 iOS 侧滑手势。
- 本地缓存仅兜底、无版本校验，可能与服务器不一致，需在界面提示「已使用本地缓存模板」。
