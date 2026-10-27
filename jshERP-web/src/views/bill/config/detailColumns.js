import { FormTypes } from '@/utils/JEditableTableUtil'

/**
 * 单据明细（商品录入表格）列定义 —— 唯一事实来源
 *
 * 用法：
 *   import { buildDetailColumns, DETAIL_DEFAULT_COLUMNS } from '@/views/bill/config/detailColumns'
 *   data() { return { defColumns: buildDetailColumns(), defDataIndex: DETAIL_DEFAULT_COLUMNS } }
 *
 * 字段说明：
 *   key            数据字段名（与明细行数据一致）
 *   title          列标题
 *   width          列宽，统一使用像素（DataTable 只识别数字与 px，百分比会被判为 0）
 *   type           编辑类型，缺省为只读文本；FormTypes.hidden 的列只参与提交、不出现在表格中
 *   statistics     是否参与合计行与金额统计
 *   validateRules  提交校验规则
 */
export const DETAIL_COLUMNS = [
  { key: 'imgName', title: '图片', width: 90 },
  { key: 'name', title: '商品名称', width: 180 },
  { key: 'skuCode', title: 'SKU商品码', width: 170 },
  { key: 'categoryName', title: '产品类型', width: 110 },
  { key: 'unit', title: '单位', width: 70 },
  { key: 'fabric', title: '面料', width: 100 },
  { key: 'specDisplay', title: '规格', width: 220 },
  { key: 'filler', title: '填充物', width: 100 },
  { key: 'purchaseDecimal', title: '进货价', width: 80 },
  { key: 'commodityDecimal', title: '零售价', width: 80 },
  { key: 'marketPrice', title: '划线价', width: 80 },
  {
    key: 'operNumber',
    title: '数量',
    width: 80,
    type: FormTypes.inputNumber,
    statistics: true,
    validateRules: [{ required: true, message: '${title}不能为空' }]
  },
  {
    key: 'depotId',
    title: '仓库',
    width: 120,
    type: FormTypes.select,
    placeholder: '请选择${title}',
    options: [],
    allowSearch: true,
    validateRules: [{ required: true, message: '${title}不能为空' }]
  },
  // ---- 隐藏列：只参与数据提交/金额计算，不在表格中显示 ----
  { key: 'unitPrice', title: '单价', width: 80, type: FormTypes.hidden },
  { key: 'allPrice', title: '金额', width: 90, type: FormTypes.hidden, statistics: true },
  { key: 'linkId', title: '关联id', width: 80, type: FormTypes.hidden }
]

/** 默认显示的列（隐藏列除外，用于列设置的初始勾选） */
export const DETAIL_DEFAULT_COLUMNS = DETAIL_COLUMNS
  .filter(col => col.type !== FormTypes.hidden)
  .map(col => col.key)

/** 需要合计的字段（用于表格合计行） */
export const DETAIL_STATISTICS_KEYS = DETAIL_COLUMNS
  .filter(col => col.statistics)
  .map(col => col.key)

/**
 * 生成列定义副本（每个弹窗实例独立，避免实例间相互影响）
 */
export function buildDetailColumns() {
  return DETAIL_COLUMNS.map(col => Object.assign({}, col))
}

/**
 * 零售/销售类明细：标准商品列 + 条码、序列号、批号、有效期、库存、单价/金额/备注
 * 与入库类明细共用同一套字段命名与显示方式
 */
export const RETAIL_OUT_COLUMNS = [
  {
    key: 'depotId',
    title: '仓库',
    width: 120,
    type: FormTypes.select,
    placeholder: '请选择${title}',
    options: [],
    allowSearch: true,
    validateRules: [{ required: true, message: '${title}不能为空' }]
  },
  {
    key: 'barCode',
    title: '条码',
    width: 160,
    type: FormTypes.popupJsh,
    kind: 'material',
    multi: true,
    validateRules: [{ required: true, message: '${title}不能为空' }]
  },
  { key: 'imgName', title: '图片', width: 90 },
  { key: 'name', title: '商品名称', width: 180 },
  { key: 'skuCode', title: 'SKU商品码', width: 170 },
  { key: 'categoryName', title: '产品类型', width: 110 },
  { key: 'unit', title: '单位', width: 70 },
  { key: 'fabric', title: '面料', width: 100 },
  { key: 'specDisplay', title: '规格', width: 220 },
  { key: 'filler', title: '填充物', width: 100 },
  { key: 'stock', title: '库存', width: 80 },
  { key: 'purchaseDecimal', title: '进货价', width: 80 },
  { key: 'commodityDecimal', title: '零售价', width: 80 },
  { key: 'marketPrice', title: '划线价', width: 80 },
  { key: 'snList', title: '序列号', width: 160, type: FormTypes.popupJsh, kind: 'sn', multi: true },
  { key: 'batchNumber', title: '批号', width: 120, type: FormTypes.popupJsh, kind: 'batch', multi: false },
  { key: 'expirationDate', title: '有效期', width: 120, type: FormTypes.input, readonly: true },
  {
    key: 'operNumber',
    title: '数量',
    width: 80,
    type: FormTypes.inputNumber,
    statistics: true,
    validateRules: [{ required: true, message: '${title}不能为空' }]
  },
  { key: 'unitPrice', title: '单价', width: 80, type: FormTypes.inputNumber },
  { key: 'allPrice', title: '金额', width: 90, type: FormTypes.inputNumber, statistics: true },
  { key: 'remark', title: '备注', width: 120, type: FormTypes.input }
]

/** 零售/销售类明细默认显示的列 */
export const RETAIL_OUT_DEFAULT_COLUMNS = RETAIL_OUT_COLUMNS.map(col => col.key)

/** 生成零售/销售类明细列定义副本 */
export function buildRetailOutColumns() {
  return RETAIL_OUT_COLUMNS.map(col => Object.assign({}, col))
}
