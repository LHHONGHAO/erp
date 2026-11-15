# 实现任务清单 · 商品AI添加识别结果页修正

> 对应规格：[spec.md](file:///D:/erp/docs/spec/spec.md)
> 状态：已完成并部署

## 任务

- [x] T1 单位回填
  - [x] `MaterialAiAdd.vue`：新增 `resolveCategoryUnit(categoryId)`，`openResult()` 传入单位。
  - [x] `MaterialModal.vue`：`applyPresetCategory(categoryId, unit)` 保存 `presetCategoryUnit`。
  - [x] `MaterialModal.vue`：新增 `findCategoryUnit(id)`（含父级回溯）与 `applyPresetUnitFallback()`。
  - [x] `MaterialModal.vue`：`applyCategoryUnitToAggregate()` 增加 `findCategoryUnit → presetCategoryUnit` 兜底，识别值优先。
  - [x] 验证：类别设置单位 → 识别结果页单位自动带出。

- [x] T2 商品已存在弹层
  - [x] `MaterialModal.vue`：AI 保存先用 `/material/checkIsNameExist` 判断名称重复。
  - [x] 新增 data：`existMaterialVisible / existMaterialLoading / existMaterial`。
  - [x] 新增 computed：`existMaterialPropRows / existMaterialSpecRows`。
  - [x] 新增 `openExistMaterialDialog()`：`/material/list` 按名称精确匹配取商品级数据（含 `attribute`）。
  - [x] `doSaveMaterial()`：名称存在时打开新弹层，否则进入 `doSaveMaterialByCheck()`；非 AI 保持原提示。
  - [x] 模板新增「商品已存在」弹层（确认稿样式，`zIndex=1170`）。
  - [x] 验证：同名商品保存 → 弹层展示名称与属性/规格。

- [x] T3 添加规格（合并）
  - [x] 新增 `mergeExistMaterial()`：
    - [x] 基础属性：识别值优先，空值用已有值补齐。
    - [x] 规格：补维度、补值、同名去重、识别值优先。
    - [x] `model.id = exist.id`。
  - [x] 合并后关闭弹层，提示“已合并已有商品属性，请确认后保存”。
  - [x] 模板重复维度 key 改为 `group.index + '-' + dIndex + '-' + dim.name`。
  - [x] 验证：合并后保存走更新接口且成功。

- [x] T4 更新规格（仅关闭）
  - [x] 新增 `closeExistMaterialDialog()`：仅关闭弹层、恢复按钮状态。
  - [x] 验证：无网络写入、`model.id` 不变、停留识别结果页。

- [x] T5 规格值图片框 & 取消识别图作商品图
  - [x] `prepareAggregateSpecGroups()`：为 dim 设置 `enableImage` 与 `image`。
  - [x] 模板：单值/组合规格在规格值后增加 `.agg-spec-img-upload`，点击 `openAggSpecImage(dim)`。
  - [x] 新增 `pendingAggImageDim` 与 `openAggSpecImage()`；扩展 `handleSpecImageOcrClose()` 处理聚合图片。
  - [x] `applySpecAggregate()`：移除识别图自动写入规格值/SKU；改为写入用户添加的 `dim.image`。
  - [x] 移除规格名旁的识别图图标。
  - [x] 新增 `.agg-spec-img-upload` 样式。
  - [x] 验证：规格图可添加/替换；保存后无识别原图写入。

- [x] T6 构建与部署
  - [x] `D:\erp\tools\node16-build\npm.cmd run build`（cwd `D:\erp\jshERP-web`）。
  - [x] 打包 `dist-deploy.zip`（正斜杠）。
  - [x] 上传并备份切换 `frontend-dist`。
  - [x] `docker compose up -d --no-deps --force-recreate app`。
  - [x] 健康检查：`http://127.0.0.1:9998` 返回 200。

## 回归检查

- [x] 非 AI 商品编辑表单：无新弹层、保存提示不变（代码分支隔离）。
- [x] 键盘弹层、识别弹层圆角、类别页单位保存等既有修复未改动。
