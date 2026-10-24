-- ============================================================
-- SKU 与 SKU 规格表重建脚本（按开发交接卡执行）
-- 执行库：jsh_erp    数据库：MySQL 8.4
--
-- 约定：
--   1. 表名、字段严格按交接文档定义，不做删减
--   2. 额外增加 tenant_id：项目为多租户架构，缺失会导致数据串租户
--   3. 软删除统一使用 deleted_at，禁止物理删除
--   4. spec_json / spec_summary 由后端代码生成，禁止前端传入
-- ============================================================

-- ---------- 1. SKU 主表 ----------
DROP TABLE IF EXISTS `sku`;

CREATE TABLE `sku` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `spu_id` bigint unsigned NOT NULL COMMENT '关联SPU',
  `sku_code` varchar(64) NOT NULL COMMENT 'SKU编码',
  `sku_name` varchar(255) NOT NULL COMMENT '展示名，如：红色 L码',

  -- 核心冗余字段
  `spec_json` json NOT NULL COMMENT '前端渲染用：[{"name":"颜色","value":"红"}]',
  `spec_summary` varchar(512) NOT NULL COMMENT '搜索用：颜色:红;尺码:L',

  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID（项目多租户必需）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁',
  `deleted_at` datetime DEFAULT NULL COMMENT '软删除，NULL=有效',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_code` (`sku_code`,`deleted_at`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_spec_summary` (`spu_id`,`spec_summary`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU主表';

-- ---------- 2. SKU 规格表（唯一事实来源） ----------
DROP TABLE IF EXISTS `sku_spec`;

CREATE TABLE `sku_spec` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `sku_id` bigint unsigned NOT NULL,
  `spec_name` varchar(50) NOT NULL COMMENT '规格名：颜色',
  `spec_value` varchar(50) NOT NULL COMMENT '规格值：红',
  `image_url` varchar(500) DEFAULT NULL COMMENT '规格值图片相对路径；同一SKU内最多一行有值',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号，用于固定拼接顺序',
  `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sku_name` (`sku_id`, `spec_name`),
  KEY `idx_name_value` (`spec_name`, `spec_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SKU规格表';

-- ---------- 3. 历史数据迁移（从 jsh_material_extend.spec_values 反推） ----------
-- 拼接顺序：按 spec_values 数组的原始顺序（seq）
-- 说明：JSON_ARRAYAGG 不保证顺序，故用 GROUP_CONCAT + ORDER BY 拼接后再转 JSON
INSERT INTO `sku` (`spu_id`, `sku_code`, `sku_name`, `spec_json`, `spec_summary`, `tenant_id`)
SELECT
  t.material_id,
  t.sku_code,
  GROUP_CONCAT(t.valueText ORDER BY t.seq SEPARATOR ' '),
  CAST(CONCAT('[', GROUP_CONCAT(JSON_OBJECT('name', t.attributeName, 'value', t.valueText) ORDER BY t.seq SEPARATOR ','), ']') AS JSON),
  GROUP_CONCAT(CONCAT(t.attributeName, ':', t.valueText) ORDER BY t.seq SEPARATOR ';'),
  t.tenant_id
FROM (
  SELECT e.material_id,
         e.sku_code,
         e.tenant_id,
         jt.seq,
         jt.attributeName,
         jt.valueText
  FROM jsh_material_extend e
  JOIN JSON_TABLE(e.spec_values, '$[*]' COLUMNS (
         seq           FOR ORDINALITY,
         attributeName VARCHAR(50)  PATH '$.attributeName',
         valueText     VARCHAR(200) PATH '$.valueText'
       )) jt
  WHERE IFNULL(e.delete_Flag, '0') <> '1'
    AND IFNULL(e.sku_code, '') <> ''
    AND IFNULL(e.spec_values, '') <> ''
) t
GROUP BY t.material_id, t.sku_code, t.tenant_id;

-- 规格明细拆行写入 sku_spec
INSERT INTO `sku_spec` (`sku_id`, `spec_name`, `spec_value`, `image_url`, `sort_no`, `tenant_id`)
SELECT
  s.id,
  t.attributeName,
  t.valueText,
  CASE WHEN IFNULL(t.imageUrl, '') = '' THEN NULL ELSE t.imageUrl END,
  t.seq,
  t.tenant_id
FROM (
  SELECT e.material_id,
         e.sku_code,
         e.tenant_id,
         jt.seq,
         jt.attributeName,
         jt.valueText,
         jt.imageUrl
  FROM jsh_material_extend e
  JOIN JSON_TABLE(e.spec_values, '$[*]' COLUMNS (
         seq           FOR ORDINALITY,
         attributeName VARCHAR(50)  PATH '$.attributeName',
         valueText     VARCHAR(200) PATH '$.valueText',
         imageUrl      VARCHAR(500) PATH '$.image'
       )) jt
  WHERE IFNULL(e.delete_Flag, '0') <> '1'
    AND IFNULL(e.sku_code, '') <> ''
    AND IFNULL(e.spec_values, '') <> ''
) t
JOIN `sku` s
  ON s.spu_id = t.material_id
 AND s.sku_code = t.sku_code
 AND s.deleted_at IS NULL;

-- ---------- 4. 校验 ----------
-- SELECT id, spu_id, sku_code, sku_name, spec_json, spec_summary FROM sku;
-- SELECT * FROM sku_spec ORDER BY sku_id, sort_no;
