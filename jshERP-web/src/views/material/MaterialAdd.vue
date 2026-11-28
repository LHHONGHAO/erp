<template>
  <!-- 固定高度容器，内容超出时页面内滚动 -->
  <div class="material-add-page" :style="cardStyle">
    <!-- 桌面端页头：操作按钮一行并列，滚动时固定在顶部；手机端由全局蓝底顶栏承载（返回+标题+顶部按钮） -->
    <div class="ma-header" v-if="device !== 'mobile'">
      <a-button icon="left" class="ma-back-btn" @click="handleBack">返回</a-button>
      <div class="ma-actions">
        <a-button icon="setting" @click="handlePropertyConfig">属性设置</a-button>
        <a-button @click="handleReset">重置</a-button>
        <a-button type="primary" @click="handleOk">保存</a-button>
      </div>
    </div>
    <!-- 人工添加 / 编辑商品表单直接铺在页面中 -->
    <div class="ma-body">
      <material-modal ref="form" inline @ok="onSaved" @close="onClose" />
    </div>
  </div>
</template>

<script>
  import MaterialModal from './modules/MaterialModal'
  import { getAction } from '@/api/manage'
  import { mixinDevice } from '@/utils/mixin.js'
  import { setMobileHeaderActions, clearMobileHeaderActions, setMobileHeaderTitle, clearMobileHeaderTitle } from '@/utils/mobilePageHeader'

  export default {
    name: 'MaterialAdd',
    components: {
      MaterialModal
    },
    mixins: [mixinDevice],
    data () {
      return {
        // 容器高度，页面内容超出时出现纵向滚动条
        cardStyle: '',
        // 是否为编辑模式
        isEdit: false
      }
    },
    mounted () {
      this.setHeight()
      window.addEventListener('resize', this.setHeight)
      // 录入页禁止下拉刷新，避免手势触发页面重新加载
      document.body.classList.add('mm-no-pull-refresh')
      this.$nextTick(() => {
        this.initPage()
      })
    },
    // keep-alive 激活时重新读取 sessionStorage 中的编辑数据，避免残留上一次编辑的商品
    activated () {
      document.body.classList.add('mm-no-pull-refresh')
      this.registerHeaderActions()
      this.loadRecordFromStorage()
    },
    deactivated () {
      clearMobileHeaderActions()
      clearMobileHeaderTitle()
      document.body.classList.remove('mm-no-pull-refresh')
    },
    beforeDestroy () {
      window.removeEventListener('resize', this.setHeight)
      clearMobileHeaderActions()
      clearMobileHeaderTitle()
      document.body.classList.remove('mm-no-pull-refresh')
    },
    methods: {
      // 页面初始化：有编辑数据则加载，否则直接进入人工添加表单
      initPage () {
        this.registerHeaderActions()
        this.loadRecordFromStorage()
      },
      // 注册手机端顶部按钮：设置（属性设置）、重置、保存
      registerHeaderActions () {
        setMobileHeaderActions([
          { key: 'setting', icon: 'setting', handler: this.handlePropertyConfig },
          { key: 'reset', icon: 'reload', handler: this.handleReset },
          { key: 'save', text: '保存', type: 'primary', handler: this.handleOk }
        ])
        this.updateHeaderTitle()
      },
      // 顶栏标题：新增为人工添加商品，带数据时为编辑商品
      updateHeaderTitle () {
        setMobileHeaderTitle(this.isEdit ? '编辑商品' : '人工添加商品')
      },
      // 从 sessionStorage 读取商品数据并加载到表单（编辑模式），否则打开新增表单
      loadRecordFromStorage () {
        const recordStr = sessionStorage.getItem('materialEditRecord')
        if (recordStr) {
          try {
            const record = JSON.parse(recordStr)
            sessionStorage.removeItem('materialEditRecord')
            this.isEdit = true
            this.loadFullRecord(record)
          } catch (e) {
            this.openForm()
          }
        } else {
          this.openForm()
        }
      },
      // 按窗口高度计算内容区高度（仅桌面端固定高度；手机端改为页面自然滚动，避免底部内容被裁剪）
      setHeight () {
        if (document.documentElement.clientWidth <= 768) {
          this.cardStyle = ''
          return
        }
        this.cardStyle = 'height:' + (document.documentElement.clientHeight - 100) + 'px'
      },
      // 初始化新增商品表单
      openForm () {
        const form = this.$refs.form
        if (!form) return
        this.isEdit = false
        this.updateHeaderTitle()
        form.action = 'add'
        form.disableSubmit = false
        form.showOkFlag = true
        form.add()
      },
      // 编辑模式：先按 id 拉取商品完整信息（列表行只含部分字段），再加载表单
      loadFullRecord (record) {
        if (!record || !record.id) {
          this.openEditForm(record || {})
          return
        }
        getAction('/material/info', { id: record.id }).then(res => {
          const info = res && res.code === 200 && res.data && res.data.info
          // 以列表行为准（保留 SKU 维度字段），缺失的商品主表字段由完整信息补齐
          this.openEditForm(info ? Object.assign({}, info, record) : record)
        }).catch(() => {
          this.openEditForm(record)
        })
      },
      // 编辑模式：加载商品数据及其所有SKU
      openEditForm (record) {
        const form = this.$refs.form
        if (!form) return
        this.isEdit = true
        this.updateHeaderTitle()
        form.action = 'edit'
        form.disableSubmit = false
        form.showOkFlag = true
        form.edit(record)
        // 商品卡片「编辑」入口：加载完成后直接打开价格设置页（标题显示为编辑商品）
        if (sessionStorage.getItem('materialEditPrice') === '1') {
          sessionStorage.removeItem('materialEditPrice')
          this.$nextTick(() => form.openPriceSheetForEdit())
        }
      },
      // 返回上一页（商品列表）：直接回退历史记录，避免新增一条历史导致列表与编辑页来回循环
      handleBack () {
        sessionStorage.removeItem('materialEditFrom')
        sessionStorage.removeItem('materialEditRecord')
        this.$router.back()
      },
      // 保存
      handleOk () {
        const form = this.$refs.form
        if (!form) return
        form.handleOk()
      },
      // 保存成功：标记列表数据已变更，返回列表时重置瀑布流拉取最新数据
      onSaved () {
        sessionStorage.setItem('materialListDirty', '1')
      },
      // 表单关闭（取消或重置）：编辑模式返回商品列表，新增模式回到空白的人工添加表单
      onClose () {
        if (this.isEdit) {
          this.isEdit = false
          this.handleBack()
          return
        }
        this.$nextTick(() => this.openForm())
      },
      // 打开基础属性设置弹窗
      handlePropertyConfig () {
        const form = this.$refs.form
        if (!form) return
        form.openPropertyConfig()
      },
      // 重置表单（有未保存数据时先确认）
      handleReset () {
        const form = this.$refs.form
        if (!form) return
        form.confirmIfDirty(() => form.close())
      }
    },
    // 离开本页（顶栏返回、手势返回、路由跳转）前，若页面数据会丢失则确认
    beforeRouteLeave (to, from, next) {
      const form = this.$refs.form
      if (form && form.hasUnsavedData()) {
        form.confirmIfDirty(() => next(), () => next(false))
      } else {
        next()
      }
    }
  }
</script>

<style scoped>
  .material-add-page {
    background: #fff;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 0 12px 40px;
  }
  .ma-header {
    display: flex;
    align-items: center;
    justify-content: flex-start;
    padding: 12px 0;
    margin-bottom: 8px;
    border-bottom: 1px solid #e8e8e8;
    background: #fff;
    position: sticky;
    top: 0;
    z-index: 2;
    gap: 10px;
  }
  .ma-back-btn {
    flex-shrink: 0;
  }
  .ma-actions {
    display: flex;
    align-items: center;
    flex-wrap: nowrap;
    gap: 8px;
    margin-left: auto;
  }
  .ma-body {
    padding-bottom: 24px;
  }
  /* 手机端：不限制容器高度，内容随页面整体滚动到底部 */
  @media (max-width: 768px) {
    .material-add-page {
      height: auto !important;
      overflow: visible;
      padding-bottom: calc(96px + env(safe-area-inset-bottom));
    }
  }
</style>

<style>
/* 商品录入页禁止下拉刷新（iOS 16+ / Android），避免手势刷新导致录入数据丢失 */
body.mm-no-pull-refresh {
  overscroll-behavior-y: contain;
}
</style>
