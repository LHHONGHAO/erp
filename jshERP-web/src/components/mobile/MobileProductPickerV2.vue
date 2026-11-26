<template>
  <!-- 新版手机端添加商品弹层：左侧二级类目导航 + 商品卡片点选加数量（复用标准弹层骨架与商品流模块） -->
  <mobile-modal-page class="picker-page-white" :style="{ background: '#fff' }" :visible="visible" :title="title" subtitle="V1126G" @close="$emit('close')">
    <mobile-search-layout
      v-model="keyword"
      :show-filter="false"
      placeholder="搜索商品名称 / 规格 / 条码"
      @search="handleSearch"
      @camera="ocrVisible = true">
      <!-- 左侧二级类目导航 + 商品流：点卡片数量 +1，卡片右下角减号/加号步进器（点击 -1/+1，减到 0 步进器消失） -->
      <div class="pkv2-body">
        <div v-if="navCategories.length" ref="navBox" class="pkv2-nav">
          <!-- 最近常选：默认展示所有商品（顶部并入近一周选择次数最高的商品），滚动到类目商品时导航联动高亮 -->
          <div
            ref="navItemAll"
            class="pkv2-nav__item"
            :class="{ 'is-on': filterCategoryId === undefined && !navSpyId }"
            @click="selectNavAll">最近常选</div>
          <div
            v-for="cat in navCategories"
            :key="cat.id"
            :ref="'navItem' + cat.id"
            class="pkv2-nav__item"
            :class="{ 'is-on': isNavActive(cat) }"
            @click="selectNavCategory(cat)">{{ cat.title }}</div>
          <!-- 常用规格卡：当前类目（含子类目）下最常用的 4 个规格值，点击滚动到该规格商品处（不做过滤） -->
          <div v-if="favSpecs.length" class="pkv2-fav">
            <div class="pkv2-fav__title">常用规格</div>
            <div
              v-for="tag in favSpecs"
              :key="tag.text"
              class="pkv2-fav__tag"
              :class="{ 'is-on': activeSpec === tag.text }"
              @click="scrollToSpec(tag)">{{ tag.text }}</div>
          </div>
        </div>
        <div class="pkv2-feed">
          <mobile-goods-feed
            ref="feed"
            feed-key="goods_picker_v2"
            :loader="loadPickerFeed"
            :gap="16"
            :reset-key="pickerResetKey"
            qty-mode
            :qty-map="qtyMap"
            @item-click="onFeedItemClick"
            @minus="onFeedMinus"
            @plus="onFeedPlus"
            @first-visible="onFeedFirstVisible">
          </mobile-goods-feed>
        </div>
      </div>

      <!-- 已选合计件数：常驻显示在搜索胶囊右侧；点击按当前已选数量直接同步回结算单 -->
      <template #float-extra>
        <div class="msearch-entry" :class="{ 'is-active': totalQty > 0 }" @click="confirmSelect">
          <span>已选</span>
          <span class="msearch-entry__badge">{{ totalQty }}</span>
        </div>
      </template>
    </mobile-search-layout>

    <!-- AI 图片识别：识别商品信息后直接作为搜索词 -->
    <ocr-recognize-modal
      :visible="ocrVisible"
      :zIndex="3000"
      @ok="onOcrOk"
      @cancel="ocrVisible = false" />
  </mobile-modal-page>
</template>

<script>
import { getAction } from '@/api/manage'
import { queryMaterialCategoryTreeList } from '@/api/api'
import { getMpListShort, filterObj } from '@/utils/util'
import { getRowImgUrl } from '@/views/material/utils/materialDisplay'
import { toGoodsFeedItem, splitSpecValues, parseSpecLines } from '@/components/mobile/goodsFeedItem'
import Vue from 'vue'
import MobileModalPage from './MobileModalPage'
import MobileSearchLayout from './MobileSearchLayout'
import MobileGoodsFeed from './MobileGoodsFeed'
import OcrRecognizeModal from '@/views/material/modules/OcrRecognizeModal'
import { recordGoodsUsage, getRecentGoods, fetchRecentGoods, clearLocalGoodsUsage } from './goodsUsage'

/**
 * 标准商品选择/添加弹层（唯一复用模块）：所有需要「添加商品」的页面统一复用本组件，
 * 选择、数量、最近常选、类目导航、搜索/扫码关键字/AI 拍照识别等业务逻辑全部内聚于此。
 *
 * 集成契约（页面只需三步）：
 *   <mobile-product-picker-v2
 *     :visible="pickerVisible"          // 打开/关闭（关闭由页面在 @close/@confirm 里置 false）
 *     :initial-keyword="keyword"        // 可选：打开时带入搜索词（如外部扫码识别到的条码）
 *     :initial-selected="selectedGoods" // 可选：打开时带入已选商品（[{ key,name,spec,spec1..3,imgUrl,qty,raw }]）
 *     @confirm="onPickerConfirm"        // 确认：回传当前已选全集 [{ ...goods, qty }]，页面自行同步到单据/列表
 *     @close="pickerVisible = false" />
 * 已选状态（selectedGoods + qtyMap）与最近使用记录（goodsUsage，跨页面共享）均由本模块维护，
 * @confirm 是唯一回填出口：返回完整已选列表，页面按自身业务做全量同步或追加。
 *
 * 交互能力：
 * 1. 左侧二级类目导航 + 最近常选（近一周选择最多的 20 个商品置顶，其余按类目+规格排序，滚动联动导航高亮）
 * 2. 点击卡片任意位置数量 +1；数量不为 0 时右下角显示减号/加号步进器；底部「已选 N」胶囊点击确认回填
 * 3. 左栏底部常用规格卡：点击滚动定位到该规格商品组（锚点模式，不过滤），滚动时当前规格浅蓝高亮
 */
export default {
  name: 'MobileProductPickerV2',
  components: { MobileModalPage, MobileSearchLayout, MobileGoodsFeed, OcrRecognizeModal },
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '添加商品' },
    recentDays: { type: Number, default: 7 },
    recentLimit: { type: Number, default: 20 },
    pageSize: { type: Number, default: 20 },
    // 打开时带入的搜索关键字（如扫码识别到的条码）
    initialKeyword: { type: String, default: '' },
    // 最近使用中仅展示包含完整商品数据的记录（回填单据/标签需要完整字段，标准场景均要求完整）
    recentRequireRaw: { type: Boolean, default: true },
    // 打开时带入的已选商品（goods 可携带 qty 数量，按传入顺序展示）
    initialSelected: { type: Array, default: () => [] }
  },
  data() {
    return {
      keyword: '',
      recentList: [],
      // 已选商品（按首次点击顺序，数量存于 qtyMap）
      selectedGoods: [],
      // 各商品已选数量（key → 数量）
      qtyMap: {},
      // 商品流条件重置信号
      pickerResetKey: 0,
      // 本次加载已出现过的商品键（最近使用与分页去重）
      seenKeys: {},
      // 最近常选商品的「名称+规格」键：最新列表中同键商品不重复展示（全部页次生效）
      recentNameKeys: {},
      // 左侧类目导航：当前激活的二级类目 id（undefined = 全部商品）
      filterCategoryId: undefined,
      // 常用规格卡：当前类目下最常用的 4 个规格值（点击为锚点滚动定位，不做过滤）
      favSpecs: [],
      // 常用规格请求序号：类目快速切换时丢弃旧响应，避免覆盖
      favSeq: 0,
      // 滚动联动已解析到的二级类目 id（去重，避免重复请求）
      spyCategoryId: '',
      // 滚动联动高亮的左侧导航类目 id（空 = 停留在「最近常选」区）
      navSpyId: '',
      // 当前视口顶部商品所属的规格值（最近常选区为空）：规格标签随滚动自动高亮浅蓝
      activeSpec: '',
      // 类目 id → 二级祖先节点映射：商品行 categoryId 可能是叶子类目，常用规格卡按二级类目取规格
      catParentMap: {},
      categoryTree: [],
      categoryLoaded: false,
      // 拍照识别
      ocrVisible: false
    }
  },
  computed: {
    /** 已选商品数量合计（底部胶囊展示；用箭头函数保证回调内 this 指向组件） */
    totalQty() {
      return this.selectedGoods.reduce((sum, goods) => {
        return sum + (this.qtyMap[goods.key] || 0)
      }, 0)
    },
    /** 左侧导航：遍历类目树取所有二级节点 */
    navCategories() {
      var list = []
      function walk(nodes) {
        ;(nodes || []).forEach(function(node) {
          if (String(node.categoryLevel) === '2') list.push(node)
          walk(node.children)
        })
      }
      walk(this.categoryTree)
      return list
    }
  },
  watch: {
    visible(val) {
      if (val) {
        this.keyword = this.initialKeyword || ''
        // 带入业务页面已选商品（含数量）
        var qtyMap = {}
        this.selectedGoods = (this.initialSelected || []).map(function(goods) {
          var normalized = this.normalizeGoods(goods)
          qtyMap[normalized.key] = Number(goods.qty) || 1
          return normalized
        }, this)
        this.qtyMap = qtyMap
        var recentOpts = {
          days: this.recentDays,
          limit: this.recentLimit,
          requireRaw: this.recentRequireRaw
        }
        // 打开时只刷新一次商品流：等最近常选（店铺共享，失败回退本地）确定后再重建，
        // 避免本地列表与服务端列表各触发一次重载导致的空白/不可滚动
        this.loadSharedRecent(recentOpts)
        // 每次打开都回到默认状态：展示全部商品（类目树里存在无商品的废弃类目，不默认选中任何类目）
        this.filterCategoryId = undefined
        this.favSpecs = []
        // 导航联动回到初始状态，等待商品流加载后按首个可见商品联动
        this.spyCategoryId = ''
        this.navSpyId = ''
        this.activeSpec = ''
        if (!this.categoryLoaded) this.loadTreeData()
        // 常用规格卡：打开时先展示「全部商品」的常用规格
        this.loadFavSpecs()
        // 每次打开都从第 1 页开始，并清掉上次的滚动位置
        if (this.$store && this.$store.commit) {
          this.$store.commit('goodsFeed/CLEAR_FEED_STATE', 'goods_picker_v2')
        }
      }
    }
  },
  methods: {
    /** 最近使用只存了名称+规格展示文本，回填前补齐规格值（规格解析复用 goodsFeedItem 标准模块） */
    normalizeGoods(goods) {
      var values = [goods.spec1, goods.spec2, goods.spec3]
      if (!values[0] && !values[1] && !values[2] && goods.spec) {
        values = splitSpecValues(goods.spec, 3)
      }
      return {
        key: goods.key || (goods.name + '_' + (goods.spec || '')),
        name: goods.name || '',
        spec: goods.spec || '',
        spec1: values[0] || '',
        spec2: values[1] || '',
        spec3: values[2] || '',
        imgUrl: goods.imgUrl || (goods.raw ? getRowImgUrl(goods.raw) : ''),
        specLines: parseSpecLines(goods.spec, 2),
        price: (goods.raw && goods.raw.commodityDecimal != null) ? goods.raw.commodityDecimal : '-',
        stock: (goods.raw && goods.raw.stock != null) ? goods.raw.stock : '-',
        raw: goods.raw || null
      }
    },
    /** 点击商品卡片：数量 +1（首次点击加入已选） */
    addQty(goods) {
      if (!goods || !goods.name) return
      var key = goods.key
      if (!this.qtyMap[key]) {
        this.selectedGoods.push(this.normalizeGoods(goods))
        this.$set(this.qtyMap, key, 0)
      }
      this.$set(this.qtyMap, key, this.qtyMap[key] + 1)
    },
    /** 减号按钮：数量 -1，减到 0 自动从已选移除 */
    minusQty(key) {
      var next = (this.qtyMap[key] || 0) - 1
      if (next > 0) {
        this.$set(this.qtyMap, key, next)
      } else {
        this.$delete(this.qtyMap, key)
        for (var i = 0; i < this.selectedGoods.length; i++) {
          if (this.selectedGoods[i].key === key) {
            this.selectedGoods.splice(i, 1)
            break
          }
        }
      }
    },
    /** 商品流卡片点击：goods 已在加载时换算好，直接数量 +1 */
    onFeedItemClick(item) {
      if (!item) return
      this.addQty(item.goods)
    },
    /** 商品流减号按钮：数量 -1 */
    onFeedMinus(item) {
      if (!item) return
      this.minusQty(item.goods.key)
    },
    /** 商品流加号按钮：数量 +1 */
    onFeedPlus(item) {
      if (!item) return
      this.addQty(item.goods)
    },
    /** 点击底部已选数量胶囊：按首次点击顺序带回商品与数量同步到结算单（由父组件关闭弹层） */
    confirmSelect() {
      if (this.selectedGoods.length === 0) return
      var list = this.selectedGoods.map(function(goods) {
        return Object.assign({}, goods, { qty: this.qtyMap[goods.key] || 1 })
      }, this)
      list.forEach(function(goods) {
        for (var i = 0; i < goods.qty; i++) recordGoodsUsage(goods)
      })
      this.$emit('confirm', list)
    },
    /** 底栏搜索：重置商品流并按关键字查询 */
    handleSearch(text) {
      if (typeof text === 'string') this.keyword = text
      this.resetFeed()
    },
    /** 加载商品类别树（左侧导航使用；无二级类目时不显示导航栏） */
    loadTreeData() {
      var self = this
      queryMaterialCategoryTreeList({ id: '' }).then(function(res) {
        self.categoryTree = res || []
        self.categoryLoaded = true
        // 构建叶子类目 → 二级类目映射，供滚动联动/常用规格定位使用
        self.buildCatParentMap()
      }).catch(function() {
        self.categoryLoaded = true
      })
    },
    /** 类目 id → 二级祖先节点映射：商品行 categoryId 可能挂在三级叶子类目上，规格统计按二级类目取 */
    buildCatParentMap() {
      var map = {}
      function walk(nodes, anchor) {
        ;(nodes || []).forEach(function(node) {
          var next = String(node.categoryLevel) === '2' ? node : anchor
          if (next) map[String(node.id)] = next
          walk(node.children, next)
        })
      }
      walk(this.categoryTree, null)
      this.catParentMap = map
    },
    /**
     * 商品流首个可见商品（scroll-spy）：仅「最近常选」模式联动——
     * 最近常选区（近一周高频商品）保持「最近常选」高亮；
     * 其余商品 categoryId 解析到二级祖先类目后，联动左侧导航高亮。
     * 类目显式选中时不联动。
     */
    onFeedFirstVisible(item) {
      // 常用规格卡滚动联动高亮：所有模式都跟随视口顶部商品（最近常选区商品不参与，保持无高亮）
      this.updateActiveSpec(item)
      if (this.filterCategoryId !== undefined) return
      // 最近常选区：导航回到「最近常选」高亮
      if (item && item.recentFlag) {
        if (this.navSpyId) {
          this.navSpyId = ''
          this.scrollNavIntoView()
        }
        return
      }
      var goods = item && item.goods
      var cid = goods ? ((goods.categoryId || (goods.raw && goods.raw.categoryId)) || '') : ''
      var node = cid ? this.catParentMap[String(cid)] : null
      if (!node) return
      var id = String(node.id)
      if (id !== this.spyCategoryId) {
        this.spyCategoryId = id
      }
      if (this.navSpyId !== id) {
        this.navSpyId = id
        this.scrollNavIntoView()
      }
    },
    /**
     * 视口顶部商品的规格值 → 标签浅蓝高亮。
     * 仅当商品属于当前类目区（显式选中或滚动联动的类目）时生效；
     * 最近常选区商品与跨类目滚动期间不高亮（activeSpec 置空）
     */
    updateActiveSpec(item) {
      var spec = ''
      var goods = item && !item.recentFlag ? item.goods : null
      var lines = goods && goods.specLines
      if (lines) {
        for (var i = 0; i < lines.length; i++) {
          var line = lines[i]
          if (line && (line.label === '尺寸' || line.label === '规格') && line.value) {
            spec = String(line.value).replace(/\s+/g, '')
            break
          }
        }
      }
      if (spec) {
        // 商品类目需与当前类目区一致，否则置空避免跨区误高亮
        var zone = this.filterCategoryId !== undefined ? String(this.filterCategoryId) : (this.spyCategoryId || '')
        var cid = goods.categoryId || (goods.raw && goods.raw.categoryId) || ''
        var node = cid ? this.catParentMap[String(cid)] : null
        if (!zone || !node || String(node.id) !== String(zone)) spec = ''
      }
      if (spec !== this.activeSpec) this.activeSpec = spec
    },
    /** 左侧导航类目高亮：显式选中按选中态，否则跟随滚动联动 */
    isNavActive(cat) {
      if (this.filterCategoryId !== undefined) return this.isActiveCategory(cat)
      return !!this.navSpyId && this.navSpyId === String(cat.id)
    },
    /** 导航联动高亮后，让目标类目在导航栏内居中可见（导航栏自身滚动，不影响页面） */
    scrollNavIntoView() {
      var self = this
      this.$nextTick(function() {
        var nav = self.$refs.navBox
        var found = self.navSpyId ? self.$refs['navItem' + self.navSpyId] : self.$refs.navItemAll
        var el = found && (found[0] || found)
        if (!nav || !el || !el.offsetTop) return
        var target = el.offsetTop - (nav.clientHeight - el.offsetHeight) / 2
        var max = nav.scrollHeight - nav.clientHeight
        nav.scrollTop = Math.max(0, Math.min(max, target))
      })
    },
    /** 类目是否激活（id 统一按字符串比较） */
    isActiveCategory(cat) {
      return String(this.filterCategoryId) === String(cat.id)
    },
    /** 点击左侧「最近常选」：清空类目过滤；已在最近常选时仅回到顶部（顶部为近一周高频商品） */
    selectNavAll() {
      if (this.filterCategoryId !== undefined) {
        this.filterCategoryId = undefined
        this.resetFeed()
        this.loadFavSpecs()
        // 导航联动清空，等待商品流加载后按首个可见商品重新联动
        this.spyCategoryId = ''
        this.navSpyId = ''
        this.activeSpec = ''
      } else if (this.$refs.feed) {
        this.$refs.feed.scrollToTop()
      }
    },
    /** 点击左侧类目：再次点击取消（回到最近常选） */
    selectNavCategory(cat) {
      var selected = !this.isActiveCategory(cat)
      this.filterCategoryId = selected ? cat.id : undefined
      // 类目切换后常用规格随类目刷新
      this.resetFeed()
      this.loadFavSpecs()
      if (selected) {
        // 显式选中类目：导航高亮该类目（滚动联动停用）
        this.spyCategoryId = String(cat.id)
        this.navSpyId = String(cat.id)
      } else {
        // 返回最近常选：联动清空，等待商品流加载后按首个可见商品重新联动
        this.spyCategoryId = ''
        this.navSpyId = ''
        this.activeSpec = ''
      }
    },
    /**
     * 点击规格标签（常用规格卡，锚点模式）：商品流滚动到该规格商品处并带到视口顶部。
     * 列表不过滤，其他商品位置不变；商品流已按类目+规格排序，同规格商品相邻。
     * 优先定位当前类目区内的该规格（显式选中的类目或滚动联动解析到的类目），找不到再定位全流首个匹配。
     */
    scrollToSpec(tag) {
      if (!this.$refs.feed) return
      var self = this
      var text = String(tag.text || '')
      var zone = this.filterCategoryId !== undefined ? String(this.filterCategoryId) : (this.spyCategoryId || '')
      function specMatch(item, sameZone) {
        // 最近常选区不受规格影响：锚点定位跳过该区商品
        if (!item || item.recentFlag) return false
        var goods = item.goods
        var lines = goods && goods.specLines
        if (!lines) return false
        for (var i = 0; i < lines.length; i++) {
          var line = lines[i]
          if (!line || (line.label !== '尺寸' && line.label !== '规格')) continue
          if (String(line.value || '').replace(/\s+/g, '') !== text) continue
          if (!sameZone) return true
          var cid = goods.categoryId || (goods.raw && goods.raw.categoryId) || ''
          var node = cid ? self.catParentMap[String(cid)] : null
          return !!node && String(node.id) === String(zone)
        }
        return false
      }
      if (zone) {
        this.$refs.feed.scrollToItem(function(item) { return specMatch(item, true) }).then(function(found) {
          if (!found && self.$refs.feed) {
            return self.$refs.feed.scrollToItem(function(item) { return specMatch(item, false) })
          }
        })
      } else {
        this.$refs.feed.scrollToItem(function(item) { return specMatch(item, false) })
      }
    },
    /**
     * 常用规格卡：按当前类目取规格值频次 Top4。
     * 类目选中 → /material/listCategorySkuValueStats（SQL 按类目+直接子类目统计，返回全部属性名），
     * 「全部」→ /material/listSkuAttributeValueStats（全租户按属性名统计）。
     * 属性名沿用旧版约定：优先「尺寸」，无值退「规格」。
     */
    loadFavSpecs() {
      var self = this
      var seq = ++this.favSeq
      var promise
      if (this.filterCategoryId === undefined) {
        promise = this.fetchFavRows('尺寸').then(function(rows) {
          return rows.length ? rows : self.fetchFavRows('规格')
        })
      } else {
        promise = getAction('/material/listCategorySkuValueStats', { categoryId: this.filterCategoryId })
          .then(function(res) {
            var rows = (res && res.data && res.data.rows) || []
            var sizeRows = rows.filter(function(row) { return row.fieldName === '尺寸' })
            var specRows = rows.filter(function(row) { return row.fieldName === '规格' })
            return sizeRows.length ? sizeRows : specRows
          })
          .catch(function() { return [] })
      }
      promise.then(function(rows) {
        // 请求期间类目已再次切换：丢弃旧响应
        if (seq !== self.favSeq) return
        self.favSpecs = self.normalizeFavRows(rows)
      })
    },
    /** 全部商品维度：按属性名统计规格值频次（rows 统一补 fieldName 后返回） */
    fetchFavRows(attributeName) {
      return getAction('/material/listSkuAttributeValueStats', { attributeName: attributeName })
        .then(function(res) {
          return ((res && res.data && res.data.rows) || []).map(function(row) {
            return Object.assign({ fieldName: attributeName }, row)
          })
        })
        .catch(function() { return [] })
    },
    /**
     * 规格值清洗与聚合（与旧版规则一致）：
     * 多值取第一段（200x230cm/245x250cm → 200x230cm）、去不可见空白、按频次累加后排序
     * limit：取前 N 个（左侧常用规格卡 4 个）
     */
    normalizeFavRows(rows, limit) {
      var counts = {}
      var order = []
      ;(rows || []).forEach(function(row) {
        var text = row && row.valueText ? String(row.valueText).split('/')[0] : ''
        text = text.replace(/\s+/g, '')
        if (!text) return
        if (!counts[text]) {
          counts[text] = 0
          order.push(text)
        }
        counts[text] += Number(row.valueCount) || 0
      })
      return order
        .map(function(text) { return { text: text, count: counts[text] } })
        .sort(function(a, b) { return b.count - a.count || (a.text < b.text ? -1 : 1) })
        .slice(0, limit || 4)
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
    /**
     * 打开时先确定最近常选，再用它重建一次商品流（服务端为空也照样重建，展示全部商品按类目排序）。
     * 接口超时/失败时回退本地记录，保证离线也能打开。
     */
    loadSharedRecent(opts) {
      var self = this
      var settled = false
      var timer = setTimeout(function() {
        if (settled) return
        settled = true
        self.recentList = getRecentGoods(opts)
        self.resetFeed()
      }, 2500)
      fetchRecentGoods(opts).then(function(res) {
        if (settled) return
        settled = true
        clearTimeout(timer)
        if (!self.visible || !res || !res.list) return
        if (res.fromServer) clearLocalGoodsUsage()
        self.recentList = res.list
        self.resetFeed()
      })
    },
    filterActive() {
      return !!this.filterCategoryId
    },
    /** 最近使用 → 商品流卡片数据（仅在无搜索、无筛选时并入首页） */
    buildRecentFeedItems() {
      if (this.keyword || this.filterActive()) return []
      var rows = []
      this.recentList.forEach(function(goods) {
        var g = this.normalizeGoods(goods)
        if (!g.name) return
        rows.push({
          skuRowKey: g.key,
          // 名称+规格用于和接口最新商品做去重：最新数据优先，避免沿用旧 SKU 编码
          nameKey: g.name + '\u0000' + (g.spec || ''),
          // 最近常选区标记：滚动联动时保持「最近常选」导航高亮
          recentFlag: true,
          name: g.name,
          imgUrl: g.imgUrl,
          specs: g.specLines,
          // 弹层卡片不展示库存
          items: [],
          // 零售价：卡片名称行右侧
          price: g.price,
          goods: g
        })
      }, this)
      return rows
    },
    /** 标准商品流加载器：第 1 页并入最近使用，其余按接口分页；categoryId 跟随左侧类目导航 */
    loadPickerFeed({ page, pageSize }) {
      var self = this
      if (page === 1) this.seenKeys = {}
      var recentRows = page === 1 ? this.buildRecentFeedItems() : []
      if (page === 1) this.recentNameKeys = {}
      var searchObj = {
        materialParam: this.keyword,
        categoryId: this.filterCategoryId,
        // 无关键字时按左侧类目树顺序 + 类目内规格值排序（配合导航联动与规格标签锚点滚动；搜索时保持接口默认排序）
        orderByCategory: this.keyword ? '' : '1',
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
        // 最近常选优先：近一周选择最多的商品固定在商品流最前（不受类目/规格排序影响），
        // 与最近常选同名称+规格的最新商品不再重复展示（全部页次生效），避免最近常选区被去重清空
        var head = []
        recentRows.forEach(function(item) {
          if (item.nameKey) self.recentNameKeys[item.nameKey] = true
          if (self.seenKeys[item.skuRowKey]) return
          self.seenKeys[item.skuRowKey] = true
          head.push(item)
        })
        rows.forEach(function(row) {
          var item = toGoodsFeedItem(row)
          // 弹层卡片不展示库存：清空信息行（零售价独立定位，卡片高度不受影响）
          item.items = []
          // 规格文本已在 toGoodsFeedItem 内解析一次，去重键直接复用（此前每行重复解析 2 次）
          var nameKey = item.name + '\u0000' + item.goods.spec
          if (self.seenKeys[item.skuRowKey]) return
          if (nameKey && self.recentNameKeys[nameKey]) return
          self.seenKeys[item.skuRowKey] = true
          list.push(item)
        })
        return { list: head.concat(list), total: total + head.length }
      })
    }
  }
}
</script>

<style>
/* 新版添加商品弹层页面底色纯白：仅作用于本弹层，不影响其它标准弹层页（mmp-page 默认 #f2f4f7） */
.mmp-page.picker-page-white {
  background: #fff;
}
/* 左侧二级类目导航 + 商品流：左右分栏，商品流自带滚动。
   高度用 height:0 + flex-grow 撑开：部分 iOS 内核对嵌套 flex-basis:0% 按内容解析高度，
   会导致导航溢出弹层、商品流没有可滚动区域（上下滑不动），不能用 flex:1 简写 */
.pkv2-body {
  display: flex;
  height: 0;
  flex-grow: 1;
  min-height: 0;
}
/* 左侧导航：92px 浅灰底，激活项白底加粗 + 左缘蓝色指示条 */
.pkv2-nav {
  position: relative;
  flex: none;
  width: 92px;
  background: #f7f8fa;
  overflow-y: auto;
  padding: 6px 0 84px;
}
.pkv2-nav__item {
  position: relative;
  padding: 13px 8px;
  font-size: 13px;
  line-height: 1.4;
  color: #4b5563;
  text-align: center;
  word-break: break-all;
  cursor: pointer;
}
.pkv2-nav__item.is-on {
  background: #fff;
  color: #111827;
  font-weight: 600;
}
.pkv2-nav__item.is-on::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  background: #1890ff;
  border-radius: 0 2px 2px 0;
}
/* 常用规格卡：类目列表下方空白区，虚线分隔；白底描边小标签，滚动联动当前规格浅蓝高亮（点击滚动定位） */
.pkv2-fav {
  margin: 10px 8px 0;
  border-top: 1px dashed #d8dee7;
  padding-top: 10px;
}
.pkv2-fav__title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #9aa3af;
  margin-bottom: 8px;
}
.pkv2-fav__title::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: #e8f3ff;
  border: 1px solid #1890ff;
}
.pkv2-fav__tag {
  background: #fff;
  border: 1px solid #e3eaf3;
  border-radius: 6px;
  padding: 6px;
  font-size: 11px;
  color: #4b5563;
  text-align: center;
  word-break: break-all;
  cursor: pointer;
}
.pkv2-fav__tag.is-on {
  background: #e8f3ff;
  border-color: #1890ff;
  color: #1890ff;
  font-weight: 600;
}
/* 商品流：与左侧导航之间留白 12px */
.pkv2-feed {
  flex: 1;
  min-width: 0;
  padding-left: 12px;
}
</style>
