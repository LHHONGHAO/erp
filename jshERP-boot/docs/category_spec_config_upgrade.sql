-- 商品类别规格模板升级
-- spec_config 保存规格卡片、预设规格值及规格图片路径的 JSON。
-- 使用条件 DDL，已有该列时不会重复添加。
SET @category_spec_config_exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'jsh_material_category'
    AND COLUMN_NAME = 'spec_config'
);

SET @category_spec_config_ddl = IF(
  @category_spec_config_exists = 0,
  'ALTER TABLE `jsh_material_category` ADD COLUMN `spec_config` TEXT NULL COMMENT ''商品规格模板JSON'' AFTER `remark`',
  'SELECT ''spec_config already exists'''
);

PREPARE category_spec_config_stmt FROM @category_spec_config_ddl;
EXECUTE category_spec_config_stmt;
DEALLOCATE PREPARE category_spec_config_stmt;

ALTER TABLE `jsh_material_category`
  MODIFY COLUMN `spec_config` TEXT NULL COMMENT '商品类别规格属性配置JSON';
