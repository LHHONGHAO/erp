-- 为已创建的单件商品表补充出库记录字段；仅执行一次。
-- 先确认 status 字段已通过 material_instance_status_upgrade.sql 添加。
ALTER TABLE jsh_material_instance
  ADD COLUMN outbound_depot_id BIGINT DEFAULT NULL COMMENT '出库仓库ID' AFTER inbound_time,
  ADD COLUMN outbound_type VARCHAR(20) DEFAULT NULL COMMENT '出库来源：SALE销售、TRANSFER调拨、OTHER其他出库' AFTER outbound_depot_id,
  ADD COLUMN outbound_head_id BIGINT DEFAULT NULL COMMENT '出库单据ID' AFTER outbound_type,
  ADD COLUMN outbound_item_id BIGINT DEFAULT NULL COMMENT '出库单据明细ID' AFTER outbound_head_id,
  ADD COLUMN outbound_time DATETIME DEFAULT NULL COMMENT '出库时间' AFTER outbound_item_id,
  ADD KEY idx_material_instance_outbound_item (outbound_item_id),
  ADD KEY idx_material_instance_status_depot (status, inbound_depot_id);
