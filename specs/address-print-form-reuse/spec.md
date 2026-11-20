# 地址打印：复用标准编辑模块（表单元素 → 编辑 → 保存 → 预览 → 打印）

## 1. 目标

地址打印不再维护独立/旧的模板结构。入口固定为：

```
地址识别页（表单） → 打印预览（LabelPrintModal, type=address）
  → 编辑（标准 LabelEditModalPage / LabelEditMobile）
  → 保存 → 回到预览 → 打印
```

编辑页的字段元素不再使用硬编码的地址字段清单，而是由「表单标题 + 表单字段 + 品类规格并集」组成。
页面侧只提供取值器；旧模板由用户一次性清除 `address_layout`，代码不内置自动判定。

## 2. 现状与偏差（已核实）

| 位置 | 现状 | 问题 |
| --- | --- | --- |
| `jshERP-web/src/views/material/modules/LabelEditMobile.vue` L632-641 | `TYPE_CONFIG.address` 已存在：platformConfig / `address_layout` / attrsBuilder `address` | 编辑器具备地址类型，但未接入表单结构 |
| 同文件 L1167 | 品类树加载条件为 `receipt` 或 `label` | 地址编辑拿不到品类规格并集 |
| 同文件 L1223-1228 | `applyCategoryEditElements` 仅 `label`（`receipt` 单独分支） | 地址不会补充规格块 |
| 同文件 L1469-1499 | `buildAddressAttrs()` 硬编码 8 个字段 | 与预览端重复，且与表单定义可能漂移 |
| `LabelPrintModal.vue` L2817-2854 | 另一份 `buildAddressAttrs()` | 双份清单，维护分叉 |
| `LabelPrintModal.vue` L2870-2882 | `handleCreateTemplate` 打开地址编辑页时未传 `editorData` | 编辑页只能走 `buildAttrs('address')` 的硬编码分支 |
| `LabelPrintModal.vue` L2885-2920 | `handleEdit` 用 `buildTemplateEditContext` 生成 `editorData`，标题取自 `opts.title`（未传则为空） | 地址编辑缺稳定的表单标题/字段签名 |
| `LabelPrintModal.vue` L2340-2391 | 读取 `address_layout`，仅非 V2 才标记 `outdatedTemplate` | V2 但由旧地址字段清单保存的模板仍会被加载 |
| `jshERP-web/src/views/system/AddressRecognize.vue` L112-118 | 面板字段单独定义 5 项（platform 由面板单独渲染） | 与打印属性清单未共用同一数据源 |

已确认的既有能力：

- 编辑上下文通道：`LabelEditModalPage.open` 写 `sessionStorage['jsh.labelEdit.context']`，`LabelEditMobile.initEditor`（L1122-1176）读取 `record/sku/editorData/template/businessType`；`editorData.fields` 存在时按字段结构建 `allAttrs`，业务值一律置空。
- 品类规格并集：`categorySpecBlocksForRows()`（L2974-3015）遍历整棵品类树，取所有配置了 `specConfig` 的品类并集，与明细行无关；数据库为空时回退模板已保存的 `specBlocks`。
- 保存：`TYPE_CONFIG.address.storage = platformConfig`，`persistUnifiedTemplate` → `postSnapshot('address_layout', v2)`。
- 预览回读：`LabelPrintModal.loadRouteTemplate` 读 `address_layout`，`normalizeTemplate` 支持 V2。

## 3. 方案

### 3.1 新增取值器（唯一数据源）

新增 `jshERP-web/src/views/material/utils/addressFormFields.js`，只做字段结构与取值，不含流程逻辑：

- `ADDRESS_FIELD_DEFS`：地址打印表单的规范字段清单，顺序固定：
  `contacts(联系人)`、`telephone(电话)`、`account(账号)`、`platform(平台)`、`remark(备注)`、`address(地址)`、`goods(商品信息)`、`goods_table(商品明细表)`。
- `ADDRESS_PANEL_FIELDS`：从 `ADDRESS_FIELD_DEFS` 派生面板用的 5 项（contacts/telephone/account/remark/address），
  类型保持现状：telephone、address 为 `textarea`，其余 `input`。
- `buildAddressAttrs(record)`：按 `ADDRESS_FIELD_DEFS` 生成属性数组；`goods_table` 为结构元素（value 为空、group 地址信息），其余 value 取 `record[key]`。
- `ADDRESS_FORM_TITLE`：表单标题常量（`地址打印`）。

### 3.2 编辑页（LabelEditMobile.vue）

1. 删除本地 `buildAddressAttrs`，`buildAttrs('address')` 改为调用共享 `buildAddressAttrs(this.record)`。
2. `initEditor` 中品类树加载条件（L1167）加入 `address`：
   `(templateType === 'receipt' || templateType === 'label' || templateType === 'address')`。
3. `applyCategoryEditElements`（L1223-1228）允许地址走 `label` 同分支：
   `if (this.templateType !== 'label' && this.templateType !== 'address') return`。
4. `TYPE_CONFIG.address.defaultKeys` 改为全部 8 个表单字段，保证新建地址模板时画布直接载入表单元素（分页标签仍不默认勾选）。

### 3.3 预览页（LabelPrintModal.vue）

1. 删除本地 `buildAddressAttrs`，改为共享实现。
2. `handleCreateTemplate`（地址分支）：
   - `businessType/provider` 保持空串；
   - 新增 `editorData: normalizeEditorData({ title: '地址打印', fields: buildAddressAttrs(this.record), specBlocks: [] })`；
   - `record` 仍传当前表单值（供预览/回显），编辑页会自行剥离业务值。
3. `handleEdit`（地址分支）：当 `type === 'address'` 时，`editorData` 由取值器与 `normalizeEditorData` 生成
   （标题 `地址打印`，fields = 共享字段，specBlocks 取 `this.template.specBlocks`），不依赖 `allAttrs` 的临时拼装。
4. `loadRouteTemplate` 沿用模块既有的旧模板机制（非 V2 标记 `outdatedTemplate`），不新增自动清除代码。

### 3.4 地址表单页（AddressRecognize.vue）

`fields` 改为由 `ADDRESS_PANEL_FIELDS` 生成，保证面板字段与打印属性同源；`form`、`platformOptions`、`printResult` 逻辑不变。

## 4. 明确不做

- 不改后端接口与 `print_template` 表结构。
- 不清理 `print_template` 中历史 `objectType = address` 的标签模板（地址打印入口已不读取它们；
  如需物理清理另行确认）。
- 不调整保存弹层的命名规则、打印设备流程。
- 不部署；部署需用户明确确认。

## 5. 验证

1. 静态：`LabelEditMobile.vue`、`LabelPrintModal.vue` 中不再存在本地 `buildAddressAttrs`；共享模块被两处引用。
2. 构建：在 `jshERP-web` 执行项目既有构建命令（`npm run build` 或 `npm run lint`，以 package.json 为准）通过。
3. 手机端流程：
   - 地址识别页填写联系人/电话/账号/平台/备注/地址 + 添加商品 → 打印；
   - 无模板时点「创建地址打印模板」→ 编辑页展示表单标题与 8 个字段（含商品信息、商品明细表），并补充品类规格并集块；
   - 保存 → 自动回到预览，模板按刚保存的布局渲染；再次进入打印可正常打印；
   - 旧模板：由用户一次性清除 `address_layout` 后重建，代码不做自动判定。
