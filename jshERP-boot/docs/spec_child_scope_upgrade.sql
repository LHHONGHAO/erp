-- =====================================================
-- 学习/归一/补全的维度隔离
-- 背景：
--   1. 同一品类下很多属性值是固定的少量取值（如安全类别），应按「品类 + 属性」归一与补全；
--   2. 同一规格名下不同子规格是不同维度（花色下的「颜色」、尺寸下的「被套/床单/枕套」），
--      规格值样本必须按子规格隔离，避免被套的 200x230 串到床单。
-- 说明：一次性 ALTER，重复执行会报「列/索引已存在」，属正常。
-- =====================================================

ALTER TABLE `jsh_spec_value_sample`
  ADD COLUMN `child_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '规格子项名（被套/床单/枕套/颜色），空表示不限子项' AFTER `attribute_id`,
  DROP INDEX `uk_spec_sample_category_norm`,
  ADD UNIQUE KEY `uk_spec_sample_scope_norm`
    (`tenant_id`, `category_id`, `attribute_id`, `child_name`, `norm_key`);

ALTER TABLE `jsh_spec_value_alias`
  ADD COLUMN `child_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '规格子项名（被套/床单/枕套/颜色），空表示不限子项' AFTER `attribute_id`,
  DROP INDEX `uk_spec_alias_category_norm`,
  ADD UNIQUE KEY `uk_spec_alias_scope_norm`
    (`tenant_id`, `category_id`, `attribute_id`, `child_name`, `norm_key`);
