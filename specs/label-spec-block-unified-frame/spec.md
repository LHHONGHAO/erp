# 标签规格块：边框单一路径 + 与普通文字一致的样式/显示

## 1. 目标

规格块（属性名 + N 个规格值）与普通文字元素走 **同一个渲染组件、同一套几何与边框样式**：

1. 组件只保留 `LabelTextFrame`（编辑）/ `LabelPaper` 同款文本盒（打印），不再有「子元素各自渲染 + 外接覆盖框」的第二套边框。
2. 无模板数据时，全部规格块默认 **并列排列**：块内 `名称：值 值 …` 横向排布，多个规格块按标签宽度从左到右流动、放不下自动换行。
3. 边框被拖窄时按 **块内全部属性名宽度之和** 为最小宽度自动折行；属性名不压缩、不截断。
4. 属性编辑面板为每个属性名提供「折行」复选框：勾选后该属性名后面的属性值整体换到下一行。
5. 属性编辑面板提供 左齐 / 居中 / 右齐：块内文本在自身边框范围内按所选方式对齐。
6. 编辑器预览、打印预览、打印输出使用同一份行布局结果，视觉一致。

默认值：`wrap=false`、`textAlign=left`；字段缺失按默认处理。

## 2. 现状与问题（已核实）

| 位置 | 现状 | 问题 |
| --- | --- | --- |
| `LabelEditMobile.vue` L62-118 | `selectedAttrs` 逐 key 渲染 `LabelTextFrame`，规格 key `show-frame=false` | 规格文字仍按子元素各自渲染 |
| `LabelEditMobile.vue` L118-134 | 额外渲染 `specBlockFrames`（`with-inner=false`）覆盖框，L937-985 用子元素外接矩形并集 + `pad=2` | 第二套边框路径；与文字不同源，拖动/折行时错位、超纸张 |
| `templateEditDataModel.js` L111-159 `buildCategorySpecEditAttrs` | 规格名固定 32% 宽，值在右侧纵向堆叠（`y + valueIndex*lineHeightPx`） | 默认不是并列；块宽写死，不按属性名长度 |
| `labelElementDrag.js` L231-241 | 块缩放手柄对块内每个 key 的 `width` 加同一 delta | 与各自固定 `x` 冲突，边框与文字错位 |
| `LabelPrintModal.vue` L2738-2783 `itemStyle` | 按单 key 输出文字位置 | 打印侧与编辑器各算一套 |
| `labelTemplateV2.js` L106-149 / L272-313 | 只保存/还原 x/y/width/height/font 等 | 无 `textAlign`、`specWrap`，无法复用显示方式 |

## 3. 设计

### 3.1 共享布局模块（唯一算法）

新增 `jshERP-web/src/views/material/labelPrint/utils/specBlockLayout.js`（纯函数，无 Vue 依赖，编辑器与打印共同引用）：

```js
export const SPEC_BLOCK_GAP = 8      // 块与块之间、块内相邻 item 之间的间距（px）
export const SPEC_BLOCK_PAD_X = 2    // 文字距边框的左右内边距（px）

export function measureSpecTextWidth(text, fontSize, fontWeight, letterSpacing)
// canvas 测宽；无 document/canvas 时按 fontSize * 0.62 * 字符数 退化估算

export function buildSpecBlockItems({ nameText, nameWrap, values })
// → [{ key, kind:'name'|'value', text, wrap }]
// nameText 已带「：」；values = [{ key, text }]

export function resolveSpecBlockLines(options)
// options: { items, width, fontSize, fontWeight, letterSpacing, lineHeight, textAlign }
// → { lines: [{ items, text, width }], width, height, minWidth, lineHeightPx }
```

排布规则：

- 顺序流式：name、value… 依次进入当前行，item 为原子文本单元（不可拆分）。
- 行宽累计 `itemWidth + 间距`；加入下一项会超出 `width` 时另起一行。
- `item.wrap === true` 的名称项之后强制换行（该名称的属性值从下一行开始）。
- 名称文本、值文本都不裁剪，故 `minWidth = 所有名称项宽度之和 + 名称间间距`。
- `width` 取 `max(给定宽度或自然宽度, minWidth)`；自然宽度即全部 item 单行总宽 + 内边距。
- `height = lines.length * lineHeightPx`，`lineHeightPx = Math.round(fontSize * lineHeight)`。
- `line.text` 由 item 文本以 `' '` 连接；`textAlign` 原样回传，由渲染层使用。

### 3.2 编辑页（`LabelEditMobile.vue`）

1. 画布第一个 `v-for` 只渲染非规格元素：新增 computed `plainAttrKeys = selectedAttrs.filter(k => !isSpecItemKey(k))`，`v-for="key in plainAttrKeys"`。
2. 新增 computed `specBlockViews`，按 `rowNo + categoryId + blockIdentity/blockNo` 分组（沿用现 `specBlockFrames` 的分组键），输出：
   ```
   {
     id, anchorKey,          // 属性名 key；名称被关闭时取块内第一个可见 key
     keys, visibleKeys, active,
     items,                  // buildSpecBlockItems 结果
     layout,                 // resolveSpecBlockLines 结果
     frameStyle              // left/top/width/height/fontSize/fontWeight/letterSpacing/lineHeight/
                             // textAlign/padding:'0 2px'/boxSizing/whiteSpace:'nowrap'
   }
   ```
   - 块几何只读锚点 key 的 `attrStyles[anchorKey]`：`x/y`；`width>0` 用该值，否则用 `labelWidth - x`；`textAlign` 默认 `left`；名称 `specWrap` 默认 `false`。
   - 名称文本取属性值并补「：」，值文本取 `attr.value`；隐藏项（不在 `selectedAttrs`）不进入 items。
3. 模板中删除 `specBlockFrames` 覆盖框，改为一个 `LabelTextFrame`（`with-inner`、`show-frame`、`show-handle/show-delete` 与普通元素一致）：
   - `:drag-key="view.anchorKey"`、`:element-key="view.anchorKey"`、`:frame-style="view.frameStyle"`；
   - 内容层按 `view.layout.lines` 渲染 `div.lem-spec-line`（`height: lineHeightPx`、`white-space: pre`、`overflow: hidden`、`:style="{ textAlign }"`）。
4. 删除 `specBlockFrames` computed；`removeSpecBlock` 改为接收 view（`keys` 不变）。
5. 属性面板 `currentSpecBlock` 段（L152-190）在「属性名」输入框下方新增一行控件：
   - `a-checkbox`「折行」：`:checked="specBlockWrap"`，`@change="onSpecWrapChange"`；
   - `a-radio-group` 左齐 / 居中 / 右齐：`:value="specBlockAlign"`，`@change="onSpecAlignChange"`。
   - 两个 computed 读 `attrStyles[anchorKey].specWrap / .textAlign`，方法写入锚点 key 并 `pushSnapshot()`；写入后值同时作用于整块。
6. `getElementStyle` 不再需要规格分支（规格 key 已不进入普通渲染）；其余逻辑不变。
7. 新增 `specBlockMinWidth(anchorKey)`：用 `resolveSpecBlockLines({ width: Infinity, ... })` 取 `minWidth`，供拖动限宽。

### 3.3 拖动（`labelElementDrag.js`）

- `startPointerDrag`：`blockResizeKeys` 取消；resize 时若 `vm.isSpecAttrKey(key)`，最小宽度取 `vm.specBlockMinWidth(key)`。
- `onPointerMove` resize 分支：规格锚点只改 `attrStyles[key].width`（清 `height`），不再逐 key 加宽；move 分支保留整块位移。
- `measureContentSize` 保持普通元素逻辑。

### 3.4 默认布局（`templateEditDataModel.js` `buildCategorySpecEditAttrs`）

- 每个块先 `measureSpecTextWidth` 求名称/值自然宽，得到块自然宽（`items` 单行 + 内边距）。
- 流式放置：`x` 从左边界开始，放不下 `availableWidth` 换行（`y += lineHeightPx + 4`，`x` 复位）。
- 锚点（名称 key）style：`{ x, y, width: 块自然宽, height: lineHeightPx, fontSize, fontWeight:'normal', letterSpacing:0, lineHeight, textAlign:'left', specWrap:false }`。
- 值 key style：`{ x: 锚点 x, y: 锚点 y, width: 0, height: 0, fontSize, fontWeight:'normal', letterSpacing:0, lineHeight }`（几何以锚点为准）。
- 返回值结构 `{ attrs, styles, keys, height }` 不变。

### 3.5 持久化（`labelTemplateV2.js`）

- `editorStateToV2`：当 key 是规格名（`isSpecNameKey`）时，element 增加
  `textAlign: style.textAlign === 'center' || 'right' ? style.textAlign : 'left'`、
  `specWrap: !!style.specWrap`。
- `v2ToEditorState`：还原 `style.textAlign = element.textAlign || 'left'`、`style.specWrap = !!element.specWrap`。
- 其余 key 不写这两个字段。

### 3.6 打印（`LabelPrintModal.vue`）

1. 新增方法 `specBlockViewsFor(layout, values)`：分组规则与编辑器一致；用 `layout.styles[anchorKey]`、`values`、共享 `buildSpecBlockItems` / `resolveSpecBlockLines` 生成与编辑器 `specBlockViews` 同构的数据（含 `frameStyle`）。
2. 三个预览分支（L72-98 堆叠、L111-139 单页、L606-634 弹层）在 `<label-paper>` 之后追加：
   `<div v-for="view in ..." :key="view.id" class="lp-spec-block" :style="view.frameStyle"><div v-for="line in view.layout.lines" class="lp-spec-line" :style="{ textAlign: view.frameStyle.textAlign }">{{ line.text }}</div></div>`
   堆叠分支用 `specBlockViewsFor(pg.layout, pg.values)`，其余用 `specBlockViewsFor(labelLayout, previewValues)`。
3. `schemaForLayout` 过滤掉所有规格块元素（`isSpecItemKey(el.field || el.id)`），避免与覆盖层重复渲染；不影响其他元素。
4. 新增 `.lp-spec-block`（`position:absolute; box-sizing:border-box; padding:0 2px; white-space:nowrap; overflow:hidden`）与 `.lp-spec-line`（`overflow:hidden; white-space:pre`）样式，与编辑页行渲染一致。

## 4. 明确不做

- 不改后端接口与 `print_template` 表结构。
- 不新增旧格式兼容分支；旧模板仍按现状提示删除后重建。
- 不改普通文字元素的边框、拖动、对齐、商品明细表逻辑。
- 不部署；部署需用户明确确认。

## 5. 验证

1. 新建小票/标签模板（无模板数据）：规格块全部并列，块内 `名称：值 值` 同行；多项超宽自动换到下一行。
2. 拖窄规格块：以全部属性名宽度之和为下限；名称不被截断；超出部分按 item 折行；块高随行数变化。
3. 勾选「折行」：该名称后的值整体换行，边框高度增加；取消恢复。
4. 切换 左齐/居中/右齐：块内每行文本在边框内按选择对齐。
5. 保存 → 关闭 → 重新打开：折行、对齐、块宽、块位置完整还原。
6. 打印预览与打印输出：行布局、对齐、边框位置与编辑页一致。
7. `jshERP-web` 构建通过：`$env:Path='D:\erp\tools\node16-build;'+$env:Path; & 'D:\erp\tools\node16-build\npm.cmd' run build:pc`。
