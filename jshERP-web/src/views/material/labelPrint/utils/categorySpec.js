/**
 * 品类规格标准模块（唯一定义，供全部表单复用）
 *
 * 1. 映射表：一级类目名称 → 标签「规格」需要读取的规格名称
 * 2. 归一化元素：类别（label_category）、规格（label_spec）固定 key 与表单定义
 * 3. 取值：类别取一级类目名，规格按映射表读取规格值并用 / 拼接
 *
 * 商品标签、地址打印等表单共用本模块；新增表单直接引入，禁止再写一份映射表。
 */

/** 归一化「类别」元素 key（所有表单共用） */
export const LABEL_CATEGORY_KEY = 'label_category'
/** 归一化「规格」元素 key（所有表单共用） */
export const LABEL_SPEC_KEY = 'label_spec'

/**
 * 品类规格映射表（一级类目 → 标签「规格」需要读取的规格）
 *
 * 键：一级类目名称（与商品类目树一级节点名称一致）
 * 值：该一级类目商品在标签上需要读取的规格名称，按数组顺序用 / 拼接
 *
 * 例：四件套商品读取「颜色」「被套」两个规格值，
 *     打印到规格元素时输出「颜色值/被套值」。
 */
export const CATEGORY_SPEC_MAP = {
  四件套: ['颜色', '被套'],
  三件套: ['颜色', '床单']
}

/** 取一级类目对应的规格名称列表，未配置返回空数组 */
export function resolveCategorySpecNames(categoryName) {
  const name = categoryName == null ? '' : String(categoryName).trim()
  if (!name || !Object.prototype.hasOwnProperty.call(CATEGORY_SPEC_MAP, name)) return []
  const list = CATEGORY_SPEC_MAP[name]
  return Array.isArray(list) ? list.map(function(item) { return String(item).trim() }).filter(Boolean) : []
}

/**
 * 从商品行的规格块中按映射表挑选规格值
 *
 * @param {String} categoryName 一级类目名称
 * @param {Array} blocks 商品行规格块 [{ name, values: [] }]
 * @returns {Array|null} 按映射顺序的规格值；未配置映射或无匹配返回 null（调用方按原逻辑取值）
 */
export function pickMappedSpecValues(categoryName, blocks) {
  const names = resolveCategorySpecNames(categoryName)
  if (!names.length) return null
  const list = Array.isArray(blocks) ? blocks : []
  const picked = []
  names.forEach(function(name) {
    list.forEach(function(block) {
      if (!block) return
      if (String(block.name || '').trim() === name) {
        ;(block.values || []).forEach(function(value) {
          const text = value == null ? '' : String(value).trim()
          if (text) picked.push(text)
        })
      } else if (Array.isArray(block.values) && block.values.map(String).indexOf(name) > -1) {
        // 映射到的是规格值名（如「被套」属于「规格」），直接取该值
        picked.push(name)
      }
    })
  })
  return picked.length ? picked : null
}

/**
 * 类别 + 规格统一取值
 *
 * @param {String} categoryName 一级类目名称
 * @param {Array} blocks 商品行规格块 [{ name, values: [] }]
 * @returns {{categoryName:String, specText:String}|null} 无类目时返回 null
 */
export function resolveCategorySpecText(categoryName, blocks) {
  const name = categoryName == null ? '' : String(categoryName).trim()
  if (!name) return null
  const mapped = pickMappedSpecValues(name, blocks)
  const values = mapped || (Array.isArray(blocks) ? blocks : []).reduce(function(list, block) {
    return list.concat((block && block.values) || [])
  }, [])
  return { categoryName: name, specText: values.join('/') }
}

/**
 * 按模板数据映射表中的「已选规格 / 规格值」挑选规格值
 *
 * 只有模板里被选择的规格值才读取业务数据，未选择的不输出：
 * 1. 商品规格值里直接存在用户选的值 → 直接使用；
 * 2. 用户选的是品类规格配置里的子规格名（如「被套」「床单」「枕套」），
 *    按它在 sourceValues 中的位置读取商品规格值的同一位置；
 * 3. 商品规格值用 / 连接多个子规格（220x240cm/260x270cm/48x74cm）时，先按 / 拆开再按位置取。
 *
 * @param {Array} defs 模板映射表 [{ name, sourceName, values: [已选规格值], sourceValues: [...] }]
 * @param {Array} blocks 商品行规格块 [{ name, values: [] }]
 * @returns {Array} 命中的规格值（按映射顺序去重）
 */
export function pickSpecValuesFromDefs(defs, blocks) {
  const list = Array.isArray(blocks) ? blocks : []
  const picked = []
  const push = function(value) {
    const text = value == null ? '' : String(value).trim()
    if (text && picked.indexOf(text) === -1) picked.push(text)
  }
  const blockValues = function(block) {
    return Array.isArray(block && block.values)
      ? block.values.map(function(item) { return String(item == null ? '' : item).trim() }).filter(Boolean)
      : []
  }
  // 商品规格值可能用 / 连接多个子规格，拆开后与品类配置的规格值顺序一一对应
  const splitParts = function(values) {
    const parts = []
    const unitOnly = /^(?:cm|mm|m|厘米|毫米|米)$/i
    ;(Array.isArray(values) ? values : []).forEach(function(value) {
      String(value).split('/').forEach(function(part) {
        const text = part.trim()
        if (!text) return
        // 数据里尺寸中间的空格会把单位拆成独立片段（200x230c m），合并回上一个尺寸
        if (unitOnly.test(text) && parts.length) {
          parts[parts.length - 1] += text
          return
        }
        parts.push(text)
      })
    })
    return parts
  }
  ;(Array.isArray(defs) ? defs : []).forEach(function(def) {
    if (!def) return
    const selected = Array.isArray(def.values)
      ? def.values.map(function(value) { return String(value == null ? '' : value).trim() }).filter(Boolean)
      : []
    if (!selected.length) return
    const name = String(def.name == null ? '' : def.name).trim()
    const sourceName = String(def.sourceName == null ? '' : def.sourceName).trim()
    // 规格名可能被编辑器重命名：同时按配置名匹配商品行的规格块
    const block = list.filter(function(item) {
      const blockName = item && String(item.name == null ? '' : item.name).trim()
      return !!blockName && (blockName === name || (!!sourceName && blockName === sourceName))
    })[0]
    const actualValues = blockValues(block)
    const actualParts = splitParts(actualValues)
    const sourceValues = (Array.isArray(def.sourceValues) && def.sourceValues.length
      ? def.sourceValues
      : (Array.isArray(def.values) ? def.values : []))
      .map(function(value) { return String(value == null ? '' : value).trim() })
      .filter(Boolean)
    selected.forEach(function(value) {
      // 商品规格值本身就是这个值
      if (actualValues.indexOf(value) > -1) {
        push(value)
        return
      }
      // 选中的是品类配置里的子规格名：按位置读取商品规格值
      const slot = sourceValues.indexOf(value)
      if (slot > -1) {
        // 该规格只有一个子规格时，商品规格值整块都属于它，不需要按位置拆分
        if (sourceValues.length <= 1) {
          if (actualValues.length) push(actualValues.join(''))
          return
        }
        // 商品规格值本身已按子规格拆成多项时按位置取，否则先按 / 拆开再按位置取
        if (actualValues.length === sourceValues.length) push(actualValues[slot])
        else if (actualParts.length > slot) push(actualParts[slot])
        else if (actualValues.length === 1) push(actualValues[0])
        return
      }
      // 选中的是另一个规格名：读取该规格的全部值
      const byName = list.filter(function(item) {
        return item && String(item.name == null ? '' : item.name).trim() === value
      })[0]
      if (byName) blockValues(byName).forEach(push)
    })
  })
  return picked
}

/** 模板已选规格 → 规格文本（/ 拼接） */
export function resolveSpecTextFromDefs(defs, blocks) {
  return pickSpecValuesFromDefs(defs, blocks).join('/')
}

function toText(value) {
  return value == null ? '' : String(value).trim()
}

function uniqueTexts(list) {
  var result = []
  ;(Array.isArray(list) ? list : []).forEach(function(item) {
    var text = toText(item)
    if (text && result.indexOf(text) === -1) result.push(text)
  })
  return result
}

/**
 * 标签规格数据映射表（标准结构，按类别分组）
 *
 * [
 *   {
 *     categoryId: '100', categoryName: '被芯', memberIds: ['100', '101'],
 *     specs: [{ name: '尺寸', values: ['200*230'], sourceName: '尺寸', sourceValues: ['150*200', '200*230'] }]
 *   }
 * ]
 *
 * 语义：该标签从哪个类别读取哪个规格的哪些值；不在表内的类别 / 规格 / 值不加载业务数据。
 */
export function normalizeSpecMapping(input) {
  var groups = []
  var groupMap = {}
  ;(Array.isArray(input) ? input : []).forEach(function(group) {
    if (!group) return
    var categoryId = toText(group.categoryId != null ? group.categoryId : group.id)
    var categoryName = toText(group.categoryName != null ? group.categoryName : group.name)
    if (!categoryId && !categoryName) return
    var key = categoryId || categoryName
    var target = groupMap[key]
    if (!target) {
      target = {
        categoryId: categoryId,
        categoryName: categoryName,
        memberIds: uniqueTexts(group.memberIds || group.categoryMemberIds),
        specs: []
      }
      groupMap[key] = target
      groups.push(target)
    }
    if (!target.categoryName && categoryName) target.categoryName = categoryName
    uniqueTexts(group.memberIds || group.categoryMemberIds).forEach(function(id) {
      if (target.memberIds.indexOf(id) === -1) target.memberIds.push(id)
    })
    ;(Array.isArray(group.specs) ? group.specs : []).forEach(function(spec) {
      if (!spec) return
      var name = toText(spec.name)
      if (!name) return
      var exist = target.specs.filter(function(item) { return item.name === name })[0]
      if (!exist) {
        exist = { name: name, values: [], sourceName: toText(spec.sourceName) || name, sourceValues: [] }
        target.specs.push(exist)
      }
      exist.values = uniqueTexts(exist.values.concat(spec.values))
      exist.sourceValues = uniqueTexts(exist.sourceValues.concat(spec.sourceValues || spec.values))
    })
  })
  return groups.filter(function(group) {
    return group.specs.some(function(spec) { return spec.values.length > 0 })
  })
}

/** 映射表是否选中了任何规格值 */
export function specMappingHasSelection(mapping) {
  return normalizeSpecMapping(mapping).length > 0
}

/**
 * 映射表 → 规格块（打印渲染内部结构，blockNo 按类别内顺序）
 * 每个块带 categoryId / categoryName / categoryMemberIds，供按行类别精确匹配。
 */
export function flattenSpecMapping(mapping) {
  var blocks = []
  normalizeSpecMapping(mapping).forEach(function(group) {
    group.specs.forEach(function(spec, index) {
      blocks.push({
        categoryId: group.categoryId,
        categoryName: group.categoryName,
        categoryMemberIds: group.memberIds.slice(),
        blockNo: index + 1,
        name: spec.name,
        values: spec.values.slice(),
        sourceName: spec.sourceName || spec.name,
        sourceValues: spec.sourceValues.slice()
      })
    })
  })
  return blocks
}

/**
 * 类别 / 规格标准表单元素（编辑页字段、元素面板、打印预览共用）
 * 与 buildFormFieldAttrs 的输出结构一致。
 */
export function buildCategorySpecAttrs() {
  return [
    { key: LABEL_CATEGORY_KEY, label: '类别', value: '类别', isCustom: false, group: '商品规格' },
    { key: LABEL_SPEC_KEY, label: '规格', value: '规格', isCustom: false, group: '商品规格' }
  ]
}

/**
 * 把标准「类别 / 规格」元素并入表单字段清单（同 key 以原清单为准）
 * 各表单配置统一调用本函数，规格卡片与规格映射器保持同一套通用实现。
 */
export function appendCategorySpecAttrs(list) {
  var exists = {}
  var result = []
  ;(list || []).concat(buildCategorySpecAttrs()).forEach(function(attr) {
    if (!attr || !attr.key || exists[attr.key]) return
    exists[attr.key] = true
    result.push(attr)
  })
  return result
}
