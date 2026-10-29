<template>
  <a-row :gutter="24">
    <a-col :md="24">
      <a-card :style="cardStyle" :bordered="false">
        <!-- 操作按钮区域：聚焦开单 -->
        <div class="table-operator" style="margin-top: 5px">
          <a-button type="primary" icon="plus" size="large" @click="openSaleOrder">销售开单</a-button>
          <a-button icon="reload" @click="loadData(1)">刷新</a-button>
          <a-button v-if="quickBtn.retailBack.indexOf(1)>-1 && btnEnableList.indexOf(1)>-1" icon="share-alt" @click="transferBill('转零售退货', quickBtn.retailBack)">转零售退货</a-button>
          <a-button v-if="checkFlag && btnEnableList.indexOf(2)>-1" icon="check" @click="batchSetStatus(1)">审核</a-button>
          <a-button v-if="checkFlag && btnEnableList.indexOf(7)>-1" icon="stop" @click="batchSetStatus(0)">反审核</a-button>
          <a-button v-if="isShowExcel && btnEnableList.indexOf(3)>-1" icon="download" @click="handleExport">导出</a-button>
          <a-tooltip placement="left" title="用于零售前台快速开单，录入商品后保存即可生成零售出库单。" slot="action">
            <a-icon type="question-circle" style="font-size:20px;float:right;" />
          </a-tooltip>
        </div>
        <!-- 查询区域 -->
        <div class="table-page-search-wrapper" style="margin-top: 10px">
          <a-form layout="inline" @keyup.enter.native="searchQuery">
            <a-row :gutter="24">
              <a-col :md="6" :sm="24">
                <a-form-item label="单据编号" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入单据编号" v-model="queryParam.number"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="商品信息" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入条码、名称、助记码、规格、型号等信息" v-model="queryParam.materialParam"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="单据日期" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-range-picker
                    style="width:100%"
                    v-model="queryParam.createTimeRange"
                    format="YYYY-MM-DD"
                    :placeholder="['开始时间', '结束时间']"
                    @change="onDateChange"
                    @ok="onDateOk"
                  />
                </a-form-item>
              </a-col>
              <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
                <a-col :md="6" :sm="24">
                  <a-button type="primary" @click="searchQuery">查询</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset">重置</a-button>
                </a-col>
              </span>
            </a-row>
          </a-form>
        </div>
        <!-- table区域-begin -->
        <div style="margin-top: 10px">
          <data-table
            ref="table"
            bordered
            draggable
            row-key="id"
            table-key="bill_retail_sale_order"
            :extra-columns="defColumns"
            :default-columns="defDataIndex"
            storage-key="columns_LSCK"
            :summary="summaryEnabled"
            summary-fields="materialCount,totalPrice"
            :data-source="dataSource"
            :pagination="ipagination"
            :scroll="scroll"
            :loading="loading"
            :row-selection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
            :expanded-row-keys="expandedRowKeys"
            @expand="onExpand"
            @change="handleTableChange">
            <span slot="action" slot-scope="text, record">
              <a @click="myHandleDetail(record, '零售出库', prefixNo)">查看</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a v-if="btnEnableList.indexOf(1)>-1" @click="myHandleEdit(record)">编辑</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a v-if="btnEnableList.indexOf(1)>-1" @click="myHandleCopyAdd(record)">复制</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a-popconfirm v-if="btnEnableList.indexOf(1)>-1" title="确定删除吗?" @confirm="() => myHandleDelete(record)">
                <a>删除</a>
              </a-popconfirm>
            </span>
            <template slot="customRenderStatus" slot-scope="status">
              <a-tag v-if="status == '0'" color="red">未审核</a-tag>
              <a-tag v-if="status == '1'" color="green">已审核</a-tag>
              <a-tag v-if="status == '9'" color="orange">审核中</a-tag>
            </template>
            <template slot="expandedRowRender" slot-scope="record">
              <data-table
                size="small"
                bordered
                :show-column-setting="false"
                :extra-columns="detailColumns"
                :data-source="record.childrens"
                :loading="record.loading"
                row-key="id"
                :pagination="false">
              </data-table>
            </template>
          </data-table>
        </div>
        <!-- table区域-end -->
        <!-- 表单区域 -->
        <retail-out-modal ref="modalForm" @ok="modalFormOk" @close="modalFormClose"></retail-out-modal>
        <retail-back-modal ref="transferModalForm" @ok="modalFormOk" @close="modalFormClose"></retail-back-modal>
        <bill-detail ref="modalDetail" @ok="modalFormOk" @close="modalFormClose"></bill-detail>
        <bill-excel-iframe ref="billExcelIframe" @ok="modalFormOk" @close="modalFormClose"></bill-excel-iframe>
      </a-card>
    </a-col>
  </a-row>
</template>
<script>
  import RetailOutModal from './modules/RetailOutModal'
  import RetailBackModal from './modules/RetailBackModal'
  import BillDetail from './dialog/BillDetail'
  import BillExcelIframe from '@/components/tools/BillExcelIframe'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { BillListMixin } from './mixins/BillListMixin'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import JDate from '@/components/jeecg/JDate'
  import DataTable from '@/components/DataTable'
  import Vue from 'vue'
  import moment from 'moment'
  import { getFormatDate } from '@/utils/util'
  export default {
    name: "RetailSaleOrderList",
    mixins:[JeecgListMixin,BillListMixin],
    components: {
      RetailOutModal,
      RetailBackModal,
      BillDetail,
      BillExcelIframe,
      JEllipsis,
      JDate,
      DataTable,
      VNodes: {
        functional: true,
        render: (h, ctx) => ctx.props.vnodes,
      }
    },
    data () {
      return {
        // 查询条件 - 默认只查当天单据，聚焦开单
        queryParam: {
          number: "",
          materialParam: "",
          type: "出库",
          subType: "零售",
          organId: undefined,
          depotId: undefined,
          creator: undefined,
          accountId: undefined,
          status: undefined,
          remark: ""
        },
        prefixNo: 'LSCK',
        urlPath: '/bill/retail_sale_order',
        labelCol: {
          span: 5
        },
        wrapperCol: {
          span: 18,
          offset: 1
        },
        // 默认索引
        defDataIndex:['action','organName','number','materialsList','operTimeStr','userName','materialCount','totalPrice','getAmount',
          'backAmount','status'],
        // 默认列
        defColumns: [
            {
              title: '操作',
            dataIndex: 'action',
            align:"center", width: 180,
            fixed: 'right', scopedSlots: { customRender: 'action' },
          },
          { title: '会员', dataIndex: 'organName',width:120, ellipsis:true},
          { title: '单据编号', dataIndex: 'number',width:160,
            customRender:function (text,record,index) {
              text = record.hasBackFlag?text+"[退]":text
              return text
            }
          },
          { title: '商品信息', dataIndex: 'materialsList',width:220, ellipsis:true},
          { title: '单据日期', dataIndex: 'operTimeStr',width:145},
          { title: '收款类型', dataIndex: 'payType',width:80},
          { title: '操作员', dataIndex: 'userName',width:80, ellipsis:true},
          { title: '数量', dataIndex: 'materialCount',width:60},
          { title: '金额合计', dataIndex: 'totalPrice',width:80},
          { title: '收款金额', dataIndex: 'getAmount',width:80,
            customRender:function (text,record,index) {
              if(record.backAmount) {
                return record.changeAmount + record.backAmount
              } else {
                return record.changeAmount
              }
            }
          },
          { title: '找零', dataIndex: 'backAmount',width:50},
          { title: '收款账户', dataIndex: 'accountName',width:80},
          { title: '备注', dataIndex: 'remark',width:200},
          { title: '状态', dataIndex: 'status', width: 80, align: "center",
            scopedSlots: { customRender: 'customRenderStatus' }
},
        ],
        url: {
          list: "/depotHead/list",
          delete: "/depotHead/delete",
          deleteBatch: "/depotHead/deleteBatch",
          batchSetStatusUrl: "/depotHead/batchSetStatus"
        }
      }
    },
    computed: {
      summaryEnabled() {
        const pageSize = this.ipagination && this.ipagination.pageSize
        return this.dataSource && this.dataSource.length > 0 && pageSize % 10 === 1
      }
    },
    created () {
      this.initSystemConfig()
      this.initRetail()
      this.getDepotData()
      this.initUser()
      this.initAccount()
      this.initQuickBtn()
      this.getDepotByCurrentUser()
    },
    mounted() {
      // 销售开单页面默认聚焦当天单据
      const today = getFormatDate()
      this.queryParam.beginTime = today
      this.queryParam.endTime = today
      this.queryParam.createTimeRange = [moment(today), moment(today)]
    },
    methods: {
      // 打开销售开单弹窗，复用零售出库的开单逻辑
      openSaleOrder() {
        this.$refs.modalForm.action = "add"
        if(this.btnEnableList.indexOf(2)===-1) {
          this.$refs.modalForm.isCanCheck = false
        }
        this.$refs.modalForm.add()
        this.$refs.modalForm.title = "销售开单"
        this.$refs.modalForm.disableSubmit = false
      },
      // 主表汇总行改由 DataTable 的 summary 计算属性处理，覆盖 mixin 的空置实现
      tableAddTotalRow() {
      }
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less'
</style>
