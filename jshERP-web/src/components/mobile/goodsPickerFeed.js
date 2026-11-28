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

function recentFeedItems(list) {
  return list.map(normalizePickerGoods).filter(function(goods) { return !!goods.name }).map(function(goods) {
    return {
      skuRowKey: goods.key,
      nameKey: goods.name + '\u0000' + goods.spec,
      recentFlag: true,
      name: goods.name,
      imgUrl: goods.imgUrl,
      specs: goods.specLines,
      items: [],
      price: goods.price,
      goods: goods
    }
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

  function requestRows(current, page, pageSize) {
    return getAction('/material/listSku', {
      search: JSON.stringify(filterObj({
        materialParam: current.keyword,
        orderByCategory: current.keyword ? '' : '1',
        mpList: getMpListShort(Vue.ls.get('materialPropertyList'))
      })),
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
        recentFeedItems(results[0]).forEach(function(item) {
          current.recentNames[item.nameKey] = true
          if (current.seen[item.skuRowKey]) return
          current.seen[item.skuRowKey] = true
          list.push(item)
        })
        current.headCount = list.length
      }
      var data = results[1]
      data.rows.forEach(function(row) {
        var item = toGoodsFeedItem(row)
        var nameKey = item.name + '\u0000' + item.goods.spec
        if (current.seen[item.skuRowKey] || (page === 1 && current.recentNames[nameKey])) return
        current.seen[item.skuRowKey] = true
        item.items = []
        list.push(item)
      })
      return { list: list, total: data.total + current.headCount, hasMore: data.rows.length > 0 && page * pageSize < data.total }
    })
  }

  return { load: load, prepareRecent: prepareRecent, invalidate: invalidate }
}
