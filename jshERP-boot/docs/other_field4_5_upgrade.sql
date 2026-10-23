ALTER TABLE jsh_material ADD COLUMN other_field4 VARCHAR(200) NULL DEFAULT NULL COMMENT '扩展4' AFTER other_field3;
ALTER TABLE jsh_material ADD COLUMN other_field5 VARCHAR(200) NULL DEFAULT NULL COMMENT '扩展5' AFTER other_field4;
-- 如果扩展属性表中不存在扩展4/5默认记录，则插入，以便用户在"商品属性"配置界面可改名
INSERT IGNORE INTO jsh_material_property (id, native_name, another_name, remark, create_time, update_time, tenant_id, delete_flag)
VALUES (4, '扩展4', '扩展4', NULL, NOW(), NOW(), NULL, '0'),
       (5, '扩展5', '扩展5', NULL, NOW(), NOW(), NULL, '0');
