# 任务清单：标签表单字段配置统一 + 元素面板「表单数据」

> 对应规格：`d:\erp\specs\label-form-field-config\spec.md`
> 执行目录：`d:\erp\jshERP-web`

## T1 新建五份表单字段配置与统一入口

- [x] 新建 `src/views/material/labelPrint/config/formFields/` 目录
- [x] `labelForm.js`：`type: 'label'`，`title: '商品标签'`，`buildAttrs({record, sku})` 委托 `buildLabelAttrs`
- [x] `deliveryForm.js`：29 个 `dn_*` 字段，顺序/group 与 `buildDeliveryAttrs` 完全一致，值取 `record[key]`
- [x] `receiptForm.js`：9 个 `rc_*` 字段，值取 `record[key]`
- [x] `memberForm.js`：13 个会员字段及 record 映射（`phone_num←phoneNum` 等），`enabled` false→'禁用'
- [x] `addressForm.js`：委托 `buildAddressAttrs(record)`
- [x] `index.js`：`getFormFieldConfig` / `buildFormFieldAttrs` / `buildFormFieldSignature`

验收：五个配置导出的 key 与现状逐字一致；`buildFormFieldAttrs` 不修改原配置、不追加 `page_no`。

## T2 编辑页改读配置并删除旧构造

- [x] `LabelEditMobile.vue` 删除 `buildLabelAttrs`、`buildAddressAttrs` 导入，新增 `buildFormFieldAttrs`
- [x] `TYPE_CONFIG` 删除 5 处 `attrsBuilder`
- [x] `buildAttrs()` 改为按 `this.templateType` + `{record, sku}` 取配置，保留 `appendPageAttr`
- [x] 更新 L1213、L1392 两处调用
- [x] 删除 `buildMemberAttrs`、`buildDeliveryAttrs`、`buildReceiptAttrs`

验收：全局搜索 `attrsBuilder` 为 0；编辑页五类字段分组与改动前一致。

## T3 元素面板底部「表单数据」按钮

- [x] `.lem-side`（element 分支）在 `LabelElementPanel` 后新增 `.lem-side-foot` 与主按钮
- [x] 新增 `.lem-side-foot` 样式，固定在面板底部、不随字段列表滚动

验收：元素列表滚动时按钮位置固定；窄屏（≤360px）不被遮挡。

## T4 底部标准弹层与字段勾选

- [x] 新增 `a-modal`（`wrapClassName="lem-form-sheet-wrap"`，标题「表单数据」，底部单个「确定」）
- [x] 新增 data：`formDataVisible` / `formDataKeyword` / `formDataDraft`
- [x] 新增 computed：`formDataFieldList`（allAttrs 为主 + 配置补差，排除 page_no/custom_/规格项，规格块聚合为「品类规格」一条）
- [x] 新增 computed：`formDataGroups`（分组、搜索过滤、组头全选态与计数）
- [x] 新增方法：`openFormDataSheet` / `closeFormDataSheet` / `toggleFormDataItem` / `toggleFormDataGroup` / `confirmFormDataSheet`
- [x] 新增弹层样式（底部贴合、上圆角、内容区 62vh 滚动、固定在底部的确定按钮）

验收：点击按钮从底部弹出；列出全域字段与已选状态；搜索与分组全选可用；取消/点遮罩不改画布。

## T5 确定写回规则

- [x] 普通字段：新增走 `addAttr`，取消走「移出 selectedAttrs + 清理 attrStyles/attrLabels」
- [x] 规格块：新增/取消逐 key 走 `setAttrVisible`（保留样式，取消记入 `hiddenItemKeys`）
- [x] 批量写回后只 `pushSnapshot()` 一次并关闭弹层

验收：取消再勾选后元素位置/字号不丢失；撤销可一次性回退本次批量修改。

## T6 预览页改读同一配置

- [x] `LabelPrintModal.vue` 删除 `buildLabelAttrs` 导入，新增 `buildFormFieldAttrs`
- [x] `buildAttrList` 改为 `buildFormFieldAttrs(this.type, {record, sku})` + 追加 `page_no`
- [x] `handleEdit` 的 `productFields` 改用 `buildFormFieldAttrs('label', ...)`
- [x] 保留 `buildAddressAttrs` 供 `buildTemplateEditContext` 使用

验收：发货单/小票/会员预览显示各自字段；`applySnapshotValues` 回填业务值正常。

## T7 清理与构建验证

- [x] 全局搜索确认无 `buildDeliveryAttrs|buildReceiptAttrs|buildMemberAttrs|attrsBuilder` 残留
- [x] 确认 `buildLabelAttrs` 在编辑页/预览页无残留引用（`materialDisplay.js` 与 `addressFormFields.js` 内部保留）
- [x] 运行构建：

```
$env:Path='D:\erp\tools\node16-build;'+$env:Path; & 'D:\erp\tools\node16-build\npm.cmd' run build:pc
```

- [x] 构建通过；记录与改动前一致的告警基线

验收：构建成功，无新增错误。
