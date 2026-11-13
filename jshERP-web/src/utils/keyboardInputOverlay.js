/**
 * 移动端键盘输入弹层
 *
 * 手机浏览器弹出软键盘时会遮挡页面输入框。这里不再滚动页面，而是：
 * 1. 输入框获得焦点时让页面保持原状（记录并恢复滚动位置）；
 * 2. 在键盘上方弹出独立输入弹层（按屏宽铺满），标题取原输入框的标签，
 *    预填该输入框的当前值并自动聚焦、全选；
 * 3. 只有点击弹层内的「确认」按钮才把内容回填到原输入框；
 * 4. 键盘收起、回车、点击弹层外等其它动作只关闭弹层，不回填数据。
 *
 * 由 main.js 全局安装，页面无需接入。
 */

const OVERLAY_ID = 'kb-input-overlay'

// 不需要弹层接管的输入类型（由系统原生选择器或特殊交互处理）
const SKIP_INPUT_TYPES = [
  'button', 'submit', 'reset', 'checkbox', 'radio', 'file', 'range',
  'color', 'date', 'time', 'datetime-local', 'month', 'week', 'hidden',
  'image', 'password'
]

// 带搜索能力的组合组件，其输入框需要保留原始焦点
const SKIP_SELECTOR = '.ant-select, .ant-cascader, .ant-tree-select, .ant-picker, [role="combobox"], [aria-autocomplete]'

// 弹层输入控件样式（与框内布局配合）
const INPUT_STYLE = [
  'flex:1',
  'min-width:0',
  'height:44px',
  'padding:0 12px',
  'box-sizing:border-box',
  'border:none',
  'background:transparent',
  'font-size:16px',
  'color:#262626',
  'outline:none'
].join(';')

const TEXTAREA_STYLE = [
  'flex:1',
  'min-width:0',
  'min-height:44px',
  'max-height:96px',
  'padding:11px 12px',
  'box-sizing:border-box',
  'border:none',
  'background:transparent',
  'font-size:16px',
  'line-height:22px',
  'color:#262626',
  'outline:none',
  'resize:none'
].join(';')

let installed = false
let activeEl = null
let rootEl = null
let titleEl = null
let fieldEl = null
let scrollSnapshot = []
let baselineHeight = 0
let viewportBound = false
let lastOkTouch = 0

/** 仅手机/平板等触摸设备启用，桌面端保持原生输入体验 */
function isMobileEnv () {
  if (typeof window === 'undefined') return false
  let coarse = false
  try {
    coarse = !!(window.matchMedia && window.matchMedia('(pointer: coarse)').matches)
  } catch (e) {
    coarse = false
  }
  const ua = navigator.userAgent || ''
  const touchUa = /Android|iPhone|iPad|iPod|Windows Phone|HarmonyOS|Mobile/i.test(ua)
  return (coarse || touchUa) && window.innerWidth <= 1024
}

/** 判断元素是否需要接管 */
function isEditableTarget (el) {
  if (!el || el.nodeType !== 1) return false
  const tag = el.tagName
  if (tag !== 'INPUT' && tag !== 'TEXTAREA') return false
  if (el.closest('#' + OVERLAY_ID)) return false
  if (el.disabled || el.readOnly) return false
  if (el.getAttribute('data-kb-overlay') === 'off') return false
  if (el.closest(SKIP_SELECTOR)) return false
  if (tag === 'INPUT') {
    const type = (el.getAttribute('type') || 'text').toLowerCase()
    if (SKIP_INPUT_TYPES.indexOf(type) >= 0) return false
  }
  const rect = el.getBoundingClientRect()
  return !!(rect.width || rect.height)
}

/** 估算软键盘占用高度；优先使用可视视口，不可用时按窗口高度变化兜底 */
function getKeyboardHeight () {
  const innerHeight = window.innerHeight || 0
  const vv = window.visualViewport
  if (vv) {
    const gap = Math.round(innerHeight - (vv.height + vv.offsetTop))
    return gap > 60 ? gap : 0
  }
  const base = baselineHeight || innerHeight
  const gap = base - innerHeight
  return gap > 80 ? gap : 0
}

/** 记录页面滚动位置，键盘弹出后用于恢复原状 */
function snapshotScroll (el) {
  scrollSnapshot = []
  try {
    const docEl = document.scrollingElement || document.documentElement
    scrollSnapshot.push({ el: docEl, top: docEl.scrollTop, left: docEl.scrollLeft })
    let node = el && el.parentElement
    while (node && node !== document.body) {
      const style = window.getComputedStyle(node)
      const scrollable = /(auto|scroll)/.test(style.overflowY) && node.scrollHeight > node.clientHeight
      if (scrollable) {
        scrollSnapshot.push({ el: node, top: node.scrollTop, left: node.scrollLeft })
      }
      node = node.parentElement
    }
  } catch (e) {
    scrollSnapshot = []
  }
}

function restoreScroll () {
  scrollSnapshot.forEach(item => {
    if (!item.el) return
    if (item.el.scrollTop !== item.top) item.el.scrollTop = item.top
    if (item.el.scrollLeft !== item.left) item.el.scrollLeft = item.left
  })
}

/** 标题文本归一化：去星号、去结尾冒号、压缩空白、限长 */
function normalizeTitle (text) {
  let value = String(text || '').replace(/\s+/g, ' ').trim()
  value = value.replace(/[*＊]/g, '').replace(/[:：]\s*$/, '').trim()
  return value.length > 30 ? value.slice(0, 30) + '…' : value
}

function textOf (node) {
  if (!node || node.nodeType !== 1) return ''
  if (/^(INPUT|TEXTAREA|SELECT|BUTTON)$/.test(node.tagName)) return ''
  return normalizeTitle(node.textContent || node.innerText || '')
}

/** 取原输入框的标题：显式属性 > label > 表单标签 > 相邻文本 > 占位符 */
function resolveFieldTitle (el) {
  const explicit = normalizeTitle(el.getAttribute('data-kb-title') || el.getAttribute('data-title'))
  if (explicit) return explicit

  if (el.id) {
    try {
      const label = document.querySelector('label[for="' + CSS.escape(el.id) + '"]')
      const text = textOf(label)
      if (text) return text
    } catch (e) {}
  }

  const aria = normalizeTitle(el.getAttribute('aria-label'))
  if (aria) return aria

  const formItem = el.closest('.ant-form-item')
  if (formItem) {
    const text = textOf(formItem.querySelector('.ant-form-item-label'))
    if (text) return text
  }

  // 常见结构：<span>名称：</span><div><input/></div>
  const candidates = [el.previousElementSibling, el.parentElement && el.parentElement.previousElementSibling]
  for (let i = 0; i < candidates.length; i++) {
    const text = textOf(candidates[i])
    if (text) return text
  }

  return normalizeTitle(el.getAttribute('placeholder'))
}

/** 创建（或复用）弹层 DOM */
function ensureOverlay () {
  if (rootEl) return rootEl
  const root = document.createElement('div')
  root.id = OVERLAY_ID
  // 弹层按屏宽铺满，不再使用浮动卡片布局
  root.style.cssText = [
    'position:fixed',
    'left:0',
    'right:0',
    'width:100%',
    'max-width:100vw',
    'bottom:0',
    'z-index:100000',
    'display:none',
    'margin:0',
    'padding:10px 12px',
    'padding-bottom:calc(10px + env(safe-area-inset-bottom, 0px))',
    'box-sizing:border-box',
    'background:#fff',
    'border-top:1px solid #e8e8e8',
    'font-size:15px'
  ].join(';')

  const title = document.createElement('div')
  title.setAttribute('data-kb-title-role', '1')
  title.style.cssText = [
    'display:none',
    'margin:0 0 8px',
    'font-size:14px',
    'font-weight:600',
    'line-height:20px',
    'color:#262626',
    'white-space:nowrap',
    'overflow:hidden',
    'text-overflow:ellipsis'
  ].join(';')

  const box = document.createElement('div')
  box.style.cssText = [
    'display:flex',
    'align-items:stretch',
    'width:100%',
    'box-sizing:border-box',
    'border:1px solid #d9d9d9',
    'border-radius:8px',
    'background:#fff',
    'overflow:hidden'
  ].join(';')

  const field = document.createElement('input')
  field.type = 'text'
  field.setAttribute('data-kb-field', '1')
  field.style.cssText = INPUT_STYLE

  // 框内右侧的确认按钮：只有它会把内容回填到原输入框
  const okBtn = document.createElement('button')
  okBtn.type = 'button'
  okBtn.textContent = '确认'
  okBtn.style.cssText = [
    'flex:0 0 auto',
    'align-self:flex-start',
    'height:44px',
    'padding:0 18px',
    'border:none',
    'border-left:1px solid #e8e8e8',
    'border-radius:0',
    'background:#1890ff',
    'color:#fff',
    'font-size:15px',
    'font-weight:500',
    'outline:none'
  ].join(';')

  // 按下按钮时阻止输入框失焦，避免弹层先被关闭
  const keepFocus = e => e.preventDefault()
  okBtn.addEventListener('mousedown', keepFocus)
  okBtn.addEventListener('touchstart', e => {
    e.preventDefault()
    lastOkTouch = Date.now()
  }, { passive: false })
  okBtn.addEventListener('touchend', e => {
    e.preventDefault()
    commit()
  }, { passive: false })
  okBtn.addEventListener('click', () => {
    if (Date.now() - lastOkTouch < 600) return
    commit()
  })

  box.appendChild(field)
  box.appendChild(okBtn)
  root.appendChild(title)
  root.appendChild(box)
  document.body.appendChild(root)

  rootEl = root
  titleEl = title
  fieldEl = field
  bindOverlayEvents(field)
  return root
}

function bindOverlayEvents (field) {
  // 键盘收起导致弹层输入框失焦：只关闭弹层，不回填
  field.addEventListener('blur', () => {
    const el = activeEl
    setTimeout(() => {
      if (!activeEl || activeEl !== el) return
      const focusEl = document.activeElement
      if (focusEl && rootEl && rootEl.contains(focusEl)) return
      // 焦点已切到页面其它输入框时交给 focusin 处理
      if (focusEl && isEditableTarget(focusEl)) return
      closeOverlay()
    }, 0)
  })
  // 回车等键盘动作不回填、不关闭，等待用户点击确认
  field.addEventListener('keydown', e => {
    if (e.key !== 'Enter') return
    if (field.tagName === 'TEXTAREA') return
    e.preventDefault()
  })
  field.addEventListener('input', () => {
    if (field.tagName !== 'TEXTAREA') return
    field.style.height = 'auto'
    field.style.height = Math.min(field.scrollHeight, 96) + 'px'
  })
}

/** 按原输入框属性重建弹层输入控件 */
function buildField (el) {
  const isTextarea = el.tagName === 'TEXTAREA'
  const current = fieldEl
  let next
  if (isTextarea) {
    next = document.createElement('textarea')
    next.rows = 2
    next.style.cssText = TEXTAREA_STYLE
  } else {
    next = document.createElement('input')
    next.type = 'text'
    next.style.cssText = INPUT_STYLE
  }
  next.setAttribute('data-kb-field', '1')
  const type = (el.getAttribute('type') || 'text').toLowerCase()
  if (!isTextarea && type) next.type = type
  const inputmode = el.getAttribute('inputmode')
  if (inputmode) next.setAttribute('inputmode', inputmode)
  const maxlength = el.getAttribute('maxlength')
  if (maxlength) next.setAttribute('maxlength', maxlength)
  const placeholder = el.getAttribute('placeholder')
  if (placeholder) next.setAttribute('placeholder', placeholder)
  next.value = el.value || ''

  current.parentNode.replaceChild(next, current)
  fieldEl = next
  bindOverlayEvents(next)
  return next
}

function positionOverlay () {
  if (!rootEl || !activeEl) return
  rootEl.style.bottom = getKeyboardHeight() + 'px'
  const vv = window.visualViewport
  if (vv && vv.width) {
    // 按可视区屏宽铺满，兼容横屏与页面缩放
    rootEl.style.left = Math.round(vv.offsetLeft) + 'px'
    rootEl.style.right = 'auto'
    rootEl.style.width = Math.round(vv.width) + 'px'
  } else {
    rootEl.style.left = '0'
    rootEl.style.right = '0'
    rootEl.style.width = '100%'
  }
}

function onViewportChange () {
  positionOverlay()
}

function bindViewport () {
  if (viewportBound) return
  viewportBound = true
  window.addEventListener('resize', onViewportChange)
  window.addEventListener('orientationchange', onViewportChange)
  window.addEventListener('scroll', onViewportChange, true)
  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', onViewportChange)
    window.visualViewport.addEventListener('scroll', onViewportChange)
  }
}

function unbindViewport () {
  if (!viewportBound) return
  viewportBound = false
  window.removeEventListener('resize', onViewportChange)
  window.removeEventListener('orientationchange', onViewportChange)
  window.removeEventListener('scroll', onViewportChange, true)
  if (window.visualViewport) {
    window.visualViewport.removeEventListener('resize', onViewportChange)
    window.visualViewport.removeEventListener('scroll', onViewportChange)
  }
}

/** 打开弹层 */
function openOverlay (el) {
  ensureOverlay()
  if (!rootEl) return
  buildField(el)
  // 弹层标题取原输入框的标题
  const title = resolveFieldTitle(el)
  if (titleEl) {
    titleEl.textContent = title
    titleEl.style.display = title ? 'block' : 'none'
  }
  rootEl.style.display = 'block'
  positionOverlay()
  bindViewport()
  // 原输入框先失焦，避免系统再弹一次键盘
  try { el.blur() } catch (e) {}
  try {
    fieldEl.focus({ preventScroll: true })
  } catch (e) {
    try { fieldEl.focus() } catch (e2) {}
  }
  try {
    if (fieldEl.setSelectionRange) {
      const len = fieldEl.value.length
      fieldEl.setSelectionRange(0, len)
    }
  } catch (e) {}
  // 抵消浏览器为聚焦输入框产生的自动滚动，保持页面原状
  requestAnimationFrame(restoreScroll)
  setTimeout(restoreScroll, 120)
  setTimeout(restoreScroll, 320)
}

/** 触发原生事件，兼容不支持 Event 构造器的旧浏览器 */
function fireEvent (el, type) {
  let evt
  try {
    evt = new Event(type, { bubbles: true })
  } catch (e) {
    evt = document.createEvent('HTMLEvents')
    evt.initEvent(type, true, false)
  }
  el.dispatchEvent(evt)
}

/** 点击确认：回填并关闭 */
function commit () {
  const el = activeEl
  if (!el) return
  const value = fieldEl ? fieldEl.value : ''
  activeEl = null
  unbindViewport()
  if (rootEl) rootEl.style.display = 'none'
  if (document.body.contains(el)) {
    if (el.value !== value) {
      el.value = value
      fireEvent(el, 'input')
      fireEvent(el, 'change')
    }
  }
  restoreScroll()
}

/** 关闭弹层：不回填数据 */
function closeOverlay () {
  activeEl = null
  unbindViewport()
  if (rootEl) rootEl.style.display = 'none'
  restoreScroll()
}

function onFocusIn (e) {
  const el = e.target
  if (!isEditableTarget(el)) return
  if (activeEl === el) return
  // 切换输入框时不回填上一项，直接关闭原弹层
  if (activeEl) closeOverlay()
  activeEl = el
  snapshotScroll(el)
  openOverlay(el)
}

function onDocumentClick (e) {
  if (!activeEl || !rootEl) return
  if (rootEl.contains(e.target)) return
  // 点击其它输入框由 focusin 负责切换，这里只处理点到弹层之外
  if (isEditableTarget(e.target)) return
  closeOverlay()
}

export function installKeyboardInputOverlay () {
  if (installed) return
  installed = true
  baselineHeight = window.innerHeight || 0
  window.addEventListener('resize', () => {
    if (!activeEl && window.innerHeight > baselineHeight) baselineHeight = window.innerHeight
  })
  document.addEventListener('focusin', onFocusIn, true)
  document.addEventListener('click', onDocumentClick, true)
}

export default installKeyboardInputOverlay
