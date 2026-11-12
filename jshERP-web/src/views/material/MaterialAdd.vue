<template>
  <!-- 固定高度容器，内容超出时页面内滚动 -->
  <div class="material-add-page" :style="cardStyle">
    <!-- 桌面端页头：操作按钮一行并列，滚动时固定在顶部；手机端由全局蓝底顶栏承载（返回+标题+顶部按钮） -->
    <div class="ma-header" v-if="device !== 'mobile'">
      <a-button icon="left" class="ma-back-btn" @click="handleBack">返回</a-button>
      <div class="ma-actions" v-if="!showCategoryGate">
        <a-button icon="setting" @click="handlePropertyConfig">属性设置</a-button>
        <a-button @click="handleReset">重置</a-button>
        <a-button type="primary" @click="handleOk">保存</a-button>
      </div>
    </div>
    <!-- 表单直接铺在页面中，非弹窗 -->
    <div class="ma-body">
      <!-- 新增商品第一步：页面只显示类别选择，选好类别后再显示商品录入表单 -->
      <div v-if="showCategoryGate" class="ma-gate">
        <div class="ma-gate__title">新增商品</div>
        <div class="ma-gate__desc">请先选择商品类别，系统将按该类别加载规格与属性</div>
        <a-tree-select
          v-model="gateCategoryId"
          class="ma-gate__select"
          :dropdownStyle="{ maxHeight: '320px', overflow: 'auto' }"
          :treeData="gateTreeData"
          :treeDefaultExpandAll="true"
          allow-clear
          placeholder="请选择商品类别（须选二级类目，一级类目不可选）"
          @change="handleGateCategoryChange" />
        <a-button class="ma-gate__add" type="dashed" icon="plus" block @click="handleAddCategory">添加类目</a-button>
      </div>
      <material-modal v-else ref="form" inline @close="onClose" @ok="onSaved" />
    </div>
    <!-- 类别新增弹层：手机端为全屏蓝底标准弹层，桌面端为居中弹窗 -->
    <material-category-modal ref="categoryModal" @ok="handleCategoryAdded" />
  </div>
</template>

<script>
  import MaterialModal from './modules/MaterialModal'
  import MaterialCategoryModal from './modules/MaterialCategoryModal'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { mixinDevice } from '@/utils/mixin.js'
  import { setMobileHeaderActions, clearMobileHeaderActions, setMobileHeaderTitle, clearMobileHeaderTitle } from '@/utils/mobilePageHeader'

  export default {
    name: 'MaterialAdd',
    components: {
      MaterialModal,
      MaterialCategoryModal
    },
    mixins: [mixinDevice],
    data () {
      return {
        // 容器高度，页面内容超出时出现纵向滚动条
        cardStyle: '',
        // 是否为编辑模式
        isEdit: false,
        // 新增商品时先显示类别选择（编辑商品直接显示表单）
        showCategoryGate: true,
        gateCategoryId: undefined,
        gateTreeData: []
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
      // 仍停留在类别选择页时不重置，保留用户的选择
      if (this.showCategoryGate) {
        return
      }
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
      // 页面初始化：编辑商品直接进表单；新增商品先显示类别选择
      initPage () {
        const recordStr = sessionStorage.getItem('materialEditRecord')
        if (!recordStr) {
          this.showCategoryGate = true
          this.loadGateTree()
          this.registerHeaderActions()
          return
        }
        this.showCategoryGate = false
        this.registerHeaderActions()
        this.loadRecordFromStorage()
      },
      // 加载类别树（一级类目不可选，与表单内的类别选择保持一致）
      loadGateTree () {
        queryMaterialCategoryTreeList({ id: '' }).then(res => {
          this.gateTreeData = this.disableLevelOneNodes(res || [])
        }).catch(() => {
          this.gateTreeData = []
        })
      },
      disableLevelOneNodes (nodes) {
        return (nodes || []).map(node => {
          const item = Object.assign({}, node)
          if (item.categoryLevel === 1) {
            item.disabled = true
          }
          if (item.children && item.children.length > 0) {
            item.children = this.disableLevelOneNodes(item.children)
          }
          return item
        })
      },
      // 打开类别新增弹层（手机端为标准全屏弹层，桌面端为居中弹窗）
      handleAddCategory () {
        const modal = this.$refs.categoryModal
        if (!modal) return
        modal.title = '新增品类'
        modal.add()
      },
      // 类别新增成功后刷新类别选择树
      handleCategoryAdded () {
        this.loadGateTree()
      },
      // 选择类别后进入商品录入表单，并把已选类别带入表单
      handleGateCategoryChange (value) {
        if (!value) {
          return
        }
        const categoryId = value
        this.showCategoryGate = false
        this.registerHeaderActions()
        this.$nextTick(() => {
          this.openForm()
          const form = this.$refs.form
          if (form && form.applyPresetCategory) {
            form.applyPresetCategory(categoryId)
          }
        })
      },
      // 注册手机端顶部按钮：设置（属性设置）、重置、保存（类别选择阶段不显示）
      registerHeaderActions () {
        if (this.showCategoryGate) {
          setMobileHeaderActions([])
          this.updateHeaderTitle()
          return
        }
        setMobileHeaderActions([
          { key: 'setting', icon: 'setting', handler: this.handlePropertyConfig },
          { key: 'reset', icon: 'reload', handler: this.handleReset },
          { key: 'save', text: '保存', type: 'primary', handler: this.handleOk }
        ])
        this.updateHeaderTitle()
      },
      // 顶栏标题按当前模式显示（新增与编辑共用本页面）
      updateHeaderTitle () {
        setMobileHeaderTitle(this.isEdit ? '编辑商品' : '新增商品')
      },
      // 从 sessionStorage 读取商品数据并加载到表单（编辑模式），否则打开新增表单
      loadRecordFromStorage () {
        const recordStr = sessionStorage.getItem('materialEditRecord')
        if (recordStr) {
          try {
            const record = JSON.parse(recordStr)
            sessionStorage.removeItem('materialEditRecord')
            this.isEdit = true
            this.openEditForm(record)
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
      // 保存成功后给出提示并重置为空白表单，便于连续录入
      onSaved () {
        this.$message.success('保存成功')
      },
      // 表单关闭（取消或保存成功后）回到类别选择，便于按类别连续录入
      onClose () {
        this.returnToCategoryGate()
      },
      // 回到类别选择步骤
      returnToCategoryGate () {
        this.gateCategoryId = undefined
        this.isEdit = false
        this.showCategoryGate = true
        this.registerHeaderActions()
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
  /* 类别选择步骤：页面只展示一个类别选择框 */
  .ma-gate {
    max-width: 480px;
    margin: 0 auto;
    padding: 48px 8px 0;
    text-align: center;
  }
  .ma-gate__title {
    color: #1f2329;
    font-size: 20px;
    font-weight: 600;
  }
  .ma-gate__desc {
    margin: 10px 0 24px;
    color: #8b949e;
    font-size: 13px;
    line-height: 1.6;
  }
  .ma-gate__select {
    width: 100%;
    text-align: left;
  }
  .ma-gate__add {
    margin-top: 12px;
    height: 40px;
  }
  /* 手机端：不限制容器高度，内容随页面整体滚动到底部 */
  @media (max-width: 768px) {
    .material-add-page {
      height: auto !important;
      overflow: visible;
      padding-bottom: calc(96px + env(safe-area-inset-bottom));
    }
    .ma-gate {
      padding-top: 32px;
    }
  }
</style>

<style>
/* 商品录入页禁止下拉刷新（iOS 16+ / Android），避免手势刷新导致录入数据丢失 */
body.mm-no-pull-refresh {
  overscroll-behavior-y: contain;
}
</style>
