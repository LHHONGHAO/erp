<template>
  <!-- 会员信息打印预览：沿用手机端标准页面（蓝底顶栏 + 内容区 + 底部操作栏） -->
  <mobile-page title="会员信息预览" body-padding="0" @back="handleBack">
    <div class="mpp-body">
      <div v-if="hasRecord" id="memberPrintArea" class="mpp-paper">
        <div class="mpp-head">
          <div class="mpp-name">{{ record.contacts || record.supplier || '-' }}</div>
          <div class="mpp-card-no">会员卡号：{{ record.supplier || '-' }}</div>
        </div>
        <div class="mpp-rows">
          <div class="mpp-row">
            <span class="mpp-label">联系电话</span>
            <span class="mpp-value">{{ record.telephone || '-' }}</span>
          </div>
          <div class="mpp-row">
            <span class="mpp-label">联系地址</span>
            <span class="mpp-value">{{ record.address || '-' }}</span>
          </div>
          <div class="mpp-row">
            <span class="mpp-label">电子邮箱</span>
            <span class="mpp-value">{{ record.email || '-' }}</span>
          </div>
          <div class="mpp-row">
            <span class="mpp-label">微信账户</span>
            <span class="mpp-value">{{ record.wechatAccount || '-' }}</span>
          </div>
          <div class="mpp-row">
            <span class="mpp-label">抖音账户</span>
            <span class="mpp-value">{{ record.douyinAccount || '-' }}</span>
          </div>
          <div class="mpp-row">
            <span class="mpp-label">支付宝账户</span>
            <span class="mpp-value">{{ record.alipayAccount || '-' }}</span>
          </div>
          <div class="mpp-row">
            <span class="mpp-label">其它账户</span>
            <span class="mpp-value">{{ record.otherAccount || '-' }}</span>
          </div>
          <div class="mpp-row">
            <span class="mpp-label">状态</span>
            <span class="mpp-value">{{ record.enabled === false ? '禁用' : '启用' }}</span>
          </div>
          <div class="mpp-row">
            <span class="mpp-label">备注</span>
            <span class="mpp-value">{{ record.description || '-' }}</span>
          </div>
        </div>
        <div class="mpp-time">打印时间：{{ printTime }}</div>
      </div>
      <div v-else class="mpp-empty">暂无会员信息，请从会员列表点击打印进入</div>
    </div>

    <template #footer>
      <a-button type="primary" block size="large" :disabled="!hasRecord" v-print="'#memberPrintArea'">打印</a-button>
    </template>
  </mobile-page>
</template>

<script>
  import MobilePage from '@/components/page/MobilePage'

  export default {
    name: 'MemberPrintPreview',
    components: { MobilePage },
    data () {
      return {
        record: {}
      }
    },
    created () {
      try {
        this.record = JSON.parse(sessionStorage.getItem('jsh.memberPrint.record') || '{}') || {}
      } catch (e) {
        this.record = {}
      }
    },
    computed: {
      hasRecord () {
        return Object.keys(this.record).length > 0
      },
      printTime () {
        const d = new Date()
        const pad = n => (n < 10 ? '0' + n : '' + n)
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) +
          ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes())
      }
    },
    methods: {
      handleBack () {
        this.$router.back()
      }
    }
  }
</script>

<style scoped>
  .mpp-body {
    min-height: 100%;
    padding: 12px;
    box-sizing: border-box;
    background: #f5f6f7;
  }
  .mpp-paper {
    padding: 20px 18px;
    background: #fff;
    border-radius: 12px;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  }
  .mpp-head {
    padding-bottom: 16px;
    border-bottom: 1px solid #f0f0f0;
  }
  .mpp-name {
    font-size: 22px;
    font-weight: 600;
    color: #222;
    word-break: break-all;
  }
  .mpp-card-no {
    margin-top: 8px;
    font-size: 14px;
    color: #666;
  }
  .mpp-rows {
    padding-top: 6px;
  }
  .mpp-row {
    display: flex;
    align-items: flex-start;
    padding: 12px 0;
    border-bottom: 1px solid #f5f5f5;
  }
  .mpp-row:last-child {
    border-bottom: none;
  }
  .mpp-label {
    flex: 0 0 84px;
    width: 84px;
    font-size: 14px;
    color: #999;
  }
  .mpp-value {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    line-height: 1.5;
    color: #333;
    word-break: break-all;
  }
  .mpp-time {
    margin-top: 18px;
    font-size: 12px;
    color: #bbb;
    text-align: right;
  }
  .mpp-empty {
    padding: 60px 0;
    font-size: 14px;
    color: #999;
    text-align: center;
  }
  @media print {
    .mpp-body {
      padding: 0;
      background: #fff;
    }
    .mpp-paper {
      border-radius: 0;
      box-shadow: none;
    }
  }
</style>
