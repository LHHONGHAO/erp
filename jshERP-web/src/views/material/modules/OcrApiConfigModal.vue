<template>
  <a-modal
    title="OCR API 配置"
    :visible="visible"
    :width="480"
    :maskClosable="false"
    @cancel="$emit('cancel')">
    <a-form layout="vertical">
      <a-form-item label="启用 OCR 识别">
        <a-switch checkedChildren="启用" unCheckedChildren="禁用" v-model="config.enabled" />
      </a-form-item>
      <a-form-item label="API Key">
        <a-input v-model="config.apiKey" :placeholder="config.maskedApiKey || '请输入百度OCR API Key'" allowClear />
      </a-form-item>
      <a-form-item label="Secret Key">
        <a-input v-model="config.secretKey" type="password" :placeholder="config.maskedSecretKey || '请输入百度OCR Secret Key'" allowClear />
      </a-form-item>
      <div style="margin-top:-12px;color:#999;font-size:12px">留空会保留已保存的密钥；更换密钥时再输入新值。</div>
    </a-form>
    <div slot="footer">
      <a-button @click="$emit('cancel')">取消</a-button>
      <a-button type="primary" @click="saveConfig" :loading="saving">保存</a-button>
    </div>
  </a-modal>
</template>

<script>
import { getAction, postAction } from '@/api/manage'

export default {
  name: 'OcrApiConfigModal',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      saving: false,
      config: {
        enabled: false,
        apiKey: '',
        secretKey: '',
        maskedApiKey: '',
        maskedSecretKey: ''
      }
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.loadConfig()
      }
    }
  },
  methods: {
    loadConfig() {
      getAction('/ocr/isEnabled').then(res => {
        if (res && res.code === 200 && res.data) {
          this.config.enabled = res.data.enabled
          this.config.maskedApiKey = res.data.apiKey || ''
          this.config.maskedSecretKey = res.data.secretKey || ''
          this.config.apiKey = ''
          this.config.secretKey = ''
        }
      }).catch(err => {
        if (this.isLoginExpired(err)) {
          this.$message.warning('登录状态已失效，请重新登录后配置 OCR。')
        } else {
          this.$message.error('读取 OCR 配置失败，请检查服务连接后重试。')
        }
      })
    },
    saveConfig() {
      this.saving = true
      postAction('/ocr/saveConfig', {
        apiKey: this.config.apiKey,
        secretKey: this.config.secretKey,
        enabled: this.config.enabled
      }).then(res => {
        this.saving = false
        if (res && res.code === 200) {
          this.$message.success('配置保存成功')
          this.config.apiKey = ''
          this.config.secretKey = ''
          this.loadConfig()
        } else {
          var msg = (res && res.data && res.data.message) ? res.data.message : '保存失败'
          this.$message.error(msg)
        }
      }).catch(err => {
        this.saving = false
        var response = err && err.response && err.response.data
        if (this.isLoginExpired(err)) {
          this.$message.warning('登录状态已失效，请重新登录后保存 OCR 配置。')
          return
        }
        var message = response && (response.message || (response.data && response.data.message))
        this.$message.error(message || ('保存请求失败：' + (err && err.message ? err.message : '网络异常')))
      })
    },
    isLoginExpired(err) {
      var response = err && err.response
      return !!(response && (response.status === 401 || response.data === 'loginOut'))
    }
  }
}
</script>
