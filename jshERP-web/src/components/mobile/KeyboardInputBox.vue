<template>
  <div class="kib">
    <div v-if="title" class="kib__title">{{ title }}</div>
    <div class="kib__card">
      <textarea
        v-if="multiline"
        ref="field"
        class="kib__field kib__field--multiline"
        rows="1"
        data-kb-overlay="off"
        :value="value"
        :placeholder="placeholder"
        :maxlength="maxlength"
        :enterkeyhint="enterkeyhint"
        @input="onInput" />
      <input
        v-else
        ref="field"
        class="kib__field"
        :type="inputType"
        :inputmode="inputmode || null"
        data-kb-overlay="off"
        :value="value"
        :placeholder="placeholder"
        :maxlength="maxlength"
        :enterkeyhint="enterkeyhint"
        @input="onInput"
        @keydown.enter.prevent />
      <button
        type="button"
        class="kib__send"
        :disabled="disabled"
        :aria-label="confirmLabel"
        :title="confirmLabel"
        @mousedown.prevent
        @touchstart.prevent="markTouch"
        @touchend.prevent="onConfirm"
        @click="onClick">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 19V5" />
          <path d="M5 12l7-7 7 7" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script>
/**
 * 手机端标准输入框（可复用）
 * 圆角白卡片 + 自适应输入区 + 右侧圆形发送按钮。
 * 输入框标记 data-kb-overlay="off"，不会触发全局键盘输入弹层。
 * 由调用方决定定位与显隐，组件只负责输入与发送交互。
 */
export default {
  name: 'KeyboardInputBox',
  props: {
    /** 输入内容，配合 @input 使用 */
    value: {
      type: String,
      default: ''
    },
    /** 卡片上方的字段标题，为空时不显示 */
    title: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: ''
    },
    /** 多行输入（自动增高），默认单行 */
    multiline: {
      type: Boolean,
      default: false
    },
    /** 发送按钮视觉禁用（如内容为空时） */
    disabled: {
      type: Boolean,
      default: false
    },
    /** 发送按钮的无障碍/悬浮文案 */
    confirmLabel: {
      type: String,
      default: '确认'
    },
    maxlength: {
      type: [String, Number],
      default: null
    },
    enterkeyhint: {
      type: String,
      default: ''
    },
    /** 输入框类型，金额等场景可传 number */
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
      lastTouch: 0
    }
  },
  watch: {
    value() {
      this.resize()
    }
  },
  mounted() {
    this.resize()
  },
  methods: {
    onInput(e) {
      this.$emit('input', e.target.value)
      this.resize()
    },
    /** 多行输入随内容自动增高，上限由样式 max-height 控制 */
    resize() {
      const field = this.$refs.field
      if (!field || !this.multiline) return
      field.style.height = 'auto'
      field.style.height = Math.min(field.scrollHeight, 120) + 'px'
    },
    focus() {
      const field = this.$refs.field
      if (!field) return
      try {
        field.focus({ preventScroll: true })
      } catch (e) {
        try { field.focus() } catch (e2) {}
      }
      try {
        if (field.setSelectionRange) field.setSelectionRange(0, field.value.length)
      } catch (e) {}
    },
    blur() {
      const field = this.$refs.field
      if (field) field.blur()
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
      if (this.disabled) return
      this.$emit('confirm', this.value)
    }
  }
}
</script>

<style lang="less">
@import './keyboardInputBox.less';
</style>
