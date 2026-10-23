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
    switchHelp
    switchFullscreen
    @cancel="handleCancel"
    :id="prefixNo"
    style="top:20px;height: 95%;">
    <template slot="footer">
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
        <a-row class="form-row" :gutter="24">
          <a-col :lg="6" :md="12" :sm="24">
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
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单据日期">
              <j-date v-decorator="['operTime', validatorRules.operTime]" :show-time="true"/>
            </a-form-item>
          </a-col>
          <a-col :lg="6" :md="12" :sm="24">
            <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="单据编号" data-step="2" data-title="单据编号"
                         data-intro="单据编号自动生成、自动累加、开头是单据类型的首字母缩写，累加的规则是每次打开页面会自动占用一个新的编号">
              <a-input placeholder="请输入单据编号" v-decorator.trim="[ 'number', validatorRules.number ]" />
            </a-form-item>
          </a-col>
          <a-col :lg="6" :md="12" :sm="24">
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
        <a-row class="form-row" :gutter="24">
          <a-col :lg="18" :md="12" :sm="24">
            <data-table
              ref="materialDataTable"
              :loading="materialTable.loading"
              :data-source="materialTable.dataSource"
              :extra-columns="detailColumns"
              table-key="bill_retail_out_detail"
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
                <a-button type="primary" icon="plus" @click="openSelectMaterial">新增商品</a-button>
                <a-button @click="handleBatchSetDepot">批量设置仓库</a-button>
                <a-row :gutter="24" style="float:left;" data-step="4" data-title="扫码录入" data-intro="此功能支持扫码枪扫描商品条码进行录入">
                  <a-col v-if="scanStatus" :md="6" :sm="24">
                    <a-button @click="scanEnter">扫码录入</a-button>
                  </a-col>
                  <a-col v-if="!scanStatus" :md="16" :sm="24" style="padding: 0 6px 0 12px">
                    <a-input placeholder="请扫条码或序列号并回车" v-model="scanBarCode" @pressEnter="scanPressEnter" ref="scanBarCode"/>
                  </a-col>
                  <a-col v-if="!scanStatus" :md="6" :sm="24" style="padding: 0px 18px 0 0">
                    <a-button @click="stopScan">收起扫码</a-button>
                  </a-col>
                </a-row>
              </template>
              <template slot="barCode" slot-scope="text, record">
                <j-select-list kind="material" :value="record.barCode" :rows="getPopupRows(record)" :multi="true" @change="v => onDetailPopupChange('barCode', v, record)" />
              </template>
              <template slot="imgName" slot-scope="text, record">
                <img v-if="record.imgName" :src="getDetailImgUrl(record.imgName)" class="detail-sku-img" />
                <span v-else class="detail-sku-img-placeholder">无图</span>
              </template>
              <template slot="specValues" slot-scope="text, record">
                {{ parseSpecValues(record.specValues) }}
              </template>
              <template slot="snList" slot-scope="text, record">
                <j-select-list kind="sn" :value="record.snList" :rows="getPopupRows(record)" @change="v => onDetailPopupChange('snList', v, record)" />
              </template>
              <template slot="batchNumber" slot-scope="text, record">
                <j-select-list kind="batch" :value="record.batchNumber" :rows="getPopupRows(record)" @change="v => onDetailPopupChange('batchNumber', v, record)" />
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
    <batch-set-depot ref="batchSetDepotModalForm" @ok="batchSetDepotModalFormOk"></batch-set-depot>
    <j-select-material-modal ref="selectMaterialModal" :multi="true" @ok="onSelectMaterialOk"></j-select-material-modal>
    <workflow-iframe ref="modalWorkflow" @ok="workflowModalFormOk"></workflow-iframe>
    <bill-print-iframe ref="modalPrint"></bill-print-iframe>
    <bill-print-pro-iframe ref="modalPrintPro"></bill-print-pro-iframe>
  </j-modal>
</template>
<script>
  import pick from 'lodash.pick'
  import MemberModal from '../../system/modules/MemberModal'
  import DepotModal from '../../system/modules/DepotModal'
  import AccountModal from '../../system/modules/AccountModal'
  import BatchSetDepot from '../dialog/BatchSetDepot'
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
  import DataTable from '@/components/DataTable'
  import JSelectList from '@/components/jeecgbiz/JSelectList'
  import JSelectMaterialModal from '@/components/jeecgbiz/modal/JSelectMaterialModal'
  import Vue from 'vue'
  export default {
    name: "RetailOutModal",
    mixins: [JEditableTableMixin, BillModalMixin, DataTableDetailMixin],
    components: {
      MemberModal,
      DepotModal,
      AccountModal,
      BatchSetDepot,
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
        // 列设置：商品信息 + SKU信息 + 单据明细字段
        defColumns: [
          { title: '仓库名称', key: 'depotId', width: '8%', type: FormTypes.select, placeholder: '请选择${title}', options: [],
            allowSearch:true, validateRules: [{ required: true, message: '${title}不能为空' }]
          },
          { title: '条码', key: 'barCode', width: '12%', type: FormTypes.popupJsh, kind: 'material', multi: true,
            validateRules: [{ required: true, message: '${title}不能为空' }]
          },
          { title: '名称', key: 'name', width: '10%', type: FormTypes.normal },
          { title: '商品编码', key: 'code', width: '8%', type: FormTypes.normal },
          { title: '图片', key: 'imgName', width: '5%', type: FormTypes.normal },
          { title: 'SKU编码', key: 'skuCode', width: '9%', type: FormTypes.normal },
          { title: '规格值', key: 'specValues', width: '12%', type: FormTypes.normal },
          { title: '规格', key: 'standard', width: '8%', type: FormTypes.normal },
          { title: '型号', key: 'model', width: '6%', type: FormTypes.normal },
          { title: '颜色', key: 'color', width: '5%', type: FormTypes.normal },
          { title: '单位', key: 'unit', width: '5%', type: FormTypes.normal },
          { title: '品牌', key: 'brand', width: '5%', type: FormTypes.normal },
          { title: '助记码', key: 'mnemonic', width: '6%', type: FormTypes.normal },
          { title: '类别', key: 'categoryName', width: '6%', type: FormTypes.normal },
          { title: '基础重量', key: 'weight', width: '6%', type: FormTypes.normal },
          { title: '制造商', key: 'mfrs', width: '8%', type: FormTypes.normal },
          { title: '面料', key: 'otherField1', width: '5%', type: FormTypes.normal },
          { title: '安全类别', key: 'otherField2', width: '5%', type: FormTypes.normal },
          { title: '执行标准', key: 'otherField3', width: '5%', type: FormTypes.normal },
          { title: '商品ID', key: 'mId', width: '5%', type: FormTypes.normal },
          { title: '扩展4', key: 'otherField4', width: '4%', type: FormTypes.normal },
          { title: '扩展5', key: 'otherField5', width: '4%', type: FormTypes.normal },
          { title: '扩展6', key: 'otherField6', width: '4%', type: FormTypes.normal },
          { title: '扩展7', key: 'otherField7', width: '4%', type: FormTypes.normal },
          { title: '库存', key: 'stock', width: '5%', type: FormTypes.normal },
          { title: '采购价', key: 'purchaseDecimal', width: '5%', type: FormTypes.normal },
          { title: '零售价', key: 'commodityDecimal', width: '5%', type: FormTypes.normal },
          { title: '销售价', key: 'wholesaleDecimal', width: '5%', type: FormTypes.normal },
          { title: '最低售价', key: 'lowDecimal', width: '5%', type: FormTypes.normal },
          { title: 'SKU单位', key: 'commodityUnit', width: '5%', type: FormTypes.normal },
          { title: 'SKU_ID', key: 'meId', width: '5%', type: FormTypes.normal },
          { title: '序列号', key: 'snList', width: '12%', type: FormTypes.popupJsh, kind: 'sn', multi: true },
          { title: '批号', key: 'batchNumber', width: '8%', type: FormTypes.popupJsh, kind: 'batch', multi: false },
          { title: '有效期', key: 'expirationDate', width: '9%', type: FormTypes.input, readonly: true },
          { title: '数量', key: 'operNumber', width: '6%', type: FormTypes.inputNumber, statistics: true,
            validateRules: [{ required: true, message: '${title}不能为空' }]
          },
          { title: '单价', key: 'unitPrice', width: '6%', type: FormTypes.inputNumber },
          { title: '金额', key: 'allPrice', width: '6%', type: FormTypes.inputNumber, statistics: true },
          { title: '备注', key: 'remark', width: '7%', type: FormTypes.input }
        ],
        // 默认显示：商品信息 + SKU信息 + 数量金额
        defDataIndex: ['depotId','barCode','name','code','imgName','skuCode','specValues','unit','brand','mnemonic','categoryName','weight','mfrs',
          'otherField1','otherField2','otherField3','mId','stock','purchaseDecimal','commodityDecimal','wholesaleDecimal','lowDecimal',
          'operNumber','unitPrice','allPrice','remark','snList','batchNumber','expirationDate'],
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
      let realScreenWidth = window.screen.width
      this.minWidth = realScreenWidth<1500?1800:2200
    },
    methods: {
      //调用完edit()方法之后会自动调用此方法
      editAfter() {
        this.billStatus = '0'
        this.currentSelectDepotId = ''
        this.changeColumnHide()
        this.changeFormTypes(this.materialTable.columns, 'snList', 0)
        this.changeFormTypes(this.materialTable.columns, 'batchNumber', 0)
        this.changeFormTypes(this.materialTable.columns, 'expirationDate', 0)
        if (this.action === 'add') {
          this.addInit(this.prefixNo)
          this.fileList = []
          this.$nextTick(() => {
            handleIntroJs(this.prefixNo, 1)
          })
          this.$nextTick(() => {
            this.form.setFieldsValue({'payType': '现付', 'getAmount':0, 'backAmount':0})
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
      //改变实收金额、收款金额的值
      autoChangePrice(target) {
        let allLastMoney = target.statisticsColumns.allPrice
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
      // 明细行SKU图片地址
      getDetailImgUrl(imgUrl) {
        if (!imgUrl) return ''
        if (imgUrl.indexOf('http') === 0) return imgUrl
        const base = (window._CONFIG && window._CONFIG['domianURL']) ? window._CONFIG['domianURL'] : ''
        if (imgUrl.indexOf('systemConfig/static/') === -1) {
          return base + '/systemConfig/static/' + imgUrl
        }
        return base + '/' + imgUrl
      },
      // 规格值JSON转为“颜色:蓝色 / 规格:200*230”
      parseSpecValues(specValues) {
        if (!specValues) return ''
        try {
          const arr = JSON.parse(specValues)
          if (!Array.isArray(arr)) return specValues
          return arr.map(item => item.attributeName + ':' + item.valueText).join(' / ')
        } catch (e) {
          return specValues
        }
      },
      // 初始化明细列：完整列定义交给 DataTable 组件，由列设置（tableKey + defaultColumns）控制显隐
      initColumns() {
        this.materialTable.columns = this.defColumns.map(c => Object.assign({}, c))
      }
    }
  }
</script>
<style scoped>
  .detail-sku-img {
    width: 36px;
    height: 36px;
    object-fit: cover;
    border-radius: 4px;
    vertical-align: middle;
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