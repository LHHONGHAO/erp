-- 完善 SKU/仓库库存查询关系及单件商品当前仓库字段。
-- 已有单件记录按原入库仓库回填当前仓库；不会变更 SPU、SKU 或汇总库存数量。
-- 以下 ALTER 语句在现有数据库中执行一次。

ALTER TABLE jsh_material_instance
  ADD COLUMN current_depot_id BIGINT DEFAULT NULL COMMENT '当前所在仓库ID，在库状态时必填' AFTER inbound_depot_id;

UPDATE jsh_material_instance
SET current_depot_id = inbound_depot_id
WHERE status = 'IN_STOCK' AND current_depot_id IS NULL;

ALTER TABLE jsh_material_extend
  ADD KEY idx_material_extend_tenant_material (tenant_id, material_id, delete_Flag);

ALTER TABLE jsh_material_initial_stock
  ADD KEY idx_initial_tenant_sku_depot (tenant_id, material_extend_id, depot_id, delete_flag);

ALTER TABLE jsh_material_current_stock
  ADD KEY idx_current_tenant_sku_depot (tenant_id, material_extend_id, depot_id, delete_flag);

ALTER TABLE jsh_material_instance
  ADD KEY idx_instance_tenant_sku_status_depot (tenant_id, material_extend_id, status, current_depot_id, delete_flag),
  ADD KEY idx_instance_tenant_inbound_item (tenant_id, inbound_item_id, delete_flag),
  ADD KEY idx_instance_tenant_outbound_item (tenant_id, outbound_item_id, delete_flag);
