# Spec：标签打印全链路规则梳理与复用模块补齐

> 状态：待确认（确认后进入实现）
> 范围：jshERP-web 移动端标签打印链路（表单数据提取 → 元素面板回填 → 模板保存 → 预览 → 打印下发）
> 既有规格：[label-form-field-config](../label-form-field-config/spec.md)、[label-delivery-edit-fix](../label-delivery-edit-fix/spec.md)、[address-print-form-reuse](../address-print-form-reuse/spec.md)

## 1. 目标

按已约定规则对整条链路做一次完整审计，把规则收敛到复用模块，并补齐不符合规则的实现：

1. 页面表单数据提取：每个表单一个 `extract*` 入口，字段 key 与字段配置一致。
2. 元素面板回填：编辑页/预览页同源（`config/formFields`），勾选状态随模板保存。
3. 打印快照：表单页统一写 `jsh.labelEdit.snapshot`；编辑页打印统一合并业务数据。
4. 标签模板保存/读取：统一 `buildTemplateSnapshot` + `labelTemplateFlow` 分发。
5. 预览与打印：布局、折行、实际尺寸、打印下发使用同一套算法。

## 2. 已确认规则（审计结论，保持不破坏）

### R1 商品字段约定

- 含商品数据的表单（商品标签、发货单、销售开单/小票、地址打印），打印快照 `values` 必须并入「商品基础属性 + SKU 属性」真实值。
- 唯一实现：`utils/productFormFields.js`
  - `buildProductFormAttrs(rows)`：字段结构（值可空），`buildLabelAttrs({}, {})` 打底 + 逐行真实值补空。
  - `extractProductAttrValues(rows)`：只返回非空值；明细行 `materialName` 归一为 `name`，`sku` 支持 JSON 字符串。
- 字段 key 来源 `buildLabelAttrs`；商品条码 key 为 `mBarCode`；编码元素的 `code__<key>` 只复用该 key 的值（`labelCode.codeSourceKey`）。
- 编辑页 `LabelEditMobile.handlePrint` 的 `valuesFromAttrs(this.allAttrs)` 已覆盖各表单字段（含商品字段）。

### R2 类目约定

- 「类别」元素 key 固定 `label_category`，「规格」固定 `label_spec`（`utils/categorySpec.js`）。
- 类别取**一级类目**：二级子类目沿类目树向上回溯到根（`resolvePrimaryCategoryName`）。
- 规格按 `CATEGORY_SPEC_MAP`（一级类目名 → 规格名数组）读取；模板映射表 `specMapping` 为唯一权威，`specBlocks` 由其扁平派生。
- 明细行类目解析链固定：`row.categoryId → row.materialCategoryId → row.raw.categoryId → row.sku.categoryId`。
- 预览端 `ensureCategoryValues` 在快照缺 `label_category` 时按明细行类目树回填；`ensureSpecBlocks` 在模板未建立映射表时按一级类目补回规格块。

### R3 折行规则

- 折行宽度 = `min(元素宽, 标签宽 - x)`；元素宽为 0 时取 `标签宽 - x`；下限 20px。
- 三处一致（已验证）：布局 `goodsLabelLayout.applyTextOverflow`、预览 `LabelPrintModal.itemStyle`、编辑 `LabelEditMobile.getElementStyle`。
- 样式统一 `white-space: pre-wrap; word-break: break-all; overflow-wrap: anywhere`，固定高度元素 `overflow: hidden`。

### R4 实际尺寸展示

- `PX_PER_MM = 3.779528`（`utils/labelSchema.js`），模板坐标/尺寸存储用毫米，字号用像素。
- 预览 `updatePreviewScale` 与编辑 `fitLabelToView` 均 `min(适配屏幕缩放, 1)`，不放大。
- 打印下发 `labelPrint.buildHtml` 按 `px / PX_PER_MM` 生成 `@page size`，一页一张标签。

### R5 快照约定

- 表单页 → 编辑/预览的唯一载体：`utils/labelEditSnapshot.js`
  - 写入：`saveLabelEditSnapshot({ type, number, values, rows, ... })`
  - 读取：`readLabelEditSnapshot()`
  - 编辑页打印合并：`mergeEditSnapshotForPrint(templateSnapshot, templateType)`，仅保留旧快照 `number/values/rows`，模板结构以编辑页为准；`type` 不匹配不合并。
- 各表单统一 `extract*`：`extractAddressValues` / `extractDeliveryValues` + `extractDeliveryRows` / `extractRetailSaleValues` + `extractReceiptRows` / `extractMemberValues`；明细行必须带 `categoryId`。
- 预览 `applySnapshotValues` 按 key 回填元素，未登记 key 只补结构不伪造值。

### R6 模板保存/读取约定

- 唯一组装：`labelTemplateFlow.buildTemplateSnapshot`（v2），只存结构：`selectedAttrs/attrStyles/attrLabels/editorData/specBlocks/specMapping/hiddenItemKeys/formDataCheckedKeys`，不存业务值。
- 唯一分发：`resolveTemplate` / `saveTemplateSnapshot` 按 `storage`：
  - `localTemplates`：`labelTemplateStore`（`print_template`）。
  - `deliveryTemplate`：`deliveryNoteTemplateStore`（`businessType=shipping.delivery`，`meta.kind=delivery-note`）。
  - `platformConfig`：`/platformConfig/saveUiConfig`（小票/会员/地址）。
- `editorData` 只存字段结构（`normalizeEditorData` 剥离 value）。

## 3. 缺口与修复方案（本次改动）

### G1 地址打印快照未并入商品属性值（违反 R1）

- 文件：`jshERP-web/src/views/material/labelPrint/utils/addressFormFields.js`
- 现状：`extractAddressValues` 只输出地址字段 + `goods` 文本 + `goodsRows`，商品基础属性/SKU 属性值未进入快照；编辑→打印后，模板里的商品字段元素取不到值。
- 改动：引入 `extractProductAttrValues`（或统一入口 `mergeProductAttrValues`），对 `s.goodsRows` 提取后仅补未定义 key（与发货单/小票语义一致）。

### G2 商品值合并逻辑三处重复（复用模块补齐）

- 文件：`utils/productFormFields.js`
- 新增导出：

```js
/** 把商品字段快照值并入既有 values：只补未定义 key，不覆盖表单字段 */
export function mergeProductAttrValues(values, rows) {
  var target = values || {}
  var productValues = extractProductAttrValues(rows)
  Object.keys(productValues).forEach(function(key) {
    if (target[key] === undefined) target[key] = productValues[key]
  })
  return target
}
```

- 调用方改为该入口（语义不变）：
  - `config/formFields/deliveryForm.js` `extractDeliveryValues` 末尾 `productValues` 合并段。
  - `utils/retailSaleFormFields.js` `extractRetailSaleValues` 末尾 `productValues` 合并段。
  - `utils/addressFormFields.js` `extractAddressValues` 末尾（G1）。

### G3 明细行类目解析链三处重复（R2 复用统一）

- 文件：`utils/categorySpec.js`
- 新增导出：

```js
/** 明细行品类 id：统一解析链 categoryId → materialCategoryId → raw.categoryId → sku.categoryId */
export function resolveRowCategoryId(row) { ... }
```

- 替换调用点，行为不变：
  - `LabelPrintModal.vue` `ensureCategoryValues`（原 2519-2529 行内链）。
  - `LabelPrintModal.vue` `ensureSpecBlocks`（原 2552-2559 行内链）。
  - `utils/labelDataResolver.js` `normalizeRows` 的 `categoryId` 取值。

### G4 地址/会员快照缺 `type`（R5 快照身份）

- `src/views/system/AddressRecognize.vue` `printResult`：快照增加 `type: 'address'`。
- `src/views/system/modules/MemberPreview.vue` `buildSnapshot`：快照增加 `type: 'member'`。
- `LABEL_EDIT_SNAPSHOT_DEFAULTS` 不默认 `type`（骨架不携带业务身份），由业务页显式声明。
- 效果：`mergeEditSnapshotForPrint` 的跨业务残留保护对地址/会员同样生效。

### G5 商品标签编辑→打印丢业务值（R5 缺口）

- 现状：`LabelPrintModal.open(record, sku)`（商品标签预览）不写快照；编辑页 `handlePrint` 合并时无旧业务快照，`valuesFromAttrs` 来自瘦身后的编辑 record（只有 `categoryId/goodsRows`），商品字段为空。
- 修复（文件 `LabelPrintModal.vue`）：
  - 引入 `saveLabelEditSnapshot`、`valuesFromAttrs`。
  - `open(record, sku, preferredTpl)` 在 `this.allAttrs = this.buildAttrList()` 之后，仅当 `record.id != null || (record.goodsRows && record.goodsRows.length)` 时写入：

```js
saveLabelEditSnapshot({
  type: 'label',
  number: record.number || record.code || '',
  values: valuesFromAttrs(this.allAttrs),
  rows: Array.isArray(record.goodsRows) ? record.goodsRows : []
})
```

  - 不改变 `openByType` 路径（发货/小票/地址等由各自表单页写快照）。
- 效果：商品标签「预览 → 编辑 → 打印」后商品字段/明细仍在；`type: 'label'` 不匹配其它表单，避免串数据。

### G6 折行/实际尺寸

- 复核一致，无代码改动；在实现后由 harness 断言继续守护。

## 4. 验证

1. harness（`D:\erp\tools\label-repro`，node=`D:\erp\tools\node16-build\node.exe`）：
   - `run-attrs.js` 增加断言：`mergeProductAttrValues` 不覆盖已有 key、只补未定义 key；`extractAddressValues(goodsRows)` 含商品字段值；`resolveRowCategoryId` 四条链分支。
   - `run.js`、`run-merge.js` 保持全绿（布局零重叠、快照合并 4 用例）。
2. 构建（只构建不部署）：

```
cwd D:\erp\jshERP-web
$env:Path='D:\erp\tools\node16-build;'+$env:Path; npm run build:pc
```

3. 手动验收路径：
   - 地址识别 → 打印 → 预览选中「商品名称/品牌/条码」元素有值 → 编辑 → 打印 → 值仍在。
   - 会员打印 → 编辑 → 打印：会员字段不串入其他表单；跨类型残留被忽略。
   - 零售出库「发货」→ 预览 → 编辑 → 打印：dn_* 与商品字段仍在。
   - 商品标签 → 预览 → 编辑 → 打印：商品字段/明细仍在，其他模板不受影响。
   - 折扣/换行：长文本仍按 `min(元素宽, 标签宽-x)` 折行，标签宽超过屏幕不放大。

## 5. 风险与回滚

- 风险：`open()` 写快照可能覆盖上一次 `label` 快照；仅在同类型打印时读取，且表单页每次打印前都会重写自身 `type` 快照，跨类型由 `type` 守卫隔离。
- 风险：`resolveRowCategoryId` 迁移需逐字保持原优先级；harness 覆盖四条链。
- 回滚：改动均为纯前端局部函数替换，按文件回退即可；模板存储结构不变，无数据迁移。
