import { FormTypes } from '@/utils/JEditableTableUtil'

/**
 * 财务单据新增页明细表 DataTable 兼容层
 * 说明：
 * 1. 将 JEditableTable 的列定义（accountTable.columns）转换为 DataTable 的 extraColumns；
 * 2. 通过 DataTable 的 detail 模式暴露 getAll/statisticsColumns 等兼容 API，
 *    使 JEditableTableMixin / FinancialModalMixin 的提交流程无需改动；
 * 3. 明细行新增由“插入行”按钮写入空行，编辑、删除后同步重算统计并联动主表金额。
 */
export const FinancialDataTableDetailMixin = {
  computed: {
    /** 将 accountTable.columns 转换为 DataTable 的 extraColumns（隐藏列不展示） */
    detailColumns() {
      return (this.accountTable.columns || [])
        .filter(c => c.type !== FormTypes.hidden)
        .map(c => this.toDetailColumn(c))
    },
    /** 需要统计的字段（statistics: true 的列） */
    detailStatisticsFields() {
      return this.detailColumns.filter(c => c.statistics).map(c => c.dataIndex)
    },
    /** 汇总行需要合计的字段（逗号分隔） */
    detailSummaryFields() {
      return this.detailStatisticsFields.join(',')
    },
    /** 明细表操作列 */
    detailActions() {
      return [{
        title: '删除',
        type: 'link',
        icon: 'delete',
        confirm: '确定删除该行吗？',
        click: (record) => this.onDetailDelete(record)
      }]
    }
  },
  methods: {
    /** 将单个 JEditableTable 列定义转换为 DataTable 列定义 */
    toDetailColumn(col) {
      const c = {
        dataIndex: col.key,
        title: col.title,
        key: col.key,
        statistics: !!col.statistics,
        validateRules: col.validateRules
      }
      switch (col.type) {
        case FormTypes.inputNumber:
          c.editType = 'inputNumber'
          break
        case FormTypes.input:
          c.editType = 'input'
          break
        case FormTypes.select:
          c.editType = 'select'
          c.editOptions = (col.options || []).map(o => ({
            value: String(o.value),
            text: o.text || o.title || String(o.value)
          }))
          c.editProps = {
            placeholder: col.placeholder,
            showSearch: !!col.allowSearch,
            optionFilterProp: 'children'
          }
          break
        default:
          break
      }
      if (col.readonly) {
        delete c.editType
      }
      return c
    },

    /** DataTable 单元格编辑回调，桥接为 FinancialModalMixin.onValueChange 事件 */
    onDetailEditChange({ record, key, value }) {
      const column = (this.accountTable.columns || []).find(c => c.key === key) || { key }
      const target = this.$refs[this.refKeys[0]]
      if (typeof this.onValueChange === 'function') {
        this.onValueChange({ type: 'input', row: record, column, value, target })
      }
    },

    /** 删除明细行：移除行后重算统计并联动主表金额 */
    onDetailDelete(record) {
      const idx = this.accountTable.dataSource.indexOf(record)
      if (idx > -1) {
        this.accountTable.dataSource.splice(idx, 1)
      }
      const target = this.$refs[this.refKeys[0]]
      if (target) {
        target.recalcAllStatisticsColumns()
      }
      if (typeof this.autoChangeAmount === 'function') {
        this.autoChangeAmount(target)
      }
    },

    /** 工具栏“插入行”：新增一条空明细行 */
    addDetailRow() {
      this.accountTable.dataSource.push({ id: this._genDetailId() })
      const target = this.$refs[this.refKeys[0]]
      if (target) {
        target.recalcAllStatisticsColumns()
      }
      if (typeof this.autoChangeAmount === 'function') {
        this.autoChangeAmount(target)
      }
    },

    /** 行 key：兼容“选择期初”等无 id 的行 */
    detailRowKey(record, index) {
      return record && record.id != null ? record.id : ('row_' + index)
    },

    _genDetailId() {
      return 'tmp_' + Date.now() + '_' + Math.floor(Math.random() * 100000)
    }
  }
}
