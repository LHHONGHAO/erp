<template>
  <div class="mgf">
    <virtual-waterfall
      ref="waterfall"
      :feed-key="feedKey"
      :loader="loader"
      :reset-key="resetKey"
      :page-size="pageSize"
      :columns="1"
      :fixed-item-height="itemHeight"
      :prepend-items="prependItems"
      :exclude-keys="excludeKeys"
      :empty-text="emptyText">
      <template #item="{ item, deferImage }">
        <div class="mgf-item">
          <mobile-product-card
            :name="item.name"
            :img-url="deferImage ? '' : item.imgUrl"
            :specs="item.specs"
            :items="item.items"
            fill
            @click="$emit('item-click', item)">
            <template #nameExtra>
              <a-tag v-if="item.enableSerialNumber == 1" color="orange">序</a-tag>
              <a-tag v-if="item.enableBatchNumber == 1" color="orange">批</a-tag>
            </template>
            <template #metaExtra>
              <!-- 打印标签：放在信息行内，底边与零售价自然对齐 -->
              <a v-if="showPrint" class="mgf-print" title="打印标签" @click.stop="$emit('print', item)">
                <a-icon type="printer" />
              </a>
              <slot name="metaExtra" :item="item"></slot>
            </template>
          </mobile-product-card>
          <span v-if="selectable" class="mgf-pick" :class="{ 'is-on': isPicked(item) }" @click.stop="$emit('select', item)">
            <a-icon v-if="isPicked(item)" type="check" />
          </span>
        </div>
      </template>
    </virtual-waterfall>
  </div>
</template>

<script>
import VirtualWaterfall from './VirtualWaterfall'
import MobileProductCard from './MobileProductCard'

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
    itemHeight: { type: Number, default: 92 },
    emptyText: { type: String, default: '暂无商品' },
    /** 多选模式：卡片右上角显示勾选圈 */
    selectable: { type: Boolean, default: false },
    /** 已勾选的唯一键（卡片 _key） */
    selectedKeys: { type: Array, default: () => [] },
    /** 不在流中渲染的键（如已选商品已在顶部置顶） */
    excludeKeys: { type: Array, default: () => [] },
    /** 排到流最前面的卡片（如已选商品，随列表一起滚动，不做固定置顶） */
    prependItems: { type: Array, default: () => [] },
    /** 信息行右侧打印标签按钮 */
    showPrint: { type: Boolean, default: false }
  },
  computed: {
    pickedMap() {
      const map = {}
      ;(this.selectedKeys || []).forEach(key => { map[key] = true })
      return map
    }
  },
  methods: {
    isPicked(item) {
      return !!this.pickedMap[item._key]
    },
    /** 供父级手动刷新（回到顶部重新加载第 1 页） */
    reload() {
      if (this.$refs.waterfall) this.$refs.waterfall.reload(true)
    },
    /** 直接替换列表数据（如扫码精确结果），不再走分页加载 */
    replaceItems(rows) {
      if (this.$refs.waterfall) this.$refs.waterfall.replaceItems(rows)
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
  top: 6px;
  right: 8px;
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
</style>
