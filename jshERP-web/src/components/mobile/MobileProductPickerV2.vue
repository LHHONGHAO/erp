<template>
  <!-- 新版手机端添加商品弹层：左侧二级类目导航 + 商品卡片点选加数量（导航与商品流复用 MobileGoodsBrowse） -->
  <mobile-modal-page class="picker-page-white" :style="{ background: '#fff' }" :visible="visible" :title="title" subtitle="V1126G" @close="$emit('close')">
    <!-- 标准商品浏览框架：搜索胶囊 / 左栏导航 / 常用规格 / 商品流全部由框架统一提供 -->
    <mobile-goods-browse
      ref="browse"
      v-model="keyword"
      :active="visible"
      feed-key="goods_picker_v2"
      :query="emptyQuery"
      :recent="recentConfig"
      :page-size="pageSize"
      :gap="16"
      qty-mode
      :qty-map="qtyMap"
      @camera="ocrVisible = true"
      @error="$message.error($event)"
      @item-click="onFeedItemClick"
      @minus="onFeedMinus"
      @plus="onFeedPlus">
      <!-- 已选合计件数：常驻显示在搜索胶囊右侧；点击按当前已选数量直接同步回结算单 -->
      <template #float-extra>
        <div class="msearch-entry" :class="{ 'is-active': totalQty > 0 }" @click="confirmSelect">
          <span>已选</span>
          <span class="msearch-entry__badge">{{ totalQty }}</span>
        </div>
      </template>
    </mobile-goods-browse>

    <!-- AI 图片识别：识别商品信息后直接作为搜索词 -->
    <ocr-recognize-modal
      :visible="ocrVisible"
      :zIndex="3000"
      @ok="onOcrOk"
      @cancel="ocrVisible = false" />
  </mobile-modal-page>
</template>

<script>
import { normalizePickerGoods } from './goodsPickerFeed'
import MobileModalPage from './MobileModalPage'
import MobileGoodsBrowse from './MobileGoodsBrowse'
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
 *
 * 浏览主体（类目导航 + 常用规格 + 商品流）由 MobileGoodsBrowse 统一提供，与商品信息页共用。
 */
export default {
  name: 'MobileProductPickerV2',
  components: { MobileModalPage, MobileGoodsBrowse, OcrRecognizeModal },
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
      // 浏览框架只接收业务条件，排序与关键字由框架统一注入
      emptyQuery: {},
      // 已选商品（按首次点击顺序，数量存于 qtyMap）
      selectedGoods: [],
      // 各商品已选数量（key → 数量）
      qtyMap: {},
      // 拍照识别
      ocrVisible: false
    }
  },
  computed: {
    /** 最近常选配置（选择场景只展示包含完整商品数据的记录） */
    recentConfig() {
      return {
        days: this.recentDays,
        limit: this.recentLimit,
        requireRaw: this.recentRequireRaw
      }
    },
    /** 已选商品数量合计（底部胶囊展示；用箭头函数保证回调内 this 指向组件） */
    totalQty() {
      return this.selectedGoods.reduce((sum, goods) => {
        return sum + (this.qtyMap[goods.key] || 0)
      }, 0)
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
        // 每次打开都从第 1 页开始，并清掉上次的滚动位置（最近常选由浏览框架在激活时准备）
        if (this.$store && this.$store.commit) {
          this.$store.commit('goodsFeed/CLEAR_FEED_STATE', 'goods_picker_v2')
        }
      }
    }
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
    /** AI 拍照识别完成：优先使用识别出的商品名称作为搜索词 */
    onOcrOk(res) {
      this.ocrVisible = false
      var text = ((res && (res.name || res.rawText)) || '').trim()
      if (!text) {
        this.$message.warning('未识别到有效商品信息')
        return
      }
      this.keyword = text
      if (this.$refs.browse) this.$refs.browse.search(text)
    }
  }
}
</script>

<style>
/* 新版添加商品弹层页面底色纯白：仅作用于本弹层，不影响其它标准弹层页（mmp-page 默认 #f2f4f7） */
.mmp-page.picker-page-white {
  background: #fff;
}
</style>
