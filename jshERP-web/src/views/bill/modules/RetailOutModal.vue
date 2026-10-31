<template>
  <component
    :is="inline ? 'div' : 'JModal'"
    v-bind="inline ? {} : modalProps"
    :class="inline ? 'ro-inline-form' : ''"
    @cancel="handleCancel"
    :id="prefixNo"
    :style="inline ? '' : modalStyle">
    <template v-if="!inline" slot="footer">
      <a-button @click="handleCancel">取消</a-button>
      <a-button v-if="billPrintFlag && isShowPrintBtn" @click="handlePrintPro('零售出库')">三联打印-新版</a-button>
      <a-button v-if="billPrintFlag && isShowPrintBtn" @click="handlePrint('零售出库')">三联打印</a-button>
      <a-button v-if="checkFlag && isCanCheck" :loading="confirmLoading" @click="handleOkAndCheck">保存并审核</a-button>
      <a-button type="primary" :loading="confirmLoading" @click="handleOkOnly">保存（Ctrl+S）</a-button>
      <!--发起多级审核-->
      <a-button v-if="!checkFlag" @click="handleWorkflow()" type="primary">提交流程</a-button>
    </template>
    <a-spin :spinning="confirmLoading">
      <a-form :form="form">
        <!-- 单据抬头：与下方明细表格同宽（左右边缘对齐），字段间距压缩 -->
        <a-row class="form-row" :gutter="24">
          <a-col :lg="18" :md="24" :sm="24">
            <a-row class="bill-head-row" :gutter="12">
          <a-col :lg="5" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="会员卡号" data-step="1" data-title="会员卡号"
                         data-intro="如果发现需要选择的会员卡号尚未录入，可以在下拉框中点击新增会员信息进行录入">
              <a-select placeholder="请选择会员卡号" v-decorator="[ 'organId' ]"
                :dropdownMatchSelectWidth="false" showSearch optionFilterProp="children" @change="onChangeOrgan" @search="handleSearchRetail">
                <div slot="dropdownRender" slot-scope="menu">
                  <v-nodes :vnodes="menu" />
                  <a-divider style="margin: 4px 0;" />
                  <div v-if="quickBtn.member" class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="addMember"><a-icon type="plus" /> 新增会员</div>
                  <div class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="initRetail(0)"><a-icon type="reload" /> 刷新列表</div>
                </div>
                <a-select-option v-for="(item,index) in retailList" :key="index" :value="item.id">
                  {{ item.supplier }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :lg="5" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单据日期">
              <j-date v-decorator="['operTime', validatorRules.operTime]" :show-time="true"/>
            </a-form-item>
          </a-col>
          <a-col :lg="5" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单据编号" data-step="2" data-title="单据编号"
                         data-intro="单据编号自动生成、自动累加、开头是单据类型的首字母缩写，累加的规则是每次打开页面会自动占用一个新的编号">
              <a-input placeholder="请输入单据编号" v-decorator.trim="[ 'number', validatorRules.number ]" />
            </a-form-item>
          </a-col>
          <a-col :lg="5" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="仓库">
              <a-select placeholder="请选择仓库" v-model="currentSelectDepotId" :dropdownMatchSelectWidth="false" @change="onChangeDepot">
                <a-select-option v-for="item in depotList" :key="item.id" :value="String(item.id)">
                  {{ item.depotName }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :lg="4" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="收款类型" data-step="3" data-title="收款类型"
                         data-intro="收款类型可以有现付和预付款两种类型，当选择了会员之后，如果该会员有预付款，在此处会显示具体预付款的金额，而且系统会优先默认选中预付款">
              <a-select placeholder="请选择付款类型" v-decorator="[ 'payType' ]" :dropdownMatchSelectWidth="false">
                <a-select-option v-for="(item,index) in payTypeList" :key="index" :value="item.value">
                  {{ item.text }}
                </a-select-option>
              </a-select>
            </a-form-item>
          </a-col>
            </a-row>
          </a-col>
        </a-row>
        <a-row class="form-row" :gutter="24">
          <a-col :lg="18" :md="12" :sm="24">
            <data-table
              ref="materialDataTable"
              :loading="materialTable.loading"
              :data-source="materialTable.dataSource"
              :extra-columns="detailColumns"
              table-key="bill_retail_out_detail_v2"
              :default-columns="defDataIndex"
              :scroll="{ x: minWidth }"
              :actions="detailActions"
              :statistics-fields="detailStatisticsFields"
              :summary="true"
              :summary-fields="detailSummaryFields"
              :detail="true"
              :editable="true"
              :preserve-column-setting="true"
              :show-column-setting-button="false"
              @edit-change="onDetailEditChange">
              <template slot="toolbar">
                <a-input v-if="rowCanEdit" :placeholder="scanPlaceholder" v-model="scanBarCode" @pressEnter="scanPressEnter" ref="scanBarCode" style="width:320px;flex-shrink:0;"/>
                <a-button type="primary" icon="search" @click="scanPressEnter" style="flex-shrink:0;">搜索</a-button>
                <a-button type="primary" icon="plus" @click="openSelectMaterial" style="flex-shrink:0;">新增商品</a-button>
              </template>
              <template slot="barCode" slot-scope="text, record">
                <j-select-list kind="material" :value="record.barCode" :rows="getPopupRows(record)" :multi="true" @change="v => onDetailPopupChange('barCode', v, record)" />
              </template>
              <template slot="imgName" slot-scope="text, record">
                <img v-if="getRowImgUrl(record)" :src="getRowImgUrl(record)" class="detail-sku-img" />
                <span v-else class="detail-sku-img-empty">无图</span>
              </template>
              <template slot="specDisplay" slot-scope="text, record">
                <div class="detail-spec-display">{{ getSpecDisplay(record) }}</div>
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
                <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="附件" data-step="9" data-title="附件"
                             data-intro="可以上传与单据相关的图片、文档，支持多个文件">
                  <j-upload v-model="fileList" bizPath="bill"></j-upload>
                </a-form-item>
              </a-col>
            </a-row>
          </a-col>
          <div class="sign">
            <a-col :lg="6" :md="12" :sm="24">
              <a-row class="form-row" :gutter="24">
                <a-col :lg="24" :md="6" :sm="6"><br/><br/></a-col>
                <a-col :lg="24" :md="6" :sm="6">
                  <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" data-step="5" data-title="单据金额"
                               data-intro="单据金额等于左侧商品的总金额">
                    <span slot="label" style="font-size: 20px;line-height:20px">单据金额</span>
                    <a-input v-decorator.trim="[ 'changeAmount' ]" :style="{color:'purple'}" :readOnly="true"/>
                  </a-form-item>
                </a-col>
                <a-col :lg="24" :md="6" :sm="6">
                  <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" data-step="6" data-title="收款金额"
                               data-intro="收款金额为收银员收取用户的实际金额">
                    <span slot="label" style="font-size: 20px;line-height:20px">收款金额</span>
                    <a-input v-decorator.trim="[ 'getAmount' ]" :style="{color:'red'}" defaultValue="0" @change="onChangeGetAmount"/>
                  </a-form-item>
                </a-col>
                <a-col :lg="24" :md="6" :sm="6">
                  <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" data-step="7" data-title="找零"
                               data-intro="找零等于收款金额减去实收金额">
                    <span slot="label" style="font-size: 20px;line-height:20px">找零</span>
                    <a-input v-decorator.trim="[ 'backAmount' ]" :style="{color:'green'}" :readOnly="true" defaultValue="0"/>
                  </a-form-item>
                </a-col>
                <a-col :lg="24" :md="6" :sm="6">
                  <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" data-step="8" data-title="收款账户"
                               data-intro="收款账户的信息来自基本资料菜单下的【结算账户】">
                    <span slot="label" style="font-size: 20px;line-height:20px">收款账户</span>
                    <a-select placeholder="请选择收款账户" style="font-size:20px;" v-decorator="[ 'accountId', validatorRules.accountId ]" :dropdownMatchSelectWidth="false">
                      <div slot="dropdownRender" slot-scope="menu">
                        <v-nodes :vnodes="menu" />
                        <a-divider style="margin: 4px 0;" />
                        <div v-if="quickBtn.account" class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="addAccount"><a-icon type="plus" /> 新增</div>
                        <div class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="initAccount(0)"><a-icon type="reload" /> 刷新</div>
                      </div>
                      <a-select-option v-for="(item,index) in accountList" :key="index" :value="item.id">
                        {{ item.name }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>
            </a-col>
          </div>
        </a-row>
      </a-form>
    </a-spin>
    <member-modal ref="memberModalForm" @ok="memberModalFormOk"></member-modal>
    <depot-modal ref="depotModalForm" @ok="depotModalFormOk"></depot-modal>
    <account-modal ref="accountModalForm" @ok="accountModalFormOk"></account-modal>
    <import-item-modal ref="importItemModalForm" @ok="importItemModalFormOk"></import-item-modal>
    <j-select-material-modal ref="selectMaterialModal" :multi="true" @ok="onSelectMaterialOk"></j-select-material-modal>
    <workflow-iframe ref="modalWorkflow" @ok="workflowModalFormOk"></workflow-iframe>
    <bill-print-iframe ref="modalPrint"></bill-print-iframe>
    <bill-print-pro-iframe ref="modalPrintPro"></bill-print-pro-iframe>
  </component>
</template>
<script>
  import pick from 'lodash.pick'
  import MemberModal from '../../system/modules/MemberModal'
  import DepotModal from '../../system/modules/DepotModal'
  import AccountModal from '../../system/modules/AccountModal'
  import ImportItemModal from '../dialog/ImportItemModal'
  import WorkflowIframe from '@/components/tools/WorkflowIframe'
  import BillPrintIframe from '../dialog/BillPrintIframe'
  import BillPrintProIframe from '../dialog/BillPrintProIframe'
  import { FormTypes } from '@/utils/JEditableTableUtil'
  import { JEditableTableMixin } from '@/mixins/JEditableTableMixin'
  import { BillModalMixin } from '../mixins/BillModalMixin'
  import { DataTableDetailMixin } from '../mixins/DataTableDetailMixin'
  import { getMpListShort,handleIntroJs } from "@/utils/util"
  import { getAccount } from '@/api/api'
  import { getAction } from '@/api/manage'
  import JUpload from '@/components/jeecg/JUpload'
  import JDate from '@/components/jeecg/JDate'
  import JModal from '@/components/jeecg/JModal'
  import DataTable from '@/components/DataTable'
  import JSelectList from '@/components/jeecgbiz/JSelectList'
  import JSelectMaterialModal from '@/components/jeecgbiz/modal/JSelectMaterialModal'
  import { buildRetailOutColumns, RETAIL_OUT_DEFAULT_COLUMNS } from '../config/detailColumns'
  import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'
  import { getMaterialByBarCode } from '@/api/api'
  import Vue from 'vue'
  export default {
    name: "RetailOutModal",
    mixins: [JEditableTableMixin, BillModalMixin, DataTableDetailMixin],
    props: {
      // 嵌入模式：不使用弹窗，直接把单据表单渲染为页面内容
      inline: {
        type: Boolean,
        default: false
      }
    },
    components: {
      MemberModal,
      DepotModal,
      AccountModal,
      ImportItemModal,
      WorkflowIframe,
      BillPrintIframe,
      BillPrintProIframe,
      JUpload,
      JDate,
      DataTable,
      JSelectList,
      JSelectMaterialModal,
      JModal,
      VNodes: {
        functional: true,
        render: (h, ctx) => ctx.props.vnodes,
      }
    },
    data () {
      return {
        title:"操作",
        width: '1600px',
        modalStyle: 'top:20px;height: 95%;',
        moreStatus: false,
        // 新增时子表默认添加几行空数据
        addDefaultRowNum: 1,
        visible: false,
        operTimeStr: '',
        prefixNo: 'LSCK',
        fileList:[],
        payTypeList: [],
        minWidth: 2200,
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
        // 明细列定义统一来自 '@/views/bill/config/detailColumns'，与入库类明细同一套字段
        defColumns: buildRetailOutColumns(),
        defDataIndex: RETAIL_OUT_DEFAULT_COLUMNS,
        //统一搜索框内容：回车或点击“搜索”后按内容格式自动匹配字段
        scanBarCode: '',
        rowCanEdit: true,
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
          accountId:{
            rules: [
              { required: true, message: '请选择结算账户!' }
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
      this.initPayTypeList()
      this.initColumns()
      this.initDepotSelect()
      let realScreenWidth = window.screen.width
      this.minWidth = realScreenWidth<1500?1800:2200
    },
    computed: {
      // 弹窗模式下透传给 JModal 的属性集合
      modalProps() {
        return {
          title: this.title,
          width: this.width,
          visible: this.visible,
          confirmLoading: this.confirmLoading,
          keyboard: false,
          forceRender: true,
          prefixNo: this.prefixNo,
          fullscreen: true,
          switchHelp: true,
          switchFullscreen: true
        }
      },
      //统一搜索框提示语：按内容自动识别商品码/SKU码/条码
      scanPlaceholder() {
        return '请输入商品码、SKU码或条码后回车/点搜索，直接加入明细'
      }
    },
    watch: {
      // 明细行发生任何变化（选品、扫码、改数量、删除）都重新计算单据金额/收款金额
      'materialTable.dataSource': {
        deep: true,
        handler() {
          this.$nextTick(() => this.autoChangePrice())
        }
      }
    },
    methods: {
      //加载仓库下拉：抬头选定仓库后，明细行统一使用该仓库
      initDepotSelect() {
        getAction('/depot/findDepotByCurrentUser').then((res) => {
          if (res && res.code === 200) {
            this.depotList = res.data || []
            //只有一个仓库时默认选中
            if (this.depotList.length === 1) {
              this.currentSelectDepotId = String(this.depotList[0].id)
            }
          }
        })
      },
      //抬头切换仓库：同步到所有明细行并刷新库存
      onChangeDepot(value) {
        const rows = this.materialTable.dataSource || []
        const target = this.$refs[this.refKeys[0]]
        rows.forEach((row) => {
          row.depotId = value || ''
          if (row.barCode && typeof this.getStockByDepotBarCode === 'function') {
            this.getStockByDepotBarCode(row, target)
          }
        })
      },
      //统一搜索：回车或点击“搜索”后，按输入内容格式识别字段并查询
      //纯数字 → 条码；含字母/横杠 → SKU码或商品码（后台查询同时匹配这两个字段）
      scanPressEnter() {
        const code = (this.scanBarCode || '').trim()
        if (!code) return
        const isBarCode = /^[0-9]+$/.test(code)
        this.addSkuCodeToDetail(code, isBarCode ? 'barCode' : 'skuCode')
      },
      //按识别出的字段查询商品并加入明细；同一商品重复录入则数量+1
      addSkuCodeToDetail(code, field) {
        const param = {
          organId: this.form.getFieldValue('organId'),
          mpList: getMpListShort(Vue.ls.get('materialPropertyList')),
          prefixNo: this.prefixNo
        }
        param[field || 'skuCode'] = code
        getMaterialByBarCode(param).then((res) => {
          if (!(res && res.code === 200 && res.data && res.data.length > 0)) {
            this.$message.warning('未找到「' + code + '」对应的商品')
            this.clearScanAndFocus()
            return
          }
          const mInfo = res.data[0]
          const detailArr = this.materialTable.dataSource.slice()
          const exist = detailArr.find(r => mInfo.meId != null && String(r.meId) === String(mInfo.meId))
          if (exist) {
            exist.operNumber = (exist.operNumber - 0) + 1
            // 金额 = 零售价 × 数量
            const retailPrice = (exist.commodityDecimal - 0) || 0
            exist.unitPrice = retailPrice
            exist.allPrice = (retailPrice * exist.operNumber).toFixed(2) - 0
          } else {
            this.changeColumnShow(mInfo)
            const mObj = this.parseInfoToObj(mInfo)
            mObj.id = this._genDetailId()
            mObj.depotId = this.currentSelectDepotId ? String(this.currentSelectDepotId) : (mInfo.depotId == null ? mInfo.depotId : String(mInfo.depotId))
            mObj.stock = mInfo.stock
            mObj.meId = mInfo.meId
            mObj.mId = mInfo.id
            mObj.code = mInfo.code
            mObj.skuCode = mInfo.skuCode
            mObj.specValues = mInfo.specValues
            mObj.categoryName = mInfo.categoryName
            mObj.imgName = mInfo.imgName
            mObj.purchaseDecimal = mInfo.purchaseDecimal
            mObj.commodityDecimal = mInfo.commodityDecimal
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
      // 图片地址 / 规格显示统一使用 '@/views/material/utils/materialDisplay' 中的实现
      getRowImgUrl,
      getSpecDisplay,
      //调用完edit()方法之后会自动调用此方法
      editAfter() {
        this.billStatus = '0'
        this.currentSelectDepotId = ''
        this.changeColumnHide()
        if (this.action === 'add') {
          this.addInit(this.prefixNo)
          this.fileList = []
          this.$nextTick(() => {
            handleIntroJs(this.prefixNo, 1)
          })
          this.$nextTick(() => {
            this.form.setFieldsValue({'payType': '现付', 'changeAmount':0, 'getAmount':0, 'backAmount':0})
          })
        } else {
          this.model.operTime = this.model.operTimeStr
          if(this.model.backAmount) {
            this.model.getAmount = (this.model.changeAmount + this.model.backAmount).toFixed(2)
          } else {
            this.model.getAmount = this.model.changeAmount
          }
          this.fileList = this.model.fileName
          if(this.model.payType === '预付款'){
            this.payTypeList = []
            this.payTypeList.push({"value":"预付款", "text":"预付款"})
            this.payTypeList.push({"value":"现付", "text":"现付"})
          }
          this.$nextTick(() => {
            this.form.setFieldsValue(pick(this.model,'organId', 'operTime', 'number', 'payType', 'remark',
              'discount','discountMoney','discountLastMoney','otherMoney','accountId','changeAmount','getAmount','backAmount'))
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
        this.initRetail(0)
        this.initDepot()
        this.initAccount(0)
        this.initPlatform()
        this.initQuickBtn()
        this.handleChangeOtherField()
      },
      //提交单据时整理成formData
      classifyIntoFormData(allValues) {
        let totalPrice = 0
        let billMain = Object.assign(this.model, allValues.formValue)
        let detailArr = allValues.tablesValue[0].values
        billMain.type = '出库'
        billMain.subType = '零售'
        for(let item of detailArr){
          totalPrice += item.allPrice-0
        }
        billMain.totalPrice = totalPrice
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
      //加载收款类型
      initPayTypeList() {
        this.payTypeList.push({"value":"现付", "text":"现付"})
      },
      initAccount(isChecked){
        getAccount({}).then((res)=>{
          if(res && res.code === 200) {
            this.accountList = res.data.accountList
            if(isChecked && this.accountList.length>0) {
              this.form.setFieldsValue({'accountId': this.accountList[0].id})
            }
          }
        })
      },
      //选择会员的触发事件
      onChangeOrgan(value) {
        getAction("/supplier/info", {id: value}).then(res=>{
          if(res && res.code === 200){
            this.payTypeList = []
            let info = res.data.info
            if(info.advanceIn) {
              this.payTypeList.push({"value":"预付款", "text":"预付款（" + info.advanceIn + "）"})
              this.payTypeList.push({"value":"现付", "text":"现付"})
              this.$nextTick(() => {
                this.form.setFieldsValue({'payType': '预付款'})
              })
            } else {
              this.payTypeList.push({"value":"现付", "text":"现付"})
              this.$nextTick(() => {
                this.form.setFieldsValue({'payType': '现付'})
              })
            }
          }
        })
      },
      // 覆盖：单价固定取零售价，金额=零售价×数量；不再引入序列号/批号/有效期
      parseInfoToObj(mInfo) {
        const retailPrice = (mInfo.commodityDecimal - 0) || 0
        return {
          barCode: mInfo.mBarCode,
          name: mInfo.name,
          unit: mInfo.commodityUnit,
          sku: mInfo.sku,
          commodityDecimal: mInfo.commodityDecimal,
          operNumber: 1,
          unitPrice: retailPrice,
          allPrice: retailPrice,
          taxRate: mInfo.taxRate,
          taxMoney: mInfo.taxMoney,
          taxLastMoney: mInfo.taxLastMoney
        }
      },
      //改变实收金额、收款金额的值：直接按明细行「金额」列求和，不依赖表格统计列
      autoChangePrice() {
        let allLastMoney = 0
        for (const row of (this.materialTable.dataSource || [])) {
          allLastMoney += (row.allPrice - 0) || 0
        }
        allLastMoney = allLastMoney.toFixed(2) - 0
        this.$nextTick(() => {
          this.form.setFieldsValue({'changeAmount':allLastMoney,'getAmount':allLastMoney,'backAmount':0})
        });
      },
      //改变收款金额
      onChangeGetAmount(e) {
        const value = e.target.value
        let changeAmount = this.form.getFieldValue('changeAmount')-0
        let backAmount = (value - changeAmount).toFixed(2)-0
        this.$nextTick(() => {
          this.form.setFieldsValue({'backAmount':backAmount})
        });
      },
      // 初始化明细列：完整列定义交给 DataTable 组件，由列设置（tableKey + defaultColumns）控制显隐
      initColumns() {
        this.materialTable.columns = this.defColumns.map(c => Object.assign({}, c))
      }
    }
  }
</script>
<style scoped>
  /* 嵌入模式（页面内展示）时占满内容区 */
  .ro-inline-form {
    width: 100%;
  }
  .detail-sku-img {
    width: 36px;
    height: 36px;
    object-fit: cover;
    border-radius: 4px;
    vertical-align: middle;
  }
  /* 规格显示：统一为「规格名：规格值」单列左对齐，多行显示 */
  .detail-spec-display {
    white-space: pre-line;
    line-height: 18px;
    text-align: left;
    word-break: break-all;
  }
  .detail-sku-img-placeholder {
    font-size: 12px;
    color: #ccc;
  }
  .sign .ant-input{
    font-size: 30px;
    font-weight:bolder;
    text-align:center;
    border-left-width:0px!important;
    border-top-width:0px!important;
    border-right-width:0px!important;
  }

  /* 可编辑表格样式完全对齐 a-table size="middle" */
  #billModal /deep/ .input-table {
    font-size: 14px;
    border: 1px solid #e8e8e8;
    border-radius: 0;
  }
  /* 表头 */
  #billModal /deep/ .input-table .thead {
    overflow: hidden !important;
    border-bottom: 1px solid #e8e8e8;
    background: #fafafa;
  }
  #billModal /deep/ .input-table .thead .td {
    padding: 12px 8px !important;
    background: #fafafa;
    font-weight: 500;
    font-size: 14px;
    color: rgba(0,0,0,0.85);
    border-color: #e8e8e8;
    justify-content: flex-start !important;
    white-space: nowrap;
  }
  /* 表体 */
  #billModal /deep/ .input-table .tbody .tr {
    border-bottom: 1px solid #e8e8e8;
  }
  #billModal /deep/ .input-table .tbody .tr:last-child {
    border-bottom: none;
  }
  #billModal /deep/ .input-table .tbody .td {
    padding: 12px 8px !important;
    font-size: 14px;
    color: rgba(0,0,0,0.65);
    justify-content: flex-start !important;
    transition: background 0.3s;
  }
  /* 悬停行 */
  #billModal /deep/ .input-table .tbody .tr:hover {
    background: #e6f7ff !important;
  }
  /* 选中行 */
  #billModal /deep/ .input-table .tbody .tr.tr-checked {
    background: #fafafa !important;
  }
  /* 单元格内的输入组件对齐 a-table 正文样式 */
  #billModal /deep/ .input-table .tbody .td input,
  #billModal /deep/ .input-table .tbody .td .ant-select-selection,
  #billModal /deep/ .input-table .tbody .td .ant-input-number-input {
    font-size: 14px;
    color: rgba(0,0,0,0.65);
  }
  #billModal /deep/ .input-table .tbody .td .ant-input-number {
    width: 100%;
  }
  /* 复选框列居中 */
  #billModal /deep/ .input-table .thead .td.td-cb,
  #billModal /deep/ .input-table .tbody .td.td-cb {
    justify-content: center !important;
  }
</style>