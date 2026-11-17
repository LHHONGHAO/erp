<template>
  <div v-if="visible" class="mip-root" :style="rootStyle" @focusout="onBlur">
    <keyboard-input-box
      ref="box"
      :value="draft"
      :title="title"
      :placeholder="placeholder"
      :confirm-label="confirmText"
      :disabled="!draft.trim()"
      :enterkeyhint="enterkeyhint"
      :input-type="inputType"
      :inputmode="inputmode"
      @input="draft = $event"
      @confirm="onConfirm" />
  </div>
</template>

<script>
import KeyboardInputBox from './KeyboardInputBox'

/**
 * 手机端标准输入弹层
 * 基于 KeyboardInputBox 组件：定位在软键盘上方、按屏宽铺满；
 * 点击发送按钮或键盘工具条的勾/完成键都视为输入回填，点击弹层外取消。
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
    },
    /** 卡片上方标题，为空时不显示 */
    title: {
      type: String,
      default: ''
    },
    /** 键盘回车键文案提示 */
    enterkeyhint: {
      type: String,
      default: 'search'
    },
    /** 输入框类型，金额等场景传 number */
    inputType: {
      type: String,
      default: 'text'
    },
    /** 键盘类型提示，金额场景传 decimal 拉起数字键盘 */
    inputmode: {
      type: String,
      default: ''
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
      // 是否因点击弹层外而失焦：用于区分「点外部取消」与「键盘勾选回填」
      closedByOutside: false
    }
  },
  computed: {
    rootStyle() {
      return {
        bottom: this.keyboardHeight + 'px',
        left: this.popupLeft + 'px',
        width: this.popupWidth,
        // 键盘展开时去掉底部安全区留白，让弹层紧贴键盘顶边
        paddingBottom: this.keyboardHeight > 0 ? '6px' : ''
      }
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.draft = this.value || ''
        this.closedByOutside = false
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
      const prevHeight = this.keyboardHeight
      this.keyboardHeight = this.getKeyboardHeight()
      const vv = window.visualViewport
      if (vv && vv.width) {
        this.popupLeft = Math.round(vv.offsetLeft)
        this.popupWidth = Math.round(vv.width) + 'px'
      } else {
        this.popupLeft = 0
        this.popupWidth = '100%'
      }
      // 键盘从展开变为收起（如输入法工具条的勾/完成键）：点击弹层外则取消，否则视为输入回填
      if (this.visible && prevHeight > 0 && this.keyboardHeight === 0) {
        if (this.closedByOutside) this.close()
        else this.commitOrClose()
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
      document.addEventListener('touchstart', this.onDocumentTouchStart, true)
    },
    unbindOutside() {
      if (!this.outsideBound) return
      this.outsideBound = false
      document.removeEventListener('click', this.onDocumentClick, true)
      document.removeEventListener('touchstart', this.onDocumentTouchStart, true)
    },
    onDocumentTouchStart(e) {
      if (!this.visible) return
      if (this.$el && this.$el.contains(e.target)) return
      // 标记本次失焦来自点击弹层外，键盘收起时应按取消处理
      this.closedByOutside = true
    },
    onDocumentClick(e) {
      if (!this.visible) return
      if (this.$el && this.$el.contains(e.target)) return
      this.closedByOutside = true
      this.close()
    },
    /** 失焦：点击外部/切换焦点时关闭；键盘被系统收起时按输入回填处理 */
    onBlur() {
      setTimeout(() => {
        if (!this.visible) return
        const active = document.activeElement
        if (active && this.$el && this.$el.contains(active)) return
        // 键盘仍展开：说明是焦点切换或点击外部，关闭不回填
        if (this.getKeyboardHeight() > 0) {
          this.close()
          return
        }
        // 键盘已收起：点击外部则取消，否则视为输入框勾选/完成，回填
        if (this.closedByOutside) this.close()
        else this.commitOrClose()
      }, 260)
    },
    onConfirm() {
      const text = (this.draft || '').trim()
      // 无内容时不做任何反应
      if (!text) return
      this.unbindViewport()
      this.unbindOutside()
      this.$emit('confirm', text)
    },
    /** 有内容则回填，无内容直接关闭 */
    commitOrClose() {
      const text = (this.draft || '').trim()
      if (text) this.onConfirm()
      else this.close()
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
