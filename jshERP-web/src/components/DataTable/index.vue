<template>
  <div class="data-table-wrapper">
    <!-- 工具栏：左侧 slot + 右侧列设置（无内容时不渲染，避免空行） -->
    <div class="data-table-toolbar" v-if="hasToolbar">
      <div class="data-table-toolbar-left">
        <slot name="toolbar"></slot>
      </div>
      <div class="data-table-toolbar-right" v-if="showColumnSetting && settingColumns.length > 0">
        <a-popover trigger="click" placement="bottom" :overlayStyle="{ zIndex: 1050 }">
          <template slot="content">
            <a-checkbox-group @change="onColChange" v-model="settingDataIndex">
              <div style="width: 220px; max-height: 360px; overflow-y: auto; padding-right: 4px;">
                <div style="padding: 4px 0 8px; color: #999; font-size: 12px;">按勾选顺序排列显示</div>
                <template v-for="item in settingColumns">
                  <a-row :key="item.dataIndex" style="margin-bottom: 4px;">
                    <a-checkbox :value="item.dataIndex">{{ columnLabel(item) }}</a-checkbox>
                  </a-row>
                </template>
              </div>
            </a-checkbox-group>
            <a-row style="padding-top: 10px; border-top: 1px solid #f0f0f0;">
              <a-col>
                <a-checkbox :checked="isAllColumnsChecked" @change="handleSelectAllColumns">全选</a-checkbox>
                恢复默认：<a-button @click="handleRestDefault" type="link" size="small">恢复默认</a-button>
              </a-col>
            </a-row>
          </template>
          <a-button icon="setting">列设置</a-button>
        </a-popover>
      </div>
    </div>

    <!-- a-table -->
    <a-table
      ref="innerTable"
      :size="size"
      :bordered="bordered"
      :columns="renderColumns"
      :dataSource="displayDataSource"
      :loading="loading"
      :pagination="resolvedPagination"
      :rowSelection="rowSelection"
      :rowKey="rowKey"
      :customRow="customRow"
      :components="tableComponents"
      :expandedRowRender="expandedRowRenderFn"
      v-on="tableListeners"
      v-bind="tableAttrs"
    >
    </a-table>
    <!-- 常显横向滚动条：固定在表格下方，纵向滚动时不会消失 -->
    <div class="dt-hscroll" ref="hscroll" v-show="hscrollVisible">
      <div class="dt-hscroll-inner" :style="{ width: hscrollContentWidth + 'px' }"></div>
    </div>
    <!-- 合计行模式下：合计行追加在本页数据末尾，必须关闭内置分页，用外部翻页器避免合计行被分页截断 -->
    <div class="dt-summary-pager" v-if="summaryPager">
      <a-pagination
        size="small"
        show-size-changer
        :showQuickJumper="true"
        :current="pagination.current"
        :page-size="pagination.pageSize"
        :page-size-options="pagination.pageSizeOptions"
        :total="pagination.total"
        :show-total="pagination.showTotal"
        @change="onSummaryPageChange"
        @showSizeChange="onSummaryPageChange" />
    </div>
  </div>
</template>

<script>
  import Vue from 'vue'
  import { getAction, postAction } from '@/api/manage'
  import { VALIDATE_NO_PASSED } from '@/utils/JEditableTableUtil'

  export default {
    name: 'DataTable',
    props: {
      /** 数据库表名：自动从 information_schema 获取字段元数据 */
      tableName: { type: String, default: '' },
      /** 表格场景标识：从列配置中心读取默认显示列与标题覆盖 */
      tableKey: { type: String, default: '' },
      /** 额外列定义：补充关联查询的字段，格式 [{ dataIndex, title, scopedSlots? }] */
      extraColumns: { type: Array, default: () => [] },
      /** 列标题覆盖：{ dataIndex: newTitle }，自动发现后覆盖标题 */
      columnOverrides: { type: Object, default: () => ({}) },
      /** 默认显示的列 dataIndex 列表，未配置 localStorage 时生效 */
      defaultColumns: { type: Array, default: undefined },
      /** 受控列选择（dataIndex 数组）：父组件提供时优先于 localStorage/配置中心，双向同步 */
      selectedColumns: { type: Array, default: undefined },
      /** 数据源 */
      dataSource: { type: Array, default: () => [] },
      /** 加载状态 */
      loading: { type: Boolean, default: false },
      /** 分页配置 */
      pagination: { type: [Object, Boolean], default: undefined },
      /** 行选择配置 */
      rowSelection: { type: Object, default: undefined },
      /** 行 key */
      rowKey: { type: [String, Function], default: 'id' },
      /** 滚动配置 */
      scroll: { type: Object, default: undefined },
      /** 自定义行属性 */
      customRow: { type: Function, default: undefined },
      /** 操作列按钮定义 */
      actions: { type: Array, default: undefined },
      /** 是否显示边框 */
      bordered: { type: Boolean, default: true },
      /** 表格尺寸 */
      size: { type: String, default: 'middle' },
      /** 是否启用列宽拖拽（默认所有表格开启） */
      draggable: { type: Boolean, default: true },
      /** 是否显示汇总行 */
      summary: { type: Boolean, default: false },
      /** 需要合计的字段（逗号分隔） */
      summaryFields: { type: String, default: '' },
      /** 是否显示列设置按钮 */
      showColumnSetting: { type: Boolean, default: true },
      /** 自定义 localStorage 键（默认根据 tableName/tableKey 生成） */
      storageKey: { type: String, default: '' },
      /** 是否可编辑：开启后，带 editType 的列渲染为编辑控件 */
      editable: { type: Boolean, default: false },
      /** 展开行子表列定义（提供后自动渲染紧挨主表的子表布局） */
      detailColumns: { type: Array, default: () => [] },
      /** 展开行子数据所在字段 */
      detailDataField: { type: String, default: 'childrens' },
      /** 展开行子表行 key */
      detailRowKey: { type: String, default: 'id' },
      /** 展开行子表操作按钮定义 */
      detailActions: { type: Array, default: () => [] },
      /** 明细模式：提供 JEditableTable 兼容 API（statisticsColumns/getAll/setValues 等） */
      detail: { type: Boolean, default: false },
      /** 明细模式下需要合计到 statisticsColumns 的字段 */
      statisticsFields: { type: Array, default: () => [] },
      /** 重建列时是否保留 localStorage 列设置（明细列动态显隐时避免重置用户列设置） */
      preserveColumnSetting: { type: Boolean, default: false }
    },
    data() {
      return {
        /** 所有可选列（列设置弹窗展示的精简列，仅含默认选中列） */
        allColumns: [],
        /** 完整列（基础列 + 额外列），用于精简过滤与重建 */
        _fullColumns: [],
        /** 从 API 获取的基础列（不含 extraColumns），用于 initColumns 重建 */
        _baseColumns: [],
        /** 列配置中心返回的默认显示列 dataIndex 列表 */
        _configColumns: null,
        /** 列配置中心返回的标题覆盖 { dataIndex: title } */
        _configTitles: {},
        /** 列配置中心返回的公式列 [{ dataIndex, title, formula }] */
        _configCustomColumns: [],
        /** 列配置中心返回的原始配置对象（保存列宽时合并写回，避免丢失其他字段） */
        configRaw: null,
        /** 列配置中心对应的数据表名（保存时原样写回） */
        configTableNames: '',
        /** 列宽配置 { dataIndex: px } */
        columnWidths: {},
        /** 常显横向滚动条：是否显示 + 内容宽度 */
        hscrollVisible: false,
        hscrollContentWidth: 0,
        /** 选择列/展开列等非数据列占用宽度：表格总宽 = 数据列宽之和 + 该值 */
        extraHeaderWidth: 0,
        /** 当前选中的列 dataIndex 列表 */
        settingDataIndex: [],
        /** 用于 a-table 的列定义 */
        displayColumns: [],
        /** 表格可视区域宽度（用于计算末尾空白填充列宽度） */
        containerWidth: 0,
        /** 汇总后的数据源（summary 开启时，实时计算合计行） */
        summaryDataSource: [],
        /** 明细模式：统计列合计对象（兼容 JEditableTable.statisticsColumns） */
        statisticsColumns: {},
        /** 明细模式：行高（兼容 resetScrollTop 滚动定位） */
        rowHeight: 40
      }
    },
    computed: {
      resolvedStorageKey() {
        return this.storageKey || ('columns_' + (this.tableKey || this.tableName || 'default'))
      },
      /** 列宽在 localStorage 中的键（无 tableKey 时兜底保存） */
      resolvedWidthStorageKey() {
        return 'widths_' + this.resolvedStorageKey
      },
      /** 列设置弹窗中展示的列：受控模式只显示已勾选列；其余情况展示全部可选列（配置中心只决定默认显示列） */
      settingColumns() {
        const map = {}
        this.allColumns.forEach(c => { map[c.dataIndex] = c })
        if (this.selectedColumns !== undefined) {
          return this.selectedColumns.map(k => map[k]).filter(Boolean)
        }
        return this.allColumns
      },
      /** 列设置弹窗中是否已全部勾选 */
      isAllColumnsChecked() {
        const keys = this.settingColumns.map(c => c.dataIndex)
        return keys.length > 0 && keys.every(k => this.settingDataIndex.indexOf(k) >= 0)
      },
      /** 是否渲染工具栏：有 toolbar 插槽或需显示列设置按钮时为 true */
      hasToolbar() {
        return !!(this.$slots.toolbar || this.$scopedSlots.toolbar) ||
          (this.showColumnSetting && this.settingColumns.length > 0)
      },
      /** 是否渲染展开行：有 expandedRowRender 插槽或配置了内置子表列 */
      hasExpandedRow() {
        return !!this.$scopedSlots.expandedRowRender || this.detailColumns.length > 0
      },
      /** 分页配置：默认开启每页条数（行数）选择，父组件传入的 pagination 可覆盖 */
      resolvedPagination() {
        if (this.pagination === false) return false
        // 合计行模式改为外部翻页器，内置分页会把追加的合计行当成第 N+1 行截掉
        if (this.summaryPager) return false
        const base = { showSizeChanger: true, pageSizeOptions: ['10', '20', '30', '50', '100'] }
        if (this.pagination && typeof this.pagination === 'object') {
          return Object.assign({}, base, this.pagination)
        }
        return Object.assign({}, base)
      },
      /** 是否使用外部翻页器（合计行 + 服务端分页） */
      summaryPager() {
        return !!this.summary && this.pagination && typeof this.pagination === 'object'
      },
      /** 展开行渲染函数：无展开行时返回 undefined，避免出现展开按钮列 */
      expandedRowRenderFn() {
        if (!this.hasExpandedRow) {
          return undefined
        }
        const self = this
        return function(record) {
          if (self.$scopedSlots.expandedRowRender) {
            return self.$scopedSlots.expandedRowRender(record)
          }
          return self.renderDetail(record)
        }
      },
      /**
       * 表格固定宽度：数据列宽之和 + 选择/展开列宽。
       * 任一列缺少数值列宽时返回 0（此时先按浏览器自适应渲染，随后由 _captureColumnWidths 补齐）。
       */
      fixedTableWidth() {
        const cols = this.displayColumns || []
        if (cols.length === 0) return 0
        let total = 0
        for (let i = 0; i < cols.length; i++) {
          const col = cols[i]
          const key = col.dataIndex || col.key
          const saved = col.__lockWidth ? 0 : this.parseColumnWidth(this.columnWidths[key])
          const width = saved || this.parseColumnWidth(col.width)
          if (!width) return 0
          total += width
        }
        return total + (this.extraHeaderWidth || 0)
      },
      /**
       * 末尾空白填充列宽度：容器比列宽总和宽时，用一列空白列补齐，
       * 避免浏览器把多出来的空间摊到最后一列（或固定列）上导致错位。
       */
      fillerWidth() {
        const total = this.fixedTableWidth
        if (!total || !this.containerWidth) return 0
        const diff = this.containerWidth - total
        return diff > 2 ? diff : 0
      },
      /**
       * 实际传给 a-table 的列：数据列 + 末尾空白填充列。
       * 填充列插在固定右侧列（如操作列）之前，保证固定列仍在最右。
       */
      renderColumns() {
        const cols = this.displayColumns || []
        const width = this.fillerWidth
        if (!width) return cols
        const filler = {
          dataIndex: '__filler',
          key: '__filler',
          title: '',
          width: width,
          className: 'dt-filler-col',
          __filler: true
        }
        const fixedRightIndex = cols.findIndex(col => col.fixed === 'right')
        if (fixedRightIndex === -1) return cols.concat([filler])
        const result = cols.slice()
        result.splice(fixedRightIndex, 0, filler)
        return result
      },
      /**
       * 表格滚动配置：列宽已知时把表格宽度固定为列宽之和（含空白填充列），
       * 拖拽某一列后其它列不会被自动拉伸（类 Excel 行为）。
       */
      resolvedScroll() {
        const base = this.scroll
        const total = this.fixedTableWidth
        if (!total) return base
        const baseObj = (base && typeof base === 'object') ? base : {}
        return Object.assign({}, baseObj, { x: total + this.fillerWidth })
      },
      tableAttrs() {
        const exclude = ['tableName', 'extraColumns', 'columnOverrides', 'defaultColumns',
          'dataSource', 'loading', 'pagination', 'rowSelection', 'rowKey', 'scroll', 'customRow', 'actions',
          'components']
        const attrs = {}
        for (const key in this.$attrs) {
          if (exclude.indexOf(key) === -1) {
            attrs[key] = this.$attrs[key]
          }
        }
        if (!this.hasExpandedRow) {
          if (this.resolvedScroll) {
            attrs.scroll = this.resolvedScroll
          }
        } else if (this.fixedTableWidth) {
          // 展开行：固定纵向滚动与展开行渲染不兼容，只固定横向宽度
          attrs.scroll = { x: this.fixedTableWidth }
        }
        return attrs
      },
      tableListeners() {
        return Object.assign({}, this.$listeners)
      },
      /** 汇总行数据源：开启汇总时返回实时计算的合计行数据源 */
      displayDataSource() {
        if (this.summary) {
          return this.summaryDataSource
        }
        return this.dataSource
      },
      /**
       * 列宽拖拽：每个表头右侧渲染手柄，拖动实时调整列宽并在松手后持久化
       * 注意：vc-table 只在初始化时读取一次 components，因此这里不能依赖 displayColumns 的实时值，
       * 列对象在实际按下手柄时再解析（见 _onResizeStart）
       */
      tableComponents() {
        if (!this.draggable) return undefined
        const self = this
        return {
          header: {
            cell: (h, props, children) => {
              const { key, ...restProps } = props
              const thClass = restProps.class ? restProps.class + ' resize-table-th' : 'resize-table-th'
              // 锁定宽度的列（如操作列）不提供拖拽手柄
              if (self._isLockedColumn(key)) {
                return h('th', { ...restProps, class: thClass }, children)
              }
              const drag = h('span', {
                key: 'resize-handle',
                class: 'table-draggable-handle',
                style: { position: 'absolute', top: 0, bottom: 0, right: '-5px', width: '10px', zIndex: 3, cursor: 'col-resize' },
                attrs: { title: '拖动调整列宽' },
                on: {
                  mousedown: (e) => self._onResizeStart(e, key),
                  click: (e) => e.stopPropagation()
                }
              })
              return h('th', { ...restProps, class: thClass }, [children, drag])
            }
          }
        }
      }
    },
    watch: {
      /** extraColumns 变化时（无 tableName 场景，如动态明细列）自动重建列 */
      extraColumns() {
        if (!this.tableName) {
          this.initColumns()
        }
      },
      /** 数据源变化时实时重算汇总行（深度监听，支持行内编辑后统计联动） */
      dataSource: {
        deep: true,
        immediate: true,
        handler() {
          this._recalcSummary()
          if (this.detail) {
            this.recalcAllStatisticsColumns()
          }
        }
      },
      displayColumns() {
        this._recalcSummary()
        // 列变化后（列设置调整、列定义就绪）补齐缺失列宽，使表格宽度固定；同时重新测量容器宽度
        this.$nextTick(() => {
          this._captureColumnWidths()
          this._measureContainerWidth()
          this.syncHScroll()
        })
      },
      summary() {
        this._recalcSummary()
      },
      summaryFields() {
        this._recalcSummary()
      },
      statisticsFields() {
        if (this.detail) {
          this.recalcAllStatisticsColumns()
        }
      },
      selectedColumns: {
        immediate: true,
        handler(v) {
          if (v === undefined) return
          this.settingDataIndex = (v || []).slice()
          this.displayColumns = this.buildColumns(this.settingDataIndex)
        }
      },
      /** 列池（allColumns）就绪后，受控模式下用 selectedColumns 重建列，解决列头不更新 */
      allColumns() {
        if (this.selectedColumns !== undefined) {
          this.settingDataIndex = (this.selectedColumns || []).slice()
          this.displayColumns = this.buildColumns(this.settingDataIndex)
        }
      }
    },
    created() {
      // 列来源：tableName 自动发现数据库字段，否则用 extraColumns 手工列
      if (this.tableName) {
        this._fetchTableMeta(this.tableName)
      } else {
        this._baseColumns = []
        this._fullColumns = this.extraColumns.slice()
        this.allColumns = this._fullColumns.slice()
        if (this.tableKey) {
          // tableKey 只关联配置中心（默认列/标题），不改变列来源
          this._fetchConfig()
        } else {
          this._applyColumnFilter()
          this._initFromStorage()
        }
      }
    },
    mounted() {
      // 首次渲染完成后固化列宽，使表格宽度固定（拖拽只影响当前列），并测量容器宽度以生成空白填充列
      this.$nextTick(() => {
        this._captureColumnWidths()
        this._measureContainerWidth()
        this.syncHScroll()
      })
      this._resizeHandler = () => {
        this._measureContainerWidth()
        this.syncHScroll()
      }
      window.addEventListener('resize', this._resizeHandler)
      if (typeof ResizeObserver !== 'undefined') {
        this._resizeObserver = new ResizeObserver(() => {
          this._measureContainerWidth()
          this.syncHScroll()
        })
        this._resizeObserver.observe(this.$el)
      }
    },
    beforeDestroy() {
      this.unbindHScroll()
      if (this._resizeHandler) {
        window.removeEventListener('resize', this._resizeHandler)
        this._resizeHandler = null
      }
      if (this._resizeObserver) {
        this._resizeObserver.disconnect()
        this._resizeObserver = null
      }
    },
    methods: {
      /** 测量表格可视区域宽度（不含纵向滚动条），用于计算末尾空白填充列 */
      _measureContainerWidth() {
        const root = this.$el
        if (!root || !root.querySelector) return
        const el = root.querySelector('.ant-table-body') || root.querySelector('.ant-table-content') || root
        const width = Math.floor(el.clientWidth || 0)
        if (width > 0 && Math.abs(width - this.containerWidth) > 2) {
          this.containerWidth = width
        }
      },
      /** 解析列宽：仅接受数字或像素字符串，其余返回 0 */
      parseColumnWidth(width) {
        if (typeof width === 'number') {
          return width > 0 ? Math.round(width) : 0
        }
        if (typeof width === 'string') {
          const str = width.trim()
          if (/^\d+(\.\d+)?(px)?$/.test(str)) {
            return Math.round(parseFloat(str))
          }
        }
        return 0
      },
      /** 是否为锁定宽度的列（宽度由列配置计算，不允许拖拽或保存值覆盖） */
      _isLockedColumn(key) {
        if (key == null) return false
        return (this.displayColumns || []).some(col => (col.dataIndex || col.key) === key && col.__lockWidth)
      },
      /**
       * 首次渲染后把当前实际列宽固化下来（不写入配置，只作为运行期固定宽度）。
       * 这样表格宽度一开始就等于内容宽度，拖拽时其它列不会被浏览器重新分摊。
       * 说明：固定列在主表头中也有同宽占位单元格，因此可按列顺序一一对应测量。
       */
      _captureColumnWidths() {
        const table = this.$refs.innerTable
        const root = table && table.$el
        const cols = this.renderColumns || []
        if (!root || cols.length === 0) return
        const head = root.querySelector('.ant-table-thead')
        const ths = head ? Array.prototype.slice.call(head.querySelectorAll('tr:first-child > th')) : []
        if (ths.length < cols.length) return
        // 表头中展开列/选择列等自动插入列位于最前面，其宽度也要计入表格总宽
        const offset = ths.length - cols.length
        let extra = 0
        for (let i = 0; i < offset; i++) {
          extra += Math.round(ths[i].getBoundingClientRect().width)
        }
        if (extra > 0 && extra !== this.extraHeaderWidth) {
          this.extraHeaderWidth = extra
        }
        let changed = false
        cols.forEach((col, i) => {
          const key = col.dataIndex || col.key
          if (key == null || col.__lockWidth || col.__filler) return
          // 列定义已指定像素宽度的列不再测量：浏览器可能已把剩余空间摊给它，测出来的是被拉伸后的宽度
          if (this.parseColumnWidth(col.width)) return
          if (this.parseColumnWidth(this.columnWidths[key])) return
          const width = Math.round(ths[i + offset].getBoundingClientRect().width)
          if (width > 0) {
            this.$set(this.columnWidths, key, width)
            changed = true
          }
        })
        if (changed) {
          // 把固化后的宽度写回列定义，配合固定宽度让每列宽严格等于设定值
          this.displayColumns = this.buildColumns(this.settingDataIndex)
        }
      },
      /** 列设置弹窗中的展示文本：仅显示字段业务含义，不附带字段名 */
      columnLabel(item) {
        return item.title || item.dataIndex
      },
      /** 从后端获取表字段元数据（支持逗号分隔多表名，主表优先去重） */
      _fetchTableMeta(tables) {
        const tableNames = tables || this.tableName
        if (!tableNames) {
          this._baseColumns = []
          this._fullColumns = this.extraColumns.slice()
          this.allColumns = this._fullColumns.slice()
          this.$nextTick(() => {
            this._applyColumnFilter()
            this._initFromStorage()
          })
          return
        }
        getAction('/tableMeta/' + tableNames, {}).then((res) => {
          if (res && res.code === 200 && res.data) {
            const columns = res.data.map(field => ({
              dataIndex: field.columnName,
              title: field.columnComment || field.columnName,
              key: field.columnName
            }))
            // 保存基础列（不含额外列），用于 initColumns 重建
            this._baseColumns = columns
            this._fullColumns = columns.concat(this.extraColumns)
            this.allColumns = this._fullColumns.slice()
          } else {
            this._baseColumns = []
            this._fullColumns = this.extraColumns.slice()
            this.allColumns = this._fullColumns.slice()
          }
          // nextTick 确保 $scopedSlots 已从父组件注入
          this.$nextTick(() => {
            if (this.tableKey) {
              this._fetchConfig()
            } else {
              this._applyColumnFilter()
              this._initFromStorage()
            }
          })
        }).catch(() => {
          // 失败时使用额外列
          this._baseColumns = []
          this._fullColumns = this.extraColumns.slice()
          this.allColumns = this._fullColumns.slice()
          this.$nextTick(() => {
            if (this.tableKey) {
              this._fetchConfig()
            } else {
              this._applyColumnFilter()
              this._initFromStorage()
            }
          })
        })
      },

      /** 从列配置中心获取默认显示列、标题覆盖与列宽（不改变列来源） */
      _fetchConfig() {
        getAction('/tableColumnConfig/' + this.tableKey, {}).then((res) => {
          if (res && res.code === 200 && res.data && res.data.config) {
            const cfg = res.data.config
            this.configRaw = cfg
            this.configTableNames = res.data.tableNames || ''
            this._configColumns = cfg.columns || []
            this._configTitles = cfg.titles || {}
            this._configCustomColumns = cfg.customColumns || []
            this.columnWidths = this._normalizeWidths(cfg.widths)
          }
          this._applyCustomColumns()
          this._applyColumnFilter()
          this._initFromStorage()
        }).catch(() => {
          this._applyCustomColumns()
          this._applyColumnFilter()
          this._initFromStorage()
        })
      },

      /** 从 localStorage 恢复，否则依次用配置中心、defaultColumns，兜底全部列 */
      _initFromStorage() {
        if (!this.tableKey) {
          // 无配置中心的表格，列宽保存在 localStorage
          const savedWidths = Vue.ls.get(this.resolvedWidthStorageKey)
          if (savedWidths && typeof savedWidths === 'object') {
            this.columnWidths = Object.assign({}, this.columnWidths, this._normalizeWidths(savedWidths))
          }
        }
        if (this.selectedColumns !== undefined) {
          this.settingDataIndex = this.selectedColumns.slice()
          this.displayColumns = this.buildColumns(this.settingDataIndex)
          return
        }
        let saved = this.showColumnSetting ? Vue.ls.get(this.resolvedStorageKey) : null
        if (typeof saved === 'string') {
          saved = saved.split(',').filter(k => !!k)
        }
        const allKeys = this.allColumns.map(c => c.dataIndex)

        if (saved && saved.length > 0) {
          this.settingDataIndex = saved.filter(k => allKeys.indexOf(k) >= 0)
        }
        if (!this.settingDataIndex || this.settingDataIndex.length === 0) {
          this.settingDataIndex = this._getDefaultColumns()
        }
        this.displayColumns = this.buildColumns(this.settingDataIndex)
      },

      /** 归一化列宽配置：仅保留正数并转为数值 */
      _normalizeWidths(widths) {
        const result = {}
        if (!widths || typeof widths !== 'object') return result
        Object.keys(widths).forEach(k => {
          const v = parseInt(widths[k], 10)
          if (!isNaN(v) && v > 0) result[k] = v
        })
        return result
      },

      /** 计算默认显示列：配置中心 > defaultColumns > 全部列（配置中心为列设置的初始属性） */
      _getDefaultColumns() {
        const allKeys = (this._fullColumns.length ? this._fullColumns : this.allColumns).map(c => c.dataIndex)
        if (this._configColumns && this._configColumns.length > 0) {
          return this._configColumns.filter(k => allKeys.indexOf(k) >= 0)
        }
        if (this.defaultColumns && this.defaultColumns.length > 0) {
          return this.defaultColumns.filter(k => allKeys.indexOf(k) >= 0)
        }
        return allKeys.slice()
      },

      /** 构建 a-table 的 columns */
      buildColumns(checkedValues) {
        const self = this
        const cols = []

        for (let i = 0; i < checkedValues.length; i++) {
          const found = this.allColumns.find(c => c.dataIndex === checkedValues[i])
          if (!found) continue

          const col = Object.assign({}, found)

          // 应用已保存的列宽（配置中心 / localStorage）；锁定宽度的列（如操作列）忽略保存值
          const savedWidth = col.__lockWidth ? 0 : this.columnWidths[col.dataIndex]
          if (savedWidth) {
            col.width = savedWidth
          }

          // 确定 slot 名称：优先 extraColumns 中显式的 scopedSlots，
          // 其次自动匹配父组件的 scoped slot（字段名 == slot 名）
          let slotName = null
          if (col.scopedSlots && col.scopedSlots.customRender) {
            slotName = col.scopedSlots.customRender
            delete col.scopedSlots
          } else if (self.$scopedSlots[col.dataIndex]) {
            slotName = col.dataIndex
          }

          if (slotName) {
            col.customRender = function(text, record, index, column) {
              // 汇总行默认不渲染自定义插槽；列上声明 summaryRender 的列（如需显示合计值）照常渲染
              if (record && record._dataTableSummary && !col.summaryRender) {
                return ''
              }
              const slotFn = self.$scopedSlots[slotName]
              if (slotFn) {
                // 与 a-table 的 scopedSlots 保持一致：传递多个独立参数
                return slotFn(text, record, index, column)
              }
              return text
            }
          } else if (col.__customColumn && col.formula) {
            // 公式列：按行计算
            col.customRender = function(text, record) {
              if (record && record._dataTableSummary) {
                return '-'
              }
              return self._computeFormula(col.formula, record)
            }
          } else if (self.editable && col.editType) {
            // 可编辑模式：无自定义插槽的列按 editType 渲染编辑控件
            col.customRender = function(text, record) {
              if (record && record._dataTableSummary) {
                return text
              }
              return self._renderEditableCell(col, record)
            }
          }

          cols.push(col)
        }

        // 追加操作列
        if (this.actions && this.actions.length > 0) {
          cols.push({
            title: '操作',
            key: 'action',
            fixed: 'right',
            __lockWidth: true,
            width: self.actions.length * 58 + 24,
            customRender(text, record, index) {
              // 汇总行不渲染操作按钮
              if (record && record._dataTableSummary) {
                return ''
              }
              const h = self.$createElement
              const btns = []
              self.actions.forEach(act => {
                const btnNode = h('a-button', {
                  props: { type: act.type || 'link', size: 'small', icon: act.icon },
                  on: { click: () => act.click && act.click(record, index) }
                }, [act.title])
                if (act.confirm) {
                  btns.push(h('a-popconfirm', {
                    props: { title: act.confirm },
                    on: { confirm: () => act.click && act.click(record, index) }
                  }, [btnNode]))
                } else {
                  btns.push(btnNode)
                }
              })
              return h('span', { style: { display: 'inline-flex', gap: '4px' } }, btns)
            }
          })
        }

        // 固定右侧的列强制排到最后：列设置里把其它列拖到操作列之后时，避免数据列被固定列浮层遮挡
        const fixedRightCols = cols.filter(col => col.fixed === 'right')
        if (fixedRightCols.length > 0) {
          return cols.filter(col => col.fixed !== 'right').concat(fixedRightCols)
        }

        return cols
      },
      /** 可编辑单元格渲染：根据列 editType 返回对应编辑控件 */
      _renderEditableCell(col, record) {
        const h = this.$createElement
        const key = col.dataIndex
        const value = record[key]
        const props = Object.assign({}, col.editProps)

        if (col.editType === 'select') {
          const options = (col.editOptions || []).map(opt => {
            if (typeof opt === 'object') {
              return h('a-select-option', {
                key: opt.value,
                props: { value: opt.value }
              }, opt.text || opt.label || String(opt.value))
            }
            return h('a-select-option', { key: opt, props: { value: opt } }, String(opt))
          })
          return h('a-select', {
            props: Object.assign({
              value: value,
              placeholder: props.placeholder || '请选择',
              allowClear: !!props.allowClear,
              showSearch: !!props.showSearch,
              optionFilterProp: props.optionFilterProp || 'children'
            }, props),
            style: { width: '100%' },
            on: {
              change: (v) => this._updateCell(record, key, v)
            }
          }, options)
        }

        if (col.editType === 'inputNumber') {
          return h('a-input-number', {
            props: Object.assign({ value: value }, props),
            style: { width: '100%' },
            on: {
              change: (v) => this._updateCell(record, key, v)
            }
          })
        }

        // 默认按文本输入框处理
        return h('a-input', {
          props: Object.assign({ value: value }, props),
          on: {
            input: (e) => this._updateCell(record, key, e.target.value)
          }
        })
      },

      /** 更新单元格值并通知父组件 */
      _updateCell(record, key, value) {
        this.$set(record, key, value)
        this.$emit('edit-change', { record, key, dataIndex: key, value })
      },

      /** 计算公式列：引用当前行字段，支持 + - * / 与括号，非数字按 0 */
      _computeFormula(formula, record) {
        if (!formula) {
          return ''
        }
        let expr = String(formula).replace(/[A-Za-z_][A-Za-z0-9_]*/g, (m) => {
          const v = record == null ? NaN : parseFloat(record[m])
          return isNaN(v) ? '0' : String(v)
        })
        // 仅允许数字、运算符、括号、空白，防止注入
        if (!/^[0-9+\-*/().\s]+$/.test(expr)) {
          return ''
        }
        try {
          // eslint-disable-next-line no-new-func
          const fn = new Function('return (' + expr + ')')
          const r = fn()
          return (typeof r === 'number' && isFinite(r)) ? r : ''
        } catch (e) {
          return ''
        }
      },

      /** 表格纵向滚动容器（a-table 的 body） */
      _tableBodyEl() {
        return this.$el ? this.$el.querySelector('.ant-table-body') : null
      },
      /** 绑定常显横向滚动条与表格 body 的滚动同步 */
      bindHScroll() {
        const body = this._tableBodyEl()
        if (body && !body.__dtHScrollBound) {
          body.addEventListener('scroll', this.onTableBodyScroll, { passive: true })
          body.__dtHScrollBound = true
        }
        const bar = this.$refs.hscroll
        if (bar && !bar.__dtHScrollBound) {
          bar.addEventListener('scroll', this.onHScrollBarScroll, { passive: true })
          bar.__dtHScrollBound = true
        }
      },
      unbindHScroll() {
        const body = this._tableBodyEl()
        if (body && body.__dtHScrollBound) {
          body.removeEventListener('scroll', this.onTableBodyScroll)
          body.__dtHScrollBound = false
        }
        const bar = this.$refs.hscroll
        if (bar && bar.__dtHScrollBound) {
          bar.removeEventListener('scroll', this.onHScrollBarScroll)
          bar.__dtHScrollBound = false
        }
      },
      onTableBodyScroll() {
        const body = this._tableBodyEl()
        const bar = this.$refs.hscroll
        if (body && bar && Math.abs(bar.scrollLeft - body.scrollLeft) > 1) {
          bar.scrollLeft = body.scrollLeft
        }
      },
      onHScrollBarScroll() {
        const body = this._tableBodyEl()
        const bar = this.$refs.hscroll
        if (body && bar && Math.abs(body.scrollLeft - bar.scrollLeft) > 1) {
          body.scrollLeft = bar.scrollLeft
        }
      },
      /** 计算内容宽度、决定常显横向滚动条是否出现，并锁死固定右列宽度 */
      syncHScroll() {
        const body = this._tableBodyEl()
        if (!body) {
          this.hscrollVisible = false
          return
        }
        const contentWidth = body.scrollWidth
        const visible = contentWidth - body.clientWidth > 2
        this.hscrollContentWidth = contentWidth
        this.hscrollVisible = visible
        this.bindHScroll()
        this.$nextTick(() => {
          const bar = this.$refs.hscroll
          if (bar && this.hscrollVisible) bar.scrollLeft = body.scrollLeft
        })
        this._lockFixedRightWidth()
      },
      /** 固定右列（操作列）宽度：拖动其它列时不被浏览器分摊拉伸 */
      _lockFixedRightWidth() {
        const fixedCols = (this.displayColumns || []).filter(c => c.fixed === 'right')
        if (!fixedCols.length || !this.$el) return
        let total = 0
        const widths = fixedCols.map(c => {
          const w = this.parseColumnWidth(c.width) || 0
          total += w
          return w
        })
        if (!total) return
        Array.prototype.forEach.call(this.$el.querySelectorAll('.ant-table-fixed-right table'), table => {
          table.style.tableLayout = 'fixed'
          table.style.width = total + 'px'
          const colEls = table.querySelectorAll('colgroup > col')
          Array.prototype.forEach.call(colEls, (el, i) => {
            const w = widths[i]
            if (w) {
              el.style.width = w + 'px'
              el.style.minWidth = w + 'px'
            }
          })
        })
      },

      /** 外部翻页器：翻页/改每页条数时通知父组件重新取数 */
      onSummaryPageChange(page, pageSize) {
        const size = pageSize || this.pagination.pageSize
        // 页码与条数都没变时不重复请求
        if (page === this.pagination.current && size === this.pagination.pageSize) return
        const pagination = Object.assign({}, this.pagination, {
          current: page,
          pageSize: size
        })
        this.$emit('change', pagination, {}, {})
      },

      /** 实时计算汇总行数据源 */
      _recalcSummary() {
        if (!this.summary || !this.dataSource || this.dataSource.length === 0) {
          this.summaryDataSource = this.dataSource || []
          return
        }
        const fields = (this.summaryFields || '').split(',').filter(Boolean)
        const totalRow = { _dataTableSummary: true }
        // 合计标签优先放在码列，其次放在第一个非操作、非固定、非插槽列（插槽列汇总行可能不渲染）
        const isLabelCandidate = c => {
          const key = c.dataIndex || c.key
          return key && key !== 'action' && !c.fixed
        }
        const labelCol = this.displayColumns.find(c => isLabelCandidate(c) && (c.dataIndex || c.key) === 'materialCode')
          || this.displayColumns.find(c => isLabelCandidate(c) && (!c.customRender || c.summaryRender))
        const labelKey = labelCol ? (labelCol.dataIndex || labelCol.key) : null
        if (labelKey) {
          totalRow[labelKey] = '合计'
        }
        this.displayColumns.forEach(col => {
          const key = col.dataIndex || col.key
          if (key === labelKey || key === 'action') return
          if (fields.indexOf(key) > -1) {
            let total = 0
            this.dataSource.forEach(d => {
              const v = parseFloat(d[key])
              if (!isNaN(v)) total += v
            })
            totalRow[key] = total.toFixed(2)
          } else {
            totalRow[key] = '-'
          }
        })
        this.summaryDataSource = this.dataSource.concat([totalRow])
      },

      /** 明细模式：重算统计列合计（兼容 JEditableTable.recalcAllStatisticsColumns） */
      recalcAllStatisticsColumns() {
        if (!this.detail) {
          return
        }
        const total = {}
        const fields = this.statisticsFields || []
        fields.forEach(f => {
          let sum = 0
          this.dataSource.forEach(d => {
            const v = parseFloat(d[f])
            if (!isNaN(v)) sum += v
          })
          total[f] = sum
        })
        this.statisticsColumns = total
      },

      /** 明细模式：获取所有行值（兼容 JEditableTable.getAll，返回 Promise） */
      getAll(needCheck, deleteTempId) {
        return new Promise((resolve, reject) => {
          if (needCheck && this.detail) {
            const invalid = this._validateDetail()
            if (invalid) {
              reject(VALIDATE_NO_PASSED)
              return
            }
          }
          resolve({
            values: this.dataSource.slice(),
            statisticsColumns: Object.assign({}, this.statisticsColumns)
          })
        })
      },

      /** 明细模式：校验必填列（简化版 JEditableTable.validateValue，仅 required 规则） */
      _validateDetail() {
        const columns = this._fullColumns || []
        const rows = this.dataSource || []
        for (let i = 0; i < rows.length; i++) {
          const row = rows[i]
          for (let j = 0; j < columns.length; j++) {
            const col = columns[j]
            const rules = col.validateRules
            if (!(rules instanceof Array)) {
              continue
            }
            for (let k = 0; k < rules.length; k++) {
              const rule = rules[k]
              if (rule.required === true) {
                const value = row[col.dataIndex]
                if (value == null || value === '') {
                  return { row, col, rule }
                }
              }
            }
          }
        }
        return null
      },

      /** 明细模式：回调式获取所有行值（兼容 JEditableTable.getValues） */
      getValues(callback) {
        if (typeof callback === 'function') {
          callback(null, this.dataSource.slice())
        }
      },

      /** 明细模式：按 rowKey 批量更新行值（兼容 JEditableTable.setValues） */
      setValues(rows) {
        if (!(rows instanceof Array)) {
          return
        }
        rows.forEach(item => {
          const { rowKey, values } = item || {}
          if (!values) {
            return
          }
          const row = this.dataSource.find(r => r.id === rowKey || r.rowKey === rowKey)
          if (row) {
            Object.keys(values).forEach(k => {
              this.$set(row, k, values[k])
            })
          }
        })
        this.recalcAllStatisticsColumns()
        this.$forceUpdate()
      },

      /** 明细模式：自动聚焦特殊键（兼容空实现，聚焦行为由父组件处理） */
      autoSelectBySpecialKey() {},

      /** 明细模式：滚动到指定位置（兼容空实现，滚动行为由浏览器默认处理） */
      resetScrollTop() {},

      /** 明细模式：新增空行（兼容 JEditableTable.add，明细页通常改为弹窗选商品） */
      add(rowNum) {
        const n = rowNum || 1
        for (let i = 0; i < n; i++) {
          this.dataSource.push({ id: this._genTempId() })
        }
        this.recalcAllStatisticsColumns()
      },

      /** 明细模式：重置表格（兼容 JEditableTable.initialize） */
      initialize() {
        if (this.dataSource) {
          this.dataSource.splice(0, this.dataSource.length)
        }
        this.recalcAllStatisticsColumns()
      },

      /** 生成临时行 id */
      _genTempId() {
        return 'tmp_' + Date.now() + '_' + Math.floor(Math.random() * 100000)
      },

      /** 内置子表单元格宽度样式 */
      detailCellStyle(col) {
        if (col.width) {
          return { width: col.width + 'px', flexShrink: 0 }
        }
        return { flex: 1, minWidth: 60 }
      },

      /** 渲染内置展开行子表（div flex 布局，紧挨主表） */
      renderDetail(record) {
        const h = this.$createElement
        const children = []
        const detailData = record[this.detailDataField] || []
        // 表头
        const headerCells = this.detailColumns.map(col => h('div', {
          class: ['detail-cell', { 'detail-cell-right': col.align === 'right' }],
          style: this.detailCellStyle(col)
        }, col.title))
        if (this.detailActions.length > 0) {
          headerCells.push(h('div', { class: 'detail-cell detail-cell-action' }))
        }
        children.push(h('div', { class: 'detail-row detail-row-header' }, headerCells))
        // 数据行
        detailData.forEach((row, idx) => {
          const cells = this.detailColumns.map(col => h('div', {
            class: ['detail-cell', { 'detail-cell-right': col.align === 'right' }],
            style: this.detailCellStyle(col)
          }, row[col.dataIndex]))
          if (this.detailActions.length > 0) {
            const links = this.detailActions.map(act => h('a', {
              class: 'detail-action-link',
              on: { click: () => act.click && act.click(record, row) }
            }, act.title))
            cells.push(h('div', { class: 'detail-cell detail-cell-action' }, links))
          }
          children.push(h('div', {
            class: 'detail-row detail-row-data',
            key: row[this.detailRowKey] || idx
          }, cells))
        })
        // 加载 / 空态
        if (record.loading) {
          children.push(h('div', { class: 'detail-empty' }, '加载中...'))
        } else if (detailData.length === 0) {
          children.push(h('div', { class: 'detail-empty' }, '暂无明细数据'))
        }
        return h('div', { class: 'data-table-detail' }, children)
      },

      /**
       * 开始拖拽列宽：Windows 表格（Excel）方式
       * 拖动时只改变当前列宽度，其余列保持原像素宽度不变，表格总宽随之增减；
       * 拖动过程只操作 DOM（不触发 Vue 重渲染），松手后再提交一次配置。
       */
      _onResizeStart(e, fallbackKey) {
        if (e.button !== undefined && e.button !== 0) return
        if (this._isLockedColumn(fallbackKey)) return
        e.preventDefault()
        e.stopPropagation()
        const handle = e.currentTarget || e.target
        const th = handle && handle.closest ? handle.closest('th') : null
        if (!th || !th.parentNode) return
        // 固定列（左侧/右侧）由独立表格渲染，不参与列宽拖拽，避免列索引错位
        if (th.closest('.ant-table-fixed-right') || th.closest('.ant-table-fixed-left')) return
        const root = this.$refs.innerTable && this.$refs.innerTable.$el
        if (!root) return

        const headerThs = Array.prototype.slice.call(th.parentNode.children)
        const index = headerThs.indexOf(th)
        if (index < 0) return

        // 列对象在拖拽时才解析：components 在表格初始化时就被缓存，不能依赖渲染时捕获的列数组
        let colIndex = -1
        if (fallbackKey !== undefined && fallbackKey !== null) {
          colIndex = this.displayColumns.findIndex(c => (c.dataIndex || c.key) === fallbackKey)
        }
        // 选择列/展开列等非数据列不参与列宽调整
        if (colIndex < 0) return
        // antd 会在最前面自动插入选择列/展开列，用于表头索引与列对象的偏移换算
        const offset = index - colIndex

        // 冻结所有列的当前实际宽度（以表头渲染宽度为准），避免浏览器把剩余空间分摊到其它列
        const startWidths = headerThs.map(el => Math.round(el.getBoundingClientRect().width))
        const startTotal = startWidths.reduce((sum, w) => sum + w, 0)
        // 同步自动插入列（展开列/选择列）的实际宽度，保证表格总宽计算准确
        if (offset > 0) {
          let extra = 0
          for (let i = 0; i < offset; i++) {
            extra += startWidths[i] || 0
          }
          this.extraHeaderWidth = extra
        }

        // 表头与表体各有一张 table，列数一致时同步冻结；固定列等子表列数不同则不参与
        const tableStates = Array.prototype.slice.call(root.querySelectorAll('table'))
          .map(table => ({
            table,
            cols: Array.prototype.slice.call(table.querySelectorAll('colgroup > col'))
          }))
          .filter(state => state.cols.length === headerThs.length)
        tableStates.forEach(({ table, cols }) => {
          table.style.tableLayout = 'fixed'
          table.style.width = startTotal + 'px'
          cols.forEach((colEl, i) => {
            if (startWidths[i] == null) return
            colEl.style.width = startWidths[i] + 'px'
            colEl.style.minWidth = startWidths[i] + 'px'
          })
        })
        headerThs.forEach((el, i) => {
          el.style.width = startWidths[i] + 'px'
        })

        const startX = e.clientX
        const startWidth = startWidths[index] || 0
        let lastWidth = startWidth
        let frame = null

        // 拖动中只改当前列的 col/th 宽度和表格总宽，其它列保持不变
        const apply = () => {
          frame = null
          const total = startTotal + (lastWidth - startWidth)
          tableStates.forEach(({ table, cols }) => {
            table.style.width = total + 'px'
            const colEl = cols[index]
            if (colEl) {
              colEl.style.width = lastWidth + 'px'
              colEl.style.minWidth = lastWidth + 'px'
            }
          })
          th.style.width = lastWidth + 'px'
        }
        const onMove = (ev) => {
          lastWidth = Math.max(40, Math.round(startWidth + ev.clientX - startX))
          if (!frame) frame = window.requestAnimationFrame(apply)
        }
        const onUp = () => {
          document.removeEventListener('mousemove', onMove)
          document.removeEventListener('mouseup', onUp)
          document.body.style.cursor = ''
          document.body.style.userSelect = ''
          if (frame) window.cancelAnimationFrame(frame)
          apply()
          if (lastWidth !== startWidth) {
            this._commitColumnWidths(startWidths, index, lastWidth, offset)
          }
          // 松手后交回 Vue 渲染，清掉表头临时行内宽度，避免后续列变化时残留旧宽度
          this.$nextTick(() => {
            headerThs.forEach(el => { el.style.width = '' })
            tableStates.forEach(({ cols }) => {
              cols.forEach(colEl => { colEl.style.minWidth = '' })
            })
            // 重新计算填充列/横向滚动条，并把固定右列宽度锁回约定值
            this.syncHScroll()
          })
        }
        document.body.style.cursor = 'col-resize'
        document.body.style.userSelect = 'none'
        document.addEventListener('mousemove', onMove)
        document.addEventListener('mouseup', onUp)
      },

      /** 松手后把冻结的列宽一次性提交到状态与配置（其它列宽度保持不变） */
      _commitColumnWidths(startWidths, dragIndex, dragWidth, offset) {
        const cols = this.displayColumns
        const changed = {}
        startWidths.forEach((width, i) => {
          const col = cols[i - offset]
          if (!col) return
          // 锁定宽度的列（如操作列）不参与列宽持久化，避免被拖动其它列时的实际宽度覆盖
          if (col.__lockWidth) return
          const key = col.dataIndex || col.key
          if (key == null) return
          changed[key] = i === dragIndex ? dragWidth : width
        })
        Object.keys(changed).forEach((key) => {
          this.$set(this.columnWidths, key, changed[key])
          const col = cols.find(c => (c.dataIndex || c.key) === key)
          if (col) this.$set(col, 'width', changed[key])
        })
        this._saveColumnWidths()
      },

      /** 保存列宽：有 tableKey 写入列配置中心，否则写入 localStorage */
      _saveColumnWidths() {
        if (this._widthSaveTimer) {
          clearTimeout(this._widthSaveTimer)
        }
        this._widthSaveTimer = setTimeout(() => {
          const widths = this._normalizeWidths(this.columnWidths)
          if (!this.tableKey) {
            Vue.ls.set(this.resolvedWidthStorageKey, widths, 365 * 24 * 60 * 60 * 1000)
            return
          }
          const config = Object.assign({}, this.configRaw || {})
          config.widths = widths
          if (!config.columns || config.columns.length === 0) {
            config.columns = (this._configColumns && this._configColumns.length > 0)
              ? this._configColumns
              : this.settingDataIndex.slice()
          }
          if (!config.titles) {
            config.titles = this._configTitles || {}
          }
          if (!config.customColumns) {
            config.customColumns = this._configCustomColumns || []
          }
          postAction('/tableColumnConfig/save', {
            tableKey: this.tableKey,
            tableNames: this.configTableNames,
            config: config
          }).then((res) => {
            if (res && res.code === 200) {
              this.configRaw = config
            }
          }).catch(() => {})
        }, 300)
      },

      /** 列勾选变更 */
      onColChange(checkedValues) {
        this.settingDataIndex = checkedValues
        this.displayColumns = this.buildColumns(checkedValues)
        // 用户的列勾选写入 localStorage（有 tableKey 的表格同样保存，配置中心只负责可选列与默认列）
        if (this.selectedColumns === undefined && this.showColumnSetting) {
          Vue.ls.set(this.resolvedStorageKey, checkedValues)
        }
        this.$emit('column-change', checkedValues)
        this.$emit('update:selectedColumns', checkedValues)
      },

      /** 全选列 / 取消全选 */
      handleSelectAllColumns(e) {
        const checked = !!(e && e.target && e.target.checked)
        this.onColChange(checked ? this.settingColumns.map(c => c.dataIndex) : [])
      },

      /** 恢复默认列配置 */
      handleRestDefault() {
        this.settingDataIndex = this._getDefaultColumns()
        this.displayColumns = this.buildColumns(this.settingDataIndex)
        if (this.selectedColumns === undefined && this.showColumnSetting) {
          Vue.ls.remove(this.resolvedStorageKey)
        }
        this.$emit('column-change', this.settingDataIndex)
        this.$emit('update:selectedColumns', this.settingDataIndex)
      },

      /** 暴露给父组件：恢复默认列 */
      resetColumns() {
        this.handleRestDefault()
      },

      /** 暴露给父组件：重新刷新元数据 */
      refreshMeta() {
        if (this.tableName) {
          this._fetchTableMeta()
        }
      },

      /** 暴露给父组件：使用新的 extraColumns 重新初始化列配置 */
      initColumns() {
        // 重建完整列：基础列 + 新的 extraColumns
        this._fullColumns = this._baseColumns.concat(this.extraColumns)
        this._applyColumnFilter()
        // 默认清除 localStorage 缓存，回落到配置中心/defaultColumns；
        // 明细列动态显隐场景下保留用户列设置
        if (!this.preserveColumnSetting) {
          Vue.ls.remove(this.resolvedStorageKey)
        }
        this._initFromStorage()
      },

      /** 暴露给父组件：动态修改列标题（用于自定义字段命名） */
      updateColumnTitle(dataIndex, newTitle) {
        const col = this.allColumns.find(c => c.dataIndex === dataIndex)
        if (col) {
          col.title = newTitle
          // 重新构建 displayColumns 以反映标题变化
          this.displayColumns = this.buildColumns(this.settingDataIndex)
        }
      },

      /** 合并配置中心公式列到可选列（自定义列，按行计算） */
      _applyCustomColumns() {
        this._fullColumns = this._fullColumns.filter(c => !c.__customColumn)
        this.allColumns = this.allColumns.filter(c => !c.__customColumn)
        ;(this._configCustomColumns || []).forEach(cc => {
          if (!cc || !cc.dataIndex) return
          const col = {
            dataIndex: cc.dataIndex,
            title: cc.title || cc.dataIndex,
            formula: cc.formula,
            __customColumn: true
          }
          this._fullColumns.push(col)
          this.allColumns.push(col)
        })
      },

      /** 精简列设置弹窗：仅在自动发现模式（已发现数据库字段）下按默认选中列精简；手工列模式显示全部 extraColumns */
      _applyColumnFilter() {
        const full = this._fullColumns.length ? this._fullColumns : this.allColumns
        if (this._baseColumns.length > 0) {
          const keys = this._getDefaultColumns()
          this.allColumns = full.filter(c => keys.indexOf(c.dataIndex) >= 0)
        } else {
          this.allColumns = full.slice()
        }
        this._applyColumnOverrides()
      },

      /** 应用列标题覆盖：合并配置中心标题 + columnOverrides prop（prop 优先） */
      _applyColumnOverrides() {
        const overrides = Object.assign({}, this._configTitles, this.columnOverrides)
        Object.keys(overrides).forEach(key => {
          const col = this.allColumns.find(c => c.dataIndex === key)
          if (col) {
            col.title = overrides[key]
          }
        })
      }
    }
  }
</script>

<style scoped>
  .data-table-wrapper {
    /* 整体容器 */
  }
  .dt-summary-pager {
    margin-top: 8px;
    text-align: right;
  }
  /* 常显横向滚动条：吸附在表格可视区域底部（不随页面/弹窗滚动而跑到下方） */
  .dt-hscroll {
    position: sticky;
    bottom: 0;
    z-index: 6;
    height: 14px;
    margin-top: -14px;
    overflow-x: auto;
    overflow-y: hidden;
    background: #fafafa;
    border: 1px solid #e8e8e8;
    border-radius: 2px;
    box-shadow: 0 -2px 4px rgba(0, 0, 0, 0.06);
  }
  .dt-hscroll-inner {
    height: 1px;
  }
  .data-table-wrapper ::v-deep .ant-table-body::-webkit-scrollbar {
    width: 8px;
    height: 0;
  }
  .data-table-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 8px;
    min-height: 28px;
  }
  .data-table-toolbar-left {
    flex: 1 1 auto;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    min-width: 0;
  }
  .data-table-toolbar-right {
    flex-shrink: 0;
    text-align: right;
  }

  /* === 内置展开行子表（div flex 布局，紧挨主表） === */
  .data-table-detail {
    padding: 0;
    width: 100%;
  }
  .detail-row {
    display: flex;
    align-items: center;
    border-bottom: 1px solid #f0f2f5;
    box-sizing: border-box;
    width: 100%;
  }
  .detail-row-header {
    font-weight: 500;
    color: #4f535c;
    background: #f7f9fc;
    height: 40px;
  }
  .detail-row-data {
    color: #1d2129;
    background: #ffffff;
    min-height: 40px;
  }
  .detail-cell {
    padding: 0 8px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    box-sizing: border-box;
    text-align: center;
  }
  .detail-cell-right {
    text-align: right;
  }
  .detail-cell-action {
    margin-left: auto;
    flex-shrink: 0;
    text-align: center;
  }
  .detail-action-link {
    margin: 0 8px;
    color: #1890ff;
    cursor: pointer;
  }
  .detail-empty {
    padding: 12px 16px;
    color: #999;
    text-align: center;
  }

  /* 展开行紧挨主表：去掉 td 默认 padding（与 MaterialList 一致） */
  .data-table-wrapper /deep/ .ant-table-tbody > tr.ant-table-expanded-row > td {
    padding: 0 !important;
  }
  /* 固定表格布局：避免整列为空时列宽塌陷、后列前移 */
  .data-table-wrapper /deep/ .ant-table table {
    table-layout: fixed;
  }
</style>
