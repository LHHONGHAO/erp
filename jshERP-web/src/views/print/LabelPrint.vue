<template>
  <!-- 手机端：标准搜索页面（顶栏 + 商品流 + 底部搜索栏） -->
  <mobile-search-page
    v-if="isNarrowScreen"
    v-model="keyword"
    title="标签打印"
    placeholder="搜索商品 / 条码"
    :filter-visible.sync="filterDrawerVisible"
    @back="$router.back()"
    @search="onSearch"
    @scan-result="onScanResult"
    @camera="ocrVisible = true"
    @filter-clear="onFilterClear">
    <!-- 商品流：与商品信息页共用标准卡片与分页刷新，点击卡片直接打开标签打印 -->
    <mobile-goods-feed
      ref="feed"
      feed-key="label_print"
      :loader="loadFeed"
      :reset-key="resetKey"
      empty-text="暂无商品，搜索或扫码后点击商品打印标签"
      @item-click="onPrintItem" />

    <!-- 条件筛选：商品类别（统一筛选面板内） -->
    <template #filter-extra>
      <div class="msearch-filter__field">
        <span class="msearch-filter__field-label">商品类别</span>
        <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}" allow-clear
          :treeData="categoryTree" v-model="categoryId" placeholder="请选择类别"
          @change="onCategoryChange">
        </a-tree-select>
      </div>
    </template>

    <template #overlay>
      <!-- 拍照识别：识别出的商品名称作为搜索词 -->
      <ocr-recognize-modal
        :visible="ocrVisible"
        :extractOnly="true"
        @ok="onOcrOk"
        @cancel="ocrVisible = false" />

      <!-- 标签打印预览 + 标签编辑 -->
      <label-print-modal ref="labelPrintModal" @edit="handleEditLabel" @print-success="onPrintSuccess"></label-print-modal>
      <label-edit-modal-page ref="labelEditor" />
    </template>
  </mobile-search-page>

  <!-- 电脑端：打印标签请使用手机 -->
  <a-row v-else :gutter="24">
    <a-col :span="24">
      <a-card :bordered="false">
        <div class="lp-desktop">
          <a-icon type="mobile" class="lp-desktop__icon" />
          <p>标签打印请在手机端使用：扫码或搜索商品后，点击商品即可打印标签。</p>
        </div>
      </a-card>
    </a-col>
  </a-row>
</template>

<script>
  import { getAction } from '@/api/manage'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { getMpListShort } from '@/utils/util'
  import { getSpecValueMerged } from '@/views/material/utils/materialDisplay'
  import { toGoodsFeedItem } from '@/components/mobile/goodsFeedItem'
  import Vue from 'vue'
  import MobileSearchPage from '@/components/mobile/MobileSearchPage'
  import MobileGoodsFeed from '@/components/mobile/MobileGoodsFeed'
  import OcrRecognizeModal from '@/views/material/modules/OcrRecognizeModal'
  import { LabelPrintModal } from '@/views/material/labelPrint'
  import LabelEditModalPage from '@/views/material/modules/LabelEditModalPage'

  /**
   * 手机端标签打印页
   * 搜索/扫码商品 → 点击商品卡片直接打开标签打印弹层（标签模板与打印方式复用既有弹层）
   */
  export default {
    name: 'LabelPrint',
    components: { MobileSearchPage, MobileGoodsFeed, OcrRecognizeModal, LabelPrintModal, LabelEditModalPage },
    data() {
      return {
        isNarrowScreen: window.innerWidth <= 768,
        keyword: '',
        resetKey: 0,
        filterDrawerVisible: false,
        categoryId: undefined,
        categoryTree: [],
        ocrVisible: false,
        // 打印成功后清空页面数据：保持空列表，直到下一次搜索/扫码
        cleared: false
      }
    },
    created() {
      this.loadCategoryTree()
    },
    mounted() {
      window.addEventListener('resize', this.onResize)
    },
    beforeDestroy() {
      window.removeEventListener('resize', this.onResize)
    },
    methods: {
      onResize() {
        this.isNarrowScreen = window.innerWidth <= 768
      },
      /** 标准商品流加载器：按关键字/类别分页查询 SKU */
      loadFeed({ page, pageSize }) {
        // 打印成功后的清空态：不自动回填默认商品，等待下一次搜索或扫码
        if (this.cleared) return Promise.resolve({ list: [], total: 0 })
        const params = {
          search: JSON.stringify({
            materialParam: this.keyword,
            categoryId: this.categoryId,
            mpList: getMpListShort(Vue.ls.get('materialPropertyList'))
          }),
          currentPage: page,
          pageSize: pageSize,
          field: 'id'
        }
        return getAction('/material/listSku', params).then(res => {
          if (res && res.code === 200 && res.data) {
            const rows = Array.isArray(res.data.rows) ? res.data.rows : []
            return { list: rows.map(toGoodsFeedItem), total: Number(res.data.total) || 0 }
          }
          this.$message.error((res && res.data && res.data.message) || '商品加载失败')
          return { list: [], total: 0 }
        })
      },
      /** 打印成功：清空页面数据（关键字、类别筛选与商品列表），便于直接扫描下一件商品 */
      onPrintSuccess() {
        this.cleared = true
        this.keyword = ''
        this.categoryId = undefined
        this.resetKey += 1
      },
      onSearch() {
        this.cleared = false
        this.resetKey += 1
      },
      /** 扫码结果：直接用识别文本作为关键字重新查询 */
      onScanResult(payload) {
        const text = ((payload && payload.text) || '').trim()
        if (!text) return
        this.cleared = false
        this.keyword = text
        this.resetKey += 1
      },
      /** 拍照识别结果：优先使用商品名称作为关键字 */
      onOcrOk(res) {
        this.ocrVisible = false
        const text = ((res && (res.name || res.rawText)) || '').trim()
        if (!text) {
          this.$message.warning('未识别到有效商品信息')
          return
        }
        this.cleared = false
        this.keyword = text
        this.resetKey += 1
      },
      /** 选择商品类别：立即重新查询 */
      onCategoryChange() {
        this.cleared = false
        this.resetKey += 1
      },
      /** 全部关闭：清空筛选条件 */
      onFilterClear() {
        this.cleared = false
        this.categoryId = undefined
        this.filterDrawerVisible = false
        this.resetKey += 1
      },
      /** 点击商品：打开标签打印弹层（SKU 信息与商品信息页打印入口一致） */
      onPrintItem(item) {
        const record = item && item.raw
        if (!record) return
        const sku = {
          specValuesText: getSpecValueMerged(record),
          skuCode: record.skuCode,
          purchaseDecimal: record.purchaseDecimal,
          commodityDecimal: record.commodityDecimal,
          wholesaleDecimal: record.wholesaleDecimal,
          lowDecimal: record.lowDecimal,
          currentStock: record.stock
        }
        this.$refs.labelPrintModal.open(record, sku)
      },
      /** 预览弹层点「编辑」：手机端走标准标签编辑弹层页 */
      handleEditLabel({ record, sku, templateId }) {
        this.$refs.labelEditor.open({
          type: 'label',
          businessType: 'product',
          provider: 'product.label',
          templateId: templateId || null,
          record: record || {},
          sku: sku || {}
        })
      },
      loadCategoryTree() {
        queryMaterialCategoryTreeList({ id: '' }).then(res => {
          this.categoryTree = res || []
        }).catch(() => {
          this.categoryTree = []
        })
      }
    }
  }
</script>

<style scoped>
  .lp-desktop {
    padding: 48px 24px;
    text-align: center;
    color: #8c8c8c;
  }
  .lp-desktop__icon {
    font-size: 40px;
    color: #bfbfbf;
  }
</style>
