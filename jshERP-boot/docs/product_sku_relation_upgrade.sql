-- 商品/SPU、规格SKU、库存流水关系增量升级
-- 适用：已部署 jshERP 的 MySQL 数据库；本脚本不创建单件实物表。
-- 执行前先备份数据库。若字段或索引已由 sku_upgrade.sql / sku_upgrade_v2.sql 创建，跳过对应 ADD 语句。
-- 本脚本仅增加字段、索引和填补空编码，不删除或覆盖已有编码。

-- 关系约定：
--   jsh_material.id                 = SPU 主键；jsh_material.code 为稳定 SPU 码
--   jsh_material_extend.id          = 现有规格/单位明细主键；sku_code 为 SKU 码
--   jsh_depot_item.material_extend_id -> jsh_material_extend.id（采购、销售、调拨等单据明细）
--   jsh_material_current_stock.material_extend_id -> jsh_material_extend.id（当前库存）
--   jsh_material_initial_stock.material_extend_id -> jsh_material_extend.id（期初/安全库存）
--   单件实物码表暂不创建；当前阶段按 SKU 汇总数量管理库存。

-- 1. 幂等补齐SPU、SKU和SKU库存关联字段（兼容已执行过旧升级脚本的数据库）。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_category' AND column_name='code')=0,
  'ALTER TABLE jsh_material_category ADD COLUMN code VARCHAR(8) NULL DEFAULT NULL COMMENT ''类别类型码'' AFTER serial_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material' AND column_name='code')=0,
  'ALTER TABLE jsh_material ADD COLUMN code VARCHAR(50) NULL DEFAULT NULL COMMENT ''SPU商品码'' AFTER name', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_extend' AND column_name='sku_code')=0,
  'ALTER TABLE jsh_material_extend ADD COLUMN sku_code VARCHAR(80) NULL DEFAULT NULL COMMENT ''SKU规格组合编码'' AFTER sku', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT IFNULL(MAX(character_maximum_length), 0) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_extend' AND column_name='sku_code') < 80,
  'ALTER TABLE jsh_material_extend MODIFY COLUMN sku_code VARCHAR(80) NULL DEFAULT NULL COMMENT ''SKU规格组合编码''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_extend' AND column_name='spec_values')=0,
  'ALTER TABLE jsh_material_extend ADD COLUMN spec_values TEXT NULL DEFAULT NULL COMMENT ''规格值组合JSON'' AFTER material_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_depot_item' AND column_name='material_extend_id')=0,
  'ALTER TABLE jsh_depot_item ADD COLUMN material_extend_id BIGINT NULL DEFAULT NULL COMMENT ''关联SKU/商品扩展ID'' AFTER material_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_current_stock' AND column_name='material_extend_id')=0,
  'ALTER TABLE jsh_material_current_stock ADD COLUMN material_extend_id BIGINT NULL DEFAULT NULL COMMENT ''SKU记录ID'' AFTER material_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_current_stock' AND column_name='sku_code')=0,
  'ALTER TABLE jsh_material_current_stock ADD COLUMN sku_code VARCHAR(80) NULL DEFAULT NULL COMMENT ''SKU编码快照'' AFTER material_extend_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT IFNULL(MAX(character_maximum_length), 0) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_current_stock' AND column_name='sku_code') < 80,
  'ALTER TABLE jsh_material_current_stock MODIFY COLUMN sku_code VARCHAR(80) NULL DEFAULT NULL COMMENT ''SKU编码快照''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_initial_stock' AND column_name='material_extend_id')=0,
  'ALTER TABLE jsh_material_initial_stock ADD COLUMN material_extend_id BIGINT NULL DEFAULT NULL COMMENT ''SKU记录ID'' AFTER material_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_initial_stock' AND column_name='sku_code')=0,
  'ALTER TABLE jsh_material_initial_stock ADD COLUMN sku_code VARCHAR(80) NULL DEFAULT NULL COMMENT ''SKU编码快照'' AFTER material_extend_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT IFNULL(MAX(character_maximum_length), 0) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='jsh_material_initial_stock' AND column_name='sku_code') < 80,
  'ALTER TABLE jsh_material_initial_stock MODIFY COLUMN sku_code VARCHAR(80) NULL DEFAULT NULL COMMENT ''SKU编码快照''', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 为历史商品补齐 SPU 码。已存在的商品码保持不变；空码使用类别类型码 + 商品ID构造稳定值。
UPDATE jsh_material m
LEFT JOIN jsh_material_category c ON c.id = m.category_id
SET m.code = CONCAT(
  'SP', RIGHT(CONCAT('00', COALESCE(NULLIF(c.code, ''), CAST(c.id AS CHAR), '00')), 2),
  '-', LPAD(m.id, 8, '0')
)
WHERE m.code IS NULL OR m.code = '';

-- 3. 为历史扩展行补齐 SKU 码。若规格数据缺失，使用扩展行ID生成唯一兼容码。
-- 已有 sku_code 不会被重写；新建SKU继续由应用按 SPU码和规格属性编码生成。
UPDATE jsh_material_extend me
JOIN jsh_material m ON m.id = me.material_id
SET me.sku_code = CONCAT(m.code, '-V', LPAD(me.id, 8, '0'))
WHERE (me.sku_code IS NULL OR me.sku_code = '')
  AND m.code IS NOT NULL AND m.code <> '';

-- 4. 单据明细仅在商品、规格文本和租户范围内能唯一匹配时回填SKU关系。
-- 无规格或存在多个计量单位候选的旧明细保留NULL，避免猜测关联对象。
UPDATE jsh_depot_item di
JOIN jsh_material_extend me
  ON me.material_id = di.material_id
  AND me.sku = di.sku
  AND IFNULL(me.tenant_id, 0) = IFNULL(di.tenant_id, 0)
  AND IFNULL(me.delete_Flag, '0') <> '1'
SET di.material_extend_id = me.id
WHERE di.material_extend_id IS NULL
  AND di.sku IS NOT NULL AND di.sku <> ''
  AND NOT EXISTS (
    SELECT 1 FROM jsh_material_extend other_me
    WHERE other_me.material_id = di.material_id
      AND other_me.sku = di.sku
      AND IFNULL(other_me.tenant_id, 0) = IFNULL(di.tenant_id, 0)
      AND IFNULL(other_me.delete_Flag, '0') <> '1'
      AND other_me.id <> me.id
  );

-- 查看仍未关联SKU的历史单据行；这些行因规格缺失或匹配不唯一，需业务核实后再补。
SELECT COUNT(*) AS unlinked_detail_count
FROM jsh_depot_item
WHERE material_extend_id IS NULL AND IFNULL(delete_flag, '0') <> '1';

-- 5. 唯一索引创建前的重复检查；如有返回行，先人工核对并处理，之后再重跑本段索引语句。
SELECT code, COUNT(*) AS row_count
FROM jsh_material
WHERE code IS NOT NULL AND code <> ''
GROUP BY code HAVING COUNT(*) > 1;
SELECT sku_code, COUNT(*) AS row_count
FROM jsh_material_extend
WHERE sku_code IS NOT NULL AND sku_code <> ''
GROUP BY sku_code HAVING COUNT(*) > 1;

-- 6. 建立唯一编码约束和SKU关联查询索引；索引已存在时自动跳过。
SET @ddl = IF(
  (SELECT COUNT(*) FROM (SELECT code FROM jsh_material WHERE code IS NOT NULL AND code<>'' GROUP BY code HAVING COUNT(*)>1) d)=0
  AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='jsh_material' AND index_name='uk_material_spu_code')=0,
  'ALTER TABLE jsh_material ADD UNIQUE INDEX uk_material_spu_code (code)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF(
  (SELECT COUNT(*) FROM (SELECT sku_code FROM jsh_material_extend WHERE sku_code IS NOT NULL AND sku_code<>'' GROUP BY sku_code HAVING COUNT(*)>1) d)=0
  AND (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='jsh_material_extend' AND index_name IN ('uk_material_extend_sku_code','uk_sku_code'))=0,
  'ALTER TABLE jsh_material_extend ADD UNIQUE INDEX uk_material_extend_sku_code (sku_code)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='jsh_material_extend' AND index_name='idx_material_extend_material_status')=0,
  'ALTER TABLE jsh_material_extend ADD INDEX idx_material_extend_material_status (material_id, delete_Flag)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='jsh_depot_item' AND index_name='idx_depot_item_sku')=0,
  'ALTER TABLE jsh_depot_item ADD INDEX idx_depot_item_sku (material_extend_id, material_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='jsh_material_current_stock' AND index_name='idx_current_stock_sku_depot_tenant')=0,
  'ALTER TABLE jsh_material_current_stock ADD INDEX idx_current_stock_sku_depot_tenant (material_extend_id, depot_id, tenant_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='jsh_material_initial_stock' AND index_name='idx_initial_stock_sku_depot_tenant')=0,
  'ALTER TABLE jsh_material_initial_stock ADD INDEX idx_initial_stock_sku_depot_tenant (material_extend_id, depot_id, tenant_id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 说明：不添加物理外键约束，避免影响系统现有逻辑删除、历史单据保留和租户数据隔离方式。
-- 历史库存快照目前按商品汇总，无法可靠拆分到颜色/尺码SKU；因此本脚本不将其猜分到某个SKU。
-- 历史快照中的 material_extend_id 可暂时为空，盘点或后续明确SKU库存后再逐条迁移。
