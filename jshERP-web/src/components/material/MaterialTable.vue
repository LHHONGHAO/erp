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

  <!-- 手机浏览器：图片 / 名称规格 / 操作按钮 纵向排列的卡片列表 -->
  <div v-if="isMobileView" class="mt-mobile-list">
    <div v-for="row in mobileRows" :key="row.skuRowKey || row.id" class="mt-mobile-card">
      <img v-if="resolveImg(row)" :src="resolveImg(row)" class="mt-mobile-img" />
      <div v-else class="mt-mobile-img mt-mobile-img-empty">无图</div>
      <div class="mt-mobile-body">
        <div class="mt-mobile-title">
          {{ row.name }}
          <a-tag v-if="row.enableSerialNumber == 1" color="orange">序</a-tag>
          <a-tag v-if="row.enableBatchNumber == 1" color="orange">批</a-tag>
        </div>
        <div class="mt-mobile-code">{{ resolveCode(row) }}</div>
        <div class="mt-mobile-spec">{{ resolveSpec(row) }}</div>
        <div class="mt-mobile-meta">
          库存 {{ row.stock == null ? '-' : row.stock }}　零售价 {{ row.retailPrice == null ? '-' : row.retailPrice }}
        </div>
        <div v-if="visibleActions(row).length > 0" class="mt-mobile-actions">
          <a-button
            v-for="(act, i) in visibleActions(row)"
            :key="'mact-' + i"
            size="small"
            @click="onAction(act, row)">{{ act.title }}</a-button>
        </div>
      </div>
    </div>
    <div v-if="mobileRows.length === 0" class="mt-mobile-empty">暂无数据</div>
    <div v-if="mobilePagination && mobilePagination.total > 0" class="mt-mobile-pager">
      <a-pagination
        size="small"
        :current="mobilePagination.current"
        :page-size="mobilePagination.pageSize"
        :total="mobilePagination.total"
        :show-total="mobilePagination.showTotal"
        @change="onMobilePageChange" />
    </div>
  </div>
  </div>
</template>

<script>
  import Vue from 'vue'
  import DataTable from '@/components/DataTable'
  import { MATERIAL_COLUMNS, buildTableColumns, defaultColumnKeys, actionColumnWidth, CODE_MODES } from '@/views/material/config/materialColumns'
  import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'

  /** 内置插槽名：父组件透传时跳过，避免同名插槽冲突 */
  const BUILTIN_SLOTS = ['mtImage', 'mtName', 'mtSpec', 'mtCode', 'mtStock', 'mtAction']

  export default {
    name: 'MaterialTable',
    components: { DataTable },
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
        isMobileView: false
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
      }
    },
    watch: {
      codeMode() {
        this.syncCodeTitle()
      }
    },
    mounted() {
      this.syncCodeTitle()
      // 手机浏览器（<=768px）改为卡片列表：图片、规格、操作按钮纵向排列
      this.isMobileView = window.innerWidth <= 768
      window.addEventListener('resize', this.onWindowResize)
      // 列被重建（配置中心回填、列设置调整）后重新套用码列标题，避免标题被还原
      this.$watch(
        () => (this.$refs.dataTable ? this.$refs.dataTable.displayColumns : null),
        () => this.applyCodeTitle()
      )
    },
    beforeDestroy() {
      window.removeEventListener('resize', this.onWindowResize)
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
      /** 手机端翻页：按 a-table 的 change 签名通知父组件 */
      onMobilePageChange(page) {
        const size = this.mobilePagination ? this.mobilePagination.pageSize : 10
        this.$emit('change', { current: page, pageSize: size }, {}, {})
      },
      resolveImg(record) {
        return getRowImgUrl(record)
      },
      resolveSpec(record) {
        return getSpecDisplay(record)
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
  /* === 手机端卡片列表：图片 → 名称规格 → 库存价格 → 操作按钮 依次向下排列 === */
  .mt-mobile-list {
    padding: 0;
  }
  .mt-mobile-card {
    display: flex;
    flex-direction: row;
    align-items: flex-start;
    background: #fff;
    border: 1px solid #F0F2F5;
    border-radius: 8px;
    padding: 8px;
    margin-bottom: 8px;
  }
  .mt-mobile-img {
    width: 88px;
    height: 88px;
    object-fit: cover;
    border-radius: 8px;
    flex-shrink: 0;
    margin: 0 10px 0 0;
  }
  .mt-mobile-img-empty {
    display: flex;
    align-items: center;
    justify-content: center;
    background: #FAFBFD;
    color: #bbb;
    font-size: 12px;
  }
  .mt-mobile-body {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .mt-mobile-title {
    font-size: 15px;
    font-weight: 600;
    color: #1D2129;
    word-break: break-all;
  }
  .mt-mobile-code {
    margin-top: 4px;
    font-size: 12px;
    color: #86909C;
    font-family: Consolas, Monaco, monospace;
    word-break: break-all;
  }
  .mt-mobile-spec {
    margin-top: 6px;
    font-size: 13px;
    color: #4F535C;
    white-space: pre-line;
    line-height: 18px;
    text-align: left;
    word-break: break-all;
  }
  .mt-mobile-meta {
    margin-top: 6px;
    font-size: 12px;
    color: #86909C;
  }
  .mt-mobile-actions {
    display: flex;
    flex-wrap: wrap;
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px dashed #F0F2F5;
  }
  .mt-mobile-actions > * {
    margin: 0 8px 6px 0;
  }
  .mt-mobile-empty {
    text-align: center;
    padding: 30px 0;
    color: #bbb;
    font-size: 13px;
  }
  .mt-mobile-pager {
    display: flex;
    justify-content: center;
    padding: 10px 0 4px;
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
