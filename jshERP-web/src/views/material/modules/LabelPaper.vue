<template>
  <div class="lpp-paper" :style="paperStyle">
    <!-- 文本元素 -->
    <div
      v-for="el in textElems"
      :key="el.id"
      class="lpp-text"
      :class="{ 'is-multiline': el.multiline }"
      :style="el.style"
    >{{ el.display }}</div>

    <!-- 条码 / 二维码 -->
    <img
      v-for="el in codeElems"
      :key="el.id"
      class="lpp-code"
      :src="codeImages[el.id] || ''"
      :style="el.style" />

    <!-- 明细表：按数据源 items 数组循环渲染 -->
    <div v-for="el in tableElems" :key="el.id" class="lpp-table" :style="el.style">
      <div class="lpp-table-row lpp-table-head">
        <span v-for="col in el.columns" :key="col.field" :style="{ flex: col.flex }">{{ col.label }}</span>
      </div>
      <div v-for="(row, index) in rows" :key="index" class="lpp-table-row">
        <span v-for="col in el.columns" :key="col.field" :style="{ flex: col.flex }">{{ row[col.field] }}</span>
      </div>
      <div v-if="!rows.length" class="lpp-table-empty">无明细</div>
    </div>
  </div>
</template>

<script>
  /**
   * LabelPaper —— 新版标签模板（v3 elements）预览渲染
   *
   * 只做渲染：模板中的坐标/尺寸为毫米，这里换算成像素；
   * 值由父组件通过 values / rows 传入（来自统一数据接口，可能是业务适配器数据）。
   * 条码与二维码按时实际尺寸生成图片，保证预览与打印尺寸一致。
   */
  import { mmToPx } from '../utils/labelSchema'
  import { renderCodeDataUrl, fitCodeSize } from '../utils/labelCode'

  var DEFAULT_COLUMNS = [
    { field: 'name', label: '商品', flex: '1 1 auto' },
    { field: 'qty', label: '数量', flex: '0 0 17%' },
    { field: 'price', label: '单价', flex: '0 0 25%' },
    { field: 'amount', label: '金额', flex: '0 0 26%' }
  ]

  export default {
    name: 'LabelPaper',
    props: {
      schema:          { type: Object, required: true },
      values:          { type: Object, default: function() { return {} } },
      rows:            { type: Array,  default: function() { return [] } },
      scale:           { type: Number, default: null },
      transformOrigin: { type: String, default: 'center top' },
      padLR:           { type: Number, default: 12 },
      padT:            { type: Number, default: 12 }
    },
    data() {
      return { codeImages: {} }
    },
    computed: {
      size() {
        return this.schema.size || {}
      },
      paperW() {
        return mmToPx(this.size.width || 40)
      },
      paperH() {
        return mmToPx(this.size.height || 30)
      },
      autoScale() {
        var el = this.$el
        if (!el) return 1
        var parent = el.parentElement
        var cw = parent ? parent.clientWidth : (window.innerWidth - 20)
        var ch = parent ? parent.clientHeight : 600
        var availW = cw - this.padLR * 2
        var availH = ch - this.padT
        if (availW <= 0 || availH <= 0) return 1
        return Math.max(0.05, Math.min(availW / this.paperW, availH / this.paperH))
      },
      effectiveScale() {
        return this.scale != null ? this.scale : this.autoScale
      },
      paperStyle() {
        var style = { width: this.paperW + 'px', height: this.paperH + 'px' }
        if (this.effectiveScale !== 1) {
          style.transform = 'scale(' + this.effectiveScale + ')'
          style.transformOrigin = this.transformOrigin
        }
        return style
      },
      elements() {
        return (this.schema.elements || []).filter(function(el) { return el && el.id })
      },
      textElems() {
        var self = this
        return this.elements.filter(function(el) {
          return el.type !== 'barcode' && el.type !== 'qrcode' && el.type !== 'table'
        }).map(function(el) {
          var value = el.field ? self.textOf(self.values[el.field]) : self.textOf(el.text)
          var display = el.label ? el.label + ': ' + value : value
          var style = self.baseStyle(el)
          // 指定宽度：按框宽自动换行；指定高度：超出部分不打印（裁剪）
          if (el.width) {
            style.whiteSpace = 'pre-wrap'
            style.wordBreak = 'break-all'
            style.overflowWrap = 'anywhere'
          }
          if (el.height) style.overflow = 'hidden'
          return {
            id: el.id,
            display: display,
            multiline: value.indexOf('\n') > -1,
            style: style
          }
        })
      },
      codeElems() {
        var self = this
        return this.elements.filter(function(el) {
          return el.type === 'barcode' || el.type === 'qrcode'
        }).map(function(el) {
          var text = self.textOf(self.values[el.field])
          var width = mmToPx(el.width || 20)
          var height = mmToPx(el.height || 10)
          var fitted = fitCodeSize(el.type, text, width, height)
          return {
            id: el.id,
            type: el.type,
            text: text,
            width: width,
            height: height,
            style: {
              left: mmToPx(el.x) + 'px',
              top: mmToPx(el.y) + 'px',
              width: fitted.width + 'px',
              height: fitted.height + 'px'
            }
          }
        })
      },
      tableElems() {
        var self = this
        return this.elements.filter(function(el) {
          return el.type === 'table'
        }).map(function(el) {
          return {
            id: el.id,
            columns: (el.columns && el.columns.length ? el.columns : DEFAULT_COLUMNS),
            style: self.baseStyle(el)
          }
        })
      },
      // 编码内容签名：值/尺寸变化时重新生成图片
      codeSignature() {
        return this.codeElems.map(function(el) {
          return [el.id, el.type, el.text, el.width, el.height].join('|')
        }).join(';')
      }
    },
    watch: {
      codeSignature() {
        this.refreshCodeImages()
      }
    },
    methods: {
      textOf(value) {
        return value == null ? '' : String(value)
      },
      baseStyle(el) {
        var style = {
          left: mmToPx(el.x) + 'px',
          top: mmToPx(el.y) + 'px',
          fontSize: (el.fontSize || 14) + 'px',
          fontWeight: el.fontWeight || 'normal',
          letterSpacing: (el.letterSpacing || 0) + 'px',
          lineHeight: String(el.lineHeight || 1.3)
        }
        if (el.width) style.width = mmToPx(el.width) + 'px'
        if (el.height) style.height = mmToPx(el.height) + 'px'
        return style
      },
      refreshCodeImages() {
        var self = this
        this.codeElems.forEach(function(el) {
          renderCodeDataUrl(el.type, el.text, el.width, el.height).then(function(url) {
            if (url) self.$set(self.codeImages, el.id, url)
          })
        })
      }
    },
    mounted() {
      this.refreshCodeImages()
      if (this.scale == null && typeof window !== 'undefined') {
        this._onResize = this.$forceUpdate.bind(this)
        window.addEventListener('resize', this._onResize)
      }
    },
    beforeDestroy() {
      if (this._onResize) {
        window.removeEventListener('resize', this._onResize)
        this._onResize = null
      }
    }
  }
</script>

<style scoped>
  .lpp-paper {
    position: relative;
    background: #fff;
    overflow: hidden;
  }
  .lpp-text {
    position: absolute;
    white-space: nowrap;
    color: #333;
  }
  .lpp-text.is-multiline {
    white-space: pre-wrap;
  }
  .lpp-code {
    position: absolute;
    object-fit: fill;
  }
  .lpp-table {
    position: absolute;
    font-size: 12px;
  }
  .lpp-table-row {
    display: flex;
    border-bottom: 1px solid #f5f5f5;
  }
  .lpp-table-row span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: #333;
  }
  .lpp-table-head span {
    color: #999;
  }
  .lpp-table-empty {
    padding: 8px 0;
    text-align: center;
    color: #999;
  }
</style>
