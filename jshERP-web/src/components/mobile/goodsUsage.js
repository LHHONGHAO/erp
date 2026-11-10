import Vue from 'vue'

/**
 * 商品使用记录全局共享数据模型
 * 所有页面（地址识别、销售开单等）共用同一份记录与次数统计，避免各页面口径不一致。
 * 记录结构：{ key, name, spec, time, raw }
 */
const STORAGE_KEY = 'jsh.goodsUsage'
// 兼容旧版地址识别页的历史数据，首次读取时自动迁移
const LEGACY_KEY = 'jsh.addressRecognize.goodsHistory'
// 最多保留的记录条数，避免本地存储无限增长
const MAX_RECORDS = 500
// 业务页面加入购物车所需的商品字段，避免完整商品对象撑爆本地存储
const RAW_FIELDS = [
  'id', 'meId', 'name', 'standard', 'model', 'commodityDecimal', 'skuCode', 'sku',
  'barCode', 'mBarCode', 'depotId', 'unit', 'stock', 'currentStock', 'imgName',
  'attribute', 'specValues', 'specJson', 'specSummary', 'retailPrice', 'commodityUnit', 'categoryId'
]

function compactRaw(raw) {
  if (!raw) return null
  var out = {}
  RAW_FIELDS.forEach(function(field) {
    if (raw[field] !== undefined) out[field] = raw[field]
  })
  return out
}

function readRecords() {
  var records = null
  try {
    records = JSON.parse(Vue.ls.get(STORAGE_KEY) || 'null')
  } catch (e) {
    records = null
  }
  if (!Array.isArray(records)) {
    records = []
    try {
      var legacy = JSON.parse(Vue.ls.get(LEGACY_KEY) || '[]')
      if (Array.isArray(legacy)) records = legacy
    } catch (e) {
      records = []
    }
    if (records.length > 0) writeRecords(records)
  }
  return records
}

function writeRecords(records) {
  try {
    Vue.ls.set(STORAGE_KEY, JSON.stringify(records.slice(-MAX_RECORDS)))
  } catch (e) {
    /* 本地存储不可用时忽略 */
  }
}

/**
 * 记录一次商品选择
 * @param {{key, name, spec, raw}} goods
 */
export function recordGoodsUsage(goods) {
  if (!goods || !goods.name) return
  var records = readRecords()
  records.push({
    key: goods.key || (goods.name + '_' + (goods.spec || '')),
    name: goods.name,
    spec: goods.spec || '',
    time: Date.now(),
    raw: compactRaw(goods.raw)
  })
  writeRecords(records)
}

/**
 * 统计近 N 天选择次数最多的商品
 * @param {{days?: number, limit?: number, requireRaw?: boolean}} options
 * @returns {Array<{key, name, spec, count, last, raw}>}
 */
export function getRecentGoods(options) {
  var opts = options || {}
  var span = (opts.days || 7) * 24 * 60 * 60 * 1000
  var limit = opts.limit || 20
  var requireRaw = !!opts.requireRaw
  var now = Date.now()
  var map = {}

  readRecords().forEach(function(h) {
    if (!h || !h.name) return
    if (now - (h.time || 0) > span) return
    if (requireRaw && !h.raw) return
    var k = h.key || (h.name + '_' + (h.spec || ''))
    if (!map[k]) {
      map[k] = { key: k, name: h.name, spec: h.spec || '', count: 0, last: 0, raw: null }
    }
    map[k].count++
    map[k].last = Math.max(map[k].last, h.time || 0)
    if (h.raw) map[k].raw = h.raw
  })

  return Object.keys(map).map(function(k) {
    return map[k]
  }).sort(function(a, b) {
    return b.count - a.count || b.last - a.last
  }).slice(0, limit)
}
