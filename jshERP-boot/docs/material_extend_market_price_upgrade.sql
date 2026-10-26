-- 商品SKU明细表补充「划线价」字段
-- 划线价原先只存在于 sku 表，导致商品新增/编辑时录入的划线价无处保存，
-- 商品信息页与库存明细页读取 sku.market_price 时恒为 0。
-- 这里在 jsh_material_extend 中增加同名字段，保存后由后端同步到 sku.market_price。

SET @dbname = DATABASE();
SET @tablename = 'jsh_material_extend';
SET @columnname = 'market_price';

SET @preparedStatement = (SELECT IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = @dbname AND table_name = @tablename AND column_name = @columnname) > 0,
    'SELECT ''market_price 已存在，跳过'' AS msg;',
    'ALTER TABLE jsh_material_extend ADD COLUMN market_price decimal(24,6) NULL DEFAULT NULL COMMENT ''划线价'' AFTER commodity_decimal;'
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 用 sku 表中已有的划线价回填历史数据（sku 表有值时同步到扩展表，便于编辑回显）
UPDATE jsh_material_extend me
INNER JOIN sku s ON s.spu_id = me.material_id AND s.sku_code = me.sku_code AND s.deleted_at IS NULL
SET me.market_price = s.market_price
WHERE s.market_price IS NOT NULL AND (me.market_price IS NULL OR me.market_price = 0);
