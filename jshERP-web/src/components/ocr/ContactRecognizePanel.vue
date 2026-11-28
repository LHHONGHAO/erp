<template>
  <div class="contact-panel">
    <!-- 标准识别框：粘贴文本 / 拍照识别 / 图片识别 / 清除 / 智能识别 -->
    <div class="addr-ocr-box">
      <image-recognize-box
        v-model="pasteText"
        placeholder="粘贴名片、快递单等文本内容，或拍照 / 选择图片自动识别"
        :image-url="imageUrl"
        :loading="ocrLoading"
        :error-text="ocrError"
        :smart-loading="pasteLoading"
        :image-inline="imageInline"
        @camera="triggerCamera"
        @gallery="triggerGallery"
        @clear="clearAll"
        @smart="parsePastedText"
        @paste="onPaste"
        @paste-fail="onPasteFail"
        @preview="previewVisible = true" />
      <input ref="cameraInput" type="file" accept="image/*" capture="environment" class="addr-file-input" @change="onPick" />
      <input ref="galleryInput" type="file" accept="image/*" class="addr-file-input" @change="onPick" />
    </div>

    <!-- 信息卡片：行数随业务字段配置增减，样式固定 -->
    <div class="addr-card">
      <div class="addr-card__head">
        <span>{{ title }}</span>
        <div class="addr-card__head-right">
          <span v-if="showStatus" class="addr-status" :class="{ 'is-done': hasResult }">{{ hasResult ? '已识别' : '待识别' }}</span>
          <span v-if="copyable" class="addr-copy-btn" @click="openWechat">微信</span>
          <span v-if="copyable" class="addr-copy-btn" @click="shareContact">分享</span>
        </div>
      </div>
      <div class="addr-row" v-for="prop in fields" :key="prop.field">
        <template v-if="accountField && prop.field === accountField">
          <span class="addr-row__label">{{ prop.label }}</span>
          <a-input v-model="form[prop.field]" class="addr-row__input addr-account-input" :bordered="false"
                   :placeholder="getPlaceholder(prop.label)" />
          <a-select v-model="form[platformField]" class="addr-platform-select" :dropdownMatchSelectWidth="false">
            <a-select-option v-for="opt in platformOptions" :key="opt" :value="opt">{{ opt }}</a-select-option>
          </a-select>
        </template>
        <template v-else>
          <span class="addr-row__label">{{ prop.label }}</span>
          <a-textarea v-if="prop.type === 'textarea'" v-model="form[prop.field]" class="addr-row__input"
                      :bordered="false" autosize :placeholder="getPlaceholder(prop.label)" />
          <a-input v-else v-model="form[prop.field]" class="addr-row__input" :bordered="false"
                   :placeholder="getPlaceholder(prop.label)" />
          <!-- 联系人行：地址簿按钮，从已有会员中选联系人 -->
          <span v-if="prop.field === 'contacts'" class="addr-book-btn" @click="openAddressBook">
            <a-icon type="book" />
          </span>
        </template>
      </div>
      <!-- 业务扩展行（如地址识别的商品行） -->
      <slot></slot>
    </div>

    <!-- 业务操作区（如确定、打印） -->
    <slot name="actions"></slot>

    <!-- 图片放大预览 -->
    <a-modal
      :visible="previewVisible"
      :footer="null"
      :width="'96%'"
      :zIndex="zIndex + 10"
      wrapClassName="ocr-preview-modal"
      @cancel="previewVisible = false">
      <img :src="imageUrl" class="addr-preview-full" @click="previewVisible = false" />
    </a-modal>

    <!-- 地址簿：从已有会员中选择联系人并回填 -->
    <a-modal
      :visible="bookVisible"
      :footer="null"
      :closable="false"
      :width="'96%'"
      :zIndex="zIndex + 10"
      wrapClassName="addr-book-modal"
      @cancel="bookVisible = false">
      <div class="addr-book__head">
        <span class="addr-book__title">地址簿</span>
      </div>
      <div class="addr-book__search">
        <a-icon type="search" class="addr-book__search-icon" />
        <input v-model="bookKeyword" class="addr-book__search-input" placeholder="输入姓名、手机号进行搜索" @input="onBookKeyword" />
        <a-icon v-if="bookKeyword" type="close-circle" class="addr-book__search-clear" @click="clearBookKeyword" />
      </div>
      <div class="addr-book__list">
        <div v-for="item in bookList" :key="item.id" class="addr-book__item" @click="bookSelected = item.id">
          <span class="addr-book__radio" :class="{ 'is-checked': bookSelected === item.id }"></span>
          <div class="addr-book__info">
            <div class="addr-book__name">
              {{ item.contacts || item.supplier || '未命名' }}
              <span v-if="item.telephone" class="addr-book__phone">{{ item.telephone }}</span>
            </div>
            <div class="addr-book__addr">{{ formatBookAddress(item.address) }}</div>
          </div>
        </div>
        <div v-if="bookLoading" class="addr-book__empty">加载中...</div>
        <div v-else-if="!bookList.length" class="addr-book__empty">暂无联系人</div>
      </div>
      <div class="addr-book__foot">
        <a-button block class="addr-book__btn" @click="bookVisible = false">取消</a-button>
        <a-button block type="primary" class="addr-book__btn" :disabled="!bookSelected" @click="confirmBook">确认</a-button>
      </div>
    </a-modal>
  </div>
</template>

<script>
import { postAction, getAction } from '@/api/manage'
import ImageRecognizeBox from '@/components/ocr/ImageRecognizeBox'

/**
 * 标准联系人信息提取面板
 *
 * 统一的识别框 + 信息卡片外观，字段行由 fields 配置决定（不同业务可增减行），
 * 地址识别独立页、新增会员地址识别弹层共用该组件，样式与交互保持一致。
 */
export default {
  name: 'ContactRecognizePanel',
  components: { ImageRecognizeBox },
  props: {
    // 表单数据对象（由调用方持有，组件直接回填）
    form: {
      type: Object,
      required: true
    },
    // 字段配置：[{ field, label, type: 'input' | 'textarea' }]
    fields: {
      type: Array,
      required: true
    },
    // 卡片标题
    title: {
      type: String,
      default: '识别结果'
    },
    // 是否显示「分享」（复制地址 + 系统分享）
    copyable: {
      type: Boolean,
      default: false
    },
    // 是否显示「待识别 / 已识别」状态
    showStatus: {
      type: Boolean,
      default: false
    },
    // 账号行字段名：设置后该行右侧附带平台下拉框
    accountField: {
      type: String,
      default: ''
    },
    platformField: {
      type: String,
      default: 'platform'
    },
    platformOptions: {
      type: Array,
      default: function() { return [] }
    },
    // 图片在识别框内联显示（地址识别页样式）
    imageInline: {
      type: Boolean,
      default: true
    },
    zIndex: {
      type: Number,
      default: 1000
    }
  },
  data() {
    return {
      ocrLoading: false,
      pasteLoading: false,
      hasResult: false,
      ocrError: '',
      imageUrl: '',
      previewVisible: false,
      pasteText: '',
      // 地址簿
      bookVisible: false,
      bookKeyword: '',
      bookLoading: false,
      bookList: [],
      bookSelected: null,
      bookTimer: null
    }
  },
  beforeDestroy() {
    if (this.imageUrl) {
      URL.revokeObjectURL(this.imageUrl)
    }
    clearTimeout(this.bookTimer)
  },
  methods: {
    getPlaceholder(label) {
      return this.ocrLoading ? '识别中...' : ('请输入' + label)
    },
    // ===== 分享地址（复制到剪贴板 + 系统分享） =====
    /** 待复制内容：联系人、电话、地址、备注 */
    buildCopyText() {
      var parts = []
      var contacts = String(this.form.contacts || '').trim()
      var telephone = String(this.form.telephone || '').trim()
      var address = this.joinAddressParts(this.form.address)
      var remark = String(this.form.remark || '').trim()
      if (contacts) parts.push('联系人：' + contacts)
      if (telephone) parts.push('电话：' + telephone)
      if (address) parts.push('地址：' + address)
      // 地址识别页的备注随地址一起复制（无备注时不输出空行）
      if (remark) parts.push('备注：' + remark)
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
    /** 分享：先复制地址到剪贴板，支持系统分享时同时调起系统分享面板 */
    shareContact() {
      var text = this.buildCopyText()
      if (!text) {
        this.$message.warning('请先填写地址信息')
        return
      }
      var that = this
      this.copyToClipboard(text)
      // 微信分享扩展只接收图片/文件/链接，纯文本会被拒绝，因此优先以 txt 文件分享
      var file = null
      try {
        if (typeof File !== 'undefined' && navigator.canShare) {
          var f = new File([text], '地址信息.txt', { type: 'text/plain' })
          if (navigator.canShare({ files: [f] })) file = f
        }
      } catch (e) {
        file = null
      }
      if (navigator.share) {
        // 系统分享需在点击手势内同步调用，与复制保持同一手势激活
        var payload = file ? { files: [file], title: '地址信息' } : { title: '地址信息', text: text }
        navigator.share(payload).catch(function(err) {
          // 用户主动取消分享不提示
          if (err && err.name === 'AbortError') return
          // 其它失败给出原因，地址已复制可手动粘贴分享
          that.$message.warning('系统分享未能调起：' + ((err && err.message) || '未知原因') + '，地址已复制，可手动粘贴分享')
        })
      } else if (/MicroMessenger/i.test(navigator.userAgent)) {
        // 微信内置浏览器不支持 Web Share，引导用 Safari 打开或手动粘贴
        this.$message.info('微信内不支持系统分享，地址已复制，可直接粘贴给好友；需要系统分享请点右上角用 Safari 打开')
      } else {
        this.$message.info('当前浏览器不支持系统分享，地址已复制，可粘贴到微信等应用分享')
      }
    },
    /** 打开微信：同步复制地址后立即跳转微信，选好友长按粘贴发送（微信分享扩展不接收纯文本） */
    openWechat() {
      var text = this.buildCopyText()
      if (!text) {
        this.$message.warning('请先填写地址信息')
        return
      }
      // 微信内置浏览器无法再跳回微信，直接提示粘贴
      if (/MicroMessenger/i.test(navigator.userAgent)) {
        this.copyToClipboard(text, true)
        this.$message.info('地址已复制，长按聊天输入框粘贴即可发送')
        return
      }
      // 同步复制，避免页面跳转中断异步剪贴板写入
      var copied = this.copyByTextarea(text, true)
      if (!copied) this.copyToClipboard(text, true)
      this.$message.success(copied ? '地址已复制，正在打开微信' : '正在打开微信，请到聊天框粘贴地址')
      // 必须在点击手势内同步跳转，否则 iOS 会忽略自定义协议
      window.location.href = 'weixin://'
      // 兜底：2 秒后仍在本页说明跳转被拦截
      var that = this
      setTimeout(function() {
        if (!document.hidden) {
          that.$message.warning('未能跳转微信，地址已复制，请手动切换到微信粘贴发送')
        }
      }, 2000)
    },
    copyToClipboard(text, silent) {
      var that = this
      if (navigator.clipboard && navigator.clipboard.writeText && window.isSecureContext) {
        navigator.clipboard.writeText(text).then(function() {
          if (!silent) that.$message.success('地址信息已复制')
        }).catch(function() {
          that.copyByTextarea(text)
        })
      } else {
        that.copyByTextarea(text)
      }
    },
    /** 非安全上下文（http）下的兜底复制方案，返回是否复制成功 */
    copyByTextarea(text, silent) {
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
      if (!silent) {
        if (copied) this.$message.success('地址信息已复制')
        else this.$message.error('复制失败，请长按输入框手动复制')
      }
      return copied
    },
    // ===== 地址簿 =====
    openAddressBook() {
      this.bookVisible = true
      this.bookSelected = null
      this.bookKeyword = ''
      this.loadBook()
    },
    onBookKeyword() {
      var self = this
      clearTimeout(this.bookTimer)
      this.bookTimer = setTimeout(function() {
        self.loadBook()
      }, 350)
    },
    clearBookKeyword() {
      this.bookKeyword = ''
      this.loadBook()
    },
    /** 读取会员列表作为地址簿；关键词同时按联系人和电话查询后合并 */
    loadBook() {
      var self = this
      var keyword = String(this.bookKeyword || '').trim()
      var build = function(extra) {
        var search = Object.assign({ type: '会员' }, extra)
        return { search: JSON.stringify(search), currentPage: 1, pageSize: 50 }
      }
      var requests
      if (keyword) {
        requests = [
          getAction('/supplier/list', build({ contacts: keyword })),
          getAction('/supplier/list', build({ telephone: keyword }))
        ]
      } else {
        requests = [getAction('/supplier/list', build({}))]
      }
      this.bookLoading = true
      Promise.all(requests).then(function(results) {
        var rows = []
        var seen = {}
        results.forEach(function(res) {
          var list = (res && res.code === 200 && res.data && res.data.rows) || []
          list.forEach(function(row) {
            if (!row || seen[row.id]) return
            seen[row.id] = true
            rows.push(row)
          })
        })
        self.bookList = rows
        self.bookLoading = false
      }).catch(function() {
        self.bookLoading = false
        self.$message.error('地址簿加载失败')
      })
    },
    formatBookAddress(value) {
      return value ? String(value).replace(/\s*\n\s*/g, ' ') : '暂无地址'
    },
    /** 选中联系人回填：联系人、电话、地址 */
    confirmBook() {
      var self = this
      var target = null
      this.bookList.forEach(function(item) {
        if (item.id === self.bookSelected) target = item
      })
      if (!target) return
      if (target.contacts) this.form.contacts = target.contacts
      if (target.telephone) this.form.telephone = String(target.telephone).replace(/\s*\n\s*/g, '')
      if (target.address) this.form.address = this.formatBookAddress(target.address)
      this.bookVisible = false
      this.bookSelected = null
      this.setHasResult(true)
      this.$emit('filled', this.form)
    },
    // ===== 识别 =====
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
      this.ocrError = ''
      this.ocrLoading = false
      this.pasteLoading = false
      this.setHasResult(false)
      this.$emit('clear')
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
    /** 粘贴识别：按钮手势里读剪贴板，填入后走与智能识别相同的解析（iOS 无需询问层） */
    onPaste(text) {
      if (!text) {
        this.$message.info('剪贴板为空')
        return
      }
      this.pasteText = text
      this.parsePastedText()
    },
    onPasteFail() {
      this.$message.warning('剪贴板读取失败，请长按文本框手动粘贴')
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
    /**
     * 清理识别文本：去掉与地址无关的无效内容
     * 1. 纯数字/符号碎片（如快递单表格的行号 8、9、10）
     * 2. 订单号、运单号、重量、金额等物流/单据信息
     * 保留含中文的行、电话行，并去重
     */
    cleanOcrText(text) {
      var lines = String(text || '').split(/\r?\n/)
      var seen = {}
      var result = []
      lines.forEach(function(line) {
        var item = line.replace(/\s+/g, ' ').trim()
        if (!item) return
        var hasChinese = /[\u4e00-\u9fa5]/.test(item)
        var digits = item.replace(/\D/g, '')
        var isPhone = /^[+\d][\d\s()-]{5,19}$/.test(item) && digits.length >= 7
        // 非中文且不是电话的碎片行（行号、金额符号、分隔线等）直接丢弃
        if (!hasChinese && !isPhone) return
        // 与地址无关的单据/物流信息
        if (/^(订单号|运单号|快递单号|流水号|单号|重量|体积|数量|金额|合计|小计|单价|价格|日期|时间)[:：]?/.test(item)) return
        if (seen[item]) return
        seen[item] = true
        result.push(item)
      })
      return result.join('\n')
    },
    fillFields(data) {
      if (data.rawText) {
        // 回填编辑框前清理与地址无关的内容（表格行号、订单/物流信息等）
        this.pasteText = this.cleanOcrText(data.rawText)
      }
      var self = this
      this.fields.forEach(function(prop) {
        if (prop.field === 'address') {
          // 识别结果按 省/市/区县/乡镇街道 用 / 拼接后回填
          self.form[prop.field] = self.joinAddressParts(data.address || '')
        } else if (data[prop.field] != null && data[prop.field] !== '') {
          self.form[prop.field] = data[prop.field]
        }
      })
      this.setHasResult(true)
      this.$emit('filled', data)
    },
    setHasResult(value) {
      this.hasResult = !!value
      this.$emit('update:hasResult', this.hasResult)
    }
  }
}
</script>

<style scoped>
.addr-ocr-box {
  margin-bottom: 12px;
}
.addr-file-input {
  display: none;
}
.addr-card {
  margin-bottom: 12px;
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
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
.addr-status {
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 400;
  color: #8c8c8c;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.addr-status.is-done {
  color: #1890ff;
  border-color: #bae0ff;
  background: #e6f4ff;
}
.addr-copy-btn {
  margin-left: 8px;
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
.addr-preview-full {
  display: block;
  width: 100%;
  max-height: 78vh;
  object-fit: contain;
  cursor: zoom-out;
}
/* 联系人行：地址簿按钮 */
.addr-book-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  margin-left: 6px;
  font-size: 15px;
  color: #1890ff;
  background: #f0f7ff;
  border: 1px solid #cfe4ff;
  border-radius: 15px;
  cursor: pointer;
}
/* 地址簿弹层 */
.addr-book__head {
  position: relative;
  padding: 4px 0 12px;
  font-size: 17px;
  font-weight: 600;
  color: #262626;
  text-align: center;
}
.addr-book__search {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  background: #f5f5f5;
  border-radius: 8px;
}
.addr-book__search-icon {
  margin-right: 8px;
  color: #bfbfbf;
}
.addr-book__search-input {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  color: #262626;
  background: transparent;
  border: none;
  outline: none;
}
.addr-book__search-clear {
  color: #bfbfbf;
}
.addr-book__list {
  max-height: 52vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.addr-book__item {
  display: flex;
  align-items: center;
  padding: 14px 4px;
  border-bottom: 1px solid #f5f5f5;
}
.addr-book__radio {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  margin-right: 12px;
  border: 1.5px solid #d9d9d9;
  border-radius: 50%;
  box-sizing: border-box;
}
.addr-book__radio.is-checked {
  border: 5px solid #1890ff;
}
.addr-book__info {
  flex: 1;
  min-width: 0;
}
.addr-book__name {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
}
.addr-book__phone {
  margin-left: 8px;
  font-weight: 400;
  color: #595959;
}
.addr-book__addr {
  margin-top: 4px;
  font-size: 13px;
  line-height: 18px;
  color: #8c8c8c;
  word-break: break-all;
}
.addr-book__empty {
  padding: 30px 0;
  font-size: 14px;
  color: #bfbfbf;
  text-align: center;
}
.addr-book__foot {
  display: flex;
  margin-top: 14px;
}
.addr-book__btn {
  flex: 1;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}
.addr-book__btn + .addr-book__btn {
  margin-left: 12px;
}
</style>
