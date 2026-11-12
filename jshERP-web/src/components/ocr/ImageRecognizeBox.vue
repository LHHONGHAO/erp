<template>
  <div class="ir-box">
    <div class="ir-box__editor" :class="{ 'is-image-inline': imageInline && imageUrl }">
      <a-textarea
        class="ir-box__textarea"
        :value="value"
        :rows="rows"
        :bordered="false"
        :placeholder="placeholder"
        @change="onTextChange" />
      <template v-if="imageUrl">
        <template v-if="imageInline">
          <img class="ir-box__inline-image" :src="imageUrl" @click="$emit('preview')" />
          <div v-if="loading || errorText" class="ir-box__inline-status">
            <span v-if="loading">正在识别图片...</span>
            <span v-else class="ir-box__preview-error">识别失败：{{ errorText }}</span>
          </div>
        </template>
        <div v-else class="ir-box__preview">
          <img :src="imageUrl" @click="$emit('preview')" />
          <div class="ir-box__preview-text">
            <template v-if="loading">正在识别图片...</template>
            <template v-else-if="errorText"><span class="ir-box__preview-error">识别失败：{{ errorText }}</span></template>
            <template v-else>{{ previewText }}</template>
          </div>
        </div>
      </template>
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
    // 图片内嵌在输入框左侧、垂直居中显示，文字在图片右侧逐行显示且可编辑
    imageInline: {
      type: Boolean,
      default: false
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
.ir-box__editor {
  position: relative;
}
.ir-box__textarea {
  display: block;
  padding: 14px;
  font-size: 16px;
  line-height: 24px;
  resize: none;
}
/* 内嵌图片时：输入框高度不变，文字左侧留出图片位置，从第一行开始逐行显示 */
.ir-box__editor.is-image-inline .ir-box__textarea {
  padding-left: 100px;
}
.ir-box__inline-image {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  width: 72px;
  height: 72px;
  border-radius: 8px;
  object-fit: cover;
  cursor: zoom-in;
}
.ir-box__inline-status {
  position: absolute;
  left: 100px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 13px;
  color: #8c8c8c;
}
.ir-box__preview {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px 12px;
  font-size: 13px;
  color: #8c8c8c;
}
.ir-box__preview-text {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.ir-box__preview img {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  object-fit: cover;
  cursor: zoom-in;
  flex-shrink: 0;
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
  min-height: 48px;
  border-top: 1px solid #f0f0f0;
}
.ir-box__toolbar-left {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 14px;
  color: #595959;
}
/* 图标与文字同高、同行中线对齐：固定行高并让图标成为 flex 项，抵消 antd 的基线偏移 */
.ir-box__toolbar-left a {
  display: inline-flex;
  align-items: center;
  height: 28px;
  line-height: 28px;
  color: #595959;
  white-space: nowrap;
}
.ir-box__toolbar-left .anticon {
  display: inline-flex;
  align-items: center;
  height: 1em;
  margin-right: 4px;
  font-size: 15px;
  line-height: 1;
  vertical-align: 0;
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
