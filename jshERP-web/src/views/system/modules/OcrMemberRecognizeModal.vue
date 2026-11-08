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
    :mask="isDesktop()"
    @cancel="handleCancel">
    <template slot="title">
      <div class="ocr-member-title">
        <a-icon v-if="isMobileView" type="arrow-left" class="ocr-member-back" @click="handleCancel" />
        <span class="ocr-member-title__text">地址识别</span>
        <a-icon v-if="isMobileView" type="setting" class="ocr-member-setting" @click="showConfig = true" />
      </div>
    </template>

    <!-- 标准图片识别框（文本输入 + 拍照/图片/清除 + 智能识别） -->
    <div>
      <image-recognize-box
        v-model="pasteText"
        class="ocr-member-ocr-box"
        placeholder="粘贴名片、快递单等文本内容，或拍照 / 选择图片自动识别"
        :image-url="imageUrl"
        :loading="ocrLoading"
        :error-text="ocrError"
        :smart-loading="pasteLoading"
        @camera="triggerCamera"
        @gallery="triggerGallery"
        @clear="clearMobile"
        @smart="parsePastedText"
        @preview="previewVisible = true" />
      <input ref="cameraInput" type="file" accept="image/*" capture="environment" class="ocr-file-input" @change="onMobilePick" />
      <input ref="galleryInput" type="file" accept="image/*" class="ocr-file-input" @change="onMobilePick" />
    </div>

    <!-- 图片放大预览 -->
    <a-modal
      :visible="previewVisible"
      :footer="null"
      :width="'96%'"
      :zIndex="2000"
      wrapClassName="ocr-preview-modal"
      @cancel="previewVisible = false">
      <img :src="imageUrl" class="ocr-preview-full" @click="previewVisible = false" />
    </a-modal>

    <!-- 识别结果（原始文本）：电脑端展示 -->
    <div v-if="!isMobileView && ocrFields.rawText" style="margin-top:4px">
      <div style="font-size:13px;color:rgba(0,0,0,0.85);line-height:20px;margin-bottom:2px">原始文本</div>
      <a-textarea v-model="ocrFields.rawText" readOnly style="font-size:12px;color:#666;resize:none" :rows="2" />
    </div>

    <!-- 手机端：联系人信息卡片 -->
    <div v-if="isMobileView" class="ocr-member-card ocr-member-info">
      <div class="ocr-member-card__head">
        <span>联系人信息</span>
        <span class="ocr-member-tag" :class="{ 'is-done': hasOcrResult }">{{ hasOcrResult ? '已识别' : '待识别' }}</span>
      </div>
      <div class="ocr-member-row" v-for="prop in memberProps" :key="prop.field">
        <span class="ocr-member-row__label">{{ prop.label }}</span>
        <a-textarea v-if="prop.type==='textarea'" v-model="ocrFields[prop.field]" class="ocr-member-row__input"
                    :bordered="false" autosize :placeholder="ocrLoading ? '识别中...' : ('请输入' + prop.label)" />
        <a-input v-else v-model="ocrFields[prop.field]" class="ocr-member-row__input" :bordered="false"
                 :placeholder="ocrLoading ? '识别中...' : ('请输入' + prop.label)" />
      </div>
    </div>

    <!-- 电脑端：属性信息 -->
    <div v-else style="margin-top:4px">
      <a-divider orientation="left" style="margin:4px 0 8px;font-size:13px">识别的信息</a-divider>
      <a-row :gutter="8">
        <a-col :span="12" v-for="prop in memberProps" :key="prop.field">
          <div class="ocr-prop-item">
            <div style="font-size:13px;color:rgba(0,0,0,0.85);line-height:20px;margin-bottom:0px">{{ prop.label }}</div>
            <a-input v-if="prop.type==='input'" v-model="ocrFields[prop.field]" :placeholder="ocrLoading ? '识别中...' : ''" size="small" />
            <a-textarea v-if="prop.type==='textarea'" v-model="ocrFields[prop.field]" :placeholder="ocrLoading ? '识别中...' : ''" size="small" :rows="2" />
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- 操作按钮 -->
    <div v-if="isMobileView" class="ocr-member-actions">
      <a-button type="primary" block :disabled="!hasOcrResult" @click="applyResult">确定</a-button>
    </div>
    <div v-else style="text-align:right;margin-top:8px">
      <a-button size="small" @click="handleCancel" style="margin-right:8px">取消</a-button>
      <a-button size="small" type="primary" :disabled="!hasOcrResult" @click="applyResult">应用</a-button>
    </div>

    <!-- 地址识别参数配置：独立弹层，挂到 body 避免嵌套层叠上下文遮挡，z-index 高于地址识别页 -->
    <a-modal
      :visible="showConfig"
      title="地址识别参数"
      :width="isMobileView ? '100%' : 480"
      :footer="null"
      :maskClosable="false"
      :zIndex="2000"
      :wrapClassName="isMobileView ? 'ocr-config-modal-mobile' : ''"
      @cancel="showConfig = false">
      <div class="ocr-config-form">
        <div class="ocr-config-row">
          <span class="ocr-config-row__label">启用</span>
          <a-switch v-model="cfg.enabled" checkedChildren="启用" unCheckedChildren="禁用" />
        </div>
        <div class="ocr-config-row">
          <span class="ocr-config-row__label">API Key</span>
          <a-input v-model="cfg.apiKey" :bordered="false" allowClear
                   :placeholder="cfg.maskedApiKey ? ('已保存 ' + cfg.maskedApiKey + '，留空则沿用') : '请输入 API Key'" />
        </div>
        <div class="ocr-config-row">
          <span class="ocr-config-row__label">Secret Key</span>
          <a-input v-model="cfg.secretKey" type="password" :bordered="false" allowClear
                   :placeholder="cfg.maskedSecretKey ? ('已保存 ' + cfg.maskedSecretKey + '，留空则沿用') : '请输入 Secret Key'" />
        </div>
        <div class="ocr-config-actions">
          <a-button type="primary" :loading="cfgSaving" @click="saveConfig">保存</a-button>
        </div>
      </div>
    </a-modal>
  </a-modal>
</template>

<script>
import { getAction, postAction } from '@/api/manage'
import { mixinDevice } from '@/utils/mixin'
import ImageRecognizeBox from '@/components/ocr/ImageRecognizeBox'

export default {
  name: 'OcrMemberRecognizeModal',
  mixins: [mixinDevice],
  components: { ImageRecognizeBox },
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
      showConfig: false,
      ocrLoading: false,
      pasteLoading: false,
      hasOcrResult: false,
      ocrError: '',
      imageUrl: '',
      previewVisible: false,
      pasteText: '',
      cfgSaving: false,
      cfg: {
        enabled: false,
        apiKey: '',
        secretKey: '',
        maskedApiKey: '',
        maskedSecretKey: ''
      },
      ocrFields: {
        rawText: '',
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
      return this.wrapClassNameInfo() + (this.isMobileView ? ' ocr-member-modal-mobile' : '')
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.resetOcrFields()
        this.loadConfig()
      }
    }
  },
  methods: {
    /** 读取地址识别参数配置（独立于图片识别的 API Key） */
    loadConfig() {
      getAction('/ocr/addressConfig').then(res => {
        if (res && res.code === 200 && res.data) {
          this.cfg.enabled = !!res.data.enabled
          this.cfg.maskedApiKey = res.data.apiKey || ''
          this.cfg.maskedSecretKey = res.data.secretKey || ''
          this.cfg.apiKey = ''
          this.cfg.secretKey = ''
        }
      }).catch(() => {
        this.$message.error('读取地址识别配置失败')
      })
    },
    /** 保存地址识别参数配置 */
    saveConfig() {
      this.cfgSaving = true
      postAction('/ocr/saveAddressConfig', {
        enabled: this.cfg.enabled,
        apiKey: this.cfg.apiKey,
        secretKey: this.cfg.secretKey
      }).then(res => {
        this.cfgSaving = false
        if (res && res.code === 200) {
          this.$message.success('地址识别参数保存成功')
          this.cfg.apiKey = ''
          this.cfg.secretKey = ''
          this.loadConfig()
          this.showConfig = false
        } else {
          this.$message.error((res && res.data && res.data.message) ? res.data.message : '保存失败')
        }
      }).catch(err => {
        this.cfgSaving = false
        this.$message.error('保存请求失败：' + (err && err.message ? err.message : '未知错误'))
      })
    },
    handleCancel() {
      this.$emit('cancel')
    },
    resetOcrFields() {
      this.ocrFields = {
        rawText: '',
        contacts: '',
        telephone: '',
        email: '',
        address: '',
        company: ''
      }
      this.hasOcrResult = false
      this.ocrLoading = false
      this.pasteLoading = false
      this.ocrError = ''
      this.imageUrl = ''
      this.pasteText = ''
    },
    /** 手机端：拍照/相册选择图片 */
    triggerCamera() {
      this.$refs.cameraInput && this.$refs.cameraInput.click()
    },
    triggerGallery() {
      this.$refs.galleryInput && this.$refs.galleryInput.click()
    },
    onMobilePick(event) {
      var input = event.target
      var file = input.files && input.files[0]
      // 允许重复选择同一张图片
      input.value = ''
      if (file) {
        this.beforeUpload(file)
      }
    },
    clearMobile() {
      if (this.imageUrl) {
        URL.revokeObjectURL(this.imageUrl)
      }
      this.resetOcrFields()
    },
    beforeUpload(file) {
      this.ocrLoading = true
      this.hasOcrResult = false
      this.ocrError = ''
      if (this.imageUrl) {
        URL.revokeObjectURL(this.imageUrl)
      }
      this.imageUrl = URL.createObjectURL(file)
      var formData = new FormData()
      formData.append('file', file)
      var that = this
      postAction('/ocr/recognizeAddress', formData).then(function(res) {
        that.ocrLoading = false
        if (res && res.code === 200 && res.data) {
          that.fillOcrFields(res.data)
          that.hasOcrResult = true
        } else {
          var msg = (res && res.data && res.data.message) ? res.data.message : '识别失败'
          that.ocrError = msg
          that.$message.error(msg)
        }
      }).catch(function(err) {
        that.ocrLoading = false
        var errMsg = '识别请求失败：' + (err && err.message ? err.message : '未知错误')
        that.ocrError = errMsg
        that.$message.error(errMsg)
      })
      return false
    },
    parsePastedText() {
      var text = this.pasteText.trim()
      if (!text) {
        this.$message.warn('请先粘贴文本内容')
        return
      }
      this.pasteLoading = true
      this.ocrError = ''
      var that = this
      postAction('/ocr/parseAddressText', { text: text }).then(function(res) {
        that.pasteLoading = false
        if (res && res.code === 200 && res.data) {
          that.fillOcrFields(res.data)
          that.hasOcrResult = true
          that.imageUrl = ''
        } else {
          var msg = (res && res.data && res.data.message) ? res.data.message : '解析失败'
          that.ocrError = msg
          that.$message.error(msg)
        }
      }).catch(function(err) {
        that.pasteLoading = false
        var errMsg = '解析请求失败：' + (err && err.message ? err.message : '未知错误')
        that.ocrError = errMsg
        that.$message.error(errMsg)
      })
    },
    fillOcrFields(data) {
      if (data.rawText) {
        this.ocrFields.rawText = data.rawText
      }
      this.ocrFields.contacts = data.contacts || ''
      this.ocrFields.telephone = data.telephone || ''
      this.ocrFields.email = data.email || ''
      this.ocrFields.address = data.address || ''
      this.ocrFields.company = data.company || ''
    },
    applyResult() {
      if (this.hasOcrResult) {
        var output = {
          contacts: this.ocrFields.contacts || '',
          telephone: this.ocrFields.telephone || '',
          email: this.ocrFields.email || '',
          address: this.ocrFields.address || '',
          company: this.ocrFields.company || ''
        }
        this.$emit('ok', output)
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
/* 地址识别参数配置弹窗：表单行 */
.ocr-config-form {
  padding: 4px 0;
}
.ocr-config-row {
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.ocr-config-row__label {
  width: 100px;
  flex-shrink: 0;
  font-size: 14px;
  color: #595959;
  text-align: right;
  padding-right: 12px;
  white-space: nowrap;
}
.ocr-config-row .ant-input,
.ocr-config-row .ant-input-affix-wrapper {
  flex: 1;
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  font-size: 14px;
}
.ocr-config-row .ant-input::placeholder {
  color: #bfbfbf;
}
.ocr-config-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
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
  .ocr-member-setting {
    font-size: 18px;
    color: #fff;
    padding: 0 16px;
    cursor: pointer;
  }
  .ocr-member-card {
    margin-bottom: 12px;
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
  }
  .ocr-preview-full {
    display: block;
    width: 100%;
    max-height: 78vh;
    object-fit: contain;
    cursor: zoom-out;
  }
  .ocr-member-card__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 14px 8px;
    font-size: 16px;
    font-weight: 600;
    color: #262626;
  }
  .ocr-member-tag {
    padding: 2px 10px;
    font-size: 12px;
    font-weight: 400;
    color: #8c8c8c;
    border: 1px solid #e5e7eb;
    border-radius: 10px;
  }
  .ocr-member-tag.is-done {
    color: #1890ff;
    border-color: #bae0ff;
    background: #e6f4ff;
  }
  .ocr-member-row {
    display: flex;
    align-items: center;
    min-height: 52px;
    padding: 6px 14px;
    border-top: 1px solid #f0f0f0;
  }
  .ocr-member-row__label {
    width: 84px;
    flex-shrink: 0;
    font-size: 15px;
    color: #595959;
  }
  .ocr-member-row__input {
    flex: 1;
    padding: 0;
    font-size: 16px;
    line-height: 22px;
    border: none;
    box-shadow: none;
    background: transparent;
  }
  .ocr-member-row__input::placeholder {
    color: #bfbfbf;
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

/* 手机端：地址识别参数配置弹窗 —— 跟随唤出页面（地址识别页本身全屏），底部弹出 */
.ocr-config-modal-mobile {
  .ant-modal-wrap {
    position: fixed !important;
    inset: 0;
    z-index: 2000 !important;
  }
  .ant-modal {
    width: 100% !important;
    max-width: 100% !important;
    top: auto;
    bottom: 0;
    margin: 0;
    padding-bottom: 0;
  }
  .ant-modal-content {
    border-radius: 16px 16px 0 0;
    height: auto;
    max-height: 70vh;
    display: flex;
    flex-direction: column;
  }
  .ant-modal-header {
    border-bottom: 1px solid #f0f0f0;
    padding: 14px 16px;
  }
  .ant-modal-title {
    font-size: 16px;
    font-weight: 600;
  }
  .ant-modal-body {
    padding: 8px 16px 16px;
    overflow-y: auto;
  }
  .ocr-config-row {
    min-height: 52px;
  }
  .ocr-config-row__label {
    font-size: 15px;
  }
  .ocr-config-row .ant-input,
  .ocr-config-row .ant-input-affix-wrapper {
    font-size: 16px;
  }
  .ocr-config-actions .ant-btn {
    height: 40px;
    padding: 0 24px;
    font-size: 15px;
    border-radius: 20px;
  }
}
</style>
