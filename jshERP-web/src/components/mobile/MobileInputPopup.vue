<template>
  <div v-if="visible" class="mip-root" :style="rootStyle" @focusout="onBlur">
    <keyboard-input-box
      ref="box"
      :value="draft"
      :placeholder="placeholder"
      :confirm-label="confirmText"
      :disabled="!draft.trim()"
      enterkeyhint="search"
      @input="draft = $event"
      @confirm="onConfirm" />
  </div>
</template>

<script>
import KeyboardInputBox from './KeyboardInputBox'

/**
 * 手机端标准输入弹层
 * 基于 KeyboardInputBox 组件：定位在软键盘上方、按屏宽铺满；
 * 只有点击发送按钮才会提交，失焦/点击弹层外只关闭不回填，回车不触发提交。
 */
export default {
  name: 'MobileInputPopup',
  components: { KeyboardInputBox },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    /** 打开时的初始内容 */
    value: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: '请输入'
    },
    confirmText: {
      type: String,
      default: '确认'
    }
  },
  data() {
    return {
      draft: '',
      keyboardHeight: 0,
      popupLeft: 0,
      popupWidth: '100%',
      viewportBound: false,
      outsideBound: false,
      baselineHeight: 0
    }
  },
  computed: {
    rootStyle() {
      return {
        bottom: this.keyboardHeight + 'px',
        left: this.popupLeft + 'px',
        width: this.popupWidth
      }
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.draft = this.value || ''
        this.baselineHeight = window.innerHeight || 0
        this.$nextTick(() => {
          if (this.$refs.box) this.$refs.box.focus()
          this.bindViewport()
          this.bindOutside()
          this.positionPopup()
        })
      } else {
        this.unbindViewport()
        this.unbindOutside()
      }
    }
  },
  beforeDestroy() {
    this.unbindViewport()
    this.unbindOutside()
  },
  methods: {
    /** 估算软键盘高度，让弹层始终贴在键盘上方 */
    getKeyboardHeight() {
      const innerHeight = window.innerHeight || 0
      const vv = window.visualViewport
      if (vv) {
        const gap = Math.round(innerHeight - (vv.height + vv.offsetTop))
        return gap > 60 ? gap : 0
      }
      const gap = (this.baselineHeight || innerHeight) - innerHeight
      return gap > 80 ? gap : 0
    },
    /** 按可视视口定位，兼容横屏与页面缩放 */
    positionPopup() {
      this.keyboardHeight = this.getKeyboardHeight()
      const vv = window.visualViewport
      if (vv && vv.width) {
        this.popupLeft = Math.round(vv.offsetLeft)
        this.popupWidth = Math.round(vv.width) + 'px'
      } else {
        this.popupLeft = 0
        this.popupWidth = '100%'
      }
    },
    bindViewport() {
      if (this.viewportBound) return
      this.viewportBound = true
      window.addEventListener('resize', this.positionPopup)
      window.addEventListener('orientationchange', this.positionPopup)
      window.addEventListener('scroll', this.positionPopup, true)
      if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', this.positionPopup)
        window.visualViewport.addEventListener('scroll', this.positionPopup)
      }
    },
    unbindViewport() {
      if (!this.viewportBound) return
      this.viewportBound = false
      window.removeEventListener('resize', this.positionPopup)
      window.removeEventListener('orientationchange', this.positionPopup)
      window.removeEventListener('scroll', this.positionPopup, true)
      if (window.visualViewport) {
        window.visualViewport.removeEventListener('resize', this.positionPopup)
        window.visualViewport.removeEventListener('scroll', this.positionPopup)
      }
    },
    /** 点击弹层外关闭，不提交 */
    bindOutside() {
      if (this.outsideBound) return
      this.outsideBound = true
      document.addEventListener('click', this.onDocumentClick, true)
    },
    unbindOutside() {
      if (!this.outsideBound) return
      this.outsideBound = false
      document.removeEventListener('click', this.onDocumentClick, true)
    },
    onDocumentClick(e) {
      if (!this.visible) return
      if (this.$el && this.$el.contains(e.target)) return
      this.close()
    },
    /** 键盘收起导致的失焦：只关闭弹层，不提交 */
    onBlur() {
      setTimeout(() => {
        if (!this.visible) return
        const active = document.activeElement
        if (active && this.$el && this.$el.contains(active)) return
        this.close()
      }, 0)
    },
    onConfirm() {
      const text = (this.draft || '').trim()
      // 无内容时不做任何反应
      if (!text) return
      this.unbindViewport()
      this.unbindOutside()
      this.$emit('confirm', text)
    },
    close() {
      this.unbindViewport()
      this.unbindOutside()
      this.$emit('cancel')
    }
  }
}
</script>

<style lang="less" scoped>
/* 悬停在软键盘上方的输入弹层：卡片样式由 KeyboardInputBox 提供 */
.mip-root {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  width: 100%;
  max-width: 100vw;
  margin: 0;
  padding: 8px 12px;
  padding-bottom: calc(8px + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
}
</style>
