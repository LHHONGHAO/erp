<template>
  <div class="dn-page">
    <mobile-top-bar :title="pageTitle" @back="goBack">
      <template #actions>
        <span v-if="hasTemplate" class="mtb-action" @click="openTemplate">编辑模板</span>
      </template>
    </mobile-top-bar>
    <div v-if="!loading && !hasTemplate" class="dn-empty">
      <div class="dn-empty-title">添加发货模板</div>
      <div class="dn-empty-desc">尚未设置发货单模板，添加后可查看并核对发货单</div>
      <a-button type="primary" size="large" @click="openTemplate">添加</a-button>
    </div>
    <div v-else-if="hasTemplate" class="dn-body">
      <!-- 新格式：自由布局模板，用通用组件渲染 -->
      <template-paper
        v-if="isNewFormat"
        :template="tpl"
        :values="paperValues"
        :rows="paperRows"
        :tableKeys="['dn_table']"
        :detailColKeys="['dn_col_name','dn_col_spec','dn_col_qty','dn_col_price','dn_col_amount']" />
      <!-- 旧格式：表单模版 -->
      <div v-else class="dn-paper">
        <div class="dn-paper-title">{{ tpl.title || '发货单' }}</div>
        <div v-if="tpl.companyName || tpl.companyAddress || tpl.companyPhone" class="dn-company">
          <div v-if="tpl.companyName" class="dn-company-name">{{ tpl.companyName }}</div>
          <div v-if="tpl.companyAddress" class="dn-company-line">地址：{{ tpl.companyAddress }}</div>
          <div v-if="tpl.companyPhone" class="dn-company-line">电话：{{ tpl.companyPhone }}</div>
        </div>
        <div class="dn-meta">
          <div class="dn-meta-row">
            <span class="dn-meta-label">单号</span>
            <span class="dn-meta-value">{{ bill.number }}</span>
          </div>
          <div class="dn-meta-row">
            <span class="dn-meta-label">日期</span>
            <span class="dn-meta-value">{{ bill.operTimeStr || '--' }}</span>
          </div>
          <div v-if="tpl.showMember" class="dn-meta-row">
            <span class="dn-meta-label">会员</span>
            <span class="dn-meta-value">{{ memberName }}</span>
          </div>
          <div v-if="tpl.showMember && memberPhone" class="dn-meta-row">
            <span class="dn-meta-label">电话</span>
            <span class="dn-meta-value">{{ memberPhone }}</span>
          </div>
        </div>
        <div class="dn-table">
          <div class="dn-table-head">
            <div class="dn-col-name">商品</div>
            <div class="dn-col-qty">数量</div>
            <div class="dn-col-price">单价</div>
            <div class="dn-col-amount">金额</div>
          </div>
          <div v-for="(item, index) in items" :key="index" class="dn-table-row">
            <div class="dn-col-name">
              <span>{{ item.materialName || item.name }}</span>
              <span v-if="itemSpec(item)" class="dn-item-spec">{{ itemSpec(item) }}</span>
            </div>
            <div class="dn-col-qty">{{ formatNumber(item.operNumber) }}</div>
            <div class="dn-col-price">{{ formatMoney(item.unitPrice) }}</div>
            <div class="dn-col-amount">{{ formatMoney(itemAmount(item)) }}</div>
          </div>
          <div v-if="items.length === 0" class="dn-item-empty">无商品明细</div>
        </div>
        <div class="dn-total-row">
          <span class="dn-total-label">合计数量</span>
          <span class="dn-total-value">{{ formatNumber(totalQty) }}</span>
        </div>
        <div class="dn-total-row">
          <span class="dn-total-label">合计金额</span>
          <span class="dn-total-value">¥{{ formatMoney(totalAmount) }}</span>
        </div>
        <div class="dn-total-row">
          <span class="dn-total-label">实收金额</span>
          <span class="dn-total-value">¥{{ formatMoney(paidAmount) }}</span>
        </div>
        <div v-if="tpl.showRemark && bill.remark" class="dn-remark">备注：{{ bill.remark }}</div>
        <div v-for="(line, index) in customLines" :key="'line-' + index" class="dn-line">{{ line }}</div>
        <div v-if="tpl.showFooter && tpl.footerText" class="dn-footer">{{ tpl.footerText }}</div>
      </div>
    </div>
    <!-- 底部打印工具栏 -->
    <div v-if="hasTemplate" class="dn-footer-bar">
      <a-button type="primary" icon="printer" block size="large" @click="handlePrint">打印发货单</a-button>
    </div>
    <label-edit-modal ref="labelEditModal" />
  </div>
</template>

<script>
  import { getAction, postAction } from '@/api/manage'
  import { findBillDetailByNumber, findBySelectRetail } from '@/api/api'
  import { getMpListShort } from '@/utils/util'
  import Vue from 'vue'
  import LabelEditModal from '@/views/material/modules/LabelEditModal'
  import TemplatePaper from '@/views/material/modules/TemplatePaper'
  import MobileTopBar from '@/components/mobile/MobileTopBar'
  import { fetchDeliveryTemplate } from '@/views/bill/utils/deliveryNoteTemplateStore'

  export default {
    name: 'DeliveryNote',
    components: { LabelEditModal, TemplatePaper, MobileTopBar },
    data() {
      return {
        loading: true,
        number: '',
        tpl: null,
        bill: {},
        items: [],
        member: null
      }
    },
    computed: {
      hasTemplate() {
        return !!this.tpl
      },
      paperW() {
        return (this.tpl && this.tpl.width) || 560
      },
      paperH() {
        return (this.tpl && this.tpl.height) || 800
      },
      isNewFormat() {
        return !!(this.tpl && this.tpl.selectedAttrs && this.tpl.selectedAttrs.length)
      },
      // 拍平的普通元素值：key → 已格式化的字符串
      paperValues() {
        var self = this
        var bill = this.bill || {}, member = this.member || {}
        var labels = (this.tpl && this.tpl.attrLabels) || {}
        var map = {}
        ;(this.tpl && this.tpl.selectedAttrs || []).forEach(function(key) {
          // 明细表和明细列不在普通 values 里
          if (key === 'dn_table') return
          if (['dn_col_name','dn_col_spec','dn_col_qty','dn_col_price','dn_col_amount'].indexOf(key) > -1) return
          // 实收金额特殊处理
          if (key === 'dn_paid_total') { map[key] = '¥' + self.formatMoney(self.paidAmount); return }
          // 通用：尝试 getDeliveryValue
          map[key] = self.getDeliveryValue(key)
        })
        return map
      },
      // 拍平的明细行：[{ name, spec, qty, price, amount }, ...]
      paperRows() {
        var self = this
        return this.items.map(function(item) {
          return {
            name:   item.materialName || item.name || '',
            spec:   self.itemSpec(item),
            qty:    self.formatNumber(item.operNumber),
            price:  self.formatMoney(item.unitPrice),
            amount: self.formatMoney(self.itemAmount(item))
          }
        })
      },
      pageTitle() {
        if (this.isNewFormat) {
          var titleAttr = this.tpl.selectedAttrs.find(k => k === 'dn_title')
          return '发货单'
        }
        return (this.tpl && this.tpl.title) || '发货单'
      },
      memberName() {
        return (this.member && this.member.name) || '散客'
      },
      memberPhone() {
        return (this.member && this.member.telephone) || ''
      },
      customLines() {
        return (this.tpl && this.tpl.customLines) ? this.tpl.customLines.filter(line => !!line) : []
      },
      totalQty() {
        return this.items.reduce((sum, item) => sum + this.toNumber(item.operNumber), 0)
      },
      totalAmount() {
        return this.items.reduce((sum, item) => sum + this.toNumber(this.itemAmount(item)), 0)
      },
      paidAmount() {
        const paid = this.bill.changeAmount
        return paid === undefined || paid === null ? this.totalAmount : this.toNumber(paid)
      }
    },
    mounted() {
      this.number = this.$route.query.number
      this.loadTemplate()
      this.loadBill()
    },
    beforeDestroy() {
      // 无（TemplatePaper 组件自己处理 resize 清理）
    },
    methods: {
      goBack() {
        this.$router.back()
      },
      handlePrint() {
        // 保存当前模板快照到 sessionStorage，跳转到打印预览页（LabelPrintModal 独立路由）
        // 模板结构 + 业务值一起带过去：预览页先标准化模板，再回填 values，避免读到未规整的数据
        try {
          sessionStorage.setItem('jsh.labelEdit.snapshot', JSON.stringify({
            type: 'delivery',
            number: this.number,
            labelWidth: this.paperW,
            labelHeight: this.paperH,
            selectedAttrs: this.tpl ? this.tpl.selectedAttrs : [],
            attrStyles: this.tpl ? (this.tpl.attrStyles || {}) : {},
            attrLabels: this.tpl ? (this.tpl.attrLabels || {}) : {},
            provider: (this.tpl && this.tpl.provider) || 'sales.delivery',
            values: this.paperValues,
            rows: this.paperRows
          }))
        } catch (e) { /* 忽略 */ }
        this.$router.push({ path: '/label/print-mobile', query: { type: 'delivery' } })
      },
      openTemplate() {
        var isMobile = window.innerWidth <= 768
        if (isMobile) {
          // 带上来源业务与单据数据：编辑页会自动选中「销售出库」数据表并回填字段值
          try {
            sessionStorage.setItem('jsh.labelEdit.context', JSON.stringify({
              record: this.bill || {},
              sku: {},
              provider: 'sales.delivery',
              businessType: 'delivery'
            }))
          } catch (e) { /* 忽略 */ }
          this.$router.push({ path: '/label/edit-mobile', query: { type: 'delivery', businessType: 'delivery' } })
        } else {
          this.$refs.labelEditModal.open({}, {}, [], null, 'delivery')
        }
      },
      toNumber(value) {
        const num = Number(value)
        return isNaN(num) ? 0 : num
      },
      formatNumber(value) {
        const num = this.toNumber(value)
        return Number.isInteger(num) ? String(num) : num.toFixed(2)
      },
      formatMoney(value) {
        return this.toNumber(value).toFixed(2)
      },
      itemAmount(item) {
        const allPrice = this.toNumber(item.allPrice)
        if (allPrice) return allPrice
        return this.toNumber(item.unitPrice) * this.toNumber(item.operNumber)
      },
      itemSpec(item) {
        if (!item.sku) return ''
        let sku = item.sku
        if (typeof sku === 'string') {
          try {
            sku = JSON.parse(sku)
          } catch (e) {
            return String(item.sku)
          }
        }
        if (Array.isArray(sku)) {
          return sku.map(s => (s && typeof s === 'object') ? (s.value || s.VALUE || '') : s).filter(v => !!v).join('/')
        }
        if (typeof sku === 'object' && sku !== null) {
          if (Array.isArray(sku.specValues)) {
            return sku.specValues.map(s => (s && typeof s === 'object') ? (s.value || '') : s).filter(v => !!v).join('/')
          }
          return Object.keys(sku).map(key => sku[key]).filter(v => typeof v === 'string' || typeof v === 'number').join('/')
        }
        return ''
      },
      loadTemplate() {
        // 通过统一模板存储适配器读取发货单模板（print_template，兼容旧 platformConfig）
        var self = this
        fetchDeliveryTemplate().then(function(tpl) {
          self.tpl = (tpl && (tpl.selectedAttrs || tpl.title)) ? tpl : null
          self.loading = false
        }).catch(function() {
          self.tpl = null
          self.loading = false
        })
      },
      // 新格式：根据元素 key 获取发货单实际数据值
      getDeliveryValue(key) {
        var bill = this.bill || {}
        var member = this.member || {}
        switch (key) {
          case 'dn_title': return this.tpl.attrLabels && this.tpl.attrLabels[key] ? this.tpl.attrLabels[key].replace(/：$/, '') : '发货单'
          case 'dn_no': return bill.number || '--'
          case 'dn_date': return bill.operTimeStr || '--'
          case 'dn_member': return bill.organName || this.memberName
          case 'dn_receiver': return bill.contacts || member.name || this.memberName
          case 'dn_phone': return bill.telephone || member.telephone || '--'
          case 'dn_address': return bill.address || member.address || '--'
          case 'dn_depot': return bill.depotName || '--'
          case 'dn_paytype': return bill.payType || '--'
          case 'dn_account': return bill.accountName || '--'
          case 'dn_operator': return bill.userName || '--'
          case 'dn_salesman': return bill.salesManStr || '--'
          case 'dn_remark': return bill.remark || ''
          case 'dn_attach': return bill.fileName || '无'
          case 'dn_qty_total': return this.formatNumber(this.totalQty)
          case 'dn_amount_total': return '¥' + this.formatMoney(this.totalAmount)
          case 'dn_discount': return bill.discount == null ? '--' : String(bill.discount)
          case 'dn_discount_amount': return bill.discountMoney == null ? '--' : this.formatMoney(bill.discountMoney)
          case 'dn_real_amount': return bill.discountLastMoney == null ? this.formatMoney(this.totalAmount) : this.formatMoney(bill.discountLastMoney)
          case 'dn_back_amount': return bill.backAmount == null ? '0.00' : this.formatMoney(bill.backAmount)
          case 'dn_status': return this.statusText(bill.status)
          case 'dn_footer': return (this.tpl.attrLabels && this.tpl.attrLabels[key]) || '华夏家纺'
          // 自定义标签：文案直接取模板中保存的属性名
          default: return (this.tpl.attrLabels && this.tpl.attrLabels[key]) || ''
        }
      },
      statusText(status) {
        var map = { '0': '未审核', '1': '已审核', '2': '已完成', '3': '已关闭' }
        return map[String(status)] || (status == null ? '--' : String(status))
      },
      loadBill() {
        if (!this.number) {
          this.loading = false
          return
        }
        findBillDetailByNumber({ number: this.number }).then(res => {
          if (res && res.code === 200 && res.data) {
            this.bill = res.data
            this.loadItems()
            this.loadMember()
          }
        }).catch(() => {})
      },
      loadItems() {
        if (!this.bill.id) return
        const params = {
          headerId: this.bill.id,
          mpList: getMpListShort(Vue.ls.get('materialPropertyList')),
          linkType: 'basic',
          isReadOnly: '0'
        }
        getAction('/depotItem/getDetailList', params).then(res => {
          if (res && res.code === 200 && res.data) {
            this.items = res.data.rows || []
          }
        }).catch(() => {})
      },
      loadMember() {
        if (!this.bill.organId) return
        postAction('/supplier/findBySelect_retail', { organId: this.bill.organId }).then(res => {
          let list = []
          if (Array.isArray(res)) {
            list = res
          } else if (res && Array.isArray(res.data)) {
            list = res.data
          }
          if (list.length) this.member = list[0]
        }).catch(() => {})
      }
    }
  }
</script>

<style scoped>
  .dn-page {
    min-height: 100vh;
    /* iOS Safari 的 100vh 会包含底部工具栏区域，导致底部按钮被遮挡 */
    min-height: 100dvh;
    display: flex;
    flex-direction: column;
    background: #f0f2f5;
  }
  /* 标准顶栏固定在滚动区域顶部 */
  .dn-page > .mobile-top-bar {
    position: sticky;
    top: 0;
    z-index: 10;
  }
  .dn-body {
    flex: 1;
    padding: 10px;
    /* 给固定底部工具栏留出空间，避免内容被遮挡 */
    padding-bottom: calc(66px + env(safe-area-inset-bottom));
    overflow: hidden;
  }
  .dn-paper {
    background: #fff;
    border-radius: 8px;
    padding: 14px 12px;
  }
  .dn-paper-title {
    text-align: center;
    font-size: 18px;
    font-weight: 600;
    color: #333;
    letter-spacing: 2px;
  }
  .dn-company {
    margin-top: 10px;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
  }
  .dn-company-name {
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }
  .dn-company-line {
    margin-top: 2px;
    font-size: 12px;
    color: #888;
  }
  .dn-meta {
    margin-top: 8px;
  }
  .dn-meta-row {
    display: flex;
    align-items: baseline;
    padding: 3px 0;
    font-size: 13px;
  }
  .dn-meta-label {
    flex: 0 0 44px;
    text-align: right;
    margin-right: 8px;
    color: #999;
  }
  .dn-meta-value {
    flex: 1;
    color: #333;
    word-break: break-all;
  }
  .dn-table {
    margin-top: 10px;
    border-top: 1px solid #ebeef5;
  }
  .dn-table-head,
  .dn-table-row {
    display: flex;
    align-items: flex-start;
    padding: 6px 0;
    border-bottom: 1px solid #f5f5f5;
    font-size: 12px;
  }
  .dn-table-head {
    color: #999;
  }
  .dn-col-name {
    flex: 1 1 auto;
    min-width: 0;
    color: #333;
    word-break: break-all;
  }
  .dn-col-qty {
    flex: 0 0 44px;
    color: #333;
  }
  .dn-col-price {
    flex: 0 0 62px;
    color: #333;
  }
  .dn-col-amount {
    flex: 0 0 70px;
    color: #333;
    font-weight: 600;
  }
  /* 标题行右对齐，数据行左对齐 */
  .dn-table-head .dn-col-qty,
  .dn-table-head .dn-col-price,
  .dn-table-head .dn-col-amount {
    text-align: right;
  }
  .dn-table-row .dn-col-qty,
  .dn-table-row .dn-col-price,
  .dn-table-row .dn-col-amount {
    text-align: left;
  }
  .dn-item-spec {
    display: block;
    font-size: 11px;
    color: #999;
  }
  .dn-item-empty {
    padding: 10px 0;
    text-align: center;
    font-size: 12px;
    color: #999;
  }
  .dn-total-row {
    display: flex;
    justify-content: space-between;
    padding: 6px 0 0;
    font-size: 13px;
  }
  .dn-total-label {
    color: #666;
  }
  .dn-total-value {
    font-weight: 600;
    color: #333;
  }
  .dn-remark {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px solid #f0f0f0;
    font-size: 12px;
    color: #666;
    word-break: break-all;
  }
  .dn-line {
    margin-top: 6px;
    font-size: 12px;
    color: #666;
  }
  .dn-footer {
    margin-top: 16px;
    padding-top: 10px;
    border-top: 1px dashed #e5e5e5;
    font-size: 12px;
    color: #888;
    text-align: right;
    white-space: pre-wrap;
  }
  .dn-empty {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 40px 20px;
  }
  .dn-empty-title {
    font-size: 16px;
    font-weight: 600;
    color: #333;
  }
  .dn-empty-desc {
    margin: 8px 0 20px;
    font-size: 13px;
    color: #999;
    text-align: center;
  }
  /* 新格式自由布局样式 → 已迁到 TemplatePaper.vue */
  .dn-footer-bar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 20;
    box-sizing: border-box;
    height: calc(58px + env(safe-area-inset-bottom));
    padding: 8px 12px calc(8px + env(safe-area-inset-bottom));
    background: #fff;
    border-top: 1px solid #eee;
    box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
  }
  .dn-footer-bar .ant-btn {
    height: 42px;
  }
</style>
