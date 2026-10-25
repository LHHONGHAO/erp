<template>
  <a-modal
    :visible="visible"
    :width="extractOnly ? 480 : 453"
    :footer="null"
    :maskClosable="false"
    @cancel="handleCancel">
    <!-- 自定义标题栏 -->
    <template slot="title">
      <span>{{ extractOnly ? '图片文字提取' : '图片识别' }}</span>
      <a-button v-if="!extractOnly" size="small" style="margin-left:12px" @click="apiConfigVisible=true">
        <a-icon type="setting" />API 配置
      </a-button>
      <a-button v-if="!extractOnly" size="small" style="margin-left:8px" @click="openPropertyConfig">
        <a-icon type="control" />属性显示设置
      </a-button>
    </template>

    <!-- 文字编辑框 + 底部操作栏（不再显示图片预览框） -->
    <div class="ocr-text-panel">
      <a-textarea
        v-model="ocrFields.rawText"
        class="ocr-raw-text"
        :rows="extractOnly ? 10 : 8"
        :placeholder="ocrLoading ? '正在识别图片文字...' : '识别结果会显示在这里，也可以直接输入或粘贴文字'"
        @input="onTextEdited" />
      <div class="ocr-text-toolbar">
        <div class="ocr-text-toolbar-left">
          <input ref="cameraInput" type="file" accept="image/*" capture="environment" class="ocr-file-input" @change="onPickImage" />
          <input ref="galleryInput" type="file" accept="image/*" class="ocr-file-input" @change="onPickImage" />
          <a-button type="link" class="ocr-toolbar-btn" :loading="ocrLoading" @click="triggerCamera">
            <a-icon type="camera" />拍照识别
          </a-button>
          <a-button type="link" class="ocr-toolbar-btn" :loading="ocrLoading" @click="triggerGallery">
            <a-icon type="picture" />图片识别
          </a-button>
          <a-button type="link" class="ocr-toolbar-btn ocr-toolbar-clear" :disabled="!ocrFields.rawText && !ocrLoading" @click="clearText">清除</a-button>
        </div>
        <a-button v-if="!extractOnly" type="primary" class="ocr-ai-btn" :loading="ocrLoading" :disabled="!ocrFields.rawText" @click="smartRecognize">智能识别</a-button>
        <a-button v-else type="primary" class="ocr-ai-btn" :disabled="!ocrFields.rawText" @click="copyRawText">复制文字</a-button>
      </div>
    </div>

    <!-- 属性框（始终显示） -->
    <div v-if="!extractOnly" style="margin-top:4px">
      <a-divider orientation="left" style="margin:12px 0 8px;font-size:13px">属性信息</a-divider>
      <a-row :gutter="8">
        <a-col :span="12" v-for="prop in visibleOcrProps" :key="prop.field">
          <div class="ocr-prop-item">
            <div style="font-size:13px;color:rgba(0,0,0,0.85);line-height:20px;margin-bottom:0px">{{ prop.anotherName }}</div>
            <a-input v-model="ocrFields[prop.field]" :placeholder="ocrLoading ? '识别中...' : ''" size="small" />
          </div>
        </a-col>
      </a-row>
    </div>

    <!-- 操作按钮 -->
    <div style="text-align:right;margin-top:8px">
      <a-button size="small" @click="handleCancel" style="margin-right:8px">取消</a-button>
      <a-button v-if="!extractOnly" size="small" type="primary" :disabled="!hasOcrResult" @click="applyResult">应用</a-button>
    </div>

    <!-- 摄像头拍照弹层 -->
    <a-modal
      title="拍照识别"
      :visible="cameraVisible"
      :footer="null"
      :width="560"
      :maskClosable="false"
      @cancel="closeCamera">
      <div class="ocr-camera-wrap">
        <video ref="cameraVideo" class="ocr-camera-video" autoplay playsinline muted></video>
      </div>
      <div style="text-align:right;margin-top:12px">
        <a-button style="margin-right:8px" @click="closeCamera">取消</a-button>
        <a-button type="primary" @click="capturePhoto"><a-icon type="camera" />拍照</a-button>
      </div>
    </a-modal>

    <!-- API 配置弹层 -->
    <ocr-api-config-modal :visible="apiConfigVisible" @cancel="apiConfigVisible=false" />

    <!-- 属性显示设置弹层 -->
    <a-modal
      title="属性显示设置"
      :visible="propertyConfigVisible"
      :width="500"
      :maskClosable="false"
      @ok="savePropertyConfig"
      @cancel="propertyConfigVisible=false">
      <a-table :columns="propConfigColumns" :dataSource="propertyConfigData" :pagination="false" size="small" rowKey="field">
        <template slot="visible" slot-scope="text, record">
          <a-switch :checked="record.visible" @change="(val) => { record.visible = val }" />
        </template>
      </a-table>
    </a-modal>
  </a-modal>
</template>

<script>
import Vue from 'vue'
import { postAction } from '@/api/manage'
import { axios as uploadHttp } from '@/utils/request'
import OcrApiConfigModal from './OcrApiConfigModal'

// 基础属性列表：与商品新增页「基础属性设置」的可选项保持一致
// （即 MaterialModal.basePropertyList 中 basePropertyFields 包含的属性，顺序、名称、默认显示状态一致）
var BASE_PROPERTIES = [
  { nativeName: '名称', field: 'name', visible: true, sort: 0, anotherName: '名称', fixed: true },
  { nativeName: '单位', field: 'unit', visible: true, sort: 1, anotherName: '单位', fixed: true },
  { nativeName: '品牌', field: 'brand', visible: true, sort: 2, anotherName: '品牌', fixed: true },
  { nativeName: '助记码', field: 'mnemonic', visible: true, sort: 3, anotherName: '助记码', fixed: true },
  { nativeName: '类别', field: 'categoryId', visible: true, sort: 4, anotherName: '类别', fixed: true },
  { nativeName: '基础重量', field: 'weight', visible: true, sort: 5, anotherName: '基础重量', fixed: true },
  { nativeName: '制造商', field: 'mfrs', visible: true, sort: 6, anotherName: '制造商', fixed: true },
  { nativeName: '条码', field: 'barCode', visible: true, sort: 8, anotherName: '条码', fixed: true },
  { nativeName: '商品ID', field: 'id', visible: false, sort: 9, anotherName: '商品ID', fixed: true },
  { nativeName: '面料', field: 'fabric', visible: true, sort: 10, anotherName: '面料', fixed: true },
  { nativeName: '安全类别', field: 'safetyCategory', visible: true, sort: 11, anotherName: '安全类别', fixed: true },
  { nativeName: '执行标准', field: 'executiveStandard', visible: true, sort: 12, anotherName: '执行标准', fixed: true },
  { nativeName: '等级', field: 'grade', visible: true, sort: 13, anotherName: '等级', fixed: true },
  { nativeName: '检验', field: 'inspection', visible: true, sort: 14, anotherName: '检验', fixed: true },
  { nativeName: '填充物', field: 'filler', visible: true, sort: 15, anotherName: '填充物', fixed: true },
  { nativeName: '扩展1', field: 'otherField1', visible: true, sort: 16, anotherName: '扩展1', fixed: false },
  { nativeName: '扩展2', field: 'otherField2', visible: true, sort: 17, anotherName: '扩展2', fixed: false },
  { nativeName: '扩展3', field: 'otherField3', visible: true, sort: 18, anotherName: '扩展3', fixed: false },
  { nativeName: '扩展4', field: 'otherField4', visible: true, sort: 19, anotherName: '扩展4', fixed: false },
  { nativeName: '扩展5', field: 'otherField5', visible: true, sort: 20, anotherName: '扩展5', fixed: false },
  { nativeName: '扩展6', field: 'otherField6', visible: true, sort: 21, anotherName: '扩展6', fixed: false },
  { nativeName: '扩展7', field: 'otherField7', visible: true, sort: 22, anotherName: '扩展7', fixed: false }
]

export default {
  name: 'OcrRecognizeModal',
  components: {
    OcrApiConfigModal
  },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    imageFile: {
      type: Object,
      default: null
    },
    extractOnly: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      ocrLoading: false,
      hasOcrResult: false,
      ocrFields: { rawText: '' },
      specImagePath: '',
      cameraVisible: false,
      cameraStream: null,
      apiConfigVisible: false,
      propertyConfigVisible: false,
      propertyConfigData: [],
      ocrPropertyList: [],
      propConfigColumns: [
        { title: '属性名称', dataIndex: 'nativeName', key: 'nativeName', width: '160px' },
        { title: '显示', dataIndex: 'visible', key: 'visible', width: '80px', scopedSlots: { customRender: 'visible' } }
      ]
    }
  },
  computed: {
    visibleOcrProps() {
      return this.ocrPropertyList.filter(function(p) { return p.visible })
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.loadOcrPropertyList()
        this.resetOcrFields()
        // 规格图片识别：直接使用传入的图片自动识别文字
        if (this.extractOnly && this.imageFile) {
          this.startRecognition(this.imageFile)
        }
      } else {
        // 关闭弹层时释放摄像头
        this.closeCamera()
      }
    }
  },
  beforeDestroy() {
    this.closeCamera()
  },
  methods: {
    resetOcrFields() {
      var fields = { rawText: '' }
      // 预先声明所有属性键，保证属性输入框的 v-model 具备响应式
      BASE_PROPERTIES.forEach(function(p) { fields[p.field] = '' })
      this.ocrFields = fields
      this.hasOcrResult = false
      this.ocrLoading = false
      this.specImagePath = ''
    },
    handleCancel() {
      this.closeCamera()
      // 规格图取字模式：把提取框内加载并上传成功的图片路径带回
      if (this.extractOnly) {
        this.$emit('cancel', this.specImagePath || '')
      } else {
        this.$emit('cancel')
      }
    },
    loadOcrPropertyList() {
      var baseConfig = Vue.ls.get('material_base_property_config')
      var allProps = BASE_PROPERTIES.map(function(p) { return Object.assign({}, p) })
      // 属性名称跟随「基础属性设置」中的别名，保证两处显示一致
      if (baseConfig && baseConfig.length) {
        for (var i = 0; i < allProps.length; i++) {
          var cfg = baseConfig.find(function(c) { return c.field === allProps[i].field })
          if (cfg) {
            allProps[i].anotherName = cfg.anotherName || allProps[i].nativeName
          }
        }
      }
      // 可选属性与「基础属性设置」的可选项完全一致，不按基础信息里的显示状态过滤
      // OCR 侧只覆盖“是否显示”，名称始终沿用基础属性设置
      var ocrConfig = Vue.ls.get('ocrPropertyList')
      if (ocrConfig && ocrConfig.length) {
        for (var j = 0; j < allProps.length; j++) {
          var ocrCfg = ocrConfig.find(function(c) { return c.field === allProps[j].field })
          if (ocrCfg) {
            allProps[j].visible = ocrCfg.visible !== false
          }
        }
      }
      this.ocrPropertyList = allProps
    },
    openPropertyConfig() {
      this.propertyConfigData = this.ocrPropertyList.map(function(p) { return Object.assign({}, p) })
      this.propertyConfigVisible = true
    },
    savePropertyConfig() {
      this.ocrPropertyList.forEach(function(item) {
        var cfg = this.propertyConfigData.find(function(p) { return p.field === item.field })
        if (cfg) {
          item.visible = cfg.visible
        }
      }.bind(this))
      Vue.ls.set('ocrPropertyList', this.ocrPropertyList.map(function(p) {
        return { field: p.field, nativeName: p.nativeName, visible: p.visible, anotherName: p.anotherName }
      }), 365 * 24 * 60 * 60 * 1000)
      this.propertyConfigVisible = false
    },
    triggerCamera() {
      // 优先调用设备摄像头（手机后置 / 电脑摄像头）
      var hasCamera = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
      if (!hasCamera) {
        this.$refs.cameraInput && this.$refs.cameraInput.click()
        return
      }
      this.openCamera()
    },
    openCamera() {
      var that = this
      navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false })
        .then(function(stream) {
          that.cameraStream = stream
          that.cameraVisible = true
          that.$nextTick(function() {
            var video = that.$refs.cameraVideo
            if (video) {
              video.srcObject = stream
              var playPromise = video.play()
              if (playPromise && playPromise.catch) {
                playPromise.catch(function() {})
              }
            }
          })
        })
        .catch(function() {
          that.$message.warning('无法调用摄像头，已切换为选择图片')
          that.$refs.cameraInput && that.$refs.cameraInput.click()
        })
    },
    closeCamera() {
      this.cameraVisible = false
      if (this.cameraStream) {
        this.cameraStream.getTracks().forEach(function(track) { track.stop() })
        this.cameraStream = null
      }
      var video = this.$refs.cameraVideo
      if (video) {
        video.srcObject = null
      }
    },
    capturePhoto() {
      var video = this.$refs.cameraVideo
      if (!video || !video.videoWidth) {
        this.$message.warning('摄像头画面尚未就绪，请稍后再拍')
        return
      }
      var canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d').drawImage(video, 0, 0, canvas.width, canvas.height)
      var that = this
      canvas.toBlob(function(blob) {
        if (!blob) {
          that.$message.error('拍照失败，请重试')
          return
        }
        var file = new File([blob], 'camera-' + Date.now() + '.jpg', { type: 'image/jpeg' })
        that.closeCamera()
        if (that.extractOnly) {
          that.uploadSpecImage(file)
        }
        that.startRecognition(file)
      }, 'image/jpeg', 0.92)
    },
    triggerGallery() {
      this.$refs.galleryInput && this.$refs.galleryInput.click()
    },
    onPickImage(event) {
      var input = event.target
      var file = input.files && input.files[0]
      // 允许重复选择同一张图片
      input.value = ''
      if (file) {
        // 规格图取字模式：图片在提取框内加载，同时上传供规格值使用
        if (this.extractOnly) {
          this.uploadSpecImage(file)
        }
        this.startRecognition(file)
      }
    },
    uploadSpecImage(file) {
      var formData = new FormData()
      formData.append('file', file)
      formData.append('biz', 'material')
      var that = this
      uploadHttp.post('/systemConfig/upload', formData).then(function(res) {
        if (res && res.code === 200 && res.data) {
          that.specImagePath = res.data
        }
      }).catch(function() {
        that.$message.error('图片上传失败，规格值图片不会被保存')
      })
    },
    clearText() {
      var path = this.specImagePath
      this.resetOcrFields()
      // 清除文字不影响已加载的规格图片
      this.specImagePath = path
    },
    onTextEdited() {
      // 手动输入/修改文本后也允许直接智能识别或应用
      if (this.ocrFields.rawText) {
        this.hasOcrResult = true
      }
    },
    /** 拍照/选图后自动识别文字，结果输出到编辑框 */
    startRecognition(file) {
      var keepText = this.ocrFields.rawText
      this.resetOcrFields()
      this.ocrFields.rawText = keepText || ''
      this.ocrLoading = true
      var that = this
      // 使用 Base64 JSON 接口，避免 multipart 在部分代理/网关下出现 Network Error
      this.fileToBase64(file).then(function(imageBase64) {
        return postAction('/ocr/recognizeProductByBase64', { imageBase64: imageBase64 })
      }).then(function(res) {
        that.ocrLoading = false
        that.applyOcrSuccess(res)
      }).catch(function(err) {
        that.ocrLoading = false
        that.$message.error(that.formatOcrRequestError(err))
      })
    },
    /** 对编辑框中的文本做智能识别，解析成属性字段 */
    smartRecognize() {
      var text = this.ocrFields.rawText || ''
      if (!text.trim()) {
        this.$message.warning('请先输入或识别文字')
        return
      }
      this.ocrLoading = true
      var that = this
      postAction('/ocr/parseProductText', { text: text }).then(function(res) {
        that.ocrLoading = false
        that.applyOcrSuccess(res)
      }).catch(function(err) {
        that.ocrLoading = false
        that.$message.error(that.formatOcrRequestError(err))
      })
    },
    fileToBase64(file) {
      return new Promise(function(resolve, reject) {
        var reader = new FileReader()
        reader.onload = function() { resolve(reader.result) }
        reader.onerror = function() { reject(new Error('图片读取失败')) }
        reader.readAsDataURL(file)
      })
    },
    formatOcrRequestError(err) {
      var errorData = err && err.response && err.response.data
      if (errorData === 'loginOut') {
        return '登录状态已失效，请重新登录后再识别图片。'
      }
      if (!err || !err.response) {
        return '识别请求失败：无法连接后端（请确认后端 9999 已启动，且通过前端开发地址访问，例如 http://localhost:3000）'
      }
      return '识别请求失败：' + (err.message || '未知错误')
    },
    applyOcrSuccess(res) {
      if (!(res && res.code === 200 && res.data)) {
        var msg = (res && res.data && res.data.message) ? res.data.message : '识别失败'
        this.$message.error(msg)
        return false
      }
      this.ocrFields.rawText = res.data.rawText || this.ocrFields.rawText || ''
      if (this.extractOnly) {
        this.hasOcrResult = !!this.ocrFields.rawText
        return true
      }
      var visibleProps = this.visibleOcrProps
      for (var i = 0; i < visibleProps.length; i++) {
        var prop = visibleProps[i]
        var value = ''
        if (res.data[prop.field] !== undefined && res.data[prop.field] !== null && res.data[prop.field] !== '') {
          value = res.data[prop.field]
        } else if (res.data[prop.nativeName] !== undefined && res.data[prop.nativeName] !== null && res.data[prop.nativeName] !== '') {
          value = res.data[prop.nativeName]
        } else if (res.data[prop.anotherName] !== undefined && res.data[prop.anotherName] !== null && res.data[prop.anotherName] !== '') {
          value = res.data[prop.anotherName]
        }
        this.$set(this.ocrFields, prop.field, value)
      }
      this.hasOcrResult = true
      return true
    },
    copyRawText() {
      this.copyText(this.ocrFields.rawText || '')
    },
    copyText(text) {
      if (!text) {
        this.$message.warning('没有可复制的文字')
        return
      }
      var that = this
      var copyFallback = function() {
        var textarea = document.createElement('textarea')
        textarea.value = text
        textarea.setAttribute('readonly', '')
        textarea.style.position = 'fixed'
        textarea.style.left = '-9999px'
        document.body.appendChild(textarea)
        textarea.select()
        var copied = false
        try {
          copied = document.execCommand('copy')
        } catch (e) {
          copied = false
        }
        document.body.removeChild(textarea)
        if (copied) {
          that.$message.success('识别文字已复制')
        } else {
          that.$message.error('复制失败，请手动选择文字复制')
        }
      }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function() {
          that.$message.success('识别文字已复制')
        }).catch(copyFallback)
      } else {
        copyFallback()
      }
    },
    applyResult() {
      if (this.hasOcrResult) {
        var output = { rawText: this.ocrFields.rawText }
        var visibleProps = this.visibleOcrProps
        for (var i = 0; i < visibleProps.length; i++) {
          var field = visibleProps[i].field
          output[field] = this.ocrFields[field] || ''
        }
        this.$emit('ok', output)
      }
    }
  }
}
</script>

<style scoped>
.ocr-text-panel {
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.ocr-raw-text {
  border: none !important;
  box-shadow: none !important;
  border-radius: 0 !important;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 22px;
  resize: vertical;
}

.ocr-text-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-top: 1px solid #f0f0f0;
}

.ocr-text-toolbar-left {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ocr-file-input {
  display: none;
}

.ocr-camera-wrap {
  width: 100%;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}

.ocr-camera-video {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: contain;
  background: #000;
}

.ocr-toolbar-btn {
  padding: 0 8px;
  color: #595959;
  font-size: 14px;
}

.ocr-toolbar-btn .anticon {
  margin-right: 4px;
  font-size: 16px;
}

.ocr-toolbar-clear {
  color: #bfbfbf;
}

.ocr-ai-btn {
  min-width: 120px;
  height: 36px;
  border-radius: 18px;
  font-size: 15px;
}
</style>
