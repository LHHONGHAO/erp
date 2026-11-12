<template>
  <div class="m-home">
    <!-- 数据卡片：一行三列，左右滑动查看更多 -->
    <div class="m-stats">
      <div class="m-stat-card" v-for="(item, index) in statCards" :key="item.key" :class="'m-stat-card--' + (index % 6)">
        <div class="m-stat-card__top">
          <span class="m-stat-card__label">{{ item.label }}</span>
          <span class="m-stat-card__icon"><a-icon :type="item.icon" /></span>
        </div>
        <div class="m-stat-card__value">{{ formatStat(statistics[item.key]) }}</div>
        <div class="m-stat-card__unit">元</div>
      </div>
    </div>

    <!-- 常用页面卡片 -->
    <div class="m-common">
      <div class="m-common__head">
        <span class="m-common__title">常用页面</span>
        <a-button class="m-common__add" size="small" shape="round" @click="openPicker">
          <a-icon type="plus" />添加
        </a-button>
      </div>
      <div v-if="commonPages.length > 0" class="m-common__grid" ref="commonGrid">
        <div
          class="m-common__item"
          v-for="(item, index) in commonPages"
          :key="item.url"
          :class="{ 'is-dragging': dragIndex === index, 'is-drag-over': dragOverIndex === index }"
          :draggable="true"
          @click="onItemClick(item, index)"
          @dragstart="onDragStart($event, index)"
          @dragover.prevent="onDragOver($event, index)"
          @dragend="onDragEnd"
          @touchstart="onTouchStart($event, index)"
          @touchmove="onTouchMove($event)"
          @touchend="onTouchEnd"
        >
          <span class="m-common__icon"><a-icon :type="item.icon || 'appstore'" /></span>
          <span class="m-common__text">{{ item.text }}</span>
        </div>
      </div>
      <div v-else class="m-common__empty" @click="openPicker">
        <a-icon type="plus-circle" />
        <span>暂无常用页面，点击添加</span>
      </div>
    </div>

    <!-- 页面卡片：按分类菜单切换，内容为该分类的子页面 -->
    <div class="m-pages" v-if="allGroups.length > 0">
      <div class="m-pages__tabs">
        <span
          class="m-pages__tab"
          v-for="group in allGroups"
          :key="group.key"
          :class="{ active: activeGroup && activeGroup.key === group.key }"
          @click="setActiveGroup(group.key)">
          <a-icon :type="group.icon || 'appstore'" /><span class="m-pages__tab-text">{{ group.text }}</span>
        </span>
      </div>
      <div class="m-pages__grid" v-if="activeGroup">
        <div class="m-pages__item" v-for="item in activeGroup.pages" :key="item.url" @click="openPage(item)">
          <span class="m-pages__icon"><a-icon :type="item.icon || 'appstore'" /></span>
          <span class="m-pages__text">{{ item.text }}</span>
        </div>
      </div>
    </div>

    <!-- 选择常用页面 -->
    <a-modal
      :visible="pickerVisible"
      title="选择常用页面"
      :width="'92%'"
      :style="{ maxWidth: '420px' }"
      :bodyStyle="{ padding: '0 12px' }"
      @cancel="closePicker">
      <div class="m-picker">
        <a-input
          v-model="keyword"
          allowClear
          placeholder="搜索页面名称"
          class="m-picker__search" />
        <div class="m-picker__list">
          <!-- 按侧边栏分类折叠展开 -->
          <div class="m-picker__group" v-for="group in filteredGroups" :key="group.key">
            <div class="m-picker__group-head" @click="toggleGroup(group.key)">
              <span class="m-picker__group-title">
                <a-icon :type="group.icon || 'appstore'" />
                <span class="m-picker__group-text">{{ group.text }}</span>
              </span>
              <span class="m-picker__group-meta">
                <span class="m-picker__group-count">{{ group.pages.length }}</span>
                <a-icon :type="isGroupOpen(group.key) ? 'down' : 'right'" />
              </span>
            </div>
            <div class="m-picker__group-body" v-show="isGroupOpen(group.key)">
              <div
                class="m-picker__row"
                v-for="item in group.pages"
                :key="item.url"
                @click="togglePick(item)">
                <span class="m-picker__check" :class="{ checked: pickedUrls.indexOf(item.url) > -1 }">
                  <a-icon v-if="pickedUrls.indexOf(item.url) > -1" type="check" />
                </span>
                <span class="m-picker__name">{{ item.text }}</span>
              </div>
            </div>
          </div>
          <div v-if="filteredGroups.length === 0" class="m-picker__empty">没有匹配的页面</div>
        </div>
      </div>
      <template slot="footer">
        <a-button @click="closePicker">取消</a-button>
        <a-button type="primary" @click="savePicker">保存</a-button>
      </template>
    </a-modal>
  </div>
</template>

<script>
  import Vue from 'vue'
  import { mapState } from 'vuex'
  import { getBuyAndSaleStatistics, getCommonPages, saveCommonPages } from '@/api/api'
  import { USER_ID } from '@/store/mutation-types'

  const STORAGE_PREFIX = 'mobile_common_pages_'

  export default {
    name: 'AnalysisMobile',
    data() {
      return {
        statistics: {},
        statCards: [
          { key: 'todaySale', label: '今日销售', icon: 'line-chart' },
          { key: 'todayRetailSale', label: '今日零售', icon: 'shopping-cart' },
          { key: 'todayBuy', label: '今日采购', icon: 'inbox' },
          { key: 'monthSale', label: '本月销售', icon: 'bar-chart' },
          { key: 'monthRetailSale', label: '本月零售', icon: 'shopping' },
          { key: 'monthBuy', label: '本月采购', icon: 'import' },
          { key: 'yesterdaySale', label: '昨日销售', icon: 'area-chart' },
          { key: 'yesterdayRetailSale', label: '昨日零售', icon: 'shop' },
          { key: 'yesterdayBuy', label: '昨日采购', icon: 'export' },
          { key: 'yearSale', label: '今年销售', icon: 'fund' },
          { key: 'yearRetailSale', label: '今年零售', icon: 'tags' },
          { key: 'yearBuy', label: '今年采购', icon: 'database' }
        ],
        commonPages: [],
        savedPages: [],
        // 新用户没有保存过常用页面时，首次进入按默认加载项生成
        defaultPending: false,
        pickerVisible: false,
        keyword: '',
        pickedUrls: [],
        collapsedGroups: [],
        activeGroupKey: '',
        // 拖拽排序相关
        dragIndex: -1,
        dragOverIndex: -1,
        touchDragIndex: -1,
        touchStartX: 0,
        touchStartY: 0,
        touchMoved: false
      }
    },
    computed: {
      ...mapState({
        permissionMenuList: state => state.user.permissionList
      }),
      // 按侧边栏一级分类分组
      allGroups() {
        let result = []
        let menus = this.permissionMenuList || []
        menus.forEach(root => {
          if (!root || root.hidden) {
            return
          }
          let pages = []
          this.collectPages(root, pages)
          pages = this.uniquePages(pages)
          if (pages.length > 0) {
            result.push({
              key: root.id === undefined || root.id === null ? root.text : String(root.id),
              text: root.text,
              icon: root.icon,
              pages: pages
            })
          }
        })
        return result
      },
      // 当前选中的分类（默认第一个）
      activeGroup() {
        if (!this.allGroups.length) {
          return null
        }
        return this.allGroups.find(group => group.key === this.activeGroupKey) || this.allGroups[0]
      },
      allPages() {
        let result = []
        let seen = {}
        this.allGroups.forEach(group => {
          group.pages.forEach(item => {
            if (!seen[item.url]) {
              seen[item.url] = true
              result.push(item)
            }
          })
        })
        return result
      },
      filteredGroups() {
        let keyword = (this.keyword || '').trim().toLowerCase()
        if (!keyword) {
          return this.allGroups
        }
        return this.allGroups.map(group => {
          let groupMatched = (group.text || '').toLowerCase().indexOf(keyword) > -1
          let pages = groupMatched ? group.pages : group.pages.filter(item => {
            return item.text.toLowerCase().indexOf(keyword) > -1
          })
          return Object.assign({}, group, { pages: pages })
        }).filter(group => group.pages.length > 0)
      },
      storageKey() {
        let userId = Vue.ls.get(USER_ID) || 'anonymous'
        return STORAGE_PREFIX + userId
      }
    },
    watch: {
      // 菜单是异步写入 Vuex 的，晚于本组件创建时必须重新过滤，
      // 否则常用页面会因拿不到有效菜单地址而被全部过滤成空白
      permissionMenuList() {
        this.applyCommonPages()
      }
    },
    created() {
      // 先用本地缓存渲染，避免云端/菜单加载期间常用页面出现"清空再加载"的闪烁
      this.savedPages = this.getCachedPages()
      this.commonPages = this.savedPages
      this.initStatistics()
      this.initCommonPages()
    },
    methods: {
      getCachedPages() {
        let pages = Vue.ls.get(this.storageKey)
        return Array.isArray(pages) ? pages : []
      },
      initStatistics() {
        getBuyAndSaleStatistics().then(res => {
          if (res && res.code === 200) {
            this.statistics = res.data || {}
          }
        })
      },
      formatStat(value) {
        if (value === undefined || value === null || value === '') {
          return '—'
        }
        if (typeof value === 'number') {
          let parts = value.toFixed(2).split('.')
          parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
          return parts.join('.')
        }
        return value
      },
      initCommonPages() {
        // 优先从云端加载，失败时回退到本地缓存
        getCommonPages().then(res => {
          let pages = []
          if (res && res.code === 200 && res.data) {
            try {
              pages = JSON.parse(res.data)
            } catch (e) {
              pages = []
            }
          }
          if (!pages || pages.length === 0) {
            // 云端无数据，尝试从本地缓存恢复
            pages = Vue.ls.get(this.storageKey) || []
          }
          this.savedPages = pages
          this.defaultPending = !pages || pages.length === 0
          this.applyCommonPages()
        }).catch(() => {
          // 接口失败，使用本地缓存
          let pages = Vue.ls.get(this.storageKey) || []
          this.savedPages = pages
          this.defaultPending = !pages || pages.length === 0
          this.applyCommonPages()
        })
      },
      // 默认加载项：新用户首次进入时自动展示的常用页面
      buildDefaultPages() {
        const defaultUrls = ['/system/address-recognize']
        return defaultUrls
          .map(url => this.allPages.find(item => item.url === url))
          .filter(item => !!item)
      },
      // 按当前菜单过滤出仍可打开的页面，菜单就绪后可重复调用
      applyCommonPages() {
        let validUrls = this.allPages.map(item => item.url)
        // 菜单尚未就绪时保持原样，避免常用页面被过滤成空白
        if (validUrls.length === 0) {
          this.commonPages = this.savedPages
          return
        }
        // 没有历史配置时写入默认加载项，之后用户可在选择弹窗中自行增删
        if (this.defaultPending) {
          let defaults = this.buildDefaultPages()
          if (defaults.length > 0) {
            this.defaultPending = false
            this.savedPages = defaults
            this.commonPages = defaults
            this.persistCommonPages()
            return
          }
        }
        this.commonPages = this.savedPages.filter(item => validUrls.indexOf(item.url) > -1)
      },
      // 保存常用页面到云端（同时更新本地缓存）
      persistCommonPages() {
        this.savedPages = this.commonPages
        Vue.ls.set(this.storageKey, this.commonPages)
        let pagesJson = JSON.stringify(this.commonPages)
        saveCommonPages({ pages: pagesJson }).catch(() => {
          // 保存失败不影响本地显示，但本地缓存已更新
        })
      },
      // 递归收集分类下的所有可打开页面
      collectPages(menu, result) {
        if (!menu) {
          return
        }
        if (menu.children && menu.children.length > 0) {
          menu.children.forEach(child => this.collectPages(child, result))
        }
        if (this.isOpenable(menu)) {
          result.push({
            url: menu.url,
            text: menu.text,
            icon: menu.icon
          })
        }
      },
      uniquePages(pages) {
        let seen = {}
        return pages.filter(item => {
          if (!item.url || seen[item.url]) {
            return false
          }
          seen[item.url] = true
          return true
        })
      },
      isOpenable(menu) {
        if (!menu || !menu.url || !menu.component) {
          return false
        }
        if (menu.url === '/dashboard/analysis' || menu.url === '/') {
          return false
        }
        // 布局容器本身不是具体页面
        return menu.component !== '/layouts/TabLayout' && menu.component !== '/layouts/RouteView'
      },
      openPage(item) {
        if (!item || !item.url) {
          return
        }
        if (item.url.indexOf('http') === 0) {
          window.open(item.url)
          return
        }
        this.$router.push(item.url).catch(() => {})
      },
      setActiveGroup(key) {
        this.activeGroupKey = key
      },
      openPicker() {
        this.pickedUrls = this.commonPages.map(item => item.url)
        this.keyword = ''
        // 分组默认全部关闭，点击标题展开
        this.collapsedGroups = this.allGroups.map(group => group.key)
        this.pickerVisible = true
      },
      closePicker() {
        this.pickerVisible = false
      },
      toggleGroup(key) {
        if ((this.keyword || '').trim()) {
          return
        }
        let index = this.collapsedGroups.indexOf(key)
        if (index > -1) {
          this.collapsedGroups.splice(index, 1)
        } else {
          this.collapsedGroups.push(key)
        }
      },
      isGroupOpen(key) {
        if ((this.keyword || '').trim()) {
          return true
        }
        return this.collapsedGroups.indexOf(key) === -1
      },
      togglePick(item) {
        let index = this.pickedUrls.indexOf(item.url)
        if (index > -1) {
          this.pickedUrls.splice(index, 1)
        } else {
          this.pickedUrls.push(item.url)
        }
      },
      savePicker() {
        // 按勾选顺序生成；菜单未就绪时退回已保存数据，避免误清空
        let pool = this.allPages.length > 0 ? this.allPages : this.savedPages
        let selected = this.pickedUrls
          .map(url => pool.find(item => item.url === url))
          .filter(item => !!item)
        this.commonPages = selected
        this.persistCommonPages()
        this.pickerVisible = false
      },
      // 点击常用页面（区分拖拽和点击）
      onItemClick(item, index) {
        if (this.touchMoved) {
          this.touchMoved = false
          return
        }
        this.openPage(item)
      },
      // ===== 拖拽排序（桌面端 HTML5 Drag API）=====
      onDragStart(e, index) {
        this.dragIndex = index
        e.dataTransfer.effectAllowed = 'move'
      },
      onDragOver(e, index) {
        if (e && e.dataTransfer) {
          e.dataTransfer.dropEffect = 'move'
        }
        this.dragOverIndex = index
      },
      onDragEnd() {
        if (this.dragIndex > -1 && this.dragOverIndex > -1 && this.dragIndex !== this.dragOverIndex) {
          this.reorderPages(this.dragIndex, this.dragOverIndex)
        }
        this.dragIndex = -1
        this.dragOverIndex = -1
      },
      // ===== 拖拽排序（移动端 Touch 事件）=====
      onTouchStart(e, index) {
        this.touchDragIndex = index
        this.touchMoved = false
        if (e.touches && e.touches[0]) {
          this.touchStartX = e.touches[0].clientX
          this.touchStartY = e.touches[0].clientY
        }
      },
      onTouchMove(e) {
        if (this.touchDragIndex < 0) return
        if (e.touches && e.touches[0]) {
          let dx = Math.abs(e.touches[0].clientX - this.touchStartX)
          let dy = Math.abs(e.touches[0].clientY - this.touchStartY)
          if (dx > 8 || dy > 8) {
            this.touchMoved = true
          }
        }
        // 拖拽时阻止页面滚动
        if (this.touchMoved && e.cancelable) {
          e.preventDefault()
        }
        // 计算手指当前悬停在哪个 item 上
        let touch = e.touches[0]
        let grid = this.$refs.commonGrid
        if (grid) {
          let items = grid.querySelectorAll('.m-common__item')
          for (let i = 0; i < items.length; i++) {
            let rect = items[i].getBoundingClientRect()
            if (touch.clientX >= rect.left && touch.clientX <= rect.right &&
                touch.clientY >= rect.top && touch.clientY <= rect.bottom) {
              this.dragOverIndex = i
              this.dragIndex = this.touchDragIndex
              break
            }
          }
        }
      },
      onTouchEnd() {
        if (this.touchDragIndex > -1 && this.dragOverIndex > -1 && this.touchDragIndex !== this.dragOverIndex) {
          this.reorderPages(this.touchDragIndex, this.dragOverIndex)
        }
        this.touchDragIndex = -1
        this.dragIndex = -1
        this.dragOverIndex = -1
      },
      // 重新排序并保存到云端
      reorderPages(fromIndex, toIndex) {
        let list = this.commonPages.slice()
        // 索引越界时直接忽略，避免 splice 拿到 undefined 导致渲染崩溃
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= list.length || toIndex >= list.length) {
          return
        }
        let item = list.splice(fromIndex, 1)[0]
        list.splice(toIndex, 0, item)
        this.commonPages = list
        this.persistCommonPages()
      }
    }
  }
</script>

<style lang="less" scoped>
  .m-home {
    min-height: 100vh;
    padding: 12px 12px 20px;
    background: #f4f6fb;
    box-sizing: border-box;
  }

  /* 数据卡片：一行三列，左右滑动 */
  .m-stats {
    display: flex;
    gap: 10px;
    margin: 2px 0 16px;
    padding: 2px 0 8px;
    overflow-x: auto;
    overflow-y: hidden;
    scroll-snap-type: x mandatory;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .m-stat-card {
    flex: 0 0 calc((100% - 20px) / 3);
    aspect-ratio: 1 / 1;
    scroll-snap-align: start;
    box-sizing: border-box;
    border-radius: 16px;
    padding: 10px;
    color: #fff;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    overflow: hidden;
    box-shadow: 0 6px 16px rgba(31, 58, 147, 0.12);

    &--0 { background: linear-gradient(135deg, #5b8dff, #2f5fe0); }
    &--1 { background: linear-gradient(135deg, #ffb44d, #ff7a18); }
    &--2 { background: linear-gradient(135deg, #41d99a, #0ea06b); }
    &--3 { background: linear-gradient(135deg, #a78bfa, #7048e8); }
    &--4 { background: linear-gradient(135deg, #36d1ea, #0b8fb2); }
    &--5 { background: linear-gradient(135deg, #fb9bb3, #e11d48); }
  }

  .m-stat-card__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  .m-stat-card__label {
    font-size: 12px;
    opacity: 0.92;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .m-stat-card__icon {
    width: 24px;
    height: 24px;
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.22);
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
  }

  .m-stat-card__value {
    font-size: 17px;
    font-weight: 700;
    line-height: 1.15;
    word-break: break-all;
  }

  .m-stat-card__unit {
    font-size: 10px;
    opacity: 0.8;
    margin-top: -4px;
  }

  /* 常用页面 */
  .m-common {
    background: #fff;
    border-radius: 18px;
    padding: 14px 12px 16px;
    box-shadow: 0 4px 14px rgba(31, 58, 147, 0.06);
  }

  .m-common__head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;
  }

  .m-common__title {
    font-size: 16px;
    font-weight: 600;
    color: #1f2937;
  }

  .m-common__add {
    color: #2f5fe0;
    border-color: #c7d6ff;
    background: #f2f6ff;
  }

  .m-common__grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 10px;
  }

  .m-common__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 10px 2px;
    border-radius: 14px;
    background: #f7f9ff;
    transition: transform 0.15s ease, box-shadow 0.15s ease, opacity 0.15s ease;
    -webkit-user-select: none;
    user-select: none;
    touch-action: none;

    &.is-dragging {
      opacity: 0.4;
      transform: scale(0.92);
    }

    &.is-drag-over {
      box-shadow: 0 0 0 2px #2f5fe0, 0 4px 12px rgba(47, 95, 224, 0.25);
      background: #eaf1ff;
    }
  }

  .m-common__icon {
    width: 38px;
    height: 38px;
    border-radius: 12px;
    background: linear-gradient(135deg, #eaf1ff, #dbe7ff);
    color: #2f5fe0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    margin-bottom: 6px;
  }

  .m-common__text {
    font-size: 12px;
    color: #334155;
    text-align: center;
    line-height: 16px;
    max-height: 32px;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .m-common__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 22px 0;
    color: #94a3b8;
    font-size: 13px;

    .anticon {
      font-size: 26px;
      color: #c7d6ff;
      margin-bottom: 6px;
    }
  }

  /* 页面卡片：按分类切换 */
  .m-pages {
    margin-top: 12px;
    background: #fff;
    border-radius: 18px;
    padding: 14px 12px 16px;
    box-shadow: 0 4px 14px rgba(31, 58, 147, 0.06);
  }

  .m-pages__tabs {
    display: flex;
    gap: 8px;
    margin: 0 -12px 14px;
    padding: 0 12px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  .m-pages__tab {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    padding: 6px 12px;
    border-radius: 999px;
    background: #f5f8ff;
    color: #475569;
    font-size: 13px;
    white-space: nowrap;

    .anticon {
      margin-right: 4px;
      font-size: 13px;
      color: #64748b;
    }

    &.active {
      background: linear-gradient(135deg, #5b8dff, #2f5fe0);
      color: #fff;
      font-weight: 600;

      .anticon {
        color: #fff;
      }
    }
  }

  .m-pages__grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 10px;
  }

  .m-pages__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 10px 2px;
    border-radius: 14px;
    background: #f7f9ff;
  }

  .m-pages__icon {
    width: 38px;
    height: 38px;
    border-radius: 12px;
    background: linear-gradient(135deg, #eaf1ff, #dbe7ff);
    color: #2f5fe0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    margin-bottom: 6px;
  }

  .m-pages__text {
    font-size: 12px;
    color: #334155;
    text-align: center;
    line-height: 16px;
    max-height: 32px;
    overflow: hidden;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  /* 选择弹窗 */
  .m-picker__search {
    margin: 8px 0 4px;
  }

  .m-picker__list {
    max-height: 50vh;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
  }

  .m-picker__group-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 11px 8px;
    margin-top: 8px;
    border-radius: 10px;
    background: #f5f8ff;
  }

  .m-picker__group-title {
    display: flex;
    align-items: center;
    font-size: 14px;
    font-weight: 600;
    color: #1f2937;

    .anticon {
      color: #2f5fe0;
      margin-right: 8px;
      font-size: 15px;
    }
  }

  .m-picker__group-meta {
    display: flex;
    align-items: center;
    font-size: 12px;
    color: #94a3b8;
  }

  .m-picker__group-count {
    min-width: 18px;
    height: 18px;
    line-height: 18px;
    padding: 0 6px;
    margin-right: 6px;
    border-radius: 9px;
    background: #e8efff;
    color: #2f5fe0;
    font-size: 11px;
    text-align: center;
  }

  .m-picker__group-body {
    padding-left: 6px;
  }

  .m-picker__row {
    display: flex;
    align-items: center;
    padding: 11px 4px;
    border-bottom: 1px solid #f1f3f7;
  }

  .m-picker__check {
    flex: 0 0 auto;
    width: 18px;
    height: 18px;
    border-radius: 6px;
    border: 1px solid #cbd5e1;
    margin-right: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    color: #fff;

    &.checked {
      background: #2f5fe0;
      border-color: #2f5fe0;
    }
  }

  .m-picker__name {
    flex: 1 1 auto;
    font-size: 14px;
    color: #1f2937;
  }

  .m-picker__empty {
    padding: 24px 0;
    text-align: center;
    color: #94a3b8;
    font-size: 13px;
  }
</style>
