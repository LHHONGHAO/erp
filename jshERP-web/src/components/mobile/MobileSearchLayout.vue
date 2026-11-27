<template>
  <!-- 手机端标准搜索布局：内容区 + 悬浮搜索胶囊 + 右侧悬浮筛选面板 + 底部弹层 -->
  <div class="msearch-layout" :class="{ 'is-left-hand': leftHand, 'has-scroll-body': scrollBody }">
    <!-- 内容区：商品流（自带滚动）或卡片列表（scrollBody 时由本区滚动） -->
    <div class="msearch-layout__body">
      <slot></slot>
    </div>

    <!-- 右侧悬浮筛选面板：配置驱动标签组 + 自定义表单项 -->
    <div v-if="filterVisible" class="msearch-filter">
      <div class="msearch-filter__head">
        <span class="msearch-filter__title">筛选</span>
        <a class="msearch-filter__close" @click="$emit('filter-clear')">全部关闭</a>
      </div>
      <div class="msearch-filter__body">
        <div v-for="group in filterGroups" :key="group.key" class="msearch-filter__item">
          <span class="msearch-filter__label">{{ group.label }}</span>
          <div class="msearch-chips">
            <span
              v-for="opt in (group.options || [])"
              :key="opt.value"
              class="msearch-chip"
              :class="{ 'is-active': isActive(group, opt) }"
              @click="$emit('filter-change', group.key, opt)">{{ opt.text }}</span>
            <span v-if="!(group.options || []).length" class="msearch-chips__empty">{{ group.emptyText || '暂无数据' }}</span>
          </div>
        </div>
        <!-- 自定义筛选表单（如下拉、输入框） -->
        <slot name="filter-extra"></slot>
      </div>
      <!-- 左右手切换：左手时整页布局水平镜像 -->
      <div v-if="showHandSwitch" class="msearch-filter__foot">
        <span class="msearch-filter__foot-label">布局</span>
        <div class="msearch-hand-switch">
          <span
            class="msearch-hand-switch__item"
            :class="{ 'is-active': leftHand }"
            @click="$emit('update:leftHand', true)">左手</span>
          <span
            class="msearch-hand-switch__item"
            :class="{ 'is-active': !leftHand }"
            @click="$emit('update:leftHand', false)">右手</span>
        </div>
      </div>
    </div>

    <!-- 悬浮搜索胶囊：搜索 / 扫码 / 拍照 / 筛选，整体浮在内容之上；内联输入模式下随键盘上移并钉在键盘上方 -->
    <div ref="floatbar" class="msearch-floatbar" :class="{ 'is-vv-tracking': vvTracking }" :style="floatbarStyle">
      <!-- 临时调试读数：定位真机键盘跟随偏差，问题定位后整行删除 -->
      <div v-if="kbDebug" style="position:absolute;top:-16px;left:0;right:0;font-size:9px;line-height:14px;text-align:center;white-space:nowrap;color:#7a4b00;background:#ffe58f;border-radius:4px;pointer-events:none;">{{ kbDebug }}</div>
      <mobile-search-bar
        ref="searchBar"
        :value="value"
        :placeholder="placeholder"
        :auto-scan-close="autoScanClose"
        :show-filter="showFilter"
        :inline="inlineInput"
        @input="$emit('input', $event)"
        @search="$emit('search', $event)"
        @camera="$emit('camera')"
        @filter="$emit('update:filterVisible', !filterVisible)"
        @scan-result="$emit('scan-result', $event)"
        @focus="onSearchFocus"
        @blur="onSearchBlur">
        <!-- 追加操作置于筛选按钮左侧，与筛选按钮互换位置 -->
        <template #before-filter>
          <slot name="float-extra"></slot>
        </template>
      </mobile-search-bar>
    </div>

    <!-- 底部弹层（如已选商品清单） -->
    <slot name="sheet"></slot>
  </div>
</template>

<script>
import MobileSearchBar from './MobileSearchBar'

/** 胶囊底边与键盘顶边的目标间距（px） */
const KEYBOARD_GAP_PX = 5

/**
 * 手机端标准搜索布局
 * 1. 内容区插槽放商品流或卡片列表，底部预留悬浮胶囊高度；
 * 2. 悬浮搜索胶囊统一搜索 / 扫码 / 拍照 / 筛选入口，右侧可追加操作（float-extra）；
 * 3. 右侧悬浮筛选面板按 filterGroups 渲染标签组，可追加自定义表单（filter-extra）；
 * 4. 筛选面板显隐、左右手通过 .sync 与调用页双向绑定；
 * 5. 视觉样式统一由全局 common.less 的 msearch-* 提供，业务页面无需重复编写。
 */
export default {
  name: 'MobileSearchLayout',
  components: { MobileSearchBar },
  props: {
    /** 搜索关键字，支持 v-model */
    value: { type: String, default: '' },
    placeholder: { type: String, default: '请输入关键字' },
    /** 识别到内容后是否自动关闭扫码页，false 时由调用页决定关闭时机 */
    autoScanClose: { type: Boolean, default: true },
    /** 筛选面板是否可见，支持 .sync */
    filterVisible: { type: Boolean, default: false },
    /** 是否显示搜索胶囊上的筛选按钮（新版商品选择页改用左侧类目栏后隐藏） */
    showFilter: { type: Boolean, default: true },
    /** 筛选标签组：[{ key, label, value, options: [{ text, value }], emptyText }] */
    filterGroups: { type: Array, default: () => [] },
    /** 左右手布局，支持 .sync */
    leftHand: { type: Boolean, default: false },
    /** 是否显示面板底部的左右手切换 */
    showHandSwitch: { type: Boolean, default: false },
    /** 内容区是否由本组件滚动（卡片列表页面用；商品流自带滚动时保持 false） */
    scrollBody: { type: Boolean, default: false },
    /** 内联输入模式：搜索框直接输入，聚焦后悬浮胶囊随键盘上移到键盘上方 */
    inlineInput: { type: Boolean, default: false }
  },
  data() {
    return {
      /** 当前软键盘高度（像素），>0 时悬浮胶囊上移 */
      keyboardHeight: 0,
      /** 搜索输入框是否处于聚焦态（仅内联模式会置位） */
      inputFocused: false,
      /** 视口监听是否已绑定 */
      viewportBound: false,
      /** 聚焦前的窗口高度，用于无 visualViewport 环境估算键盘高度 */
      baselineHeight: 0,
      /** 胶囊相对布局容器底边的偏移（聚焦时记录，用于推算胶囊自然底边） */
      bottomOffset: 0,
      /** 上一次键盘高度：用于区分键盘开合动画与拖动平移 */
      lastKb: 0,
      /** 上一次视觉视口平移量 */
      lastVvTop: 0,
      /** 拖动平移视觉视口期间置位：关闭胶囊过渡，保证逐帧贴合键盘上沿 */
      vvTracking: false,
      /** 临时调试读数（真机键盘跟随定位用，问题定位后删除） */
      kbDebug: '',
      /** rAF 与恢复过渡的定时器句柄 */
      rafPending: false,
      rafId: 0,
      trackTimer: null
    }
  },
  computed: {
    /** 悬浮胶囊随键盘上移的位移：键盘展开时整体上移一个键盘高度 */
    floatbarStyle() {
      // 位移可为负（布局收缩型设备上自然位置偏高，需下移贴到键盘上方 5px）
      if (this.keyboardHeight !== 0) {
        return { transform: 'translateY(' + (-this.keyboardHeight) + 'px)' }
      }
      return {}
    }
  },
  beforeDestroy() {
    this.unbindViewport()
    if (this.trackTimer) clearTimeout(this.trackTimer)
  },
  methods: {
    /** 标签是否选中：空值统一按空串比较 */
    isActive(group, opt) {
      var cur = (group.value === undefined || group.value === null) ? '' : String(group.value)
      var val = (opt.value === undefined || opt.value === null) ? '' : String(opt.value)
      return cur === val
    },
    // 供调用页主动关闭标准扫码页
    closeScanPage() {
      if (this.$refs.searchBar) this.$refs.searchBar.closeScanPage()
    },
    // 供调用页在扫码页内显示提示（如未命中商品）
    setScanTip(text, isError) {
      if (this.$refs.searchBar) this.$refs.searchBar.setScanTip(text, isError)
    },
    /** 内联模式：搜索框聚焦 → 记录基线与胶囊偏移、绑定视口监听、立即测算 */
    onSearchFocus() {
      if (!this.inlineInput) return
      this.inputFocused = true
      this.baselineHeight = window.innerHeight || 0
      this.keyboardHeight = 0
      this.lastKb = 0
      this.lastVvTop = 0
      // blur→focus 同帧时 Vue 的 DOM 更新尚未执行，旧位移还挂在元素上；
      // 不同步清掉会把“带位移的位置”录成自然基线，导致胶囊比键盘高出一个键盘高度
      const bar = this.$refs.floatbar
      if (bar && bar.style) bar.style.transform = 'none'
      this.captureFloatbarOffset()
      this.bindViewport()
      this.updateKeyboard()
    },
    /** 内联模式：搜索框失焦 → 解绑监听、复位胶囊位置 */
    onSearchBlur() {
      this.inputFocused = false
      this.unbindViewport()
      this.keyboardHeight = 0
      this.kbDebug = ''
      this.lastKb = 0
      this.vvTracking = false
      if (this.trackTimer) { clearTimeout(this.trackTimer); this.trackTimer = null }
      if (this.rafPending && window.cancelAnimationFrame) window.cancelAnimationFrame(this.rafId)
      this.rafPending = false
    },
    /** 记录胶囊相对布局容器底边的偏移：聚焦时胶囊无位移，容器无 transform，量取稳定 */
    captureFloatbarOffset() {
      const bar = this.$refs.floatbar
      if (!bar || !this.$el) { this.bottomOffset = 0; return }
      const hostRect = this.$el.getBoundingClientRect()
      const barRect = bar.getBoundingClientRect()
      this.bottomOffset = Math.round(hostRect.bottom - barRect.bottom)
    },
    /**
     * 估算软键盘高度（整条底栏需要上移的像素）。
     * 取两者较大值以兼容两类浏览器：
     *  - iOS：innerHeight 不变、visualViewport.height 收缩 → 用 innerHeight - vv.height；
     *  - Android/部分 WebView：innerHeight 与 vv.height 同步收缩 → 用聚焦前基线 baselineHeight - innerHeight。
     * 不能用 innerHeight - (vv.height + vv.offsetTop)：聚焦底部输入框时 iOS 会滚动页面，offsetTop > 0
     * 会把键盘高度抵消成 0，导致底栏不动、只剩系统原生把输入框滚进可视区（表现为“只有输入框在移动”）。
     */
    getKeyboardHeight() {
      const innerHeight = window.innerHeight || 0
      const vv = window.visualViewport
      let gap = 0
      if (vv) gap = Math.max(gap, innerHeight - vv.height)
      gap = Math.max(gap, (this.baselineHeight || innerHeight) - innerHeight)
      gap = Math.round(gap)
      return gap > 60 ? gap : 0
    },
    /**
     * 重算胶囊位移：把胶囊底边钉在键盘上沿上方 KEYBOARD_GAP_PX 处。
     * 全程使用布局视口坐标直接相减（胶囊与宿主均为 fixed 布局锚定，拖动平移 scrollY/vv.offsetTop 不影响它们）：
     * 键盘上沿（布局坐标）= min(innerHeight, vv.height)，兼容三类设备：
     *  - 布局收缩型（iOS 新版：键盘弹出 innerHeight 收缩到键盘上沿）：min = innerHeight；
     *  - 键盘遮盖型（iOS 旧版：innerHeight 不变、vv.height 收缩）：min = vv.height；
     *  - Android/WebView（两者同步收缩）：min = 两者相等值。
     * 实测证据（真机调试读数）：ih=393 vh=393 vt=404 sy=500——旧公式用 sy+vt+vh 当键盘上沿，
     * 偏差恰为 vt(404)，位移被钳成 0，胶囊只靠系统自动滚动“碰巧”靠近键盘且留大空隙。
     */
    updateKeyboard() {
      if (!this.inputFocused) return
      const kb = this.getKeyboardHeight()
      if (!kb) { this.keyboardHeight = 0; this.lastKb = 0; this.kbDebug = ''; return }
      const innerHeight = window.innerHeight || 0
      const scrollY = window.scrollY || window.pageYOffset || 0
      let shift = kb - KEYBOARD_GAP_PX
      const vv = window.visualViewport
      if (vv && this.$el) {
        // 胶囊自然底边（布局坐标）＝容器实时底边 − 聚焦时记录的容器内偏移（容器无 transform，量取稳定）
        const hostRect = this.$el.getBoundingClientRect()
        const naturalBottom = hostRect.bottom - this.bottomOffset
        // 键盘上沿（布局坐标）＝innerHeight 与 vv.height 取小（见上方方法注释）
        const keyboardTop = Math.min(innerHeight, Math.round(vv.height))
        shift = Math.round(naturalBottom - keyboardTop + KEYBOARD_GAP_PX)
      }
      this.keyboardHeight = Math.round(shift)
      // 临时调试读数：ih=innerHeight vh=vv.height vt=vv.offsetTop sy=scrollY kb=键盘判定 s=位移
      this.kbDebug = vv
        ? `ih:${innerHeight} vh:${Math.round(vv.height)} vt:${Math.round(vv.offsetTop || 0)} sy:${scrollY} kb:${kb} s:${Math.round(shift)}`
        : `ih:${innerHeight} novv kb:${kb} s:${Math.round(shift)}`
      // 键盘高度稳定后 offsetTop 仍在变化＝用户拖动平移视觉视口：关闭过渡逐帧贴合，停止 180ms 后恢复开合动画
      const vvTop = vv ? Math.round(vv.offsetTop || 0) : 0
      const kbStable = this.lastKb === kb
      this.lastKb = kb
      if (kbStable && vvTop !== this.lastVvTop) {
        this.vvTracking = true
        if (this.trackTimer) clearTimeout(this.trackTimer)
        this.trackTimer = setTimeout(() => { this.vvTracking = false }, 180)
      }
      this.lastVvTop = vvTop
    },
    /** 视口事件统一入口：rAF 跳频后重算位移 */
    handleViewportEvent() {
      if (this.rafPending) return
      if (window.requestAnimationFrame) {
        this.rafPending = true
        this.rafId = window.requestAnimationFrame(() => {
          this.rafPending = false
          this.updateKeyboard()
        })
      } else {
        this.updateKeyboard()
      }
    },
    /** 横竖屏切换：容器几何变化，复位后重新记录胶囊偏移再测算 */
    handleOrientationChange() {
      this.keyboardHeight = 0
      this.$nextTick(() => {
        this.captureFloatbarOffset()
        this.updateKeyboard()
      })
    },
    /** 绑定视口变化监听（键盘展开/收起、拖动平移、缩放、横竖屏） */
    bindViewport() {
      if (this.viewportBound) return
      this.viewportBound = true
      window.addEventListener('resize', this.handleViewportEvent)
      window.addEventListener('orientationchange', this.handleOrientationChange)
      if (window.visualViewport) {
        window.visualViewport.addEventListener('resize', this.handleViewportEvent)
        window.visualViewport.addEventListener('scroll', this.handleViewportEvent)
      }
    },
    /** 解绑视口变化监听 */
    unbindViewport() {
      if (!this.viewportBound) return
      this.viewportBound = false
      window.removeEventListener('resize', this.handleViewportEvent)
      window.removeEventListener('orientationchange', this.handleOrientationChange)
      if (window.visualViewport) {
        window.visualViewport.removeEventListener('resize', this.handleViewportEvent)
        window.visualViewport.removeEventListener('scroll', this.handleViewportEvent)
      }
    }
  }
}
</script>
