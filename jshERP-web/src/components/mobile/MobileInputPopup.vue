<template>
  <div v-if="visible" class="mip-root" :style="{ bottom: keyboardHeight + 'px' }">
    <div class="mip-row">
      <input
        ref="input"
        v-model="draft"
        class="mip-input"
        type="text"
        data-kb-overlay="off"
        enterkeyhint="search"
        :placeholder="placeholder"
        @keyup.enter="onConfirm" />
      <button type="button" class="mip-btn mip-cancel" @mousedown.prevent @touchstart.prevent @click="onCancel">{{ cancelText }}</button>
      <button type="button" class="mip-btn mip-ok" @mousedown.prevent @touchstart.prevent @click="onConfirm">{{ confirmText }}</button>
    </div>
  </div>
</template>

<script>
/**
 * 手机端标准输入弹层
 * 交互与 AI 商品添加页的键盘输入弹层保持一致：底部弹出、自动聚焦、取消/确认。
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
    cancelText: {
      type: String,
      default: '取消'
    },
    confirmText: {
      type: String,
      default: '确定'
    }
  },
  data() {
    return {
      draft: '',
      keyboardHeight: 0,
      viewportBound: false,
      baselineHeight: 0
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
          this.positionPopup()
        })
      } else {
        this.unbindViewport()
      }
    }
  },
  beforeDestroy() {
    this.unbindViewport()
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
    positionPopup() {
      this.keyboardHeight = this.getKeyboardHeight()
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
    onConfirm() {
      const text = (this.draft || '').trim()
      // 无内容时点击确认不做任何反应
      if (!text) return
      this.$emit('confirm', text)
    },
    onCancel() {
      this.$emit('cancel')
    }
  }
}
</script>

<style lang="less" scoped>
/* 与 AI 商品添加页的键盘输入弹层保持同一视觉：白色底部条 + 圆角 + 顶部阴影 */
.mip-root {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  padding: 8px 10px;
  box-sizing: border-box;
  background: #fff;
  border-top: 1px solid #e8e8e8;
  border-radius: 12px 12px 0 0;
  box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.12);
  font-size: 14px;
}
.mip-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.mip-input {
  flex: 1;
  min-width: 0;
  height: 44px;
  padding: 0 12px;
  box-sizing: border-box;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 15px;
  color: #262626;
  background: #fff;
  outline: none;
  &::placeholder {
    color: #b7b7b7;
  }
  &:focus {
    border-color: #1890ff;
    box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.12);
  }
}
.mip-btn {
  flex: 0 0 auto;
  height: 44px;
  border: none;
  border-radius: 8px;
  font-size: 15px;
}
.mip-cancel {
  padding: 0 10px;
  background: #f5f5f5;
  color: #595959;
}
.mip-ok {
  padding: 0 14px;
  background: #1890ff;
  color: #fff;
}
</style>
