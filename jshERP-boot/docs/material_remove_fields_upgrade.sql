-- 删除 jsh_material 主表字段：unit_id / color / attribute / enabled / enable_batch_number / position
-- 仅删列，不改动其它结构；specModules / manySku / meList 非数据库字段，无需删除
-- 删除前备份被删列数据，便于误删恢复

CREATE TABLE IF NOT EXISTS `jsh_material_removed_cols_bak` AS
SELECT `id`, `unit_id`, `color`, `attribute`, `enabled`, `enable_batch_number`, `position`
FROM `jsh_material`;

ALTER TABLE `jsh_material` DROP COLUMN `unit_id`;
ALTER TABLE `jsh_material` DROP COLUMN `color`;
ALTER TABLE `jsh_material` DROP COLUMN `attribute`;
ALTER TABLE `jsh_material` DROP COLUMN `enabled`;
ALTER TABLE `jsh_material` DROP COLUMN `enable_batch_number`;
ALTER TABLE `jsh_material` DROP COLUMN `position`;
