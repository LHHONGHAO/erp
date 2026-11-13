-- 商品类别默认单位升级
-- unit 保存类目默认单位，商品录入选择该类别时自动带出。
-- 使用条件 DDL，已有该列时不会重复添加。
SET @category_unit_exists = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'jsh_material_category'
    AND COLUMN_NAME = 'unit'
);

SET @category_unit_ddl = IF(
  @category_unit_exists = 0,
  'ALTER TABLE `jsh_material_category` ADD COLUMN `unit` VARCHAR(50) NULL COMMENT ''默认单位'' AFTER `spec_config`',
  'SELECT ''unit already exists'''
);

PREPARE category_unit_stmt FROM @category_unit_ddl;
EXECUTE category_unit_stmt;
DEALLOCATE PREPARE category_unit_stmt;
