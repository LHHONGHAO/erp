<template>
  <div v-if="visible" class="cim-mask">
    <div class="cim-panel">
      <div class="cim-head">
        <span class="cim-title">裁剪图片</span>
        <span class="cim-close" @click="onCancel">✕</span>
      </div>
      <div
        ref="stage"
        class="cim-stage"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @wheel.prevent="onWheel">
        <img
          v-if="src"
          ref="img"
          :src="src"
          class="cim-img"
          :style="imgStyle"
          draggable="false"
          alt="" />
        <div class="cim-frame">
          <i v-for="n in 9" :key="n" class="cim-grid" :class="'g' + n"></i>
        </div>
        <div v-if="loading" class="cim-loading">图片加载中…</div>
      </div>
      <div class="cim-hint">拖动图片调整位置 · 双指或滑块缩放</div>
      <div class="cim-zoom">
        <span class="cim-zoom-ico">−</span>
        <input :value="zoomPercent" type="range" min="100" max="400" step="1" class="cim-range" @input="onSlider" />
        <span class="cim-zoom-ico">＋</span>
      </div>
      <div class="cim-actions">
        <button type="button" class="cim-btn" @click="onCancel">取消</button>
        <button type="button" class="cim-btn" @click="resetView">重置</button>
        <button type="button" class="cim-btn primary" :disabled="exporting || loading" @click="onConfirm">确定</button>
      </div>
    </div>
  </div>
</template>

<script>
  import { cropImageFile } from '@/utils/imageCompress'

  export default {
    name: 'CropImageModal',
    props: {
      visible: { type: Boolean, default: false },
      file: { type: Object, default: null },
      outputSize: { type: Number, default: 600 },
      maxSizeKB: { type: Number, default: 500 }
    },
    data() {
      return {
        src: '',
        loading: false,
        exporting: false,
        naturalW: 0,
        naturalH: 0,
        stageW: 0,
        stageH: 0,
        zoomPercent: 100,
        cx: 0,
        cy: 0,
        pointers: {}
      }
    },
    computed: {
      scale() {
        if (!this.naturalW || !this.naturalH || !this.stageW) return 0
        var base = Math.max(this.stageW / this.naturalW, this.stageH / this.naturalH)
        return base * (this.zoomPercent / 100)
      },
      imgStyle() {
        if (!this.scale) return { display: 'none' }
        return {
          width: this.naturalW * this.scale + 'px',
          height: this.naturalH * this.scale + 'px',
          transform: 'translate3d(' + this.cx + 'px,' + this.cy + 'px,0)'
        }
      }
    },
    watch: {
      visible(val) {
        if (val) this.init()
        else this.release()
      },
      file() {
        if (this.visible) this.init()
      }
    },
    beforeDestroy() {
      this.release()
    },
    methods: {
      init() {
        if (!this.file) return
        this.release()
        this.loading = true
        this.zoomPercent = 100
        var self = this
        var url = URL.createObjectURL(this.file)
        var img = new Image()
        img.onload = function() {
          self.naturalW = img.width
          self.naturalH = img.height
          self.src = url
          self.$nextTick(function() {
            self.measure()
            self.loading = false
          })
        }
        img.onerror = function() {
          URL.revokeObjectURL(url)
          self.loading = false
          self.$emit('cancel')
        }
        img.src = url
      },
      measure() {
        var stage = this.$refs.stage
        if (!stage) return
        var width = stage.clientWidth
        // 老内核不支持 aspect-ratio 时，按宽度补成正方形
        if (!stage.clientHeight || Math.abs(stage.clientHeight - width) > 2) {
          stage.style.height = width + 'px'
        }
        this.stageW = stage.clientWidth
        this.stageH = stage.clientHeight
        this.centerImage()
      },
      /** 图片居中：按当前缩放让图片中心对准裁剪框中心 */
      centerImage() {
        this.cx = (this.stageW - this.naturalW * this.scale) / 2
        this.cy = (this.stageH - this.naturalH * this.scale) / 2
        this.clampOffset()
      },
      clampOffset() {
        var w = this.naturalW * this.scale
        var h = this.naturalH * this.scale
        var minX = this.stageW - w
        var minY = this.stageH - h
        this.cx = Math.min(0, Math.max(minX, this.cx))
        this.cy = Math.min(0, Math.max(minY, this.cy))
      },
      resetView() {
        this.zoomPercent = 100
        this.$nextTick(this.centerImage)
      },
      onPointerDown(e) {
        if (!this.src) return
        this.pointers[e.pointerId] = { x: e.clientX, y: e.clientY }
        if (e.target && e.target.setPointerCapture) {
          try { e.target.setPointerCapture(e.pointerId) } catch (err) { /* ignore */ }
        }
      },
      onPointerMove(e) {
        var p = this.pointers[e.pointerId]
        if (!p) return
        var ids = Object.keys(this.pointers)
        if (ids.length === 1) {
          this.cx += e.clientX - p.x
          this.cy += e.clientY - p.y
          p.x = e.clientX
          p.y = e.clientY
          this.clampOffset()
          return
        }
        // 双指缩放：按两指间距变化调整缩放，并保持两指中点不动
        var otherId = ids.filter(function(id) { return id !== String(e.pointerId) })[0]
        var other = this.pointers[otherId]
        if (!other || !this.$refs.stage) return
        var stageRect = this.$refs.stage.getBoundingClientRect()
        var prevDist = Math.max(1, Math.hypot(p.x - other.x, p.y - other.y))
        p.x = e.clientX
        p.y = e.clientY
        var dist = Math.max(1, Math.hypot(p.x - other.x, p.y - other.y))
        var midX = (p.x + other.x) / 2 - stageRect.left
        var midY = (p.y + other.y) / 2 - stageRect.top
        this.zoomAt(midX, midY, dist / prevDist)
      },
      onPointerUp(e) {
        delete this.pointers[e.pointerId]
      },
      onWheel(e) {
        if (!this.src) return
        var stageRect = this.$refs.stage.getBoundingClientRect()
        this.zoomAt(e.clientX - stageRect.left, e.clientY - stageRect.top, e.deltaY < 0 ? 1.08 : 0.92)
      },
      onSlider(e) {
        var value = Number(e.target.value)
        var old = this.zoomPercent || 100
        if (!value || value === old) return
        this.zoomAt(this.stageW / 2, this.stageH / 2, value / old)
      },
      /** 以某点为锚缩放 */
      zoomAt(anchorX, anchorY, factor) {
        var oldScale = this.scale
        if (!oldScale) return
        var next = Math.min(400, Math.max(100, this.zoomPercent * factor))
        if (next === this.zoomPercent) return
        var imgX = (anchorX - this.cx) / oldScale
        var imgY = (anchorY - this.cy) / oldScale
        this.zoomPercent = next
        var scale = this.scale
        this.cx = anchorX - imgX * scale
        this.cy = anchorY - imgY * scale
        this.clampOffset()
      },
      onCancel() {
        if (this.exporting) return
        this.release()
        this.$emit('cancel')
      },
      onConfirm() {
        if (this.exporting || !this.src) return
        var imgEl = this.$refs.img
        if (!imgEl) return
        var scale = this.scale
        var rect = {
          x: -this.cx / scale,
          y: -this.cy / scale,
          w: this.stageW / scale,
          h: this.stageH / scale
        }
        this.exporting = true
        var self = this
        cropImageFile(imgEl, rect, {
          size: this.outputSize,
          maxSizeKB: this.maxSizeKB,
          name: (this.file && this.file.name) || 'image'
        }).then(function(cropped) {
          self.exporting = false
          self.release()
          self.$emit('confirm', cropped)
        }).catch(function() {
          self.exporting = false
          self.$emit('error')
        })
      },
      release() {
        if (this.src) {
          URL.revokeObjectURL(this.src)
          this.src = ''
        }
        this.pointers = {}
      }
    }
  }
</script>

<style scoped>
.cim-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 2100;
  background: rgba(0, 0, 0, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}
.cim-panel {
  width: 100%;
  max-width: 440px;
  background: #fff;
  border-radius: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.cim-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eef1f5;
}
.cim-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
}
.cim-close {
  font-size: 18px;
  color: #8a919c;
  padding: 4px;
}
.cim-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 1 / 1;
  background: #0f1115;
  overflow: hidden;
  touch-action: none;
  user-select: none;
}
.cim-img {
  position: absolute;
  left: 0;
  top: 0;
  max-width: none;
  will-change: transform;
}
.cim-frame {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  border: 2px solid #12a05c;
  box-sizing: border-box;
  pointer-events: none;
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  grid-template-rows: 1fr 1fr 1fr;
}
.cim-grid {
  border: 0 solid rgba(255, 255, 255, 0.28);
}
.cim-grid.g2, .cim-grid.g5, .cim-grid.g8 {
  border-left-width: 1px;
  border-right-width: 1px;
}
.cim-grid.g4, .cim-grid.g5, .cim-grid.g6 {
  border-top-width: 1px;
  border-bottom-width: 1px;
}
.cim-loading {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
}
.cim-hint {
  padding: 8px 16px 0;
  font-size: 12px;
  color: #8a919c;
}
.cim-zoom {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px 12px;
}
.cim-zoom-ico {
  font-size: 16px;
  color: #8a919c;
  width: 16px;
  text-align: center;
}
.cim-range {
  flex: 1;
  accent-color: #12a05c;
}
.cim-actions {
  display: flex;
  gap: 8px;
  padding: 0 16px 16px;
}
.cim-btn {
  flex: 1;
  height: 40px;
  border-radius: 8px;
  border: 1px solid #e5e8ee;
  background: #fff;
  color: #1f2329;
  font-size: 15px;
}
.cim-btn.primary {
  background: #12a05c;
  border-color: #12a05c;
  color: #fff;
  font-weight: 600;
}
.cim-btn:disabled {
  opacity: 0.6;
}
</style>
