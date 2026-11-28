import Vue from 'vue'
import { getAction } from '@/api/manage'
import { getMpListShort, filterObj } from '@/utils/util'
import { getRowImgUrl } from '@/views/material/utils/materialDisplay'
import { toGoodsFeedItem, splitSpecValues, parseSpecLines } from './goodsFeedItem'
import { getRecentGoods, fetchRecentGoods, clearLocalGoodsUsage } from './goodsUsage'

export function normalizePickerGoods(goods) {
  var values = [goods.spec1, goods.spec2, goods.spec3]
  if (!values[0] && !values[1] && !values[2] && goods.spec) values = splitSpecValues(goods.spec, 3)
  return Object.assign({}, goods, {
    key: goods.key || (goods.name + '_' + (goods.spec || '')),
    name: goods.name || '',
    spec: goods.spec || '',
    spec1: values[0] || '',
    spec2: values[1] || '',
    spec3: values[2] || '',
    categoryId: goods.categoryId || (goods.raw && goods.raw.categoryId) || '',
    skuId: goods.skuId || (goods.raw && goods.raw.meId) || '',
    imgUrl: goods.imgUrl || (goods.raw ? getRowImgUrl(goods.raw) : ''),
    specLines: parseSpecLines(goods.spec, 2),
    price: (goods.raw && goods.raw.commodityDecimal != null) ? goods.raw.commodityDecimal : '-',
    stock: (goods.raw && goods.raw.stock != null) ? goods.raw.stock : '-',
    raw: goods.raw || null
  })
}

/** 最近常选也走标准行映射，卡片结构（名称/图片/规格/价格/信息行）与普通商品完全一致 */
function recentFeedItems(list, compact) {
  return list.map(normalizePickerGoods).filter(function(goods) { return !!goods.name }).map(function(goods) {
    var raw = goods.raw || {}
    var stock = raw.stock != null ? raw.stock : (raw.currentStock != null ? raw.currentStock : goods.stock)
    var item = toGoodsFeedItem(Object.assign({}, raw, {
      name: goods.name,
      spec: goods.spec,
      stock: stock
    }))
    if (compact !== false) item.items = []
    item.recentFlag = true
    item.nameKey = item.name + '\u0000' + item.goods.spec
    return item
  })
}

/** 每个 Picker 独立持有会话；过期响应不得写入当前页的去重集合。 */
export function createGoodsPickerFeed(options) {
  var session = null
  var recentVersion = 0
  var recentReady = Promise.resolve([])

  function invalidate(close) {
    session = null
    if (close) {
      recentVersion += 1
      recentReady = Promise.resolve([])
    }
  }

  function prepareRecent(opts) {
    var version = ++recentVersion
    recentReady = new Promise(function(resolve) {
      var settled = false
      function finish(result) {
        if (settled) return
        settled = true
        clearTimeout(timer)
        if (version !== recentVersion) return resolve([])
        if (result.fromServer) clearLocalGoodsUsage()
        resolve(result.list || [])
      }
      var timer = setTimeout(function() {
        finish({ list: getRecentGoods(opts) })
      }, 2500)
      fetchRecentGoods(opts).then(finish, function() {
        finish({ list: getRecentGoods(opts) })
      })
    })
    return recentReady
  }

  /**
   * 标准商品分页请求：页面只提供业务查询条件（getSearch），
   * 关键字与「按类目+规格排序」由本模块统一注入，保证类目/规格锚点始终可用。
   */
  function requestRows(current, page, pageSize) {
    var search = Object.assign({}, options.getSearch ? options.getSearch() : {}, {
      materialParam: current.keyword,
      orderByCategory: current.keyword ? '' : '1'
    })
    if (!search.mpList) search.mpList = getMpListShort(Vue.ls.get('materialPropertyList'))
    return getAction(options.getUrl ? options.getUrl() : (options.url || '/material/listSku'), {
      search: JSON.stringify(filterObj(search)),
      currentPage: page,
      pageSize: pageSize
    }).then(function(res) {
      if (session !== current) return null
      if (!(res && res.code === 200)) {
        var message = (res && res.data && res.data.message) || '商品加载失败'
        if (options.onError) options.onError(message)
        throw new Error(message)
      }
      return { rows: (res.data && res.data.rows) || [], total: Number(res.data && res.data.total) || 0 }
    })
  }

  function load({ page, pageSize }) {
    if (page === 1) {
      session = { keyword: options.getKeyword(), seen: Object.create(null), recentNames: Object.create(null), headCount: 0 }
    }
    var current = session
    if (!current) return Promise.resolve({ list: [], total: 0, hasMore: false })
    var ready = page === 1 && !current.keyword ? recentReady : Promise.resolve([])
    return Promise.all([ready, requestRows(current, page, pageSize)]).then(function(results) {
      if (session !== current || !results[1]) return { list: [], total: 0, hasMore: false }
      var list = []
      if (page === 1) {
        recentFeedItems(results[0], options.compact).forEach(function(item) {
          current.recentNames[item.nameKey] = true
          if (current.seen[item.skuRowKey]) return
          current.seen[item.skuRowKey] = item
          list.push(item)
        })
        current.headCount = list.length
      }
      var data = results[1]
      data.rows.forEach(function(row) {
        var item = toGoodsFeedItem(row)
        var nameKey = item.name + '\u0000' + item.goods.spec
        var pinned = current.seen[item.skuRowKey]
        if (pinned) {
          // 已完成去重：若置顶的最近常选用的是历史快照，这里用最新接口行刷新库存
          if (options.compact === false && typeof pinned === 'object') pinned.items = item.items
          return
        }
        if (page === 1 && current.recentNames[nameKey]) return
        current.seen[item.skuRowKey] = item
        // 选择场景卡片只保留名称/图片/规格/价格，信息行由业务页面按需补充
        if (options.compact !== false) item.items = []
        list.push(item)
      })
      return { list: list, total: data.total + current.headCount, hasMore: data.rows.length > 0 && page * pageSize < data.total }
    })
  }

  return { load: load, prepareRecent: prepareRecent, invalidate: invalidate }
}
