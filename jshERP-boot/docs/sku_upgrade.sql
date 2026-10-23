-- =====================================================
-- SKU 编码体系升级 SQL
-- 新版规则: SPU码 + 各规格属性值编码；属性值编码使用无前缀数字序号
-- =====================================================

-- 1. 新增属性值字典表（结构化存储颜色/尺码等属性值，带稳定 value_code）
CREATE TABLE IF NOT EXISTS `jsh_material_attr_value` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `attribute_id` BIGINT NOT NULL COMMENT '关联 jsh_material_attribute.id（属性名：颜色/尺码）',
  `category_id` BIGINT DEFAULT NULL COMMENT '商品类别ID；NULL表示旧版全局属性值',
  `value_text` VARCHAR(64) NOT NULL COMMENT '属性值文本（红、XL）',
  `value_detail` TEXT DEFAULT NULL COMMENT '组合规格子项JSON明细',
  `value_code` VARCHAR(16) NOT NULL COMMENT '属性值编码（01/02），用于SKU编码',
  `sort` INT DEFAULT 0 COMMENT '排序',
  `status` VARCHAR(1) DEFAULT '0' COMMENT '状态：0启用 1停用（逻辑删除）',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户id',
  `delete_flag` VARCHAR(1) DEFAULT '0' COMMENT '删除标记：0未删 1已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_code` (`tenant_id`, `value_code`),
  KEY `idx_attribute_id` (`attribute_id`),
  KEY `idx_category_attribute` (`category_id`, `attribute_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='商品属性值字典表';

-- 2. 商品类别表增加 code 字段（类型码，2位，如 01服装/02食品）
ALTER TABLE `jsh_material_category` ADD COLUMN `code` VARCHAR(8) DEFAULT NULL COMMENT '类别编码（用于SKU类型码段）' AFTER `serial_no`;

-- 3. 商品规格明细增加 sku_code 字段（SPU码+颜色码+尺码码）
ALTER TABLE `jsh_material_extend` ADD COLUMN `sku_code` VARCHAR(50) DEFAULT NULL COMMENT 'SKU编码（SPU码-颜色码-尺码码）' AFTER `sku`;
ALTER TABLE `jsh_material_extend` ADD UNIQUE INDEX `uk_sku_code` (`sku_code`);

-- =====================================================
-- 初始化示例数据（可选，按需执行）
-- =====================================================
-- 属性名：颜色、尺码（若 jsh_material_attribute 已有则跳过）
-- INSERT INTO jsh_material_attribute(attribute_name, attribute_value, tenant_id, delete_flag) VALUES('颜色','',1,'0');
-- INSERT INTO jsh_material_attribute(attribute_name, attribute_value, tenant_id, delete_flag) VALUES('尺码','',1,'0');

-- 颜色属性值字典
-- INSERT INTO jsh_material_attr_value(attribute_id, value_text, value_code, sort, status, tenant_id, delete_flag) VALUES
-- ((SELECT id FROM jsh_material_attribute WHERE attribute_name='颜色' LIMIT 1), '红', '01', 1, '0', 1, '0'),
-- ((SELECT id FROM jsh_material_attribute WHERE attribute_name='颜色' LIMIT 1), '蓝', '02', 2, '0', 1, '0'),
-- ((SELECT id FROM jsh_material_attribute WHERE attribute_name='颜色' LIMIT 1), '黑', '03', 3, '0', 1, '0');

-- 尺码属性值字典
-- INSERT INTO jsh_material_attr_value(attribute_id, value_text, value_code, sort, status, tenant_id, delete_flag) VALUES
-- ((SELECT id FROM jsh_material_attribute WHERE attribute_name='尺码' LIMIT 1), 'S',  '04', 1, '0', 1, '0'),
-- ((SELECT id FROM jsh_material_attribute WHERE attribute_name='尺码' LIMIT 1), 'M',  '05', 2, '0', 1, '0'),
-- ((SELECT id FROM jsh_material_attribute WHERE attribute_name='尺码' LIMIT 1), 'L',  '06', 3, '0', 1, '0'),
-- ((SELECT id FROM jsh_material_attribute WHERE attribute_name='尺码' LIMIT 1), 'XL', '07', 4, '0', 1, '0');
