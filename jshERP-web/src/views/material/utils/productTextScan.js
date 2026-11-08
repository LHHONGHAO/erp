import { postAction } from '@/api/manage'

/**
 * 商品文字扫描模块
 * 统一封装「图片文字识别 → 文字解析 → 生成商品搜索候选」，
 * 供商品信息页、零售开单页、商品添加页等复用。
 */

/** 识别图片中的文字，返回后端原始响应（rawText 位于 res.data.rawText） */
export function recognizeTextFromImage(imageBase64) {
  return postAction('/ocr/recognizeProductByBase64', { imageBase64: imageBase64 })
}

/** 解析商品文字（复用商品添加页的 parseProductText 规则），返回后端原始响应 */
export function parseProductText(rawText) {
  return postAction('/ocr/parseProductText', { text: rawText })
}

// 品类标题（按长度倒序匹配，避免「类型」抢先匹配「商品类型」）
const CATEGORY_LABELS = ['商品类型', '品类', '分类', '类别', '类型']

/**
 * 从原文中提取商品分类名称。
 * 后端 parseProductText 未映射品类，这里按「品类：xxx」等行格式补充提取。
 */
export function extractCategoryName(rawText) {
  const lines = String(rawText || '').split(/\r?\n/)
  for (let i = 0; i < lines.length; i++) {
    const text = lines[i].trim()
    if (!text) continue
    for (let j = 0; j < CATEGORY_LABELS.length; j++) {
      const label = CATEGORY_LABELS[j]
      if (text.indexOf(label) !== 0) continue
      const rest = text.substring(label.length)
      // 标题后必须紧跟分隔符，避免把正文误当品类标题
      if (!/^[\s:：=＝|｜\-–—、，,.;；。]/.test(rest)) continue
      const value = rest.replace(/^[\s:：=＝|｜\-–—、，,.;；。]+/, '').trim()
      if (value) return value
    }
  }
  return ''
}

/**
 * 生成按优先级排序的商品搜索候选。
 * 单行文字：按品名搜索；
 * 多行文字：条码 → 品名 → 商品分类，空值剔除。
 */
export function buildSearchCandidates(rawText, parsed) {
  const text = String(rawText || '').trim()
  if (!text) return []
  const lines = text.split(/\r?\n/).map(function(line) { return line.trim() }).filter(Boolean)
  if (lines.length <= 1) {
    return lines.length === 1 ? [{ field: 'name', value: lines[0] }] : []
  }
  const data = parsed || {}
  const candidates = []
  const barCode = data.barCode != null ? String(data.barCode).trim() : ''
  if (barCode) candidates.push({ field: 'barCode', value: barCode })
  const name = data.name != null ? String(data.name).trim() : ''
  if (name) candidates.push({ field: 'name', value: name })
  const category = extractCategoryName(text)
  if (category) candidates.push({ field: 'category', value: category })
  return candidates
}

/**
 * 按编码生成规则判定内容类型：
 * 条码 = 13 位纯数字；SPU = SP+2位类型码+-款号；SKU/单件码 = SPU 后再接规格段。
 */
export function classifyCode(content) {
  const value = String(content || '').trim()
  if (!value) return 'unknown'
  if (/^\d{13}$/.test(value)) return 'barCode'
  if (/^SP\d{2}-[A-Za-z0-9]{1,16}$/.test(value)) return 'spu'
  if (/^SP\d{2}-[A-Za-z0-9]{1,16}(-[A-Za-z0-9]+)+$/.test(value)) return 'sku'
  return 'unknown'
}
