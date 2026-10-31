/**
 * 扫码状态与识别结果数据结构。
 * 项目为 Vue 2 + JavaScript，使用 JSDoc 描述类型，运行时由扫描服务返回。
 */

export const SCANNER_STATE = Object.freeze({
  INITIAL: 'INITIAL',
  REQUESTING: 'REQUESTING',
  SCANNING: 'SCANNING',
  RECOGNIZING: 'RECOGNIZING',
  SUCCESS: 'SUCCESS',
  SEARCHING: 'SEARCHING',
  NOT_FOUND: 'NOT_FOUND',
  ERROR: 'ERROR',
  STOPPED: 'STOPPED'
})

/**
 * @typedef {Object} BarcodeResult
 * @property {string} content 识别内容，必须保留原始字符串
 * @property {string} format ZXing 返回的编码格式
 * @property {'1D'|'2D'} type 一维码 / 二维码
 * @property {number} timestamp 识别时间戳
 */
