-- =====================================================
-- 规格值样本库（一期）
-- 用途：沉淀「规格值」的各种写法（200x230cm / 200·230 / 200×230CM 等），
--       识别时先归一化再比对样本，命中后回填标准写法，持续提升识别准确率。
-- 说明：CREATE TABLE IF NOT EXISTS，可重复执行。
-- =====================================================

-- 1. 规格值样本（标准写法 + 归一化键）
CREATE TABLE IF NOT EXISTS `jsh_spec_value_sample` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
  `category_id` BIGINT NOT NULL DEFAULT 0 COMMENT '商品类别ID，0表示不限品类',
  `attribute_id` BIGINT NOT NULL DEFAULT 0 COMMENT '规格名ID（jsh_material_attribute.id），0表示不限规格名',
  `canonical_value` VARCHAR(255) NOT NULL COMMENT '标准写法，如 200×230cm',
  `norm_key` VARCHAR(255) NOT NULL COMMENT '归一化键，如 200*230|cm；无单位时为 200*230',
  `attr_value_id` BIGINT DEFAULT NULL COMMENT '对应的商品属性值字典ID（jsh_material_attr_value.id）',
  `source` VARCHAR(16) DEFAULT 'manual' COMMENT '来源：manual 人工、ocr 识别、sku 商品保存',
  `confidence` DECIMAL(5,4) DEFAULT 1.0000 COMMENT '样本置信度',
  `hit_count` INT DEFAULT 0 COMMENT '命中次数',
  `status` VARCHAR(4) DEFAULT '0' COMMENT '0启用 1停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_flag` VARCHAR(4) DEFAULT '0' COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spec_sample_norm` (`tenant_id`, `attribute_id`, `norm_key`),
  KEY `idx_spec_sample_norm` (`tenant_id`, `norm_key`),
  KEY `idx_spec_sample_attr` (`attribute_id`, `category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格值样本库';

-- 2. 规格值别名（同一标准写法的不同原始写法）
CREATE TABLE IF NOT EXISTS `jsh_spec_value_alias` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
  `sample_id` BIGINT NOT NULL COMMENT '所属样本ID',
  `raw_text` VARCHAR(255) NOT NULL COMMENT '原始写法，如 200·230',
  `norm_key` VARCHAR(255) NOT NULL COMMENT '原始写法归一化键',
  `hit_count` INT DEFAULT 0 COMMENT '命中次数',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `delete_flag` VARCHAR(4) DEFAULT '0' COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spec_alias_norm` (`tenant_id`, `norm_key`),
  KEY `idx_spec_alias_sample` (`sample_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格值别名表';

-- 3. 规格值学习日志（记录人工纠错，便于回溯与二期训练）
CREATE TABLE IF NOT EXISTS `jsh_spec_learn_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
  `raw_text` VARCHAR(255) DEFAULT NULL COMMENT '原始识别文本',
  `attr_id` BIGINT DEFAULT NULL COMMENT '规格名ID',
  `original_value` VARCHAR(255) DEFAULT NULL COMMENT '识别结果',
  `corrected_value` VARCHAR(255) DEFAULT NULL COMMENT '用户纠正后的值',
  `material_id` BIGINT DEFAULT NULL COMMENT '关联商品ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `delete_flag` VARCHAR(4) DEFAULT '0' COMMENT '0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_spec_learn_time` (`tenant_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='规格值学习日志';

-- =====================================================
-- 4. P0 结构修正（2026-11）
-- 目的：修复唯一键失效、别名跨规格名串味、学习日志缺少场景信息
-- 说明：本段为一次性 ALTER，重复执行会报「列/索引已存在」，属正常
-- =====================================================

-- 4.1 租户ID补 0：MySQL 中 NULL 不参与唯一约束，会导致样本重复
UPDATE `jsh_spec_value_sample` SET `tenant_id` = 0 WHERE `tenant_id` IS NULL;
ALTER TABLE `jsh_spec_value_sample`
  MODIFY COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID';

-- 4.2 别名按规格名隔离：同一个 norm_key 在不同规格名下可能含义不同
UPDATE `jsh_spec_value_alias` SET `tenant_id` = 0 WHERE `tenant_id` IS NULL;
ALTER TABLE `jsh_spec_value_alias`
  MODIFY COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  ADD COLUMN `attribute_id` BIGINT NOT NULL DEFAULT 0 COMMENT '规格名ID（jsh_material_attribute.id），0表示不限规格名' AFTER `sample_id`,
  DROP INDEX `uk_spec_alias_norm`,
  ADD UNIQUE KEY `uk_spec_alias_norm` (`tenant_id`, `attribute_id`, `norm_key`);

-- 4.3 学习日志补充场景与采纳结果，便于统计纠错率
ALTER TABLE `jsh_spec_learn_log`
  ADD COLUMN `scene` VARCHAR(16) DEFAULT 'ocr_spec' COMMENT '场景：ocr_spec 规格识别、material_attr 商品属性、sku_spec SKU规格' AFTER `attr_id`,
  ADD COLUMN `accepted` VARCHAR(1) DEFAULT '0' COMMENT '是否被采纳：0未采纳 1采纳' AFTER `corrected_value`,
  ADD COLUMN `source_image_idx` INT DEFAULT NULL COMMENT '来源图片序号，多图聚合时使用' AFTER `material_id`;

