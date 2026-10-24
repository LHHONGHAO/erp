-- ============================================================
-- sku 表补充价格、时间与操作单号字段
-- 执行库：jsh_erp    数据库：MySQL 8.4
-- 说明：tenant_id 已存在，不重复添加
--       划线价字段命名为 market_price（电商通用命名），如需改名请告知
-- ============================================================

ALTER TABLE `sku`
  ADD COLUMN `purchase_decimal`  decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '进货价'        AFTER `spec_summary`,
  ADD COLUMN `commodity_decimal` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '零售价'        AFTER `purchase_decimal`,
  ADD COLUMN `market_price`      decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '划线价'        AFTER `commodity_decimal`,
  ADD COLUMN `create_time`       datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `deleted_at`,
  ADD COLUMN `update_time`       datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`,
  ADD COLUMN `create_serial`     varchar(255)          DEFAULT NULL COMMENT '创建操作单号'     AFTER `update_time`,
  ADD COLUMN `update_serial`     varchar(255)          DEFAULT NULL COMMENT '更新操作单号'     AFTER `create_serial`;

-- 校验
-- SHOW FULL COLUMNS FROM `sku`;
