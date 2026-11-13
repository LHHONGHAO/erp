<template>
  <!-- 复用手机端标准弹层页面：白底顶栏 + 标题居中 + 右侧关闭 -->
  <mobile-modal-page :visible="visible" :title="title" @close="handleClose">
    <div class="mpp-picker">
      <div class="mpp-picker__scroll">
        <!-- 最近使用按次数排在前面，与全部商品逐行展示，已出现过的商品不再重复 -->
        <div v-for="row in displayList" :key="row.key" class="mpp-picker__item"
             @click="onSinglePick(row.goods)">
          <div class="mpp-picker__main">
            <div class="mpp-picker__name">{{ row.goods.name }}</div>
            <div v-if="row.goods.spec" class="mpp-picker__spec">{{ row.goods.spec }}</div>
          </div>
          <span v-if="row.count" class="mpp-picker__count">{{ row.count }} 次</span>
          <span class="mpp-picker__check" @click.stop="toggleSelect(row.goods)">
            <a-checkbox :checked="isSelected(row.goods)" />
          </span>
        </div>

        <div v-if="!loading && displayList.length === 0" class="mpp-picker__empty">暂无商品</div>
        <div v-if="hasMore" class="mpp-picker__more">
          <a-button :loading="loading" @click="loadList(true)">加载更多</a-button>
        </div>
      </div>

      <!-- 筛选抽屉：复用商品信息页的类别与状态筛选 -->
      <a-drawer
        :visible="filterDrawerVisible"
        :zIndex="3000"
        placement="right"
        title="条件筛选"
        width="280"
        @close="filterDrawerVisible = false">
        <div class="mpp-picker__filter-item">
          <span class="mpp-picker__filter-label">商品类别</span>
          <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}" allow-clear
            :treeData="categoryTree" v-model="filterCategoryId" placeholder="请选择类别">
          </a-tree-select>
        </div>
        <div class="mpp-picker__filter-item">
          <span class="mpp-picker__filter-label">状态</span>
          <a-select style="width:100%" placeholder="请选择状态" v-model="filterEnabled">
            <a-select-option value="1">启用</a-select-option>
            <a-select-option value="0">禁用</a-select-option>
          </a-select>
        </div>
        <div class="mpp-picker__filter-footer">
          <a-button block type="primary" @click="applyFilter">确定</a-button>
        </div>
      </a-drawer>

      <!-- AI 图片识别：识别商品信息后直接作为搜索词 -->
      <ocr-recognize-modal
        :visible="ocrVisible"
        :zIndex="3000"
        @ok="onOcrOk"
        @cancel="ocrVisible = false" />
    </div>

    <!-- 底部：多选确认 + 标准搜索底栏（拍照/搜索框/扫码/搜索/筛选） -->
    <template #footer>
      <div class="mpp-picker__footer-area">
        <div v-if="selectedGoods.length > 0" class="mpp-picker__selected-bar">
          <span class="mpp-picker__selected">已选 {{ selectedGoods.length }} 个</span>
          <a-button type="primary" @click="confirmSelect">确定</a-button>
        </div>
        <mobile-bottom-bar :fixed="false" left-auto>
          <mobile-search-bar
            v-model="keyword"
            placeholder="搜索商品名称 / 规格 / 条码"
            @search="handleSearch"
            @camera="ocrVisible = true"
            @filter="filterDrawerVisible = true" />
        </mobile-bottom-bar>
      </div>
    </template>
  </mobile-modal-page>
</template>

<script>
import { getAction } from '@/api/manage'
import { queryMaterialCategoryTreeList } from '@/api/api'
import { getMpListShort, filterObj } from '@/utils/util'
import { getSpecDisplay } from '@/views/material/utils/materialDisplay'
import Vue from 'vue'
import MobileModalPage from './MobileModalPage'
import MobileBottomBar from './MobileBottomBar'
import MobileSearchBar from './MobileSearchBar'
import OcrRecognizeModal from '@/views/material/modules/OcrRecognizeModal'
import { recordGoodsUsage, getRecentGoods } from './goodsUsage'

/**
 * 手机端标准商品选择/添加弹层
 * 1. 与打印弹层同一套标准页面样式（白底顶栏、标题居中、右侧关闭）
 * 2. 近 N 天选择次数前 N 名的商品按次数排在列表前面，与全部商品逐行展示且不重复
 * 3. 单选：点击商品直接回填并关闭；多选：勾选后点「确定」批量回填
 * 4. 底部复用标准搜索底栏：拍照识别 / 搜索框 / 扫码 / 搜索 / 筛选
 */
export default {
  name: 'MobileProductPicker',
  components: { MobileModalPage, MobileBottomBar, MobileSearchBar, OcrRecognizeModal },
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '添加商品' },
    recentDays: { type: Number, default: 7 },
    recentLimit: { type: Number, default: 20 },
    pageSize: { type: Number, default: 20 },
    // 打开时带入的搜索关键字（如扫码识别到的条码）
    initialKeyword: { type: String, default: '' },
    // 最近使用中仅展示包含完整商品数据的记录（业务页面加入购物车需要完整字段）
    recentRequireRaw: { type: Boolean, default: false }
  },
  data() {
    return {
      keyword: '',
      list: [],
      loading: false,
      page: 1,
      total: 0,
      recentList: [],
      selectedGoods: [],
      // 筛选
      filterDrawerVisible: false,
      filterCategoryId: undefined,
      filterEnabled: undefined,
      categoryTree: [],
      categoryLoaded: false,
      // 拍照识别
      ocrVisible: false
    }
  },
  computed: {
    hasMore() {
      return this.list.length < this.total
    },
    /** 最近使用置顶，其余商品按接口顺序；同一商品只出现一次 */
    displayList() {
      var recent = (!this.keyword && !this.filterActive) ? this.recentList : []
      var recentKeys = {}
      var legacyNames = {}
      var rows = recent.map(function(g) {
        var goods = this.normalizeGoods(g)
        if (g.raw) {
          goods.key = this.goodsKey(g.raw)
        } else {
          // 旧记录没有原始商品数据，用名称+规格兜底去重
          legacyNames[goods.name + '\u0000' + (goods.spec || '')] = true
        }
        recentKeys[goods.key] = true
        return {
          key: 'recent-' + goods.key,
          goods: goods,
          count: g.count || 0
        }
      }, this)
      this.list.forEach(function(item) {
        var goods = this.formatGoods(item)
        if (recentKeys[goods.key]) return
        if (legacyNames[goods.name + '\u0000' + (goods.spec || '')]) return
        rows.push({ key: 'item-' + goods.key, goods: goods, count: 0 })
      }, this)
      return rows
    },
    filterActive() {
      return !!this.filterCategoryId || this.filterEnabled === '0' || this.filterEnabled === '1'
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.keyword = this.initialKeyword || ''
        this.selectedGoods = []
        this.recentList = getRecentGoods({
          days: this.recentDays,
          limit: this.recentLimit,
          requireRaw: this.recentRequireRaw
        })
        if (!this.categoryLoaded) this.loadTreeData()
        this.loadList()
      }
    }
  },
  methods: {
    handleClose() {
      this.$emit('close')
    },
    goodsKey(item) {
      var id = (item.id === 0 || item.id) ? item.id : item.name
      return String(id) + '_' + (item.meId || 0)
    },
    specOf(item) {
      try {
        return getSpecDisplay(item) || ''
      } catch (e) {
        return ''
      }
    },
    /** 规格展示文本拆成最多 3 个规格值：去掉「规格名：」，逐行取值 */
    splitSpecValues(spec) {
      var values = []
      String(spec || '').split('\n').forEach(function(line) {
        var text = line.trim()
        if (!text) return
        var idx = text.search(/[：:]/)
        if (idx > -1) text = text.slice(idx + 1).trim()
        if (text) values.push(text)
      })
      return values.slice(0, 3)
    },
    formatGoods(item) {
      var spec = this.specOf(item)
      var values = this.splitSpecValues(spec)
      return {
        key: this.goodsKey(item),
        name: item.name || '',
        spec: spec,
        spec1: values[0] || '',
        spec2: values[1] || '',
        spec3: values[2] || '',
        // 保留原始商品数据，供业务页面（如销售开单）加入购物车使用
        raw: item
      }
    },
    /** 最近使用只存了名称+规格展示文本，回填前补齐规格值 */
    normalizeGoods(goods) {
      var values = [goods.spec1, goods.spec2, goods.spec3]
      if (!values[0] && !values[1] && !values[2] && goods.spec) {
        values = this.splitSpecValues(goods.spec)
      }
      return {
        key: goods.key || (goods.name + '_' + (goods.spec || '')),
        name: goods.name || '',
        spec: goods.spec || '',
        spec1: values[0] || '',
        spec2: values[1] || '',
        spec3: values[2] || '',
        raw: goods.raw || null
      }
    },
    /** 单选：点击商品直接回填（由父组件关闭弹层） */
    onSinglePick(goods) {
      if (!goods || !goods.name) return
      var item = this.normalizeGoods(goods)
      recordGoodsUsage(item)
      this.$emit('pick', item)
    },
    isSelected(goods) {
      var key = goods && goods.key
      return this.selectedGoods.some(function(g) { return g.key === key })
    },
    toggleSelect(goods) {
      if (!goods || !goods.name) return
      var key = goods.key
      var idx = -1
      for (var i = 0; i < this.selectedGoods.length; i++) {
        if (this.selectedGoods[i].key === key) { idx = i; break }
      }
      if (idx > -1) {
        this.selectedGoods.splice(idx, 1)
      } else {
        this.selectedGoods.push(this.normalizeGoods(goods))
      }
    },
    /** 多选：手动点「确定」后批量回填 */
    confirmSelect() {
      if (this.selectedGoods.length === 0) return
      var list = this.selectedGoods.slice()
      list.forEach(function(goods) { recordGoodsUsage(goods) })
      this.$emit('confirm', list)
    },
    /** 底栏搜索：重置分页并按关键字查询 */
    handleSearch(text) {
      if (typeof text === 'string') this.keyword = text
      this.loadList()
    },
    /** 加载商品类别树（筛选使用） */
    loadTreeData() {
      var self = this
      queryMaterialCategoryTreeList({ id: '' }).then(function(res) {
        self.categoryTree = res || []
        self.categoryLoaded = true
      }).catch(function() {
        self.categoryLoaded = true
      })
    },
    /** 筛选确定：应用类别/状态后重新查询 */
    applyFilter() {
      this.filterDrawerVisible = false
      this.loadList()
    },
    /** AI 拍照识别完成：优先使用识别出的商品名称作为搜索词 */
    onOcrOk(res) {
      this.ocrVisible = false
      var text = ((res && (res.name || res.rawText)) || '').trim()
      if (!text) {
        this.$message.warning('未识别到有效商品信息')
        return
      }
      this.keyword = text
      this.loadList()
    },
    /** 商品数据加载逻辑与原商品选择弹层保持一致 */
    loadList(more) {
      var self = this
      if (more && (this.loading || this.list.length >= this.total)) return
      this.loading = true
      if (!more) {
        this.page = 1
        this.list = []
      }
      var searchObj = {
        materialParam: this.keyword,
        categoryId: this.filterCategoryId,
        enabled: this.filterEnabled,
        mpList: getMpListShort(Vue.ls.get('materialPropertyList'))
      }
      getAction('/material/listSku', {
        search: JSON.stringify(filterObj(searchObj)),
        currentPage: this.page,
        pageSize: this.pageSize
      }).then(function(res) {
        if (res && res.code === 200) {
          var rows = (res.data && res.data.rows) || []
          self.list = more ? self.list.concat(rows) : rows
          self.total = (res.data && res.data.total) || self.list.length
          self.page++
        } else {
          self.$message.error((res && res.data && res.data.message) || '商品加载失败')
        }
      }).catch(function() {
        self.$message.error('商品加载失败')
      }).finally(function() {
        self.loading = false
      })
    }
  }
}
</script>

<style scoped>
.mpp-picker {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.mpp-picker__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.mpp-picker__item {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
}
.mpp-picker__main {
  flex: 1;
  min-width: 0;
}
.mpp-picker__name {
  font-size: 15px;
  color: #333;
}
.mpp-picker__spec {
  margin-top: 2px;
  font-size: 12px;
  color: #999;
}
.mpp-picker__count {
  flex-shrink: 0;
  margin-left: 8px;
  font-size: 12px;
  color: #1890ff;
}
.mpp-picker__check {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-left: 10px;
  padding: 4px;
}
.mpp-picker__empty {
  padding: 30px 16px;
  text-align: center;
  font-size: 13px;
  color: #999;
}
.mpp-picker__more {
  padding: 12px;
  text-align: center;
}
.mpp-picker__footer-area {
  flex: none;
  background: #fff;
}
.mpp-picker__selected-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-top: 1px solid #f0f0f0;
}
.mpp-picker__selected {
  font-size: 14px;
  color: #595959;
}
/* 筛选抽屉 */
.mpp-picker__filter-item {
  margin-bottom: 18px;
}
.mpp-picker__filter-label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: #666;
}
.mpp-picker__filter-footer {
  margin-top: 24px;
}
</style>
