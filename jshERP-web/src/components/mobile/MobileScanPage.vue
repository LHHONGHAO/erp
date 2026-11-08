<template>
  <div v-if="visible" class="msp-scan-page">
    <div class="msp-scan-stage">
      <video ref="scanVideo" class="msp-scan-video" autoplay playsinline muted></video>
      <!-- 取景框 + 扫描线 -->
      <div class="msp-scan-guide" :style="guideStyle">
        <span class="msp-scan-line"></span>
      </div>
    </div>
    <div class="msp-scan-header">
      <a-icon type="close" class="msp-scan-close" @click="close" />
      <span class="msp-scan-title">扫码</span>
    </div>
    <div class="msp-scan-tip" :class="{ 'msp-scan-tip-error': hasError }" :style="tipStyle">{{ tipText }}</div>
    <div class="msp-scan-footer">
      <button class="msp-scan-btn" :disabled="!cameraReady || busy" @click="captureText">扫描文字</button>
      <button class="msp-scan-btn" :disabled="!cameraReady || busy" @click="captureBarcode">扫描条码</button>
    </div>
  </div>
</template>

<script>
import { postAction } from '@/api/manage'

/**
 * 手机端条码扫描页（全屏）：不自动解码，由用户点击「条码扫描 / 文字识别」按钮触发，
 * 识别结果通过 result 事件抛出，由调用页处理（如回填搜索框并搜索）。
 */
export default {
  name: 'MobileScanPage',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      stream: null,
      cameraReady: false,
      barcodeLoading: false,
      ocrLoading: false,
      tipText: '将条码或文字放入框内，点击下方按钮识别',
      hasError: false,
      /** 取景框位置尺寸（与裁剪区域保持一致） */
      guideStyle: {},
      /** 提示文字位置（跟随取景框上移，位于取景框上方） */
      tipStyle: {}
    }
  },
  computed: {
    busy() {
      return this.barcodeLoading || this.ocrLoading
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.$nextTick(() => this.startCamera())
      } else {
        this.stopCamera()
      }
    }
  },
  beforeDestroy() {
    this.stopCamera()
  },
  methods: {
    // 是否具备网页摄像头调用条件（必须 HTTPS 安全连接且浏览器支持 getUserMedia）
    cameraAvailable() {
      return !!(window.isSecureContext && navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
    },
    startCamera() {
      if (!this.cameraAvailable()) {
        this.hasError = true
        this.tipText = '当前页面不是 HTTPS 安全连接，无法使用摄像头，请通过 https://主机:9999 访问'
        return
      }
      const video = this.$refs.scanVideo
      if (!video) return
      this.stopCamera()
      this.hasError = false
      this.tipText = '将条码或文字放入框内，点击下方按钮识别'
      // 固定使用后置摄像头
      const constraints = {
        audio: false,
        video: {
          width: { ideal: 1920 },
          height: { ideal: 1080 },
          facingMode: { ideal: 'environment' }
        }
      }
      navigator.mediaDevices.getUserMedia(constraints).then(stream => {
        if (!this.visible) {
          this.stopStream(stream)
          return
        }
        this.stream = stream
        video.srcObject = stream
        video.setAttribute('playsinline', 'true')
        return video.play().then(() => {
          this.cameraReady = true
          this.updateGuide()
          window.addEventListener('resize', this.updateGuide)
        })
      }).catch(err => {
        this.cameraReady = false
        this.hasError = true
        this.tipText = '无法访问摄像头：' + (err && err.message ? err.message : '请检查摄像头权限')
      })
    },
    stopCamera() {
      window.removeEventListener('resize', this.updateGuide)
      if (this.stream) {
        this.stopStream(this.stream)
        this.stream = null
      }
      const video = this.$refs.scanVideo
      if (video) {
        video.srcObject = null
        try {
          video.pause()
        } catch (e) {}
      }
      this.cameraReady = false
      this.barcodeLoading = false
      this.ocrLoading = false
    },
    stopStream(stream) {
      if (stream) {
        stream.getTracks().forEach(track => track.stop())
      }
    },
    // 取景框：在居中框基础上加宽 1/3、高度缩为 1/3，并整体上移到原顶边位置
    getGuideBox(elW, elH) {
      const baseW = Math.min(elW * 0.62, elH * 0.36)
      const baseH = Math.min(baseW * 1.04, elH * 0.42)
      const w = Math.min(elW - 16, baseW * 4 / 3)
      const h = baseH / 3
      const x = (elW - w) / 2
      // 底边对齐原取景框顶边
      const y = Math.max(0, (elH - baseH) / 2 - h)
      return { x, y, w, h }
    },
    // 同步取景框的屏幕位置（与裁剪区域一致）
    updateGuide() {
      const video = this.$refs.scanVideo
      if (!video) return
      const elW = video.clientWidth
      const elH = video.clientHeight
      if (!elW || !elH) return
      const box = this.getGuideBox(elW, elH)
      this.guideStyle = {
        left: box.x + 'px',
        top: box.y + 'px',
        width: box.w + 'px',
        height: box.h + 'px'
      }
      // 提示文字固定在取景框上方，避免与底部按钮重叠
      this.tipStyle = {
        top: Math.max(56, box.y - 34) + 'px'
      }
    },
    // 取景框对应的视频原始像素区域（拍照时按这个区域裁剪）
    getFrameSourceRect(video) {
      const elW = video.clientWidth
      const elH = video.clientHeight
      const vw = video.videoWidth
      const vh = video.videoHeight
      const scale = Math.min(elW / vw, elH / vh)
      const offX = (elW - vw * scale) / 2
      const offY = (elH - vh * scale) / 2
      const box = this.getGuideBox(elW, elH)
      const x = Math.max(0, (box.x - offX) / scale)
      const y = Math.max(0, (box.y - offY) / scale)
      const w = Math.min(vw - x, box.w / scale)
      const h = Math.min(vh - y, box.h / scale)
      return { x, y, w, h }
    },
    // 截取取景框内的画面，放大后交给识别接口
    captureFrameImage() {
      const video = this.$refs.scanVideo
      const rect = this.getFrameSourceRect(video)
      const scale = Math.min(3, Math.max(1, 1280 / rect.w))
      const canvas = document.createElement('canvas')
      canvas.width = Math.round(rect.w * scale)
      canvas.height = Math.round(rect.h * scale)
      canvas.getContext('2d').drawImage(video, rect.x, rect.y, rect.w, rect.h, 0, 0, canvas.width, canvas.height)
      return canvas.toDataURL('image/jpeg', 0.92)
    },
    // 条码扫描
    captureBarcode() {
      if (this.busy) return
      const video = this.$refs.scanVideo
      if (!video || !video.videoWidth) {
        this.hasError = true
        this.tipText = '摄像头尚未就绪，请稍候再试'
        return
      }
      this.barcodeLoading = true
      this.hasError = false
      this.tipText = '正在识别条码…'
      try {
        const image = this.captureFrameImage()
        postAction('/material/scanFrame', { image }).then(res => {
          this.barcodeLoading = false
          if (!this.visible) return
          const codes = (res && res.code === 200 && res.data && res.data.codes) || []
          if (codes.length === 0) {
            this.hasError = true
            this.tipText = '条码未识别，请将条码完整放入框内后重试'
            return
          }
          if (navigator.vibrate) navigator.vibrate(60)
          this.$emit('result', { type: 'barcode', text: codes[0].code, format: codes[0].format || 'UNKNOWN' })
        }).catch(err => {
          this.barcodeLoading = false
          if (!this.visible) return
          this.hasError = true
          this.tipText = '识别失败：' + (err && err.message ? err.message : '未知错误')
        })
      } catch (e) {
        this.barcodeLoading = false
        this.hasError = true
        this.tipText = '截取画面失败，请重试'
      }
    },
    // 文字识别
    captureText() {
      if (this.busy) return
      const video = this.$refs.scanVideo
      if (!video || !video.videoWidth) {
        this.hasError = true
        this.tipText = '摄像头尚未就绪，请稍候再试'
        return
      }
      this.ocrLoading = true
      this.hasError = false
      this.tipText = '正在识别文字…'
      try {
        const image = this.captureFrameImage()
        postAction('/ocr/recognizeProductByBase64', { imageBase64: image }).then(res => {
          this.ocrLoading = false
          if (!this.visible) return
          const data = (res && res.data) || {}
          const text = (data.rawText || '').trim()
          if (!text) {
            this.hasError = true
            this.tipText = (res && res.code !== 200) ? ('文字识别失败：' + (data.message || 'OCR服务不可用')) : '未识别到文字，请将文字完整放入框内后重试'
            return
          }
          this.$emit('result', { type: 'text', text: text })
        }).catch(err => {
          this.ocrLoading = false
          if (!this.visible) return
          this.hasError = true
          this.tipText = '文字识别失败：' + (err && err.message ? err.message : '未知错误')
        })
      } catch (e) {
        this.ocrLoading = false
        this.hasError = true
        this.tipText = '截取画面失败，请重试'
      }
    },
    // 供调用页在未命中/异常时停留在本页显示提示
    setTip(text, isError) {
      this.tipText = text || ''
      this.hasError = !!isError
    },
    close() {
      this.$emit('close')
    }
  }
}
</script>

<style lang="less" scoped>
.msp-scan-page {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: #000;
}
.msp-scan-stage {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.msp-scan-video {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.msp-scan-guide {
  position: absolute;
  box-sizing: border-box;
  border: 2px solid #2b9bf4;
  border-radius: 10px;
  box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.55);
  overflow: hidden;
  z-index: 5;
}
/* 取景框内的动态扫描线：上下滑动 */
.msp-scan-line {
  position: absolute;
  left: 6px;
  right: 6px;
  top: 0;
  height: 2px;
  background: linear-gradient(90deg, rgba(43, 155, 244, 0), #2b9bf4 20%, #7cc4ff 50%, #2b9bf4 80%, rgba(43, 155, 244, 0));
  box-shadow: 0 0 8px rgba(43, 155, 244, 0.9);
  animation: msp-scan-line-move 0.9s ease-in-out infinite alternate;
}
@keyframes msp-scan-line-move {
  0% {
    top: 0;
    opacity: 0.3;
  }
  12% {
    opacity: 1;
  }
  88% {
    opacity: 1;
  }
  100% {
    top: calc(100% - 2px);
    opacity: 0.3;
  }
}
.msp-scan-header {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: calc(48px + env(safe-area-inset-top));
  padding-top: env(safe-area-inset-top);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 16px;
  z-index: 10;
}
.msp-scan-close {
  position: absolute;
  left: 16px;
  font-size: 22px;
}
.msp-scan-tip {
  position: absolute;
  /* 默认位于顶栏下方；取景框就绪后由 tipStyle 定位到框上方 */
  top: 56px;
  left: 16px;
  right: 16px;
  text-align: center;
  color: rgba(255, 255, 255, 0.85);
  font-size: 13px;
  z-index: 10;
}
.msp-scan-tip-error {
  color: #ff6b6b;
}
.msp-scan-footer {
  position: absolute;
  /* 按钮中线与屏幕下边三分之一处（即距顶 2/3 屏高）重合 */
  top: calc(66.6667% - 22px);
  left: 24px;
  right: 24px;
  display: flex;
  justify-content: center;
  z-index: 10;
}
.msp-scan-btn {
  flex: 1;
  max-width: 150px;
  height: 44px;
  margin: 0 8px;
  border: none;
  border-radius: 22px;
  background: #1890ff;
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  outline: none;
  &:disabled {
    background: rgba(24, 144, 255, 0.45);
  }
  &:active {
    background: #096dd9;
  }
}
</style>
