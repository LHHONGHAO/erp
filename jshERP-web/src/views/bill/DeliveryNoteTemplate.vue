<template>
  <div class="dnt-page">
    <mobile-top-bar title="发货模板" @back="goBack">
      <template #actions>
        <span class="mtb-action" @click="saveTemplate">保存</span>
      </template>
    </mobile-top-bar>
    <div class="dnt-body">
      <div class="dnt-card">
        <div class="dnt-item">
          <span class="dnt-label">单据标题</span>
          <a-input v-model="form.title" placeholder="请输入单据标题" />
        </div>
        <div class="dnt-item">
          <span class="dnt-label">公司名称</span>
          <a-input v-model="form.companyName" placeholder="请输入公司名称" />
        </div>
        <div class="dnt-item">
          <span class="dnt-label">联系电话</span>
          <a-input v-model="form.companyPhone" placeholder="请输入联系电话" />
        </div>
        <div class="dnt-item">
          <span class="dnt-label">公司地址</span>
          <a-input v-model="form.companyAddress" placeholder="请输入公司地址" />
        </div>
      </div>
      <div class="dnt-card">
        <div class="dnt-switch-row">
          <span class="dnt-label">显示会员信息</span>
          <a-switch v-model="form.showMember" />
        </div>
        <div class="dnt-switch-row">
          <span class="dnt-label">显示备注</span>
          <a-switch v-model="form.showRemark" />
        </div>
        <div class="dnt-switch-row">
          <span class="dnt-label">显示落款</span>
          <a-switch v-model="form.showFooter" />
        </div>
        <div v-if="form.showFooter" class="dnt-item">
          <span class="dnt-label">落款文字</span>
          <a-textarea v-model="form.footerText" :rows="2" placeholder="例如：发货人：张三    日期：____" />
        </div>
      </div>
      <div class="dnt-card">
        <div class="dnt-card-head">
          <span class="dnt-card-title">自定义文字行</span>
          <a-button size="small" type="primary" ghost @click="addLine">添加</a-button>
        </div>
        <div v-for="(line, index) in form.customLines" :key="'line-' + index" class="dnt-line-row">
          <a-input v-model="form.customLines[index]" placeholder="请输入文字内容" />
          <a-icon type="delete" class="dnt-line-delete" @click="removeLine(index)" />
        </div>
        <div v-if="form.customLines.length === 0" class="dnt-line-empty">暂无自定义文字行</div>
      </div>
    </div>
  </div>
</template>

<script>
  import { getAction, postAction } from '@/api/manage'
  import MobileTopBar from '@/components/mobile/MobileTopBar'

  const TEMPLATE_KEY = 'delivery_note_template'

  const DEFAULT_TEMPLATE = {
    title: '发货单',
    companyName: '',
    companyPhone: '',
    companyAddress: '',
    showMember: true,
    showRemark: true,
    showFooter: true,
    footerText: '',
    customLines: []
  }

  export default {
    name: 'DeliveryNoteTemplate',
    components: { MobileTopBar },
    data() {
      return {
        number: '',
        form: Object.assign({}, DEFAULT_TEMPLATE, { customLines: [] }),
        saving: false
      }
    },
    mounted() {
      this.number = this.$route.query.number
      this.loadTemplate()
    },
    methods: {
      goBack() {
        this.$router.back()
      },
      loadTemplate() {
        getAction('/platformConfig/getUiConfig', { key: TEMPLATE_KEY }).then(res => {
          let parsed = null
          if (res && res.code === 200 && res.data) {
            parsed = res.data
            if (typeof parsed === 'string') {
              try {
                parsed = JSON.parse(parsed)
              } catch (e) {
                parsed = null
              }
            }
          }
          if (parsed && typeof parsed === 'object') {
            this.form = Object.assign({}, DEFAULT_TEMPLATE, parsed, {
              customLines: Array.isArray(parsed.customLines) ? parsed.customLines.slice() : []
            })
          }
        }).catch(() => {})
      },
      addLine() {
        this.form.customLines.push('')
      },
      removeLine(index) {
        this.form.customLines.splice(index, 1)
      },
      saveTemplate() {
        if (this.saving) return
        if (!this.form.title) {
          this.$message.warning('请输入单据标题')
          return
        }
        this.saving = true
        const template = Object.assign({}, this.form, {
          customLines: this.form.customLines.filter(line => !!line)
        })
        postAction('/platformConfig/saveUiConfig', {
          key: TEMPLATE_KEY,
          value: JSON.stringify(template)
        }).then(res => {
          if (res && res.code === 200) {
            this.$message.success('保存成功')
            this.$router.back()
          } else {
            this.$message.warning((res && res.data && res.data.message) || '保存失败')
          }
        }).catch(() => {
          this.$message.warning('保存失败')
        }).finally(() => {
          this.saving = false
        })
      }
    }
  }
</script>

<style scoped>
  .dnt-page {
    min-height: 100vh;
    display: flex;
    flex-direction: column;
    background: #f0f2f5;
  }
  /* 标准顶栏固定在滚动区域顶部 */
  .dnt-page > .mobile-top-bar {
    position: sticky;
    top: 0;
    z-index: 10;
  }
  .dnt-body {
    flex: 1;
    padding: 10px;
  }
  .dnt-card {
    margin-bottom: 10px;
    padding: 4px 12px;
    background: #fff;
    border-radius: 8px;
  }
  .dnt-item {
    padding: 8px 0;
    border-bottom: 1px solid #f5f5f5;
  }
  .dnt-item:last-child {
    border-bottom: none;
  }
  .dnt-label {
    display: block;
    margin-bottom: 4px;
    font-size: 12px;
    color: #666;
  }
  .dnt-switch-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 0;
    border-bottom: 1px solid #f5f5f5;
  }
  .dnt-switch-row .dnt-label {
    margin-bottom: 0;
    font-size: 13px;
    color: #333;
  }
  .dnt-switch-row:last-child {
    border-bottom: none;
  }
  .dnt-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 0;
  }
  .dnt-card-title {
    font-size: 13px;
    font-weight: 500;
    color: #333;
  }
  .dnt-line-row {
    display: flex;
    align-items: center;
    padding: 6px 0;
  }
  .dnt-line-delete {
    margin-left: 10px;
    font-size: 16px;
    color: #ff4d4f;
  }
  .dnt-line-empty {
    padding: 10px 0 12px;
    text-align: center;
    font-size: 12px;
    color: #999;
  }
</style>
