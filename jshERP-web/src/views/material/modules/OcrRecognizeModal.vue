<template>
  <a-modal
    :visible="visible"
    :width="extractOnly ? '92vw' : 680"
    :bodyStyle="extractOnly ? extractModalBodyStyle : {}"
    :footer="null"
    :maskClosable="false"
    @cancel="$emit('cancel')">
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

    <!-- 规格图片识别：大窗口左右分栏 -->
    <div v-if="extractOnly" class="ocr-extract-workspace">
      <div class="ocr-extract-image-column">
        <div class="ocr-extract-pane-title">
          <span>图片</span>
          <span class="ocr-zoom-label">{{ Math.round(imageZoom * 100) }}%</span>
        </div>
        <div class="ocr-image-viewport" @wheel.prevent="onImageWheel" @mousemove="updateImageSelection" @mouseup="finishImageSelection" @mouseleave="finishImageSelection">
          <div v-if="imageUrl" class="ocr-image-canvas" :style="imageCanvasStyle" @mousedown.prevent="startImageSelection" @click="handleImageClick">
            <img ref="previewImage" :src="imageUrl" :style="previewImageStyle" draggable="false" @load="onPreviewImageLoad" />
            <div v-if="selectionVisible" class="ocr-image-selection" :style="selectionStyle"></div>
          </div>
          <div v-else-if="ocrLoading" class="ocr-image-placeholder"><a-icon type="loading" /> 正在加载图片并识别...</div>
          <div v-else class="ocr-image-placeholder">图片加载失败</div>
        </div>
        <div class="ocr-image-toolbar">
          <span>左键点击提取全文，拖动框选区域提取文字</span>
          <span class="ocr-zoom-controls">
            <a-button size="small" @click="changeImageZoom(-0.1)">−</a-button>
            <a-button size="small" @click="resetImageZoom">适合窗口</a-button>
            <a-button size="small" @click="changeImageZoom(0.1)">＋</a-button>
            <a-button size="small" type="primary" :loading="ocrLoading" @click="recognizeWholeImage">提取全文</a-button>
          </span>
        </div>
      </div>
      <div class="ocr-extract-text-column">
        <div class="ocr-extract-pane-title">
          <span>提取文字</span>
          <a-button size="small" type="link" :disabled="!ocrFields.rawText" @click="copyVisibleText"><a-icon type="copy" />复制文字</a-button>
        </div>
        <a-textarea ref="extractTextArea" v-model="ocrFields.rawText" readOnly class="ocr-extracted-text" :placeholder="ocrLoading ? '正在识别...' : '识别结果会显示在这里，可拖选文字后复制'" />
        <div class="ocr-extract-status">{{ extractionStatus }}</div>
      </div>
    </div>

    <!-- 普通商品信息图片识别 -->
    <div v-else style="display:flex;gap:12px;margin-top:4px" @click="hideImageContextMenu">
      <!-- 左侧：图片上传/预览（正方形） -->
      <div style="width:200px;flex-shrink:0">
        <a-upload
          name="file"
          :multiple="false"
          :before-upload="beforeUpload"
          :showUploadList="false"
          accept="image/*"
          style="display:block"
        >
          <!-- 未上传：上传框 -->
          <div v-if="!ocrLoading && !imageUrl" style="width:200px;height:200px;border:1px dashed #d9d9d9;border-radius:4px;text-align:center;cursor:pointer;display:flex;flex-direction:column;align-items:center;justify-content:center;">
            <a-icon type="cloud-upload" style="font-size:32px;color:#1890ff" />
            <p style="margin-top:6px;color:#999;font-size:12px">点击上传图片</p>
          </div>
          <!-- 识别中：loading -->
          <div v-if="ocrLoading" style="width:200px;height:200px;border:1px dashed #d9d9d9;border-radius:4px;text-align:center;display:flex;flex-direction:column;align-items:center;justify-content:center;">
            <a-icon type="loading" style="font-size:32px;color:#1890ff" />
            <p style="margin-top:6px;color:#999;font-size:12px">识别中...</p>
          </div>
          <!-- 识别完成：图片预览 -->
          <div v-if="imageUrl && !ocrLoading" style="width:200px;height:200px;border:1px solid #d9d9d9;border-radius:4px;overflow:hidden;cursor:copy;position:relative;" title="左键复制全文，右键提取文字" @click.stop.prevent="copyRawText" @contextmenu.stop.prevent="showImageContextMenu">
            <img :src="imageUrl" style="width:100%;height:100%;object-fit:contain;" />
            <div style="position:absolute;bottom:0;left:0;right:0;background:rgba(0,0,0,0.5);color:#fff;text-align:center;font-size:11px;padding:3px 0;pointer-events:none;">左键复制全文 · 右键提取文字</div>
          </div>
        </a-upload>
      </div>
      <!-- 右侧：原始文本（底边与图片框对齐） -->
      <div style="flex:1;display:flex;flex-direction:column;min-width:0">
        <div style="display:flex;align-items:center;justify-content:space-between;font-size:14px;color:rgba(0,0,0,0.85);line-height:22px;margin-bottom:2px">
          <span>原始文本</span>
          <a-button v-if="ocrFields.rawText" size="small" type="link" style="padding:0" @click="copyRawText">
            <a-icon type="copy" />复制文字
          </a-button>
        </div>
        <a-textarea v-model="ocrFields.rawText" readOnly placeholder="识别后显示原始文本" style="flex:1;font-size:12px;color:#666;resize:none;min-height:176px" />
      </div>
    </div>

    <!-- 普通图片识别的右键菜单 -->
    <div v-if="!extractOnly && imageContextMenuVisible" :style="imageContextMenuStyle" class="ocr-image-context-menu" @click.stop>
      <div class="ocr-image-context-item" :class="{ disabled: !ocrFields.rawText }" @click="openExtractedText">
        <a-icon type="scan" />提取文字
      </div>
    </div>

    <!-- 可选择的提取文字窗口 -->
    <a-modal v-if="!extractOnly"
      title="提取文字"
      :visible="extractedTextVisible"
      :width="520"
      :maskClosable="false"
      @cancel="extractedTextVisible=false">
      <a-textarea ref="extractedTextArea" :value="ocrFields.rawText" readOnly :rows="12" style="resize:vertical;user-select:text" placeholder="图片识别完成后，提取的文字会显示在这里" />
      <template slot="footer">
        <a-button @click="extractedTextVisible=false">关闭</a-button>
        <a-button type="primary" :disabled="!ocrFields.rawText" @click="copySelectedExtractedText">复制选中内容</a-button>
      </template>
    </a-modal>

    <!-- 属性框（始终显示） -->
    <div v-if="!extractOnly" style="margin-top:4px">
      <a-divider orientation="left" style="margin:4px 0 8px;font-size:13px">属性信息</a-divider>
      <a-row v-if="!extractOnly" :gutter="8">
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
      <a-button size="small" @click="$emit('cancel')" style="margin-right:8px">取消</a-button>
      <a-button v-if="!extractOnly" size="small" type="primary" :disabled="!hasOcrResult" @click="applyResult">应用</a-button>
    </div>

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
import { getAction, postAction } from '@/api/manage'
import OcrApiConfigModal from './OcrApiConfigModal'

// 基础属性列表（与 MaterialModal basePropertyList 同步）
var BASE_PROPERTIES = [
  { nativeName: '名称', field: 'name', visible: true, sort: 0, anotherName: '名称', fixed: true },
  { nativeName: '单位', field: 'unit', visible: true, sort: 1, anotherName: '单位', fixed: true },
  { nativeName: '品牌', field: 'brand', visible: true, sort: 2, anotherName: '品牌', fixed: true },
  { nativeName: '助记码', field: 'mnemonic', visible: true, sort: 3, anotherName: '助记码', fixed: true },
  { nativeName: '类别', field: 'categoryId', visible: true, sort: 4, anotherName: '类别', fixed: true },
  { nativeName: '基础重量', field: 'weight', visible: true, sort: 5, anotherName: '基础重量', fixed: true },
  { nativeName: '仓位货架', field: 'position', visible: true, sort: 6, anotherName: '仓位货架', fixed: true },
  { nativeName: '制造商', field: 'mfrs', visible: true, sort: 7, anotherName: '制造商', fixed: true },
  { nativeName: '扩展1', field: 'otherField1', visible: true, sort: 8, anotherName: '面料', fixed: true },
  { nativeName: '扩展2', field: 'otherField2', visible: true, sort: 9, anotherName: '安全类别', fixed: true },
  { nativeName: '扩展3', field: 'otherField3', visible: true, sort: 10, anotherName: '执行标准', fixed: true },
  { nativeName: '商品ID', field: 'id', visible: true, sort: 11, anotherName: '商品ID', fixed: true },
  { nativeName: '条码', field: 'barCode', visible: true, sort: 12, anotherName: '条码', fixed: true },
  { nativeName: '扩展4', field: 'otherField4', visible: true, sort: 13, anotherName: '扩展4', fixed: false },
  { nativeName: '扩展5', field: 'otherField5', visible: true, sort: 14, anotherName: '扩展5', fixed: false },
  { nativeName: '扩展6', field: 'otherField6', visible: true, sort: 15, anotherName: '等级', fixed: false },
  { nativeName: '扩展7', field: 'otherField7', visible: true, sort: 16, anotherName: '检验', fixed: false },
  { nativeName: '面料', field: 'fabric', visible: true, sort: 17, anotherName: '面料', fixed: true },
  { nativeName: '安全类别', field: 'safetyCategory', visible: true, sort: 18, anotherName: '安全类别', fixed: true },
  { nativeName: '执行标准', field: 'executiveStandard', visible: true, sort: 19, anotherName: '执行标准', fixed: true },
  { nativeName: '等级', field: 'grade', visible: true, sort: 20, anotherName: '等级', fixed: false },
  { nativeName: '检验', field: 'inspection', visible: true, sort: 21, anotherName: '检验', fixed: false }
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
      imageUrl: '',
      imageNaturalWidth: 0,
      imageNaturalHeight: 0,
      imageZoom: 1,
      imageSelectionStart: null,
      imageSelectionEnd: null,
      isSelectingImage: false,
      selectionVisible: false,
      suppressNextImageClick: false,
      extractionStatus: '',
      imageContextMenuVisible: false,
      imageContextMenuStyle: {},
      extractedTextVisible: false,
      ocrFields: {},
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
    extractModalBodyStyle() {
      return { height: 'calc(90vh - 70px)', padding: '12px', overflow: 'hidden' }
    },
    imageCanvasStyle() {
      return {
        width: Math.max(1, this.imageNaturalWidth * this.imageZoom) + 'px',
        height: Math.max(1, this.imageNaturalHeight * this.imageZoom) + 'px'
      }
    },
    previewImageStyle() {
      return {
        width: Math.max(1, this.imageNaturalWidth * this.imageZoom) + 'px',
        height: Math.max(1, this.imageNaturalHeight * this.imageZoom) + 'px'
      }
    },
    selectionStyle() {
      if (!this.imageSelectionStart || !this.imageSelectionEnd) return {}
      var left = Math.min(this.imageSelectionStart.x, this.imageSelectionEnd.x)
      var top = Math.min(this.imageSelectionStart.y, this.imageSelectionEnd.y)
      var width = Math.abs(this.imageSelectionEnd.x - this.imageSelectionStart.x)
      var height = Math.abs(this.imageSelectionEnd.y - this.imageSelectionStart.y)
      return {
        left: left * this.imageZoom + 'px',
        top: top * this.imageZoom + 'px',
        width: width * this.imageZoom + 'px',
        height: height * this.imageZoom + 'px'
      }
    },
    visibleOcrProps() {
      return this.ocrPropertyList.filter(function(p) { return p.visible })
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.loadOcrPropertyList()
        this.resetOcrFields()
        if (this.extractOnly && this.imageFile) {
          this.loadImageForExtraction(this.imageFile)
        }
      }
    }
  },
  methods: {
    resetOcrFields() {
      if (this.imageUrl && this.imageUrl.indexOf('blob:') === 0) {
        URL.revokeObjectURL(this.imageUrl)
      }
      this.ocrFields = { rawText: '' }
      this.hasOcrResult = false
      this.ocrLoading = false
      this.imageUrl = ''
      this.imageNaturalWidth = 0
      this.imageNaturalHeight = 0
      this.imageZoom = 1
      this.imageSelectionStart = null
      this.imageSelectionEnd = null
      this.isSelectingImage = false
      this.selectionVisible = false
      this.suppressNextImageClick = false
      this.extractionStatus = ''
      this.imageContextMenuVisible = false
      this.extractedTextVisible = false
    },
    loadOcrPropertyList() {
      var baseConfig = Vue.ls.get('material_base_property_config')
      var allProps = BASE_PROPERTIES.map(function(p) { return Object.assign({}, p) })
      if (baseConfig && baseConfig.length) {
        for (var i = 0; i < allProps.length; i++) {
          var cfg = baseConfig.find(function(c) { return c.field === allProps[i].field })
          if (cfg) {
            allProps[i].visible = cfg.visible !== false
            allProps[i].anotherName = cfg.anotherName || allProps[i].nativeName
          }
        }
      }
      var visibleBaseProps = allProps.filter(function(p) { return p.visible })
      var ocrConfig = Vue.ls.get('ocrPropertyList')
      if (ocrConfig && ocrConfig.length) {
        for (var j = 0; j < visibleBaseProps.length; j++) {
          var ocrCfg = ocrConfig.find(function(c) { return c.field === visibleBaseProps[j].field })
          if (ocrCfg) {
            visibleBaseProps[j].visible = ocrCfg.visible !== false
            visibleBaseProps[j].anotherName = ocrCfg.anotherName || visibleBaseProps[j].nativeName
          }
        }
      }
      this.ocrPropertyList = visibleBaseProps
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
    beforeUpload(file) {
      this.startRecognition(file)
      return false
    },
    loadImageForExtraction(file) {
      if (this.imageUrl && this.imageUrl.indexOf('blob:') === 0) {
        URL.revokeObjectURL(this.imageUrl)
      }
      this.imageNaturalWidth = 0
      this.imageNaturalHeight = 0
      this.imageZoom = 1
      this.imageUrl = URL.createObjectURL(file)
      this.ocrFields = { rawText: '' }
      this.hasOcrResult = false
      this.ocrLoading = false
      this.extractionStatus = '图片已加载。左键点击图片或点“提取全文”识别全文，拖动框选区域可提取局部文字。'
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
        if (this.extractOnly) this.extractionStatus = msg
        this.$message.error(msg)
        return false
      }
      this.ocrFields.rawText = res.data.rawText || ''
      if (this.extractOnly) {
        this.hasOcrResult = true
        this.extractionStatus = this.ocrFields.rawText
          ? '全文识别完成。拖动框选图片区域可识别局部文字。'
          : '未识别到文字，可尝试框选图片中的文字区域。'
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
    startRecognition(file) {
      this.ocrLoading = true
      this.hasOcrResult = false
      this.ocrFields = { rawText: '' }
      this.imageNaturalWidth = 0
      this.imageNaturalHeight = 0
      this.selectionVisible = false
      // 创建图片预览 URL
      if (this.imageUrl) {
        URL.revokeObjectURL(this.imageUrl)
      }
      this.imageUrl = URL.createObjectURL(file)
      if (this.extractOnly) this.extractionStatus = '正在识别整张图片...'
      var that = this
      // 使用 Base64 JSON 接口，避免 multipart 在部分代理/网关下出现 Network Error
      this.fileToBase64(file).then(function(imageBase64) {
        return postAction('/ocr/recognizeProductByBase64', { imageBase64: imageBase64 })
      }).then(function(res) {
        that.ocrLoading = false
        that.applyOcrSuccess(res)
      }).catch(function(err) {
        that.ocrLoading = false
        var message = that.formatOcrRequestError(err)
        if (that.extractOnly) that.extractionStatus = message
        that.$message.error(message)
      })
    },
    copyRawText() {
      this.copyText(this.ocrFields.rawText || '')
    },
    onPreviewImageLoad(event) {
      this.imageNaturalWidth = event.target.naturalWidth || 0
      this.imageNaturalHeight = event.target.naturalHeight || 0
    },
    onImageWheel(event) {
      this.changeImageZoom(event.deltaY < 0 ? 0.1 : -0.1)
    },
    changeImageZoom(delta) {
      this.imageZoom = Math.max(0.2, Math.min(4, Math.round((this.imageZoom + delta) * 10) / 10))
    },
    resetImageZoom() {
      var viewport = this.$el.querySelector('.ocr-image-viewport')
      if (!viewport || !this.imageNaturalWidth || !this.imageNaturalHeight) {
        this.imageZoom = 1
        return
      }
      var ratio = Math.min(viewport.clientWidth / this.imageNaturalWidth, viewport.clientHeight / this.imageNaturalHeight, 1)
      this.imageZoom = Math.max(0.2, Math.round(ratio * 10) / 10)
    },
    imagePointAt(clientX, clientY) {
      var image = this.$refs.previewImage
      if (!image) return null
      var rect = image.getBoundingClientRect()
      return {
        x: Math.max(0, Math.min(this.imageNaturalWidth, (clientX - rect.left) / this.imageZoom)),
        y: Math.max(0, Math.min(this.imageNaturalHeight, (clientY - rect.top) / this.imageZoom))
      }
    },
    startImageSelection(event) {
      if (event.button !== 0 || !this.imageNaturalWidth) return
      var point = this.imagePointAt(event.clientX, event.clientY)
      if (!point) return
      this.isSelectingImage = true
      this.selectionVisible = false
      this.suppressNextImageClick = false
      this.imageSelectionStart = point
      this.imageSelectionEnd = point
    },
    updateImageSelection(event) {
      if (!this.isSelectingImage) return
      var point = this.imagePointAt(event.clientX, event.clientY)
      if (!point) return
      this.imageSelectionEnd = point
      var dx = Math.abs(point.x - this.imageSelectionStart.x)
      var dy = Math.abs(point.y - this.imageSelectionStart.y)
      if (dx > 4 || dy > 4) {
        this.selectionVisible = true
        this.suppressNextImageClick = true
      }
    },
    finishImageSelection() {
      if (!this.isSelectingImage) return
      this.isSelectingImage = false
      if (!this.selectionVisible || !this.imageSelectionStart || !this.imageSelectionEnd) return
      var x = Math.round(Math.min(this.imageSelectionStart.x, this.imageSelectionEnd.x))
      var y = Math.round(Math.min(this.imageSelectionStart.y, this.imageSelectionEnd.y))
      var width = Math.round(Math.abs(this.imageSelectionEnd.x - this.imageSelectionStart.x))
      var height = Math.round(Math.abs(this.imageSelectionEnd.y - this.imageSelectionStart.y))
      if (width < 8 || height < 8) {
        this.selectionVisible = false
        return
      }
      this.recognizeImageRegion(x, y, width, height)
    },
    handleImageClick() {
      if (this.suppressNextImageClick) {
        this.suppressNextImageClick = false
        return
      }
      this.recognizeWholeImage()
    },
    recognizeWholeImage() {
      if (!this.imageFile) {
        this.$message.warning('原始图片不可用，请重新上传')
        return
      }
      this.selectionVisible = false
      this.startRecognition(this.imageFile)
    },
    recognizeImageRegion(x, y, width, height) {
      var image = this.$refs.previewImage
      if (!image) return
      var canvas = document.createElement('canvas')
      canvas.width = width
      canvas.height = height
      var context = canvas.getContext('2d')
      context.drawImage(image, x, y, width, height, 0, 0, width, height)
      var that = this
      this.ocrLoading = true
      this.extractionStatus = '正在识别框选区域...'
      canvas.toBlob(function(blob) {
        if (!blob) {
          that.ocrLoading = false
          that.extractionStatus = '图片区域截取失败，请重新框选。'
          return
        }
        that.fileToBase64(new File([blob], 'ocr-region.png', { type: 'image/png' })).then(function(imageBase64) {
          return postAction('/ocr/recognizeProductByBase64', { imageBase64: imageBase64 })
        }).then(function(res) {
          that.ocrLoading = false
          if (res && res.code === 200 && res.data) {
            that.ocrFields.rawText = res.data.rawText || ''
            that.extractionStatus = that.ocrFields.rawText ? '框选区域识别完成，文字已复制并显示在右侧。' : '框选区域未识别到文字，请扩大选区或提高图片清晰度。'
            if (that.ocrFields.rawText) that.copyText(that.ocrFields.rawText)
          } else {
            that.extractionStatus = (res && res.data && res.data.message) || '框选区域识别失败。'
          }
        }).catch(function(err) {
          that.ocrLoading = false
          that.extractionStatus = that.formatOcrRequestError(err)
        })
      }, 'image/png')
    },
    copyVisibleText() {
      var textarea = this.$refs.extractTextArea && this.$refs.extractTextArea.$el
        ? this.$refs.extractTextArea.$el.querySelector('textarea')
        : null
      var text = textarea && textarea.selectionStart !== textarea.selectionEnd
        ? textarea.value.substring(textarea.selectionStart, textarea.selectionEnd)
        : this.ocrFields.rawText
      this.copyText(text)
    },
    showImageContextMenu(event) {
      var menuWidth = 150
      var menuHeight = 42
      var left = Math.min(event.clientX, window.innerWidth - menuWidth - 8)
      var top = Math.min(event.clientY, window.innerHeight - menuHeight - 8)
      this.imageContextMenuStyle = { left: Math.max(8, left) + 'px', top: Math.max(8, top) + 'px' }
      this.imageContextMenuVisible = true
    },
    hideImageContextMenu() {
      this.imageContextMenuVisible = false
    },
    openExtractedText() {
      this.hideImageContextMenu()
      if (!this.ocrFields.rawText) {
        this.$message.warning(this.ocrLoading ? '图片仍在识别中，请稍后再试' : '没有识别到可提取的文字')
        return
      }
      this.extractedTextVisible = true
    },
    copySelectedExtractedText() {
      var textarea = this.$refs.extractedTextArea && this.$refs.extractedTextArea.$el
        ? this.$refs.extractedTextArea.$el.querySelector('textarea')
        : null
      var text = textarea && textarea.selectionStart !== textarea.selectionEnd
        ? textarea.value.substring(textarea.selectionStart, textarea.selectionEnd)
        : this.ocrFields.rawText
      this.copyText(text)
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
.ocr-image-context-menu {
  position: fixed;
  z-index: 1100;
  min-width: 150px;
  padding: 5px 0;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.18);
}

.ocr-image-context-item {
  padding: 8px 14px;
  color: rgba(0, 0, 0, 0.85);
  cursor: pointer;
}

.ocr-image-context-item:hover {
  background: #e6f7ff;
}

.ocr-image-context-item .anticon {
  margin-right: 8px;
}

.ocr-image-context-item.disabled {
  color: rgba(0, 0, 0, 0.25);
}

.ocr-extract-workspace {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 480px;
  gap: 12px;
}

.ocr-extract-image-column,
.ocr-extract-text-column {
  display: flex;
  flex-direction: column;
  flex: 1;
  width: 50%;
  min-width: 0;
  min-height: 0;
}

.ocr-extract-pane-title {
  display: flex;
  flex: 0 0 36px;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  color: rgba(0, 0, 0, 0.85);
  border-bottom: 1px solid #e8e8e8;
  font-size: 14px;
}

.ocr-zoom-label {
  color: #888;
  font-size: 12px;
}

.ocr-image-viewport {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #181818;
  text-align: left;
}

.ocr-image-canvas {
  position: relative;
  margin: 0 auto;
  cursor: crosshair;
  user-select: none;
}

.ocr-image-canvas img {
  display: block;
  max-width: none;
  user-select: none;
}

.ocr-image-selection {
  position: absolute;
  z-index: 1;
  box-sizing: border-box;
  border: 1px solid #1890ff;
  background: rgba(24, 144, 255, 0.22);
  pointer-events: none;
}

.ocr-image-placeholder {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #ccc;
}

.ocr-image-toolbar {
  display: flex;
  flex: 0 0 46px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #777;
  font-size: 12px;
}

.ocr-zoom-controls {
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.ocr-extracted-text {
  flex: 1;
  height: auto !important;
  min-height: 0 !important;
  resize: none;
}

.ocr-extract-status {
  flex: 0 0 30px;
  overflow: hidden;
  color: #888;
  font-size: 12px;
  line-height: 30px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
