-- ============================================================
-- SKU体系升级 V2：基础信息与商品信息分离 + SKU级库存
-- 日期：2026-08-05
-- 策略说明（向后兼容版）：
--   本脚本只 ADD 列、建新表，不 DROP 任何已有列。
--   原有 color/standard/attribute/enable_batch_number 列保留在 jsh_material，
--   原 bar_code/commodity_unit/sku/sku_code 列保留在 jsh_material_extend，
--   以保证 DepotHead/DepotItem 等报表查询不中断。
--   新功能通过 bar_code(jsh_material) + spec_values(jsh_material_extend) 实现。
--
--   1. 商品主表(jsh_material)新增 bar_code（同一商品同一条码）
--   2. SKU表(jsh_material_extend)新增 spec_values（存储动态规格值组合JSON）
--   3. 新建商品规格模板表(jsh_material_spec_template)，支持按商品种类动态规格属性数量
--   4. 库存表新增 material_extend_id 和 sku_code 字段，支持SKU级库存
-- ============================================================

-- ============================================================
-- 1. 商品主表 jsh_material 新增字段
-- ============================================================
-- 1.1 新增 bar_code 字段（同一商品同一条码，从SKU表提升到主表）
--     注意：若列已存在请忽略报错
ALTER TABLE `jsh_material` ADD COLUMN `bar_code` varchar(50) NULL DEFAULT NULL COMMENT '商品条码（主表统一管理）' AFTER `name`;


-- ============================================================
-- 2. SKU表 jsh_material_extend 新增字段
-- ============================================================
-- 2.1 新增 spec_values 字段（存储该SKU的规格值组合JSON）
ALTER TABLE `jsh_material_extend` ADD COLUMN `spec_values` text NULL DEFAULT NULL COMMENT '规格值组合JSON，如[{"attributeId":1,"attributeName":"颜色","valueText":"红","valueCode":"01"}]' AFTER `material_id`;


-- ============================================================
-- 3. 新建商品规格模板表 jsh_material_spec_template
--    定义每个商品使用哪些规格属性（支持动态数量：被套2个，四件套4个）
-- ============================================================
DROP TABLE IF EXISTS `jsh_material_spec_template`;
CREATE TABLE `jsh_material_spec_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_id` bigint NULL DEFAULT NULL COMMENT '商品ID',
  `attribute_id` bigint NULL DEFAULT NULL COMMENT '规格属性ID（关联jsh_material_attribute.id）',
  `sort` int NULL DEFAULT 0 COMMENT '排序（展示顺序）',
  `delete_flag` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `material_id`(`material_id`) USING BTREE,
  INDEX `attribute_id`(`attribute_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '商品规格模板（定义商品使用的规格属性）' ROW_FORMAT = Compact;


-- ============================================================
-- 4. 库存表调整：支持SKU级库存
-- ============================================================
-- 4.1 期初库存表新增 material_extend_id 和 sku_code
ALTER TABLE `jsh_material_initial_stock` ADD COLUMN `material_extend_id` bigint NULL DEFAULT NULL COMMENT 'SKU记录ID（关联jsh_material_extend.id）' AFTER `material_id`;
ALTER TABLE `jsh_material_initial_stock` ADD COLUMN `sku_code` varchar(50) NULL DEFAULT NULL COMMENT '关联的SKU编码' AFTER `material_extend_id`;
ALTER TABLE `jsh_material_initial_stock` ADD INDEX `material_extend_id`(`material_extend_id`) USING BTREE;

-- 4.2 当前库存表新增 material_extend_id 和 sku_code
ALTER TABLE `jsh_material_current_stock` ADD COLUMN `material_extend_id` bigint NULL DEFAULT NULL COMMENT 'SKU记录ID（关联jsh_material_extend.id）' AFTER `material_id`;
ALTER TABLE `jsh_material_current_stock` ADD COLUMN `sku_code` varchar(50) NULL DEFAULT NULL COMMENT '关联的SKU编码' AFTER `material_extend_id`;
ALTER TABLE `jsh_material_current_stock` ADD INDEX `material_extend_id`(`material_extend_id`) USING BTREE;


-- ============================================================
-- 5. 数据迁移（可选）：将现有 jsh_material.bar_code 回填
--    从 jsh_material_extend 的 default_flag=1 记录取 bar_code 填入主表
-- ============================================================
UPDATE jsh_material m
INNER JOIN jsh_material_extend me ON me.material_id = m.id
  AND ifnull(me.delete_Flag,'0') !='1'
  AND me.default_flag = '1'
SET m.bar_code = me.bar_code
WHERE ifnull(m.bar_code,'') = '' AND me.bar_code IS NOT NULL AND me.bar_code != '';
