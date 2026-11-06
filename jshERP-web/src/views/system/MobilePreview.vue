<template>
  <div class="mp-page">
    <div class="mp-toolbar">
      <span class="mp-brand">手机端预览</span>
      <a-select
        :value="deviceKey"
        size="small"
        style="width: 180px"
        @change="onDeviceChange">
        <a-select-option v-for="d in devices" :key="d.key" :value="d.key">{{ d.label }}</a-select-option>
      </a-select>
      <a-button size="small" @click="toggleOrientation">
        <a-icon :type="orientation === 'portrait' ? 'mobile' : 'swap'" />
        {{ orientation === 'portrait' ? '竖屏' : '横屏' }}
      </a-button>
      <a-select :value="zoomMode" size="small" style="width: 110px" @change="onZoomChange">
        <a-select-option value="fit">自动缩放</a-select-option>
        <a-select-option :value="1">100%</a-select-option>
        <a-select-option :value="0.75">75%</a-select-option>
        <a-select-option :value="0.5">50%</a-select-option>
      </a-select>
      <a-select
        :value="currentPath"
        size="small"
        style="width: 220px"
        show-search
        option-filter-prop="children"
        @change="onPageChange">
        <a-select-option v-for="p in pages" :key="p.path" :value="p.path">{{ p.name }}</a-select-option>
      </a-select>
      <a-input
        :value="currentPath"
        size="small"
        style="width: 260px"
        placeholder="页面路径，如 /material/material_add"
        @change="onPathInput"
        @pressEnter="reload" />
      <a-button size="small" icon="reload" @click="reload">刷新</a-button>
      <a-button size="small" icon="export" @click="openNew">新窗口打开</a-button>
      <span class="mp-tip">鼠标在空白处按住拖动＝手指滑动（上下左右滚动），按住约 0.6 秒不动＝长按；宽度小于 768px 自动进入手机端布局</span>
    </div>
    <div ref="stage" class="mp-stage">
      <div class="mp-scaler" :style="scalerStyle">
        <div class="mp-phone" :style="phoneStyle">
          <iframe
            ref="frame"
            :key="frameKey"
            :src="currentPath"
            class="mp-frame"
            @load="installTouchBridge"
            allow="camera; microphone; clipboard-write; fullscreen"></iframe>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  const DEVICES = [
    { key: 'iphone-se', label: 'iPhone SE 375×667', width: 375, height: 667 },
    { key: 'iphone-12', label: 'iPhone 12 390×844', width: 390, height: 844 },
    { key: 'iphone-14-pro-max', label: 'iPhone 14 Pro Max 430×932', width: 430, height: 932 },
    { key: 'android-360', label: 'Android 360×800', width: 360, height: 800 },
    { key: 'ipad-mini', label: 'iPad mini 768×1024', width: 768, height: 1024 }
  ]

  const STATIC_PAGES = [
    { name: '首页', path: '/dashboard/analysis' },
    { name: '标准页面模版', path: '/mobile-page-demo' },
    { name: '新增商品', path: '/material/material_add' },
    { name: '新增品类', path: '/material/category_add' },
    { name: '商品类别', path: '/material/material_category' },
    { name: '商品信息', path: '/material/material' },
    { name: '销售开单', path: '/bill/retail_sale_order' },
    { name: '零售出库', path: '/bill/retail_out' }
  ]

  const LAST_PATH_KEY = 'jsh_mobile_preview_path'
  const BEZEL = 10

  export default {
    name: 'MobilePreview',
    data () {
      return {
        devices: DEVICES,
        deviceKey: 'iphone-12',
        orientation: 'portrait',
        zoomMode: 'fit',
        currentPath: '/dashboard/analysis',
        frameKey: 0,
        stageWidth: 0,
        stageHeight: 0,
        stageObserver: null
      }
    },
    computed: {
      device () {
        return this.devices.find(d => d.key === this.deviceKey) || this.devices[1]
      },
      frameWidth () {
        return this.orientation === 'portrait' ? this.device.width : this.device.height
      },
      frameHeight () {
        return this.orientation === 'portrait' ? this.device.height : this.device.width
      },
      scale () {
        if (this.zoomMode === 'fit') {
          const availableWidth = this.stageWidth - 40
          const availableHeight = this.stageHeight - 40
          if (!availableWidth || !availableHeight) return 1
          return Math.min(1, availableWidth / (this.frameWidth + BEZEL * 2), availableHeight / (this.frameHeight + BEZEL * 2))
        }
        return Number(this.zoomMode)
      },
      phoneStyle () {
        return {
          width: this.frameWidth + BEZEL * 2 + 'px',
          height: this.frameHeight + BEZEL * 2 + 'px',
          transform: 'scale(' + this.scale + ')'
        }
      },
      scalerStyle () {
        return {
          width: (this.frameWidth + BEZEL * 2) * this.scale + 'px',
          height: (this.frameHeight + BEZEL * 2) * this.scale + 'px'
        }
      },
      pages () {
        const list = []
        const seen = {}
        const add = (name, path) => {
          if (!path || path === '/' || path === '*' || path === '/404' || path === '/mobile-preview') return
          if (path.indexOf('/user/') === 0 || path.indexOf('http') === 0) return
          if (!path.startsWith('/') || seen[path]) return
          seen[path] = true
          list.push({ name: name || path, path })
        }
        STATIC_PAGES.forEach(page => add(page.name, page.path))
        const walk = routes => {
          (routes || []).forEach(route => {
            if (route.meta && route.meta.title) {
              add(route.meta.title, route.path)
            }
            if (route.children) {
              walk(route.children)
            }
          })
        }
        walk(this.$store.state.permission.routers)
        return list
      }
    },
    mounted () {
      const savedPath = localStorage.getItem(LAST_PATH_KEY)
      if (savedPath) {
        this.currentPath = savedPath
      }
      // DOM 布局完成后再量取舞台尺寸，避免初始为 0 导致手机框按 1:1 溢出、看起来不居中
      this.$nextTick(this.updateStageSize)
      window.addEventListener('resize', this.updateStageSize)
      // 监听舞台容器尺寸变化（工具栏换行、IDE 预览面板缩放时都能重新居中适配）
      if (window.ResizeObserver) {
        this.stageObserver = new window.ResizeObserver(() => this.updateStageSize())
        this.stageObserver.observe(this.$refs.stage)
      }
    },
    beforeDestroy () {
      window.removeEventListener('resize', this.updateStageSize)
      if (this.stageObserver) {
        this.stageObserver.disconnect()
        this.stageObserver = null
      }
    },
    methods: {
      updateStageSize () {
        if (!this.$refs.stage) return
        this.stageWidth = this.$refs.stage.clientWidth
        this.stageHeight = this.$refs.stage.clientHeight
      },
      onDeviceChange (key) {
        this.deviceKey = key
      },
      onZoomChange (mode) {
        this.zoomMode = mode
      },
      toggleOrientation () {
        this.orientation = this.orientation === 'portrait' ? 'landscape' : 'portrait'
      },
      onPageChange (path) {
        this.currentPath = path
        this.persistPath()
        this.reload()
      },
      onPathInput (e) {
        this.currentPath = e.target.value
      },
      persistPath () {
        localStorage.setItem(LAST_PATH_KEY, this.currentPath)
      },
      // 通过变更 key 强制重新加载 iframe
      reload () {
        this.persistPath()
        this.frameKey++
      },
      openNew () {
        window.open(this.currentPath, '_blank')
      },
      // 在 iframe 内注入触摸手势桥接：
      // 鼠标按住拖动 => 手指滑动（滚动最近的可滚动容器），按住不动 0.6s => 长按（派发 contextmenu）
      installTouchBridge () {
        const frame = this.$refs.frame
        if (!frame) return
        let doc = null
        try {
          doc = frame.contentDocument
          if (!doc || !doc.body || doc.__mpTouchBridge) return
          doc.__mpTouchBridge = true
        } catch (e) {
          // 跨域时无法注入，忽略
          return
        }
        const win = frame.contentWindow
        // .lem-label 为标签画布：内部拖动用于移动元素/框选，不参与滑动滚动
        const BLOCK = 'input, textarea, select, button, a, [contenteditable="true"], .ant-modal, .ant-select, .lem-elem, .lem-resize-handle, .lem-label'
        let state = null

        const findScroller = (el) => {
          let p = el
          while (p && p.nodeType === 1 && p !== doc.body) {
            const cs = win.getComputedStyle(p)
            if ((cs.overflowY === 'auto' || cs.overflowY === 'scroll') && p.scrollHeight > p.clientHeight + 1) return p
            if ((cs.overflowX === 'auto' || cs.overflowX === 'scroll') && p.scrollWidth > p.clientWidth + 1) return p
            p = p.parentElement
          }
          return doc.scrollingElement || doc.documentElement
        }

        doc.addEventListener('mousedown', (e) => {
          if (e.button !== 0) return
          if (e.target.closest && e.target.closest(BLOCK)) return
          const scroller = findScroller(e.target)
          state = {
            sx: e.clientX,
            sy: e.clientY,
            x: e.clientX,
            y: e.clientY,
            moved: false,
            scroller: scroller,
            scrollLeft: scroller.scrollLeft,
            scrollTop: scroller.scrollTop,
            longTimer: win.setTimeout(() => {
              if (state && !state.moved) {
                const ev = new win.MouseEvent('contextmenu', {
                  bubbles: true,
                  cancelable: true,
                  clientX: state.x,
                  clientY: state.y
                })
                e.target.dispatchEvent(ev)
              }
            }, 600)
          }
          doc.body.classList.add('mp-touch-dragging')
        }, true)

        doc.addEventListener('mousemove', (e) => {
          if (!state) return
          state.x = e.clientX
          state.y = e.clientY
          if (!state.moved && (Math.abs(e.clientX - state.sx) > 4 || Math.abs(e.clientY - state.sy) > 4)) {
            state.moved = true
            win.clearTimeout(state.longTimer)
          }
          if (state.moved) {
            state.scroller.scrollLeft = state.scrollLeft - (e.clientX - state.sx)
            state.scroller.scrollTop = state.scrollTop - (e.clientY - state.sy)
            if (e.cancelable) e.preventDefault()
          }
        }, true)

        const reset = () => {
          if (!state) return
          win.clearTimeout(state.longTimer)
          state = null
          doc.body.classList.remove('mp-touch-dragging')
        }
        doc.addEventListener('mouseup', (e) => {
          const moved = state && state.moved
          reset()
          if (moved) {
            e.preventDefault()
            e.stopPropagation()
          }
        }, true)
        doc.addEventListener('mouseleave', reset, true)

        const style = doc.createElement('style')
        style.textContent = '.mp-touch-dragging, .mp-touch-dragging * { cursor: grabbing !important; user-select: none !important; }'
        doc.head.appendChild(style)
      }
    }
  }
</script>

<style scoped>
  .mp-page {
    display: flex;
    flex-direction: column;
    height: 100vh;
    background: #f0f2f5;
  }
  .mp-toolbar {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 10px;
    padding: 10px 16px;
    background: #fff;
    border-bottom: 1px solid #e8e8e8;
  }
  .mp-brand {
    font-size: 15px;
    font-weight: 600;
    color: #1890ff;
  }
  .mp-tip {
    color: #999;
    font-size: 12px;
  }
  .mp-stage {
    flex: 1;
    min-height: 0;
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
  }
  .mp-scaler {
    position: relative;
    flex-shrink: 0;
  }
  .mp-phone {
    position: absolute;
    top: 0;
    left: 0;
    transform-origin: top left;
    overflow: hidden;
    background: #fff;
    border: 10px solid #222;
    border-radius: 28px;
    box-sizing: border-box;
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
  }
  .mp-phone .mp-frame {
    display: block;
    width: 100%;
    height: 100%;
    border: 0;
    background: #fff;
  }
</style>
