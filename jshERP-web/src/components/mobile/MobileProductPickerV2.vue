<template>
  <!-- 新版手机端添加商品弹层：左侧二级类目导航 + 商品卡片点选加数量（复用标准弹层骨架与商品流模块） -->
  <mobile-modal-page class="picker-page-white" :style="{ background: '#fff' }" :visible="visible" :title="title" subtitle="V1126G" @close="$emit('close')">
    <mobile-search-layout
      v-model="keyword"
      :show-filter="false"
      :inline-input="true"
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
            :class="{ 'is-on': !navSpyId }"
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
            :page-size="pageSize"
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
import { createGoodsPickerFeed, normalizePickerGoods } from './goodsPickerFeed'
import { secondLevelCategories, buildCatParentMap, itemCategoryId, matchesCategory, itemSpec, matchesSpec, normalizeFavRows } from './goodsPickerNavigation'
import MobileModalPage from './MobileModalPage'
import MobileSearchLayout from './MobileSearchLayout'
import MobileGoodsFeed from './MobileGoodsFeed'
import OcrRecognizeModal from '@/views/material/modules/OcrRecognizeModal'
import { recordGoodsUsage } from './goodsUsage'

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
      pickerFeed: createGoodsPickerFeed({
        getKeyword: () => this.keyword,
        onError: message => this.$message.error(message)
      }),
      // 已选商品（按首次点击顺序，数量存于 qtyMap）
      selectedGoods: [],
      // 各商品已选数量（key → 数量）
      qtyMap: {},
      // 商品流条件重置信号
      pickerResetKey: 0,
      // 左侧类目导航：点击类目为锚点滚动（商品流保持全量连续列表，不过滤不重置）
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
      return secondLevelCategories(this.categoryTree)
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
        // 最近常选（店铺共享，失败回退本地）：只记录就绪信号，不再单独重建商品流，
        // 第 1 页数据与它并行请求、合并后一次渲染，避免首屏先渲染再被重置
        this.pickerFeed.prepareRecent(recentOpts)
        // 每次打开都回到默认状态：展示全部商品（类目树里存在无商品的废弃类目，不默认选中任何类目）
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
      } else {
        this.pickerFeed.invalidate(true)
        this.favSeq += 1
      }
    }
  },
  beforeDestroy() {
    this.pickerFeed.invalidate(true)
    this.favSeq += 1
  },
  methods: {
    /** 最近使用只存了名称+规格展示文本，回填前补齐规格值（规格解析复用 goodsFeedItem 标准模块） */
    normalizeGoods(goods) {
      return normalizePickerGoods(goods)
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
      this.catParentMap = buildCatParentMap(this.categoryTree)
    },
    /**
     * 商品流首个可见商品（scroll-spy）：全程联动——
     * 最近常选区（近一周高频商品）保持「最近常选」高亮；
     * 其余商品 categoryId 解析到二级祖先类目后，联动左侧导航高亮与常用规格卡。
     */
    onFeedFirstVisible(item) {
      if (!this.visible) return
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
      return this.$refs.feed.scrollToItemResult(function(item) {
        return matchesCategory(item, id, self.catParentMap)
      }).then(function(result) {
        self.showNavigationResult(result, '该类目暂无商品')
      })
    },
    showNavigationResult(result, emptyText) {
      if (!this.visible || !result) return
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
        if (zone && result.status === 'exhausted' && self.visible && self.$refs.feed === feed) {
          return locate('')
        }
        return result
      }).then(function(result) {
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
      this.pickerFeed.invalidate()
      this.clearNavZone()
      this.pickerResetKey += 1
    },
    /** 数据会话、最近常选合并与跨页去重由独立模块维护。 */
    loadPickerFeed(params) {
      return this.pickerFeed.load(params)
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
