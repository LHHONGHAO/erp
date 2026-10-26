/**
 * 商品 / 库存统一列配置
 *
 * 所有引用商品字段的表格（商品信息页、库存页、选商品弹窗、单据明细）都从这里取列定义，
 * 新增字段或调整标题只改这一处，避免各页面各写一份导致显示不一致。
 *
 * 字段说明：
 *  field           接口返回字段名（后端 SQL 必须 select，否则显示为空）
 *  title           表头文字，也是列设置中的名称
 *  render          显示方式，对应 MaterialTable 内置插槽（见 RENDER_SLOTS），不填按文本显示
 *  width           列宽（px）
 *  dimensions      适用的数据粒度：spu=商品(单类) / sku=SKU(单品) / skuDepot=SKU+仓库(库存)
 *  defaultVisible  默认是否显示（列设置中仍可勾选）
 *  alwaysVisible   列设置中不可取消（如操作列）
 */

/** 显示方式 → MaterialTable 内置插槽名 */
export const RENDER_SLOTS = {
  image: 'mtImage',
  spec: 'mtSpec',
  name: 'mtName',
  action: 'mtAction'
}

const ALL = ['spu', 'sku', 'skuDepot']
const SKU = ['sku', 'skuDepot']
const STOCK = ['skuDepot']

export const MATERIAL_COLUMNS = [
  // ---- 图片（sku_spec.image_url / jsh_material_extend.img_url） ----
  { field: 'imgName', title: '图片', width: 120, align: 'center', render: 'image', dimensions: ALL },
  // ---- 商品主表 jsh_material ----
  { field: 'categoryName', title: '产品类型', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'name', title: '商品名称', width: 160, render: 'name', dimensions: ALL, defaultVisible: true },
  { field: 'code', title: 'SPU商品码', width: 150, dimensions: ALL, defaultVisible: true },
  { field: 'mBarCode', title: '商品条码', width: 120, dimensions: ALL, defaultVisible: true },
  { field: 'mfrs', title: '制造商', width: 120, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'brand', title: '品牌', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'mnemonic', title: '助记码', width: 80, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'unit', title: '单位', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'remark', title: '备注', width: 120, ellipsis: true, dimensions: ALL },
  { field: 'weight', title: '基础重量(kg)', width: 100, dimensions: ALL, defaultVisible: true },
  { field: 'fabric', title: '面料', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'safetyCategory', title: '安全类别', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'executiveStandard', title: '执行标准', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'grade', title: '等级', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'inspection', title: '检验', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'filler', title: '填充物', width: 100, ellipsis: true, dimensions: ALL, defaultVisible: true },
  { field: 'position', title: '仓位货架', width: 100, ellipsis: true, dimensions: ALL },
  // ---- SKU 主表 sku ----
  { field: 'skuCode', title: 'SKU编码', width: 170, dimensions: SKU, defaultVisible: true },
  { field: 'skuName', title: '展示名', width: 180, ellipsis: true, dimensions: SKU },
  { field: 'specDisplay', title: '规格显示', width: 220, render: 'spec', dimensions: SKU, defaultVisible: true },
  { field: 'purchaseDecimal', title: '进货价', width: 80, dimensions: SKU, defaultVisible: true },
  { field: 'commodityDecimal', title: '零售价', width: 80, dimensions: SKU, defaultVisible: true },
  { field: 'marketPrice', title: '划线价', width: 80, dimensions: SKU, defaultVisible: true },
  { field: 'skuCreateTime', title: '创建时间', width: 150, dimensions: SKU },
  { field: 'skuUpdateTime', title: '更新时间', width: 150, dimensions: SKU },
  // ---- 库存（jsh_material_current_stock） ----
  { field: 'depotName', title: '仓库', width: 100, dimensions: STOCK },
  { field: 'currentStock', title: '当前库存', width: 90, align: 'center', dimensions: STOCK },
  { field: 'currentUnitPrice', title: '当前单价', width: 90, dimensions: STOCK },
  // ---- 操作列 ----
  { field: 'action', title: '操作', width: 180, align: 'center', render: 'action', dimensions: ALL, defaultVisible: true, alwaysVisible: true, fixed: 'right' }
]

/** 默认显示顺序（商品信息页）：保持与原有默认列顺序一致 */
const DEFAULT_ORDER = [
  'name', 'code', 'skuCode', 'categoryName', 'unit', 'brand', 'mnemonic', 'weight', 'mfrs',
  'fabric', 'safetyCategory', 'executiveStandard', 'grade', 'inspection', 'filler', 'mBarCode',
  'purchaseDecimal', 'commodityDecimal', 'marketPrice', 'depotName', 'currentStock', 'action'
]

/**
 * 按数据粒度构建表格列（a-table 列格式）
 * @param {String} dimension spu | sku | skuDepot，为空表示不过滤
 * @param {Array} columns 列配置，默认 MATERIAL_COLUMNS
 */
export function buildTableColumns(dimension, columns) {
  const source = columns || MATERIAL_COLUMNS
  return source
    .filter(function(col) { return !dimension || (col.dimensions || []).indexOf(dimension) > -1 })
    .map(function(col) {
      const tableCol = {
        title: col.title,
        dataIndex: col.field,
        key: col.field,
        width: col.width
      }
      if (col.align) tableCol.align = col.align
      if (col.ellipsis) tableCol.ellipsis = true
      if (col.fixed) tableCol.fixed = col.fixed
      if (col.render && RENDER_SLOTS[col.render]) {
        tableCol.scopedSlots = { customRender: RENDER_SLOTS[col.render] }
      }
      return tableCol
    })
}

/**
 * 按数据粒度返回默认显示的列 key（field 数组）
 */
export function defaultColumnKeys(dimension, columns) {
  const source = columns || MATERIAL_COLUMNS
  const visible = {}
  source.forEach(function(col) {
    if (col.defaultVisible && (!dimension || (col.dimensions || []).indexOf(dimension) > -1)) {
      visible[col.field] = true
    }
  })
  const ordered = DEFAULT_ORDER.filter(function(key) { return visible[key] })
  // 兜底：DEFAULT_ORDER 未覆盖的默认列追加到末尾
  Object.keys(visible).forEach(function(key) {
    if (ordered.indexOf(key) === -1) ordered.push(key)
  })
  return ordered
}
