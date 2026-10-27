/**
 * 打印标签预览弹层
 *
 * 交互：商品信息页点击「打印标签」→ 本弹层按当前商品所用标签模板的实际尺寸显示预览
 *   - 打印：按模板直接打印（不进入编辑界面）
 *   - 编辑：交给标签编辑弹层（LabelEditModal）修改模板/样式
 */
<template>
  <a-modal
    title="打印标签"
    :width="480"
    :visible="visible"
    :maskClosable="false"
    :footer="null"
    @cancel="handleCancel"
  >
    <div class="lp-body">
      <template v-if="!template">
        <a-alert
          type="info"
          show-icon
          message="该商品暂无可用标签模板"
          description="可直接点击下方「编辑」创建标签模板后再打印"
        />
      </template>
      <template v-else>
        <div class="lp-size-tip">
          标签实际尺寸：{{ labelWidthMm }}mm × {{ labelHeightMm }}mm
          <span class="lp-template-name">{{ template.name }}</span>
        </div>
        <div class="lp-preview-wrap">
          <div class="lp-preview" :style="{ width: labelWidth + 'px', height: labelHeight + 'px' }">
            <div
              v-for="attr in selectedAttrList"
              :key="attr.key"
              class="lp-item"
              :style="itemStyle(attr.key)"
            >
              <template v-if="attrLabels[attr.key]">{{ attrLabels[attr.key] }}: </template>{{ attr.value }}
            </div>
          </div>
        </div>
      </template>
      <div class="lp-footer">
        <a-button type="primary" icon="printer" :disabled="!template" @click="handlePrint">打印</a-button>
        <a-button icon="edit" @click="handleEdit">编辑</a-button>
        <a-button @click="handleCancel">取消</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
  var PX_PER_MM = 3.779528
  export default {
    name: 'LabelPrintModal',
    data() {
      return {
        visible: false,
        record: {},
        sku: {},
        categoryTree: [],
        template: null,
        allAttrs: [],
        selectedAttrs: [],
        attrStyles: {},
        attrLabels: {},
        labelWidth: 320,
        labelHeight: 240
      }
    },
    computed: {
      selectedAttrList() {
        var self = this
        return this.selectedAttrs
          .map(function(key) { return self.allAttrs.find(function(a) { return a.key === key }) })
          .filter(function(attr) { return !!attr })
      },
      labelWidthMm() {
        return Math.round(this.labelWidth / PX_PER_MM)
      },
      labelHeightMm() {
        return Math.round(this.labelHeight / PX_PER_MM)
      }
    },
    methods: {
      open(record, sku, categoryTree) {
        this.record = record || {}
        this.sku = sku || {}
        this.categoryTree = categoryTree || []
        this.allAttrs = this.buildAttrList()
        this.loadTemplate()
        this.visible = true
      },
      handleCancel() {
        this.visible = false
      },
      // 模板优先级：当前商品的普通模板 > 品类的默认模板
      loadTemplate() {
        var templates = []
        try {
          var json = localStorage.getItem('labelTemplates')
          templates = json ? JSON.parse(json) : []
        } catch (e) {
          templates = []
        }
        var record = this.record
        var normal = templates.find(function(t) {
          return t.type === 'normal' && String(t.materialId) === String(record.id)
        })
        var defaultTpl = templates.find(function(t) {
          return t.type === 'default' && String(t.categoryId) === String(record.categoryId)
        })
        this.applyTemplate(normal || defaultTpl || null)
      },
      applyTemplate(tpl) {
        var self = this
        this.template = tpl
        this.selectedAttrs = []
        this.attrStyles = {}
        this.attrLabels = {}
        if (!tpl) return
        // 自定义标签的值保存在模板里，需要合并回属性列表
        if (tpl.customLabels && tpl.customLabels.length > 0) {
          tpl.customLabels.forEach(function(custom) {
            self.allAttrs.push(JSON.parse(JSON.stringify(custom)))
          })
        }
        this.selectedAttrs = JSON.parse(JSON.stringify(tpl.selectedAttrs || []))
        this.selectedAttrs.forEach(function(key) {
          var style = tpl.attrStyles && tpl.attrStyles[key]
          var idx = self.selectedAttrs.indexOf(key)
          self.$set(self.attrStyles, key, style
            ? JSON.parse(JSON.stringify(style))
            : { fontSize: 14, x: 10, y: 10 + idx * 30, fontWeight: 'normal', width: 120, height: 24 })
          if (tpl.attrLabels && tpl.attrLabels[key]) {
            self.$set(self.attrLabels, key, tpl.attrLabels[key])
          } else {
            var attr = self.allAttrs.find(function(a) { return a.key === key })
            self.$set(self.attrLabels, key, attr ? attr.label : '')
          }
        })
        this.labelWidth = tpl.labelWidth || 320
        this.labelHeight = tpl.labelHeight || 240
      },
      itemStyle(key) {
        var s = this.attrStyles[key]
        if (!s) return {}
        return {
          left: s.x + 'px',
          top: s.y + 'px',
          fontSize: s.fontSize + 'px',
          fontWeight: s.fontWeight,
          width: (s.width || 120) + 'px',
          height: (s.height || 24) + 'px'
        }
      },
      // 与标签编辑弹层保持同一套属性来源
      buildAttrList() {
        var record = this.record
        var sku = this.sku
        return [
          { key: 'name', label: '商品名称', value: record.name || '' },
          { key: 'standard', label: '规格型号', value: record.standard || '' },
          { key: 'model', label: '型号', value: record.model || '' },
          { key: 'color', label: '颜色', value: record.color || '' },
          { key: 'brand', label: '品牌', value: record.brand || '' },
          { key: 'mnemonic', label: '助记码', value: record.mnemonic || '' },
          { key: 'weight', label: '重量', value: record.weight != null ? String(record.weight) : '' },
          { key: 'position', label: '仓位货架', value: record.position || '' },
          { key: 'mfrs', label: '制造商', value: record.mfrs || '' },
          { key: 'mBarCode', label: '条码', value: record.mBarCode || '' },
          { key: 'commodityUnit', label: '单位', value: record.commodityUnit || '' },
          { key: 'otherField1', label: '扩展1', value: record.otherField1 || '' },
          { key: 'otherField2', label: '扩展2', value: record.otherField2 || '' },
          { key: 'otherField3', label: '扩展3', value: record.otherField3 || '' },
          { key: 'specValues', label: '规格值', value: sku.specValuesText || '' },
          { key: 'skuCode', label: 'SKU码', value: sku.skuCode || '' },
          { key: 'purchaseDecimal', label: '采购价', value: sku.purchaseDecimal || '' },
          { key: 'commodityDecimal', label: '零售价', value: sku.commodityDecimal || '' },
          { key: 'wholesaleDecimal', label: '销售价', value: sku.wholesaleDecimal || '' },
          { key: 'lowDecimal', label: '最低售价', value: sku.lowDecimal || '' },
          { key: 'currentStock', label: '库存', value: sku.currentStock != null ? String(sku.currentStock) : '' }
        ].filter(function(attr) { return attr.value })
      },
      // 编辑：先关闭预览层，动画结束后再交给标签编辑弹层，避免两层重叠
      handleEdit() {
        var payload = {
          record: this.record,
          sku: this.sku,
          categoryTree: this.categoryTree
        }
        this.visible = false
        setTimeout(() => this.$emit('edit', payload), 200)
      },
      // 打印：按模板实际尺寸直接打印
      handlePrint() {
        if (!this.template) return
        var self = this
        var printWin = window.open('', '_blank', 'width=500,height=600')
        if (!printWin) {
          this.$message.warning('请允许弹出窗口以打印标签')
          return
        }
        var itemsHtml = ''
        this.selectedAttrList.forEach(function(attr) {
          var style = self.attrStyles[attr.key]
          if (!style) return
          var labelText = self.attrLabels[attr.key] || ''
          var displayText = labelText ? labelText + ': ' + attr.value : attr.value
          itemsHtml += '<div style="position:absolute;' +
            'left:' + style.x + 'px;' +
            'top:' + style.y + 'px;' +
            'width:' + (style.width || 120) + 'px;' +
            'height:' + (style.height || 24) + 'px;' +
            'font-size:' + style.fontSize + 'px;' +
            'font-weight:' + style.fontWeight + ';' +
            'overflow:hidden;' +
            '">' + displayText + '</div>'
        })
        printWin.document.write(
          '<html><head><title>打印标签</title>' +
          '<style>' +
          '@page{margin:0}' +
          'body{margin:0;padding:0;font-family:Arial,"Microsoft YaHei",sans-serif}' +
          '.label{position:relative;width:' + this.labelWidth + 'px;height:' + this.labelHeight + 'px;overflow:hidden;page-break-after:always}' +
          '</style></head><body>' +
          '<div class="label">' + itemsHtml + '</div>' +
          '</body></html>'
        )
        printWin.document.close()
        printWin.focus()
        setTimeout(function() {
          printWin.print()
          printWin.close()
        }, 300)
      }
    }
  }
</script>

<style scoped>
  .lp-size-tip {
    color: #666;
    font-size: 12px;
    margin-bottom: 8px;
  }
  .lp-template-name {
    margin-left: 8px;
    color: #1890ff;
  }
  .lp-preview-wrap {
    display: flex;
    justify-content: center;
    padding: 12px 0;
    background: #f5f5f5;
    border-radius: 4px;
  }
  .lp-preview {
    position: relative;
    background: #fff;
    border: 1px dashed #d9d9d9;
    overflow: hidden;
    box-sizing: border-box;
  }
  .lp-item {
    position: absolute;
    line-height: 1.2;
    overflow: hidden;
    white-space: nowrap;
  }
  .lp-footer {
    margin-top: 16px;
    text-align: right;
  }
  .lp-footer .ant-btn {
    margin-left: 8px;
  }
</style>
