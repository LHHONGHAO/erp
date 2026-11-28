export function secondLevelCategories(tree) {
  var list = []
  function walk(nodes) {
    ;(nodes || []).forEach(function(node) {
      if (String(node.categoryLevel) === '2') list.push(node)
      walk(node.children)
    })
  }
  walk(tree)
  return list
}

export function buildCatParentMap(tree) {
  var map = Object.create(null)
  function walk(nodes, anchor) {
    ;(nodes || []).forEach(function(node) {
      var next = String(node.categoryLevel) === '2' ? node : anchor
      if (next) map[String(node.id)] = next
      walk(node.children, next)
    })
  }
  walk(tree, null)
  return map
}

export function itemCategoryId(item, map) {
  var goods = item && !item.recentFlag && item.goods
  var cid = goods && (goods.categoryId || (goods.raw && goods.raw.categoryId))
  var node = cid !== undefined && cid !== null ? map[String(cid)] : null
  return node ? String(node.id) : ''
}

export function matchesCategory(item, id, map) {
  var category = itemCategoryId(item, map)
  return !!category && category === String(id)
}

export function itemSpec(item) {
  var goods = item && !item.recentFlag && item.goods
  var lines = (goods && goods.specLines) || []
  for (var i = 0; i < lines.length; i++) {
    var line = lines[i]
    if (line && (line.label === '尺寸' || line.label === '规格') && line.value) {
      return String(line.value).replace(/\s+/g, '')
    }
  }
  return ''
}

export function matchesSpec(item, text, zone, map) {
  if (!item || item.recentFlag || !item.goods) return false
  if (zone && !matchesCategory(item, zone, map)) return false
  return (item.goods.specLines || []).some(function(line) {
    return line && (line.label === '尺寸' || line.label === '规格') &&
      String(line.value || '').replace(/\s+/g, '') === text
  })
}

export function normalizeFavRows(rows, limit) {
  var counts = Object.create(null)
  var order = []
  ;(rows || []).forEach(function(row) {
    var text = row && row.valueText ? String(row.valueText).split('/')[0] : ''
    text = text.replace(/\s+/g, '')
    if (!text) return
    if (!Object.prototype.hasOwnProperty.call(counts, text)) {
      counts[text] = 0
      order.push(text)
    }
    counts[text] += Number(row.valueCount) || 0
  })
  return order.map(function(text) { return { text: text, count: counts[text] } })
    .sort(function(a, b) { return b.count - a.count || (a.text < b.text ? -1 : 1) })
    .slice(0, limit || 4)
}
