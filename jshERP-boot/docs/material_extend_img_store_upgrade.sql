-- =====================================================
-- 补齐 jsh_material_extend 缺失的 img_url / store 列
-- 原因：MaterialExtendMapper 的列清单包含 img_url、store，
--       但建表脚本中缺少这两列，导致保存 SKU 时报
--       Unknown column 'img_url' in 'field list'。
-- 说明：使用 information_schema 判断，可重复执行。
-- =====================================================

SET @ddl = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'jsh_material_extend'
              AND COLUMN_NAME = 'img_url'),
    'SELECT ''column img_url already exists''',
    'ALTER TABLE `jsh_material_extend` ADD COLUMN `img_url` VARCHAR(500) NULL COMMENT ''图片地址'''
));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'jsh_material_extend'
              AND COLUMN_NAME = 'store'),
    'SELECT ''column store already exists''',
    'ALTER TABLE `jsh_material_extend` ADD COLUMN `store` VARCHAR(100) NULL COMMENT ''门店'''
));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
