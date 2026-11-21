# Tasks：标签打印全链路规则审计补齐

> 对应规格：[spec.md](./spec.md)
> 规则：改完先构建（`build:pc`），不部署；用户明确回复「部署」后才部署。

## T1 商品值合并统一入口（G2）

- [x] `jshERP-web/src/views/material/labelPrint/utils/productFormFields.js`
  - 新增 `mergeProductAttrValues(values, rows)`：调用 `extractProductAttrValues`，只补 `undefined` key。
- [x] `config/formFields/deliveryForm.js` `extractDeliveryValues` 末尾改用 `mergeProductAttrValues(values, items)`。
- [x] `utils/retailSaleFormFields.js` `extractRetailSaleValues` 末尾改用 `mergeProductAttrValues(values, s.items)`。
- 验证：`run-attrs.js` 断言不覆盖已有 key、只补未定义 key。

## T2 地址打印快照并入商品属性（G1）

- [x] `utils/addressFormFields.js` `extractAddressValues` 末尾 `mergeProductAttrValues(values, values.goodsRows)`。
- 验证：harness 断言 `goodsRows` 含 `mBarCode/brand/name` 时 `values` 同步含真实值。

## T3 明细行类目解析链统一（G3）

- [x] `utils/categorySpec.js` 新增 `resolveRowCategoryId(row)`（`categoryId → materialCategoryId → raw.categoryId → sku.categoryId`）。
- [x] `LabelPrintModal.vue` `ensureCategoryValues`、`ensureSpecBlocks` 改用该函数。
- [x] `utils/labelDataResolver.js` `normalizeRows` 的 `categoryId` 改用该函数。
- 验证：harness 四条链分支断言；预览/编辑仍能按类目回填「类别」。

## T4 地址/会员快照补 type（G4）

- [x] `src/views/system/AddressRecognize.vue` `printResult` 快照增加 `type: 'address'`。
- [x] `src/views/system/modules/MemberPreview.vue` `buildSnapshot` 快照增加 `type: 'member'`。
- [x] `LabelEditMobile.vue` 的 snapshot 合并逻辑保持不变（复用 `mergeEditSnapshotForPrint`）。
- 验证：跨类型旧快照不合并；同类型业务值/明细保留。

## T5 商品标签编辑→打印保留业务数据（G5）

- [x] `LabelPrintModal.vue` 引入 `saveLabelEditSnapshot`、`valuesFromAttrs`。
- [x] `open(record, sku, preferredTpl)` 在 `allAttrs` 构建后按条件写 `{ type:'label', number, values, rows }` 快照。
- 验证：标签预览→编辑→打印后商品字段/明细仍在；其他类型预览行为不变。

## T6 回归与构建

- [x] 运行 `run.js`、`run-merge.js`、`run-attrs.js`（node16），全部通过。
- [x] 构建：`cwd D:\erp\jshERP-web`；`$env:Path='D:\erp\tools\node16-build;'+$env:Path; npm run build:pc`（app.e2a1ae67.js，exit 0）。
- [ ] 报告产物 hash 与规则清单；**不部署**，等待用户回复「部署」。

## 验收路径

1. 地址识别：打印 → 预览（商品字段有值）→ 编辑 → 打印（值不丢）。
2. 会员打印 → 编辑 → 打印：字段正确，不串单。
3. 零售出库「发货」→ 预览 → 编辑 → 打印：dn_* 与商品字段完整。
4. 商品标签 → 预览 → 编辑 → 打印：商品字段/明细完整。
5. 长文本折行、标签实际尺寸（不放大）不回归。
