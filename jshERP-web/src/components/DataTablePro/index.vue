<template>
  <div class="dt-pro">
    <!-- 工具栏：左侧业务按钮 + 右侧列设置 -->
    <div class="dt-pro-toolbar" v-if="hasToolbar">
      <div class="dt-pro-toolbar-left">
        <slot name="toolbar"></slot>
      </div>
      <div class="dt-pro-toolbar-right" v-if="showColumnSetting && allColumns.length > 0">
        <span class="dt-pro-width-info" v-if="showWidthInfo">表宽 {{ tableWidth }}px</span>
        <a-popover trigger="click" placement="bottomRight" :overlayStyle="{ zIndex: 1050 }">
          <template slot="content">
            <div class="dt-pro-setting">
              <div class="dt-pro-setting-tip">按勾选顺序排列显示</div>
              <div class="dt-pro-setting-list">
                <a-checkbox
                  v-for="item in allColumns"
                  :key="item.dataIndex"
                  :checked="isColumnChecked(item.dataIndex)"
                  @change="toggleColumn(item.dataIndex, $event)">
                  {{ item.title || item.dataIndex }}
                </a-checkbox>
              </div>
              <div class="dt-pro-setting-footer">
                <a-checkbox
                  :checked="isAllChecked"
                  :indeterminate="isIndeterminate"
                  @change="toggleAllColumns">全选</a-checkbox>
                <a-button type="link" size="small" @click="resetColumns">恢复默认</a-button>
              </div>
            </div>
          </template>
          <a-button icon="setting">列设置</a-button>
        </a-popover>
      </div>
    </div>

    <a-table
      ref="table"
      :size="size"
      :bordered="bordered"
      :columns="displayColumns"
      :data-source="displayDataSource"
      :loading="loading"
      :pagination="resolvedPagination"
      :row-selection="rowSelection"
      :row-key="rowKey"
      :custom-row="customRow"
      :components="tableComponents"
      :scroll="resolvedScroll"
      v-bind="$attrs"
      v-on="$listeners">
    </a-table>
  </div>
</template>

<script>
  import Vue from 'vue'

  /**
   * 通用表格组件（新版）
   * 核心规则（与 Windows/Excel 一致）：
   * 1. 每一列始终持有明确的像素宽度，表格总宽 = 所有列宽之和（含选择列）；
   * 2. 拖动某一列只改变该列宽度，其它列宽度恒定，表格总宽随之增减；
   * 3. 列宽总和小于容器宽度时右侧留空，不拉伸任何列；
   * 4. 列勾选顺序即显示顺序，勾选结果与列宽持久化到 localStorage。
   */
  export default {
    name: 'DataTablePro',
    props: {
      /** 列定义：[{ dataIndex, key?, title, width?, align?, ellipsis?, scopedSlots? }] */
      columns: { type: Array, default: () => [] },
      /** 追加列定义（兼容业务页面的 extraColumns 用法） */
      extraColumns: { type: Array, default: () => [] },
      /** 默认显示列（dataIndex 数组），未配置本地缓存时生效 */
      defaultColumns: { type: Array, default: undefined },
      /** 受控列选择（dataIndex 数组），提供后由父组件控制 */
      selectedColumns: { type: Array, default: undefined },
      /** 缓存命名空间（列勾选、列宽） */
      tableKey: { type: String, default: '' },
      /** 显式指定存储键，优先级高于 tableKey */
      storageKey: { type: String, default: '' },
      dataSource: { type: Array, default: () => [] },
      loading: { type: Boolean, default: false },
      pagination: { type: [Object, Boolean], default: undefined },
      rowSelection: { type: Object, default: undefined },
      rowKey: { type: [String, Function], default: 'id' },
      customRow: { type: Function, default: undefined },
      /** 透传 a-table 的 scroll 配置（x 由组件统一接管） */
      scroll: { type: Object, default: undefined },
      /** 操作列定义（未在 columns 中定义 action 列时自动追加） */
      actions: { type: Array, default: undefined },
      bordered: { type: Boolean, default: true },
      size: { type: String, default: 'middle' },
      /** 是否允许拖拽列宽 */
      draggable: { type: Boolean, default: true },
      /** 列宽下限 */
      minColumnWidth: { type: Number, default: 50 },
      /** 列未指定宽度时的兜底宽度 */
      defaultColumnWidth: { type: Number, default: 140 },
      /** 是否显示列设置 */
      showColumnSetting: { type: Boolean, default: true },
      /** 是否显示表宽信息（调试用） */
      showWidthInfo: { type: Boolean, default: false },
      /** 汇总行 */
      summary: { type: Boolean, default: false },
      summaryFields: { type: String, default: '' }
    },
    data() {
      return {
        /** 列宽：{ dataIndex: px }，所有显示列都必须有值 */
        widths: {},
        /** 当前显示的列 key（顺序即显示顺序） */
        settingKeys: []
      }
    },
    computed: {
      /** 受控模式 */
      isControlled() {
        return this.selectedColumns !== undefined
      },
      /** 全部可选列 */
      allColumns() {
        const merged = (this.columns || []).concat(this.extraColumns || [])
        const result = []
        const seen = {}
        merged.forEach(col => {
          if (!col) return
          const key = col.dataIndex || col.key
          if (!key || seen[key]) return
          seen[key] = true
          result.push(Object.assign({}, col, { dataIndex: key, key: key }))
        })
        return result
      },
      /** 统计列（含自动追加的操作列） */
      tableColumns() {
        const cols = this.settingKeys
          .map(key => this.allColumns.find(c => c.dataIndex === key))
          .filter(Boolean)
          .map(col => Object.assign({}, col))
        if (this.actions && this.actions.length > 0 && !cols.some(c => c.dataIndex === 'action')) {
          cols.push(this.buildActionColumn())
        }
        return cols
      },
      /** a-table 渲染列：写入像素宽度 + 插槽渲染 */
      displayColumns() {
        return this.tableColumns.map(col => {
          const key = col.dataIndex || col.key
          const widthKey = col.__widthKey || key
          const next = Object.assign({}, col)
          next.key = key
          next.width = this.widthOf(widthKey)
          const slotName = this.slotNameOf(col)
          if (slotName) {
            const self = this
            next.customRender = function(text, record, index, column) {
              if (record && record._dtProSummary) return ''
              const slotFn = self.$scopedSlots[slotName]
              return slotFn ? slotFn(text, record, index, column) : text
            }
          }
          return next
        })
      },
      /** 选择列宽度（与 a-table 内部保持一致，默认 60px） */
      selectionWidth() {
        if (!this.rowSelection) return 0
        return this.parseWidth(this.rowSelection.columnWidth) || 60
      },
      /** 固定表宽 = 所有显示列宽之和 + 选择列宽 */
      tableWidth() {
        const total = this.displayColumns.reduce((sum, col) => sum + col.width, 0)
        return total + this.selectionWidth
      },
      /** 横向滚动：x 固定为表宽，保证其他列不被拉伸 */
      resolvedScroll() {
        const base = (this.scroll && typeof this.scroll === 'object') ? this.scroll : {}
        return Object.assign({}, base, { x: this.tableWidth })
      },
      resolvedPagination() {
        if (this.pagination === false) return false
        const base = { showSizeChanger: true, pageSizeOptions: ['10', '20', '30', '50', '100'] }
        if (this.pagination && typeof this.pagination === 'object') {
          return Object.assign({}, base, this.pagination)
        }
        return Object.assign({}, base)
      },
      hasToolbar() {
        return !!(this.$slots.toolbar || this.$scopedSlots.toolbar) ||
          (this.showColumnSetting && this.allColumns.length > 0)
      },
      isAllChecked() {
        return this.allColumns.length > 0 &&
          this.allColumns.every(c => this.settingKeys.indexOf(c.dataIndex) >= 0)
      },
      isIndeterminate() {
        const count = this.settingKeys.length
        return count > 0 && count < this.allColumns.length
      },
      /** 汇总行数据源 */
      displayDataSource() {
        if (!this.summary || !this.dataSource || this.dataSource.length === 0) {
          return this.dataSource
        }
        const fields = (this.summaryFields || '').split(',').filter(Boolean)
        const totalRow = { _dtProSummary: true }
        const labelCol = this.displayColumns.find(c => c.dataIndex !== 'action')
        if (labelCol) totalRow[labelCol.dataIndex] = '合计'
        this.displayColumns.forEach(col => {
          const key = col.dataIndex
          if (key === (labelCol && labelCol.dataIndex)) return
          if (fields.indexOf(key) > -1) {
            let total = 0
            this.dataSource.forEach(row => {
              const v = parseFloat(row[key])
              if (!isNaN(v)) total += v
            })
            totalRow[key] = total.toFixed(2)
          } else {
            totalRow[key] = '-'
          }
        })
        return this.dataSource.concat([totalRow])
      },
      /** 列宽拖拽手柄 */
      tableComponents() {
        if (!this.draggable) return undefined
        const self = this
        return {
          header: {
            cell: (h, props, children) => {
              const { key, ...restProps } = props
              const col = self.displayColumns.find(c => c.dataIndex === key)
              const thClass = restProps.class ? restProps.class + ' dt-pro-th' : 'dt-pro-th'
              // 固定列不支持拖拽（固定列由独立的左右表格渲染，拖拽会导致列索引错位）
              if (col && col.fixed) {
                return h('th', Object.assign({}, restProps, { class: thClass }), [children])
              }
              // 表头由 a-table 内部渲染，拿不到组件的 scoped 样式，此处使用行内样式
              const handle = h('span', {
                key: 'dt-pro-resize',
                class: 'dt-pro-resize-handle',
                style: {
                  position: 'absolute',
                  top: '0',
                  bottom: '0',
                  right: '-5px',
                  width: '10px',
                  zIndex: 3,
                  cursor: 'col-resize'
                },
                attrs: { title: '拖动调整列宽' },
                on: {
                  mousedown: (e) => self._onResizeStart(e, key),
                  click: (e) => e.stopPropagation()
                }
              })
              const thStyle = Object.assign({ position: 'relative' }, restProps.style || {})
              return h('th', Object.assign({}, restProps, { class: thClass, style: thStyle }), [children, handle])
            }
          }
        }
      }
    },
    watch: {
      /** 列池变化：补齐缺失列宽，并修正已失效的勾选 */
      allColumns() {
        this.syncWidths()
        const keys = this.allColumns.map(c => c.dataIndex)
        const valid = this.settingKeys.filter(k => keys.indexOf(k) >= 0)
        if (this.isControlled) {
          this.settingKeys = (this.selectedColumns || []).slice()
        } else if (valid.length !== this.settingKeys.length) {
          this.settingKeys = valid.length > 0 ? valid : this.defaultSelection()
        }
      },
      selectedColumns: {
        immediate: true,
        handler(v) {
          if (v === undefined) return
          this.settingKeys = (v || []).slice()
        }
      },
      /** 表宽变化后重新对齐固定列 */
      tableWidth() {
        this.$nextTick(this.syncFixedRightOffset)
      },
      displayColumns() {
        this.$nextTick(this.syncFixedRightOffset)
      }
    },
    created() {
      this.restoreWidths()
      this.syncWidths()
      this.restoreSelection()
    },
    mounted() {
      this.$nextTick(this.syncFixedRightOffset)
      window.addEventListener('resize', this.syncFixedRightOffset)
    },
    beforeDestroy() {
      window.removeEventListener('resize', this.syncFixedRightOffset)
    },
    methods: {
      /** 解析像素宽度，支持数字与 '120px' */
      parseWidth(width) {
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
      /** 获取列宽（必定返回数值） */
      widthOf(key) {
        return this.parseWidth(this.widths[key]) || this.defaultColumnWidth
      },
      /** 存储命名空间 */
      namespace() {
        return this.storageKey || this.tableKey || 'datatable_pro'
      },
      columnsStorageKey() {
        return 'dt_pro_columns_' + this.namespace()
      },
      widthsStorageKey() {
        return 'dt_pro_widths_' + this.namespace()
      },
      /** 给所有列补上初始宽度（已存在的保持不变） */
      syncWidths() {
        this.allColumns.forEach(col => {
          const key = col.dataIndex
          if (this.parseWidth(this.widths[key])) return
          const width = this.parseWidth(col.width) || this.defaultColumnWidth
          this.$set(this.widths, key, width)
        })
        if (this.actions && this.actions.length > 0 && !this.parseWidth(this.widths.__action__)) {
          this.$set(this.widths, '__action__', this.actions.length * 58 + 24)
        }
      },
      /** 读取本地缓存的列宽 */
      restoreWidths() {
        const saved = Vue.ls.get(this.widthsStorageKey())
        if (!saved || typeof saved !== 'object') return
        Object.keys(saved).forEach(key => {
          const width = this.parseWidth(saved[key])
          if (width > 0) this.$set(this.widths, key, width)
        })
      },
      /** 本地缓存的列宽写入 */
      saveWidths() {
        if (this._widthTimer) clearTimeout(this._widthTimer)
        this._widthTimer = setTimeout(() => {
          const data = {}
          Object.keys(this.widths).forEach(key => {
            const width = this.parseWidth(this.widths[key])
            if (width > 0) data[key] = width
          })
          Vue.ls.set(this.widthsStorageKey(), data, 365 * 24 * 60 * 60 * 1000)
          this.$emit('column-width-change', data)
        }, 200)
      },
      /** 读取本地缓存的列勾选（数组，单列场景同样有效） */
      restoreSelection() {
        if (this.isControlled) {
          this.settingKeys = (this.selectedColumns || []).slice()
          return
        }
        let saved = Vue.ls.get(this.columnsStorageKey())
        if (typeof saved === 'string') {
          saved = saved.split(',').filter(Boolean)
        }
        if (Array.isArray(saved)) {
          const keys = this.allColumns.map(c => c.dataIndex)
          this.settingKeys = saved.filter(k => keys.indexOf(k) >= 0)
          return
        }
        this.settingKeys = this.defaultSelection()
      },
      /** 默认显示列 */
      defaultSelection() {
        const keys = this.allColumns.map(c => c.dataIndex)
        if (this.defaultColumns && this.defaultColumns.length > 0) {
          return this.defaultColumns.filter(k => keys.indexOf(k) >= 0)
        }
        return keys
      },
      /** 应用列勾选（按传入顺序显示） */
      applySelection(keys) {
        this.settingKeys = keys.slice()
        if (!this.isControlled) {
          Vue.ls.set(this.columnsStorageKey(), this.settingKeys, 365 * 24 * 60 * 60 * 1000)
        }
        this.$emit('column-change', this.settingKeys.slice())
        this.$emit('update:selectedColumns', this.settingKeys.slice())
      },
      isColumnChecked(key) {
        return this.settingKeys.indexOf(key) >= 0
      },
      /** 勾选/取消单列：追加到末尾或移除 */
      toggleColumn(key, e) {
        const checked = !!(e && e.target && e.target.checked)
        let keys = this.settingKeys.slice()
        if (checked) {
          if (keys.indexOf(key) < 0) keys.push(key)
        } else {
          keys = keys.filter(k => k !== key)
        }
        this.applySelection(keys)
      },
      toggleAllColumns(e) {
        const checked = !!(e && e.target && e.target.checked)
        this.applySelection(checked ? this.allColumns.map(c => c.dataIndex) : [])
      },
      /** 恢复默认列 */
      resetColumns() {
        if (!this.isControlled) {
          Vue.ls.remove(this.columnsStorageKey())
        }
        this.applySelection(this.defaultSelection())
      },
      /** 恢复默认列宽（测试/业务均可调用） */
      resetColumnWidths() {
        Vue.ls.remove(this.widthsStorageKey())
        this.allColumns.forEach(col => {
          this.$set(this.widths, col.dataIndex, this.parseWidth(col.width) || this.defaultColumnWidth)
        })
        if (this.actions && this.actions.length > 0) {
          this.$set(this.widths, '__action__', this.actions.length * 58 + 24)
        }
        this.saveWidths()
      },
      /** 当前列宽快照 */
      getColumnWidths() {
        return Object.assign({}, this.widths)
      },
      /**
       * 对齐右侧固定列：
       * 表宽大于容器时固定列贴容器右侧（横向滚动时不动）；
       * 表宽小于容器时固定列贴表体右边缘，避免与表体之间出现空档。
       */
      syncFixedRightOffset(totalWidth) {
        const root = this.$refs.table && this.$refs.table.$el
        if (!root) return
        const scrollEl = root.querySelector('.ant-table-scroll')
        const fixedRight = root.querySelector('.ant-table-fixed-right')
        if (!scrollEl || !fixedRight) return
        const width = typeof totalWidth === 'number' ? totalWidth : this.tableWidth
        const offset = Math.max(0, Math.round(scrollEl.clientWidth - width))
        fixedRight.style.right = offset > 0 ? offset + 'px' : ''
      },
      /** 插槽名：优先列定义 scopedSlots.customRender，其次同名列插槽 */
      slotNameOf(col) {
        if (col.scopedSlots && col.scopedSlots.customRender) {
          return col.scopedSlots.customRender
        }
        if (this.$scopedSlots[col.dataIndex]) {
          return col.dataIndex
        }
        return null
      },
      /** 自动追加的操作列 */
      buildActionColumn() {
        const self = this
        return {
          dataIndex: 'action',
          key: 'action',
          __widthKey: '__action__',
          title: '操作',
          align: 'center',
          // 操作列固定在表格最右侧，横向滚动时不随内容移动
          fixed: 'right',
          width: this.widthOf('__action__'),
          customRender(text, record, index) {
            if (record && record._dtProSummary) return ''
            const h = self.$createElement
            const btns = self.actions.map(act => h('a-button', {
              props: { type: act.type || 'link', size: 'small', icon: act.icon },
              on: { click: () => act.click && act.click(record, index) }
            }, act.title))
            return h('span', { style: { display: 'inline-flex', gap: '4px' } }, btns)
          }
        }
      },
      /**
       * 开始拖拽列宽（Excel 行为）
       * 拖动期间直接操作 DOM，不触发 Vue 重渲染；松手后一次性提交状态。
       */
      _onResizeStart(e, columnKey) {
        if (!this.draggable) return
        if (e.button !== undefined && e.button !== 0) return
        e.preventDefault()
        e.stopPropagation()
        const handle = e.currentTarget || e.target
        const th = handle && handle.closest ? handle.closest('th') : null
        if (!th || !th.parentNode) return
        // 固定列由独立表格渲染，不参与拖拽，避免列索引错位
        if (th.closest('.ant-table-fixed-left') || th.closest('.ant-table-fixed-right')) return
        const root = this.$refs.table && this.$refs.table.$el
        if (!root) return

        const headerThs = Array.prototype.slice.call(th.parentNode.children)
        const headerIndex = headerThs.indexOf(th)
        if (headerIndex < 0) return
        const cols = this.displayColumns
        const colIndex = cols.findIndex(c => c.dataIndex === columnKey)
        if (colIndex < 0) return
        const offset = headerIndex - colIndex

        // 冻结当前渲染宽度，避免浏览器把剩余空间分摊到其它列
        const startWidths = headerThs.map(el => Math.round(el.getBoundingClientRect().width))
        const startTotal = startWidths.reduce((sum, width) => sum + width, 0)
        const tableStates = Array.prototype.slice.call(root.querySelectorAll('table'))
          .map(table => ({
            table: table,
            cols: Array.prototype.slice.call(table.querySelectorAll('colgroup > col'))
          }))
          .filter(state => state.cols.length === headerThs.length)
        tableStates.forEach(({ table, cols: colEls }) => {
          table.style.tableLayout = 'fixed'
          table.style.width = startTotal + 'px'
          colEls.forEach((colEl, i) => {
            if (startWidths[i] == null) return
            colEl.style.width = startWidths[i] + 'px'
            colEl.style.minWidth = startWidths[i] + 'px'
          })
        })
        headerThs.forEach((el, i) => { el.style.width = startWidths[i] + 'px' })

        const startX = e.clientX
        const startWidth = startWidths[headerIndex] || this.defaultColumnWidth
        let lastWidth = startWidth
        let frame = null

        const apply = () => {
          frame = null
          const total = startTotal + (lastWidth - startWidth)
          tableStates.forEach(({ table, cols: colEls }) => {
            table.style.width = total + 'px'
            const colEl = colEls[headerIndex]
            if (colEl) {
              colEl.style.width = lastWidth + 'px'
              colEl.style.minWidth = lastWidth + 'px'
            }
          })
          th.style.width = lastWidth + 'px'
          // 表宽变化时同步右固定列的位置（表宽小于容器时贴表体右边缘）
          this.syncFixedRightOffset(total)
        }
        const onMove = (ev) => {
          lastWidth = Math.max(this.minColumnWidth, Math.round(startWidth + ev.clientX - startX))
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
            this._commitWidths(startWidths, headerIndex, lastWidth, offset)
          }
          this.$nextTick(() => {
            headerThs.forEach(el => { el.style.width = '' })
            tableStates.forEach(({ cols: colEls }) => {
              colEls.forEach(colEl => { colEl.style.minWidth = '' })
            })
          })
        }
        document.body.style.cursor = 'col-resize'
        document.body.style.userSelect = 'none'
        document.addEventListener('mousemove', onMove)
        document.addEventListener('mouseup', onUp)
      },
      /** 松手后提交列宽：仅被拖动列变化，其它列保持冻结值 */
      _commitWidths(startWidths, dragIndex, dragWidth, offset) {
        const cols = this.displayColumns
        startWidths.forEach((width, i) => {
          const col = cols[i - offset]
          if (!col) return
          const key = col.__widthKey || col.dataIndex
          this.$set(this.widths, key, i === dragIndex ? dragWidth : width)
        })
        this.saveWidths()
      }
    }
  }
</script>

<style scoped>
  .dt-pro-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
    min-height: 28px;
  }
  .dt-pro-toolbar-left {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .dt-pro-toolbar-right {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .dt-pro-width-info {
    color: #999;
    font-size: 12px;
  }
  .dt-pro-setting {
    width: 220px;
  }
  .dt-pro-setting-tip {
    padding: 2px 0 8px;
    color: #999;
    font-size: 12px;
  }
  .dt-pro-setting-list {
    max-height: 360px;
    overflow-y: auto;
    padding-right: 4px;
  }
  .dt-pro-setting-list .ant-checkbox-wrapper {
    display: block;
    margin: 0 0 6px 0;
  }
  .dt-pro-setting-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: 8px;
    margin-top: 4px;
    border-top: 1px solid #f0f0f0;
  }

  /* 固定表格布局：列宽严格等于设定值 */
  .dt-pro /deep/ .ant-table table {
    table-layout: fixed;
  }
  /* antd 默认给表格 min-width:100%，会把列宽总和小于容器时的表格拉伸，这里禁用 */
  .dt-pro /deep/ .ant-table-scroll table {
    min-width: 0;
  }
  .dt-pro /deep/ .ant-table-thead > tr > th.dt-pro-th {
    position: relative;
  }
</style>

<!-- 表头单元格由 a-table 内部渲染，无法命中 scoped 样式，手柄交互样式声明为全局 -->
<style>
  .dt-pro-resize-handle:hover {
    background: rgba(24, 144, 255, 0.15);
  }
</style>
