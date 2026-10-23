-- =====================================================
-- 补齐 jsh_material_spec_template 缺失的 tenant_id 列
-- 原因：MaterialSpecTemplateMapper 的查询/写入均按 tenant_id 过滤，
--       但建表脚本中缺少该列，导致新增商品时报
--       Unknown column 'jsh_material_spec_template.tenant_id'。
-- 说明：使用 information_schema 判断，可重复执行。
-- =====================================================

SET @ddl = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'jsh_material_spec_template'
              AND COLUMN_NAME = 'tenant_id'),
    'SELECT ''column tenant_id already exists''',
    'ALTER TABLE `jsh_material_spec_template` ADD COLUMN `tenant_id` BIGINT NULL COMMENT ''租户ID'' AFTER `sort`'
));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 历史数据按商品所属租户回填
UPDATE `jsh_material_spec_template` t
JOIN `jsh_material` m ON m.id = t.material_id
SET t.tenant_id = m.tenant_id
WHERE t.tenant_id IS NULL;
