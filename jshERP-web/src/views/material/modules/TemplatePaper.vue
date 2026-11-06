<template>
  <div class="tp-wrap">
    <div class="tp-paper"
      :style="paperStyle">

      <!-- 商品明细表 -->
      <div
        v-for="key in tableAttrList"
        :key="'table-' + key"
        class="tp-table tp-table-free"
        :style="getTableStyle(key)">
        <div class="tp-table-head">
          <div class="tp-col-name">商品</div>
          <div class="tp-col-qty">数量</div>
          <div class="tp-col-price">单价</div>
          <div class="tp-col-amount">金额</div>
        </div>
        <div v-for="(row, i) in rows" :key="i" class="tp-table-row">
          <div class="tp-col-name">
            <span>{{ row.name }}</span>
            <span v-if="row.spec" class="tp-item-spec">{{ row.spec }}</span>
          </div>
          <div class="tp-col-qty">{{ row.qty }}</div>
          <div class="tp-col-price">{{ row.price }}</div>
          <div class="tp-col-amount">{{ row.amount }}</div>
        </div>
        <div v-if="rows.length === 0" class="tp-item-empty">无商品明细</div>
      </div>

      <!-- 明细列：逐行重复渲染 rows 里对应字段 -->
      <div
        v-for="key in detailColAttrList"
        :key="'col-' + key"
        class="tp-free-elem tp-col-stack"
        :style="getElemStyle(key)">
        <div v-for="(row, i) in rows" :key="i" class="tp-col-line">{{ getRowFieldValue(key, row) }}</div>
        <div v-if="rows.length === 0" class="tp-col-line">--</div>
      </div>

      <!-- 普通元素 -->
      <div
        v-for="key in plainAttrList"
        :key="'elem-' + key"
        class="tp-free-elem"
        :style="getElemStyle(key)">
        <span v-if="getLabel(key) && getLabel(key) !== (values[key] != null ? values[key] : '')" class="tp-free-label">{{ getLabel(key) }}</span>
        <span class="tp-free-value">{{ values[key] != null ? values[key] : '' }}</span>
      </div>

    </div>
  </div>
</template>

<script>
  /**
   * TemplatePaper —— 通用模板显示组件
   *
   * 只负责渲染结构 + 样式 + 等比缩放，不负责值的来源。
   * 父组件负责把所有 key→值拍平成 values 对象，把明细行拍平成 rows 数组。
   *
   * props:
   *   template      { width, height, selectedAttrs: [], attrStyles: {}, attrLabels: {} }
   *   values        { key: '显示值', ... }                  父组件已格式化好
   *   rows          [{ name, spec, qty, price, amount }, ...] 明细行，组件内部循环渲染
   *   tableKeys     ['dn_table']                            哪些 key 是商品明细表
   *   detailColKeys ['dn_col_name','dn_col_qty', ...]       哪些 key 是明细列
   *   scale         null | Number                           null=内部 autoScale；有值直接用
   *   transformOrigin 'center top' | 'center center'        缩放锚点
   */
  export default {
    name: 'TemplatePaper',
    props: {
      template:        { type: Object,  required: true },
      values:          { type: Object,  default: function() { return {} } },
      rows:            { type: Array,    default: function() { return [] } },
      tableKeys:       { type: Array,    default: function() { return ['dn_table'] } },
      detailColKeys:   { type: Array,    default: function() { return [] } },
      scale:           { type: Number,   default: null },
      transformOrigin: { type: String,   default: 'center top' },
      padLR:           { type: Number,   default: 12 },
      padT:            { type: Number,   default: 12 }
    },
    data() {
      return {
        // 可用区域尺寸：挂载后测量，resize 时更新，保证 autoScale 能响应
        availSize: null
      }
    },
    computed: {
      // v-if 不能和 v-for 写在同一个元素上，这里按类型拆成三组列表分别渲染
      tableAttrList() {
        var self = this
        return (this.template.selectedAttrs || []).filter(function(key) { return self.isTableKey(key) })
      },
      detailColAttrList() {
        var self = this
        return (this.template.selectedAttrs || []).filter(function(key) { return self.isDetailColKey(key) })
      },
      plainAttrList() {
        var self = this
        return (this.template.selectedAttrs || []).filter(function(key) {
          return !self.isTableKey(key) && !self.isDetailColKey(key)
        })
      },
      paperW() { return this.template.width || 320 },
      paperH() { return this.template.height || 240 },
      // 等比缩放：Math.min(按宽度能缩多少, 按高度能缩多少) → 完整显示不溢出
      autoScale() {
        var size = this.availSize
        if (!size) return 1
        var availW = size.w - this.padLR * 2
        var availH = size.h - this.padT
        if (availW <= 0 || availH <= 0) return 1
        var scaleW = availW / this.paperW
        var scaleH = availH / this.paperH
        return Math.max(0.05, Math.min(scaleW, scaleH))
      },
      effectiveScale() {
        return this.scale != null ? this.scale : this.autoScale
      },
      paperStyle() {
        return {
          width: this.paperW + 'px',
          height: this.paperH + 'px',
          transform: 'scale(' + this.effectiveScale + ')',
          transformOrigin: this.transformOrigin
        }
      }
    },
    methods: {
      isTableKey(key)      { return this.tableKeys.indexOf(key) > -1 },
      isDetailColKey(key)  { return this.detailColKeys.indexOf(key) > -1 },
      getLabel(key)        { var labels = this.template.attrLabels || {}; return labels[key] || '' },

      // 普通元素 / 明细列：读 attrStyles → CSS object
      getElemStyle(key) {
        var s = (this.template.attrStyles && this.template.attrStyles[key]) || {}
        var result = {
          left:   (s.x || 0) + 'px',
          top:    (s.y || 0) + 'px',
          fontSize:     (s.fontSize || 12) + 'px',
          fontWeight:   s.fontWeight || 'normal',
          letterSpacing: (s.letterSpacing || 0) + 'px',
          lineHeight:   s.lineHeight || 1.2
        }
        if (s.width > 0)  result.width  = s.width + 'px'
        if (s.height > 0) result.height = s.height + 'px'
        return result
      },

      // 明细表：没设 width 时自动撑满到画布右侧
      getTableStyle(key) {
        var s = (this.template.attrStyles && this.template.attrStyles[key]) || {}
        var style = this.getElemStyle(key)
        if (!s.width) {
          style.width = Math.max(60, this.paperW - (s.x || 0) - 10) + 'px'
        }
        return style
      },

      // 明细列：key 形如 'dn_col_qty' → 取 'qty' 字段
      // 也支持 row 直接有全 key 字段（如 row['dn_col_qty']），优先用全 key
      getRowFieldValue(key, row) {
        if (!row) return '--'
        if (row[key] != null) return row[key]
        var shortKey = key.replace(/^.*?_/, '')
        if (row[shortKey] != null) return row[shortKey]
        return '--'
      },

      // 测量父容器可用尺寸（autoScale 依赖它）；$el 不是响应式的，需手动测量
      measure() {
        var el = this.$el
        if (!el) return
        var parent = el.parentElement
        this.availSize = {
          w: parent ? parent.clientWidth : (window.innerWidth - 20),
          h: parent ? parent.clientHeight : 600
        }
      },
      _onResize() { this.measure() }
    },
    mounted() {
      this.measure()
      if (this.scale == null && typeof window !== 'undefined') {
        this._tpResize = this._onResize.bind(this)
        window.addEventListener('resize', this._tpResize)
      }
    },
    beforeDestroy() {
      if (this._tpResize && typeof window !== 'undefined') {
        window.removeEventListener('resize', this._tpResize)
        this._tpResize = null
      }
    }
  }
</script>

<style scoped>
  .tp-wrap {
    display: flex;
    justify-content: center;
    max-width: 100%;
  }
  .tp-paper {
    position: relative;
    background: #fff;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.08);
  }

  /* 明细表（自由布局，绝对定位） */
  .tp-table-free {
    position: absolute;
    background: #fff;
  }
  .tp-table-head,
  .tp-table-row {
    display: flex;
    align-items: flex-start;
    padding: 4px 0;
    border-bottom: 1px solid #f5f5f5;
    font-size: 12px;
  }
  .tp-table-head {
    color: #999;
  }
  .tp-col-name {
    flex: 1 1 auto;
    min-width: 0;
    color: #333;
    word-break: break-all;
  }
  .tp-col-qty {
    flex: 0 0 17%;
    color: #333;
  }
  .tp-col-price {
    flex: 0 0 25%;
    color: #333;
  }
  .tp-col-amount {
    flex: 0 0 28%;
    color: #333;
    font-weight: 600;
  }
  .tp-item-spec {
    display: block;
    font-size: 11px;
    color: #999;
  }
  .tp-item-empty {
    padding: 10px 0;
    text-align: center;
    font-size: 12px;
    color: #999;
  }

  /* 明细列 */
  .tp-col-stack {
    white-space: normal;
  }
  .tp-col-line {
    height: 20px;
    line-height: 20px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  /* 普通元素 */
  .tp-free-elem {
    position: absolute;
    white-space: nowrap;
  }
  .tp-free-label {
    color: #333;
  }
  .tp-free-value {
    color: #333;
  }
</style>
