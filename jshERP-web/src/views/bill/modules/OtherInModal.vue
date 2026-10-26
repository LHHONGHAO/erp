<template>
  <j-modal
    :title="title"
    :width="width"
    :visible="visible"
    :confirmLoading="confirmLoading"
    :keyboard="false"
    :forceRender="true"
    v-bind:prefixNo="prefixNo"
    fullscreen
    switchFullscreen
    @cancel="handleCancel"
    :id="prefixNo"
    style="top:20px;height: 95%;">
    <template slot="footer">
      <a-button @click="handleCancel">取消</a-button>
      <a-button v-if="billPrintFlag && isShowPrintBtn" @click="handlePrintPro('其它入库')">三联打印-新版</a-button>
      <a-button v-if="billPrintFlag && isShowPrintBtn" @click="handlePrint('其它入库')">三联打印</a-button>
      <a-button v-if="checkFlag && isCanCheck" :loading="confirmLoading" @click="handleOkAndCheck">保存并审核</a-button>
      <a-button type="primary" :loading="confirmLoading" @click="handleOkOnly">保存（Ctrl+S）</a-button>
      <!--发起多级审核-->
      <a-button v-if="!checkFlag" @click="handleWorkflow()" type="primary">提交流程</a-button>
    </template>
    <a-spin :spinning="confirmLoading">
      <a-form :form="form">
        <a-row class="form-row" :gutter="24">
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="供应商">
              <a-select placeholder="请选择供应商" v-decorator="[ 'organId' ]" :disabled="!rowCanEdit"
                :dropdownMatchSelectWidth="false" showSearch optionFilterProp="children" @search="handleSearchSupplier">
                <div slot="dropdownRender" slot-scope="menu">
                  <v-nodes :vnodes="menu" />
                  <a-divider style="margin: 4px 0;" />
                  <div v-if="quickBtn.vendor" class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="addSupplier"><a-icon type="plus" /> 新增供应商</div>
                  <div class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="initSupplier(0)"><a-icon type="reload" /> 刷新列表</div>
                </div>
                <a-select-option v-for="(item,index) in supList" :key="index" :value="item.id">
                  {{ item.supplier }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单据日期">
              <j-date v-decorator="['operTime', validatorRules.operTime]" :show-time="true"/>
            </a-form-item>
          </a-col>
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单据编号">
              <a-input placeholder="请输入单据编号" v-decorator.trim="[ 'number', validatorRules.number ]" />
            </a-form-item>
          </a-col>
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item v-if="inOutManageFlag && !model.billType" :labelCol="labelCol" :wrapperCol="wrapperCol" label="关联单据">
              <a-input-search placeholder="请选择待入库单据" v-decorator="[ 'linkNumber' ]" @search="onSearchLinkNumber" :readOnly="true"/>
            </a-form-item>
          </a-col>
        </a-row>
        <data-table
          ref="materialDataTable"
          :loading="materialTable.loading"
          :data-source="materialTable.dataSource"
          :extra-columns="detailColumns"
          table-key="bill_other_in_detail_v2"
          :default-columns="defDataIndex"
          :scroll="{ x: minWidth }"
          :actions="detailActions"
          :statistics-fields="detailStatisticsFields"
          :summary="true"
          :summary-fields="detailSummaryFields"
          :detail="true"
          :editable="true"
          :preserve-column-setting="true"
          @edit-change="onDetailEditChange">
          <template slot="toolbar">
            <a-input v-if="rowCanEdit" :placeholder="scanPlaceholder" v-model="scanBarCode" @pressEnter="scanPressEnter" ref="scanBarCode" style="width:260px;flex-shrink:0;"/>
            <a-radio-group v-if="rowCanEdit" v-model="scanType" data-step="4" data-title="扫码录入" data-intro="支持按SKU码、商品码、二维码录入商品" style="flex-shrink:0;">
              <a-radio-button value="sku">SKU码</a-radio-button>
              <a-radio-button value="barCode">商品码</a-radio-button>
              <a-radio-button value="qrCode">二维码</a-radio-button>
            </a-radio-group>
            <a-button type="primary" icon="plus" @click="openSelectMaterial" style="flex-shrink:0;">新增商品</a-button>
            <a-button @click="handleBatchSetDepot" style="flex-shrink:0;">批量设置仓库</a-button>
            <a-button icon="import" @click="onImport(prefixNo)" style="flex-shrink:0;">导入明细</a-button>
          </template>
          <template slot="imgName" slot-scope="text, record">
            <img v-if="getRowImgUrl(record)" :src="getRowImgUrl(record)" class="detail-sku-img" />
            <span v-else class="detail-sku-img-empty">无图</span>
          </template>
          <template slot="specDisplay" slot-scope="text, record">
            <div class="detail-spec-display">{{ getSpecDisplay(record) }}</div>
          </template>
          <template slot="barCode" slot-scope="text, record">
            <j-select-list kind="material" :value="record.barCode" :rows="getPopupRows(record)" :multi="true" @change="v => onDetailPopupChange('barCode', v, record)" />
          </template>
          <template slot="snList" slot-scope="text, record">
            <j-select-list kind="snAdd" :value="record.snList" :rows="getPopupRows(record)" :multi="true" @change="v => onDetailPopupChange('snList', v, record)" />
          </template>
        </data-table>
        <a-row class="form-row" :gutter="24">
          <a-col :lg="24" :md="24" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="{xs: { span: 24 },sm: { span: 24 }}" label="">
              <a-textarea :rows="1" placeholder="请输入备注" v-decorator="[ 'remark' ]" style="margin-top:8px;"/>
            </a-form-item>
          </a-col>
        </a-row>
        <a-row class="form-row" :gutter="24">
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="附件">
              <j-upload v-model="fileList" bizPath="bill"></j-upload>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-spin>
    <vendor-modal ref="vendorModalForm" @ok="vendorModalFormOk"></vendor-modal>
    <depot-modal ref="depotModalForm" @ok="depotModalFormOk"></depot-modal>
    <batch-set-depot ref="batchSetDepotModalForm" @ok="batchSetDepotModalFormOk"></batch-set-depot>
    <import-item-modal ref="importItemModalForm" @ok="importItemModalFormOk"></import-item-modal>
    <wait-bill-list ref="waitBillList" @ok="waitBillListOk"></wait-bill-list>
    <workflow-iframe ref="modalWorkflow" @ok="workflowModalFormOk"></workflow-iframe>
    <bill-print-iframe ref="modalPrint"></bill-print-iframe>
    <bill-print-pro-iframe ref="modalPrintPro"></bill-print-pro-iframe>
    <j-select-material-modal ref="selectMaterialModal" :multi="true" @ok="onSelectMaterialOk"></j-select-material-modal>
  </j-modal>
</template>
<script>
  import pick from 'lodash.pick'
  import VendorModal from '../../system/modules/VendorModal'
  import DepotModal from '../../system/modules/DepotModal'
  import BatchSetDepot from '../dialog/BatchSetDepot'
  import ImportItemModal from '../dialog/ImportItemModal'
  import WorkflowIframe from '@/components/tools/WorkflowIframe'
  import BillPrintIframe from '../dialog/BillPrintIframe'
  import BillPrintProIframe from '../dialog/BillPrintProIframe'
  import { FormTypes } from '@/utils/JEditableTableUtil'
  import { JEditableTableMixin } from '@/mixins/JEditableTableMixin'
  import { BillModalMixin } from '../mixins/BillModalMixin'
  import { DataTableDetailMixin } from '../mixins/DataTableDetailMixin'
  import { getMpListShort } from "@/utils/util"
  import { getMaterialByBarCode } from '@/api/api'
  import JUpload from '@/components/jeecg/JUpload'
  import JDate from '@/components/jeecg/JDate'
  import DataTable from '@/components/DataTable'
  import JSelectList from '@/components/jeecgbiz/JSelectList'
  import JSelectMaterialModal from '@/components/jeecgbiz/modal/JSelectMaterialModal'
  import Vue from 'vue'
  import WaitBillList from '../dialog/WaitBillList'
  export default {
    name: "OtherInModal",
    mixins: [JEditableTableMixin, BillModalMixin, DataTableDetailMixin],
    components: {
      VendorModal,
      DepotModal,
      BatchSetDepot,
      ImportItemModal,
      WaitBillList,
      WorkflowIframe,
      BillPrintIframe,
      BillPrintProIframe,
      JUpload,
      JDate,
      DataTable,
      JSelectList,
      JSelectMaterialModal,
      VNodes: {
        functional: true,
        render: (h, ctx) => ctx.props.vnodes,
      }
    },
    data () {
      return {
        title:"操作",
        width: '1600px',
        moreStatus: false,
        // 新增时子表默认添加几行空数据
        addDefaultRowNum: 1,
        visible: false,
        operTimeStr: '',
        prefixNo: 'QTRK',
        fileList:[],
        rowCanEdit: true,
        //扫码录入方式：sku=SKU码，barCode=商品码，qrCode=二维码
        scanType: 'sku',
        //选择商品加入明细时，相同SKU只累加数量、不新增行
        mergeSameSkuOnSelect: true,
        //出入库管理开关，适合独立仓管场景
        inOutManageFlag: false,
        model: {},
        labelCol: {
          xs: { span: 24 },
          sm: { span: 8 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 16 },
        },
        refKeys: ['materialDataTable', ],
        activeKey: 'materialDataTable',
        defColumns: [
          { title: '图片', key: 'imgName', width: '6%', type: FormTypes.normal },
          { title: '商品名称', key: 'name', width: '14%', type: FormTypes.normal },
          { title: 'SKU商品码', key: 'skuCode', width: '12%', type: FormTypes.normal },
          { title: '产品类型', key: 'categoryName', width: '9%', type: FormTypes.normal },
          { title: '单位', key: 'unit', width: '5%', type: FormTypes.normal },
          { title: '面料', key: 'fabric', width: '7%', type: FormTypes.normal },
          { title: '规格显示', key: 'specDisplay', width: '16%', type: FormTypes.normal },
          { title: '填充物', key: 'filler', width: '7%', type: FormTypes.normal },
          { title: '进货价', key: 'purchaseDecimal', width: '6%', type: FormTypes.normal },
          { title: '零售价', key: 'commodityDecimal', width: '6%', type: FormTypes.normal },
          { title: '划线价', key: 'marketPrice', width: '6%', type: FormTypes.normal },
          { title: '数量', key: 'operNumber', width: '6%', type: FormTypes.inputNumber, statistics: true,
            validateRules: [{ required: true, message: '${title}不能为空' }]
          },
          { title: '仓库', key: 'depotId', width: '9%', type: FormTypes.select, placeholder: '请选择${title}', options: [],
            allowSearch:true, validateRules: [{ required: true, message: '${title}不能为空' }]
          },
          { title: '单价', key: 'unitPrice', width: '5%', type: FormTypes.hidden },
          { title: '金额', key: 'allPrice', width: '5%', type: FormTypes.hidden, statistics: true },
          { title: '关联id', key: 'linkId', width: '5%', type: FormTypes.hidden },
        ],
        defDataIndex: ['imgName','name','skuCode','categoryName','unit','fabric','specDisplay','filler','purchaseDecimal','commodityDecimal','marketPrice','operNumber','depotId'],
        materialTable: {
          loading: false,
          dataSource: [],
          columns: []
        },
        confirmLoading: false,
        validatorRules:{
          operTime:{
            rules: [
              { required: true, message: '请输入单据日期!' }
            ]
          },
          number:{
            rules: [
              { required: true, message: '请输入单据编号!' }
            ]
          },
          type:{
            rules: [
              { required: true, message: '请选择类型!' }
            ]
          }
        },
        url: {
          add: '/depotHead/addDepotHeadAndDetail',
          edit: '/depotHead/updateDepotHeadAndDetail',
          detailList: '/depotItem/getDetailList'
        }
      }
    },
    created () {
      this.initColumns()
    },
    computed: {
      //扫码输入框提示语，跟随所选录入方式变化
      scanPlaceholder() {
        if (this.scanType === 'sku') {
          return '请输入或扫描SKU码后回车，直接加入明细（重复输入数量+1）'
        }
        if (this.scanType === 'barCode') {
          return '请输入或扫描商品码后回车，弹出该码对应的商品供选择'
        }
        return '请输入或扫描二维码后回车，弹出该码对应的商品供选择'
      },
      //金额、价税合计不在列设置中展示，但仍需参与合计统计
      detailStatisticsFields() {
        return ['operNumber', 'allPrice', 'taxLastMoney']
      }
    },
    methods: {
      //扫码/输入回车：SKU码直接加入明细，商品码/二维码弹出商品选择窗
      scanPressEnter() {
        const code = (this.scanBarCode || '').trim()
        if (!code) return
        if (this.scanType !== 'sku') {
          //商品码/二维码：弹出该码对应的所有商品供选择
          this.clearScanAndFocus()
          this.$refs.selectMaterialModal.showModal(code)
          return
        }
        this.addSkuCodeToDetail(code)
      },
      //按SKU码查询商品并加入明细；同一SKU重复录入则数量+1
      addSkuCodeToDetail(skuCode) {
        const param = {
          skuCode: skuCode,
          organId: this.form.getFieldValue('organId'),
          mpList: getMpListShort(Vue.ls.get('materialPropertyList')),
          prefixNo: this.prefixNo
        }
        getMaterialByBarCode(param).then((res) => {
          if (!(res && res.code === 200 && res.data && res.data.length > 0)) {
            this.$message.warning('未找到SKU码为「' + skuCode + '」的商品')
            this.clearScanAndFocus()
            return
          }
          const mInfo = res.data[0]
          const detailArr = this.materialTable.dataSource.slice()
          const exist = detailArr.find(r => mInfo.meId != null && String(r.meId) === String(mInfo.meId))
          if (exist) {
            //同一SKU重复录入：数量+1，并同步金额
            exist.operNumber = (exist.operNumber - 0) + 1
            const unitPrice = exist.unitPrice - 0
            exist.allPrice = (unitPrice * exist.operNumber).toFixed(2) - 0
            exist.taxMoney = ((exist.taxRate - 0) * 0.01 * exist.allPrice).toFixed(2) - 0
            exist.taxLastMoney = (exist.allPrice + exist.taxMoney).toFixed(2) - 0
          } else {
            this.changeColumnShow(mInfo)
            const mObj = this.parseInfoToObj(mInfo)
            mObj.id = this._genDetailId()
            //仓库下拉的选项值为字符串，这里保持一致
            mObj.depotId = mInfo.depotId == null ? mInfo.depotId : String(mInfo.depotId)
            mObj.stock = mInfo.stock
            mObj.meId = mInfo.meId
            mObj.mId = mInfo.id
            mObj.code = mInfo.code
            mObj.skuCode = mInfo.skuCode
            mObj.specValues = mInfo.specValues
            mObj.otherField4 = mInfo.otherField4
            mObj.otherField5 = mInfo.otherField5
            mObj.otherField6 = mInfo.otherField6
            mObj.otherField7 = mInfo.otherField7
            mObj.categoryName = mInfo.categoryName
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
            detailArr.push(mObj)
          }
          this.materialTable.dataSource = detailArr
          this.$nextTick(() => {
            const target = this.$refs[this.refKeys[0]]
            if (target && typeof target.recalcAllStatisticsColumns === 'function') {
              target.recalcAllStatisticsColumns()
            }
            if (typeof this.autoChangePrice === 'function') {
              this.autoChangePrice(target)
            }
          })
          this.clearScanAndFocus()
        })
      },
      //清空扫码输入框并重新聚焦，方便连续录入
      clearScanAndFocus() {
        this.scanBarCode = ''
        this.$nextTick(() => {
          if (this.$refs.scanBarCode) this.$refs.scanBarCode.focus()
        })
      },
      //明细行图片地址（与商品信息页面保持一致）
      getRowImgUrl(record) {
        if (!record || !record.imgName) return ''
        const imgUrl = record.imgName
        if (imgUrl.startsWith('http')) return imgUrl
        const base = (window._CONFIG && window._CONFIG['domianURL']) ? window._CONFIG['domianURL'] : ''
        if (imgUrl.indexOf('systemConfig/static/') === -1) {
          return base + '/systemConfig/static/' + imgUrl
        }
        return base + '/' + imgUrl
      },
      //规格显示（与商品信息页面一致）：规格名：规格值，多个规格换行并列
      getSpecDisplay(record) {
        if (!record) return ''
        if (record.specJson) {
          try {
            const arr = JSON.parse(record.specJson)
            if (Array.isArray(arr) && arr.length > 0) {
              return arr.map(item => {
                const name = item.name || item.attributeName || ''
                const value = item.value || item.valueText || ''
                return name ? (name + '：' + value) : value
              }).filter(t => t !== '').join('\n')
            }
          } catch (e) {
            // 解析失败继续走兜底
          }
        }
        if (record.specSummary) {
          return record.specSummary.split(';').map(part => part.replace(':', '：')).filter(t => t !== '').join('\n')
        }
        if (record.specValues) {
          try {
            const arr = JSON.parse(record.specValues)
            if (Array.isArray(arr) && arr.length > 0) {
              return arr.map(item => {
                const name = item.attributeName || item.name || ''
                const value = item.valueText || item.value || ''
                return name ? (name + '：' + value) : value
              }).filter(t => t !== '').join('\n')
            }
          } catch (e) {
            return record.specValues
          }
        }
        return ''
      },
      //调用完edit()方法之后会自动调用此方法
      editAfter() {
        this.billStatus = '0'
        this.currentSelectDepotId = ''
        this.rowCanEdit = true
        this.materialTable.columns[1].type = FormTypes.popupJsh
        this.changeColumnHide()
        this.changeFormTypes(this.materialTable.columns, 'snList', 0)
        this.changeFormTypes(this.materialTable.columns, 'batchNumber', 0)
        this.changeFormTypes(this.materialTable.columns, 'expirationDate', 0)
        this.changeFormTypes(this.materialTable.columns, 'preNumber', 0)
        this.changeFormTypes(this.materialTable.columns, 'finishNumber', 0)
        if (this.action === 'add') {
          this.addInit(this.prefixNo)
          this.fileList = []
        } else {
          if(this.model.linkNumber) {
            this.rowCanEdit = false
            this.materialTable.columns[1].type = FormTypes.normal
          }
          this.model.operTime = this.model.operTimeStr
          this.fileList = this.model.fileName
          this.$nextTick(() => {
            this.form.setFieldsValue(pick(this.model,'organId', 'operTime', 'number', 'linkNumber', 'remark',
              'discount','discountMoney','discountLastMoney','otherMoney','accountId','changeAmount'))
          });
          // 加载子表数据
          let params = {
            headerId: this.model.id,
            mpList: getMpListShort(Vue.ls.get('materialPropertyList')),  //扩展属性
            linkType: 'basic'
          }
          let url = this.readOnly ? this.url.detailList : this.url.detailList;
          this.requestSubTableData(url, params, this.materialTable);
        }
        //复制新增单据-初始化单号和日期
        if(this.action === 'copyAdd') {
          this.model.id = ''
          this.model.tenantId = ''
          this.copyAddInit(this.prefixNo)
        }
        this.initSystemConfig()
        this.initSupplier(0)
        this.initDepot()
        this.initPlatform()
        this.initQuickBtn()
        this.handleChangeOtherField()
      },
      //提交单据时整理成formData
      classifyIntoFormData(allValues) {
        let totalPrice = 0
        let billMain = Object.assign(this.model, allValues.formValue)
        let detailArr = allValues.tablesValue[0].values
        billMain.type = '入库'
        billMain.subType = '其它'
        for(let item of detailArr){
          totalPrice += item.allPrice-0
        }
        billMain.totalPrice = 0-totalPrice
        if(this.fileList && this.fileList.length > 0) {
          billMain.fileName = this.fileList
        } else {
          billMain.fileName = ''
        }
        if(this.model.id){
          billMain.id = this.model.id
        }
        billMain.status = this.billStatus
        return {
          info: JSON.stringify(billMain),
          rows: JSON.stringify(detailArr),
        }
      },
      onSearchLinkNumber() {
        this.$refs.waitBillList.show('入库', '采购,销售退货', "1,3")
        this.$refs.waitBillList.title = "请选择采购入库或销售退货"
      },
      waitBillListOk(selectBillDetailRows, linkNumber, remark) {
        this.rowCanEdit = false
        this.materialTable.columns[1].type = FormTypes.normal
        this.changeFormTypes(this.materialTable.columns, 'preNumber', 1)
        this.changeFormTypes(this.materialTable.columns, 'finishNumber', 1)
        if(selectBillDetailRows && selectBillDetailRows.length>0) {
          let listEx = []
          for(let j=0; j<selectBillDetailRows.length; j++) {
            let info = selectBillDetailRows[j];
            if(info.finishNumber>0) {
              info.operNumber = info.preNumber - info.finishNumber
            }
            info.unitPrice = 0
            info.allPrice = 0
            info.linkId = info.id
            listEx.push(info)
            this.changeColumnShow(info)
          }
          this.materialTable.dataSource = listEx
          this.$nextTick(() => {
            this.form.setFieldsValue({
              'linkNumber': linkNumber,
              'remark': remark
            })
          })
        }
      },
      initColumns() {
        this.materialTable.columns = this.defColumns.map(c => Object.assign({}, c))
      }
    }
  }
</script>
<style scoped>
  .detail-sku-img {
    width: 38px;
    height: 38px;
    object-fit: cover;
    border-radius: 3px;
  }
  .detail-sku-img-empty {
    color: #ccc;
    font-size: 12px;
  }
  .detail-spec-display {
    white-space: pre-line;
    line-height: 18px;
  }
</style>