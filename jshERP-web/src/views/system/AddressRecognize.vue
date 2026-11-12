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
            <span class="addr-copy-btn" @click="copyAddress">复制地址</span>
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
                        :bordered="false" autosize :placeholder="getPlaceholder(prop.label)" />
            <a-input v-else v-model="form[prop.field]" class="addr-row__input" :bordered="false"
                     :placeholder="getPlaceholder(prop.label)" />
          </template>
        </div>
        <!-- 商品：仅首行显示标题，末行「添加」，其余行可删除 -->
        <div class="addr-row addr-goods-row" :class="{ 'is-first': idx === 0 }"
             v-for="(g, idx) in goodsRows" :key="'goods-' + idx" :ref="'goodsRow' + idx">
          <span class="addr-row__label">{{ idx === 0 ? '商品' : '' }}</span>
          <div class="addr-goods-box" @click="openGoodsPicker(idx)">
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

    <!-- 商品选择：标准整页弹层（白底顶栏 + 标题居中 + 右侧关闭），复用后可跨页面使用 -->
    <mobile-product-picker
      :visible="goodsPickerVisible"
      title="添加商品"
      @pick="handlePickGoods"
      @confirm="handleConfirmGoods"
      @close="goodsPickerVisible = false" />

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
import { postAction } from '@/api/manage'
import { mixinDevice } from '@/utils/mixin'
import { scrollBlankMixin } from '@/utils/scrollBlank'
import LabelPrintModal from '@/views/material/modules/LabelPrintModal'
import ImageRecognizeBox from '@/components/ocr/ImageRecognizeBox'
import MobileProductPicker from '@/components/mobile/MobileProductPicker'

/**
 * 地址识别独立页面：界面与识别逻辑与 OcrMemberRecognizeModal 保持一致，
 * 仅展示识别结果，不回填任何业务表单。
 */
export default {
  name: 'AddressRecognize',
  mixins: [mixinDevice, scrollBlankMixin],
  components: { LabelPrintModal, ImageRecognizeBox, MobileProductPicker },
  data() {
    return {
      ocrLoading: false,
      pasteLoading: false,
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
      // 商品选择整页弹层
      goodsPickerVisible: false
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
    // ===== 复制地址 =====
    /** 待复制内容：只保留联系人、电话、地址 */
    buildCopyText() {
      var parts = []
      var contacts = String(this.form.contacts || '').trim()
      var telephone = String(this.form.telephone || '').trim()
      var address = this.joinAddressParts(this.form.address)
      if (contacts) parts.push('联系人：' + contacts)
      if (telephone) parts.push('电话：' + telephone)
      if (address) parts.push('地址：' + address)
      return parts.join('\n')
    },
    /** 把识别出的地址按 省/市/区县/乡镇街道 拆开，再用 / 拼成整体 */
    joinAddressParts(address) {
      var text = String(address || '').replace(/\s+/g, ' ').trim()
      if (!text) return ''
      if (text.indexOf('/') > -1) return text
      var patterns = [
        /^([^省市区县旗镇乡]{2,10}?(?:省|自治区|特别行政区))/,
        /^([^省区县旗镇乡]{2,10}?(?:市|自治州|地区|盟))/,
        /^([^省市县旗镇乡]{2,10}?(?:区|县|旗))/,
        /^([^省市区县]{2,10}?(?:镇|乡|街道))/
      ]
      var parts = []
      patterns.forEach(function(pattern) {
        var match = text.match(pattern)
        if (match && match[1]) {
          parts.push(match[1])
          text = text.slice(match[1].length)
        }
      })
      if (text) parts.push(text)
      return parts.join('/')
    },
    /** 复制地址：把输入框内容写入剪贴板 */
    copyAddress() {
      var text = this.buildCopyText()
      if (!text) {
        this.$message.warning('请先填写地址信息')
        return
      }
      this.copyToClipboard(text)
    },
    copyToClipboard(text) {
      var that = this
      if (navigator.clipboard && navigator.clipboard.writeText && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(function() {
          that.$message.success('地址信息已复制')
        }).catch(function() {
          that.copyByTextarea(text)
        })
      } else {
        this.copyByTextarea(text)
      }
    },
    /** 非安全上下文（http）下的兜底复制方案 */
    copyByTextarea(text) {
      var textarea = document.createElement('textarea')
      textarea.value = text
      textarea.setAttribute('readonly', '')
      textarea.style.cssText = 'position:fixed;top:0;left:0;opacity:0;'
      document.body.appendChild(textarea)
      textarea.select()
      textarea.setSelectionRange(0, text.length)
      var copied = false
      try {
        copied = document.execCommand('copy')
      } catch (e) {
        copied = false
      }
      document.body.removeChild(textarea)
      if (copied) this.$message.success('地址信息已复制')
      else this.$message.error('复制失败，请长按输入框手动复制')
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
      var that = this
      this.ocrLoading = true
      this.ocrError = ''
      if (this.imageUrl) {
        URL.revokeObjectURL(this.imageUrl)
      }
      this.imageUrl = URL.createObjectURL(file)
      // 第一步：图片识别文字（接口返回 rawText）
      this.compressImage(file).then(function(blob) {
        var formData = new FormData()
        formData.append('file', blob, 'address.jpg')
        return postAction('/ocr/recognizeProduct', formData)
      }).then(function(res) {
        if (!(res && res.code === 200 && res.data)) {
          throw new Error((res && res.data && res.data.message) ? res.data.message : '图片识别文字失败')
        }
        var text = (res.data.rawText || '').trim()
        if (!text) {
          throw new Error('未识别到文字，请更换清晰的图片')
        }
        // 第二步：文字识别地址
        return postAction('/ocr/parseAddressText', { text: text })
      }).then(function(res) {
        if (!(res && res.code === 200 && res.data)) {
          throw new Error((res && res.data && res.data.message) ? res.data.message : '文字识别地址失败')
        }
        that.ocrLoading = false
        that.fillFields(res.data)
      }).catch(function(err) {
        that.ocrLoading = false
        var errMsg = err && err.message ? err.message : '识别失败'
        that.ocrError = errMsg
        that.$message.error(errMsg)
      })
    },
    /**
     * 压缩图片：百度文字识别要求 base64 后不超过 4MB（原图约 3MB），
     * 手机拍照普遍 3~8MB，直接上传会报 input oversize，这里先缩到 1600px 内
     */
    compressImage(file) {
      return new Promise(function(resolve) {
        if (!file || !/^image\//.test(file.type)) {
          resolve(file)
          return
        }
        var url = URL.createObjectURL(file)
        var img = new Image()
        img.onload = function() {
          var maxSide = 1600
          var scale = Math.min(1, maxSide / Math.max(img.width, img.height))
          var canvas = document.createElement('canvas')
          canvas.width = Math.round(img.width * scale)
          canvas.height = Math.round(img.height * scale)
          var ctx = canvas.getContext('2d')
          ctx.fillStyle = '#fff'
          ctx.fillRect(0, 0, canvas.width, canvas.height)
          ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
          URL.revokeObjectURL(url)
          var toBlob = function(quality, callback) {
            if (canvas.toBlob) {
              canvas.toBlob(callback, 'image/jpeg', quality)
            } else {
              callback(null)
            }
          }
          toBlob(0.82, function(blob) {
            if (!blob) {
              resolve(file)
              return
            }
            if (blob.size <= 2500000) {
              resolve(blob)
              return
            }
            toBlob(0.6, function(smaller) {
              resolve(smaller && smaller.size < blob.size ? smaller : blob)
            })
          })
        }
        img.onerror = function() {
          URL.revokeObjectURL(url)
          resolve(file)
        }
        img.src = url
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
      // 识别结果按 省/市/区县/乡镇街道 用 / 拼接后回填，复制与打印均使用该格式
      this.form.address = this.joinAddressParts(data.address || '')
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
    /** 打开标准商品选择弹层，记录当前要回填的商品行 */
    openGoodsPicker(index) {
      this.goodsTargetIndex = index
      this.goodsPickerVisible = true
    },
    /** 转为商品行数据结构 */
    toGoodsRow(goods) {
      return {
        key: goods.key || '',
        name: goods.name || '',
        spec: goods.spec || '',
        spec1: goods.spec1 || '',
        spec2: goods.spec2 || '',
        spec3: goods.spec3 || ''
      }
    },
    /** 单选：弹层已由组件关闭，这里回填当前行 */
    handlePickGoods(goods) {
      if (!goods || !goods.name) return
      this.$set(this.goodsRows, this.goodsTargetIndex, this.toGoodsRow(goods))
      this.goodsPickerVisible = false
      // 商品名过长会折行加高，重新计算页面高度
      this.syncPageHeight()
    },
    /** 多选：从当前行开始依次回填，超出已有行则追加 */
    handleConfirmGoods(list) {
      if (!list || list.length === 0) return
      var self = this
      list.forEach(function(goods, i) {
        var index = self.goodsTargetIndex + i
        var row = self.toGoodsRow(goods)
        if (index < self.goodsRows.length) {
          self.$set(self.goodsRows, index, row)
        } else {
          self.goodsRows.push(row)
        }
      })
      this.goodsPickerVisible = false
      this.syncPageHeight()
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
.addr-copy-btn {
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 400;
  color: #1890ff;
  background: #e6f4ff;
  border: 1px solid #bae0ff;
  border-radius: 10px;
  cursor: pointer;
}
.addr-copy-btn:active {
  opacity: 0.7;
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
