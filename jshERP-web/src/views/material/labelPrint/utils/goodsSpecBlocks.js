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
const VALUE_SEPARATOR_RE = /[、,，／/|]/
// 尺寸表达式：数字 × 数字，乘号支持 * x X ×，可带 cm/mm/m/厘米/毫米/米
const SIZE_TAIL_RE = /[\s]*\d+(?:\.\d+)?\s*[*xX×]\s*\d+(?:\.\d+)?\s*(?:cm|mm|m|厘米|毫米|米)?$/i
const SIZE_ONLY_RE = /^\d+(?:\.\d+)?\s*[*xX×]\s*\d+(?:\.\d+)?\s*(?:cm|mm|m|厘米|毫米|米)?$/i

/** 规格名元素 key */
export function specNameKey(rowNo, blockNo, categoryId, blockIdentity) {
  if (categoryId != null && categoryId !== '') {
    const identity = blockIdentity == null ? '' : '_n' + encodeURIComponent(String(blockIdentity))
    return 'item' + rowNo + '_specname_c' + encodeURIComponent(String(categoryId)) + '_b' + blockNo + identity
  }
  return 'item' + rowNo + '_specname' + blockNo
}

/** 规格值元素 key（第一个值不带序号后缀，兼容已保存的模板） */
export function specValueKey(rowNo, blockNo, valueNo, categoryId, blockIdentity, valueIdentity) {
  const no = valueNo || 1
  if (categoryId != null && categoryId !== '') {
    const blockPart = blockIdentity == null ? '' : '_n' + encodeURIComponent(String(blockIdentity))
    const valuePart = valueIdentity == null ? '' : '_i' + encodeURIComponent(String(valueIdentity))
    return 'item' + rowNo + '_specvalue_c' + encodeURIComponent(String(categoryId)) + '_b' + blockNo + blockPart + '_v' + no + valuePart
  }
  return no > 1
    ? 'item' + rowNo + '_specvalue' + blockNo + '_' + no
    : 'item' + rowNo + '_specvalue' + blockNo
}

/** 解析规格元素 key，非规格元素返回 null */
export function parseSpecItemKey(key) {
  const text = key == null ? '' : String(key)
  let matched = /^item(\d+)_specname_c(.+)_b(\d+?)(?:_n(.*))?$/.exec(text)
  if (matched) {
    return { kind: 'name', rowNo: Number(matched[1]), categoryId: decodeURIComponent(matched[2]), blockNo: Number(matched[3]), blockIdentity: matched[4] ? decodeURIComponent(matched[4]) : null, valueNo: 0 }
  }
  matched = /^item(\d+)_specvalue_c(.+)_b(\d+?)(?:_n(.*?))?_v(\d+)(?:_i(.*))?$/.exec(text)
  if (matched) {
    return { kind: 'value', rowNo: Number(matched[1]), categoryId: decodeURIComponent(matched[2]), blockNo: Number(matched[3]), blockIdentity: matched[4] ? decodeURIComponent(matched[4]) : null, valueNo: Number(matched[5]), valueIdentity: matched[6] ? decodeURIComponent(matched[6]) : null }
  }
  matched = SPEC_NAME_RE.exec(text)
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

/**
 * 去掉规格文本末尾的尺寸描述（只服务编辑态，打印仍用原值）
 *   被套 200*230cm / 床单245*250 / 枕套 48×74cm / 被套 200x230CM → 被套 / 床单 / 枕套
 * 纯尺寸段（如 200*230cm）返回空串，由调用方丢弃。
 */
export function stripSpecSize(text) {
  const value = text == null ? '' : String(text).trim()
  if (!value) return ''
  if (SIZE_ONLY_RE.test(value)) return ''
  return value.replace(SIZE_TAIL_RE, '').trim()
}

/** 规格块结构归一化：[{ name, values: [] }]，过滤空值 */
export function normalizeSpecBlocks(blocks) {
  const list = Array.isArray(blocks) ? blocks : []
  return list.map(function(block) {
    const name = block && block.name != null ? String(block.name).replace(/：$/, '').trim() : ''
    const values = (block && Array.isArray(block.values) ? block.values : [])
      .map(function(value) { return value == null ? '' : String(value).trim() })
    const normalized = { name: name, values: values.filter(function(value) { return value !== '' }) }
    if (block && block.categoryId != null) normalized.categoryId = String(block.categoryId)
    if (block && block.categoryName) normalized.categoryName = String(block.categoryName)
    if (block && Array.isArray(block.categoryMemberIds)) normalized.categoryMemberIds = block.categoryMemberIds.map(function(id) { return String(id) })
    if (block && block.blockNo != null) normalized.blockNo = Number(block.blockNo)
    if (block && block.sourceName) normalized.sourceName = String(block.sourceName)
    if (block && Array.isArray(block.sourceValues)) normalized.sourceValues = block.sourceValues.map(function(value) { return String(value == null ? '' : value) })
    return normalized
  }).filter(function(block) { return block.name !== '' || block.values.length > 0 })
}

/**
 * 编辑页占位文本
 *
 * 编辑页只显示属性名（含表单标题），不展示表单里的业务值；
 * 业务值在打印预览/打印时按打印规则填充。
 * 自定义标签没有业务数据，直接显示其自身文案。
 */
export function editPlaceholderLabel(label, attr) {
  const name = label == null ? '' : String(label)
  if (!name && attr && attr.isCustom) {
    return attr.value == null ? '' : String(attr.value)
  }
  return name
}

/**
 * 编辑页重复属性折叠
 *
 * 会按单据条数重复出现的属性（如商品明细），编辑页只保留第一组用于排版；
 * 打印时再按打印规则展开全部行。
 *
 * @param {Array} rows 明细行
 * @param {Function} [hasValue] 判断行是否有内容
 * @returns {Array} 最多一行的数组
 */
export function firstRowForEdit(rows, hasValue) {
  const list = Array.isArray(rows) ? rows : []
  const valid = typeof hasValue === 'function' ? list.filter(hasValue) : list
  return valid.length ? [valid[0]] : []
}

/**
 * 商品行 → 规格块列表
 *
 * @param {Object} row 商品行
 * @param {Object} [options] { stripSize: true } 仅编辑态使用：去掉尺寸描述，只留属性名
 */
export function parseGoodsSpecBlocks(row, options) {
  const stripSize = !!(options && options.stripSize)
  const blocks = []
  splitSpecSegments(row).forEach(function(text) {
    const segment = stripSize ? stripSpecSize(text) : text
    // 编辑态：纯尺寸段（200*230cm 等）不进入规格块
    if (!segment) return
    const matched = SPEC_NAME_TEXT_RE.exec(segment)
    if (matched) {
      const name = matched[1].trim()
      const values = String(matched[2] || '')
        .split(VALUE_SEPARATOR_RE)
        .map(function(value) { return stripSize ? stripSpecSize(value) : value.trim() })
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
      last.values.push(segment)
    } else {
      blocks.push({ name: '', values: [segment] })
    }
  })
  return blocks
}

/**
 * SKU 规格 JSON（sku.spec_json）→ 规格块
 *
 * 两种形态都支持：
 * 1. [{ name: '花色', value: '浅灰' }, { name: '尺寸', value: '200x230cm' }]
 * 2. [{ attributeName: '花色', childNames: ['颜色'], subValues: { 颜色: '浅灰' } }]
 */
function specBlocksFromJson(source) {
  let list = source
  if (typeof list === 'string') {
    try {
      list = JSON.parse(list)
    } catch (e) {
      return []
    }
  }
  if (!Array.isArray(list)) return []
  const blocks = []
  list.forEach(function(item) {
    if (!item || typeof item !== 'object') return
    const childNames = Array.isArray(item.childNames) ? item.childNames : []
    const subValues = item.subValues || {}
    if (childNames.length) {
      childNames.forEach(function(child) {
        const name = String(child == null ? '' : child).trim()
        const value = subValues[child]
        const text = value == null ? '' : String(value).trim()
        if (name && text) blocks.push({ name: name, values: [text] })
      })
      return
    }
    const name = String(item.name || item.attributeName || '').trim()
    const value = item.value != null ? item.value : item.valueText
    const text = value == null ? '' : String(value).trim()
    if (!name || !text) return
    blocks.push({
      name: name,
      values: text.split(VALUE_SEPARATOR_RE).map(function(part) { return part.trim() }).filter(function(part) { return part !== '' })
    })
  })
  return blocks
}

/**
 * 商品行 → 规格块（打印取值专用）
 *
 * 优先使用 SKU 的结构化规格数据（sku.spec_json / specValues），
 * 没有时才退回规格显示文本，避免「行里只有文本、文本为空就取不到规格」。
 */
export function specBlocksFromRow(row) {
  if (!row) return []
  const raw = row.raw || null
  // 商品流里 raw 有时还嵌一层原始行数据
  const holder = (raw && raw.raw) || raw
  const sources = [
    row.specJson,
    holder && holder.specJson,
    holder && holder.spec_json,
    holder && holder.specValues
  ]
  for (let i = 0; i < sources.length; i++) {
    const blocks = specBlocksFromJson(sources[i])
    if (blocks.length) return blocks
  }
  return parseGoodsSpecBlocks(row)
}

/**
 * 去掉规格块内的尺寸描述（编辑态回填规则）
 *
 * 模板只保存属性名 / 属性值名，尺寸属于单据业务描述，不进入编辑数据。
 */
export function stripSpecBlockSizes(blocks) {
  return normalizeSpecBlocks(blocks).map(function(block) {
    const item = {
      name: stripSpecSize(block.name),
      values: block.values.map(stripSpecSize).filter(function(value) { return value !== '' })
    }
    if (block.categoryId != null) item.categoryId = block.categoryId
    if (block.categoryName) item.categoryName = block.categoryName
    if (block.categoryMemberIds) item.categoryMemberIds = block.categoryMemberIds.slice()
    if (block.blockNo != null) item.blockNo = block.blockNo
    if (block.sourceName) item.sourceName = block.sourceName
    if (block.sourceValues) item.sourceValues = block.sourceValues.slice()
    return item
  }).filter(function(block) { return block.name !== '' || block.values.length > 0 })
}

/**
 * 商品品类设置的规格配置 → 规格块
 *
 * 品类保存的 specConfig 为 [{ name, enableImage, childNames }]（兼容 JSON 字符串），
 * 规格块只取规格名与规格值名，编辑态不关心图片开关。
 */
export function buildSpecBlocksFromCategory(specConfig) {
  let config = specConfig
  if (typeof config === 'string') {
    try {
      config = JSON.parse(config)
    } catch (e) {
      config = null
    }
  }
  if (!Array.isArray(config)) return []
  const blocks = []
  config.forEach(function(item) {
    let name = ''
    let childNames = []
    if (typeof item === 'string') {
      name = item.trim()
    } else if (item && typeof item === 'object') {
      name = String(item.name || '').trim()
      childNames = Array.isArray(item.childNames) ? item.childNames : []
    }
    const values = childNames.map(function(value) {
      return String(value == null ? '' : value).trim()
    }).filter(function(value) { return value !== '' })
    if (!name && !values.length) return
    blocks.push({ name: name, values: values })
  })
  return blocks
}

/**
 * 模板编辑页数据回填规则（编辑态专用，打印不调用）
 *
 * 1. 已保存且非通用占位的 specBlocks 是模板结构，保留用户编辑过的规格名/子属性名；
 * 2. 无已保存规格块，或只有“规格值1/规格值”一类旧占位时，以商品品类 specConfig 初始化；
 * 3. 品类无配置时保留有效的模板规格块；不从单据行的实际规格值推导编辑属性；
 * 4. 尺寸等商品业务值不进入编辑模板。
 *
 * 编辑页只编辑模板结构，不回填单据业务值；打印时仍按真实单据值展开。
 *
 * @param {Array} savedBlocks 模板保存的规格块
 * @param {Array} [categoryBlocks] 商品品类设置的规格块
 * @returns {Array} [{ name, values: [] }]
 */
export function resolveEditSpecBlocks(savedBlocks, categoryBlocks) {
  const saved = stripSpecBlockSizes(savedBlocks)
  const category = normalizeSpecBlocks(categoryBlocks)
  const isGeneric = function(text) {
    return /^(?:规格(?:值)?\s*\d*|规格)$/i.test(String(text || '').trim())
  }
  const onlyGenericPlaceholders = saved.length > 0 && saved.every(function(block) {
    return isGeneric(block.name) && block.values.every(isGeneric)
  })
  if (saved.length && !onlyGenericPlaceholders) return saved
  if (category.length) return category
  return saved
}
