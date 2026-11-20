# 任务清单

## [x] T1 共享规格块布局模块
- 新建 `jshERP-web/src/views/material/labelPrint/utils/specBlockLayout.js`
- 导出 `SPEC_BLOCK_GAP`、`SPEC_BLOCK_PAD_X`、`measureSpecTextWidth`、`buildSpecBlockItems`、`resolveSpecBlockLines`
- 行内 item 原子排布；`wrap` 名称后强制换行；`minWidth`=全部名称宽之和；`height`=行数×行高

## [x] T2 默认并列布局
- `utils/templateEditDataModel.js` `buildCategorySpecEditAttrs`：按自然宽流式左右排列、超宽换行
- 锚点 style 承载 `x/y/width/height/font` + `textAlign:'left'` + `specWrap:false`
- 值 key style 只保留字体字段，几何归锚点

## [x] T3 编辑页单一渲染路径
- `modules/LabelEditMobile.vue`：新增 `plainAttrKeys`、`specBlockViews`、`specBlockWrap`、`specBlockAlign`、`specBlockMinWidth`
- 删除 `specBlockFrames` 及覆盖框；规格块改用 `LabelTextFrame`（`with-inner`）按 `layout.lines` 渲染
- 属性面板增加「折行」复选框与 左齐/居中/右齐 选择，写入锚点 style 并 `pushSnapshot()`

## [x] T4 块缩放只改锚点
- `labelPrint/edit/labelElementDrag.js`：resize 只改锚点 `width`（清 `height`），最小宽取 `vm.specBlockMinWidth(key)`
- 移除 `blockResizeKeys` 逐 key 加宽逻辑；整体移动保持不变

## [x] T5 持久化 textAlign / specWrap
- `utils/labelTemplateV2.js`：`editorStateToV2` 仅规格名 element 写 `textAlign`、`specWrap`
- `v2ToEditorState` 还原到 `attrStyles[key]`，缺省 `left` / `false`

## [x] T6 打印与编辑同源
- `LabelPrintModal.vue`：新增 `specBlockViewsFor(layout, values)` 与 `specElementsFor`
- 规格块作为 `type: 'specblock'` 元素并入 schema，由 `LabelPaper` 模块统一输出到屏幕/打印（不再在预览组件里另写覆盖层样式）
- `schemaForLayout(layout, values)` 过滤原逐 key 规格元素并追加整块元素
- `LabelPaper.vue` 新增 `specElems` 渲染与 `.lpp-spec` / `.lpp-spec-line` 样式，与编辑页行渲染一致

## [x] T7 构建验证
- 执行 `build:pc`（cwd `d:\erp\jshERP-web`，Node16 工具链）→ 成功（仅既有资源体积告警）
- 手机端手工验证（并列 / 折行 / 对齐 / 拖窄 / 保存重开 / 打印）待用户在运行环境确认

## 待用户确认
- 是否现在执行部署；部署需明确确认
- 旧模板处理维持「提示删除后重建」，不做自动迁移
