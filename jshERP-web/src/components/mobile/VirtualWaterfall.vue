<template>
  <div
    class="vw"
    ref="scroller"
    @scroll.passive="onScroll"
    @touchstart="onPullTouchStart"
    @touchmove="onPullTouchMove"
    @touchend="onPullTouchEnd"
    @touchcancel="onPullTouchEnd">
    <!-- 下拉刷新指示条：位于流顶部，高度跟随下拉距离，把下方内容自然推下去 -->
    <div class="vw-pull" :style="{ height: pullY + 'px' }">
      <a-icon v-if="refreshing" type="loading" />
      <span>{{ pullText }}</span>
    </div>
    <!-- 占位容器：按双列最大高度撑开滚动条，卡片绝对定位，节点数与数据量无关 -->
    <div class="vw-phantom" :style="{ height: totalHeight + 'px' }">
      <div
        v-for="cell in visibleCells"
        :key="cell.item._key"
        class="vw-cell"
        :style="cell.style">
        <slot
          name="item"
          :item="cell.item"
          :index="cell.index"
          :imageHeight="cell.imageHeight"
          :deferImage="deferImages"></slot>
      </div>
    </div>

    <!-- 底部加载状态：只在这里使用轻量 a-spin -->
    <div v-if="loading" class="vw-loading">
      <a-spin size="small" />
      <span class="vw-loading__text">加载中...</span>
    </div>
    <div v-else-if="list.length > 0 && !hasMore" class="vw-no-more">没有更多了</div>
    <div v-if="list.length === 0 && !loading" class="vw-empty">{{ emptyText }}</div>
  </div>
</template>

<script>
/** 下拉刷新触发阈值（px） */
const PULL_THRESHOLD = 50
/** 下拉位移最大值（px），阻尼系数 0.4 */
const PULL_MAX = 80

/**
 * 高性能双列虚拟瀑布流（Vue 2.7 / 纯自研，无第三方虚拟列表依赖）
 *
 * 设计要点：
 * 1. 视口裁剪：只渲染可视区域 + 上下 overscan 屏缓冲区内的卡片，DOM 节点数恒定；
 * 2. GPU 定位：卡片 absolute + transform: translate3d，配合 contain: layout style paint；
 * 3. 高度前置计算：优先使用 item.coverWidth/coverHeight 等比换算，缺失时用 defaultRatio；
 * 4. 双列贪心：每次把卡片放到当前较矮的一列，坐标计算完毕后 Object.freeze 冻结，跳过响应式劫持；
 * 5. 无感预加载：距底部小于 2 屏时静默请求下一页；数据不足一屏时自动补载；
 * 6. 快速滑动：单帧位移 > fastScrollThreshold 时暂停渲染新进入视口的图片，停止 120ms 后恢复；
 * 7. 滚动位置：写入 Vuex goodsFeed 模块，keep-alive 页面 activated 时精确还原。
 *
 * 数据接入：通过 loader({ page, pageSize }) => Promise<{ list, total }> 注入，
 * 与具体业务接口解耦（商品信息用 /material/listSku，后续商品浏览页可换成游标接口）。
 */
export default {
  name: 'VirtualWaterfall',
  props: {
    /** 列表唯一标识：用于区分不同页面/不同搜索条件下的滚动位置缓存 */
    feedKey: { type: String, required: true },
    /** 数据加载器，返回 Promise<{ list: Array, total: Number }> */
    loader: { type: Function, required: true },
    /** 变化时重置列表并重新加载（如搜索条件、显示方式变化） */
    resetKey: { type: [String, Number], default: 0 },
    /** 每页条数 */
    pageSize: { type: Number, default: 20 },
    /** 列数：2 为双列瀑布流，1 为单列等高卡片列表 */
    columns: { type: Number, default: 2 },
    /** 固定卡片高度（>0 时忽略图片宽高比，用于单列等高卡片；0 表示按图片比例计算） */
    fixedItemHeight: { type: Number, default: 0 },
    /** 每项高度函数 (item) => number：提供时优先于 fixedItemHeight（如按文字总高计算的单列卡片） */
    itemHeightOf: { type: Function, default: null },
    /** 双列间距、上下间距 */
    gap: { type: Number, default: 8 },
    /** 卡片文字区固定高度（标题 2 行 + 规格 1 行 + 价格行） */
    textHeight: { type: Number, default: 88 },
    /** 图片缺失原始宽高时的宽高比（宽/高），1 表示正方形 */
    defaultRatio: { type: Number, default: 1 },
    /** 可视区域上下各多渲染几屏，1.2 表示上下各 1.2 屏缓冲区 */
    overscan: { type: Number, default: 1.2 },
    /** 单帧滚动位移超过该值判定为快速猛滑 */
    fastScrollThreshold: { type: Number, default: 120 },
    /** 需要从列表中排除的唯一键集合（如已选商品置顶展示，不在流中重复） */
    excludeKeys: { type: Array, default: () => [] },
    /** 排到列表最前面的数据（随列表一起滚动，不做固定置顶） */
    prependItems: { type: Array, default: () => [] },
    emptyText: { type: String, default: '暂无数据' }
  },
  data() {
    return {
      /** 已加载商品（元素已 Object.freeze） */
      list: [],
      /** 置顶数据（已冻结），排在列表最前面并跟随滚动 */
      prependList: [],
      /** 卡片坐标 + 预生成样式（已冻结） */
      cells: [],
      /** 按 top 升序排列的下标（二分查找可视区起点） */
      order: [],
      colHeights: [0, 0],
      totalHeight: 0,
      maxCellHeight: 0,
      listWidth: 0,
      scrollTop: 0,
      viewportHeight: 0,
      page: 0,
      total: 0,
      hasMore: true,
      loading: false,
      deferImages: false,
      rafPending: false,
      reqSeq: 0,
      settleTimer: null,
      saveTimer: null,
      internalResetKey: this.resetKey,
      /** 下拉刷新：当前下拉位移（px），0 表示未下拉 */
      pullY: 0,
      /** 下拉刷新请求进行中 */
      refreshing: false,
      /** 手势跟踪中（touchstart 已记录起点） */
      pullTracking: false,
      pullStartY: 0
    }
  },
  computed: {
    /** 下拉指示文案：刷新中 / 松开刷新 / 下拉刷新 */
    pullText() {
      if (this.refreshing) return '刷新中...'
      return this.pullY >= PULL_THRESHOLD ? '松开刷新' : '下拉刷新'
    },
    /** 可视区 + 缓冲区的卡片：坐标/样式已在排布时冻结，这里只做二分裁剪，零对象分配 */
    visibleCells() {
      const total = this.cells.length
      if (total === 0) return []
      const viewTop = this.scrollTop
      const viewBottom = this.scrollTop + this.viewportHeight
      const margin = this.viewportHeight * this.overscan
      const minTop = viewTop - margin - this.maxCellHeight
      const maxTop = viewBottom + margin
      // 在按 top 升序的下标里二分找到第一个可能可见的卡片
      let low = 0
      let high = this.order.length - 1
      let start = 0
      while (low <= high) {
        const mid = (low + high) >> 1
        if (this.cells[this.order[mid]].top < minTop) {
          low = mid + 1
        } else {
          start = mid
          high = mid - 1
        }
      }
      const result = []
      for (let i = start; i < this.order.length; i++) {
        const cell = this.cells[this.order[i]]
        if (cell.top > maxTop) break
        if (cell.top + cell.height < viewTop - margin) continue
        result.push(cell)
      }
      return result
    }
  },
  watch: {
    resetKey(val) {
      if (val === this.internalResetKey) return
      this.internalResetKey = val
      this.clearFeedState()
      this.reload(true)
    },
    excludeKeys() {
      // 仅重新排布，不重新请求；同时修正因内容变短导致的越界滚动
      this.recalcLayout()
      this.$nextTick(() => {
        const el = this.$refs.scroller
        if (!el) return
        const max = Math.max(0, this.totalHeight - el.clientHeight)
        if (el.scrollTop > max) {
          el.scrollTop = max
          this.scrollTop = max
        }
        this.checkNeedMore()
      })
    },
    prependItems: {
      handler() {
        this.rebuildPrepend()
      },
      immediate: true
    }
  },
  created() {
    // 非响应式：记录上一次通知父级的首个可见项键，键不变不重复 emit
    this._lastSpyKey = undefined
    // 锚点定位令牌：新的定位/回到顶部/重载/销毁会使旧定位失效
    this._navSeq = 0
    // 在途分页请求：锚点定位需要等它结束再判断数据范围
    this._loadPromise = null
    // 最近一次加载是否失败：失败后 hasMore 也会置 false，但不能据此判定「无数据」
    this._loadFailed = false
  },
  mounted() {
    const saved = this.readFeedState()
    this.reload(true)
    if (saved && saved.scrollTop) {
      this.$nextTick(() => {
        // 数据尚未加载到该位置时浏览器会截断，读回真实值避免可视区计算为空
        this.$refs.scroller.scrollTop = saved.scrollTop
        this.scrollTop = this.$refs.scroller.scrollTop
        this.measure()
        this.notifyFirstVisible()
      })
    }
    this.onResize = () => this.measure()
    window.addEventListener('resize', this.onResize)
    // 容器高度变化（如上方置区展开/收起）时重新测量，避免可视区计算偏差
    if (typeof ResizeObserver !== 'undefined' && this.$refs.scroller) {
      this._resizeObserver = new ResizeObserver(() => this.measure())
      this._resizeObserver.observe(this.$refs.scroller)
    }
  },
  activated() {
    // keep-alive 返回：还原离开时的滚动位置，列表数据仍在组件内，无白屏
    const saved = this.readFeedState()
    if (saved && saved.scrollTop && this.$refs.scroller) {
      this.$refs.scroller.scrollTop = saved.scrollTop
      this.scrollTop = this.$refs.scroller.scrollTop
      this.measure()
      this.notifyFirstVisible()
    }
  },
  deactivated() {
    this.saveFeedState()
  },
  beforeDestroy() {
    this._navSeq += 1
    window.removeEventListener('resize', this.onResize)
    if (this._resizeObserver) {
      this._resizeObserver.disconnect()
      this._resizeObserver = null
    }
    clearTimeout(this.settleTimer)
    clearTimeout(this.saveTimer)
    this.saveFeedState()
  },
  methods: {
    /** 读取缓存的滚动状态 */
    readFeedState() {
      const store = this.$store
      if (!store || !store.getters['goodsFeed/feedState']) return null
      return store.getters['goodsFeed/feedState'](this.feedKey)
    },
    /** 保存滚动状态（节流写入） */
    saveFeedState() {
      const store = this.$store
      if (!store || !store.commit) return
      // 直接读取元素当前位置：避免 rAF 节流/惯性滚动导致保存值滞后
      const el = this.$refs.scroller
      if (el) this.scrollTop = el.scrollTop
      store.commit('goodsFeed/SAVE_FEED_STATE', {
        key: this.feedKey,
        scrollTop: this.scrollTop,
        page: this.page,
        colHeights: this.colHeights
      })
    },
    scheduleSaveFeedState() {
      clearTimeout(this.saveTimer)
      this.saveTimer = setTimeout(() => this.saveFeedState(), 200)
    },
    /** 清空某个列表的滚动缓存（条件变化/数据变更后重置） */
    clearFeedState() {
      const store = this.$store
      if (store && store.commit) store.commit('goodsFeed/CLEAR_FEED_STATE', this.feedKey)
    },
    /** 重新加载：清空数据后从第 1 页开始，resetScroll 为 true 时回到顶部 */
    reload(resetScroll) {
      this.reqSeq += 1
      this.list = []
      this.cells = []
      this.order = []
      this.colHeights = [0, 0]
      this.totalHeight = 0
      this.maxCellHeight = 0
      this.page = 0
      this.total = 0
      this.hasMore = true
      this.loading = false
      this.deferImages = false
      this._navSeq += 1
      this._loadPromise = null
      this._loadFailed = false
      if (resetScroll && this.$refs.scroller) {
        this.$refs.scroller.scrollTop = 0
        this.scrollTop = 0
      }
      this.measure()
      return this.loadPage()
    },
    /** 测量容器尺寸并重算布局 */
    measure() {
      const el = this.$refs.scroller
      if (!el) return
      const height = el.clientHeight
      // 容器刚插入（如弹层打开首帧）时高度为 0：下一帧重测，避免按 0 高度裁剪可视区
      if (!height) {
        if (!this._measureRetry) {
          this._measureRetry = true
          requestAnimationFrame(() => {
            this._measureRetry = false
            this.measure()
          })
        }
        return
      }
      this.viewportHeight = height
      const width = el.clientWidth || window.innerWidth
      if (width !== this.listWidth) this.listWidth = width
      this.recalcLayout()
    },
    /** 仅回到顶部（不重新加载，滚动事件会触发联动通知） */
    scrollToTop() {
      // 回到顶部视为新的浏览意图，取消尚未完成的锚点定位
      this._navSeq += 1
      const el = this.$refs.scroller
      if (el) el.scrollTop = 0
    },
    /**
     * 锚点定位：滚动到首个满足 matchFn 的卡片并带到视口顶部，返回 Promise<boolean>。
     * 保留布尔契约供既有调用方使用；需要区分「无匹配/超限/失败」的调用方请用 scrollToItemResult
     */
    scrollToItem(matchFn) {
      return this.scrollToItemResult(matchFn).then(function(result) {
        return result.status === 'found'
      })
    },
    /**
     * 多状态锚点定位（核心实现）：滚动到首个满足 matchFn 的卡片并带到视口顶部。
     * 返回 Promise<{ status }>：
     *   found     已定位；
     *   exhausted 数据已全部取完仍无匹配（可据此判定「该类目无商品」）；
     *   limit     达到分页扫描上限，不代表无匹配；
     *   failed    加载失败；
     *   cancelled 期间发生新的定位/回到顶部/重载/关闭，本次作废。
     * 已加载范围找不到时继续翻页查找（最多再找 30 页），并等待在途请求与布局完成。
     */
    scrollToItemResult(matchFn) {
      const self = this
      const token = ++this._navSeq
      const maxScan = 30
      let failed = false

      function findCell() {
        for (let i = 0; i < self.order.length; i++) {
          const cell = self.cells[self.order[i]]
          if (cell && matchFn(cell.item)) return cell
        }
        return null
      }
      function nextTick() {
        return new Promise(resolve => self.$nextTick(resolve))
      }
      function step(remain) {
        if (token !== self._navSeq) return { status: 'cancelled' }
        const cell = findCell()
        if (cell) {
          const el = self.$refs.scroller
          if (el) el.scrollTop = cell.top
          return { status: 'found' }
        }
        if (failed) return { status: 'failed' }
        // 有在途请求先等它结束，避免并发翻页、也避免读到尚未排布的半成品
        if (self.loading && self._loadPromise) {
          return self._loadPromise.then(function(res) {
            if (res && res.ok === false) failed = true
            return nextTick().then(function() { return step(remain - 1) })
          })
        }
        if (self._loadFailed) return { status: 'failed' }
        if (!self.hasMore) return { status: 'exhausted' }
        if (remain <= 0) return { status: 'limit' }
        return self.loadPage().then(function(res) {
          if (res && res.ok === false) failed = true
          return nextTick().then(function() { return step(remain - 1) })
        })
      }
      return Promise.resolve(step(maxScan))
    },
    /** 置顶数据变化：重新冻结并排布，不重新请求 */
    rebuildPrepend() {
      this.prependList = (this.prependItems || []).map(row => this.normalizeItem(row))
      this.recalcLayout()
    },
    /**
     * 双列贪心排布：卡片高度 = 图片高度（等比换算）+ 文字区固定高度；固定高度时直接使用 fixedItemHeight
     * 坐标与样式在此一次性算好并冻结，滚动帧里的可视区裁剪不再做任何字符串拼接
     */
    recalcLayout() {
      const width = this.listWidth
      if (!width) return
      const gap = this.gap
      const count = Math.max(1, Math.floor(this.columns) || 1)
      const colWidth = Math.max(0, (width - gap * (count - 1)) / count)
      if (colWidth <= 0) return
      const heights = []
      for (let c = 0; c < count; c++) heights.push(0)
      const cells = []
      const excluded = {}
      ;(this.excludeKeys || []).forEach(key => { excluded[key] = true })
      // 置顶数据排最前，列表里同键的数据不重复渲染
      const items = []
      const prependKeys = {}
      this.prependList.forEach(item => {
        if (excluded[item._key] || prependKeys[item._key]) return
        prependKeys[item._key] = true
        items.push(item)
      })
      for (let i = 0; i < this.list.length; i++) {
        const item = this.list[i]
        if (excluded[item._key] || prependKeys[item._key]) continue
        items.push(item)
      }
      let maxCellHeight = 0
      for (let i = 0; i < items.length; i++) {
        const item = items[i]
        const ratio = this.ratioOf(item)
        const imageHeight = Math.round(colWidth / ratio)
        const height = this.itemHeightOf
          ? this.itemHeightOf(item)
          : (this.fixedItemHeight > 0 ? this.fixedItemHeight : imageHeight + this.textHeight)
        // 贪心：放进当前最矮的一列
        let col = 0
        for (let c = 1; c < count; c++) {
          if (heights[c] < heights[col]) col = c
        }
        const left = col * (colWidth + gap)
        const top = heights[col]
        heights[col] = top + height + gap
        if (height > maxCellHeight) maxCellHeight = height
        cells.push({
          index: i,
          item: item,
          left: left,
          top: top,
          width: colWidth,
          height: height,
          imageHeight: imageHeight,
          // 样式与坐标同生命周期：只在排布时生成一次
          style: Object.freeze({
            width: colWidth + 'px',
            height: height + 'px',
            transform: 'translate3d(' + left + 'px,' + top + 'px,0)'
          })
        })
      }
      const order = cells.map((cell, index) => index).sort((a, b) => cells[a].top - cells[b].top)
      this.colHeights = heights
      this.maxCellHeight = maxCellHeight
      this.totalHeight = Math.max.apply(null, heights)
      this.order = Object.freeze(order)
      this.cells = Object.freeze(cells)
    },
    /** 图片宽高比：优先接口下发的原始宽高，缺失时用默认比例 */
    ratioOf(item) {
      const w = Number(item.coverWidth)
      const h = Number(item.coverHeight)
      if (w > 0 && h > 0) {
        const ratio = w / h
        return Math.min(2.2, Math.max(0.45, ratio))
      }
      return this.defaultRatio || 1
    },
    /**
     * 加载下一页并增量计算坐标。
     * loader 可返回 { list, total, hasMore }：提供布尔 hasMore 时按接口原始进度判定，
     * 避免上层去重后展示条数变少被误判为「已到底」；未提供时沿用展示条数判定。
     * 返回 Promise<{ ok }>，ok=false 表示本次请求失败（供锚点定位区分失败与真正耗尽）
     */
    loadPage() {
      if (this.loading) return this._loadPromise || Promise.resolve({ ok: true })
      if (!this.hasMore) return Promise.resolve({ ok: true })
      this.loading = true
      const nextPage = this.page + 1
      // 请求序号：重置后丢弃旧条件下的返回，避免覆盖新列表
      const seq = ++this.reqSeq
      const promise = Promise.resolve(this.loader({ page: nextPage, pageSize: this.pageSize }))
        .then(res => {
          if (seq !== this.reqSeq) return { ok: true }
          const rows = (res && res.list) || []
          const total = Number(res && res.total) || 0
          const items = rows.map(row => this.normalizeItem(row))
          this.total = total || this.total
          this.page = nextPage
          this._loadFailed = false
          this.list = this.page === 1 ? items : this.list.concat(items)
          if (res && typeof res.hasMore === 'boolean') {
            this.hasMore = res.hasMore
          } else {
            // 接口没有更多数据（或返回空页）时停止
            this.hasMore = items.length > 0 && this.list.length < this.total
          }
          this.$nextTick(() => {
            this.measure()
            this.notifyFirstVisible()
          })
          return { ok: true }
        })
        .catch(() => {
          // 请求失败：停止本条件下的自动补载，避免空列表反复请求；重置条件后会重新加载
          if (seq !== this.reqSeq) return { ok: true }
          this._loadFailed = true
          this.hasMore = false
          return { ok: false }
        })
        .finally(() => {
          if (seq !== this.reqSeq) return
          this.loading = false
          this._loadPromise = null
          // 数据不足一屏时继续补载（放在 loading 复位之后，避免补载被拦截）
          this.$nextTick(() => this.checkNeedMore())
        })
      this._loadPromise = promise
      return promise
    },
    /** 冻结商品对象并补齐虚拟渲染所需字段，避免 Vue 2 深度劫持 */
    normalizeItem(row) {
      const key = String(row.skuRowKey || row._key || row.id || row.meId || Math.random())
      const item = Object.assign({}, row, { _key: key })
      return Object.freeze(item)
    },
    /** 直接替换列表数据（如扫码精确结果），不再走分页加载 */
    replaceItems(rows) {
      this.reqSeq += 1
      this._navSeq += 1
      this._loadPromise = null
      this._loadFailed = false
      const list = (rows || []).map(row => this.normalizeItem(row))
      this.list = list
      this.total = list.length
      this.page = 1
      this.hasMore = false
      this.loading = false
      this.deferImages = false
      this.$nextTick(() => {
        this.measure()
        if (this.$refs.scroller) {
          this.$refs.scroller.scrollTop = 0
          this.scrollTop = 0
        }
        this.notifyFirstVisible()
      })
    },
    /** 数据不足一屏时继续补载，避免出现无法滚动的「半屏」 */
    checkNeedMore() {
      if (!this.hasMore || this.loading) return
      const el = this.$refs.scroller
      if (!el) return
      // 容器尚未布局时不判断：避免按 0 高度得出「已到底」而停止补载
      const viewport = el.clientHeight || this.viewportHeight
      if (!viewport) return
      const distance = this.totalHeight - (el.scrollTop + viewport)
      if (distance < viewport * 2) this.loadPage()
    },
    /**
     * 通知父级视口顶部的首个可见商品（scroll-spy）：
     * 取按 top 升序里第一个底边越过视口顶部的卡片，键变化才 emit，滚动帧里近乎零开销
     */
    notifyFirstVisible() {
      const cells = this.cells
      const order = this.order
      const viewTop = this.scrollTop
      let key = ''
      let item = null
      for (let i = 0; i < order.length; i++) {
        const cell = cells[order[i]]
        if (cell.top + cell.height > viewTop) {
          item = cell.item
          key = item._key
          break
        }
      }
      if (key === this._lastSpyKey) return
      this._lastSpyKey = key
      this.$emit('first-visible', item)
    },
    /** 下拉刷新手势：仅在列表顶部（含空列表）向下拖动时生效，超过阈值松手即重载第 1 页 */
    onPullTouchStart(event) {
      if (this.refreshing) return
      this.pullTracking = true
      this.pullStartY = event.touches[0].clientY
    },
    onPullTouchMove(event) {
      if (!this.pullTracking || this.refreshing) return
      const el = this.$refs.scroller
      if (!el || el.scrollTop > 0) return
      const dy = event.touches[0].clientY - this.pullStartY
      if (dy <= 0) {
        this.pullY = 0
        return
      }
      // 阻尼跟随，并拦住浏览器自身的下拉回弹，保证指示条可见
      this.pullY = Math.min(Math.round(dy * 0.4), PULL_MAX)
      if (event.cancelable) event.preventDefault()
    },
    onPullTouchEnd() {
      if (!this.pullTracking) return
      this.pullTracking = false
      if (this.pullY >= PULL_THRESHOLD) {
        this.refreshing = true
        this.pullY = 40
        Promise.resolve(this.reload(true)).finally(() => {
          this.refreshing = false
          this.pullY = 0
        })
      } else {
        this.pullY = 0
      }
    },
    onScroll() {
      if (this.rafPending) return
      this.rafPending = true
      requestAnimationFrame(() => {
        this.rafPending = false
        const el = this.$refs.scroller
        if (!el) return
        const top = el.scrollTop
        const delta = Math.abs(top - this.scrollTop)
        this.scrollTop = top
        this.notifyFirstVisible()
        // 快速猛滑期间暂停图片渲染，停止后再恢复，避免并发请求阻塞
        if (delta > this.fastScrollThreshold) {
          this.deferImages = true
          clearTimeout(this.settleTimer)
          this.settleTimer = setTimeout(() => { this.deferImages = false }, 120)
        }
        this.checkNeedMore()
        this.scheduleSaveFeedState()
      })
    }
  }
}
</script>

<style lang="less" scoped>
.vw {
  position: relative;
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  box-sizing: border-box;
}
.vw-phantom {
  position: relative;
  width: 100%;
}
/* 下拉刷新指示条：高度随手势变化，内容底对齐 */
.vw-pull {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 6px;
  overflow: hidden;
  flex-shrink: 0;
  padding-bottom: 6px;
  font-size: 13px;
  color: #9aa3ad;
}
/* 可视卡片：绝对定位 + GPU 偏移，contain 隔离重绘 */
.vw-cell {
  position: absolute;
  top: 0;
  left: 0;
  will-change: transform;
  contain: layout style paint;
  -webkit-backface-visibility: hidden;
  backface-visibility: hidden;
}
.vw-loading,
.vw-no-more,
.vw-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px 0 20px;
  font-size: 13px;
  color: #9aa3ad;
}
.vw-empty {
  padding-top: 48px;
  flex-direction: column;
}
.vw-loading__text {
  margin-left: 6px;
}
</style>
