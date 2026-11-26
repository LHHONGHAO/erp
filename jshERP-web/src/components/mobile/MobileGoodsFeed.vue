<template>
  <div class="mgf">
    <virtual-waterfall
      ref="waterfall"
      :feed-key="feedKey"
      :loader="loader"
      :reset-key="resetKey"
      :page-size="pageSize"
      :columns="1"
      :gap="gap"
      :item-height-of="heightOf"
      :prepend-items="prependItems"
      :exclude-keys="excludeKeys"
      :empty-text="emptyText"
      @first-visible="$emit('first-visible', $event)">
      <template #item="{ item, deferImage }">
        <div class="mgf-item" :class="{ 'mgf-item--swipe': swipeEdit }">
          <!-- 卡片右滑后露出的编辑按钮（位于卡片左侧） -->
          <div v-if="swipeEdit" class="mgf-swipe-btn" @click.stop="emitEdit(item)">
            <a-icon type="edit" />
            <span>编辑</span>
          </div>
          <div
            class="mgf-swipe-content"
            :class="{ 'is-dragging': dragKey === item._key }"
            :style="swipeStyle(item)"
            @touchstart="onTouchStart($event, item)"
            @touchmove="onTouchMove($event, item)"
            @touchend="onTouchEnd(item)"
            @touchcancel="onTouchEnd(item)">
            <mobile-product-card
              :name="item.name"
              :img-url="deferImage ? '' : item.imgUrl"
              :specs="item.specs"
              :items="item.items"
              :price="item.price"
              fill
              @click="onCardClick(item)">
              <template #nameExtra>
                <a-tag v-if="item.enableSerialNumber == 1" color="orange">序</a-tag>
                <a-tag v-if="item.enableBatchNumber == 1" color="orange">批</a-tag>
              </template>
              <template #metaExtra>
                <!-- 打印标签：放在信息行内 -->
                <a v-if="showPrint" class="mgf-print" title="打印标签" @click.stop="$emit('print', item)">
                  <a-icon type="printer" />
                </a>
                <slot name="metaExtra" :item="item"></slot>
              </template>
            </mobile-product-card>
            <span v-if="selectable" class="mgf-pick" :class="{ 'is-on': isPicked(item) }" @click.stop="$emit('select', item)">
              <a-icon v-if="isPicked(item)" type="check" />
            </span>
            <!-- 数量模式：卡片右下角步进器（减号 + 数量 + 加号都显示），加减按钮分别通知父级数量 -1/+1；点卡片整体也可 +1 -->
            <span v-else-if="qtyMode && qtyOf(item) > 0" class="mgf-step">
              <span class="mgf-step__minus" @click.stop="$emit('minus', item)"></span>
              <span class="mgf-step__num">{{ qtyOf(item) }}</span>
              <span class="mgf-step__plus" @click.stop="$emit('plus', item)"></span>
            </span>
          </div>
        </div>
      </template>
    </virtual-waterfall>
  </div>
</template>

<script>
import VirtualWaterfall from './VirtualWaterfall'
import MobileProductCard from './MobileProductCard'
import { cardHeightOf } from './goodsFeedItem'

/** 右滑露出的编辑按钮宽度 */
const SWIPE_WIDTH = 68

/**
 * 标准商品流模块：卡片布局 + 虚拟滚动 + 分页刷新
 * 商品信息页与添加商品弹层共用，页面只需提供 loader({ page, pageSize }) 与条件重置键。
 */
export default {
  name: 'MobileGoodsFeed',
  components: { VirtualWaterfall, MobileProductCard },
  props: {
    feedKey: { type: String, required: true },
    loader: { type: Function, required: true },
    resetKey: { type: [String, Number], default: 0 },
    pageSize: { type: Number, default: 20 },
    emptyText: { type: String, default: '暂无商品' },
    /** 多选模式：商品图片左上角显示勾选圈（左手模式镜像到图片右上角） */
    selectable: { type: Boolean, default: false },
    /** 已勾选的唯一键（卡片 _key） */
    selectedKeys: { type: Array, default: () => [] },
    /** 数量模式：数量大于 0 的卡片右下角显示减号/加号步进器，配合卡片点击实现加/减 1 */
    qtyMode: { type: Boolean, default: false },
    /** 各卡片当前数量（key → 数量），qtyMode 下用于渲染步进器 */
    qtyMap: { type: Object, default: () => ({}) },
    /** 不在流中渲染的键（如已选商品已在顶部置顶） */
    excludeKeys: { type: Array, default: () => [] },
    /** 排到流最前面的卡片（如已选商品，随列表一起滚动，不做固定置顶） */
    prependItems: { type: Array, default: () => [] },
    /** 信息行右侧打印标签按钮 */
    showPrint: { type: Boolean, default: false },
    /** 卡片右滑露出编辑按钮 */
    swipeEdit: { type: Boolean, default: false },
    /** 卡片上下间距（px），透传给虚拟瀑布流 */
    gap: { type: Number, default: 8 }
  },
  data() {
    return {
      /** 当前展开编辑按钮的卡片键 */
      swipeOpenKey: '',
      /** 正在拖动或已展开的卡片键及位移 */
      dragKey: '',
      dragOffset: 0,
      /** 触摸手势起点与方向 */
      touchStartX: 0,
      touchStartY: 0,
      touchAxis: '',
      touchItemKey: ''
    }
  },
  computed: {
    pickedMap() {
      const map = {}
      ;(this.selectedKeys || []).forEach(key => { map[key] = true })
      return map
    }
  },
  methods: {
    /** 卡片高度：按文字总高逐项计算（名称+规格行+信息行+上下内边距），图片正方形跟随该高度 */
    heightOf(item) {
      return cardHeightOf(item)
    },
    isPicked(item) {
      return !!this.pickedMap[item._key]
    },
    /** 数量模式下卡片当前数量 */
    qtyOf(item) {
      return this.qtyMap[item._key] || 0
    },
    /** 供父级手动刷新（回到顶部重新加载第 1 页） */
    reload() {
      if (this.$refs.waterfall) this.$refs.waterfall.reload(true)
    },
    /** 条件微调（如规格标签切换）：重新加载但保持当前滚动位置，从原停留处继续浏览 */
    reloadKeepPosition() {
      if (this.$refs.waterfall) this.$refs.waterfall.reload(false)
    },
    /** 仅回到顶部（不重新加载数据，滚动联动会随滚动事件自动更新） */
    scrollToTop() {
      if (this.$refs.waterfall) this.$refs.waterfall.scrollToTop()
    },
    /** 规格标签锚点：滚动到首个匹配的卡片（目标未加载时继续翻页查找） */
    scrollToItem(matchFn) {
      if (this.$refs.waterfall) return this.$refs.waterfall.scrollToItem(matchFn)
      return Promise.resolve(false)
    },
    /** 直接替换列表数据（如扫码精确结果），不再走分页加载 */
    replaceItems(rows) {
      if (this.$refs.waterfall) this.$refs.waterfall.replaceItems(rows)
    },
    /** 卡片位移：拖动中实时跟随，松手后停在展开/收起位置 */
    swipeStyle(item) {
      if (!this.swipeEdit) return null
      const offset = this.dragKey === item._key
        ? this.dragOffset
        : (this.swipeOpenKey === item._key ? SWIPE_WIDTH : 0)
      return { transform: 'translateX(' + offset + 'px)' }
    },
    onTouchStart(event, item) {
      if (!this.swipeEdit) return
      const touch = event.touches[0]
      this.touchStartX = touch.clientX
      this.touchStartY = touch.clientY
      this.touchAxis = ''
      this.touchItemKey = item._key
      this.dragKey = ''
    },
    onTouchMove(event, item) {
      if (!this.swipeEdit || this.touchItemKey !== item._key) return
      const touch = event.touches[0]
      const dx = touch.clientX - this.touchStartX
      const dy = touch.clientY - this.touchStartY
      // 首次移动判断手势方向，纵向移动交给列表滚动
      if (!this.touchAxis) {
        if (Math.abs(dx) < 6 && Math.abs(dy) < 6) return
        this.touchAxis = Math.abs(dx) > Math.abs(dy) ? 'x' : 'y'
      }
      if (this.touchAxis !== 'x') return
      event.preventDefault()
      const base = this.swipeOpenKey === item._key ? SWIPE_WIDTH : 0
      this.dragKey = item._key
      this.dragOffset = Math.max(0, Math.min(SWIPE_WIDTH, base + dx))
    },
    onTouchEnd(item) {
      if (!this.swipeEdit || this.touchItemKey !== item._key) return
      if (this.touchAxis === 'x') {
        this.swipeOpenKey = this.dragOffset > SWIPE_WIDTH / 2 ? item._key : ''
      }
      this.touchAxis = ''
      this.touchItemKey = ''
      this.dragKey = ''
    },
    /** 卡片点击：有展开按钮时先收起，避免误触 */
    onCardClick(item) {
      if (this.swipeOpenKey) {
        this.swipeOpenKey = ''
        return
      }
      this.$emit('item-click', item)
    },
    /** 点击编辑按钮：收起按钮并通知调用页进入编辑 */
    emitEdit(item) {
      this.swipeOpenKey = ''
      this.$emit('edit', item)
    }
  }
}
</script>

<style scoped>
.mgf {
  height: 100%;
  min-height: 0;
}
.mgf-item {
  position: relative;
  height: 100%;
}
/* 右滑编辑：卡片可横向拖动，左侧露出编辑按钮 */
.mgf-item--swipe {
  overflow: hidden;
  border-radius: 14px;
}
.mgf-swipe-btn {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 68px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #1890ff;
  color: #fff;
  border-radius: 14px 0 0 14px;
  font-size: 12px;
}
.mgf-swipe-btn .anticon {
  font-size: 18px;
  margin-bottom: 2px;
}
.mgf-swipe-content {
  position: relative;
  z-index: 1;
  height: 100%;
  transition: transform 0.2s ease;
}
.mgf-swipe-content.is-dragging {
  transition: none;
}
.mgf-print {
  display: inline-flex;
  align-items: center;
  flex: none;
  font-size: 16px;
  line-height: 16px;
  color: #1890ff;
}
.mgf-pick {
  position: absolute;
  /* 图片贴边后占据卡片左上角（无边框无内边距）：圆圈退 2px，左上弧与卡片圆角贴合 */
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1px solid #d9d9d9;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #fff;
}
.mgf-pick.is-on {
  border-color: #1890ff;
  background: #1890ff;
}
/* 数量模式：步进器（减号+数量+加号）贴卡片右下角，与卡片右缘内边距对齐 */
.mgf-step {
  position: absolute;
  right: 12px;
  bottom: 3px;
  display: flex;
  align-items: center;
  gap: 6px;
}
/* 步进器加减按钮：16px 橙色描边圆环，细横线为减号、加竖线为加号 */
.mgf-step__minus,
.mgf-step__plus {
  position: relative;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1.5px solid #FF7A00;
  background: #fff;
  box-sizing: border-box;
}
.mgf-step__minus::after,
.mgf-step__plus::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 8px;
  height: 1.5px;
  background: #FF7A00;
  border-radius: 1px;
}
.mgf-step__plus::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 1.5px;
  height: 8px;
  background: #FF7A00;
  border-radius: 1px;
}
.mgf-step__num {
  min-width: 20px;
  text-align: center;
  font-size: 17px;
  line-height: 20px;
  font-weight: 600;
  color: #111827;
}
</style>
