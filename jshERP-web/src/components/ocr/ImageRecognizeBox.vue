<template>
  <div class="ir-box">
    <a-textarea
      class="ir-box__textarea"
      :value="value"
      :rows="rows"
      :bordered="false"
      :placeholder="placeholder"
      @change="onTextChange" />
    <div v-if="imageUrl" class="ir-box__preview">
      <img :src="imageUrl" @click="$emit('preview')" />
      <span v-if="loading">正在识别图片...</span>
      <span v-else-if="errorText" class="ir-box__preview-error">识别失败：{{ errorText }}</span>
      <span v-else>{{ previewText }}</span>
    </div>
    <div class="ir-box__toolbar">
      <div class="ir-box__toolbar-left">
        <a @click="$emit('camera')"><a-icon type="camera" />拍照识别</a>
        <a @click="$emit('gallery')"><a-icon type="picture" />图片识别</a>
        <a class="ir-box__clear" :class="{ 'is-disabled': clearDisabled }" @click="onClear">清除</a>
      </div>
      <a-button
        v-if="showSmart"
        type="primary"
        size="small"
        class="ir-box__smart"
        :loading="smartLoading"
        :disabled="smartDisabled || !value"
        @click="$emit('smart')">{{ smartText }}</a-button>
    </div>
  </div>
</template>

<script>
/**
 * 标准图片识别框：圆角白底卡片 + 无边框文本输入 + 图片预览 + 底部操作栏。
 * 各页面的图片识别统一使用本组件，页面只负责传入数据与处理事件。
 */
export default {
  name: 'ImageRecognizeBox',
  props: {
    // 识别文本（支持 v-model）
    value: {
      type: String,
      default: ''
    },
    placeholder: {
      type: String,
      default: ''
    },
    rows: {
      type: Number,
      default: 5
    },
    // 已加载图片地址（本地预览或 Base64）
    imageUrl: {
      type: String,
      default: ''
    },
    // 图片识别中
    loading: {
      type: Boolean,
      default: false
    },
    // 识别错误提示
    errorText: {
      type: String,
      default: ''
    },
    // 图片加载成功后的提示文字
    previewText: {
      type: String,
      default: '图片已识别，点击图片可放大查看'
    },
    smartText: {
      type: String,
      default: '智能识别'
    },
    smartLoading: {
      type: Boolean,
      default: false
    },
    smartDisabled: {
      type: Boolean,
      default: false
    },
    showSmart: {
      type: Boolean,
      default: true
    },
    clearDisabled: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    onTextChange(e) {
      this.$emit('input', e.target.value)
    },
    onClear() {
      if (this.clearDisabled) return
      this.$emit('clear')
    }
  }
}
</script>

<style scoped>
.ir-box {
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.ir-box__textarea {
  display: block;
  padding: 14px;
  font-size: 16px;
  line-height: 24px;
  resize: none;
}
.ir-box__preview {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px 12px;
  font-size: 13px;
  color: #8c8c8c;
}
.ir-box__preview img {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  cursor: zoom-in;
}
.ir-box__preview-error {
  color: #f5222d;
  flex: 1;
}
.ir-box__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-top: 1px solid #f0f0f0;
}
.ir-box__toolbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 14px;
  color: #595959;
}
.ir-box__toolbar-left a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #595959;
  line-height: 20px;
}
.ir-box__toolbar-left .anticon {
  font-size: 16px;
  line-height: 1;
  vertical-align: middle;
}
.ir-box__clear {
  color: #bfbfbf !important;
}
.ir-box__clear.is-disabled {
  color: #d9d9d9 !important;
  cursor: not-allowed;
}
.ir-box__smart {
  min-width: 96px;
  height: 32px;
  border-radius: 16px;
  font-size: 14px;
}
</style>
