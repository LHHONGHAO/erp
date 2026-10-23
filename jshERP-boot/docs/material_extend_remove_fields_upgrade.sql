-- =====================================================
-- 删除 jsh_material_extend（SKU 表）字段
-- 字段：bar_code / commodity_unit / sku
-- 说明：
--   1. 删除前先备份被删列数据到 jsh_material_extend_removed_cols_bak，便于误删恢复；
--   2. 使用 information_schema 判断列是否存在，已删除过的列自动跳过，可重复执行；
--   3. bar_code 列上有索引 INDEX bar_code，需先删除索引再删列。
-- 数据去向（删除前请确认）：
--   bar_code       -> 商品条码改由主表 jsh_material.bar_code 统一维护
--   commodity_unit -> 商品单位改由主表 jsh_material.unit（字符串）维护
--   sku            -> 规格描述改由 jsh_material_extend.spec_values（JSON）维护
--
-- 【执行前置条件｜重要】
--   本脚本仅改数据库，代码尚未同步。执行前必须先完成 Mapper/实体改造，
--   否则以下位置会因 Unknown column 报错（详见 docs/商品主表与SKU表字段说明.md 第六章）：
--     MaterialExtendMapper.xml / MaterialExtendMapperEx.xml /
--     MaterialMapperEx.xml / DepotItemMapperEx.xml /
--     MaterialExtend.java / MaterialExtendExample.java +
--     MaterialExtendService / MaterialService / MaterialExtendController
-- =====================================================

-- 1. 备份（仅备份当前仍存在的列，逐列判断，避免列不存在导致整条语句失败）
DROP PROCEDURE IF EXISTS `tmp_bak_material_extend_cols`;
DELIMITER $$
CREATE PROCEDURE `tmp_bak_material_extend_cols`()
BEGIN
    DECLARE col_list TEXT DEFAULT '';

    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material_extend' AND COLUMN_NAME = 'bar_code') THEN
        SET col_list = CONCAT(col_list, ', `bar_code`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material_extend' AND COLUMN_NAME = 'commodity_unit') THEN
        SET col_list = CONCAT(col_list, ', `commodity_unit`');
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material_extend' AND COLUMN_NAME = 'sku') THEN
        SET col_list = CONCAT(col_list, ', `sku`');
    END IF;

    IF col_list <> '' THEN
        SET @bak_sql = CONCAT('CREATE TABLE IF NOT EXISTS `jsh_material_extend_removed_cols_bak` AS SELECT `id`, `material_id`', col_list, ' FROM `jsh_material_extend`');
        PREPARE stmt FROM @bak_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL `tmp_bak_material_extend_cols`();
DROP PROCEDURE IF EXISTS `tmp_bak_material_extend_cols`;

-- 2. 删除列（先释放 bar_code 索引依赖，再删列；逐列判断存在性）
DROP PROCEDURE IF EXISTS `tmp_drop_material_extend_cols`;
DELIMITER $$
CREATE PROCEDURE `tmp_drop_material_extend_cols`()
BEGIN
    -- bar_code 上的普通索引需先删除
    IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material_extend' AND INDEX_NAME = 'bar_code') THEN
        ALTER TABLE `jsh_material_extend` DROP INDEX `bar_code`;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material_extend' AND COLUMN_NAME = 'bar_code') THEN
        ALTER TABLE `jsh_material_extend` DROP COLUMN `bar_code`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material_extend' AND COLUMN_NAME = 'commodity_unit') THEN
        ALTER TABLE `jsh_material_extend` DROP COLUMN `commodity_unit`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material_extend' AND COLUMN_NAME = 'sku') THEN
        ALTER TABLE `jsh_material_extend` DROP COLUMN `sku`;
    END IF;
END$$
DELIMITER ;

CALL `tmp_drop_material_extend_cols`();
DROP PROCEDURE IF EXISTS `tmp_drop_material_extend_cols`;

-- 3. 校验：剩余列
-- SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT FROM information_schema.COLUMNS
--  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'jsh_material_extend' ORDER BY ORDINAL_POSITION;
