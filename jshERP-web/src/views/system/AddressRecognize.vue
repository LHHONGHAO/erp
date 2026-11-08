<template>
  <div class="addr-page">
    <div class="addr-body">
      <!-- 标准图片识别框 -->
      <div ref="scrollBlankFirst">
        <image-recognize-box
          v-model="pasteText"
          class="addr-ocr-box"
          placeholder="粘贴名片、快递单等文本内容，或拍照 / 选择图片自动识别"
          :image-url="imageUrl"
          :loading="ocrLoading"
          :error-text="ocrError"
          :smart-loading="pasteLoading"
          @camera="triggerCamera"
          @gallery="triggerGallery"
          @clear="clearAll"
          @smart="parsePastedText"
          @preview="previewVisible = true" />
        <input ref="cameraInput" type="file" accept="image/*" capture="environment" class="addr-file-input" @change="onPick" />
        <input ref="galleryInput" type="file" accept="image/*" class="addr-file-input" @change="onPick" />
      </div>

      <!-- 识别结果卡片 -->
      <div ref="scrollBlankLast" class="addr-card addr-info">
        <div class="addr-card__head">
          <span>识别结果</span>
          <div class="addr-card__head-right">
            <span class="addr-tag" :class="{ 'is-done': hasResult }">{{ hasResult ? '已识别' : '待识别' }}</span>
            <a-icon type="setting" class="addr-config-icon" title="地址识别参数" @click="showConfig = true" />
          </div>
        </div>
        <div class="addr-row" v-for="prop in fields" :key="prop.field">
          <span class="addr-row__label">{{ prop.label }}</span>
          <a-textarea v-if="prop.type === 'textarea'" v-model="form[prop.field]" class="addr-row__input"
                      :bordered="false" autosize :placeholder="getPlaceholder(prop.label)" />
          <a-input v-else v-model="form[prop.field]" class="addr-row__input" :bordered="false"
                   :placeholder="getPlaceholder(prop.label)" />
        </div>
      </div>

      <!-- 打印按钮 -->
      <div ref="scrollBlankBottomBar" class="addr-actions">
        <a-button type="primary" block class="addr-print-btn" @click="printResult">打印</a-button>
      </div>

      <div ref="scrollBlankSpacer" class="addr-bottom-space"></div>
    </div>

    <!-- 图片放大预览 -->
    <a-modal
      :visible="previewVisible"
      :footer="null"
      :width="'96%'"
      :zIndex="2000"
      wrapClassName="ocr-preview-modal"
      @cancel="previewVisible = false">
      <img :src="imageUrl" class="addr-preview-full" @click="previewVisible = false" />
    </a-modal>

    <!-- 地址识别参数配置 -->
    <a-modal
      :visible="showConfig"
      title="地址识别参数"
      :width="isMobile() ? '100%' : 480"
      :footer="null"
      :maskClosable="false"
      :zIndex="2000"
      :wrapClassName="isMobile() ? 'ocr-config-modal-mobile' : ''"
      @cancel="showConfig = false">
      <div class="addr-config-form">
        <div class="addr-config-row">
          <span class="addr-config-row__label">启用</span>
          <a-switch v-model="cfg.enabled" checkedChildren="启用" unCheckedChildren="禁用" />
        </div>
        <div class="addr-config-row">
          <span class="addr-config-row__label">API Key</span>
          <a-input v-model="cfg.apiKey" :bordered="false" allowClear
                   :placeholder="cfg.maskedApiKey ? ('已保存 ' + cfg.maskedApiKey + '，留空则沿用') : '请输入 API Key'" />
        </div>
        <div class="addr-config-row">
          <span class="addr-config-row__label">Secret Key</span>
          <a-input v-model="cfg.secretKey" type="password" :bordered="false" allowClear
                   :placeholder="cfg.maskedSecretKey ? ('已保存 ' + cfg.maskedSecretKey + '，留空则沿用') : '请输入 Secret Key'" />
        </div>
        <div class="addr-config-actions">
          <a-button type="primary" :loading="cfgSaving" @click="saveConfig">保存</a-button>
        </div>
      </div>
    </a-modal>

    <!-- 打印标签页：会员标签模板存在时自动按模板加载，识别结果按字段回填 -->
    <label-print-modal ref="labelPrintModal" />
  </div>
</template>

<script>
import { getAction, postAction } from '@/api/manage'
import { mixinDevice } from '@/utils/mixin'
import { scrollBlankMixin } from '@/utils/scrollBlank'
import LabelPrintModal from '@/views/material/modules/LabelPrintModal'
import ImageRecognizeBox from '@/components/ocr/ImageRecognizeBox'

/**
 * 地址识别独立页面：界面与识别逻辑与 OcrMemberRecognizeModal 保持一致，
 * 仅展示识别结果，不回填任何业务表单。
 */
export default {
  name: 'AddressRecognize',
  mixins: [mixinDevice, scrollBlankMixin],
  components: { LabelPrintModal, ImageRecognizeBox },
  data() {
    return {
      showConfig: false,
      ocrLoading: false,
      pasteLoading: false,
      hasResult: false,
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
      form: {
        contacts: '',
        telephone: '',
        email: '',
        address: '',
        company: ''
      },
      fields: [
        { field: 'contacts', label: '联系人', type: 'input' },
        { field: 'telephone', label: '电话', type: 'textarea' },
        { field: 'email', label: '电子邮箱', type: 'input' },
        { field: 'address', label: '地址', type: 'textarea' },
        { field: 'company', label: '公司', type: 'input' }
      ]
    }
  },
  created() {
    this.loadConfig()
  },
  beforeDestroy() {
    if (this.imageUrl) {
      URL.revokeObjectURL(this.imageUrl)
    }
  },
  methods: {
    getPlaceholder(label) {
      return this.ocrLoading ? '识别中...' : ('请输入' + label)
    },
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
    resetResult() {
      this.form = {
        contacts: '',
        telephone: '',
        email: '',
        address: '',
        company: ''
      }
      this.hasResult = false
      this.ocrError = ''
      this.ocrLoading = false
      this.pasteLoading = false
    },
    triggerCamera() {
      this.$refs.cameraInput && this.$refs.cameraInput.click()
    },
    triggerGallery() {
      this.$refs.galleryInput && this.$refs.galleryInput.click()
    },
    onPick(event) {
      var input = event.target
      var file = input.files && input.files[0]
      // 允许重复选择同一张图片
      input.value = ''
      if (file) {
        this.recognizeImage(file)
      }
    },
    clearAll() {
      if (this.imageUrl) {
        URL.revokeObjectURL(this.imageUrl)
      }
      this.imageUrl = ''
      this.pasteText = ''
      this.resetResult()
    },
    recognizeImage(file) {
      this.ocrLoading = true
      this.hasResult = false
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
          that.fillFields(res.data)
          that.hasResult = true
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
          that.fillFields(res.data)
          that.hasResult = true
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
    fillFields(data) {
      if (data.rawText) {
        this.pasteText = data.rawText
      }
      this.form.contacts = data.contacts || ''
      this.form.telephone = data.telephone || ''
      this.form.email = data.email || ''
      this.form.address = data.address || ''
      this.form.company = data.company || ''
    },
    /**
     * 打印：打开「地址打印」页，只使用本页输入框数据
     * 地址打印模板独立存储（address_layout），与会员标签模板（member_layout）互不影响
     */
    printResult() {
      var self = this
      var values = {}
      this.fields.forEach(function(prop) {
        values[prop.field] = self.form[prop.field] || ''
      })
      try {
        sessionStorage.setItem('jsh.labelEdit.snapshot', JSON.stringify({
          labelWidth: 320,
          labelHeight: 240,
          selectedAttrs: [],
          attrStyles: {},
          attrLabels: {},
          values: values
        }))
        // 创建/编辑地址打印模板时带回本页输入值，不绑定会员数据源
        sessionStorage.setItem('jsh.labelEdit.context', JSON.stringify({
          record: values,
          sku: {},
          template: null,
          provider: '',
          businessType: ''
        }))
      } catch (e) { /* 忽略 */ }
      this.$refs.labelPrintModal.openByType('address', { title: '地址打印' })
    }
  }
}
</script>

<style scoped>
.addr-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: #f4f6fb;
}
.addr-body {
  flex: 1;
  max-width: 860px;
  width: 100%;
  margin: 0 auto;
  padding: 12px;
}
.addr-card {
  margin-bottom: 12px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.addr-ocr-box {
  margin-bottom: 12px;
}
.addr-preview-full {
  display: block;
  width: 100%;
  max-height: 78vh;
  object-fit: contain;
  cursor: zoom-out;
}
.addr-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 14px 8px;
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}
.addr-card__head-right {
  display: flex;
  align-items: center;
}
.addr-config-icon {
  margin-left: 10px;
  font-size: 16px;
  font-weight: 400;
  color: #8c8c8c;
  cursor: pointer;
}
.addr-tag {
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 400;
  color: #8c8c8c;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.addr-tag.is-done {
  color: #1890ff;
  border-color: #bae0ff;
  background: #e6f4ff;
}
.addr-row {
  display: flex;
  align-items: center;
  min-height: 52px;
  padding: 6px 14px;
  border-top: 1px solid #f0f0f0;
}
.addr-row__label {
  width: 84px;
  flex-shrink: 0;
  font-size: 15px;
  color: #595959;
}
.addr-row__input {
  flex: 1;
  padding: 0;
  font-size: 16px;
  line-height: 22px;
  border: none;
  box-shadow: none;
  background: transparent;
}
.addr-row__input::placeholder {
  color: #bfbfbf;
}
.addr-actions {
  margin-top: 16px;
}
.addr-print-btn {
  height: 46px;
  font-size: 16px;
  border-radius: 23px;
  background: #1890ff;
  border-color: #1890ff;
  font-weight: 500;
}
@media (max-width: 768px) {
  /* 顶栏 49px 已由全局布局占位；可视高度由 scrollBlank 模块写入 CSS 变量，不支持时退回 vh */
  .addr-page {
    min-height: calc(100vh - 49px);
    min-height: calc(var(--scroll-blank-vh, 100vh) - 49px);
  }
  /* 手机端：固定底部打印栏，样式与标签打印一致 */
  .addr-actions {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    margin-top: 0;
    padding: 12px 16px;
    padding: 12px 16px calc(12px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid #eee;
    z-index: 20;
  }
  .addr-bottom-space {
    /* 兜底高度，实际高度由 scrollBlankMixin 按「上滑到底与顶部留白一致」动态计算 */
    height: 80px;
    height: calc(80px + env(safe-area-inset-bottom));
  }
}
.addr-file-input {
  display: none;
}
.addr-bottom-space {
  height: calc(12px + env(safe-area-inset-bottom));
}
/* 参数配置表单 */
.addr-config-form {
  padding: 4px 0;
}
.addr-config-row {
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.addr-config-row__label {
  width: 100px;
  flex-shrink: 0;
  font-size: 14px;
  color: #595959;
  text-align: right;
  padding-right: 12px;
  white-space: nowrap;
}
.addr-config-row .ant-input,
.addr-config-row .ant-input-affix-wrapper {
  flex: 1;
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  font-size: 14px;
}
.addr-config-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
</style>

<style lang="less">
/* 复用地址识别参数弹窗的手机端样式（与 OcrMemberRecognizeModal 一致） */
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
  .addr-config-row {
    min-height: 52px;
  }
  .addr-config-row__label {
    font-size: 15px;
  }
  .addr-config-row .ant-input {
    font-size: 16px;
  }
}
</style>
