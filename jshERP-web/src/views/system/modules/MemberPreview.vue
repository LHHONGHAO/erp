<template>
  <div>
    <!-- 会员信息：标准页面弹层（标题居中 + 右侧关闭） -->
    <mobile-modal-page :visible="visible" title="会员信息" @close="close">
      <div class="mp-print">
        <!-- 会员详细字段（仅保留详情卡片） -->
        <div class="mp-block">
          <div class="mp-block-title">会员详情</div>
          <div class="mp-row" v-for="row in rows" :key="row.label">
            <span class="mp-label">{{ row.label }}</span>
            <span class="mp-value">{{ row.value }}</span>
          </div>
        </div>
      </div>
    </mobile-modal-page>
    <!-- 打印标签弹层：会员卡片「打印」与会员信息页「打印」共用，直接弹出 -->
    <label-print-modal ref="labelPrintModal" />
  </div>
</template>

<script>
  import MobileModalPage from '@/components/mobile/MobileModalPage'
  import LabelPrintModal from '@/views/material/modules/LabelPrintModal'

  export default {
    name: 'MemberPreview',
    components: { MobileModalPage, LabelPrintModal },
    data() {
      return {
        visible: false,
        member: {}
      }
    },
    computed: {
      rows() {
        var m = this.member || {}
        // key 与会员信息表 jsh_supplier 的列名保持一致，打印时按模板字段直接取数
        return [
          { key: 'supplier', label: '会员卡号', value: m.supplier || '--' },
          { key: 'contacts', label: '联系人', value: m.contacts || '--' },
          { key: 'telephone', label: '电话', value: m.telephone || '--' },
          { key: 'phone_num', label: '手机', value: m.phoneNum || '--' },
          { key: 'address', label: '联系地址', value: m.address || '--' },
          { key: 'email', label: '电子邮箱', value: m.email || '--' },
          { key: 'wechat_account', label: '微信账户', value: m.wechatAccount || '--' },
          { key: 'douyin_account', label: '抖音账户', value: m.douyinAccount || '--' },
          { key: 'alipay_account', label: '支付宝账户', value: m.alipayAccount || '--' },
          { key: 'other_account', label: '其它账户', value: m.otherAccount || '--' },
          { key: 'advance_in', label: '预付款', value: this.formatMoney(m.advanceIn) },
          { key: 'enabled', label: '状态', value: m.enabled === false ? '禁用' : '启用' },
          { key: 'description', label: '备注', value: m.description || m.remark || '--' }
        ]
      }
    },
    methods: {
      open(member) {
        this.member = member || {}
        this.visible = true
      },
      close() {
        this.visible = false
      },
      formatMoney(value) {
        var num = Number(value)
        return isNaN(num) ? '0.00' : num.toFixed(2)
      },
      // 会员卡片「打印」：不打开信息预览页，直接弹出打印层
      openPrint(member) {
        this.member = member || {}
        this.buildSnapshot(this.member)
        this.$refs.labelPrintModal.openByType('member')
      },
      // 模板只提供布局，会员数据通过快照带入
      buildSnapshot(m) {
        var values = {}
        this.rows.forEach(function(row) {
          values[row.key] = row.value === '--' ? '' : row.value
        })
        try {
          sessionStorage.setItem('jsh.labelEdit.snapshot', JSON.stringify({
            labelWidth: 320,
            labelHeight: 240,
            selectedAttrs: [],
            attrStyles: {},
            attrLabels: {},
            values: values
          }))
          sessionStorage.setItem('jsh.labelEdit.context', JSON.stringify({
            record: m,
            sku: {},
            template: null,
            provider: 'table.jsh_supplier',
            businessType: 'member'
          }))
        } catch (e) { /* 忽略 */ }
      }
    }
  }
</script>

<style scoped>
  .mp-print {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 10px 12px;
  }
  .mp-block {
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
  }
  .mp-block-title {
    padding: 12px 14px 4px;
    font-size: 15px;
    font-weight: 600;
    color: rgba(0, 0, 0, 0.85);
  }
  .mp-row {
    display: flex;
    align-items: flex-start;
    min-height: 44px;
    padding: 4px 14px;
    border-bottom: 1px solid #f0f0f0;
    box-sizing: border-box;
  }
  .mp-row:last-child {
    border-bottom: none;
  }
  .mp-label {
    flex: 0 0 84px;
    width: 84px;
    padding: 10px 10px 10px 0;
    font-size: 14px;
    color: #666;
    text-align: right;
    box-sizing: border-box;
  }
  .mp-value {
    flex: 1 1 auto;
    min-width: 0;
    padding: 10px 0;
    font-size: 14px;
    line-height: 1.5;
    color: #222;
    word-break: break-all;
  }
</style>
