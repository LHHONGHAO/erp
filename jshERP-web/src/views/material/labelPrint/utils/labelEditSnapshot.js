/**
 * 标签编辑快照模块（各表单页进入标签编辑/打印预览时共用）
 *
 * 表单页面 → 标签编辑/打印预览的数据载体：sessionStorage 快照
 * （jsh.labelEdit.snapshot）。各表单页把页面数据提取成 values/rows 后
 * 统一通过 saveLabelEditSnapshot 写入；打印预览（LabelPrintModal）通过
 * readLabelEditSnapshot 统一读取。快照默认结构与各表单页保持一致，
 * 业务字段（type / number / values / rows / hiddenItemKeys 等）由调用方透传。
 */

export const LABEL_EDIT_SNAPSHOT_KEY = 'jsh.labelEdit.snapshot'

/** 标准快照骨架：地址/会员等固定小标签页在写入时显式展开，避免写入函数静默注入 */
export const LABEL_EDIT_SNAPSHOT_DEFAULTS = {
  labelWidth: 320,
  labelHeight: 240,
  selectedAttrs: [],
  attrStyles: {},
  attrLabels: {}
}

/** 读取标签编辑快照：无快照或解析失败返回 null */
export function readLabelEditSnapshot() {
  try {
    return JSON.parse(sessionStorage.getItem(LABEL_EDIT_SNAPSHOT_KEY) || 'null')
  } catch (e) {
    return null
  }
}

/**
 * 写入标签编辑快照
 *
 * @param {Object} payload 快照内容，常用字段：
 *   type 模板类型 / number 单号 / values 业务字段值 / rows 明细行 /
 *   labelWidth / labelHeight / selectedAttrs / attrStyles / attrLabels /
 *   hiddenItemKeys 隐藏的规格键
 * @returns {Object} 实际写入的快照
 */
export function saveLabelEditSnapshot(payload) {
  var snapshot = payload || {}
  try {
    sessionStorage.setItem(LABEL_EDIT_SNAPSHOT_KEY, JSON.stringify(snapshot))
  } catch (e) { /* 存储异常不阻塞打印预览打开 */ }
  return snapshot
}

/** 编辑页字段清单 → 快照 values（key → 空串兜底的字符串值） */
export function valuesFromAttrs(attrs) {
  var values = {}
  ;(Array.isArray(attrs) ? attrs : []).forEach(function(attr) {
    if (attr && attr.key) values[attr.key] = attr.value == null ? '' : attr.value
  })
  return values
}

/**
 * 编辑页「打印」→ 打印预览的快照合并（唯一入口）
 *
 * 编辑页只持有模板结构（业务值/明细行进编辑前已被剥离），打印预览需要的
 * 业务数据来自进入编辑前的业务快照：type 匹配时保留旧快照的
 * number/values/rows，模板结构字段一律以编辑页快照为准（编辑态是最新事实）。
 *
 * @param {Object} templateSnapshot 编辑页当前模板快照（buildTemplateSnapshot 产物）
 * @param {String} templateType     编辑页模板类型（delivery/receipt/label/member/address）
 * @returns {Object} 合并后的打印快照
 */
export function mergeEditSnapshotForPrint(templateSnapshot, templateType) {
  var merged = Object.assign({}, templateSnapshot || {})
  var prev = readLabelEditSnapshot()
  // type 不匹配视为跨业务残留，不合并，避免把别的单据数据带进预览
  if (!prev || (prev.type && templateType && prev.type !== templateType)) return merged
  var businessKeys = ['number', 'values', 'rows']
  businessKeys.forEach(function(key) {
    if (prev[key] !== undefined) merged[key] = prev[key]
  })
  return merged
}
