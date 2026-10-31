import {
  BarcodeFormat,
  BrowserMultiFormatReader,
  ChecksumException,
  DecodeHintType,
  FormatException,
  NotFoundException
} from '@zxing/library'

/**
 * 支持的一维码 / 二维码格式。
 * 不设置长度限制，不要求内容必须为数字，识别结果原样返回。
 */
export const SUPPORTED_FORMATS = [
  BarcodeFormat.CODE_128,
  BarcodeFormat.CODE_39,
  BarcodeFormat.CODE_93,
  BarcodeFormat.CODABAR,
  BarcodeFormat.ITF,
  BarcodeFormat.EAN_13,
  BarcodeFormat.EAN_8,
  BarcodeFormat.UPC_A,
  BarcodeFormat.UPC_E,
  BarcodeFormat.RSS_14,
  BarcodeFormat.RSS_EXPANDED,
  BarcodeFormat.QR_CODE,
  BarcodeFormat.DATA_MATRIX,
  BarcodeFormat.PDF_417,
  BarcodeFormat.AZTEC,
  BarcodeFormat.MAXICODE,
  BarcodeFormat.MICRO_QR_CODE
]

const TWO_D_FORMATS = [
  'QR_CODE',
  'DATA_MATRIX',
  'PDF_417',
  'AZTEC',
  'MAXICODE',
  'MICRO_QR_CODE'
]

export function createHints () {
  const hints = new Map()
  hints.set(DecodeHintType.POSSIBLE_FORMATS, SUPPORTED_FORMATS)
  hints.set(DecodeHintType.TRY_HARDER, true)
  return hints
}

export function formatName (format) {
  return BarcodeFormat[format] || 'UNKNOWN'
}

export function resolveBarcodeType (format) {
  return TWO_D_FORMATS.indexOf(format) >= 0 ? '2D' : '1D'
}

/**
 * 摄像头 + ZXing 多格式扫描器。
 * 摄像头视频只在浏览器本地解码，不上传服务器。
 */
export class ZXingScanner {
  constructor (options = {}) {
    this.onResult = options.onResult || function () {}
    this.onError = options.onError || function () {}
    this.reader = null
    this.videoElement = null
    this.startToken = 0
    this.stopped = true
  }

  /**
   * 启动摄像头并持续识别。
   * @param {HTMLVideoElement} videoElement 视频预览元素
   * @param {Object} options deviceId / facingMode / timeBetweenScans
   */
  async start (videoElement, options = {}) {
    this.stop()
    if (!videoElement) {
      throw new Error('缺少视频预览元素')
    }
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      throw new Error('当前浏览器不支持摄像头调用')
    }
    const token = ++this.startToken
    this.stopped = false
    this.videoElement = videoElement

    const reader = new BrowserMultiFormatReader(createHints(), options.timeBetweenScans || 200)
    this.reader = reader

    const video = {}
    if (options.deviceId) {
      video.deviceId = { exact: options.deviceId }
    } else {
      video.facingMode = { ideal: options.facingMode || 'environment' }
    }
    video.width = { ideal: 1920 }
    video.height = { ideal: 1080 }
    video.frameRate = { ideal: 30 }

    await reader.decodeFromConstraints({ audio: false, video }, videoElement, (result, error) => {
      if (this.stopped || token !== this.startToken) return
      if (result) {
        const content = result.getText()
        const format = formatName(result.getBarcodeFormat())
        if (content) {
          this.onResult({
            content: content,
            format: format,
            type: resolveBarcodeType(format),
            timestamp: Date.now()
          })
        }
        return
      }
      if (!error) return
      // 单帧未识别属于正常情况，不能当作系统故障
      if (error instanceof NotFoundException ||
          error instanceof ChecksumException ||
          error instanceof FormatException) {
        return
      }
      const name = error.constructor ? error.constructor.name : ''
      if (name === 'NotFoundException' || name === 'ChecksumException' || name === 'FormatException') {
        return
      }
      this.onError(error)
    })

    if (this.stopped || token !== this.startToken) {
      reader.reset()
      return
    }
  }

  stop () {
    this.stopped = true
    this.startToken++
    if (this.reader) {
      try {
        this.reader.reset()
      } catch (e) {
        // 释放阶段的异常不影响业务
      }
      this.reader = null
    }
    if (this.videoElement) {
      try {
        this.videoElement.pause()
        this.videoElement.srcObject = null
      } catch (e) {
        // 忽略已销毁元素
      }
      this.videoElement = null
    }
  }

  get stream () {
    return this.reader && this.reader.stream ? this.reader.stream : null
  }

  async listDevices () {
    if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) return []
    try {
      const devices = await navigator.mediaDevices.enumerateDevices()
      return devices.filter(item => item.kind === 'videoinput')
    } catch (e) {
      return []
    }
  }

  async isTorchSupported () {
    const stream = this.stream
    if (!stream) return false
    const track = stream.getVideoTracks()[0]
    if (!track || !track.getCapabilities) return false
    const capabilities = track.getCapabilities()
    return !!(capabilities && capabilities.torch)
  }

  async setTorch (enabled) {
    const stream = this.stream
    if (!stream) return false
    const track = stream.getVideoTracks()[0]
    if (!track || !track.applyConstraints) return false
    try {
      await track.applyConstraints({ advanced: [{ torch: !!enabled }] })
      return true
    } catch (e) {
      return false
    }
  }
}
