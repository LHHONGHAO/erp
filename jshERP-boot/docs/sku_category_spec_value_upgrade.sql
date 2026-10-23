-- 商品规格值按“品类 + 规格名”分类存储。
-- 对已创建 jsh_material_attr_value 的数据库执行一次。
ALTER TABLE `jsh_material_attr_value`
  ADD COLUMN `category_id` BIGINT DEFAULT NULL COMMENT '商品类别ID；NULL表示旧版全局属性值' AFTER `attribute_id`,
  ADD COLUMN `value_detail` TEXT DEFAULT NULL COMMENT '组合规格子项JSON明细' AFTER `value_text`,
  ADD KEY `idx_category_attribute` (`category_id`, `attribute_id`);
