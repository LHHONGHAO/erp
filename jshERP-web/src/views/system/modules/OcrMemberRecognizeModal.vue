<template>
  <a-modal
    :visible="visible"
    :width="modalWidth"
    :zIndex="zIndex"
    :footer="null"
    :closable="!isMobileView"
    :maskClosable="false"
    :wrapClassName="wrapClass"
    :style="modalStyle"
    :bodyStyle="bodyStyle"
    :mask="!isMobileView"
    @cancel="handleCancel">
    <template slot="title">
      <div class="ocr-member-title">
        <a-icon v-if="isMobileView" type="arrow-left" class="ocr-member-back" @click="handleCancel" />
        <span class="ocr-member-title__text">地址识别</span>
      </div>
    </template>

    <!-- 标准联系人信息提取面板：与地址识别独立页同一组件、同一外观 -->
    <contact-recognize-panel
      ref="panel"
      :form="ocrFields"
      :fields="memberProps"
      title="联系人信息"
      show-status
      :z-index="zIndex"
      @filled="onFilled"
      @clear="onClear" />

    <!-- 电脑端：原始识别文本 -->
    <div v-if="!isMobileView && rawText" style="margin-top:4px">
      <div style="font-size:13px;color:rgba(0,0,0,0.85);line-height:20px;margin-bottom:2px">原始文本</div>
      <a-textarea :value="rawText" readOnly style="font-size:12px;color:#666;resize:none" :rows="2" />
    </div>

    <!-- 操作按钮 -->
    <div v-if="isMobileView" class="ocr-member-actions">
      <a-button type="primary" block :disabled="!hasOcrResult" @click="applyResult">确定</a-button>
    </div>
    <div v-else style="text-align:right;margin-top:8px">
      <a-button size="small" @click="handleCancel" style="margin-right:8px">取消</a-button>
      <a-button size="small" type="primary" :disabled="!hasOcrResult" @click="applyResult">应用</a-button>
    </div>
  </a-modal>
</template>

<script>
import { mixinDevice } from '@/utils/mixin'
import ContactRecognizePanel from '@/components/ocr/ContactRecognizePanel'

export default {
  name: 'OcrMemberRecognizeModal',
  mixins: [mixinDevice],
  components: { ContactRecognizePanel },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    zIndex: {
      type: Number,
      default: undefined
    }
  },
  data() {
    return {
      hasOcrResult: false,
      rawText: '',
      ocrFields: {
        contacts: '',
        telephone: '',
        email: '',
        address: '',
        company: ''
      },
      memberProps: [
        { field: 'contacts', label: '联系人', type: 'input' },
        { field: 'telephone', label: '电话', type: 'textarea' },
        { field: 'email', label: '电子邮箱', type: 'input' },
        { field: 'address', label: '地址', type: 'textarea' },
        { field: 'company', label: '公司', type: 'input' }
      ]
    }
  },
  computed: {
    isMobileView() {
      return this.isMobile()
    },
    modalWidth() {
      return this.isMobileView ? '100%' : 680
    },
    modalStyle() {
      if (this.isMobileView) {
        return {
          top: 0,
          height: '100%',
          maxWidth: '100%',
          margin: 0,
          paddingBottom: 0,
          borderRadius: 0
        }
      }
      return {}
    },
    bodyStyle() {
      if (this.isMobileView) {
        return {
          flex: '1 1 auto',
          overflowY: 'auto',
          WebkitOverflowScrolling: 'touch',
          padding: 0
        }
      }
      return {}
    },
    wrapClass() {
      return this.isMobileView ? 'ocr-member-modal-wrap ocr-member-modal-mobile' : 'ocr-member-modal-wrap'
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.resetOcrFields()
      }
    }
  },
  methods: {
    handleCancel() {
      this.$emit('cancel')
    },
    resetOcrFields() {
      this.ocrFields = {
        contacts: '',
        telephone: '',
        email: '',
        address: '',
        company: ''
      }
      this.hasOcrResult = false
      this.rawText = ''
    },
    onFilled(data) {
      this.hasOcrResult = true
      this.rawText = (data && data.rawText) || ''
    },
    onClear() {
      this.resetOcrFields()
    },
    applyResult() {
      if (this.hasOcrResult) {
        this.$emit('ok', {
          contacts: this.ocrFields.contacts || '',
          telephone: this.ocrFields.telephone || '',
          email: this.ocrFields.email || '',
          address: this.ocrFields.address || '',
          company: this.ocrFields.company || ''
        })
      }
    }
  }
}
</script>

<style scoped>
.ocr-member-ocr-box {
  margin-bottom: 12px;
}
.ocr-prop-item {
  padding: 4px 0;
}
.ocr-file-input {
  display: none;
}
</style>

<style lang="less">
/* 手机端地址识别：整页样式，与全站手机页面保持一致 */
.ocr-member-modal-mobile {
  .ant-modal {
    width: 100% !important;
    max-width: 100% !important;
    top: 0;
    margin: 0;
    padding-bottom: 0;
  }
  .ant-modal-content {
    display: flex;
    flex-direction: column;
    min-height: 100vh;
    border-radius: 0;
  }
  .ant-modal-header {
    height: 49px;
    padding: 0 8px 0 0;
    border-radius: 0;
    background: #1890ff;
  }
  .ant-modal-body {
    flex: 1 1 auto;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    background: #f4f6fb;
    padding: 12px 12px 0;
    position: relative;
  }
  .ocr-member-title {
    display: flex;
    align-items: center;
    height: 49px;
  }
  /* 返回按钮：与底边栏等高（58px）的正方形，左边贴屏幕左边 */
  .ocr-member-back {
    width: 58px;
    height: 58px;
    line-height: 1;
    margin-left: 0;
    padding: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 22px;
    color: #fff;
    cursor: pointer;
  }
  .ocr-member-title__text {
    flex: 1;
    text-align: center;
    font-size: 16px;
    font-weight: 500;
    color: #fff;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .ocr-member-actions {
    position: sticky;
    bottom: 0;
    margin: 4px -12px 0;
    padding: 10px 14px calc(10px + env(safe-area-inset-bottom));
    background: #f4f6fb;
  }
  .ocr-member-actions .ant-btn {
    height: 46px;
    border-radius: 23px;
    font-size: 16px;
  }
}
</style>
