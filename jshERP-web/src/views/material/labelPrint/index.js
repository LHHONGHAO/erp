/**
 * 标签预览与打印独立功能入口
 *
 * 页面只通过本入口使用标签打印：传入 record/sku 等业务数据即可，
 * 功能内部不反向引用任何页面文件。
 */
export { default as LabelPrintModal } from './LabelPrintModal'
export { default as LabelPaper } from './LabelPaper'
