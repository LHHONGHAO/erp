<template>
  <!-- 复用手机端标准弹层页面：蓝底顶栏 + 标题左对齐 + 副标题同行 + 右侧关闭 -->
  <mobile-modal-page :visible="visible" :title="title" @close="$emit('close')">
    <div class="mpp-picker">
      <!-- 全部商品：标准商品流模块（卡片布局 + 虚拟滚动 + 分页刷新），已选商品排在最前并随列表一起滚动 -->
      <div class="mpp-picker__feed">
        <mobile-goods-feed
          ref="feed"
          feed-key="goods_picker"
          :loader="loadPickerFeed"
          :reset-key="pickerResetKey"
          :prepend-items="prependItems"
          selectable
          :selected-keys="selectedKeys"
          @item-click="onFeedItemClick"
          @select="onFeedItemClick">
        </mobile-goods-feed>
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

    <!-- 底部：已选确认 + 标准搜索底栏（拍照/搜索框/扫码/筛选） -->
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
import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'
import { toGoodsFeedItem } from '@/components/mobile/goodsFeedItem'
import Vue from 'vue'
import MobileModalPage from './MobileModalPage'
import MobileBottomBar from './MobileBottomBar'
import MobileSearchBar from './MobileSearchBar'
import MobileGoodsFeed from './MobileGoodsFeed'
import OcrRecognizeModal from '@/views/material/modules/OcrRecognizeModal'
import { recordGoodsUsage, getRecentGoods } from './goodsUsage'

/**
 * 手机端标准商品选择/添加弹层
 * 1. 与打印弹层同一套标准页面样式（蓝底顶栏、标题左对齐、副标题同行、右侧关闭）
 * 2. 已选商品按点击顺序排到商品卡片列表最前面，与列表一起滚动，不做固定置顶区
 * 3. 全部商品由标准商品流模块渲染（卡片布局 + 虚拟滚动 + 分页刷新）
 * 4. 近 N 天选择次数前 N 名的商品并入第 1 页，与全部商品逐行展示且不重复
 * 5. 点击商品多选：选中即排到最前，再次点击取消并回到原位置，点「确定」批量回填
 * 6. 底部复用标准搜索底栏：拍照识别 / 搜索框 / 扫码 / 搜索 / 筛选
 */
export default {
  name: 'MobileProductPicker',
  components: { MobileModalPage, MobileBottomBar, MobileSearchBar, MobileGoodsFeed, OcrRecognizeModal },
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '添加商品' },
    recentDays: { type: Number, default: 7 },
    recentLimit: { type: Number, default: 20 },
    pageSize: { type: Number, default: 20 },
    // 打开时带入的搜索关键字（如扫码识别到的条码）
    initialKeyword: { type: String, default: '' },
    // 最近使用中仅展示包含完整商品数据的记录（业务页面加入购物车需要完整字段）
    recentRequireRaw: { type: Boolean, default: false },
    // 打开时带入的已选商品（按传入顺序置顶展示，再次点击可取消）
    initialSelected: { type: Array, default: () => [] }
  },
  data() {
    return {
      keyword: '',
      recentList: [],
      // 已选商品（按点击顺序）
      selectedGoods: [],
      // 商品流条件重置信号
      pickerResetKey: 0,
      // 本次加载已出现过的商品键（最近使用与分页去重）
      seenKeys: {},
      legacyNames: {},
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
    /** 已选商品的唯一键：用于商品流右上角勾选状态 */
    selectedKeys() {
      return this.selectedGoods.map(function(goods) { return goods.key })
    },
    /** 已选商品按点击顺序排到商品流最前面（与列表同一套卡片，随列表一起滚动） */
    prependItems() {
      return this.selectedGoods.map(function(goods) {
        return {
          skuRowKey: goods.key,
          name: goods.name,
          imgUrl: goods.imgUrl,
          specs: goods.specLines,
          items: this.cardItems(goods),
          goods: goods
        }
      }, this)
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.keyword = this.initialKeyword || ''
        // 带入业务页面已选商品，打开即置顶展示
        this.selectedGoods = (this.initialSelected || []).map(function(goods) {
          return this.normalizeGoods(goods)
        }, this)
        this.recentList = getRecentGoods({
          days: this.recentDays,
          limit: this.recentLimit,
          requireRaw: this.recentRequireRaw
        })
        if (!this.categoryLoaded) this.loadTreeData()
        // 每次打开都从第 1 页开始，并清掉上次的滚动位置
        if (this.$store && this.$store.commit) {
          this.$store.commit('goodsFeed/CLEAR_FEED_STATE', 'goods_picker')
        }
        this.resetFeed()
      }
    }
  },
  methods: {
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
    /**
     * 规格展示文本 → 每个规格属性一行，值只取第一个（多值以 / 分隔）
     * 例：「花色：A/B　规格：200x230cm/245x250cm」→ 花色：A、规格：200x230cm
     */
    buildSpecLines(spec) {
      var lines = []
      String(spec || '').split('\n').forEach(function(line) {
        var text = line.trim()
        if (!text) return
        var label = ''
        var value = text
        var idx = text.search(/[：:]/)
        if (idx > -1) {
          label = text.slice(0, idx).trim()
          value = text.slice(idx + 1).trim()
        }
        value = value.split('/')[0].trim()
        if (!value) return
        lines.push({ label: label, value: value })
      })
      return lines
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
        imgUrl: getRowImgUrl(item),
        specLines: this.buildSpecLines(spec).slice(0, 2),
        // 标准卡片信息行：零售价 / 数量（取库存）
        price: item.commodityDecimal == null ? '-' : item.commodityDecimal,
        stock: item.stock == null ? '-' : item.stock,
        // 保留原始商品数据，供业务页面（如销售开单）加入购物车使用
        raw: item
      }
    },
    /** 标准卡片信息行：零售价 在前、库存 在后 */
    cardItems(goods) {
      return [
        { label: '零售价', value: goods.price },
        { label: '库存', value: goods.stock }
      ]
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
        imgUrl: goods.imgUrl || (goods.raw ? getRowImgUrl(goods.raw) : ''),
        specLines: this.buildSpecLines(goods.spec).slice(0, 2),
        price: (goods.raw && goods.raw.commodityDecimal != null) ? goods.raw.commodityDecimal : '-',
        stock: (goods.raw && goods.raw.stock != null) ? goods.raw.stock : '-',
        raw: goods.raw || null
      }
    },
    /** 点击商品：选中则加入已选（置顶），再次点击取消 */
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
    /** 商品流卡片点击：全部商品与已选置顶共用同一套切换逻辑 */
    onFeedItemClick(item) {
      if (!item) return
      this.toggleSelect(item.goods ? item.goods : this.formatGoods(item.raw))
    },
    /** 确定：按已选顺序批量回填（由父组件关闭弹层） */
    confirmSelect() {
      if (this.selectedGoods.length === 0) return
      var list = this.selectedGoods.slice()
      list.forEach(function(goods) { recordGoodsUsage(goods) })
      this.$emit('confirm', list)
    },
    /** 底栏搜索：重置商品流并按关键字查询 */
    handleSearch(text) {
      if (typeof text === 'string') this.keyword = text
      this.resetFeed()
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
      this.resetFeed()
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
      this.resetFeed()
    },
    /** 条件变化：商品流回到顶部重新加载第 1 页 */
    resetFeed() {
      this.pickerResetKey += 1
    },
    filterActive() {
      return !!this.filterCategoryId || this.filterEnabled === '0' || this.filterEnabled === '1'
    },
    /** 最近使用 → 商品流卡片数据（仅在无搜索、无筛选时并入首页） */
    buildRecentFeedItems() {
      this.legacyNames = {}
      if (this.keyword || this.filterActive()) return []
      var rows = []
      this.recentList.forEach(function(goods) {
        var g = this.normalizeGoods(goods)
        if (!g.name) return
        // 旧记录没有原始商品数据，用名称+规格兜底去重
        if (!g.raw) this.legacyNames[g.name + '\u0000' + (g.spec || '')] = true
        rows.push({
          skuRowKey: g.key,
          // 名称+规格用于和接口最新商品做去重：最新数据优先，避免沿用旧 SKU 编码
          nameKey: g.name + '\u0000' + (g.spec || ''),
          name: g.name,
          imgUrl: g.imgUrl,
          specs: g.specLines,
          items: this.cardItems(g),
          goods: g
        })
      }, this)
      return rows
    },
    /** 标准商品流加载器：第 1 页并入最近使用，其余按接口分页 */
    loadPickerFeed({ page, pageSize }) {
      var self = this
      if (page === 1) this.seenKeys = {}
      var recentRows = page === 1 ? this.buildRecentFeedItems() : []
      var searchObj = {
        materialParam: this.keyword,
        categoryId: this.filterCategoryId,
        enabled: this.filterEnabled,
        mpList: getMpListShort(Vue.ls.get('materialPropertyList'))
      }
      return getAction('/material/listSku', {
        search: JSON.stringify(filterObj(searchObj)),
        currentPage: page,
        pageSize: pageSize
      }).then(function(res) {
        if (!(res && res.code === 200)) {
          self.$message.error((res && res.data && res.data.message) || '商品加载失败')
          return { list: [], total: 0 }
        }
        var rows = (res.data && res.data.rows) || []
        var total = Number(res.data && res.data.total) || 0
        var list = []
        // 接口返回的最新商品优先：同名称+规格的最近使用记录不再重复展示
        var freshNameKeys = {}
        rows.forEach(function(row) {
          var item = toGoodsFeedItem(row)
          freshNameKeys[item.name + '\u0000' + (self.specOf(row) || '')] = true
          if (self.seenKeys[item.skuRowKey]) return
          var legacy = item.name + '\u0000' + (self.specOf(row) || '')
          if (self.legacyNames[legacy]) return
          self.seenKeys[item.skuRowKey] = true
          list.push(item)
        })
        var recentCount = 0
        var head = []
        recentRows.forEach(function(item) {
          if (item.nameKey && freshNameKeys[item.nameKey]) return
          if (self.seenKeys[item.skuRowKey]) return
          self.seenKeys[item.skuRowKey] = true
          head.push(item)
          recentCount++
        })
        return { list: head.concat(list), total: total + recentCount }
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
  box-sizing: border-box;
  padding: 8px 12px 0;
  background: #f5f7fa;
}
/* 全部商品区：标准商品流模块自带滚动（已选商品排在最前，随列表滚动） */
.mpp-picker__feed {
  flex: 1;
  min-height: 0;
}
/* 底部已选确认条 */
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
.mpp-picker__footer-area {
  flex: none;
  background: #fff;
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
