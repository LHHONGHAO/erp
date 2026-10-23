-- 为已创建的单件商品表增加状态字段；仅在 status 字段尚不存在时执行一次。
ALTER TABLE jsh_material_instance
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'IN_STOCK'
  COMMENT '单件状态：IN_STOCK在库、SOLD已售、SCRAPPED报损、LOST遗失'
  AFTER inbound_time;
