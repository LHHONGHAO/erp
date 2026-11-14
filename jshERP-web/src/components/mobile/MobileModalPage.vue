<template>
  <div v-if="visible" class="mmp-page">
    <!-- 标准顶栏：蓝底 49px、标题左对齐、副标题同行、右侧关闭固定 -->
    <div class="mmp-header">
      <div class="mmp-title-block">
        <div class="mmp-title">{{ title }}</div>
        <!-- 标题附加区：例如模板名称 + 重命名按钮 -->
        <slot name="title-extra"></slot>
        <div v-if="subtitle" class="mmp-subtitle">{{ subtitle }}</div>
      </div>
      <div v-if="$slots.actions" class="mmp-tools">
        <slot name="actions"></slot>
      </div>
      <div class="mmp-close" @click="$emit('close')">
        <a-icon type="close" title="关闭" />
      </div>
    </div>

    <!-- 内容区 -->
    <div class="mmp-body">
      <slot></slot>
    </div>

    <!-- 底部操作区 -->
    <div v-if="$slots.footer" class="mmp-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script>
/**
 * 手机端标准页面弹层
 * 全屏浮层 + 标准顶栏（蓝底 49px、标题左对齐、副标题同行、右侧关闭固定），
 * 底栏白底，最小高度与标准底栏一致（58px）；内容与底部按钮通过插槽传入。
 * a-modal 形式的弹层请使用全局样式 .mm-sheet-modal + .mm-sheet-header（同一视觉规范）。
 */

// 多个弹层页同时存在时，按引用计数决定 body 是否锁定横向手势
var swipeLockCount = 0

/**
 * iOS Safari 的左右滑动回退由浏览器手势接管，touchmove 的 preventDefault 拦不住。
 * 弹层打开时压入一条同 URL 的历史记录作为哨兵：右滑只会回退到这条哨兵
 * （URL 与路由都不变，弹层原地不动），popstate 后再补回哨兵。
 */
var historyGuardActive = false
var popStateBound = false

function pushHistoryGuard() {
  if (historyGuardActive || typeof window === 'undefined' || !window.history) return
  var state = window.history.state
  // 刷新后历史里可能残留上一次的哨兵（state 带标记，但内存标记已随刷新丢失）。
  // 此时直接接管这条记录，不再压栈；否则每刷新一次就多压一条，返回要多点几次。
  if (state && state.__mmpSwipeGuard) {
    historyGuardActive = true
    return
  }
  try {
    var next = (state && typeof state === 'object') ? Object.assign({}, state) : {}
    next.__mmpSwipeGuard = true
    window.history.pushState(next, '', window.location.href)
    historyGuardActive = true
  } catch (e) { /* 忽略 */ }
}

function releaseHistoryGuard() {
  if (!historyGuardActive) return
  historyGuardActive = false
  var state = window.history.state
  if (state && state.__mmpSwipeGuard) {
    try {
      // 先清除当前记录的哨兵标记，再回退，避免回退失败时下次误判为遗留哨兵
      var next = Object.assign({}, state)
      delete next.__mmpSwipeGuard
      window.history.replaceState(next, '', window.location.href)
      window.history.back()
    } catch (e) { /* 忽略 */ }
  }
}

function onSwipeGuardPopState() {
  // 弹层已全部关闭时不处理（例如上面 releaseHistoryGuard 触发的回退）
  if (swipeLockCount <= 0) return
  historyGuardActive = false
  pushHistoryGuard()
}

function ensurePopStateBound() {
  if (popStateBound || typeof window === 'undefined') return
  popStateBound = true
  window.addEventListener('popstate', onSwipeGuardPopState)
}

function applyBodySwipeLock(on) {
  if (typeof document === 'undefined' || !document.body) return
  swipeLockCount = Math.max(0, swipeLockCount + (on ? 1 : -1))
  var locked = swipeLockCount > 0
  document.body.classList.toggle('mmp-swipe-locked', locked)
  if (locked) {
    ensurePopStateBound()
    pushHistoryGuard()
  } else {
    releaseHistoryGuard()
  }
}
export default {
  name: 'MobileModalPage',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '' },
    subtitle: { type: String, default: '' }
  },
  watch: {
    visible(val) {
      this.toggleBodyLock(val)
    }
  },
  mounted() {
    var self = this
    // 锁定左右滑动：防止浏览器手势翻页/回退把弹层页带走
    this._onSwipeStart = function(e) {
      if (!self.visible || !self.$el || !self.$el.contains(e.target)) {
        self._swipeState = null
        return
      }
      if (e.touches.length !== 1) {
        self._swipeState = null
        return
      }
      var t = e.touches[0]
      self._swipeState = {
        x: t.clientX,
        y: t.clientY,
        // 横向可滚动区域（工具条、表格等）内不拦截，保留其自身滑动
        allowScroll: self.isHorizontalScroller(e.target)
      }
    }
    this._onSwipeMove = function(e) {
      var state = self._swipeState
      if (!state || !self.visible || e.touches.length !== 1) return
      var t = e.touches[0]
      var dx = t.clientX - state.x
      var dy = t.clientY - state.y
      if (state.allowScroll) return
      if (Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.2) {
        // 横向滑动只留在弹层内，不触发浏览器左右翻页
        if (e.cancelable) e.preventDefault()
      }
    }
    document.addEventListener('touchstart', this._onSwipeStart, { passive: true })
    document.addEventListener('touchmove', this._onSwipeMove, { passive: false })
    this.toggleBodyLock(this.visible)
  },
  beforeDestroy() {
    document.removeEventListener('touchstart', this._onSwipeStart)
    document.removeEventListener('touchmove', this._onSwipeMove)
    this.toggleBodyLock(false)
  },
  methods: {
    /** 手指落在横向可滚动元素内时放行（工具条、表格等） */
    isHorizontalScroller(node) {
      var root = this.$el
      while (node && node !== root && node.nodeType === 1) {
        if (node.hasAttribute && node.hasAttribute('data-swipe-lock-ignore')) return true
        var style = window.getComputedStyle(node)
        if ((style.overflowX === 'auto' || style.overflowX === 'scroll') &&
            node.scrollWidth - node.clientWidth > 2) {
          return true
        }
        node = node.parentNode
      }
      return false
    },
    toggleBodyLock(on) {
      if (!this._bswipeLocked && !on) return
      if (this._bswipeLocked === !!on) return
      this._bswipeLocked = !!on
      applyBodySwipeLock(!!on)
    }
  }
}
</script>

<style>
/* 非 scoped：插槽内的图标由父组件渲染，scoped 选择器无法命中 */
.mmp-page {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background: #f2f4f7;
  z-index: 2900;
  /* 弹层页内滑动不向浏览器传递，避免触发左右翻页/回退手势 */
  overscroll-behavior: contain;
}
body.mmp-swipe-locked {
  overscroll-behavior-x: none;
}
.mmp-header {
  position: relative;
  flex: none;
  height: 49px;
  box-sizing: border-box;
  padding: 0 44px 0 58px;
  background: #1890ff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  display: flex;
  align-items: center;
}
.mmp-title-block {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 8px;
  /* 标题左边固定 66px，与标准手机顶栏一致 */
  padding-left: 8px;
}
.mmp-title {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 100%;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.2;
  color: #fff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mmp-subtitle {
  flex: 0 1 auto;
  min-width: 0;
  font-size: 12px;
  line-height: 1.2;
  color: rgba(255, 255, 255, 0.75);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mmp-tools {
  flex: none;
  display: flex;
  align-items: center;
  height: 49px;
}
.mmp-close {
  position: absolute;
  top: 0;
  right: 0;
  width: 44px;
  height: 49px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  cursor: pointer;
  user-select: none;
}
.mmp-close:active {
  background: rgba(255, 255, 255, 0.2);
}
.mmp-icon,
.mmp-tools .anticon {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  line-height: 1;
  color: #fff;
  cursor: pointer;
}
.mmp-icon:active,
.mmp-tools .anticon:active {
  background: rgba(255, 255, 255, 0.2);
}
.mmp-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.mmp-footer {
  flex: none;
  min-height: 58px;
  box-sizing: border-box;
  background: #fff;
}
</style>
