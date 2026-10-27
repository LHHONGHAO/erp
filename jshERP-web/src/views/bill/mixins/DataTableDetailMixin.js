import { FormTypes } from '@/utils/JEditableTableUtil'
import { getMaterialByBarCode } from '@/api/api'
import { getMpListShort } from '@/utils/util'
import Vue from 'vue'

/**
 * 单据新增页明细表 DataTable 兼容层
 * 说明：
 * 1. 将 JEditableTable 的列定义（materialTable.columns）转换为 DataTable 的 extraColumns；
 * 2. 通过 DataTable 的 detail 模式暴露 getAll/setValues/statisticsColumns 等兼容 API，
 *    使 JEditableTableMixin / BillModalMixin 的提交流程无需改动；
 * 3. 新增商品由“新增”按钮弹出商品选择弹窗，确认后把选中商品字段写入明细行。
 */
export const DataTableDetailMixin = {
  computed: {
    /** 将 materialTable.columns 转换为 DataTable 的 extraColumns（隐藏列不展示） */
    detailColumns() {
      return (this.materialTable.columns || [])
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
    /** 明细表操作列（关联单据后只读时不显示删除） */
    detailActions() {
      if (this.rowCanEdit === false) {
        return []
      }
      return [{
        title: '删除',
        type: 'link',
        icon: 'delete',
        confirm: '确定删除该行吗？',
        fixed: 'right',
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
      // 列宽跟随列定义（px / 数字），保证空数据列也有固定宽度
      if (col.width != null) {
        c.width = col.width
      }
      if (col.fixed) {
        c.fixed = col.fixed
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
          {
            // 仓库等下拉列：列定义未配置选项时，用页面加载的 depotList 回填
            let options = col.options || []
            if (options.length === 0 && col.key === 'depotId' && Array.isArray(this.depotList)) {
              options = this.depotList.map(d => ({ value: d.id, text: d.name }))
            }
            c.editOptions = options.map(o => ({
              value: o.value,
              text: o.text || o.title || String(o.value)
            }))
          }
          c.editProps = {
            placeholder: col.placeholder,
            showSearch: !!col.allowSearch,
            optionFilterProp: 'children'
          }
          break
        case FormTypes.date:
        case FormTypes.datetime:
          c.editType = 'input'
          break
        default:
          // normal / popupJsh / hidden 等：popupJsh 由模板 scoped slot 处理，其余只读
          break
      }
      if (col.readonly) {
        delete c.editType
      }
      return c
    },

    /** DataTable 单元格编辑回调，桥接为 BillModalMixin.onValueChange 事件 */
    onDetailEditChange({ record, key, value }) {
      const column = (this.materialTable.columns || []).find(c => c.key === key) || { key }
      const target = this.$refs[this.refKeys[0]]
      if (typeof this.onValueChange === 'function') {
        this.onValueChange({ type: 'input', row: record, column, value, target })
      }
    },

    /** popupJsh 列（条码/序列号/批号）选中回调，桥接为 BillModalMixin.onValueChange */
    onDetailPopupChange(key, value, record) {
      const column = (this.materialTable.columns || []).find(c => c.key === key) || { key }
      const target = this.$refs[this.refKeys[0]]
      if (typeof this.onValueChange === 'function') {
        this.onValueChange({ type: 'popupJsh', row: record, column, value, target })
      }
    },

    /** 构造 popupJsh 列传给 JSelectList 的 rows（JSON 字符串，含 depotId 等上下文） */
    getPopupRows(record) {
      return JSON.stringify(record || {})
    },

    /** 删除明细行 */
    onDetailDelete(record) {
      const idx = this.materialTable.dataSource.indexOf(record)
      if (idx > -1) {
        this.materialTable.dataSource.splice(idx, 1)
      }
      const target = this.$refs[this.refKeys[0]]
      if (target) {
        target.recalcAllStatisticsColumns()
      }
      if (typeof this.autoChangePrice === 'function') {
        this.autoChangePrice(target)
      }
    },

    /** 打开商品选择弹窗 */
    openSelectMaterial() {
      if (this.$refs.selectMaterialModal) {
        this.$refs.selectMaterialModal.showModal()
      }
    },

    /** 商品选择确认：把选中商品字段写入明细行 */
    onSelectMaterialOk(rows, ids) {
      const barCodes = ids || ''
      //商品信息表格的行主键是商品ID，SKU主键取meId
      const meIds = (rows || []).map(r => r.meId != null ? r.meId : r.id).filter(v => v != null && v !== '').join(',')
      if (!barCodes && !meIds) {
        return
      }
      const param = {
        barCode: barCodes,
        meIds: meIds,
        organId: this.form.getFieldValue('organId'),
        mpList: getMpListShort(Vue.ls.get('materialPropertyList')),
        prefixNo: this.prefixNo
      }
      getMaterialByBarCode(param).then((res) => {
        if (res && res.code === 200) {
          const mList = res.data || []
          const newDetailArr = this.materialTable.dataSource.slice()
          for (let i = 0; i < mList.length; i++) {
            const mInfo = mList[i]
            if (typeof this.changeColumnShow === 'function') {
              this.changeColumnShow(mInfo)
            }
            const mObj = typeof this.parseInfoToObj === 'function' ? this.parseInfoToObj(mInfo) : {}
            mObj.id = this._genDetailId()
            //仓库下拉的选项值为字符串，这里保持一致，避免选中值显示为ID
            mObj.depotId = mInfo.depotId == null ? mInfo.depotId : String(mInfo.depotId)
            mObj.stock = mInfo.stock
            mObj.meId = mInfo.meId
            mObj.mId = mInfo.id
            mObj.code = mInfo.code
            mObj.mnemonic = mInfo.mnemonic
            mObj.weight = mInfo.weight
            mObj.specValues = mInfo.specValues
            mObj.otherField4 = mInfo.otherField4
            mObj.otherField5 = mInfo.otherField5
            mObj.otherField6 = mInfo.otherField6
            mObj.otherField7 = mInfo.otherField7
            mObj.categoryName = mInfo.categoryName
            mObj.skuCode = mInfo.skuCode
            mObj.commodityUnit = mInfo.commodityUnit
            mObj.imgName = mInfo.imgName
            mObj.purchaseDecimal = mInfo.purchaseDecimal
            mObj.commodityDecimal = mInfo.commodityDecimal
            mObj.wholesaleDecimal = mInfo.wholesaleDecimal
            mObj.lowDecimal = mInfo.lowDecimal
            mObj.marketPrice = mInfo.marketPrice
            mObj.fabric = mInfo.fabric
            mObj.filler = mInfo.filler
            mObj.specJson = mInfo.specJson
            mObj.specSummary = mInfo.specSummary
            mObj.skuName = mInfo.skuName
            //已存在相同SKU（或条码）时只累加数量，不再新增明细行
            if (this.mergeSameSkuOnSelect) {
              const existRow = newDetailArr.find(r => {
                if (mInfo.skuCode) {
                  return r.skuCode === mInfo.skuCode
                }
                return r.barCode && r.barCode === mInfo.barCode
              })
              if (existRow) {
                const addNumber = Number(mInfo.operNumber || 1)
                existRow.operNumber = Number(existRow.operNumber || 0) + addNumber
                const unitPrice = Number(existRow.unitPrice || 0)
                existRow.allPrice = Number((existRow.operNumber * unitPrice).toFixed(2))
                existRow.taxLastMoney = existRow.allPrice
                continue
              }
            }
            newDetailArr.push(mObj)
          }
          // 组装/拆卸等单据：首行标记为组合件，其余为普通子件（与原 JEditableTable 流程一致）
          for (let i = 0; i < newDetailArr.length; i++) {
            if (i === 0) {
              newDetailArr[0].mType = '组合件'
            } else {
              newDetailArr[i].mType = '普通子件'
            }
          }
          this.materialTable.dataSource = newDetailArr
          const target = this.$refs[this.refKeys[0]]
          if (target) {
            target.recalcAllStatisticsColumns()
          }
          if (typeof this.autoChangePrice === 'function') {
            this.autoChangePrice(target)
          }
        }
      })
    },

    _genDetailId() {
      return 'tmp_' + Date.now() + '_' + Math.floor(Math.random() * 100000)
    },

    /** 覆盖 JEditableTableMixin.add：不再自动插入空行，改由“新增商品”按钮弹窗选择 */
    add() {
      if (typeof this.addBefore === 'function') this.addBefore()
      if (typeof this.addAfter === 'function') this.addAfter(this.model)
      this.edit({})
    },

    /** 覆盖 JEditableTableMixin.close：重置明细数据 */
    close() {
      this.visible = false
      if (this.materialTable) {
        this.materialTable.dataSource = []
      }
      this.$emit('close')
    }
  }
}
