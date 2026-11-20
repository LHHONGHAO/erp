# 任务清单

## [x] T1 共享地址表单字段定义
- 新建 `jshERP-web/src/views/material/utils/addressFormFields.js`
- 导出 `ADDRESS_FIELD_DEFS`、`ADDRESS_PANEL_FIELDS`、`ADDRESS_FORM_TITLE`、`buildAddressAttrs(record)`（仅取值器，无流程逻辑）
- 复用 `templateEditDataModel.js` 的 `normalizeEditorData`，业务值不进入快照

## [x] T2 地址表单页改用共享定义
- `jshERP-web/src/views/system/AddressRecognize.vue`：`fields` 由 `ADDRESS_PANEL_FIELDS` 生成
- 保持 contacts/telephone/account/remark/address 的顺序与 input/textarea 类型不变

## [x] T3 编辑页接入表单元素与品类并集
- `LabelEditMobile.vue`：
  - 删除本地 `buildAddressAttrs`，改用共享 `buildAddressAttrs`
  - L1167 品类树加载条件加入 `address`
  - L1228 `applyCategoryEditElements` 允许 `address`
  - `TYPE_CONFIG.address.defaultKeys` 声明全部表单字段

## [x] T4 预览页传入表单结构
- `LabelPrintModal.vue`：
  - 删除本地 `buildAddressAttrs`，改用共享实现
  - `handleCreateTemplate` 地址分支补 `editorData`（`normalizeEditorData` + 取值器）
  - `handleEdit` 地址分支使用取值器生成 `editorData`（title=地址打印）
  - 旧模板沿用模块既有 `outdatedTemplate` 机制，不新增自动清除代码

## [x] T5 校验
- 全局确认无残留的本地 `buildAddressAttrs`
- `jshERP-web` 执行既有构建/检查命令
- 手机端手工走通：创建/编辑 → 保存 → 预览 → 打印

## 待用户确认
- 旧地址模板的一次性清除方式（代码不再内置自动清除）
- 是否现在执行前端构建；部署另行确认
