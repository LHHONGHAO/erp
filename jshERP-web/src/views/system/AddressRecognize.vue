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
          </div>
        </div>
        <div class="addr-row" v-for="prop in fields" :key="prop.field">
          <!-- 账号：右侧为平台下拉框 -->
          <template v-if="prop.field === 'account'">
            <span class="addr-row__label">{{ prop.label }}</span>
            <a-input v-model="form.account" class="addr-row__input addr-account-input" :bordered="false"
                     placeholder="请输入账号" />
            <a-select v-model="form.platform" class="addr-platform-select" :dropdownMatchSelectWidth="false">
              <a-select-option v-for="opt in platformOptions" :key="opt" :value="opt">{{ opt }}</a-select-option>
            </a-select>
          </template>
          <template v-else>
            <span class="addr-row__label">{{ prop.label }}</span>
            <a-textarea v-if="prop.type === 'textarea'" v-model="form[prop.field]" class="addr-row__input"
                        :bordered="false" autosize :placeholder="getPlaceholder(prop.label)"
                        @input="syncPageHeight()" />
            <a-input v-else v-model="form[prop.field]" class="addr-row__input" :bordered="false"
                     :placeholder="getPlaceholder(prop.label)" />
          </template>
        </div>
        <!-- 商品：仅首行显示标题，末行「添加」，其余行可删除 -->
        <div class="addr-row addr-goods-row" :class="{ 'is-first': idx === 0 }"
             v-for="(g, idx) in goodsRows" :key="'goods-' + idx" :ref="'goodsRow' + idx">
          <span class="addr-row__label">{{ idx === 0 ? '商品' : '' }}</span>
          <div class="addr-goods-box" @click="openGoodsSheet(idx)">
            <span v-if="g.name" class="addr-goods-text">{{ g.name }}<template v-if="g.spec"> · {{ g.spec }}</template></span>
            <span v-else class="addr-goods-placeholder">请选择商品</span>
          </div>
          <a-icon v-if="idx === goodsRows.length - 1" class="addr-goods-add" type="plus-circle" @click.stop="addGoodsRow" />
          <a-icon v-else class="addr-goods-del" type="minus-circle" @click.stop="removeGoodsRow(idx)" />
        </div>
      </div>

      <div ref="scrollBlankSpacer" class="addr-bottom-space"></div>
    </div>

    <!-- 打印按钮：与内容区同级，避免被内容滚动容器裁剪 -->
    <div ref="scrollBlankBottomBar" class="addr-actions">
      <a-button type="primary" block class="addr-print-btn" @click="printResult">打印</a-button>
    </div>

    <!-- 商品选择弹层：默认展示近 7 天最近使用的前五名 -->
    <div v-if="goodsSheet" class="addr-mask" @click.self="goodsSheet = false">
      <div class="addr-sheet">
        <div class="addr-sheet__head">
          <span class="addr-sheet__title">选择商品</span>
          <a-icon type="close" class="addr-sheet__close" @click="goodsSheet = false" />
        </div>
        <div class="addr-sheet__body">
          <div class="addr-sheet__subtitle">最近使用（近 7 天，按使用次数排序）</div>
          <div v-if="recentGoods.length === 0" class="addr-sheet__empty">暂无最近使用的商品，请点击下方「添加」选择</div>
          <div v-for="g in recentGoods" :key="'recent-' + g.key" class="addr-sheet__item" @click="pickGoods(g)">
            <div class="addr-sheet__item-main">
              <div class="addr-sheet__item-name">{{ g.name }}</div>
              <div v-if="g.spec" class="addr-sheet__item-spec">{{ g.spec }}</div>
            </div>
            <span class="addr-sheet__item-count">{{ g.count }} 次</span>
          </div>
        </div>
        <div class="addr-sheet__footer">
          <a-button type="primary" block class="addr-sheet__add-btn" @click="openGoodsPicker">添加</a-button>
        </div>
      </div>
    </div>

    <!-- 商品选择卡片：搜索并选择商品 -->
    <div v-if="goodsPickerVisible" class="addr-mask addr-mask--top" @click.self="goodsPickerVisible = false">
      <div class="addr-picker">
        <div class="addr-sheet__head">
          <a-icon type="arrow-left" class="addr-sheet__back" @click="goodsPickerVisible = false" />
          <span class="addr-sheet__title">选择商品</span>
          <span class="addr-sheet__placeholder"></span>
        </div>
        <div class="addr-picker__search">
          <a-input v-model="goodsPickerKeyword" placeholder="搜索商品名称 / 规格 / 条码" allowClear
                   @pressEnter="loadGoodsPicker()" />
          <a-button type="primary" @click="loadGoodsPicker()">搜索</a-button>
        </div>
        <div class="addr-picker__list">
          <div v-for="item in goodsPickerList" :key="goodsItemKey(item)" class="addr-picker__item"
               @click="pickGoods(formatGoods(item))">
            <div class="addr-picker__name">{{ item.name }}</div>
            <div v-if="getGoodsSpec(item)" class="addr-picker__spec">{{ getGoodsSpec(item) }}</div>
          </div>
          <div v-if="!goodsPickerLoading && goodsPickerList.length === 0" class="addr-sheet__empty">暂无商品</div>
          <div v-if="goodsPickerHasMore" class="addr-picker__more">
            <a-button :loading="goodsPickerLoading" @click="loadGoodsPicker(true)">加载更多</a-button>
          </div>
        </div>
      </div>
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

    <!-- 打印标签页：会员标签模板存在时自动按模板加载，识别结果按字段回填 -->
    <label-print-modal ref="labelPrintModal" />
  </div>
</template>

<script>
import { postAction, getAction } from '@/api/manage'
import { mixinDevice } from '@/utils/mixin'
import { scrollBlankMixin } from '@/utils/scrollBlank'
import { getMpListShort, filterObj } from '@/utils/util'
import { getSpecDisplay } from '@/views/material/utils/materialDisplay'
import LabelPrintModal from '@/views/material/modules/LabelPrintModal'
import ImageRecognizeBox from '@/components/ocr/ImageRecognizeBox'
import Vue from 'vue'

// 地址识别页商品使用历史（用于「最近使用」排序），仅存本地
const GOODS_HISTORY_KEY = 'jsh.addressRecognize.goodsHistory'
const GOODS_RECENT_DAYS = 7
const GOODS_RECENT_LIMIT = 5

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
      ocrLoading: false,
      pasteLoading: false,
      hasResult: false,
      ocrError: '',
      imageUrl: '',
      previewVisible: false,
      pasteText: '',
      form: {
        contacts: '',
        telephone: '',
        account: '',
        platform: '抖音',
        remark: '',
        address: ''
      },
      // 平台下拉框内置选项
      platformOptions: ['微信', '抖音', '淘宝'],
      fields: [
        { field: 'contacts', label: '联系人', type: 'input' },
        { field: 'telephone', label: '电话', type: 'textarea' },
        { field: 'account', label: '账号', type: 'input' },
        { field: 'remark', label: '备注', type: 'input' },
        { field: 'address', label: '地址', type: 'textarea' }
      ],
      // 商品行：每行一个已选商品
      goodsRows: [{ key: '', name: '', spec: '', spec1: '', spec2: '', spec3: '' }],
      goodsTargetIndex: 0,
      // 商品选择弹层（最近使用）
      goodsSheet: false,
      recentGoods: [],
      // 商品选择卡片（全部商品）
      goodsPickerVisible: false,
      goodsPickerKeyword: '',
      goodsPickerList: [],
      goodsPickerLoading: false,
      goodsPickerPage: 1,
      goodsPickerTotal: 0
    }
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
    resetResult() {
      this.form = {
        contacts: '',
        telephone: '',
        account: '',
        platform: '抖音',
        remark: '',
        address: ''
      }
      this.goodsRows = [{ key: '', name: '', spec: '', spec1: '', spec2: '', spec3: '' }]
      this.hasResult = false
      this.ocrError = ''
      this.ocrLoading = false
      this.pasteLoading = false
      // 清空后内容减少，重新计算页面高度
      this.syncPageHeight()
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
      this.form.address = data.address || ''
      // 识别结果回填后文本可能变多导致页面加高，重新计算页面高度
      this.syncPageHeight()
    },
    /**
     * 打印：打开「地址打印」页，只使用本页输入框数据
     * 地址打印模板独立存储（address_layout），与会员标签模板（member_layout）互不影响
     */
    // ===== 商品选择 =====
    goodsValue() {
      var list = []
      this.goodsRows.forEach(function(g) {
        if (!g || !g.name) return
        var line = [g.name, g.spec1, g.spec2, g.spec3].filter(function(v) {
          return v != null && String(v).trim() !== ''
        }).join(' ')
        if (!line && g.spec) line = g.name + ' ' + g.spec
        list.push(line)
      })
      // 一个商品一行，打印时按行数换行排版
      return list.join('\n')
    },
    /** 规格展示文本拆成最多 3 个规格值：去掉「规格名：」，逐行取值 */
    splitSpecValues(spec) {
      var values = []
      String(spec || '').split('\n').forEach(function(line) {
        var text = line.trim()
        if (!text) return
        var idx = text.search(/[：:]/)
        if (idx > -1) text = text.slice(idx + 1).trim()
        if (text) values.push(text)
      })
      return values.slice(0, 3)
    },
    /** 商品多行数据（打印/模板编辑用）：名称 + 规格值1/2/3 */
    goodsRowList() {
      return this.goodsRows.filter(function(g) {
        return g && (g.name || g.spec1 || g.spec2 || g.spec3)
      }).map(function(g) {
        return {
          name: g.name || '',
          spec1: g.spec1 || '',
          spec2: g.spec2 || '',
          spec3: g.spec3 || ''
        }
      })
    },
    addGoodsRow() {
      // 最后一行未选择商品时，添加无效
      var last = this.goodsRows[this.goodsRows.length - 1]
      if (!last || !last.name) return
      this.goodsRows.push({ key: '', name: '', spec: '', spec1: '', spec2: '', spec3: '' })
      // 新增行：重算页面高度并滚动到可视区
      this.syncPageHeight(this.goodsRows.length - 1)
    },
    removeGoodsRow(index) {
      if (this.goodsRows.length <= 1) return
      this.goodsRows.splice(index, 1)
      // 删除行：重算页面高度
      this.syncPageHeight()
    },
    /**
     * 页面高度同步统一入口
     * 触发场景：商品行新增/删除、商品名折行、编辑框输入换行加高、识别结果回填
     * 1. 防抖合并连续输入，避免每敲一个字都重算导致页面抖动
     * 2. 等 DOM 与 autosize 完成后再重新计算底部留白（scrollBlank 规范）
     * 3. 传入行索引时，把该行滚动到可视区，底边与底栏留白为约定间距 targetGap
     */
    syncPageHeight(keepVisibleIndex) {
      var self = this
      if (typeof keepVisibleIndex === 'number') {
        this._syncTargetIndex = keepVisibleIndex
      }
      if (this._syncPageTimer) clearTimeout(this._syncPageTimer)
      this._syncPageTimer = setTimeout(function () {
        self._syncPageTimer = null
        self.$nextTick(function () {
          self.refreshScrollBlank()
          var index = self._syncTargetIndex
          self._syncTargetIndex = null
          if (typeof index === 'number') {
            self.scrollGoodsRowIntoView(index)
          }
        })
      }, 60)
    },
    scrollGoodsRowIntoView(index) {
      // v-for 内的 ref 在 Vue 2 中注册为数组，需取第一个元素
      var refs = this.$refs['goodsRow' + index]
      var row = Array.isArray(refs) ? refs[0] : refs
      if (!row || typeof row.getBoundingClientRect !== 'function') return
      var bar = this.$refs.scrollBlankBottomBar
      var gap = (this.scrollBlankInfo && this.scrollBlankInfo.targetGap) || 12
      var viewportBottom = window.innerHeight
      var barTop = bar ? bar.getBoundingClientRect().top : viewportBottom
      var visibleBottom = Math.min(barTop, viewportBottom) - gap
      var overflow = row.getBoundingClientRect().bottom - visibleBottom
      // 已完整可见则不滚动
      if (overflow <= 1) return
      // 页面可能由 window 滚动，也可能由内部容器滚动，按实际滚动容器计算
      var docScroller = document.scrollingElement || document.documentElement
      var scroller = this.findScrollParent(row)
      var isPage = !scroller || scroller === docScroller ||
        scroller === document.documentElement || scroller === document.body
      var maxScroll = isPage
        ? Math.max(0, Math.max(document.documentElement.scrollHeight,
          document.body ? document.body.scrollHeight : 0) - viewportBottom)
        : Math.max(0, scroller.scrollHeight - scroller.clientHeight)
      var current = isPage
        ? (window.pageYOffset || docScroller.scrollTop || 0)
        : scroller.scrollTop
      var target = Math.min(current + overflow, maxScroll)
      if (target - current <= 1) return
      var source = isPage ? window : scroller
      try {
        source.scrollTo({ top: target, behavior: 'smooth' })
      } catch (e) {
        source.scrollTo(0, target)
      }
    },
    /** 从元素向上查找真正可滚动的容器 */
    findScrollParent(el) {
      var node = el.parentNode
      while (node && node.nodeType === 1) {
        var style = window.getComputedStyle(node)
        var oy = style.overflowY
        if ((oy === 'auto' || oy === 'scroll') && node.scrollHeight > node.clientHeight + 1) {
          return node
        }
        node = node.parentNode
      }
      return null
    },
    openGoodsSheet(index) {
      this.goodsTargetIndex = index
      this.recentGoods = this.computeRecentGoods()
      this.goodsSheet = true
    },
    goodsItemKey(item) {
      var id = (item.id === 0 || item.id) ? item.id : item.name
      return String(id) + '_' + (item.meId || 0)
    },
    getGoodsSpec(item) {
      try {
        return getSpecDisplay(item) || ''
      } catch (e) {
        return ''
      }
    },
    formatGoods(item) {
      var spec = this.getGoodsSpec(item)
      var values = this.splitSpecValues(spec)
      return {
        key: this.goodsItemKey(item),
        name: item.name || '',
        spec: spec,
        spec1: values[0] || '',
        spec2: values[1] || '',
        spec3: values[2] || ''
      }
    },
    pickGoods(goods) {
      if (!goods || !goods.name) return
      // 最近使用里可能只存了名称+规格展示文本，这里再拆一次规格值
      var values = [goods.spec1, goods.spec2, goods.spec3]
      if (!values[0] && !values[1] && !values[2] && goods.spec) {
        values = this.splitSpecValues(goods.spec)
      }
      this.$set(this.goodsRows, this.goodsTargetIndex, {
        key: goods.key || '',
        name: goods.name,
        spec: goods.spec || '',
        spec1: values[0] || '',
        spec2: values[1] || '',
        spec3: values[2] || ''
      })
      this.recordGoodsHistory(goods)
      this.goodsSheet = false
      this.goodsPickerVisible = false
      // 商品名过长会折行加高，重新计算页面高度
      this.syncPageHeight()
    },
    // 记录商品使用历史：最近 7 天按次数排序取前五
    recordGoodsHistory(goods) {
      var history = []
      try {
        history = JSON.parse(Vue.ls.get(GOODS_HISTORY_KEY) || '[]') || []
      } catch (e) {
        history = []
      }
      history.push({
        key: goods.key || (goods.name + '_' + (goods.spec || '')),
        name: goods.name,
        spec: goods.spec || '',
        time: Date.now()
      })
      // 只保留最近 200 条，避免本地存储无限增长
      if (history.length > 200) history = history.slice(history.length - 200)
      try {
        Vue.ls.set(GOODS_HISTORY_KEY, JSON.stringify(history))
      } catch (e) { /* 忽略 */ }
    },
    computeRecentGoods() {
      var history = []
      try {
        history = JSON.parse(Vue.ls.get(GOODS_HISTORY_KEY) || '[]') || []
      } catch (e) {
        history = []
      }
      var now = Date.now()
      var week = GOODS_RECENT_DAYS * 24 * 60 * 60 * 1000
      var map = {}
      history.forEach(function(h) {
        if (!h || !h.name) return
        if (now - (h.time || 0) > week) return
        var k = h.key || (h.name + '_' + (h.spec || ''))
        if (!map[k]) map[k] = { key: k, name: h.name, spec: h.spec || '', count: 0, last: 0 }
        map[k].count++
        map[k].last = Math.max(map[k].last, h.time || 0)
      })
      return Object.keys(map).map(function(k) { return map[k] }).sort(function(a, b) {
        return b.count - a.count || b.last - a.last
      }).slice(0, GOODS_RECENT_LIMIT)
    },
    openGoodsPicker() {
      this.goodsPickerVisible = true
      this.goodsPickerKeyword = ''
      this.loadGoodsPicker()
    },
    loadGoodsPicker(more) {
      var self = this
      if (more && (this.goodsPickerLoading || this.goodsPickerList.length >= this.goodsPickerTotal)) return
      this.goodsPickerLoading = true
      if (!more) {
        this.goodsPickerPage = 1
        this.goodsPickerList = []
      }
      var searchObj = {
        materialParam: this.goodsPickerKeyword,
        mpList: getMpListShort(Vue.ls.get('materialPropertyList'))
      }
      getAction('/material/listSku', {
        search: JSON.stringify(filterObj(searchObj)),
        currentPage: this.goodsPickerPage,
        pageSize: 20
      }).then(function(res) {
        if (res && res.code === 200) {
          var rows = (res.data && res.data.rows) || []
          self.goodsPickerList = more ? self.goodsPickerList.concat(rows) : rows
          self.goodsPickerTotal = (res.data && res.data.total) || self.goodsPickerList.length
          self.goodsPickerPage++
        } else {
          self.$message.error((res && res.data && res.data.message) || '商品加载失败')
        }
      }).catch(function() {
        self.$message.error('商品加载失败')
      }).finally(function() {
        self.goodsPickerLoading = false
      })
    },
    printResult() {
      var self = this
      var values = {}
      this.fields.forEach(function(prop) {
        values[prop.field] = self.form[prop.field] || ''
      })
      values.goods = this.goodsValue()
      values.goodsRows = this.goodsRowList()
      values.platform = this.form.platform || ''
      try {
        sessionStorage.setItem('jsh.labelEdit.snapshot', JSON.stringify({
          labelWidth: 320,
          labelHeight: 240,
          selectedAttrs: [],
          attrStyles: {},
          attrLabels: {},
          values: values,
          // 商品明细行：标签编辑/打印按行数动态排版
          rows: values.goodsRows
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
  /* 卡片内边距（卡片留白规范）：商品行追加后的下方留白也取该值 */
  --addr-card-pad: 14px;
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
/* 账号行：账号输入框 + 右侧平台下拉框 */
.addr-account-input {
  flex: 1;
  min-width: 0;
}
.addr-platform-select {
  flex: 0 0 auto;
  /* 宽度 = 五个字符 + 下拉箭头 */
  width: calc(5em + 34px);
  margin-left: 8px;
  font-size: 13px;
}
.addr-platform-select /deep/ .ant-select-selection {
  height: 30px;
  border-radius: 15px;
  background: #f0f7ff;
  border-color: #cfe4ff;
}
.addr-platform-select /deep/ .ant-select-selection__rendered {
  margin-left: 10px;
  margin-right: 24px;
}
.addr-platform-select /deep/ .ant-select-selection-selected-value {
  max-width: none;
  padding-right: 0;
  font-size: 13px;
  line-height: 28px;
  color: #1890ff;
}
.addr-platform-select /deep/ .ant-select-arrow {
  right: 8px;
  color: #1890ff;
}
.addr-actions {
  margin-top: 16px;
}
/* ===== 商品行与商品选择弹层 ===== */
.addr-goods-row {
  align-items: center;
  min-height: 52px;
  padding: 0 var(--addr-card-pad);
  border-top: none;
}
/* 首行与普通行一致：整行分割线 */
.addr-goods-row.is-first {
  border-top: 1px solid #f0f0f0;
}
.addr-goods-box {
  flex: 1;
  min-width: 0;
  padding: var(--addr-card-pad) 0;
  font-size: 16px;
  line-height: 22px;
  cursor: pointer;
}
/* 追加行：分割线只覆盖输入框区域 */
.addr-goods-row:not(.is-first) .addr-goods-box {
  border-top: 1px solid #f0f0f0;
  padding-top: 14px;
}
/* 最后一行：下方留白与卡片内边距一致，且不画底线 */
.addr-goods-row:last-child .addr-goods-box {
  padding-bottom: var(--addr-card-pad);
}
.addr-goods-text {
  color: #333;
}
.addr-goods-placeholder {
  color: #bfbfbf;
}
.addr-goods-add,
.addr-goods-del {
  flex-shrink: 0;
  margin-left: 8px;
  font-size: 20px;
}
.addr-goods-add {
  color: #1890ff;
}
.addr-goods-del {
  color: #ff4d4f;
}
.addr-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 2100;
  display: flex;
  align-items: flex-end;
  background: rgba(0, 0, 0, 0.45);
}
.addr-mask--top {
  z-index: 2200;
  align-items: stretch;
}
.addr-sheet {
  width: 100%;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 14px 14px 0 0;
}
.addr-sheet__head {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  border-bottom: 1px solid #f0f0f0;
}
.addr-sheet__title {
  font-size: 16px;
  font-weight: 600;
  color: #222;
}
.addr-sheet__close,
.addr-sheet__back {
  position: absolute;
  right: 14px;
  top: 14px;
  font-size: 18px;
  color: #666;
}
.addr-sheet__back {
  right: auto;
  left: 14px;
}
.addr-sheet__placeholder {
  width: 18px;
}
.addr-sheet__body {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.addr-sheet__subtitle {
  padding: 6px 16px;
  font-size: 12px;
  color: #999;
}
.addr-sheet__empty {
  padding: 30px 16px;
  text-align: center;
  font-size: 13px;
  color: #999;
}
.addr-sheet__item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
}
.addr-sheet__item-main {
  flex: 1;
  min-width: 0;
}
.addr-sheet__item-name {
  font-size: 15px;
  color: #333;
}
.addr-sheet__item-spec {
  margin-top: 2px;
  font-size: 12px;
  color: #999;
}
.addr-sheet__item-count {
  flex-shrink: 0;
  font-size: 12px;
  color: #1890ff;
}
.addr-sheet__footer {
  padding: 10px 16px;
  border-top: 1px solid #f0f0f0;
}
.addr-sheet__add-btn {
  height: 42px;
  border-radius: 21px;
}
.addr-picker {
  display: flex;
  flex-direction: column;
  width: 100%;
  height: 100%;
  background: #fff;
}
.addr-picker__search {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.addr-picker__search /deep/ .ant-input-affix-wrapper {
  flex: 1;
  margin-right: 8px;
}
.addr-picker__list {
  flex: 1;
  overflow-y: auto;
}
.addr-picker__item {
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
}
.addr-picker__name {
  font-size: 15px;
  color: #333;
}
.addr-picker__spec {
  margin-top: 2px;
  font-size: 12px;
  color: #999;
}
.addr-picker__more {
  padding: 12px;
  text-align: center;
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
  /* 顶栏 49px 已由全局布局占位；内容区自己滚动，
     打印弹层、选择弹层等固定层都在内容区外部，不会被滚动容器裁剪 */
  .addr-page {
    height: calc(100vh - 49px);
    height: calc(var(--scroll-blank-vh, 100vh) - 49px);
    min-height: 0;
    overflow: hidden;
  }
  .addr-body {
    min-height: 0;
    overflow-y: auto;
    /* 底部预留固定打印栏高度 + 卡片下方留白（卡片自带 12px margin） */
    padding-bottom: calc(70px + env(safe-area-inset-bottom));
  }
  /* 手机端由 addr-body 的底部内边距承载留白，这里不再叠加 */
  .addr-bottom-space {
    height: 0;
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
    /* 底部留白已由 .addr-body 的内边距统一承载，不叠加占位 */
    height: 0;
  }
}
.addr-file-input {
  display: none;
}
@media (min-width: 769px) {
  .addr-bottom-space {
    height: calc(12px + env(safe-area-inset-bottom));
  }
}
</style>
