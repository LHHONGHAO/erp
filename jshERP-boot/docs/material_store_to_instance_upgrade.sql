-- =====================================================
-- 门店字段从 SKU 维度迁移到单品维度
-- 说明：
--   1. SKU（jsh_material_extend）不再保存门店/仓库信息；
--   2. 单品档案（jsh_material_instance）新增 store 门店字段，随单件码一起保存；
--   3. 删除字段前先备份 jsh_material_extend.store 到 jsh_material_extend_store_bak；
--   4. 全部使用 information_schema 判断，可重复执行。
-- =====================================================

-- 1. 单品表新增门店字段
SET @ddl = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'jsh_material_instance'
              AND COLUMN_NAME = 'store'),
    'SELECT ''column jsh_material_instance.store already exists''',
    'ALTER TABLE `jsh_material_instance` ADD COLUMN `store` VARCHAR(100) NULL COMMENT ''门店'' AFTER `inbound_depot_id`'
));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 备份 SKU 表门店列数据
CREATE TABLE IF NOT EXISTS `jsh_material_extend_store_bak` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `extend_id` BIGINT NULL COMMENT '原 jsh_material_extend.id',
    `material_id` BIGINT NULL COMMENT '商品ID',
    `sku_code` VARCHAR(32) NULL COMMENT 'SKU编码',
    `store` VARCHAR(100) NULL COMMENT '门店',
    `backup_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '备份时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='jsh_material_extend.store 删除前备份';

SET @ddl = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'jsh_material_extend'
              AND COLUMN_NAME = 'store'),
    'INSERT INTO `jsh_material_extend_store_bak` (`extend_id`,`material_id`,`sku_code`,`store`)
     SELECT `id`,`material_id`,`sku_code`,`store` FROM `jsh_material_extend`',
    'SELECT ''column jsh_material_extend.store not exists, skip backup'''
));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 删除 SKU 表门店字段
SET @ddl = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'jsh_material_extend'
              AND COLUMN_NAME = 'store'),
    'ALTER TABLE `jsh_material_extend` DROP COLUMN `store`',
    'SELECT ''column jsh_material_extend.store already dropped'''
));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
