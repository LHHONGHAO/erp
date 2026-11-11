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
