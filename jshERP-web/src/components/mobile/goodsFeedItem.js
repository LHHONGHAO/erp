import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'

/**
 * 标准商品流卡片数据适配模块（商品信息页 / 添加商品弹层 / 标签打印共用）
 *
 * 职责：接口行 → 卡片数据的所有换算（规格解析、图片、库存、已选商品对象）
 * 全部在这一层一次性完成，页面拿到即可直接渲染与回填，点击时零计算。
 */

/** 规格展示文本：异常兜底返回空串，调用方无需再包 try/catch */
export function specTextOf(record) {
  try {
    return getSpecDisplay(record) || ''
  } catch (e) {
    return ''
  }
}

/** 卡片唯一键：SKU 主键（缺失时退回名称）+ 类目 */
export function goodsKeyOf(row) {
  const id = (row.id === 0 || row.id) ? row.id : row.name
  return String(id) + '_' + (row.meId || 0)
}

/** 规格文本拆成值列表：去掉「规格名：」，保留行内全部值（默认最多 3 个） */
export function splitSpecValues(specText, maxValues) {
  const limit = maxValues || 3
  const values = []
  String(specText || '').split('\n').forEach(function(line) {
    let text = line.trim()
    if (!text) return
    const idx = text.search(/[：:]/)
    if (idx > -1) text = text.slice(idx + 1).trim()
    if (text) values.push(text)
  })
  return values.slice(0, limit)
}

/** 规格文本 → 每个属性一行，值只取第一个（多值以 / 分隔），默认最多 2 行 */
export function parseSpecLines(specText, maxLines) {
  const limit = maxLines || 2
  const lines = []
  String(specText || '').split('\n').forEach(function(line) {
    if (lines.length >= limit) return
    let text = line.trim()
    if (!text) return
    let label = ''
    let value = text
    const idx = text.search(/[：:]/)
    if (idx > -1) {
      label = text.slice(0, idx).trim()
      value = text.slice(idx + 1).trim()
    }
    value = value.split('/')[0].trim()
    if (!value) return
    lines.push({ label: label, value: value })
  })
  return lines
}

/** 兼容保留：按接口行解析规格行 */
export function buildSpecLines(record, maxLines) {
  return parseSpecLines(specTextOf(record), maxLines)
}

/** 标准信息行：库存 */
export function buildInfoItems(record) {
  return [
    { label: '库存', value: record.stock == null ? '-' : record.stock }
  ]
}

/**
 * 卡片高度：按文字总高计算（与 MobileProductCard 样式一一对应）
 * 上下留白 6px×2 + 名称行 22px + 规格行×N 与信息行各 22px（18px 行高 + 4px 均匀行距）
 * 名称/规格/信息行在卡片内垂直间距均匀分布，图片为正方形跟随该高度
 */
export function cardHeightOf(item) {
  const specLines = (item && item.specs && item.specs.length) || 0
  return 56 + 22 * specLines
}

/** 接口行 → 已选商品标准对象（卡片点击回填 / 最近使用 / 初始选中共用同一结构） */
export function toStandardGoods(row) {
  const spec = specTextOf(row)
  const values = splitSpecValues(spec, 3)
  const material = (row && row.raw) || row || {}
  return {
    key: goodsKeyOf(row),
    name: row.name || '',
    spec: spec,
    spec1: values[0] || '',
    spec2: values[1] || '',
    spec3: values[2] || '',
    // 商品类别：标签打印按类别匹配规格映射表
    categoryId: (row && row.categoryId) || material.categoryId || '',
    // SKU 主键：标签打印按 SKU 读取规格明细
    skuId: (row && row.meId) || material.meId || '',
    imgUrl: getRowImgUrl(row),
    specLines: parseSpecLines(spec, 2),
    price: row.commodityDecimal == null ? '-' : row.commodityDecimal,
    stock: row.stock == null ? '-' : row.stock,
    raw: row
  }
}

/** 接口行 → 标准卡片数据（skuRowKey 作为虚拟列表唯一键）：goods 字段随卡片下发，点击直接用 */
export function toGoodsFeedItem(row) {
  const goods = toStandardGoods(row)
  return {
    skuRowKey: goods.key,
    raw: row,
    name: goods.name,
    imgUrl: goods.imgUrl,
    specs: goods.specLines,
    items: buildInfoItems(row),
    // 零售价：卡片名称行右侧显示
    price: goods.price,
    enableSerialNumber: row.enableSerialNumber,
    enableBatchNumber: row.enableBatchNumber,
    // 已选商品对象：加载时一次算好，点击回填不再二次换算
    goods: goods
  }
}
