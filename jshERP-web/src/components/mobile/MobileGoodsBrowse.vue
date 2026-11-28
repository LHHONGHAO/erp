<template>
  <!--
    手机端标准商品浏览框架（添加商品 / 商品信息 / 后续同类页面统一复用）
    固定骨架：左侧二级类目导航 + 常用规格卡 + 右侧商品流 + 底部搜索胶囊。
    业务差异通过 props / 插槽增减：查询条件(query)、关键字(value)、卡片元素(show-price / qty / select / print)、
    胶囊右侧工具(float-extra)、筛选面板(filter-groups / filter-extra)。
  -->
  <mobile-search-layout
    ref="layout"
    :value="value"
    :placeholder="searchPlaceholder"
    :auto-scan-close="autoScanClose"
    :inline-input="inlineInput"
    :show-filter="showFilter"
    :filter-visible="filterVisible"
    :filter-groups="filterGroups"
    :left-hand="leftHand"
    :show-hand-switch="showHandSwitch"
    @input="onInput"
    @search="search"
    @camera="$emit('camera')"
    @scan-result="$emit('scan-result', $event)"
    @filter-change="onFilterChange"
    @filter-clear="$emit('filter-clear')"
    @update:filterVisible="$emit('update:filterVisible', $event)"
    @update:leftHand="$emit('update:leftHand', $event)">
    <div class="mgb-body">
      <div v-if="navCategories.length" ref="navBox" class="mgb-nav">
        <!-- 最近常选：由 recent 开关控制；顶部并入近一周选择次数最高的商品，滚动到类目商品时导航联动高亮 -->
        <div
          v-if="showRecent"
          ref="navItemAll"
          class="mgb-nav__item"
          :class="{ 'is-on': !navSpyId }"
          @click="selectNavAll">最近常选</div>
        <div
          v-for="cat in navCategories"
          :key="cat.id"
          :ref="'navItem' + cat.id"
          class="mgb-nav__item"
          :class="{ 'is-on': isNavActive(cat) }"
          @click="selectNavCategory(cat)">{{ cat.title }}</div>
        <!-- 常用规格卡：当前类目（含子类目）下最常用的 4 个规格值，点击滚动到该规格商品处（不做过滤） -->
        <div v-if="favSpecs.length" class="mgb-fav">
          <div class="mgb-fav__title">常用规格</div>
          <div
            v-for="tag in favSpecs"
            :key="tag.text"
            class="mgb-fav__tag"
            :class="{ 'is-on': activeSpec === tag.text }"
            @click="scrollToSpec(tag)">{{ tag.text }}</div>
        </div>
      </div>
      <div class="mgb-feed">
        <mobile-goods-feed
          ref="feed"
          :feed-key="feedKey"
          :loader="loadFeed"
          :page-size="pageSize"
          :gap="gap"
          :reset-key="resetKey"
          :qty-mode="qtyMode"
          :qty-map="qtyMap"
          :selectable="selectable"
          :show-print="showPrint"
          :show-price="showPrice"
          :highlight-spec="specHighlight"
          :swipe-edit="swipeEdit"
          :selected-keys="selectedKeys"
          :empty-text="emptyText"
          @item-click="$emit('item-click', $event)"
          @minus="$emit('minus', $event)"
          @plus="$emit('plus', $event)"
          @print="$emit('print', $event)"
          @edit="$emit('edit', $event)"
          @detail="$emit('detail', $event)"
          @select="$emit('select', $event)"
          @first-visible="onFeedFirstVisible">
          <template #metaExtra="{ item }">
            <slot name="card-meta" :item="item"></slot>
          </template>
        </mobile-goods-feed>
      </div>
    </div>

    <!-- 搜索胶囊右侧业务工具：如添加商品的「已选 N」 -->
    <template #float-extra>
      <slot name="float-extra"></slot>
    </template>
    <!-- 业务筛选表单 -->
    <template #filter-extra>
      <slot name="filter-extra"></slot>
    </template>
    <!-- 页面浮层（弹窗、扫码等） -->
    <template #sheet>
      <slot name="overlay"></slot>
    </template>
  </mobile-search-layout>
</template>

<script>
import { getAction } from '@/api/manage'
import { queryMaterialCategoryTreeList } from '@/api/api'
import { createGoodsPickerFeed } from './goodsPickerFeed'
import { secondLevelCategories, buildCatParentMap, itemCategoryId, matchesCategory, itemSpec, matchesSpec, normalizeFavRows } from './goodsPickerNavigation'
import MobileGoodsFeed from './MobileGoodsFeed'
import MobileSearchLayout from './MobileSearchLayout'

/**
 * 手机端标准商品浏览框架（唯一复用模块）
 *
 * 固定部分：左侧二级类目导航 + 最近常选 + 常用规格锚点 + 商品流 + 底部搜索胶囊。
 * 业务部分：
 *   query / value      查询条件与关键字（排序由框架统一注入 orderByCategory）
 *   recent             最近常选配置（传 null 关闭该区块与左栏入口）
 *   show-price         卡片价格是否显示
 *   qty-mode / selectable / show-print / swipe-edit / selected-keys  卡片业务操作
 *   #float-extra       胶囊右侧工具（如「已选 N」）
 *   #filter-groups     筛选标签组；#filter-extra 自定义筛选项
 *   #card-meta         卡片信息行追加内容
 *   #overlay           页面弹层
 *
 * 导航定位全部是锚点滚动，不过滤、不重置商品流；无商品类目仅提示，不影响继续上下滑动。
 */
export default {
  name: 'MobileGoodsBrowse',
  components: { MobileGoodsFeed, MobileSearchLayout },
  props: {
    /** 商品流缓存键（同一页面内唯一） */
    feedKey: { type: String, required: true },
    /** 商品分页接口，默认 SKU 列表 */
    url: { type: String, default: '/material/listSku' },
    /** 业务查询条件（状态、类别、显示方式等），排序与关键字由框架注入 */
    query: { type: Object, default: () => ({}) },
    /** 搜索关键字，支持 v-model */
    value: { type: String, default: '' },
    /** 最近常选配置 { days, limit, requireRaw }，传 null 关闭 */
    recent: { type: Object, default: null },
    /** 选择场景只保留名称/图片/规格/价格，信息行留空 */
    compact: { type: Boolean, default: true },
    pageSize: { type: Number, default: 20 },
    gap: { type: Number, default: 16 },
    /** 条件重置信号：变化时商品流回到顶部重新加载，并清空类目联动 */
    resetKey: { type: Number, default: 0 },
    /** 页面是否处于前台（弹层关闭时置 false，丢弃旧请求并停止联动） */
    active: { type: Boolean, default: true },
    emptyText: { type: String, default: '暂无商品' },
    // 底部搜索胶囊
    searchPlaceholder: { type: String, default: '搜索商品名称 / 规格 / 条码' },
    inlineInput: { type: Boolean, default: true },
    autoScanClose: { type: Boolean, default: true },
    showFilter: { type: Boolean, default: false },
    filterGroups: { type: Array, default: () => [] },
    filterVisible: { type: Boolean, default: false },
    leftHand: { type: Boolean, default: false },
    showHandSwitch: { type: Boolean, default: false },
    // 商品流能力透传
    qtyMode: { type: Boolean, default: false },
    qtyMap: { type: Object, default: () => ({}) },
    selectable: { type: Boolean, default: false },
    showPrint: { type: Boolean, default: false },
    showPrice: { type: Boolean, default: true },
    swipeEdit: { type: Boolean, default: false },
    selectedKeys: { type: Array, default: () => [] }
  },
  data() {
    return {
      // 当前搜索关键字（与 value 同步，保证重置后重新加载时读取到最新值）
      keywordText: this.value || '',
      // 商品流会话：最近常选合并、跨页去重、旧响应隔离、排序注入全部内聚
      feed: createGoodsPickerFeed({
        getKeyword: () => this.keywordText,
        getSearch: () => this.query,
        getUrl: () => this.url,
        compact: this.compact,
        onError: message => this.$emit('error', message)
      }),
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
      // 点击常用规格后需要高亮底色的规格值：卡片内对应规格行加底色，直到切换类目/搜索
      specHighlight: '',
      // 类目 id → 二级祖先节点映射：商品行 categoryId 可能是叶子类目，常用规格卡按二级类目取规格
      catParentMap: {},
      categoryTree: [],
      categoryLoaded: false
    }
  },
  computed: {
    /** 左侧导航：遍历类目树取所有二级节点 */
    navCategories() {
      return secondLevelCategories(this.categoryTree)
    },
    /** 是否启用最近常选区块（关闭时左栏不显示该入口） */
    showRecent() {
      return !!this.recent
    }
  },
  watch: {
    /** 外部关键字变化（扫码/OCR/外部带入） */
    value(val) {
      if (val !== this.keywordText) this.keywordText = val || ''
    },
    /** 进入前台：重置联动状态、加载类目树与常用规格；离开前台：作废旧请求 */
    active(val) {
      if (val) {
        this.onActivate()
      } else {
        this.favSeq += 1
        this.feed.invalidate(true)
      }
    },
    /** 搜索/筛选等条件重置：先同步关键字，再清空导航联动（商品流由 resetKey 自行重载） */
    resetKey() {
      this.keywordText = this.value || ''
      this.clearNavZone()
    }
  },
  created() {
    if (this.active) this.onActivate()
  },
  beforeDestroy() {
    this.favSeq += 1
    this.feed.invalidate(true)
  },
  methods: {
    onActivate() {
      this.spyCategoryId = ''
      this.navSpyId = ''
      this.activeSpec = ''
      this.specHighlight = ''
      if (!this.categoryLoaded) this.loadTreeData()
      // 先展示「全部商品」的常用规格，商品流加载后随滚动联动切换
      this.loadFavSpecs()
      // 最近常选：第 1 页与它并行请求、合并后一次渲染
      if (this.recent) this.feed.prepareRecent(this.recent)
    },
    /** 商品流加载器：会话、最近常选、去重与排序全部由框架维护 */
    loadFeed(params) {
      return this.feed.load(params)
    },
    /** 搜索框输入：同步关键字（不立即重载，由 search 事件触发） */
    onInput(text) {      this.keywordText = typeof text === 'string' ? text : ''
      this.$emit('input', this.keywordText)
      this.$emit('update:value', this.keywordText)
    },
    /** 搜索（回车/确认/外部带入关键字）：回到顶部按新关键字重新加载 */
    search(text) {
      if (typeof text === 'string') {
        this.keywordText = text
        this.$emit('input', text)
        this.$emit('update:value', text)
      }
      this.clearNavZone()
      if (this.$refs.feed) this.$refs.feed.reload()
    },
    /** 筛选标签点击：原样透传给调用页处理 */
    onFilterChange(key, opt) {
      this.$emit('filter-change', key, opt)
    },
    /** 加载商品类别树（左侧导航使用；无二级类目时不显示导航栏） */
    loadTreeData() {
      var self = this
      queryMaterialCategoryTreeList({ id: '' }).then(function(res) {
        self.categoryTree = res || []
        self.categoryLoaded = true
        self.buildCatParentMap()
      }).catch(function() {
        self.categoryLoaded = true
      })
    },
    /** 类目 id → 二级祖先节点映射：商品行 categoryId 可能挂在三级叶子类目上，规格统计按二级类目取 */
    buildCatParentMap() {
      this.catParentMap = buildCatParentMap(this.categoryTree)
    },
    /**
     * 商品流首个可见商品（scroll-spy）：全程联动——
     * 最近常选区（近一周高频商品）保持「最近常选」高亮；
     * 其余商品 categoryId 解析到二级祖先类目后，联动左侧导航高亮与常用规格卡。
     */
    onFeedFirstVisible(item) {
      if (!this.active) return
      if (item && item.recentFlag) {
        this.clearNavZone()
        return
      }
      var id = itemCategoryId(item, this.catParentMap)
      if (!id) {
        this.activeSpec = ''
        return
      }
      if (id !== this.spyCategoryId) {
        this.spyCategoryId = id
        this.loadFavSpecs()
      }
      if (this.navSpyId !== id) {
        this.navSpyId = id
        this.scrollNavIntoView()
      }
      this.updateActiveSpec(item)
    },
    /**
     * 视口顶部商品的规格值 → 标签浅蓝高亮。
     * 仅当商品属于滚动联动解析到的当前类目区时生效；
     * 最近常选区商品与跨类目滚动期间不高亮（activeSpec 置空）
     */
    updateActiveSpec(item) {
      this.activeSpec = this.spyCategoryId && matchesCategory(item, this.spyCategoryId, this.catParentMap)
        ? itemSpec(item) : ''
    },
    /** 左侧导航类目高亮：全程跟随滚动联动 */
    isNavActive(cat) {
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
    /** 点击左侧「最近常选」：回到顶部（顶部为近一周高频商品），商品流不重置 */
    selectNavAll() {
      this.clearNavZone()
      if (this.$refs.feed) this.$refs.feed.scrollToTop()
    },
    clearNavZone() {
      var changed = !!this.spyCategoryId
      this.spyCategoryId = ''
      this.navSpyId = ''
      this.activeSpec = ''
      this.specHighlight = ''
      this.scrollNavIntoView()
      if (changed) this.loadFavSpecs()
    },
    /**
     * 点击左侧类目：锚点滚动到该类目（含子类目）第一个商品处，商品流保持全量连续列表。
     * 已加载范围找不到时由 scrollToItem 继续翻页查找；仍找不到（类目无商品）时
     * 不跳转、不清空流，仅轻量提示，上下滑动照常浏览其他类目商品。
     */
    selectNavCategory(cat) {
      if (!this.$refs.feed) return
      var self = this
      var id = String(cat.id)
      this.specHighlight = ''
      return this.$refs.feed.scrollToItemResult(function(item) {
        return matchesCategory(item, id, self.catParentMap)
      }).then(function(result) {
        self.showNavigationResult(result, '该类目暂无商品')
      })
    },
    showNavigationResult(result, emptyText) {
      if (!this.active || !result) return
      if (result.status === 'exhausted') this.$message.info(emptyText)
      if (result.status === 'limit') this.$message.info('本次查找已达分页上限，请继续浏览或搜索')
      if (result.status === 'failed') this.$message.info('商品加载失败，请稍后重试')
    },
    /**
     * 点击规格标签（常用规格卡，锚点模式）：商品流滚动到该规格商品处并带到视口顶部。
     * 列表不过滤，其他商品位置不变；商品流已按类目+规格排序，同规格商品相邻。
     * 优先定位滚动联动解析到的当前类目区内的规格，仅在查完无匹配后定位全流首个匹配。
     */
    scrollToSpec(tag) {
      if (!this.$refs.feed) return
      var self = this
      var text = String(tag.text || '')
      var zone = this.spyCategoryId || ''
      var feed = this.$refs.feed
      function locate(inZone) {
        return feed.scrollToItemResult(function(item) {
          return matchesSpec(item, text, inZone, self.catParentMap)
        })
      }
      return locate(zone).then(function(result) {
        if (zone && result.status === 'exhausted' && self.active && self.$refs.feed === feed) {
          return locate('')
        }
        return result
      }).then(function(result) {
        if (self.active && result && result.status === 'found') self.specHighlight = text
        self.showNavigationResult(result, '暂无该规格商品')
      })
    },
    /**
     * 常用规格卡：按当前类目取规格值频次 Top4。
     * 联动类目（滚动高亮）→ /material/listCategorySkuValueStats（SQL 按类目+直接子类目统计，返回全部属性名），
     * 「全部」→ /material/listSkuAttributeValueStats（全租户按属性名统计）。
     * 属性名沿用旧版约定：优先「尺寸」，无值退「规格」。
     */
    loadFavSpecs() {
      var self = this
      var seq = ++this.favSeq
      var promise
      if (!this.spyCategoryId) {
        promise = this.fetchFavRows('尺寸').then(function(rows) {
          return rows.length ? rows : self.fetchFavRows('规格')
        })
      } else {
        promise = getAction('/material/listCategorySkuValueStats', { categoryId: this.spyCategoryId })
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
        self.favSpecs = normalizeFavRows(rows)
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
    /** 供调用页直接替换商品流数据（如精确查询结果） */
    replaceItems(list) {
      if (this.$refs.feed) this.$refs.feed.replaceItems(list)
    },
    /** 供调用页回到商品流顶部 */
    scrollToTop() {
      if (this.$refs.feed) this.$refs.feed.scrollToTop()
    },
    /** 供调用页关闭标准扫码页 */
    closeScanPage() {
      if (this.$refs.layout) this.$refs.layout.closeScanPage()
    },
    /** 供调用页在扫码页内显示提示（如未命中商品） */
    setScanTip(text, isError) {
      if (this.$refs.layout) this.$refs.layout.setScanTip(text, isError)
    }
  }
}
</script>

<style>
/* 左侧二级类目导航 + 商品流：左右分栏，商品流自带滚动。
   高度用 height:0 + flex-grow 撑开：部分 iOS 内核对嵌套 flex-basis:0% 按内容解析高度，
   会导致导航溢出、商品流没有可滚动区域（上下滑不动），不能用 flex:1 简写 */
.mgb-body {
  display: flex;
  height: 0;
  flex-grow: 1;
  min-height: 0;
}
/* 左侧导航：92px 浅灰底，激活项白底加粗 + 左缘蓝色指示条 */
.mgb-nav {
  position: relative;
  flex: none;
  width: 92px;
  background: #f7f8fa;
  overflow-y: auto;
  padding: 6px 0 84px;
}
.mgb-nav__item {
  position: relative;
  padding: 13px 8px;
  font-size: 13px;
  line-height: 1.4;
  color: #4b5563;
  text-align: center;
  word-break: break-all;
  cursor: pointer;
}
.mgb-nav__item.is-on {
  background: #fff;
  color: #111827;
  font-weight: 600;
}
.mgb-nav__item.is-on::before {
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
.mgb-fav {
  margin: 10px 8px 0;
  border-top: 1px dashed #d8dee7;
  padding-top: 10px;
}
.mgb-fav__title {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #9aa3af;
  margin-bottom: 8px;
}
.mgb-fav__title::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: #e8f3ff;
  border: 1px solid #1890ff;
}
.mgb-fav__tag {
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
.mgb-fav__tag.is-on {
  background: #e8f3ff;
  border-color: #1890ff;
  color: #1890ff;
  font-weight: 600;
}
/* 商品流：与左侧导航之间留白 12px */
.mgb-feed {
  flex: 1;
  min-width: 0;
  padding-left: 12px;
}
</style>
