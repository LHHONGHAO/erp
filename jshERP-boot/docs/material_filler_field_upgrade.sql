-- =====================================================
-- 商品主表新增专属业务字段：填充物
-- 说明：
--   1. 字段名 filler，类型 VARCHAR(100)，位置在 inspection 之后；
--   2. 使用 information_schema 判断列是否存在，可重复执行；
--   3. 历史数据从扩展4（other_field4，前端曾将别名设为「填充物」）回填。
-- =====================================================

SET @ddl = (SELECT IF(
    EXISTS (SELECT 1 FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'jsh_material'
              AND COLUMN_NAME = 'filler'),
    'SELECT ''column filler already exists''',
    'ALTER TABLE `jsh_material` ADD COLUMN `filler` VARCHAR(100) NULL COMMENT ''填充物'' AFTER `inspection`'
));
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 回填历史数据（仅当 filler 为空且扩展4 有值）
UPDATE `jsh_material` SET `filler` = `other_field4`
WHERE `filler` IS NULL AND `other_field4` IS NOT NULL AND `other_field4` != ''
  AND ifnull(`delete_flag`,'0') != '1';
