/**
 * 识别结果有效性判断。
 * 只有 EAN/UPC 使用标准校验位；其他编码只要解码成功即视为有效，
 * 不按商品条码长度或纯数字规则拒绝内容。
 */

const TWO_D_FORMATS = [
  'QR_CODE',
  'DATA_MATRIX',
  'PDF_417',
  'AZTEC',
  'MAXICODE',
  'MICRO_QR_CODE'
]

export function getBarcodeType (format) {
  return TWO_D_FORMATS.indexOf(format) >= 0 ? '2D' : '1D'
}

function isDigits (content) {
  return /^\d+$/.test(content)
}

function eanCheckDigitValid (content, length) {
  if (!isDigits(content) || content.length !== length) return false
  let sum = 0
  for (let i = 0; i < content.length - 1; i++) {
    const weight = (content.length - i) % 2 === 0 ? 3 : 1
    sum += Number(content.charAt(i)) * weight
  }
  const check = (10 - (sum % 10)) % 10
  return check === Number(content.charAt(content.length - 1))
}

// UPC-E 展开为 UPC-A 后校验
function upcECheckDigitValid (content) {
  if (!isDigits(content) || content.length !== 8) return false
  const numberSystem = content.charAt(0)
  const payload = content.substring(1, 7)
  const check = Number(content.charAt(7))
  const s1 = payload.charAt(0)
  const s2 = payload.charAt(1)
  const s3 = payload.charAt(2)
  const s4 = payload.charAt(3)
  const s5 = payload.charAt(4)
  const s6 = payload.charAt(5)
  let manufacturer = ''
  let product = ''
  if (s6 === '0' || s6 === '1' || s6 === '2') {
    manufacturer = s1 + s2 + s6 + '00'
    product = '00' + s3 + s4 + s5
  } else if (s6 === '3') {
    manufacturer = s1 + s2 + s3 + '00'
    product = '000' + s4 + s5
  } else if (s6 === '4') {
    manufacturer = s1 + s2 + s3 + s4 + '0'
    product = '0000' + s5
  } else {
    manufacturer = s1 + s2 + s3 + s4 + s5
    product = '0000' + s6
  }
  const upcA = numberSystem + manufacturer + product
  let sum = 0
  for (let i = 0; i < upcA.length; i++) {
    const weight = i % 2 === 0 ? 3 : 1
    sum += Number(upcA.charAt(i)) * weight
  }
  return ((10 - (sum % 10)) % 10) === check
}

/**
 * 按编码格式判断识别内容是否有效。
 * @returns {{valid: boolean, reason: string}}
 */
export function validateBarcode (content, format) {
  if (!content || !String(content).length) {
    return { valid: false, reason: 'EMPTY' }
  }
  if (format === 'EAN_13') {
    return eanCheckDigitValid(content, 13)
      ? { valid: true, reason: 'OK' }
      : { valid: false, reason: 'EAN_13_CHECKSUM' }
  }
  if (format === 'EAN_8') {
    return eanCheckDigitValid(content, 8)
      ? { valid: true, reason: 'OK' }
      : { valid: false, reason: 'EAN_8_CHECKSUM' }
  }
  if (format === 'UPC_A') {
    return eanCheckDigitValid(content, 12)
      ? { valid: true, reason: 'OK' }
      : { valid: false, reason: 'UPC_A_CHECKSUM' }
  }
  if (format === 'UPC_E') {
    return upcECheckDigitValid(content)
      ? { valid: true, reason: 'OK' }
      : { valid: false, reason: 'UPC_E_CHECKSUM' }
  }
  // CODE_128 / CODE_39 / CODE_93 / CODABAR / ITF / RSS / 二维码等
  // 解码器成功返回内容即有效，内容原样交给业务搜索接口判断
  return { valid: true, reason: 'OK' }
}
