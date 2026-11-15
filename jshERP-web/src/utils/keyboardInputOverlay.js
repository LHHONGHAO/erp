/**
 * 移动端键盘输入弹层（按需启用）
 *
 * 默认不接管任何输入框：输入框获得焦点时保持原生体验。
 * 只有显式指定的页面或输入框才在键盘上方弹出标准输入框：
 *   1. 页面级：在页面 mounted 中调用 enableKeyboardInputOverlay('.selector')；
 *   2. 输入框级：给 input/textarea 加 data-kb-overlay="on"。
 * data-kb-overlay="off" 始终优先，表示该输入框永不接管。
 *
 * 弹层底边贴合软键盘顶边，并随可视视口（键盘动画、页面缩放、横竖屏）移动，
 * 不依赖页面内容滚动或 DOM 变化。
 *
 * 交互：只有点击圆形发送按钮才把内容回填到原输入框；
 * 键盘收起、回车、点击弹层外等其它动作只关闭弹层，不回填数据。
 *
 * 弹层 DOM 由 main.js 全局安装，页面按需启用即可。
 */
import '@/components/mobile/keyboardInputBox.less'

const OVERLAY_ID = 'kb-input-overlay'

// 页面级启用的选择器集合
const optInSelectors = new Set()

// 圆形发送按钮内的箭头图标
const SEND_ICON = '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
  'stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">' +
  '<path d="M12 19V5"></path><path d="M5 12l7-7 7 7"></path></svg>'

// 不需要弹层接管的输入类型（由系统原生选择器或特殊交互处理）
const SKIP_INPUT_TYPES = [
  'button', 'submit', 'reset', 'checkbox', 'radio', 'file', 'range',
  'color', 'date', 'time', 'datetime-local', 'month', 'week', 'hidden',
  'image', 'password'
]

// 带搜索能力的组合组件，其输入框需要保留原始焦点
const SKIP_SELECTOR = '.ant-select, .ant-cascader, .ant-tree-select, .ant-picker, [role="combobox"], [aria-autocomplete]'

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

/** 判断输入框是否被显式启用（页面选择器或 data-kb-overlay="on"） */
function isOptedIn (el) {
  if (el.closest('[data-kb-overlay="on"]')) return true
  if (!optInSelectors.size) return false
  const matched = []
  optInSelectors.forEach(selector => matched.push(selector))
  for (let i = 0; i < matched.length; i++) {
    try {
      if (el.matches(matched[i]) || el.closest(matched[i])) return true
    } catch (e) {}
  }
  return false
}

/** 判断元素是否需要接管：必须显式启用，默认不接管 */
function isEditableTarget (el) {
  if (!el || el.nodeType !== 1) return false
  const tag = el.tagName
  if (tag !== 'INPUT' && tag !== 'TEXTAREA') return false
  if (el.closest('#' + OVERLAY_ID)) return false
  if (el.disabled || el.readOnly) return false
  if (el.getAttribute('data-kb-overlay') === 'off') return false
  if (!isOptedIn(el)) return false
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
  // 弹层按可视视口铺满，底边由 positionOverlay 贴合键盘顶边
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
    'padding:6px 12px',
    'padding-bottom:calc(6px + env(safe-area-inset-bottom, 0px))',
    'box-sizing:border-box'
  ].join(';')

  const title = document.createElement('div')
  title.setAttribute('data-kb-title-role', '1')
  title.className = 'kib__title'
  title.style.display = 'none'

  const box = document.createElement('div')
  box.className = 'kib__card'

  const field = document.createElement('input')
  field.type = 'text'
  field.className = 'kib__field'
  field.setAttribute('data-kb-field', '1')

  // 卡片右侧的圆形发送按钮：只有它会把内容回填到原输入框
  const okBtn = document.createElement('button')
  okBtn.type = 'button'
  okBtn.className = 'kib__send'
  okBtn.setAttribute('aria-label', '确认')
  okBtn.setAttribute('title', '确认')
  okBtn.innerHTML = SEND_ICON

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
    field.style.height = Math.min(field.scrollHeight, 120) + 'px'
    // 多行增高后重新贴合键盘顶边
    positionOverlay()
  })
}

/** 按原输入框属性重建弹层输入控件 */
function buildField (el) {
  const isTextarea = el.tagName === 'TEXTAREA'
  const current = fieldEl
  let next
  if (isTextarea) {
    next = document.createElement('textarea')
    next.rows = 1
    next.className = 'kib__field kib__field--multiline'
  } else {
    next = document.createElement('input')
    next.type = 'text'
    next.className = 'kib__field'
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

/**
 * 贴合软键盘顶边：以可视视口底边为基准设置 top，
 * 键盘弹起动画、页面缩放、横竖屏时都随可视视口移动。
 */
function positionOverlay () {
  if (!rootEl || !activeEl) return
  const vv = window.visualViewport
  if (vv && vv.height) {
    const height = rootEl.offsetHeight
    rootEl.style.left = Math.round(vv.offsetLeft) + 'px'
    rootEl.style.width = Math.round(vv.width) + 'px'
    rootEl.style.right = 'auto'
    rootEl.style.bottom = 'auto'
    rootEl.style.top = Math.round(vv.offsetTop + vv.height - height) + 'px'
    return
  }
  // 不支持 visualViewport 的旧内核：按窗口高度变化估算键盘高度
  rootEl.style.top = 'auto'
  rootEl.style.left = '0'
  rootEl.style.right = '0'
  rootEl.style.width = '100%'
  rootEl.style.bottom = getKeyboardHeight() + 'px'
}

// 键盘动画期间 resize/scroll 会连续触发，按帧合并
let positionRaf = 0
// 是否已确认键盘弹起过：用于键盘被系统收起（输入框仍保持焦点）时自动关闭弹层
let keyboardOpened = false
function onViewportChange () {
  if (positionRaf) return
  positionRaf = requestAnimationFrame(() => {
    positionRaf = 0
    if (!activeEl) return
    // 系统收起键盘时输入框焦点不变，不会触发 blur，需要按键盘高度判断并关闭弹层
    if (getKeyboardHeight() > 0) {
      keyboardOpened = true
    } else if (keyboardOpened) {
      closeOverlay()
      return
    }
    positionOverlay()
  })
}

function bindViewport () {
  if (viewportBound) return
  viewportBound = true
  // 只跟随可视视口与窗口尺寸变化，不监听页面滚动，避免页面滚动/内容变化影响弹层位置
  window.addEventListener('resize', onViewportChange)
  window.addEventListener('orientationchange', onViewportChange)
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
  if (window.visualViewport) {
    window.visualViewport.removeEventListener('resize', onViewportChange)
    window.visualViewport.removeEventListener('scroll', onViewportChange)
  }
  if (positionRaf) {
    cancelAnimationFrame(positionRaf)
    positionRaf = 0
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
  // 打开时记录键盘状态：切换输入框时键盘通常已弹起
  keyboardOpened = getKeyboardHeight() > 0
  positionOverlay()
  bindViewport()
  // 键盘弹起动画期间保险再对齐两次
  requestAnimationFrame(positionOverlay)
  setTimeout(positionOverlay, 260)
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
  keyboardOpened = false
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
  keyboardOpened = false
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

/**
 * 启用指定页面/输入框的键盘输入弹层（可传多个 CSS 选择器）
 * 例：mounted 中 enableKeyboardInputOverlay('.bill-form input')
 */
export function enableKeyboardInputOverlay (...selectors) {
  selectors.forEach(selector => {
    if (selector) optInSelectors.add(selector)
  })
}

/** 关闭指定选择器的键盘输入弹层 */
export function disableKeyboardInputOverlay (...selectors) {
  selectors.forEach(selector => optInSelectors.delete(selector))
}

export default installKeyboardInputOverlay
