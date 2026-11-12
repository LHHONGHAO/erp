-- One-time upgrade: isolate learned normalization samples by product category.
-- Run after spec_value_sample_upgrade.sql. Existing samples are retained in
-- their current category; category 0 remains a cross-category fallback.

ALTER TABLE `jsh_spec_value_sample`
  DROP INDEX `uk_spec_sample_norm`,
  ADD UNIQUE KEY `uk_spec_sample_category_norm`
    (`tenant_id`, `category_id`, `attribute_id`, `norm_key`);

ALTER TABLE `jsh_spec_value_alias`
  ADD COLUMN `category_id` BIGINT NOT NULL DEFAULT 0 COMMENT '商品类别ID，0表示不限类别' AFTER `sample_id`;

UPDATE `jsh_spec_value_alias` a
JOIN `jsh_spec_value_sample` s ON s.id = a.sample_id
SET a.category_id = s.category_id;

ALTER TABLE `jsh_spec_value_alias`
  DROP INDEX `uk_spec_alias_norm`,
  ADD UNIQUE KEY `uk_spec_alias_category_norm`
    (`tenant_id`, `category_id`, `attribute_id`, `norm_key`);
