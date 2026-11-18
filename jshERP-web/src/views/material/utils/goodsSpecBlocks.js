/**
 * 商品规格块规则（小票 / 标签编辑、打印预览共用）
 *
 * 一个规格块 = 1 个规格名 + N 个规格值，例如：
 *   规格 = 花色：被套 床单 枕套
 *   → [{ name: '花色', values: ['被套', '床单', '枕套'] }]
 *
 * 数据形态：
 * 1. 优先取 spec1 / spec2 / spec3，为空时取合并字段 spec；
 * 2. 字段内容按换行、全角空格拆成段；
 * 3. 段内形如「名称：值」时开启新的规格块，值内用 、，,/／| 连接时拆成多个规格值；
 * 4. 后续不带名称的段（如 床单、枕套）追加到当前规格块；
 * 5. 没有规格名时，值单独成块（name 为空）。
 *
 * 元素 key：
 *   规格名 item{行}_specname{块}
 *   规格值 item{行}_specvalue{块}（第一个值，兼容旧模板）
 *          item{行}_specvalue{块}_{值}（第二个及之后的值）
 */

const SPEC_NAME_RE = /^item(\d+)_specname(\d+)$/
const SPEC_VALUE_RE = /^item(\d+)_specvalue(\d+)(?:_(\d+))?$/
const SPEC_NAME_TEXT_RE = /^([^：:]+)[：:]\s*(.*)$/
const VALUE_SEPARATOR_RE = /[、,，／|]/

/** 规格名元素 key */
export function specNameKey(rowNo, blockNo) {
  return 'item' + rowNo + '_specname' + blockNo
}

/** 规格值元素 key（第一个值不带序号后缀，兼容已保存的模板） */
export function specValueKey(rowNo, blockNo, valueNo) {
  const no = valueNo || 1
  return no > 1
    ? 'item' + rowNo + '_specvalue' + blockNo + '_' + no
    : 'item' + rowNo + '_specvalue' + blockNo
}

/** 解析规格元素 key，非规格元素返回 null */
export function parseSpecItemKey(key) {
  const text = key == null ? '' : String(key)
  let matched = SPEC_NAME_RE.exec(text)
  if (matched) {
    return {
      kind: 'name',
      rowNo: Number(matched[1]),
      blockNo: Number(matched[2]),
      valueNo: 0
    }
  }
  matched = SPEC_VALUE_RE.exec(text)
  if (matched) {
    return {
      kind: 'value',
      rowNo: Number(matched[1]),
      blockNo: Number(matched[2]),
      valueNo: Number(matched[3] || 1)
    }
  }
  return null
}

export function isSpecNameKey(key) {
  const parsed = parseSpecItemKey(key)
  return !!parsed && parsed.kind === 'name'
}

export function isSpecValueKey(key) {
  const parsed = parseSpecItemKey(key)
  return !!parsed && parsed.kind === 'value'
}

export function isSpecItemKey(key) {
  return !!parseSpecItemKey(key)
}

/** 规格名显示文本（带分隔符） */
export function formatSpecName(name) {
  return name ? name + '：' : ''
}

/** 商品行 → 规格段列表 */
export function splitSpecSegments(row) {
  if (!row) return []
  const raw = []
  ;['spec1', 'spec2', 'spec3'].forEach(function(field) {
    const value = row[field] == null ? '' : String(row[field]).trim()
    if (value) raw.push(value)
  })
  if (!raw.length) {
    const spec = row.spec == null ? '' : String(row.spec).trim()
    if (spec) raw.push(spec)
  }
  const segments = []
  raw.forEach(function(text) {
    // 半角/全角空格、换行都视为分段：花色：被套 床单 枕套 → 名称 + 两个无名称的追加值
    String(text).split(/[\s　]+/).forEach(function(part) {
      const value = part.trim()
      if (value) segments.push(value)
    })
  })
  return segments
}

/** 商品行 → 规格块列表 */
export function parseGoodsSpecBlocks(row) {
  const blocks = []
  splitSpecSegments(row).forEach(function(text) {
    const matched = SPEC_NAME_TEXT_RE.exec(text)
    if (matched) {
      const name = matched[1].trim()
      const values = String(matched[2] || '')
        .split(VALUE_SEPARATOR_RE)
        .map(function(value) { return value.trim() })
        .filter(function(value) { return value !== '' })
      const last = blocks[blocks.length - 1]
      if (last && last.name === name) {
        last.values = last.values.concat(values)
      } else {
        blocks.push({ name: name, values: values })
      }
      return
    }
    const last = blocks[blocks.length - 1]
    if (last) {
      last.values.push(text)
    } else {
      blocks.push({ name: '', values: [text] })
    }
  })
  return blocks
}
