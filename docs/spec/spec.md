# 商品AI添加 · 识别结果页 5 项修正（规格说明）

> 状态：待确认（确认后进入实现）
> 涉及端：移动端 H5（jshERP-web）前端；不涉及后端结构变更
> 确认稿：[ai-result-exists-preview.html](file:///D:/erp/docs/ui/ai-result-exists-preview.html)

## 1. 背景与目标

商品 AI 添加流程为：`MaterialAiAdd` 选择图片与类别 → 点击「识别」→ `MaterialModal` 以 inline + aiMode 方式弹出「识别结果」弹层（承载识别与保存）。

本次修正 5 个问题：

1. 进入识别结果页后，「单位」未按类别默认单位回填。
2. 点「商品保存」时若商品名称已存在，只提示“商品已存在”，未展示已有商品信息，也无法选择处理方式。
3. 缺少「添加规格」：将已有商品的基础属性与规格与识别结果合并（缺失补齐、同名用识别值替换）。
4. 缺少「更新规格」：仅关闭提示弹层，不做任何写入或跳转。
5. 类别规格开启「添加规格图片」时，识别结果页的规格值后没有图片添加框；且识别图片被自动当成商品图片。

## 2. 现状与根因

### 2.1 单位未回填

- 已有代码：`applyPresetCategory` / `tryApplyPresetCategory` / `handleCategoryChange` → `applyCategoryUnit`，以及识别回调后的 `applyCategoryUnitToAggregate`（`MaterialModal.vue` 约 2059–2206、2360）。
- 问题点：
  - `MaterialAiAdd` 只把 `categoryId` 传给表单，没有把所选类别（或父级类目）的单位传入；当 `categoryTree` 节点 `unit` 为空或类别树查询失败时，`applyCategoryUnitToAggregate` 取不到单位。
  - 单位只从“当前选中节点”读取，未向父级回溯；部分单位维护在上级类目上。
  - 聚合结果渲染后若 `commonProps.unit` 为空，缺少基于“页面已选类别的单位”兜底。

### 2.2 名称重复

- `doSaveMaterial` 调用 `checkMaterial`（`/material/checkIsExist`），返回仅 `{status:true}`，无已有商品明细；当前只弹 `$message.warning`。
- 已有可用接口（无需改后端）：
  - `/material/findBySelect?q=名称`：返回包含 `m.*`（含 `attribute`、`unit`、`brand`、`fabric` 等）与 SKU `specValues` 的行数据。
  - `/material/findById?id=`：返回 `m.*` 完整商品行（含 `attribute` JSON）。
- `attribute` 为 `{specModules:[{name, enableImage, childNames, values:[{text, subValues, image}]}]}`，可用于展示/合并规格。

### 2.3 规格图片

- 普通商品表单已按 `module.enableImage` 显示规格值图片框（`MaterialModal.vue` 约 209–242）。
- 识别结果页（`specImageGroups` + 模板约 537–574）没有图片框。
- `applySpecAggregate` 中：
  - `if (dim.imagePath) this.$set(target,'image',dim.imagePath)`（约 2861）把识别图写入规格值图片；
  - `if (skuRow.imagePath) row.image = skuRow.imagePath`（约 2890）把识别图写入 SKU/商品图片。
  两处均需移除。

## 3. 需求细则（验收口径）

### 需求 1：单位自动回填

- 在 AI 添加页选择类别并点「识别」后，识别结果页的「单位」输入框自动显示该类别默认单位。
- 取值优先级：
  1. 识别结果本身识别到单位 → 保留识别值；
  2. 当前类别节点 `unit`；
  3. 当前类别的最近上级类目 `unit`；
  4. `MaterialAiAdd` 选中类别时解析出的单位（作为最终兜底）。
- 单位可在结果页继续手工修改，修改后不被二次覆盖。

### 需求 2：商品已存在提示弹层

- 点「商品保存」时若商品名称已存在，弹出「商品已存在」弹层（在识别结果页之上）。
- 弹层内容：
  - 标题：「商品已存在」，副标题说明“识别到的商品名称与已有商品重复，请选择后续处理方式。”
  - 已有商品卡片：名称 + 「已有商品」标签；
  - 属性列表：单位、品牌、面料、安全类别、执行标准、等级、检验、填充物、制造商、扩展字段、重量等（有值才展示）；
  - 规格列表：规格名 → 规格值（多个用 ` / ` 连接；组合规格按子规格拼接）。
  - 底部两个按钮：「更新规格」（次要）、「添加规格」（主要）。
- 仅当 `aiMode` 时使用新弹层；非 AI 的商品编辑表单保持原提示，避免影响桌面端行为。
- 若按名称未查到唯一已有商品，退回原“抱歉，该商品已存在！”提示。

### 需求 3：添加规格（合并）

- 点击「添加规格」后：
  - 基础属性：以识别结果为准；识别结果为空/缺失的字段，用已有商品同名字段补齐；同名字段识别值优先（即“补充替换”）。
  - 规格属性：已有商品的规格值合并进识别结果；缺失的规格维度补齐，同名规格值不重复追加；同名规格值以识别值为准。
  - 当前商品按“更新已有商品”处理：`model.id = 已有商品 id`，保存时走 `/material/update`，避免再次触发名称重复。
  - 关闭弹层，保留识别结果页，底部提示“已合并已有商品属性，请确认后保存”。
- 合并后仍可继续编辑，再点「商品保存」完成写入。

### 需求 4：更新规格（仅关闭）

- 点击「更新规格」：仅关闭「商品已存在」弹层，停留在识别结果页。
- 不写入任何数据、不修改 `model.id`、不跳转、不清空识别结果；恢复底部按钮可用状态。

### 需求 5：规格值图片框 & 取消识别图作商品图

- 识别结果页中，若某规格在类别配置里开启了「添加规格图片」：
  - 每个规格值名称后显示图片添加框（26×26，虚线蓝框 + 加号）；
  - 已添加图片时显示缩略图，点击可重新选择/替换；
  - 组合规格（有 childNames）时，图片框紧跟第一行子规格输入框。
- 图片添加走现有识别弹层的“仅取图”能力（`OcrRecognizeModal` `extractOnly`），选择/拍摄后写入该规格值的 `image`。
- 保存时：规格值图片随规格值写入 SKU（延续 `updateRowImagesBySpec` 行为），但**识别原图不再自动写入 SKU/商品图片**：
  - 删除 `dim.imagePath → 规格值 image` 的自动赋值；
  - 删除 `skuRow.imagePath → 行 image` 的自动赋值。

## 4. 技术方案（精确改动点）

### 4.1 文件与函数

**A. `jshERP-web/src/views/material/MaterialAiAdd.vue`**

- 新增方法 `resolveCategoryUnit(categoryId)`：在 `categoryTree` 中递归查找节点，返回自身或最近上级的 `unit`。
- `openResult()`：调用 `form.applyPresetCategory(this.categoryId, this.resolveCategoryUnit(this.categoryId))`。
- 不改变现有图片识别与保存成功清空逻辑。

**B. `jshERP-web/src/views/material/modules/MaterialModal.vue`**

1) 单位（需求 1）
- `data` 新增 `presetCategoryUnit: ''`。
- `applyPresetCategory(categoryId, unit)`：保存 `categoryId` 与 `unit`。
- `tryApplyPresetCategory()`：`handleCategoryChange` 后调用 `applyPresetUnitFallback()`。
- 新增 `findCategoryUnit(id)`：递归返回节点单位；节点无单位时回溯父级（在 `categoryTree` 上做路径查找）。
- 新增 `applyPresetUnitFallback()`：`unit` 为空时依次用 `findCategoryUnit(form.categoryId)`、`presetCategoryUnit` 写入表单。
- `applyCategoryUnitToAggregate()`：单位为空时依次取 `findCategoryUnit`、`presetCategoryUnit`，写入 `specAggregateResult.commonProps.unit`；识别值非空不覆盖。
- `openSpecRecognize()` 识别回调中保留 `prepareAggregateProps()` 后调用 `applyCategoryUnitToAggregate()`（现有顺序不变）。

2) 已有商品弹层（需求 2）
- `data` 新增：
  - `existMaterialVisible: false`
  - `existMaterialLoading: false`
  - `existMaterial: null`（`{id,name,attribute,unit,brand,...}`）
- `computed` 新增：
  - `existMaterialPropRows`：按 `aggPropLabel(field)` 输出非空基础属性；
  - `existMaterialSpecRows`：解析 `existMaterial.attribute`，输出 `{name, valuesText}`。
- `doSaveMaterial()`：AI 模式下先用 `/material/checkIsNameExist`（`id/name`）判断名称是否存在；存在则打开弹层，否则继续原 `doSaveMaterialByCheck()` 流程。非 AI 模式保持原提示。
- 新增 `openExistMaterialDialog()`：
  1. `getAction('/material/checkIsNameExist')` 命中后调用 `getAction('/material/list', { search: JSON.stringify({materialParam: name}), pageNo: 1, pageSize: 20 })`；
  2. 在 `res.data.rows` 中找 `name` 完全一致（trim、忽略大小写）的第一条（行内为 `m.*`，含 `attribute`）；
  3. 赋值 `existMaterial`，`existMaterialVisible = true`；`aggSaving` 保持 true。
  4. 查询失败/找不到 → 关闭 loading，退回原 `$message.warning('抱歉，该商品已存在！')`。
- 说明：`/material/findBySelect` 返回的 `id` 为 SKU(me) id，不是商品(spu) id，故改用 `/material/list`（商品级，行数据含 `attribute`）。
- 新增 `closeExistMaterialDialog()`（更新规格）：`existMaterialVisible=false`、`existMaterial=null`、`aggSaving=false`、`showAggSaveHint('', false)`；不写数据、不改 id。
- 新增 `mergeExistMaterial()`（添加规格）：
  - `const exist = this.existMaterial`；
  - 基础属性合并进 `specAggregateResult.commonProps`（识别非空优先，空则用已有值）；
  - 规格合并：解析 `exist.attribute.specModules`，按规格名找到 `result.dimensions`：
    - 维度不存在 → 追加维度（携带 `childNames`、`subValues`、`enableImage`）；
    - 维度存在但值为空 → 填入已有规格值；
    - 维度已有值 → 追加已有值但不重复（同规格值去重），识别值保持优先；
  - `this.model.id = exist.id`；
  - `existMaterialVisible=false`、`aggSaving=false`、`showAggSaveHint('已合并已有商品属性，请确认后保存', false)`；
  - 保留识别结果页，允许继续编辑并「商品保存」。
- 模板：在价格弹层之后新增「商品已存在」`a-modal`（`zIndex=1170`，手机端 `mm-sheet-modal agg-sheet-modal`），含已有商品卡片、属性/规格列表、底部「更新规格」「添加规格」；样式按确认稿。
- 注意：重复规格维度渲染 key 需唯一，模板改 `:key="group.index + '-' + dIndex + '-' + dim.name"`。

3) 规格图片（需求 5）
- `data` 新增 `pendingAggImageDim: null`。
- `prepareAggregateSpecGroups()`：为每个 `dim` 设置 `enableImage = !!对应 specModules 模块.enableImage`、`image = ''`（用户添加的图片）。
- 模板 `agg-values`：
  - 单值规格：值输入框后追加 `.agg-spec-img-upload`（`v-if="dim.enableImage"`），有图显示 `<img>`，无图显示 `+`；点击 `openAggSpecImage(dim)`。
  - 组合规格：第一个子规格输入框后追加同一图片框。
  - 规格名右侧的识别图小图标移除（识别图不再作为商品图）。
- 新增 `openAggSpecImage(dim)`：`pendingAggImageDim = dim`；`specImageOcrFile=null`；`specImageOcrVisible=true`。
- `handleSpecImageOcrClose(payload)`：若 `pendingAggImageDim` 存在 → 取 `payload.imagePath` 写入 `dim.image`，清空 pending 后直接返回（不影响原有规格表单图片逻辑）。
- `applySpecAggregate()`：
  - 删除 `if (dim.imagePath) this.$set(target,'image',dim.imagePath)`；
  - 改为 `if (module.enableImage && dim.image) this.$set(target,'image',dim.image)`；
  - 删除 `if (skuRow.imagePath) row.image = skuRow.imagePath`；
  - 条码与价格回填保持不变。
- 样式：新增 `.agg-spec-img-upload`（26×26、圆角 5px、虚线 #9dc4f5、背景 #f2f8ff、蓝色加号；有图时 `img` 铺满）。

## 5. 边界与异常

- 类别未配置单位：不填，保持空白；必填校验仍会拦截。
- 识别到单位：优先保留识别值，不覆盖。
- 已有商品查询接口失败：退回原提示，不阻断用户返回修改名称。
- 已有商品 `attribute` 解析失败：仅展示基础属性，规格区显示“暂无规格”。
- 添加规格后保存：以 `model.id` 走更新；如后端返回失败，沿用现有底栏错误提示。
- 「更新规格」后再次点保存：仍会再次弹出提示，符合预期。
- 规格图片框只对 `enableImage` 的规格显示；未开启的规格不显示。
- 识别图仍可用于 OCR 文字标签展示，但不再写入 SKU/商品图片。

## 6. 验收标准

1. 选择已设置单位的类别 → 识别 → 结果页「单位」自动有值，可修改。
2. 识别名称与已有商品同名 → 点「商品保存」出现「商品已存在」弹层，展示名称与规格属性。
3. 点「更新规格」→ 弹层关闭，仍在识别结果页，无接口写入、`model.id` 不变。
4. 点「添加规格」→ 基础属性缺失补齐、识别值优先；规格值合并去重；`model.id` 变为已有商品 id；提示合并成功。
5. 合并后点「商品保存」→ 用更新接口保存成功。
6. 类别规格开启“添加规格图片”→ 识别结果页每个规格值后出现图片框，可添加并显示缩略图。
7. 保存后检查商品/SKU：识别原图没有被自动写成商品图或 SKU 图；用户添加的规格值图片正常写入。
8. 非 AI 商品编辑表单行为不变（无新弹层、原提示保留）。

## 7. 构建与部署

- 仅前端改动。
- 构建：`D:\erp\tools\node16-build\npm.cmd run build`（cwd `D:\erp\jshERP-web`）。
- 打包：使用 .NET `ZipFile` 打正斜杠 zip → `D:\erp\jshERP-web\dist-deploy.zip`。
- 上传：`D:\erp\tools\pscp.exe` → `root@192.168.100.1:/mnt/mmc0-4/jsherp/dist-deploy.zip`。
- 服务器：先备份 `frontend-dist`，解压到 `frontend-dist-new`，切换为 `frontend-dist`，`docker compose up -d --no-deps --force-recreate app`。
- 健康检查：`curl 127.0.0.1:9998`。

## 8. 风险与回滚

- 风险：`checkMaterial` 为组合条件（名称/型号/颜色等），按其结果进入“名称重复”弹层后仍按名称查不到时可回退原提示，避免误判。
- 风险：`attribute` 为历史 JSON，存在旧格式；解析失败时仅展示基础属性，不影响合并基础属性。
- 回滚：切换前备份 `frontend-dist`；异常时恢复备份目录并重启 app 容器。
