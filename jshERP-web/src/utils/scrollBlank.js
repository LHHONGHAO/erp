/**
 * 手机端页面上下滑动空白检测
 *
 * 适用场景：固定顶栏（全局顶栏 49px）+ 固定底栏 + 整页滚动的手机端页面。
 * 规则（以地址识别页「图一」为准）：
 * 1. 内容区第一个元素到顶栏底边的空白为基准间距（输入卡片顶边到顶栏底边的距离）；
 * 2. 上滑到底时，最后一个元素到底栏顶边的空白与基准间距一致，页面随即停止上滑；
 * 3. 内容不足一屏时不额外占位，避免出现可滚动的空白。
 *
 * 生效方式：
 * 1. 全局自动（默认）：main.js 调用 installScrollBlank(router)，所有页面在加载、路由切换、
 *    尺寸变化、内容变化、滚动停止后自动检测，页面无需任何接入代码；
 * 2. 显式接入（可选）：页面使用 scrollBlankMixin 并给元素加约定 ref：
 *   scrollBlankFirst      内容区第一个元素
 *   scrollBlankLast       内容区最后一个元素
 *   scrollBlankBottomBar  固定底栏
 *   scrollBlankSpacer     底部占位元素（高度由检测结果动态设置）
 * 顶栏缺省取全局顶栏 .ant-layout-header，也可通过 scrollBlankTopBar ref 指定。
 */
export const SCROLL_BLANK_MOBILE_MAX_WIDTH = 768

/** 当前可视高度写入的 CSS 变量名，页面高度用它替代 vh */
export const SCROLL_BLANK_VIEWPORT_VAR = '--scroll-blank-vh'

const TOLERANCE = 1

function pageScrollTop() {
  return window.pageYOffset || document.documentElement.scrollTop || (document.body && document.body.scrollTop) || 0
}

function docTop(el) {
  return el.getBoundingClientRect().top + pageScrollTop()
}

function docBottom(el) {
  return el.getBoundingClientRect().bottom + pageScrollTop()
}

function viewportHeight() {
  return window.innerHeight || document.documentElement.clientHeight || 0
}

function pageHeight() {
  const bodyHeight = document.body ? document.body.scrollHeight : 0
  return Math.max(document.documentElement.scrollHeight, bodyHeight)
}

/** 顶栏：优先取可见高度大于 0 的全局顶栏，兼容 .ant-layout-header 与 .header 两种渲染方式 */
function findTopBar() {
  const list = document.querySelectorAll('.ant-layout-header, .header')
  for (let i = 0; i < list.length; i++) {
    if (list[i].offsetHeight > 0) return list[i]
  }
  return null
}

/** 两次检测结果是否一致，避免 updated 中重复赋值触发循环更新 */
function isSameBlankInfo(a, b) {
  if (!a || !b) return false
  return a.topGap === b.topGap &&
    a.targetGap === b.targetGap &&
    a.bottomGap === b.bottomGap &&
    a.spacerHeight === b.spacerHeight &&
    a.contentOverflow === b.contentOverflow &&
    a.balanced === b.balanced
}

/**
 * 把当前可视高度写入 CSS 变量 --scroll-blank-vh
 * 部分移动端浏览器（如 iOS Safari）的 100vh 是地址栏收起后的高度，比当前可视区大，会多出可滚动空白；
 * innerHeight 在各系统中都是当前可视高度，故用它给页面高度赋值，不依赖 dvh 等新特性。
 * @returns {number|null} 手机端返回可视高度，非手机端返回 null（并清除变量）
 */
export function syncScrollBlankViewport() {
  const root = document.documentElement
  if (window.innerWidth > SCROLL_BLANK_MOBILE_MAX_WIDTH) {
    root.style.removeProperty(SCROLL_BLANK_VIEWPORT_VAR)
    return null
  }
  const height = viewportHeight()
  root.style.setProperty(SCROLL_BLANK_VIEWPORT_VAR, height + 'px')
  return height
}

/**
 * 检测并修正页面上下空白
 * @param {Object} options
 * @param {HTMLElement} options.first       内容区第一个元素
 * @param {HTMLElement} options.last        内容区最后一个元素
 * @param {HTMLElement} [options.bottomBar] 固定底栏，缺省按无底栏处理
 * @param {HTMLElement} [options.topBar]    固定顶栏，缺省取 .ant-layout-header
 * @param {HTMLElement} [options.spacer]    底部占位元素，传入时按检测结果自动设置高度
 * @param {number} [options.gap]            指定基准间距，缺省取首元素上方空白
 * @returns {Object|null} 检测结果
 */
export function detectScrollBlank(options) {
  const opts = options || {}
  const first = opts.first
  const last = opts.last
  const bottomBar = opts.bottomBar || null
  const topBar = opts.topBar || findTopBar()
  const spacer = opts.spacer

  if (!first || !last || !topBar) return null
  const barHeight = bottomBar ? bottomBar.offsetHeight : 0
  const topBarHeight = topBar.offsetHeight
  if (!topBarHeight) return null

  // 基准间距：首元素顶边到顶栏底边（图一：输入卡片顶边到顶栏底边）
  const topGap = Math.max(0, Math.round(docTop(first) - topBarHeight))
  const targetGap = opts.gap == null ? topGap : Math.max(0, Math.round(opts.gap))

  // 末元素底边到文档底部的空白（剔除占位元素自身高度，得到内容自然高度）
  const lastBottom = docBottom(last)
  const spacerHeight = spacer ? spacer.offsetHeight : 0
  const naturalHeight = pageHeight() - spacerHeight
  const contentOverflow = naturalHeight - viewportHeight() > TOLERANCE

  // 内容超出一屏时才占位：使上滑到底后末元素到底栏的空白等于基准间距
  let nextSpacerHeight = 0
  if (contentOverflow) {
    const naturalBottomGap = naturalHeight - lastBottom
    nextSpacerHeight = Math.max(0, Math.round(barHeight + targetGap - naturalBottomGap))
  }
  if (spacer && Math.abs(nextSpacerHeight - spacerHeight) > TOLERANCE) {
    spacer.style.height = nextSpacerHeight + 'px'
  }

  // 占位生效后重新检测：上滑到底时末元素到底栏的距离
  const bottomGap = Math.round(pageHeight() - lastBottom - barHeight)
  return {
    topGap,
    targetGap,
    bottomGap,
    spacerHeight: nextSpacerHeight,
    contentOverflow,
    balanced: !contentOverflow || Math.abs(bottomGap - targetGap) <= TOLERANCE
  }
}

/* ==================== 全局自动检测 ==================== */

/** 显式接入 mixin 的页面数量，>0 时全局自动模式让位给 mixin，避免重复占位 */
let mixinDepth = 0
let autoState = null

/* ==================== 键盘状态：展开期间暂停留白刷新 ==================== */

/** 键盘判定阈值：可视区高度比窗口高度少这么多即视为键盘展开 */
const KEYBOARD_MIN_GAP = 120
/** 键盘是否展开，展开期间不做任何留白检测，收起后再统一刷新一次 */
let keyboardOpen = false
let keyboardTimer = null
/** mixin 页面的刷新函数集合，键盘收起后统一回调 */
const keyboardMixinRefreshers = []
let keyboardWatchInstalled = false

/** 当前键盘是否处于展开状态（供页面判断是否跳过自身滚动逻辑） */
export function isScrollBlankKeyboardOpen() {
  return keyboardOpen
}

function detectKeyboardOpen() {
  const vv = window.visualViewport
  if (!vv) return false
  const inner = window.innerHeight || 0
  return !!inner && inner - vv.height > KEYBOARD_MIN_GAP
}

function onKeyboardViewportChange() {
  if (keyboardTimer) clearTimeout(keyboardTimer)
  keyboardTimer = setTimeout(function () {
    keyboardTimer = null
    const open = detectKeyboardOpen()
    if (open === keyboardOpen) return
    keyboardOpen = open
    if (open) {
      // 键盘弹出动画期间视口会连续抖动，丢弃待执行的检测
      if (autoState && autoState.timer) {
        clearTimeout(autoState.timer)
        autoState.timer = null
      }
      return
    }
    // 键盘收起：恢复可视高度并统一刷新一次
    syncScrollBlankViewport()
    queueAutoRefresh(0)
    keyboardMixinRefreshers.slice().forEach(function (fn) { fn() })
  }, 120)
}

/** 监听可视视口变化来识别键盘展开/收起（仅 visualViewport 能感知软键盘） */
function watchKeyboard() {
  if (keyboardWatchInstalled) return
  const vv = window.visualViewport
  if (!vv) return
  keyboardWatchInstalled = true
  vv.addEventListener('resize', onKeyboardViewportChange)
  vv.addEventListener('scroll', onKeyboardViewportChange)
}

function isMobileView() {
  return window.innerWidth <= SCROLL_BLANK_MOBILE_MAX_WIDTH
}

function isElementVisible(el) {
  if (!el || el.nodeType !== 1) return false
  if (!el.offsetWidth && !el.offsetHeight) return false
  const style = window.getComputedStyle(el)
  return style.position !== 'fixed' && style.display !== 'none' && style.visibility !== 'hidden'
}

function visibleChildren(root, exclude) {
  const list = []
  const children = root.children || []
  for (let i = 0; i < children.length; i++) {
    const el = children[i]
    if (el === exclude) continue
    if (isElementVisible(el)) list.push(el)
  }
  return list
}

function findContentRoot() {
  return document.querySelector('.ant-layout-content')
}

/** 逐层下钻只有单个可见子元素的包裹层，定位真正的页面内容容器 */
function resolveContainer() {
  let root = findContentRoot()
  if (!root) return null
  const spacer = autoState && autoState.spacer
  for (let i = 0; i < 4; i++) {
    const kids = visibleChildren(root, spacer)
    if (kids.length !== 1) break
    root = kids[0]
  }
  return root
}

/** 在页面内查找贴在可视区底部的固定定位元素，作为底栏 */
function findFixedBottomBar(root) {
  const viewport = viewportHeight()
  const queue = root ? [root] : []
  const spacer = autoState && autoState.spacer
  let visited = 0
  let best = null
  while (queue.length && visited < 400) {
    const node = queue.shift()
    visited++
    const children = node.children || []
    for (let i = 0; i < children.length; i++) {
      const el = children[i]
      if (el === spacer) continue
      const style = window.getComputedStyle(el)
      if (style.position === 'fixed') {
        const rect = el.getBoundingClientRect()
        if (rect.height > 0 && Math.abs(rect.bottom - viewport) <= 2) {
          if (!best || rect.height > best.height) best = { el: el, height: rect.height }
        }
      }
      if (el.children && el.children.length) queue.push(el)
    }
  }
  return best ? best.el : null
}

/** 全局模式使用的占位元素：由模块创建、插入内容容器末尾 */
function ensureAutoSpacer(container) {
  const state = autoState
  if (state.spacer && state.spacer.parentNode === container) return state.spacer
  if (state.spacer && state.spacer.parentNode) state.spacer.parentNode.removeChild(state.spacer)
  const spacer = document.createElement('div')
  spacer.setAttribute('data-scroll-blank-spacer', 'true')
  spacer.style.cssText = 'height:0;margin:0;padding:0;border:0;pointer-events:none;'
  container.appendChild(spacer)
  state.spacer = spacer
  return spacer
}

/** 仅当观察目标变化时重建监听，避免 ResizeObserver 首次回调造成循环 */
function observeAuto(container, first, last) {
  const state = autoState
  const observed = state.observed
  if (observed && observed.container === container && observed.first === first && observed.last === last) return
  if (state.ro) { state.ro.disconnect(); state.ro = null }
  if (state.mo) { state.mo.disconnect(); state.mo = null }
  if (typeof ResizeObserver !== 'undefined') {
    state.ro = new ResizeObserver(function () { queueAutoRefresh(60) })
    state.ro.observe(container)
    state.ro.observe(first)
    state.ro.observe(last)
  }
  if (typeof MutationObserver !== 'undefined') {
    state.mo = new MutationObserver(function () { queueAutoRefresh(60) })
    state.mo.observe(container, { childList: true, subtree: true })
  }
  state.observed = { container: container, first: first, last: last }
}

function teardownAuto() {
  const state = autoState
  if (!state) return
  if (state.ro) { state.ro.disconnect(); state.ro = null }
  if (state.mo) { state.mo.disconnect(); state.mo = null }
  if (state.spacer && state.spacer.parentNode) state.spacer.parentNode.removeChild(state.spacer)
  state.spacer = null
  state.container = null
  state.observed = null
  state.info = null
}

function queueAutoRefresh(delay) {
  if (keyboardOpen) return
  if (!autoState || autoState.timer) return
  autoState.timer = setTimeout(function () {
    autoState.timer = null
    refreshAutoScrollBlank()
  }, delay || 0)
}

/**
 * 检测当前页面空白并修正底部占位（全局自动模式）
 * 不需要页面添加任何 ref：自动定位内容容器、首尾元素与贴底固定栏
 * @returns {Object|null} 检测结果
 */
export function refreshAutoScrollBlank() {
  const state = autoState
  if (!state || state.busy) return null
  // 键盘展开期间暂停检测，收起后由键盘监听统一刷新
  if (keyboardOpen) return null
  // 页面已显式接入 mixin，交由 mixin 处理，避免重复占位
  if (mixinDepth > 0) return null
  if (!isMobileView()) { teardownAuto(); syncScrollBlankViewport(); return null }
  if (syncScrollBlankViewport() == null) { teardownAuto(); return null }

  const container = resolveContainer()
  if (!container) { teardownAuto(); return null }
  const kids = visibleChildren(container, state.spacer)
  if (!kids.length) { teardownAuto(); return null }

  const first = kids[0]
  const last = kids[kids.length - 1]
  // 底栏可能位于页面容器外（仍在内容区内），从内容区根节点开始查找
  const bottomBar = findFixedBottomBar(findContentRoot() || container)
  const spacer = ensureAutoSpacer(container)
  state.busy = true
  try {
    const info = detectScrollBlank({ first: first, last: last, bottomBar: bottomBar, spacer: spacer })
    state.container = container
    state.info = info
    observeAuto(container, first, last)
    return info
  } finally {
    state.busy = false
  }
}

/**
 * 全局安装空白检测：所有页面在加载、路由切换、尺寸变化、内容变化、滚动停止后自动检测
 * @param {Object} router vue-router 实例
 */
export function installScrollBlank(router) {
  if (autoState) return autoState
  // 键盘展开/收起监听：展开期间暂停检测，收起后刷新
  watchKeyboard()
  autoState = {
    spacer: null,
    container: null,
    observed: null,
    info: null,
    busy: false,
    ro: null,
    mo: null,
    timer: null,
    scrollTimer: null
  }

  const onResize = function () { queueAutoRefresh(120) }
  const onScroll = function () {
    if (autoState.scrollTimer) clearTimeout(autoState.scrollTimer)
    autoState.scrollTimer = setTimeout(function () {
      autoState.scrollTimer = null
      refreshAutoScrollBlank()
    }, 200)
  }

  window.addEventListener('resize', onResize)
  window.addEventListener('orientationchange', onResize)
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('load', onResize)
  if (router && typeof router.afterEach === 'function') {
    router.afterEach(function () {
      queueAutoRefresh(0)
      setTimeout(function () { refreshAutoScrollBlank() }, 150)
    })
  }
  queueAutoRefresh(0)
  setTimeout(function () { refreshAutoScrollBlank() }, 300)
  return autoState
}

/**
 * 页面上下滑动空白检测 mixin（显式接入方式）
 * 内容尺寸变化（识别结果回填、图片预览等）后自动重新检测并修正底部占位
 */
export const scrollBlankMixin = {
  data() {
    return {
      // 最近一次检测结果：{ topGap, targetGap, bottomGap, spacerHeight, contentOverflow, balanced }
      scrollBlankInfo: null
    }
  },
  mounted() {
    // 显式接入的页面接管检测，停用全局自动模式，避免双重占位
    mixinDepth++
    teardownAuto()
    watchKeyboard()
    this._scrollBlankHandler = this.refreshScrollBlank.bind(this)
    keyboardMixinRefreshers.push(this._scrollBlankHandler)
    window.addEventListener('resize', this._scrollBlankHandler)
    window.addEventListener('orientationchange', this._scrollBlankHandler)
    if (typeof ResizeObserver !== 'undefined') {
      this._scrollBlankObserver = new ResizeObserver(this._scrollBlankHandler)
      this.observeScrollBlank()
    }
    this.$nextTick(this._scrollBlankHandler)
  },
  activated() {
    this.$nextTick(this._scrollBlankHandler)
  },
  updated() {
    this.refreshScrollBlank()
  },
  beforeDestroy() {
    if (this._scrollBlankHandler) {
      window.removeEventListener('resize', this._scrollBlankHandler)
      window.removeEventListener('orientationchange', this._scrollBlankHandler)
      const index = keyboardMixinRefreshers.indexOf(this._scrollBlankHandler)
      if (index > -1) keyboardMixinRefreshers.splice(index, 1)
    }
    if (this._scrollBlankObserver) {
      this._scrollBlankObserver.disconnect()
      this._scrollBlankObserver = null
    }
    document.documentElement.style.removeProperty(SCROLL_BLANK_VIEWPORT_VAR)
    this._scrollBlankHandler = null
    // 交还全局模式；路由切换后由 afterEach 自动重新检测
    if (mixinDepth > 0) mixinDepth--
  },
  methods: {
    /** 监听首/尾元素尺寸变化，内容增减后重新检测 */
    observeScrollBlank() {
      if (!this._scrollBlankObserver) return
      const targets = [this.$refs.scrollBlankFirst, this.$refs.scrollBlankLast]
      targets.forEach(el => {
        if (el) this._scrollBlankObserver.observe(el)
      })
    },
    /** 检测页面上下空白并修正底部占位，返回检测结果 */
    refreshScrollBlank() {
      // 键盘展开期间暂停刷新，避免键盘动画与留白重排互相干扰
      if (keyboardOpen) return this.scrollBlankInfo
      // 先同步可视高度并写入 CSS 变量，后续测量基于修正后的页面高度
      if (syncScrollBlankViewport() == null) {
        this.resetScrollBlank()
        return null
      }
      const info = detectScrollBlank({
        first: this.$refs.scrollBlankFirst,
        last: this.$refs.scrollBlankLast,
        topBar: this.$refs.scrollBlankTopBar,
        bottomBar: this.$refs.scrollBlankBottomBar,
        spacer: this.$refs.scrollBlankSpacer
      })
      if (info && !isSameBlankInfo(this.scrollBlankInfo, info)) {
        this.scrollBlankInfo = info
      }
      return info
    },
    /** 非手机端时恢复占位元素默认高度 */
    resetScrollBlank() {
      const spacer = this.$refs.scrollBlankSpacer
      if (spacer && spacer.style.height) {
        spacer.style.height = ''
      }
      this.scrollBlankInfo = null
    }
  }
}
