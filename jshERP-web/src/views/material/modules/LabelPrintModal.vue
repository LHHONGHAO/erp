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
    :width="820"
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
        <div class="lp-main">
          <!-- 左：标签预览 -->
          <div class="lp-preview-wrap">
            <div class="lp-preview" :style="{ width: labelWidth + 'px', height: labelHeight + 'px' }">
              <div
                v-for="attr in selectedAttrList"
                :key="attr.key"
                class="lp-item"
                :class="{ 'is-multiline': attr.value != null && String(attr.value).indexOf('\n') > -1 }"
                :style="itemStyle(attr.key)"
              >
                <template v-if="attrLabels[attr.key]">{{ attrLabels[attr.key] }}: </template>{{ attr.value }}
              </div>
            </div>
          </div>
          <!-- 右：打印设置 -->
          <div class="lp-settings">
            <div class="lp-settings-title">
              打印设置
              <a class="lp-refresh" @click="loadPrinters">刷新</a>
            </div>
            <div class="lp-field">
              <div class="lp-field-label">打印机</div>
              <a-select
                v-model="selectedPrinter"
                :loading="loadingPrinters"
                placeholder="请选择打印机"
                style="width:100%"
                allowClear
              >
                <a-select-option v-for="p in printers" :key="p.name" :value="p.name">
                  {{ p.name }}<span v-if="p.isDefault" class="lp-default-tag">（默认）</span>
                </a-select-option>
              </a-select>
              <div v-if="!loadingPrinters && printers.length === 0" class="lp-tip">
                未获取到打印机，请确认后端所在机器已安装打印机后点击「刷新」
              </div>
            </div>
            <div class="lp-field">
              <div class="lp-field-label">打印份数</div>
              <a-input-number v-model="copies" :min="1" :max="99" style="width:100%" />
            </div>
            <div class="lp-tip">
              打印由后端直接发送到所选打印机；若后端不可用，将自动改用浏览器打印对话框。
            </div>
          </div>
        </div>
      </template>
      <div class="lp-footer">
        <a-button type="primary" icon="printer" :disabled="!template" :loading="printing" @click="handlePrint">打印</a-button>
        <a-button icon="edit" @click="handleEdit">编辑</a-button>
        <a-button @click="handleCancel">取消</a-button>
      </div>
    </div>
  </a-modal>
</template>

<script>
  import { buildLabelAttrs } from '@/views/material/utils/materialDisplay'
  import { printLabel } from '@/views/material/utils/labelPrint'
  import { getAction, postAction } from '@/api/manage'
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
        labelHeight: 240,
        // 打印机（由后端枚举本机打印机）
        printers: [],
        selectedPrinter: undefined,
        loadingPrinters: false,
        printing: false,
        copies: 1
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
        this.selectedPrinter = undefined
        this.copies = 1
        this.loadPrinters()
        this.visible = true
      },
      // 加载本机打印机列表（后端枚举），默认选中默认打印机
      loadPrinters() {
        var self = this
        this.loadingPrinters = true
        getAction('/print/printers').then(function(res) {
          self.loadingPrinters = false
          var data = (res && res.data) || {}
          var list = data.printers || []
          self.printers = list
          if (list.length > 0) {
            var def = list.find(function(p) { return p.isDefault })
            self.selectedPrinter = (def || list[0]).name
          } else {
            self.selectedPrinter = undefined
          }
        }).catch(function() {
          self.loadingPrinters = false
          self.printers = []
          self.selectedPrinter = undefined
        })
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
          // 模板里存在该属性名的记录就按记录恢复（含空字符串，留空表示只显示值）
          if (tpl.attrLabels && Object.prototype.hasOwnProperty.call(tpl.attrLabels, key)) {
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
          letterSpacing: (s.letterSpacing || 0) + 'px',
          lineHeight: String(s.lineHeight || 1.3),
          width: (s.width || 120) + 'px',
          height: (s.height || 24) + 'px'
        }
      },
      // 与标签编辑弹层共用同一套属性来源，避免模板里的属性在这里丢失
      buildAttrList() {
        return buildLabelAttrs(this.record, this.sku)
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
      // 打印：直接发送到所选打印机（后端执行），后端不可用时回退浏览器打印
      handlePrint() {
        if (!this.template) return
        if (!this.selectedPrinter) {
          this.$message.warning('请先选择打印机')
          return
        }
        var self = this
        var items = []
        this.selectedAttrList.forEach(function(attr) {
          var style = self.attrStyles[attr.key]
          if (!style) return
          var labelText = self.attrLabels[attr.key] || ''
          var displayText = labelText ? labelText + ': ' + attr.value : attr.value
          items.push({
            x: style.x,
            y: style.y,
            width: style.width || 120,
            height: style.height || 24,
            fontSize: style.fontSize,
            fontWeight: style.fontWeight,
            letterSpacing: style.letterSpacing || 0,
            lineHeight: style.lineHeight || 1.3,
            text: displayText
          })
        })
        this.printing = true
        postAction('/print/label', {
          printerName: this.selectedPrinter,
          copies: this.copies,
          width: this.labelWidth,
          height: this.labelHeight,
          items: items
        }).then(function(res) {
          self.printing = false
          var data = (res && res.data) || {}
          if (res && res.code === 200) {
            self.$message.success('已发送到打印机：' + self.selectedPrinter)
          } else {
            self.$message.error(data.message || '打印失败')
            self.printByBrowser(items)
          }
        }).catch(function() {
          self.printing = false
          self.printByBrowser(items)
        })
      },
      // 浏览器打印（后端打印不可用时兜底）
      printByBrowser(items) {
        var bodyHtml = ''
        items.forEach(function(item) {
          bodyHtml += '<div style="position:absolute;' +
            'left:' + item.x + 'px;' +
            'top:' + item.y + 'px;' +
            'width:' + item.width + 'px;' +
            'height:' + item.height + 'px;' +
            'font-size:' + item.fontSize + 'px;' +
            'font-weight:' + item.fontWeight + ';' +
            'letter-spacing:' + (item.letterSpacing || 0) + 'px;' +
            'line-height:' + (item.lineHeight || 1.3) + ';' +
            'white-space:pre-line;' +
            'overflow:hidden;' +
            '">' + item.text + '</div>'
        })
        printLabel({
          widthPx: this.labelWidth,
          heightPx: this.labelHeight,
          bodyHtml: bodyHtml
        })
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
  .lp-main {
    display: flex;
    align-items: flex-start;
  }
  .lp-preview-wrap {
    flex: 1;
    min-width: 0;
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 300px;
    padding: 12px;
    background: #f5f5f5;
    border-radius: 4px;
    overflow: auto;
  }
  /* 打印设置 */
  .lp-settings {
    width: 260px;
    flex: none;
    margin-left: 16px;
  }
  .lp-settings-title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-weight: bold;
    font-size: 14px;
    padding-bottom: 6px;
    margin-bottom: 12px;
    border-bottom: 1px solid #e8e8e8;
  }
  .lp-refresh {
    font-size: 12px;
    font-weight: normal;
  }
  .lp-field {
    margin-bottom: 12px;
  }
  .lp-field-label {
    font-size: 13px;
    color: #333;
    margin-bottom: 6px;
  }
  .lp-tip {
    font-size: 12px;
    color: #999;
    line-height: 1.5;
  }
  .lp-default-tag {
    color: #999;
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
  .lp-item.is-multiline {
    /* 规格等属性与表格中的排版一致：逐行、左对齐 */
    white-space: pre-line;
    text-align: left;
    line-height: 1.3;
  }
  .lp-footer {
    margin-top: 16px;
    text-align: right;
  }
  .lp-footer .ant-btn {
    margin-left: 8px;
  }
</style>
