import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'

/**
 * 标准商品流卡片数据适配（商品信息页 / 添加商品弹层共用）
 * 卡片高度固定，规格最多两行，信息行固定为「零售价 / 库存」
 */
export function buildSpecLines(record, maxLines) {
  const limit = maxLines || 2
  const lines = []
  String(getSpecDisplay(record) || '').split('\n').forEach(function(line) {
    if (lines.length >= limit) return
    const text = line.trim()
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

export function buildInfoItems(record) {
  return [
    { label: '零售价', value: record.commodityDecimal == null ? '-' : record.commodityDecimal },
    { label: '库存', value: record.stock == null ? '-' : record.stock }
  ]
}

/** 接口行 → 标准卡片数据（skuRowKey 作为虚拟列表唯一键） */
export function toGoodsFeedItem(row) {
  return {
    skuRowKey: row.id + '_' + (row.meId != null ? row.meId : 0),
    raw: row,
    name: row.name || '',
    imgUrl: getRowImgUrl(row),
    specs: buildSpecLines(row),
    items: buildInfoItems(row),
    enableSerialNumber: row.enableSerialNumber,
    enableBatchNumber: row.enableBatchNumber
  }
}
