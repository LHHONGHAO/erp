-- =====================================================
-- 删除 jsh_material 主表字段（第二批）
-- 字段：model / standard / color / img_name / unit_id / expiry_num / enabled / attribute
-- 说明：
--   1. 删除前先备份被删列数据到 jsh_material_removed_cols_bak2，便于误删恢复；
--   2. 使用 information_schema 判断列是否存在，已删除过的列自动跳过，可重复执行；
--   3. 兼容此前 material_remove_fields_upgrade.sql 已删除 unit_id / color / attribute / enabled 的情况。
-- =====================================================

-- 1. 备份（仅备份当前仍存在的列，逐列判断，避免列不存在导致整条语句失败）
DROP PROCEDURE IF EXISTS `tmp_bak_material_cols_v2`;
DELIMITER $$
CREATE PROCEDURE `tmp_bak_material_cols_v2`()
BEGIN
    DECLARE col_list TEXT DEFAULT '';

    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'model') THEN
        SET col_list = CONCAT(col_list, ', `model`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'standard') THEN
        SET col_list = CONCAT(col_list, ', `standard`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'color') THEN
        SET col_list = CONCAT(col_list, ', `color`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'img_name') THEN
        SET col_list = CONCAT(col_list, ', `img_name`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'unit_id') THEN
        SET col_list = CONCAT(col_list, ', `unit_id`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'expiry_num') THEN
        SET col_list = CONCAT(col_list, ', `expiry_num`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'enabled') THEN
        SET col_list = CONCAT(col_list, ', `enabled`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'attribute') THEN
        SET col_list = CONCAT(col_list, ', `attribute`');
    END IF;

    IF col_list <> '' THEN
        SET @bak_sql = CONCAT('CREATE TABLE IF NOT EXISTS `jsh_material_removed_cols_bak2` AS SELECT `id`', col_list, ' FROM `jsh_material`');
        PREPARE stmt FROM @bak_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL `tmp_bak_material_cols_v2`();
DROP PROCEDURE IF EXISTS `tmp_bak_material_cols_v2`;

-- 2. 删除列（先释放索引/外键依赖，再删列；逐列判断存在性）
DROP PROCEDURE IF EXISTS `tmp_drop_material_cols_v2`;
DELIMITER $$
CREATE PROCEDURE `tmp_drop_material_cols_v2`()
BEGIN
    -- unit_id 上存在索引 UnitId，需先删除索引
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND INDEX_NAME = 'UnitId') THEN
        ALTER TABLE `jsh_material` DROP INDEX `UnitId`;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'model') THEN
        ALTER TABLE `jsh_material` DROP COLUMN `model`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'standard') THEN
        ALTER TABLE `jsh_material` DROP COLUMN `standard`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'color') THEN
        ALTER TABLE `jsh_material` DROP COLUMN `color`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'img_name') THEN
        ALTER TABLE `jsh_material` DROP COLUMN `img_name`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'unit_id') THEN
        ALTER TABLE `jsh_material` DROP COLUMN `unit_id`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'expiry_num') THEN
        ALTER TABLE `jsh_material` DROP COLUMN `expiry_num`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'enabled') THEN
        ALTER TABLE `jsh_material` DROP COLUMN `enabled`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' AND COLUMN_NAME = 'attribute') THEN
        ALTER TABLE `jsh_material` DROP COLUMN `attribute`;
    END IF;
END$$
DELIMITER ;

CALL `tmp_drop_material_cols_v2`();
DROP PROCEDURE IF EXISTS `tmp_drop_material_cols_v2`;

-- 3. 校验：剩余列
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material' ORDER BY ORDINAL_POSITION;
