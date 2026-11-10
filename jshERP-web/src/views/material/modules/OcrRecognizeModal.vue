<template>
  <a-modal
    :visible="visible"
    :width="modalWidth"
    :footer="null"
    :maskClosable="false"
    :mask="!isMobileView"
    :closable="!isMobileView"
    :wrapClassName="isMobileView ? 'ocr-mobile-modal' : ''"
    @cancel="handleCancel">
    <!-- 自定义标题栏：手机端为标准页面顶栏，桌面端保留按钮 -->
    <template slot="title">
      <div v-if="isMobileView" class="ocr-mobile-header">
        <a-icon type="arrow-left" class="ocr-mobile-back" @click="handleCancel" />
        <span class="ocr-mobile-title">{{ extractOnly ? '图片文字提取' : '图片识别' }}</span>
        <a-icon v-if="!extractOnly" type="control" class="ocr-mobile-action ocr-mobile-action-last" @click="openPropertyConfig" />
      </div>
      <div v-else>
        <span>{{ extractOnly ? '图片文字提取' : '图片识别' }}</span>
        <a-button v-if="!extractOnly" size="small" style="margin-left:12px" @click="openPropertyConfig">
          <a-icon type="control" />属性显示设置
        </a-button>
      </div>
    </template>

    <!-- 标准图片识别框：文本编辑 + 图片预览 + 拍照/图片/清除 + 智能识别 -->
    <image-recognize-box
      v-model="ocrFields.rawText"
      :placeholder="ocrLoading ? '正在识别图片文字...' : '识别结果会显示在这里，也可以直接输入或粘贴文字'"
      :image-url="previewImage"
      :loading="ocrLoading"
      preview-text="图片已加载，点击图片可放大查看"
      :smart-text="extractOnly ? '复制文字' : '智能识别'"
      :smart-loading="!extractOnly && ocrLoading"
      :clear-disabled="!ocrFields.rawText && !ocrLoading"
      @input="onTextEdited"
      @camera="triggerCamera"
      @gallery="triggerGallery"
      @clear="clearText"
      @smart="extractOnly ? copyRawText() : smartRecognize()"
      @preview="previewVisible = true" />
    <input ref="cameraInput" type="file" accept="image/*" capture="environment" class="ocr-file-input" @change="onPickImage" />
    <input ref="galleryInput" type="file" accept="image/*" class="ocr-file-input" @change="onPickImage" />

    <!-- 图片放大预览 -->
    <a-modal
      :visible="previewVisible"
      :footer="null"
      :width="'96%'"
      :zIndex="1200"
      wrapClassName="ocr-preview-modal"
      @cancel="previewVisible = false">
      <img :src="previewImage" class="ocr-preview-full" @click="previewVisible = false" />
    </a-modal>

    <!-- 属性框（始终显示）：手机端每行一项，标题与值并列 -->
    <div v-if="!extractOnly" style="margin-top:4px">
      <a-divider orientation="left" style="margin:12px 0 8px;font-size:13px">属性信息</a-divider>
      <a-row :gutter="isMobileView ? 0 : 8">
        <a-col :span="isMobileView ? 24 : 12" v-for="prop in visibleOcrProps" :key="prop.field">
          <div class="ocr-prop-item" :class="{ 'ocr-prop-item-mobile': isMobileView }">
            <div class="ocr-prop-label" style="font-size:13px;color:rgba(0,0,0,0.85);line-height:20px;margin-bottom:0px">{{ prop.anotherName }}</div>
            <a-input v-model="ocrFields[prop.field]" :placeholder="ocrLoading ? '识别中...' : ''" size="small" :class="{ 'ocr-prop-input': isMobileView }" />
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
      :width="isMobileView ? '94%' : 560"
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

    <!-- 属性显示设置弹层 -->
    <a-modal
      title="属性显示设置"
      :visible="propertyConfigVisible"
      :width="isMobileView ? '94%' : 500"
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
import { postAction, getAction } from '@/api/manage'
import { axios as uploadHttp } from '@/utils/request'
import ImageRecognizeBox from '@/components/ocr/ImageRecognizeBox'
import { recognizeTextFromImage, parseProductText } from '@/views/material/utils/productTextScan'

// 基础属性列表：与商品新增页「基础属性设置」的可选项保持一致
// （即 MaterialModal.basePropertyList 中 basePropertyFields 包含的属性，顺序、名称、默认显示状态一致）
var BASE_PROPERTIES = [
  { nativeName: '名称', field: 'name', visible: true, sort: 0, anotherName: '名称', fixed: true },
  { nativeName: '单位', field: 'unit', visible: true, sort: 1, anotherName: '单位', fixed: true },
  { nativeName: '品牌', field: 'brand', visible: true, sort: 2, anotherName: '品牌', fixed: true },
  { nativeName: '助记码', field: 'mnemonic', visible: true, sort: 3, anotherName: '助记码', fixed: true },
  { nativeName: '类别', field: 'categoryId', visible: true, sort: 4, anotherName: '类别', fixed: true },
  { nativeName: '填充物重量', field: 'weight', visible: true, sort: 5, anotherName: '填充物重量', fixed: true },
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
    ImageRecognizeBox
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
      // 手机端适配：窄屏时弹窗宽度自适应
      isMobileView: window.innerWidth <= 768,
      ocrLoading: false,
      hasOcrResult: false,
      ocrFields: { rawText: '' },
      previewImage: '',
      previewVisible: false,
      specImagePath: '',
      cameraVisible: false,
      cameraStream: null,
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
    // 手机端弹窗宽度占满可视区，桌面端保持原宽度
    modalWidth() {
      // 手机端改为全屏标准页面，不再保留左右边距
      if (this.isMobileView) return '100%'
      return this.extractOnly ? 480 : 453
    },
    visibleOcrProps() {
      return this.ocrPropertyList.filter(function(p) { return p.visible })
    }
  },
  mounted() {
    window.addEventListener('resize', this.onWindowResize)
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
    window.removeEventListener('resize', this.onWindowResize)
    this.closeCamera()
  },
  methods: {
    // 手机端适配：窗口宽度变化时切换弹窗宽度
    onWindowResize() {
      this.isMobileView = window.innerWidth <= 768
    },
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
    // 兼容本地缓存/后端返回的多种结构，统一取出属性配置数组
    parsePropertyConfig(raw) {
      if (!raw) return null
      var data = raw
      if (typeof data === 'string') {
        try { data = JSON.parse(data) } catch (e) { return null }
      }
      if (data && !Array.isArray(data)) {
        if (Array.isArray(data.value)) data = data.value
        else if (Array.isArray(data.config)) data = data.config
        else return null
      }
      return Array.isArray(data) ? data : null
    },
    // 把配置中的 visible 应用到属性列表，返回是否有命中
    applyOcrVisibleConfig(list, config) {
      if (!config || !config.length) return false
      var hit = false
      for (var i = 0; i < list.length; i++) {
        var item = list[i]
        var cfg = config.find(function(c) { return c.field === item.field })
        if (cfg) {
          item.visible = cfg.visible !== false
          hit = true
        }
      }
      return hit
    },
    loadOcrPropertyList() {
      var that = this
      var baseConfig = this.parsePropertyConfig(Vue.ls.get('material_base_property_config'))
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
      // 本地缓存先渲染，保证打开弹层立即有内容
      this.applyOcrVisibleConfig(allProps, this.parsePropertyConfig(Vue.ls.get('ocrPropertyList')))
      this.ocrPropertyList = allProps
      // 再读后端共享配置（与「基础属性设置」同一套存储方式），保证换设备、远端重新登录后仍生效
      getAction('/platformConfig/getUiConfig', { key: 'ocr_property_setting' }).then(function(res) {
        if (!(res && res.code === 200 && res.data)) return
        var decoded = res.data
        if (typeof decoded === 'string') {
          try { decoded = JSON.parse(decoded) } catch (e) { decoded = null }
        }
        var remoteConfig = decoded && Array.isArray(decoded.config) ? decoded.config : that.parsePropertyConfig(decoded)
        if (remoteConfig && remoteConfig.length && that.applyOcrVisibleConfig(that.ocrPropertyList, remoteConfig)) {
          that.ocrPropertyList = that.ocrPropertyList.slice()
          Vue.ls.set('ocrPropertyList', remoteConfig, 365 * 24 * 60 * 60 * 1000)
        }
      }).catch(function() {})
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
      var snapshot = this.ocrPropertyList.map(function(p) {
        return { field: p.field, nativeName: p.nativeName, visible: p.visible, anotherName: p.anotherName }
      })
      // 本地缓存 + 后端共享配置双写，避免重开弹层、换设备或远端重新登录后失效
      Vue.ls.set('ocrPropertyList', snapshot, 365 * 24 * 60 * 60 * 1000)
      postAction('/platformConfig/saveUiConfig', {
        key: 'ocr_property_setting',
        value: JSON.stringify({ config: snapshot })
      }).catch(function() {})
      this.ocrPropertyList = this.ocrPropertyList.slice()
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
        that.previewImage = imageBase64
        return recognizeTextFromImage(imageBase64)
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
      parseProductText(text).then(function(res) {
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
      var data = res.data || {}
      // 识别后的文字原文完整回填到文本框
      var rawText = typeof data.rawText === 'string' ? data.rawText : ''
      if (rawText) {
        this.ocrFields.rawText = rawText
      }
      if (this.extractOnly) {
        this.hasOcrResult = !!this.ocrFields.rawText
        return true
      }
      // 提取到的值回填到对应属性输入框：不区分是否勾选显示，避免隐藏属性丢值
      var props = this.ocrPropertyList.length ? this.ocrPropertyList : BASE_PROPERTIES
      for (var i = 0; i < props.length; i++) {
        var prop = props[i]
        var value = ''
        if (this.hasDataValue(data, prop.field)) {
          value = data[prop.field]
        } else if (this.hasDataValue(data, prop.nativeName)) {
          value = data[prop.nativeName]
        } else if (this.hasDataValue(data, prop.anotherName)) {
          value = data[prop.anotherName]
        }
        this.$set(this.ocrFields, prop.field, value === null || value === undefined ? '' : String(value).trim())
      }
      // 后端未解析出的属性，用原文逐行的「标题+值」补充（兼容各类分隔符）
      this.fillPropsFromRawText(this.ocrFields.rawText, props)
      this.hasOcrResult = true
      return true
    },
    hasDataValue(data, key) {
      if (!key) return false
      var value = data[key]
      return value !== undefined && value !== null && value !== ''
    },
    /** 逐行匹配「属性名称 + 分隔符 + 值」，如「名称：儿童T恤」→ 名称输入框填 儿童T恤 */
    fillPropsFromRawText(rawText, props) {
      var text = rawText || ''
      if (!text) return
      var lines = text.split(/\r?\n/)
      for (var i = 0; i < lines.length; i++) {
        var line = lines[i].trim()
        if (!line) continue
        for (var j = 0; j < props.length; j++) {
          var prop = props[j]
          if (this.ocrFields[prop.field]) continue
          var value = this.matchLabelValue(line, prop)
          if (value) {
            this.$set(this.ocrFields, prop.field, value)
          }
        }
      }
    },
    matchLabelValue(line, prop) {
      var labels = [prop.nativeName, prop.anotherName]
      for (var i = 0; i < labels.length; i++) {
        var label = (labels[i] || '').trim()
        if (!label || line.indexOf(label) !== 0) continue
        var rest = line.substring(label.length)
        // 标题后必须紧跟分隔符（冒号、等号、竖线、横杠、顿号、空格等），避免误匹配
        if (!/^[\s:：=＝|｜\-–—、，,.;；。]/.test(rest)) continue
        var value = rest.replace(/^[\s:：=＝|｜\-–—、，,.;；。]+/, '').trim()
        if (value) return value
      }
      return ''
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

.ocr-preview-full {
  display: block;
  width: 100%;
  max-height: 78vh;
  object-fit: contain;
  cursor: zoom-out;
}
/* 手机端适配：属性行字号防止聚焦放大 */
@media (max-width: 768px) {
  .ocr-prop-item .ant-input {
    font-size: 16px;
  }
  /* 手机端属性行：标题与值并列，输入框无边框 */
  .ocr-prop-item-mobile {
    display: flex;
    align-items: center;
    min-height: 44px;
    border-bottom: 1px solid #f0f0f0;
  }
  .ocr-prop-item-mobile .ocr-prop-label {
    width: 88px;
    flex: none;
    margin-bottom: 0 !important;
  }
  .ocr-prop-item-mobile .ant-input {
    flex: 1;
    min-width: 0;
    border: none !important;
    background: transparent !important;
    box-shadow: none !important;
    padding-left: 0;
    font-size: 14px;
  }
  .ocr-prop-item-mobile .ant-input:focus {
    border: none !important;
    box-shadow: none !important;
  }
}
/* 手机端标准页面顶栏 */
.ocr-mobile-header {
  display: flex;
  align-items: center;
  height: 49px;
  color: #fff;
}
/* 返回按钮：与底边栏等高（58px）的正方形，左边贴屏幕左边 */
.ocr-mobile-back {
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
.ocr-mobile-title {
  flex: 1;
  text-align: center;
  font-size: 16px;
  font-weight: 500;
  color: #fff;
}
.ocr-mobile-action {
  font-size: 18px;
  color: #fff;
  cursor: pointer;
  padding: 6px;
}
.ocr-mobile-action-last {
  margin-left: 12px;
}
</style>
<style>
/* 手机端把弹窗变成标准全屏页面（antd 弹窗挂载在 body，需非 scoped 样式） */
.ocr-mobile-modal .ant-modal {
  top: 0;
  margin: 0;
  padding-bottom: 0;
  width: 100% !important;
  max-width: 100%;
}
.ocr-mobile-modal .ant-modal-content {
  display: flex;
  flex-direction: column;
  height: 100vh;
  border-radius: 0;
}
.ocr-mobile-modal .ant-modal-header {
  flex: none;
  padding: 0 8px 0 0;
  background: #1890ff;
  border-radius: 0;
}
.ocr-mobile-modal .ant-modal-title {
  color: #fff;
}
.ocr-mobile-modal .ant-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}
</style>
