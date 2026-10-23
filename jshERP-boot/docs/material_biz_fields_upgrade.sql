-- 商品主表新增专属业务字段：面料/安全类别/执行标准/等级/检验
-- 保留原扩展列 other_field1~7，仅新增列并回填历史数据（可重复执行）

ALTER TABLE `jsh_material` ADD COLUMN `fabric` VARCHAR(100) NULL COMMENT '面料' AFTER `other_field7`;
ALTER TABLE `jsh_material` ADD COLUMN `safety_category` VARCHAR(100) NULL COMMENT '安全类别' AFTER `fabric`;
ALTER TABLE `jsh_material` ADD COLUMN `executive_standard` VARCHAR(100) NULL COMMENT '执行标准' AFTER `safety_category`;
ALTER TABLE `jsh_material` ADD COLUMN `grade` VARCHAR(100) NULL COMMENT '等级' AFTER `executive_standard`;
ALTER TABLE `jsh_material` ADD COLUMN `inspection` VARCHAR(100) NULL COMMENT '检验' AFTER `grade`;

-- 回填历史数据（仅当新列为空且旧扩展列有值）
UPDATE `jsh_material` SET `fabric` = `other_field1`
WHERE `fabric` IS NULL AND `other_field1` IS NOT NULL AND ifnull(`delete_flag`,'0') != '1';

UPDATE `jsh_material` SET `safety_category` = `other_field2`
WHERE `safety_category` IS NULL AND `other_field2` IS NOT NULL AND ifnull(`delete_flag`,'0') != '1';

UPDATE `jsh_material` SET `executive_standard` = `other_field3`
WHERE `executive_standard` IS NULL AND `other_field3` IS NOT NULL AND ifnull(`delete_flag`,'0') != '1';

UPDATE `jsh_material` SET `grade` = `other_field6`
WHERE `grade` IS NULL AND `other_field6` IS NOT NULL AND ifnull(`delete_flag`,'0') != '1';

UPDATE `jsh_material` SET `inspection` = `other_field7`
WHERE `inspection` IS NULL AND `other_field7` IS NOT NULL AND ifnull(`delete_flag`,'0') != '1';
