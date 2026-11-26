<template>
  <div ref="page" class="addr-page">
    <div class="addr-body">
      <!-- 标准联系人信息提取面板：识别框 + 信息卡片（字段行随业务增减） -->
      <div ref="scrollBlankFirst">
        <contact-recognize-panel
          ref="panel"
          :form="form"
          :fields="fields"
          title="识别结果"
          copyable
          account-field="account"
          :platform-options="platformOptions"
          :z-index="2000"
          @filled="onRecognized"
          @input.native="syncPageHeight"
          @clear="onPanelClear">
          <!-- 商品：仅首行显示标题；末行为「点击添加商品」触发行，已添加商品行可删除 -->
          <div class="addr-row addr-goods-row" :class="{ 'is-first': idx === 0, 'is-trigger': !g.name }"
               v-for="(g, idx) in goodsRows" :key="'goods-' + idx" :ref="'goodsRow' + idx">
            <span class="addr-row__label">{{ idx === 0 ? '商品' : '' }}</span>
            <div class="addr-goods-box" @click="openGoodsPicker(idx)">
              <span v-if="g.name" class="addr-goods-text">{{ g.name }}<template v-if="g.spec"> · {{ g.spec }}</template></span>
              <span v-else class="addr-goods-placeholder">点击添加商品</span>
            </div>
            <!-- 数量：按数量分页打印时每件一张，默认 1 -->
            <div v-if="g.name" class="addr-goods-qty" @click.stop>
              <a-icon
                class="addr-goods-qty-btn"
                :class="{ disabled: (g.qty || 1) <= 1 }"
                type="minus"
                @click.stop="changeGoodsQty(idx, -1)" />
              <span class="addr-goods-qty-num">×{{ g.qty || 1 }}</span>
              <a-icon class="addr-goods-qty-btn" type="plus" @click.stop="changeGoodsQty(idx, 1)" />
            </div>
            <a-icon v-if="g.name" class="addr-goods-del" type="delete" @click.stop="removeGoodsRow(idx)" />
          </div>
        </contact-recognize-panel>
      </div>
      <div ref="scrollBlankLast" class="addr-last-marker"></div>

      <div ref="scrollBlankSpacer" class="addr-bottom-space"></div>
    </div>

    <!-- 打印按钮：与内容区同级，避免被内容滚动容器裁剪 -->
    <div ref="scrollBlankBottomBar" class="addr-actions">
      <a-button type="primary" block class="addr-print-btn" @click="printResult">打印</a-button>
    </div>

    <!-- 商品选择：新版整页弹层（左侧二级类目导航 + 点卡片加数量） -->
    <mobile-product-picker-v2
      :visible="goodsPickerVisible"
      title="添加商品"
      @confirm="handleConfirmGoods"
      @close="goodsPickerVisible = false" />

    <!-- 打印标签页：会员标签模板存在时自动按模板加载，识别结果按字段回填 -->
    <label-print-modal ref="labelPrintModal" @print-success="onPrintSuccess" />

    <!-- 会员比对：自动匹配不唯一时由用户选择更新已有会员或新建 -->
    <a-modal
      :visible="matchVisible"
      title="发现可能重复的会员"
      :footer="null"
      :maskClosable="false"
      :z-index="2100"
      @cancel="matchVisible = false">
      <div class="addr-match-tip">按{{ matchMatchedBy || '联系人/电话' }}找到以下会员，请选择要更新的会员，或直接新建。</div>
      <div class="addr-match-list">
        <div v-for="item in matchCandidates" :key="item.id" class="addr-match-item" @click="chooseExisting(item)">
          <div class="addr-match-head">
            <span class="addr-match-card">{{ item.supplier }}</span>
            <span class="addr-match-score">{{ item.score }} 分</span>
          </div>
          <div class="addr-match-line">联系人：{{ item.contacts || '-' }}</div>
          <div class="addr-match-line">电话：{{ formatMatchText(item.telephone) }}</div>
          <div class="addr-match-line">地址：{{ formatMatchText(item.address) }}</div>
        </div>
      </div>
      <a-button type="primary" block @click="createNewMember">不更新，新建会员</a-button>
      <a-button block class="addr-match-cancel" @click="matchVisible = false">暂不保存</a-button>
    </a-modal>
    <!-- 打印成功：询问是否把地址分享到微信（层级需高于整页打印弹层） -->
    <a-modal
      :visible="shareVisible"
      title="分享到微信"
      :footer="null"
      :maskClosable="false"
      :z-index="3100"
      @cancel="cancelShareToWechat">
      <div class="addr-match-tip">打印成功，是否将地址复制并打开微信发送？</div>
      <a-button block class="addr-match-cancel" @click="cancelShareToWechat">取消</a-button>
      <a-button type="primary" block class="addr-share-ok" @click="shareToWechat">分享</a-button>
    </a-modal>
  </div>
</template>

<script>
import { saveMemberFromAddress } from '@/api/api'
import { scrollBlankMixin } from '@/utils/scrollBlank'
import { LabelPrintModal } from '@/views/material/labelPrint'
import { ADDRESS_PANEL_FIELDS, extractAddressValues } from '@/views/material/labelPrint/utils/addressFormFields'
import { saveLabelEditSnapshot, LABEL_EDIT_SNAPSHOT_DEFAULTS } from '@/views/material/labelPrint/utils/labelEditSnapshot'
import ContactRecognizePanel from '@/components/ocr/ContactRecognizePanel'
import MobileProductPickerV2 from '@/components/mobile/MobileProductPickerV2'

/**
 * 地址识别独立页面：使用标准联系人信息提取面板（ContactRecognizePanel），
 * 仅展示/编辑识别结果并支持地址打印，不回填任何业务表单。
 */
export default {
  name: 'AddressRecognize',
  mixins: [scrollBlankMixin],
  components: { LabelPrintModal, ContactRecognizePanel, MobileProductPickerV2 },
  data() {
    return {
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
      fields: ADDRESS_PANEL_FIELDS.map(function(field) { return Object.assign({}, field) }),
      // 商品行：每行一个已选商品
      goodsRows: [{ key: '', name: '', spec: '', spec1: '', spec2: '', spec3: '' }],
      goodsTargetIndex: 0,
      // 商品选择整页弹层
      goodsPickerVisible: false,
      // 会员保存：打印成功后自动比对保存，多个候选时弹层确认
      memberSaving: false,
      // 打印成功时的表单快照：页面清空后会员比对/保存仍用打印前的数据
      memberFormSnapshot: null,
      matchVisible: false,
      matchCandidates: [],
      matchMatchedBy: '',
      // 打印成功后的分享询问层
      shareVisible: false
    }
  },
  mounted() {
    // 打开地址页：询问是否填入剪贴板内容并自动识别
    this.askClipboardFill()
    // 电脑端按可视区剩余高度撑满页面，保证底部打印按钮始终可见
    this.fitPageHeight()
    this._fitPageHandler = this.fitPageHeight.bind(this)
    window.addEventListener('resize', this._fitPageHandler)
  },
  activated() {
    // keep-alive 缓存下再次进入页面也算打开地址页
    this.askClipboardFill()
    this.fitPageHeight()
  },
  beforeDestroy() {
    if (this._fitPageHandler) window.removeEventListener('resize', this._fitPageHandler)
  },
  methods: {
    /**
     * 电脑端页面高度：页签下剩余可视高度
     * 内容区（.addr-body）内部滚动，打印栏留在页面底部，输入信息后也无需滚动即可看到打印按钮；
     * 手机端由固定底栏方案处理，不写入高度
     */
    fitPageHeight() {
      var page = this.$refs.page
      if (!page) return
      if (window.innerWidth <= 768) {
        page.style.height = ''
        return
      }
      var top = page.getBoundingClientRect().top +
        (window.pageYOffset || document.documentElement.scrollTop || 0)
      page.style.height = Math.max(320, window.innerHeight - top) + 'px'
    },
    /**
     * 打开地址页：询问是否将剪贴板内容填入文本框并自动识别
     * 仅当前文本框为空时询问；桌面/安卓可直接读剪贴板，有内容才弹询问；
     * iOS 不允许无手势读剪贴板，改为先询问、在「确定」点击手势里再读取
     */
    askClipboardFill() {
      // 手机端不自动询问：iOS 不允许无手势读剪贴板，自动询问必然弹确认框，
      // 会挡在进入的页面前（表现为要点两下才进页面）。手机端粘贴由识别框的
      // 「粘贴识别」按钮承担——按钮点击即用户手势，可直接读取剪贴板，无需询问层。
      if (window.innerWidth <= 768) return
      if (this._clipboardAsking) return
      var panel = this.$refs.panel
      if (!panel) return
      // 文本框已有内容时不打扰
      if (String(panel.pasteText || '').trim()) return
      if (!window.isSecureContext || !navigator.clipboard || !navigator.clipboard.readText) return
      this._clipboardAsking = true
      var self = this
      var done = function() { self._clipboardAsking = false }
      navigator.clipboard.readText().then(function(text) {
        text = String(text || '').trim()
        // 剪贴板为空时不弹询问
        if (!text) {
          done()
          return
        }
        self.confirmClipboardFill(panel, text, done)
      }).catch(function() {
        // iOS 等平台要求用户手势才能读剪贴板：先询问，确定时再读取
        self.$confirm({
          title: '粘贴剪贴板内容',
          content: '是否读取剪贴板内容填入文本框并自动识别？',
          okText: '确定',
          cancelText: '取消',
          zIndex: 2200,
          onOk() {
            navigator.clipboard.readText().then(function(text) {
              text = String(text || '').trim()
              if (!text) {
                self.$message.info('剪贴板为空，请手动粘贴')
                return
              }
              panel.pasteText = text
              // 确定后立即自动识别
              panel.parsePastedText()
            }).catch(function() {
              self.$message.warning('剪贴板读取失败，请长按文本框手动粘贴')
            }).finally(done)
          },
          onCancel() { done() }
        })
      })
    },
    /** 已读到剪贴板内容时的询问：确定则填入并自动识别 */
    confirmClipboardFill(panel, text, done) {
      this.$confirm({
        title: '检测到剪贴板内容',
        content: '是否将剪贴板内容填入文本框并自动识别？',
        okText: '确定',
        cancelText: '取消',
        zIndex: 2200,
        onOk() {
          panel.pasteText = text
          // 确定后立即自动识别
          panel.parsePastedText()
          done()
        },
        onCancel() { done() }
      })
    },
    /** 识别完成：内容高度可能变化，重算页面高度 */
    onRecognized() {
      this.syncPageHeight()
    },
    /** 清除识别结果：重置输入与商品行 */
    onPanelClear() {
      this.form.contacts = ''
      this.form.telephone = ''
      this.form.account = ''
      this.form.platform = '抖音'
      this.form.remark = ''
      this.form.address = ''
      this.goodsRows = [{ key: '', name: '', spec: '', spec1: '', spec2: '', spec3: '' }]
      this.syncPageHeight()
    },
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
    /** 商品多行数据（打印/模板编辑用）：名称 + 规格 + 规格值1/2/3 + 数量，保留原始商品记录 */
    goodsRowList() {
      return this.goodsRows.filter(function(g) {
        return g && (g.name || g.spec1 || g.spec2 || g.spec3)
      }).map(function(g) {
        return {
          name: g.name || '',
          // 品类 ID：打印时按一级类目映射规格（取原始商品记录的类目）
          categoryId: (g.raw && g.raw.categoryId) || g.categoryId || '',
          // SKU 主键：打印时按 SKU 读取规格明细
          skuId: g.skuId || (g.raw && (g.raw.meId || (g.raw.raw && g.raw.raw.meId))) || '',
          spec: g.spec || '',
          spec1: g.spec1 || '',
          spec2: g.spec2 || '',
          spec3: g.spec3 || '',
          qty: Math.min(99, Math.max(1, parseInt(g.qty, 10) || 1)),
          raw: g.raw || null
        }
      })
    },
    /** 调整商品数量：按数量分页打印时一件一张，范围 1-99 */
    changeGoodsQty(index, delta) {
      var row = this.goodsRows[index]
      if (!row) return
      var next = Math.min(99, Math.max(1, (parseInt(row.qty, 10) || 1) + delta))
      this.$set(row, 'qty', next)
    },
    /** 保证商品列表末尾始终有一行「点击添加商品」触发行 */
    ensureGoodsTailRow() {
      var last = this.goodsRows[this.goodsRows.length - 1]
      if (!last || last.name) {
        this.goodsRows.push({ key: '', name: '', spec: '', spec1: '', spec2: '', spec3: '' })
      }
    },
    removeGoodsRow(index) {
      this.goodsRows.splice(index, 1)
      this.ensureGoodsTailRow()
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
    /** 转为商品行数据结构：保留所选商品原始记录，供标签模板回填商品属性 */
    toGoodsRow(goods) {
      var raw = goods.raw || null
      return {
        key: goods.key || '',
        name: goods.name || '',
        spec: goods.spec || '',
        spec1: goods.spec1 || '',
        spec2: goods.spec2 || '',
        spec3: goods.spec3 || '',
        // 商品类别：标签打印按类别匹配规格映射表
        categoryId: goods.categoryId || (raw && (raw.categoryId || (raw.raw && raw.raw.categoryId))) || '',
        // SKU 主键：标签打印按 SKU 读取规格明细
        skuId: goods.skuId || (raw && (raw.meId || (raw.raw && raw.raw.meId))) || '',
        qty: Math.min(99, Math.max(1, parseInt(goods.qty, 10) || 1)),
        raw: raw
      }
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
      // 多选回填后自动补一行触发行
      this.ensureGoodsTailRow()
      this.goodsPickerVisible = false
      this.syncPageHeight()
    },
    // ===== 打印成功后生成会员信息 =====
    /** 打印成功回调：快照表单用于会员比对保存，并询问是否把地址分享到微信 */
    onPrintSuccess() {
      var that = this
      // 打印成功：先快照表单（分享与会员保存都用打印前的数据）
      this.memberFormSnapshot = Object.assign({}, this.form)
      // 弹出分享询问层；页面清空改为用户点「分享」后执行
      this.shareVisible = true
      var hasInfo = String(this.memberFormSnapshot.contacts || '').trim() || String(this.memberFormSnapshot.telephone || '').trim()
        || String(this.memberFormSnapshot.account || '').trim()
      if (!hasInfo) return
      // 防止打印机回调重复触发导致重复保存
      if (this.memberSaving) return
      this.memberSaving = true
      this.saveMember().finally(function() {
        that.memberSaving = false
      })
    },
    /** 取消分享：仅关闭询问层，保留打印预览与页面数据 */
    cancelShareToWechat() {
      this.shareVisible = false
    },
    /** 分享到微信：复制地址 → 关闭分享层与打印层 → 清空页面数据 → 打开微信 */
    shareToWechat() {
      var panel = this.$refs.panel
      var text = panel ? panel.buildCopyText() : ''
      if (!text) {
        this.shareVisible = false
        this.$message.warning('请先填写地址信息')
        return
      }
      this.shareVisible = false
      // 关闭整页打印弹层
      if (this.$refs.labelPrintModal) this.$refs.labelPrintModal.handleCancel()
      // 同步复制，避免跳转微信中断异步剪贴板写入
      var copied = panel.copyByTextarea(text, true)
      if (!copied) panel.copyToClipboard(text, true)
      // 清空识别文本、图片、表单字段与商品行
      panel.clearAll()
      // 微信内置浏览器无法再跳回微信，提示手动粘贴
      if (/MicroMessenger/i.test(navigator.userAgent)) {
        this.$message.info('地址已复制，长按聊天输入框粘贴即可发送')
        return
      }
      this.$message.success(copied ? '地址已复制，正在打开微信' : '正在打开微信，请到聊天框粘贴地址')
      window.location.href = 'weixin://'
      // 兜底：2 秒后仍在本页说明跳转被拦截
      var that = this
      setTimeout(function() {
        if (!document.hidden) {
          that.$message.warning('未能跳转微信，地址已复制，请手动切换到微信粘贴发送')
        }
      }, 2000)
    },
    /** 保存会员：extra 用于确认弹层选择（decision=update/create + supplierId）；打印后页面已清空，取打印前快照 */
    saveMember(extra) {
      var that = this
      var source = this.memberFormSnapshot || this.form
      var payload = Object.assign({
        contacts: source.contacts,
        telephone: source.telephone,
        address: source.address,
        account: source.account,
        platform: source.platform,
        remark: source.remark
      }, extra || {})
      return saveMemberFromAddress(payload).then(function(res) {
        if (!(res && res.code === 200 && res.data)) {
          that.$message.error((res && res.data && res.data.message) || '会员信息保存失败')
          return
        }
        var data = res.data
        if (data.action === 'create') {
          that.$message.success('已新增会员 ' + (data.memberId || ''))
        } else if (data.action === 'update') {
          var changes = (data.changes || []).join('；')
          that.$message.success('已更新会员 ' + (data.memberId || '') + (changes ? '（' + changes + '）' : ''))
        } else if (data.action === 'skip') {
          that.$message.info(data.reason ? ('未保存：' + data.reason) : '会员信息未变化，已跳过')
        } else if (data.action === 'confirm') {
          that.matchCandidates = data.candidates || []
          that.matchMatchedBy = data.matchedBy || ''
          that.matchVisible = true
        }
      }).catch(function() {
        that.$message.error('会员信息保存失败')
      })
    },
    /** 确认弹层：更新选中的已有会员 */
    chooseExisting(candidate) {
      this.matchVisible = false
      this.saveMember({ decision: 'update', supplierId: candidate.id })
    },
    /** 确认弹层：不更新，直接新建会员 */
    createNewMember() {
      this.matchVisible = false
      this.saveMember({ decision: 'create' })
    },
    /** 弹层内多行信息展示 */
    formatMatchText(value) {
      return value ? String(value).replace(/\n/g, '、') : '-'
    },
    printResult() {
      // 表单数据统一由地址打印提取模块生成，快照统一由标签编辑快照模块写入
      var values = extractAddressValues({
        form: this.form,
        goods: this.goodsValue(),
        goodsRows: this.goodsRowList(),
        platform: this.form.platform
      })
      saveLabelEditSnapshot(Object.assign({}, LABEL_EDIT_SNAPSHOT_DEFAULTS, {
        // 快照身份：编辑页打印合并时按 type 隔离，避免带入其它单据数据
        type: 'address',
        values: values,
        // 商品明细行：标签编辑/打印按行数动态排版
        rows: values.goodsRows
      }))
      // 创建/编辑地址打印模板时带回本页输入值，不绑定会员数据源
      this.$refs.labelPrintModal.openByType('address', { title: '地址打印', record: values })
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
/* 页面高度计算锚点：位于标准面板底部 */
.addr-last-marker {
  height: 0;
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
/* 「点击添加商品」触发行：浅灰提示、不可编辑，点击打开商品选择 */
.addr-goods-placeholder {
  color: #bfbfbf;
}
.addr-goods-row.is-trigger .addr-goods-box {
  cursor: pointer;
}
.addr-goods-del {
  flex-shrink: 0;
  margin-left: 8px;
  font-size: 20px;
  color: #ff4d4f;
}
/* 商品数量：分页打印时按数量拆成多张 */
.addr-goods-qty {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  margin-left: 6px;
  border: 1px solid #d9d9d9;
  border-radius: 12px;
  overflow: hidden;
}
.addr-goods-qty-btn {
  padding: 2px 6px;
  font-size: 12px;
  color: #1890ff;
}
.addr-goods-qty-btn.disabled {
  color: #d9d9d9;
}
.addr-goods-qty-num {
  min-width: 28px;
  text-align: center;
  font-size: 12px;
  color: #333;
}
.addr-print-btn {
  height: 46px;
  font-size: 16px;
  border-radius: 23px;
  background: #1890ff;
  border-color: #1890ff;
  font-weight: 500;
}
/* ===== 会员比对确认弹层 ===== */
.addr-match-tip {
  margin-bottom: 10px;
  font-size: 13px;
  line-height: 20px;
  color: #8c8c8c;
}
.addr-match-list {
  max-height: 46vh;
  overflow-y: auto;
  margin-bottom: 12px;
}
.addr-match-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #f7f9fc;
  border: 1px solid #e8eef7;
  border-radius: 8px;
  cursor: pointer;
}
.addr-match-item:active {
  background: #eef5ff;
}
.addr-match-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.addr-match-card {
  font-size: 15px;
  font-weight: 600;
  color: #262626;
}
.addr-match-score {
  font-size: 12px;
  color: #1890ff;
}
.addr-match-line {
  font-size: 13px;
  line-height: 20px;
  color: #595959;
  word-break: break-all;
}
.addr-match-cancel {
  margin-top: 8px;
}
.addr-share-ok {
  margin-top: 8px;
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
}
@media (min-width: 769px) {
  .addr-bottom-space {
    height: calc(12px + env(safe-area-inset-bottom));
  }
  /* 电脑端：页面高度由脚本按「页签下剩余可视高度」写入，内容区内部滚动，
     打印按钮固定在页面底部，输入信息后内容变长也无需滚动即可看到打印按钮 */
  .addr-page {
    min-height: 0;
    overflow: hidden;
  }
  .addr-body {
    min-height: 0;
    overflow-y: auto;
  }
  /* 打印栏与内容区同宽居中，避免在电脑端拉满整屏 */
  .addr-actions {
    width: 100%;
    max-width: 860px;
    margin: 16px auto 0;
    padding: 0 12px 12px;
  }
}
</style>
