# 标签表单字段配置统一 + 元素面板「表单数据」

## 1. 背景与目标

标签/小票/发货单/会员/地址五类模板的字段清单目前分散在三个位置各自实现：

- 编辑页 `LabelEditMobile.vue`：`buildAttrs` + 三个私有构造 `buildMemberAttrs` / `buildDeliveryAttrs` / `buildReceiptAttrs`，商品/地址复用外部函数。
- 预览页 `LabelPrintModal.vue`：`buildAttrList` 只有「address → buildAddressAttrs，其它 → buildLabelAttrs」两分支，导致发货单/小票/会员预览退化成商品字段（现存缺陷）。
- 元素来源与取值规则没有单一配置，新增表单或字段需要多处同步。

目标：

1. 每个表单一份字段配置文件（配置即字段白名单），五份：商品标签、发货单、小票、会员、地址。
2. 编辑页与预览页都从同一配置生成字段清单，消除两页不一致。
3. 元素面板最下方新增固定「表单数据」按钮，点击从底部弹出标准弹层，加载该表单的全域字段与当前已选状态；确定后勾选结果写回画布。
4. 字段 key 与现有实现完全一致，存量模板不受影响。

## 2. 范围

### 包含

- 新增 `src/views/material/labelPrint/config/formFields/` 配置目录与统一入口。
- 编辑页按配置构建 `allAttrs`，删除三个私有字段构造与 `TYPE_CONFIG.attrsBuilder` 映射。
- 元素面板底部固定「表单数据」按钮 + 底部弹层（勾选、搜索、分组全选）。
- 预览页 `buildAttrList` 改读同一配置。
- 删除因此不再使用的导入与代码。

### 不包含（本轮明确不做）

- 不改渲染算法、分页规则、规格块布局与 `labelTemplateV2.js` 版本号。
- 不改模板保存序列化结构（`editorData` / v2 schema 字段不变）。
- 不改 `applyProviderData` / `resolveTemplateData` 的数据源加载逻辑。
- 不做「表单结构变化后提示重建并合并字段」的交互（本轮只提供 `buildFormFieldSignature` 作为后续步骤的版本标记，不持久化、不弹提示）。

## 3. 现状（代码事实）

编辑页 `d:\erp\jshERP-web\src\views\material\modules\LabelEditMobile.vue`：

- 导入：`buildLabelAttrs` L544，`buildAddressAttrs` L555。
- `TYPE_CONFIG` L572-L622，其中 `attrsBuilder` 取值等于类型名。
- `initEditor` L1213：`this.buildAttrs(cfg.attrsBuilder)`，并把非自定义字段值清空。
- `applyProviderData` L1392：`this.buildAttrs((TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label).attrsBuilder)`。
- `buildAttrs(builder)` L1434-L1445：switch 分派；`appendPageAttr` L1447 追加 `page_no`。
- `buildMemberAttrs` L1508-L1534（13 项，读 `this.record`）。
- `buildDeliveryAttrs` L1644-L1676（29 项，静态示例值）。
- `buildReceiptAttrs` L1678-L1695（9 项，读 `this.record`）。
- 元素面板模板 L287-L300：`.lem-side` 内只有 `LabelElementPanel`（其根节点 `.lem-side-body` 为 `flex:1` 滚动区）。
- `attrGroups` L906-L945：按 `group` 分组，规格项聚合为 `isSpecBlock` + `blockKeys`，`page_no` 不展示。
- `addAttr` L1829、`removeAttr` L1874、`setAttrVisible` L1956、`removeSpecBlock` L1887、`pushSnapshot` 为既有写回方法。
- 相关样式：`.lem-side` L3252（flex 纵向）、`.lem-side-body` L3301（flex:1 滚动）、底部弹层样式范式 `.lem-save-sheet-wrap` L3609-L3657。

预览页 `d:\erp\jshERP-web\src\views\material\labelPrint\LabelPrintModal.vue`：

- 导入 `buildLabelAttrs` L808，`buildAddressAttrs` L816。
- `buildAttrList` L2940-L2948：address/其它二分，追加 `page_no`。
- `openByType` L1490 调用 `buildAttrList`；L1586、L2221 亦有重建。
- `handleEdit` L3004 用 `buildLabelAttrs(this.record, this.sku)` 作为 `productFields`。
- 业务值由 `applySnapshotValues` L2485 在模板应用后按 key 回填（发货单/会员快照 key 与配置 key 一致）。

## 4. 方案

### 4.1 配置文件结构

目录：`d:\erp\jshERP-web\src\views\material\labelPrint\config\formFields\`

统一形状：

```js
export default {
  type: 'label',          // 与模板类型一致
  title: '商品标签',       // 表单标题（弹层/后续签名使用）
  buildAttrs(context) {   // context = { record, sku }
    // 返回 [{ key, label, value, isCustom: false, group }]
  }
}
```

| 文件 | type | title | 字段来源 | 取值 |
| --- | --- | --- | --- | --- |
| `labelForm.js` | label | 商品标签 | 复用 `buildLabelAttrs(record, sku)`（字段来自 `materialColumns.js`，key 不变） | 同现有实现 |
| `deliveryForm.js` | delivery | 发货单 | 移入现有 29 个 `dn_*` 定义，group 保持「单据头/商品明细/金额合计/其它」 | `record[key]`，无值返回 ''（不再使用静态示例值；编辑页本就清空非自定义值，示例值无实际作用） |
| `receiptForm.js` | receipt | 小票 | 移入现有 9 个 `rc_*` 定义 | `record[key]` |
| `memberForm.js` | member | 会员标签 | 移入现有 13 项及 record 字段映射（`phone_num←phoneNum`、`supplier←supplier` 等） | 同现有实现，`enabled` false→'禁用' |
| `addressForm.js` | address | 地址打印 | 委托 `buildAddressAttrs(record)`（8 个地址字段 + 商品行属性） | 同现有实现 |

`deliveryForm.js` 字段顺序与 group 必须与 `buildDeliveryAttrs` 完全一致：

```
单据头: dn_title, dn_no, dn_date, dn_member, dn_receiver, dn_phone, dn_address, dn_depot,
        dn_paytype, dn_account, dn_operator, dn_salesman, dn_remark, dn_attach
商品明细: dn_table, dn_col_name, dn_col_spec, dn_col_qty, dn_col_price, dn_col_amount
金额合计: dn_qty_total, dn_amount_total, dn_discount, dn_discount_amount, dn_real_amount,
          dn_paid_total, dn_back_amount
其它: dn_status, dn_footer
```

`receiptForm.js`：

```
单据头: rc_title, rc_no, rc_date, rc_member
商品明细: rc_table
金额合计: rc_qty_total, rc_amount_total
其它: rc_remark, rc_footer
```

`memberForm.js`：

```
contacts/联系人, telephone/电话, phone_num/手机, address/联系地址, supplier/会员卡号,
email/电子邮箱, wechat_account/微信账户, douyin_account/抖音账户, alipay_account/支付宝账户,
other_account/其它账户, advance_in/预付款, description/备注, enabled/状态（group=会员信息）
```

### 4.2 统一入口 `config/formFields/index.js`

```js
getFormFieldConfig(type)                  // 未知类型回退 label
buildFormFieldAttrs(type, context)        // 返回字段数组，不含 page_no
buildFormFieldSignature(type, context)    // 字段结构签名：key + ':' + label，用 '|' 连接
```

- `buildFormFieldAttrs` 只做 `config.buildAttrs(context || {})` 与浅拷贝，不修改原配置。
- `page_no` 仍由调用方追加（编辑页 `appendPageAttr`、预览页 `buildAttrList`），保证行为不变。

### 4.3 编辑页改造（LabelEditMobile.vue）

1. 导入：删除 `buildLabelAttrs`（L544）与 `buildAddressAttrs`（L555），新增
   `import { buildFormFieldAttrs } from '@/views/material/labelPrint/config/formFields'`。
2. `TYPE_CONFIG`：删除 5 处 `attrsBuilder` 行，其余不变。
3. `buildAttrs()` 改为无参（或保留参数但忽略，避免遗漏调用）：

```js
buildAttrs() {
  var fields = buildFormFieldAttrs(this.templateType, { record: this.record, sku: this.sku })
  return this.appendPageAttr(fields)
}
```

4. 调用点更新：L1213 `this.buildAttrs(cfg.attrsBuilder)` → `this.buildAttrs()`；
   L1392 `this.buildAttrs((TYPE_CONFIG[this.templateType] || TYPE_CONFIG.label).attrsBuilder)` → `this.buildAttrs()`。
5. 删除 `buildMemberAttrs`、`buildDeliveryAttrs`、`buildReceiptAttrs` 三个方法。
6. `initEditor` 中清空非自定义字段值的逻辑保持不变（配置返回值在编辑页一律清空）。

### 4.4 元素面板「表单数据」

#### 4.4.1 按钮

模板 L287-L300 的 `.lem-side` 在 `LabelElementPanel` 之后追加：

```html
<div class="lem-side-foot">
  <a-button type="primary" size="small" block icon="profile" @click="openFormDataSheet">表单数据</a-button>
</div>
```

样式（与 `.lem-side` flex 布局配合，固定不随列表滚动）：

```css
.lem-side-foot {
  flex: none;
  padding: 8px;
  background: #fff;
  border-top: 1px solid #f0f0f0;
}
```

#### 4.4.2 底部标准弹层

在保存弹层 `a-modal.lem-save-sheet` 之后新增：

```html
<a-modal
  title="表单数据"
  :visible="formDataVisible"
  :width="'100%'"
  :maskClosable="true"
  :zIndex="3020"
  wrapClassName="lem-form-sheet-wrap"
  @cancel="closeFormDataSheet"
>
  <div class="lem-form-search">
    <a-input v-model="formDataKeyword" placeholder="搜索字段" allowClear />
  </div>
  <div v-for="group in formDataGroups" :key="group.name" class="lem-form-group">
    <div class="lem-form-group-head">
      <a-checkbox
        :checked="group.checked"
        :indeterminate="group.indeterminate"
        @change="toggleFormDataGroup(group)"
      />
      <span class="lem-form-group-name">{{ group.name }}</span>
      <span class="lem-form-group-count">{{ group.selectedCount }}/{{ group.count }}</span>
    </div>
    <div
      v-for="item in group.items"
      :key="item.key"
      class="lem-form-row"
      @click="toggleFormDataItem(item)"
    >
      <a-checkbox :checked="item.checked" @change="toggleFormDataItem(item)" />
      <span class="lem-form-row-label">{{ item.label }}</span>
      <span class="lem-form-row-value">{{ item.valueText }}</span>
    </div>
  </div>
  <template slot="footer">
    <a-button type="primary" block @click="confirmFormDataSheet">确定</a-button>
  </template>
</a-modal>
```

样式复用 `.lem-save-sheet-wrap` 的底部弹层范式：

```css
.lem-form-sheet-wrap { display: flex; align-items: flex-end; }
.lem-form-sheet-wrap .ant-modal {
  position: relative; top: auto; bottom: 0;
  width: 100% !important; max-width: 100%; margin: 0; padding-bottom: 0;
}
.lem-form-sheet-wrap .ant-modal-content {
  border-radius: 16px 16px 0 0;
  padding-bottom: env(safe-area-inset-bottom);
  overflow: hidden;
}
.lem-form-sheet-wrap .ant-modal-header { border-radius: 16px 16px 0 0; border-bottom: none; padding: 10px 16px 2px; }
.lem-form-sheet-wrap .ant-modal-title { text-align: center; font-size: 15px; }
.lem-form-sheet-wrap .ant-modal-body { padding: 4px 16px; max-height: 62vh; overflow-y: auto; }
.lem-form-sheet-wrap .ant-modal-footer { padding: 8px 16px 10px; border-top: 1px solid #f0f0f0; }
.lem-form-sheet-wrap .ant-modal-footer .ant-btn { width: 100%; height: 40px; border-radius: 20px; }
```

弹层高度 = 标题栏 + 62vh 内容 + 底部按钮，整体不超过约 80vh；遮罩点击关闭。

#### 4.4.3 字段清单（全域字段）

新增 computed `formDataFieldList`：

1. 以 `this.allAttrs` 为基础（保留数据表字段、外键补全字段、显示名与当前值）。
2. 追加 `buildFormFieldAttrs(this.templateType, { record: this.record, sku: this.sku })` 中 `allAttrs` 不存在的 key。
3. 过滤：`page_no`、`custom_` 前缀的自定义标签（不是表单字段）；规格项 `parseSpecItemKey(key)` 不按普通字段展开。
4. 规格项按块聚合为一个条目：`{ key: 'specblock-' + blockId, isSpecBlock: true, blockKeys, label: 规格名, group: '品类规格' }`。
5. 普通条目字段：`{ key, label, valueText, group, checked }`，`valueText` 取 `attr.value`（为空显示 ''）。

新增 computed `formDataGroups`：

- 按 `group`（缺省「可用字段」）分组，保持出现顺序。
- `formDataKeyword` 非空时按 `label` / `key` 忽略大小写过滤；过滤后的空分组不展示。
- 每组 `checked` = 全部勾选，`indeterminate` = 部分勾选，`selectedCount` / `count` 供组头展示。

#### 4.4.4 勾选状态与写回

- 新增 data：`formDataVisible: false`、`formDataKeyword: ''`、`formDataDraft: {}`。
- `openFormDataSheet()`：`formDataDraft = {}`；遍历 `formDataFieldList` 普通项按 `selectedAttrs.indexOf(key) > -1` 初始化；规格块按 `blockKeys.every(...)` 初始化；`formDataKeyword = ''`；`formDataVisible = true`。
- `toggleFormDataItem(item)`：普通项翻转 `formDataDraft[item.key]`；规格块翻转整块全部 `blockKeys`。
- `toggleFormDataGroup(group)`：组内全部条目设置为 `!group.checked`。
- `confirmFormDataSheet()` 写回规则：

| 条目 | 由未选→选中 | 由选中→未选 |
| --- | --- | --- |
| 普通字段 | `addAttr(key)`（追加默认位置与显示名） | `removeAttr` 同等效果：从 `selectedAttrs` 移除，`$delete attrStyles[key]`、`$delete attrLabels[key]` |
| 品类规格块 | 每个 key 执行 `setAttrVisible(key, true)`（保留原样式/位置） | 每个 key 执行 `setAttrVisible(key, false)`（记入 `hiddenItemKeys`，保证小票重建后仍隐藏） |

- 全部条目处理完毕后只调用一次 `pushSnapshot()`，随后关闭弹层。
- `closeFormDataSheet()`：不写回，仅关闭并清空草稿。

### 4.5 预览页改造（LabelPrintModal.vue）

1. 导入：删除 `buildLabelAttrs`（L808）；新增
   `import { buildFormFieldAttrs } from './config/formFields'`；保留 `buildAddressAttrs`（`handleCreateTemplate` L2981、`handleEdit` L3001 仍在使用）。
2. `buildAttrList`（L2940）改为：

```js
buildAttrList() {
  var list = buildFormFieldAttrs(this.type, { record: this.record, sku: this.sku })
  if (!list.some(function(a) { return a.key === 'page_no' })) {
    list.push({ key: 'page_no', label: '分页标签', value: '', isCustom: true, isPage: true, group: '模板元素' })
  }
  return list
}
```

3. `handleEdit` 的 `productFields`（L3004）改为 `buildFormFieldAttrs('label', { record: this.record, sku: this.sku })`。
4. 不再出现「address / 其它」二分；发货单、小票、会员预览获得各自字段字典，`applySnapshotValues` 仍按 key 回填真实业务值。

## 5. 边界与兼容

- 所有字段 key 与现状逐字一致，存量模板 `selectedAttrs` / `attrStyles` / `attrLabels` 无需迁移。
- 发货单/小票/会员预览从「商品字段 + 空值补字段」变为「本表单字段 + 快照真实值」，属缺陷修复。
- 编辑页非自定义字段值继续清空，配置返回的示例值不会泄漏到画布。
- 规格项与 `page_no`、自定义标签不进入「表单数据」普通行，避免破坏规格块渲染与模板元素语义。
- 弹层内勾选为草稿，取消/点遮罩不改画布；确定后一次性写回并产生一条撤销记录。

## 6. 验证

1. 构建（cwd `d:\erp\jshERP-web`）：

```
$env:Path='D:\erp\tools\node16-build;'+$env:Path; & 'D:\erp\tools\node16-build\npm.cmd' run build:pc
```

要求：无编译错误、无新增 ESLint 阻断（与改动前基线一致）。

2. 代码核对：
   - 全局搜索 `buildDeliveryAttrs|buildReceiptAttrs|buildMemberAttrs|attrsBuilder` 结果为 0。
   - `buildLabelAttrs` 在 `LabelEditMobile.vue`、`LabelPrintModal.vue` 无残留引用。
3. 功能自测（开发服务）：
   - 商品标签编辑页：字段分组与改动前一致，旧模板打开显示正常。
   - 发货模板编辑页：元素面板最下方固定「表单数据」按钮；弹层列出 29 个 `dn_*` 字段及勾选状态；取消勾选后确定，画布元素移除；重新勾选后确定，元素恢复。
   - 发货单打印预览：不再出现商品字段，显示发货单字段与真实单据值。
   - 小票/会员/地址预览：字段与各自编辑页一致。
   - 规格块：在弹层中整块勾选/取消，画布规格块整体显示/隐藏，样式不丢失。

## 7. 风险

- 预览字段集合变化可能让个别依赖「商品字段出现在发货/会员模板」的历史模板补出空元素；`restoreMissingAttrs` 仍会为模板里已选但字典没有的 key 补结构，不丢元素。
- `allAttrs` 在数据表切换后可能含 provider 字段，弹层以 `allAttrs` 为主、配置补差，保证手动切换数据表后字段仍可见。
- 底部弹层样式与 `.lem-save-sheet-wrap` 同源，需确认两个弹层同时存在时 zIndex（3020）与遮罩互不干扰。
