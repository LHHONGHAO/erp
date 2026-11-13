<template>
  <div v-if="visible" class="mip-root" :style="rootStyle">
    <div class="mip-box">
      <input
        ref="input"
        v-model="draft"
        class="mip-input"
        type="text"
        data-kb-overlay="off"
        enterkeyhint="search"
        :placeholder="placeholder"
        @blur="onBlur"
        @keydown.enter.prevent />
      <button
        type="button"
        class="mip-ok"
        @mousedown.prevent
        @touchstart.prevent="markTouch"
        @touchend.prevent="onConfirm"
        @click="onClick">{{ confirmText }}</button>
    </div>
  </div>
</template>

<script>
/**
 * 手机端标准输入弹层
 * 交互与全局键盘输入弹层保持一致：按屏宽铺满、框内右侧确认按钮；
 * 只有点击确认按钮才会提交，失焦/点击弹层外只关闭不回填，回车不触发提交。
 * 输入框标记 data-kb-overlay="off"，避免被全局键盘弹层二次接管。
 */
export default {
  name: 'MobileInputPopup',
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
      baselineHeight: 0,
      lastTouch: 0
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
          this.focusInput()
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
    focusInput() {
      const input = this.$refs.input
      if (!input) return
      try {
        input.focus({ preventScroll: true })
      } catch (e) {
        try { input.focus() } catch (e2) {}
      }
      try {
        if (input.setSelectionRange) input.setSelectionRange(0, input.value.length)
      } catch (e) {}
    },
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
    markTouch() {
      this.lastTouch = Date.now()
    },
    onClick() {
      // touchend 已处理过的场景，忽略后续 click，避免重复提交
      if (Date.now() - this.lastTouch < 600) return
      this.onConfirm()
    },
    onConfirm() {
      const text = (this.draft || '').trim()
      // 无内容时点击确认不做任何反应
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
/* 与全局键盘输入弹层一致的铺满式底部条：输入框 + 框内确认按钮 */
.mip-root {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  width: 100%;
  max-width: 100vw;
  margin: 0;
  padding: 10px 12px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom, 0px));
  box-sizing: border-box;
  background: #fff;
  border-top: 1px solid #e8e8e8;
  font-size: 15px;
}
.mip-box {
  display: flex;
  align-items: stretch;
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}
.mip-input {
  flex: 1;
  min-width: 0;
  height: 44px;
  padding: 0 12px;
  box-sizing: border-box;
  border: none;
  background: transparent;
  font-size: 16px;
  color: #262626;
  outline: none;
  &::placeholder {
    color: #b7b7b7;
  }
}
.mip-ok {
  flex: 0 0 auto;
  align-self: flex-start;
  height: 44px;
  padding: 0 18px;
  border: none;
  border-left: 1px solid #e8e8e8;
  border-radius: 0;
  background: #1890ff;
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  outline: none;
}
</style>
