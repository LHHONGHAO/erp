# Spec：零售出库→发货模板编辑→预览回填 修复

日期：2026-11-20　范围：jshERP-web（仅移动端标签链路）　状态：待用户确认

## 1. 现象

从零售出库列表「发货」进入的打印预览，点「编辑」进入发货模板编辑页（发货模版编辑，148×212mm）：

1. 元素面板顶部多出「数据表」下拉框（显示「销售出库」），与标准元素面板不一致；标准面板无下拉框，字段由「表单数据」弹层勾选后回填到元素/面板。
2. 编辑页点「打印」回到预览页时，预览拿不到商品的具体信息（明细/字段值空白）。

## 2. 根因（可核验证据链）

### 2.1 标准链路（正常）

- 零售出库列表 [RetailOutList.vue:653-664](file:///D:/erp/jshERP-web/src/views/bill/RetailOutList.vue#L653-L664) `openDeliveryPrint`：
  `extractDeliveryValues` → dn_* 值；`extractDeliveryRows` → 明细行；
  `saveLabelEditSnapshot({type:'delivery', number, values, rows})` 写入 sessionStorage `jsh.labelEdit.snapshot`；再 `openByType('delivery')`（**不传 record**）。
- 预览 [LabelPrintModal.vue:1477-1498](file:///D:/erp/jshERP-web/src/views/material/labelPrint/LabelPrintModal.vue#L1477-L1498) `openByType`：`record = opts.record || {}`；数据全靠快照（values → 字段值，rows → detailRows/明细表）。

### 2.2 根因 A：编辑页「打印」用空业务数据覆盖快照（问题 2 的直接原因）

- 预览「编辑」 [LabelPrintModal.vue:3351-3388](file:///D:/erp/jshERP-web/src/views/material/labelPrint/LabelPrintModal.vue#L3351-L3388) `handleEdit`：经 [templateEditDataModel.js:70-112](file:///D:/erp/jshERP-web/src/views/material/labelPrint/utils/templateEditDataModel.js#L70-L112) `buildTemplateEditContext` 瘦身——record 只剩 `{categoryId, goodsRows:[{categoryId}]}`，editorData 只留字段结构（值清空）。**编辑页天生没有业务值/明细行，这是有意设计。**
- 编辑页「打印」 [LabelEditMobile.vue:3317-3352](file:///D:/erp/jshERP-web/src/views/material/modules/LabelEditMobile.vue#L3317-L3352) `handlePrint`：
  - `snapshot.values = valuesFromAttrs(this.allAttrs)`（[labelEditSnapshot.js:49-55](file:///D:/erp/jshERP-web/src/views/material/labelPrint/utils/labelEditSnapshot.js#L49-L55)）——编辑页 allAttrs 的 value 全为空串 → **values 全空**；
  - 仅 `templateType === 'receipt'` 时回带 `snapshot.rows`（3330-3332）；**delivery 无 rows**；
  - `saveLabelEditSnapshot(snapshot)` → **覆盖 2.1 写入的完整业务快照**；
  - `openByType(type, {templateId, name, directEntry:false})` 不传 record → 预览 record={}、快照 values 空、rows 无 → 预览空白。
- 小票之所以没暴露：receipt 分支回带了 rows（虽同样丢 values，预览端按明细行重建）。发货单缺同一补偿。

### 2.3 根因 B：「数据表」下拉框来自 provider 数据源双轨（问题 1 的直接原因）

- [LabelEditMobile.vue:1549-1550](file:///D:/erp/jshERP-web/src/views/material/modules/LabelEditMobile.vue#L1549-L1550)：delivery 类型 businessType 兜底为 `'delivery'`；
- [LabelEditMobile.vue:1797-1815](file:///D:/erp/jshERP-web/src/views/material/modules/LabelEditMobile.vue#L1797-L1815) `initProvider`：businessType 非空即拉 `/labelProvider/list` → providerList 含「销售出库」等数据表；
- [LabelElementPanel.vue:5-16](file:///D:/erp/jshERP-web/src/views/material/labelPrint/edit/LabelElementPanel.vue#L5-L16)：`v-if="providers.length"` 渲染「数据表」下拉 → 标准商品标签（product 数据源列表为空）因此无下拉框；
- [LabelEditMobile.vue:1825-1869](file:///D:/erp/jshERP-web/src/views/material/modules/LabelEditMobile.vue#L1825-L1869) `applyProviderData`：provider 字段会以 group「销售出库」覆盖表单字段清单（无 editorData 时），与标准「表单数据→回填」链路形成双轨。

### 2.4 结论

- 元素面板字段本体（分组 单据头/金额合计/其它）已与 deliveryForm 表单配置一致（面板按 `selectedAttrs ∪ formDataCheckedKeys` 过滤，[LabelEditMobile.vue:951-977](file:///D:/erp/jshERP-web/src/views/material/modules/LabelEditMobile.vue#L951-L977)）；不标准的是**顶部的 provider 下拉框**这一数据源轨道。
- 预览丢商品信息不是存储问题（模板保存正常），是**编辑→预览快照传递丢业务数据**。

## 3. 修复方案（收敛共享模块，页面只传数据）

### Fix 1：编辑页打印快照合并业务数据（改 labelEditSnapshot.js + LabelEditMobile.vue 各 1 处）

- [labelEditSnapshot.js](file:///D:/erp/jshERP-web/src/views/material/labelPrint/utils/labelEditSnapshot.js) 新增共享纯函数：
  `mergeEditSnapshotForPrint(templateSnapshot, templateType)`
  - 读 `readLabelEditSnapshot()`；旧快照 `type` 匹配（或无 type）时，保留其业务字段 `number / values / rows`；
  - 模板结构字段（labelWidth/labelHeight/selectedAttrs/attrStyles/attrLabels/template/hiddenItemKeys/pageMode/flowElementKeys/specBlocks 等）一律以编辑页 snapshot 为准（编辑态是最新事实）；
  - type 不匹配（跨业务残留）则不合并，仅返回模板快照。
- [LabelEditMobile.vue:3317-3352](file:///D:/erp/jshERP-web/src/views/material/modules/LabelEditMobile.vue#L3317-L3352) `handlePrint`：
  - 删除 receipt 特判回带 rows 的分支，统一改为
    `snapshot = mergeEditSnapshotForPrint(snapshot, this.templateType)` 后 `saveLabelEditSnapshot(snapshot)`；
  - receipt 原 `this.record.goodsRows` 兜底仅在合并后仍无 rows 且有旧快照缺 rows 时保留，行为不回退。
- 效果：delivery/label/member/address 编辑→打印预览均保留进编辑前的业务值与明细行；receipt 同步受益。

### Fix 2：delivery 元素面板去「数据表」下拉（改 LabelEditMobile.vue initProvider）

- [LabelEditMobile.vue:1798-1815](file:///D:/erp/jshERP-web/src/views/material/modules/LabelEditMobile.vue#L1798-L1815) `initProvider`：`templateType === 'delivery'` 时不再请求 `/labelProvider/list`（providerList 保持空 → 下拉框不渲染），字段唯一来源 = `buildFormFieldAttrs('delivery')` + 「表单数据」勾选回填；
- provider 标识仍按 `context.template.provider || 'sales.delivery'` 兜底写回（仅存储兼容，不进 UI），`buildTemplateSnapshot` 的 `provider: vm.provider || vm.businessType` 不变；
- member/address/label 现有 provider 行为不动（本次只收敛 delivery，符合用户指认的场景）。

### 不改动

- 后端 `/labelProvider/*`、PC 端 LabelEditModal、模板 v2 存储格式、goodsLabelLayout/labelTemplateFlow 商品块模型。

## 4. 实现时核验点（不改变上述决策）

- `openByType → loadRouteTemplate/applySnapshot` 对快照中 template 字段的消费方式（只确认，不改机制）。
- `openFormDataSheet` 对 delivery 的 fieldGroups 来源（应为 buildAttrs 表单配置）。
- 后端 `/labelProvider/list?businessType=product` 返回为空（解释标准面板无下拉；仅验证，不改后端）。

## 5. 验证

1. harness（D:\erp\tools\label-repro）：为 `mergeEditSnapshotForPrint` 增加用例——旧快照 values/rows 保留、模板结构以编辑页覆盖、type 不匹配不合并、无旧快照兜底。
2. 构建：`npm run build:pc`（node16），**只构建不部署**；产物经用户确认后再部署。
3. 手动验收路径：零售出库 → 发货 → 预览（有商品）→ 编辑 → 调整元素 → 打印 → 预览商品信息仍在；发货编辑页元素面板无「数据表」下拉；标准商品标签编辑回归无变化。
