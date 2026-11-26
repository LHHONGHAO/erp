<template>
  <div class="material-table-wrap">
  <data-table
    v-if="!isMobileView"
    ref="dataTable"
    v-bind="$attrs"
    :extra-columns="tableColumns"
    :default-columns="resolvedDefaultColumns"
    v-on="$listeners">
    <!-- 父组件自定义列插槽原样透传（内置渲染以外） -->
    <template v-for="name in forwardSlotNames" v-slot:[name]="scope">
      <slot :name="name" v-bind="scope"></slot>
    </template>

    <!-- 内置：图片（100*80、圆角12） -->
    <template slot="mtImage" slot-scope="text, record">
      <img v-if="resolveImg(record)" :src="resolveImg(record)" class="mt-row-img" />
      <span v-else class="mt-row-img-placeholder">无图</span>
    </template>

    <!-- 内置：商品名称（含序/批标记） -->
    <template slot="mtName" slot-scope="text, record">
      {{ record.name }}
      <a-tag v-if="record.enableSerialNumber == 1" color="orange">序</a-tag>
      <a-tag v-if="record.enableBatchNumber == 1" color="orange">批</a-tag>
    </template>

    <!-- 内置：规格显示（每个规格单独一行） -->
    <template slot="mtSpec" slot-scope="text, record">
      <div class="mt-spec-cell">{{ resolveSpec(record) }}</div>
    </template>

    <!-- 内置：码列（商品码 / SKU码 / SN码 三合一，标题与取值随 codeMode 变化） -->
    <template slot="mtCode" slot-scope="text, record">
      {{ record._dataTableSummary ? text : resolveCode(record) }}
    </template>

    <!-- 内置：库存（悬浮显示大单位库存） -->
    <template slot="mtStock" slot-scope="text, record">
      <a-tooltip :title="record.bigUnitStock || ''">{{ text }}</a-tooltip>
    </template>

    <!-- 内置：操作列 -->
    <template slot="mtAction" slot-scope="text, record">
      <span v-if="visibleActions(record).length > 0" class="mt-action-cell">
        <template v-for="(act, index) in visibleActions(record)">
          <a :key="'act-' + index" @click="onAction(act, record)">{{ act.title }}</a>
          <a-divider v-if="index < visibleActions(record).length - 1" :key="'div-' + index" type="vertical" />
        </template>
      </span>
    </template>
  </data-table>

  <!-- 手机浏览器：标准商品卡片（图片 / 名称规格 / 零售价库存 / 操作按钮） -->
  <div v-if="isMobileView" class="mt-mobile-list">
    <!-- 标准商品卡片（商品选择 / 商品信息 / 销售开单共用） -->
    <mobile-product-card
      v-for="(row, rowIndex) in mobileList"
      :key="row.skuRowKey || row.id"
      :name="row.name"
      :img-url="resolveImg(row)"
      :specs="mobileSpecLines(row)"
      :items="mobileInfoItems(row)"
      :price="row.commodityDecimal"
      :alt="rowIndex % 2 === 1">
      <template #nameExtra>
        <a-tag v-if="row.enableSerialNumber == 1" color="orange">序</a-tag>
        <a-tag v-if="row.enableBatchNumber == 1" color="orange">批</a-tag>
      </template>
      <template #side>
        <div class="mt-mobile-actions">
        <a-button
          v-for="(act, i) in mobileActions(row)"
          :key="'mact-' + i"
          class="mt-action-icon-btn"
          type="link"
          size="small"
          :icon="act.icon"
          :title="act.title"
          @click="onAction(act, row)"></a-button>
        </div>
      </template>
    </mobile-product-card>
    <div v-if="mobileList.length === 0 && !mobileLoading" class="mt-mobile-empty">暂无数据</div>
    <!-- 下拉（滚动到底部）自动加载：不再显示分页 -->
    <div ref="mobileSentinel" class="mt-mobile-sentinel"></div>
    <div v-if="mobileLoading" class="mt-mobile-loadmore">
      <a-spin size="small" />
      <span class="mt-mobile-loadtext">加载中...</span>
    </div>
    <div v-else-if="mobileList.length > 0 && !mobileHasMore" class="mt-mobile-no-more">没有更多了</div>
  </div>
  </div>
</template>

<script>
  import Vue from 'vue'
  import DataTable from '@/components/DataTable'
  import MobileProductCard from '@/components/mobile/MobileProductCard'
  import { MATERIAL_COLUMNS, buildTableColumns, defaultColumnKeys, actionColumnWidth, CODE_MODES } from '@/views/material/config/materialColumns'
  import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'

  /** 内置插槽名：父组件透传时跳过，避免同名插槽冲突 */
  const BUILTIN_SLOTS = ['mtImage', 'mtName', 'mtSpec', 'mtCode', 'mtStock', 'mtAction']

  export default {
    name: 'MaterialTable',
    components: { DataTable, MobileProductCard },
    props: {
      /** 列配置（默认使用商品/库存统一列配置） */
      columns: { type: Array, default: () => MATERIAL_COLUMNS },
      /** 数据粒度：spu=商品(单类) / sku=SKU(单品) / skuDepot=SKU+仓库(库存)，为空不过滤 */
      dimension: { type: String, default: '' },
      /** 默认显示列（field 数组），不传则按列配置的 defaultVisible + 默认顺序 */
      defaultColumns: { type: Array, default: undefined },
      /** 操作列按钮：[{ title, click(record), show(record)? }] */
      actions: { type: Array, default: () => [] },
      /** 码列三合一：把 SPU码/SKU码 合并为一列，取值与标题随 codeMode 变化（库存页使用） */
      mergeCode: { type: Boolean, default: false },
      /** 码列显示方式：spu=商品码 / sku=SKU码 / unit=SN码 */
      codeMode: { type: String, default: 'spu' }
    },
    data() {
      return {
        /** 当前窗口宽度是否属于手机浏览器（<=768px） */
        isMobileView: false,
        /** 手机端下拉加载已累积的数据（翻页时追加，不再整页替换） */
        mobileList: [],
        /** 已加载到的页码 */
        mobileLoadedPage: 0,
        /** 服务端返回的总条数（父级分页对象上的 total，单独留存避免被覆盖丢失） */
        mobileTotal: 0,
        /** 是否正在触发下拉加载（防止重复请求） */
        mobileLoadingMore: false,
        /** 底部哨兵元素的可见性监听器 */
        mobileObserver: null,
        /** 手机端滚动容器及滚动兜底监听 */
        mobileScrollEl: null,
        mobileScrollHandler: null,
        /** 手机端卡片右上角多选的已选行 key 数组 */
        mobileSelectedKeys: []
      }
    },
    computed: {
      /** 按粒度过滤并转换为 a-table 列；操作列宽度按按钮数量重新计算 */
      tableColumns() {
        let cols = buildTableColumns(this.dimension, this.columns)
        // 码列三合一：去掉 SPU码 / SKU码 两列，在原位置插入一列动态码列
        if (this.mergeCode) {
          const firstIdx = cols.findIndex(col => col.dataIndex === 'code' || col.dataIndex === 'skuCode')
          cols = cols.filter(col => col.dataIndex !== 'code' && col.dataIndex !== 'skuCode')
          cols.splice(firstIdx > -1 ? firstIdx : 0, 0, {
            title: CODE_MODES.spu.title,
            dataIndex: 'materialCode',
            key: 'materialCode',
            width: 150,
            ellipsis: true,
            summaryRender: true,
            scopedSlots: { customRender: 'mtCode' }
          })
        }
        // 未配置操作按钮的场景（如选商品弹窗）不显示操作列
        if (this.actions.length === 0) {
          return cols.filter(col => col.dataIndex !== 'action')
        }
        const width = actionColumnWidth(this.actions)
        cols.forEach(col => {
          if (col.dataIndex === 'action') col.width = width
        })
        return cols
      },
      /** 默认显示列 */
      resolvedDefaultColumns() {
        const allKeys = this.tableColumns.map(c => c.dataIndex)
        let defaults = (this.defaultColumns && this.defaultColumns.length > 0)
          ? this.defaultColumns
          : defaultColumnKeys(this.dimension, this.columns)
        // 码列三合一后，原有的 code / skuCode 统一映射为 materialCode（只保留一次）
        if (this.mergeCode) {
          const mapped = []
          defaults.forEach(k => {
            if (k === 'code' || k === 'skuCode') {
              if (mapped.indexOf('materialCode') === -1) mapped.push('materialCode')
            } else {
              mapped.push(k)
            }
          })
          defaults = mapped
        }
        return defaults.filter(k => allKeys.indexOf(k) > -1)
      },
      /** 需要透传给 DataTable 的父组件插槽名 */
      forwardSlotNames() {
        return Object.keys(this.$scopedSlots).filter(name => BUILTIN_SLOTS.indexOf(name) === -1)
      },
      /** 手机端卡片列表数据（取父组件传给表格的 dataSource） */
      mobileRows() {
        return this.$attrs['data-source'] || this.$attrs.dataSource || []
      },
      /** 手机端分页信息 */
      mobilePagination() {
        const p = this.$attrs.pagination
        return p && typeof p === 'object' ? p : null
      },
      /** 是否还有下一页可下拉加载：按「已加载条数 < 总条数」判断，不依赖父级当前页码 */
      mobileHasMore() {
        if (!this.mobilePagination) return false
        return this.mobileTotal > this.mobileList.length
      },
      /** 表格加载状态（父组件通过 loading 传入） */
      mobileLoading() {
        return !!(this.$attrs.loading)
      }
    },
    watch: {
      codeMode() {
        this.syncCodeTitle()
      },
      /** 父组件数据变化：首页整体替换，后续页追加到列表尾部 */
      mobileRows: {
        handler(rows) {
          this.syncMobileList(rows || [])
        },
        immediate: true
      },
      /** 桌面表格 / 手机卡片切换时重新绑定底部哨兵监听 */
      isMobileView(visible) {
        if (visible) {
          this.$nextTick(this.bindMobileObserver)
        } else {
          this.unbindMobileObserver()
        }
      }
    },
    mounted() {
      this.syncCodeTitle()
      // 手机浏览器（<=768px）改为卡片列表：图片、规格、操作按钮纵向排列
      this.isMobileView = window.innerWidth <= 768
      window.addEventListener('resize', this.onWindowResize)
      this.$nextTick(this.bindMobileObserver)
      // 列被重建（配置中心回填、列设置调整）后重新套用码列标题，避免标题被还原
      this.$watch(
        () => (this.$refs.dataTable ? this.$refs.dataTable.displayColumns : null),
        () => this.applyCodeTitle()
      )
    },
    beforeDestroy() {
      window.removeEventListener('resize', this.onWindowResize)
      this.unbindMobileObserver()
    },
    created() {
      // 兼容旧版本列设置：原「规格JSON」列已改为「规格显示」列
      const storageKey = this.$attrs.storageKey || this.$attrs['storage-key']
      if (!storageKey) return
      const saved = Vue.ls.get(storageKey)
      if (saved && typeof saved === 'string' && saved.indexOf('specJson') > -1) {
        Vue.ls.set(storageKey, saved.split(',').map(k => k === 'specJson' ? 'specDisplay' : k))
      }
    },
    methods: {
      /** 窗口尺寸变化时切换桌面表格 / 手机卡片布局 */
      onWindowResize() {
        const mobile = window.innerWidth <= 768
        if (mobile !== this.isMobileView) {
          this.isMobileView = mobile
        }
      },
      /** 手机端数据同步：普通加载整体替换；触底加载追加到列表尾部 */
      syncMobileList(rows) {
        const keyOf = row => row.skuRowKey || row.id
        const p = this.mobilePagination
        if (p && p.total != null) {
          this.mobileTotal = Number(p.total) || this.mobileTotal
        }
        if (this.mobileLoadingMore) {
          // 触底加载：追加新行（按 key 去重）
          const exists = {}
          this.mobileList.forEach(row => { exists[keyOf(row)] = true })
          const appended = this.mobileList.slice()
          rows.forEach(row => {
            const key = keyOf(row)
            if (!exists[key]) {
              exists[key] = true
              appended.push(row)
            }
          })
          // 接口返回的全是已存在的行时说明已无法再加载，直接结束
          if (appended.length === this.mobileList.length) {
            this.mobileTotal = this.mobileList.length
          } else {
            this.mobileLoadedPage += 1
          }
          this.mobileList = appended
        } else {
          this.mobileList = rows.slice()
          this.mobileLoadedPage = 1
          if (p && p.total != null) this.mobileTotal = Number(p.total) || 0
        }
        this.mobileLoadingMore = false
        // 数据不足一屏时继续自动补载
        this.$nextTick(this.checkMobileSentinel)
      },
      /** 手机端滚动容器：优先取可滚动祖先，取不到则用窗口 */
      resolveMobileScrollEl(el) {
        let node = el && el.parentElement
        while (node && node !== document.body && node !== document.documentElement) {
          const style = window.getComputedStyle(node)
          if (/(auto|scroll)/.test(style.overflowY)) return node
          node = node.parentElement
        }
        return window
      },
      /** 监听底部哨兵：滚动到列表末尾时自动加载下一页 */
      bindMobileObserver() {
        this.unbindMobileObserver()
        if (!this.isMobileView) return
        const el = this.$refs.mobileSentinel
        if (!el) return
        this.mobileScrollEl = this.resolveMobileScrollEl(el)
        // 1. 滚动兜底：任意滚动容器的触底都触发加载，避免 IntersectionObserver 不回调
        this.mobileScrollHandler = () => this.checkMobileSentinel()
        this.mobileScrollEl.addEventListener('scroll', this.mobileScrollHandler, { passive: true })
        // 2. 哨兵可见性：进入可视区域即加载
        if (typeof IntersectionObserver !== 'undefined') {
          const root = this.mobileScrollEl === window ? null : this.mobileScrollEl
          this.mobileObserver = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) this.loadMoreMobile()
          }, { root: root, rootMargin: '0px 0px 80px 0px' })
          this.mobileObserver.observe(el)
        }
      },
      unbindMobileObserver() {
        if (this.mobileObserver) {
          this.mobileObserver.disconnect()
          this.mobileObserver = null
        }
        if (this.mobileScrollHandler && this.mobileScrollEl) {
          this.mobileScrollEl.removeEventListener('scroll', this.mobileScrollHandler)
        }
        this.mobileScrollHandler = null
        this.mobileScrollEl = null
      },
      /** 哨兵是否已进入可视区域（滚动兜底 + 数据到达后补载都用它） */
      checkMobileSentinel() {
        if (!this.isMobileView) return
        const el = this.$refs.mobileSentinel
        if (!el) return
        const rect = el.getBoundingClientRect()
        const containerBottom = (this.mobileScrollEl && this.mobileScrollEl !== window)
          ? this.mobileScrollEl.getBoundingClientRect().bottom
          : (window.innerHeight || document.documentElement.clientHeight)
        if (rect.top <= containerBottom + 80) this.loadMoreMobile()
      },
      /** 加载下一页：按 a-table 的 change 签名通知父组件 */
      loadMoreMobile() {
        if (this.mobileLoadingMore || this.mobileLoading) return
        if (!this.mobileHasMore) return
        const p = this.mobilePagination
        this.mobileLoadingMore = true
        this.$emit('change', {
          current: (this.mobileLoadedPage || 1) + 1,
          pageSize: (p && p.pageSize) || 10
        }, {}, {})
      },
      resolveImg(record) {
        return getRowImgUrl(record)
      },
      resolveSpec(record) {
        return getSpecDisplay(record)
      },
      /**
       * 手机端规格展示：每个规格属性一行，值只取第一个（多值以 / 分隔）
       * 与「添加商品」弹层的展示保持一致
       */
      mobileSpecLines(record) {
        const lines = []
        String(this.resolveSpec(record) || '').split('\n').forEach(function(line) {
          const text = line.trim()
          if (!text) return
          let label = ''
          let value = text
          const idx = text.search(/[：:]/)
          if (idx > -1) {
            label = text.slice(0, idx).trim()
            value = text.slice(idx + 1).trim()
          }
          value = value.split('/')[0].trim()
          if (!value) return
          lines.push({ label: label, value: value })
        })
        return lines
      },
      /** 标准卡片信息行：库存（零售价已移到名称行右侧） */
      mobileInfoItems(record) {
        return [
          { label: '库存', value: record.stock == null ? '-' : record.stock }
        ]
      },
      /** 码列取值：单类→SPU码 / 单品→SKU码 / 单件→SN码，缺失时回退商品条码 */
      resolveCode(record) {
        if (!record) return ''
        const mode = CODE_MODES[this.codeMode] || CODE_MODES.spu
        return record[mode.field] || record.mBarCode || ''
      },
      /** 只改码列标题（不重建列，避免切换显示方式时重置列设置） */
      syncCodeTitle() {
        if (!this.mergeCode) return
        this.$nextTick(() => this.applyCodeTitle())
      },
      /** 码列标题与当前显示方式对齐（标题已一致时不重复触发重建） */
      applyCodeTitle() {
        const dt = this.$refs.dataTable
        if (!dt || !this.mergeCode) return
        const mode = CODE_MODES[this.codeMode] || CODE_MODES.spu
        const col = (dt.allColumns || []).find(c => c.dataIndex === 'materialCode')
        if (col && col.title !== mode.title) {
          dt.updateColumnTitle('materialCode', mode.title)
        }
      },
      visibleActions(record) {
        return (this.actions || []).filter(act => !act.show || act.show(record))
      },
      /** 手机端卡片操作：不显示编辑按钮（编辑入口在电脑端操作列） */
      mobileActions(record) {
        return this.visibleActions(record).filter(act => act.icon !== 'edit')
      },
      /** 手机端卡片多选：切换当前行勾选状态，通知父组件 */
      onPickMobileRow(row) {
        var key = row.skuRowKey || row.id
        var idx = this.mobileSelectedKeys.indexOf(key)
        if (idx > -1) {
          this.mobileSelectedKeys.splice(idx, 1)
        } else {
          this.mobileSelectedKeys.push(key)
        }
        // 构建已选行数组
        var selectedRows = this.mobileList.filter(r =>
          this.mobileSelectedKeys.indexOf(r.skuRowKey || r.id) > -1
        )
        this.$emit('row-selection-change', this.mobileSelectedKeys.slice(), selectedRows)
        this.$emit('row-select', row)
      },
      /** 暴露给父组件：清空手机端选中 */
      clearMobileSelection() {
        this.mobileSelectedKeys = []
      },
      onAction(act, record) {
        if (act && typeof act.click === 'function') {
          act.click(record)
        }
      },
      /** 暴露给父组件：当前实际显示的列（用于导出） */
      getVisibleColumns() {
        const dt = this.$refs.dataTable
        return (dt && dt.displayColumns) || []
      },
      /** 暴露给父组件：恢复默认列 */
      resetColumns() {
        this.$refs.dataTable.resetColumns()
      }
    }
  }
</script>

<style scoped>
  /* === 手机端：标准商品卡片列表（图片 / 名称规格 / 零售价库存 / 操作按钮） === */
  /* 多选：无外框，仅保留勾图标；未选灰色，选中主题色 */
  .mt-mobile-check {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    font-size: 20px;
    line-height: 1;
    color: #c8c9cc;
    cursor: pointer;
    transition: color .2s;
  }
  .mt-mobile-check.is-checked {
    color: #1890ff;
  }
  /* 操作列：占满卡片高度，多选 / 编辑 / 打印 垂直等距、水平居中，上下留白与卡片内边距对称 */
  .mt-mobile-actions {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: space-between;
    align-self: stretch;
    width: 40px;
    flex-shrink: 0;
  }
  .mt-action-icon-btn {
    color: #595959;
    width: auto !important;
    height: auto !important;
    padding: 2px !important;
    font-size: 22px !important;
    line-height: 1;
    border: none !important;
    box-shadow: none !important;
    background: transparent !important;
  }
  .mt-action-icon-btn:hover,
  .mt-action-icon-btn:focus {
    color: #1890ff;
    background: transparent !important;
  }
  .mt-mobile-empty {
    text-align: center;
    padding: 30px 0;
    color: #bbb;
    font-size: 13px;
  }
  /* 下拉加载：底部哨兵 + 加载中 / 到底提示 */
  .mt-mobile-sentinel {
    height: 1px;
  }
  .mt-mobile-loadmore,
  .mt-mobile-no-more {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 6px;
    padding: 12px 0;
    color: #999;
    font-size: 12px;
  }
  .mt-row-img {
    width: 100px;
    height: 80px;
    object-fit: cover;
    border-radius: 12px;
    vertical-align: middle;
  }
  .mt-row-img-placeholder {
    font-size: 12px;
    color: #ccc;
  }
  .mt-spec-cell {
    white-space: pre-line;
    line-height: 18px;
    word-break: break-all;
    text-align: left;
  }
  .mt-action-cell {
    white-space: nowrap;
  }
</style>

<!-- 操作列：宽度已按「按钮总宽 + 20px」计算，取消单元格内边距，保证多出来的正好 20px -->
<style>
  /* 表头另有一条 .ant-table-header > table > .ant-table-thead > tr > th 的 !important 内边距，这里用同等结构提高优先级 */
  .ant-table-header > table > .ant-table-thead > tr > th.mt-action-col,
  .ant-table-thead > tr > th.mt-action-col.mt-action-col,
  .ant-table-tbody > tr > td.mt-action-col.mt-action-col {
    padding-left: 0 !important;
    padding-right: 0 !important;
  }
</style>
