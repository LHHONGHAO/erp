<template>
  <a-modal
    :visible="visible"
    :width="680"
    :footer="null"
    :maskClosable="false"
    @cancel="$emit('cancel')">
    <template slot="title">
      <span>地址识别</span>
    </template>

    <!-- 地址识别参数配置：与图片识别的 API 相互独立 -->
    <div class="ocr-config-bar">
      <span class="ocr-config-label">地址识别参数</span>
      <a-switch v-model="cfg.enabled" checkedChildren="启用" unCheckedChildren="禁用" size="small" />
      <a-input v-model="cfg.apiKey" size="small" allowClear
               :placeholder="cfg.maskedApiKey ? ('已保存 ' + cfg.maskedApiKey + '，留空则沿用') : 'API Key（与图片识别不同的 API）'"
               style="width:230px" />
      <a-input v-model="cfg.secretKey" size="small" allowClear type="password"
               :placeholder="cfg.maskedSecretKey ? ('已保存 ' + cfg.maskedSecretKey + '，留空则沿用') : 'Secret Key'"
               style="width:230px" />
      <a-button size="small" type="primary" :loading="cfgSaving" @click="saveConfig">保存</a-button>
    </div>

    <!-- 图片上传区域 -->
    <div style="display:flex;gap:12px;margin-top:4px">
      <div style="width:200px;flex-shrink:0">
        <a-upload
          name="file"
          :multiple="false"
          :before-upload="beforeUpload"
          :showUploadList="false"
          accept="image/*"
          style="display:block"
        >
          <div v-if="!ocrLoading && !imageUrl" style="width:200px;height:200px;border:1px dashed #d9d9d9;border-radius:4px;text-align:center;cursor:pointer;display:flex;flex-direction:column;align-items:center;justify-content:center;">
            <a-icon type="camera" style="font-size:32px;color:#1890ff" />
            <p style="margin-top:6px;color:#999;font-size:12px">拍照或上传图片</p>
          </div>
          <div v-if="ocrLoading" style="width:200px;height:200px;border:1px dashed #d9d9d9;border-radius:4px;text-align:center;display:flex;flex-direction:column;align-items:center;justify-content:center;">
            <a-icon type="loading" style="font-size:32px;color:#1890ff" />
            <p style="margin-top:6px;color:#999;font-size:12px">识别中...</p>
          </div>
          <div v-if="imageUrl && !ocrLoading" style="width:200px;height:200px;border:1px solid #d9d9d9;border-radius:4px;overflow:hidden;cursor:pointer;position:relative;">
            <img :src="imageUrl" style="width:100%;height:100%;object-fit:contain;" />
            <div style="position:absolute;bottom:0;left:0;right:0;background:rgba(0,0,0,0.4);color:#fff;text-align:center;font-size:11px;padding:2px 0;">点击更换图片</div>
          </div>
        </a-upload>
      </div>

      <!-- 粘贴文本区域 -->
      <div style="flex:1;display:flex;flex-direction:column;min-width:0">
        <div style="font-size:14px;color:rgba(0,0,0,0.85);line-height:22px;margin-bottom:2px">
          粘贴内容
          <a-button size="small" type="link" style="padding:0;margin-left:8px" :loading="pasteLoading" @click="parsePastedText">识别文本</a-button>
        </div>
        <a-textarea v-model="pasteText" placeholder="粘贴名片、快递单等文本内容，点击「识别文本」解析" style="flex:1;font-size:12px;color:#666;resize:none;min-height:176px" />
      </div>
    </div>

    <!-- 识别结果（原始文本） -->
    <div v-if="ocrFields.rawText" style="margin-top:4px">
      <div style="font-size:13px;color:rgba(0,0,0,0.85);line-height:20px;margin-bottom:2px">原始文本</div>
      <a-textarea v-model="ocrFields.rawText" readOnly style="font-size:12px;color:#666;resize:none" :rows="2" />
    </div>

    <!-- 属性信息 -->
    <div style="margin-top:4px">
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
    <div style="text-align:right;margin-top:8px">
      <a-button size="small" @click="$emit('cancel')" style="margin-right:8px">取消</a-button>
      <a-button size="small" type="primary" :disabled="!hasOcrResult" @click="applyResult">应用</a-button>
    </div>
  </a-modal>
</template>

<script>
import { getAction, postAction } from '@/api/manage'

export default {
  name: 'OcrMemberRecognizeModal',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      ocrLoading: false,
      pasteLoading: false,
      hasOcrResult: false,
      imageUrl: '',
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
        } else {
          this.$message.error((res && res.data && res.data.message) ? res.data.message : '保存失败')
        }
      }).catch(err => {
        this.cfgSaving = false
        this.$message.error('保存请求失败：' + (err && err.message ? err.message : '未知错误'))
      })
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
      this.imageUrl = ''
      this.pasteText = ''
    },
    beforeUpload(file) {
      this.ocrLoading = true
      this.hasOcrResult = false
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
          that.$message.error(msg)
        }
      }).catch(function(err) {
        that.ocrLoading = false
        that.$message.error('识别请求失败：' + (err && err.message ? err.message : '未知错误'))
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
      var that = this
      postAction('/ocr/parseAddressText', { text: text }).then(function(res) {
        that.pasteLoading = false
        if (res && res.code === 200 && res.data) {
          that.fillOcrFields(res.data)
          that.hasOcrResult = true
          that.imageUrl = ''
        } else {
          var msg = (res && res.data && res.data.message) ? res.data.message : '解析失败'
          that.$message.error(msg)
        }
      }).catch(function(err) {
        that.pasteLoading = false
        that.$message.error('解析请求失败：' + (err && err.message ? err.message : '未知错误'))
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
.ocr-prop-item {
  padding: 4px 0;
}
.ocr-config-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  margin-bottom: 6px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 4px;
}
.ocr-config-label {
  font-size: 12px;
  color: #666;
  white-space: nowrap;
}
</style>
