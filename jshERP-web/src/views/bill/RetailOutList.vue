<template>
  <div>
    <!-- 手机端：从页面最上方开始加载当日销售单卡片 -->
    <div v-if="isMobile()" class="rol-mobile">
      <div v-if="mobileLoading" class="rol-mobile-tip">加载中...</div>
      <div v-else-if="mobileBills.length === 0" class="rol-mobile-tip">今日暂无销售单</div>
      <div v-for="bill in mobileBills" :key="bill.id" class="rol-card">
        <!-- 最上方：单号 + 日期左对齐，右侧多选 -->
        <div class="rol-card-head">
          <div class="rol-card-number-wrap">
            <span class="rol-card-label">单号</span>
            <span class="rol-card-number">{{ bill.number }}</span>
            <span class="rol-card-time">{{ bill.operTimeStr }}</span>
          </div>
          <a-checkbox
            class="rol-card-check"
            :checked="isMobileSelected(bill)"
            @change="e => toggleMobileSelect(bill, e.target.checked)"
          />
        </div>
        <!-- 中间：商品名称与规格同行，下方显示数量、零售价、实付价格 -->
        <div class="rol-card-items">
          <div v-for="(item, index) in (bill.items || [])" :key="item.id || index" class="rol-item">
            <div class="rol-item-title">
              <span class="rol-item-name">{{ item.name }}</span>
              <span v-if="itemSpec(item)" class="rol-item-spec">{{ itemSpec(item) }}</span>
            </div>
            <div class="rol-item-metrics">
              <div class="rol-item-metric">
                <span class="rol-item-label rol-label-qty">数量</span>
                <span class="rol-item-value">{{ item.operNumber }}</span>
              </div>
              <div class="rol-item-metric">
                <span class="rol-item-label rol-label-retail">零售价</span>
                <span class="rol-item-value">¥{{ item.unitPrice }}</span>
              </div>
              <div class="rol-item-metric">
                <span class="rol-item-label rol-label-paid">实付价</span>
                <span class="rol-item-value">¥{{ itemSettleAmount(bill, item) }}</span>
              </div>
            </div>
          </div>
          <div v-if="!bill.items || bill.items.length === 0" class="rol-item-empty">无商品明细</div>
        </div>
        <!-- 最下方：合计行（数量、零售价、实付价），会员信息与发货/审核按钮同一行 -->
        <div class="rol-card-foot">
          <div class="rol-totals">
            <div class="rol-item-metric">
              <span class="rol-item-label rol-label-qty">合计数量</span>
              <span class="rol-item-value">{{ billTotalQty(bill) }}</span>
            </div>
            <div class="rol-item-metric">
              <span class="rol-item-label rol-label-retail">零售价</span>
              <span class="rol-item-value">¥{{ billRetailAmount(bill) }}</span>
            </div>
            <div class="rol-item-metric">
              <span class="rol-item-label rol-label-paid">实付价</span>
              <span class="rol-item-value">¥{{ billPaidAmount(bill) }}</span>
            </div>
          </div>
          <div class="rol-member-row">
            <span class="rol-member">{{ billMemberText(bill) }}</span>
            <div class="rol-actions">
              <a-button size="small" @click="mobileDeliver(bill)">发货</a-button>
              <a-button v-if="bill.status === '0'" size="small" type="primary" @click="mobileSetStatus(bill, 1)">审核</a-button>
              <a-button v-else size="small" @click="mobileSetStatus(bill, 0)">反审核</a-button>
            </div>
          </div>
        </div>
      </div>
    </div>
    <a-row v-else :gutter="24">
    <a-col :md="24">
      <a-card :style="cardStyle" :bordered="false">
        <!-- 查询区域 -->
        <div class="table-page-search-wrapper">
          <!-- 搜索区域 -->
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
                  <a @click="handleToggleSearch" style="margin-left: 8px">
                    {{ toggleSearchStatus ? '收起' : '展开' }}
                    <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
                  </a>
                </a-col>
              </span>
            </a-row>
            <template v-if="toggleSearchStatus">
              <a-row :gutter="24">
                <a-col :md="6" :sm="24">
                  <a-form-item label="会员卡号" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择会员卡号" showSearch allow-clear optionFilterProp="children" v-model="queryParam.organId" @search="handleSearchRetail">
                      <div slot="dropdownRender" slot-scope="menu">
                        <v-nodes :vnodes="menu" />
                        <a-divider style="margin: 4px 0;" />
                        <div class="dropdown-btn" @mousedown="e => e.preventDefault()" @click="initRetail(0)"><a-icon type="reload" /> 刷新列表</div>
                      </div>
                      <a-select-option v-for="(item,index) in retailList" :key="index" :value="item.id">
                        {{ item.supplier }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="仓库名称" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择仓库" showSearch allow-clear optionFilterProp="children" v-model="queryParam.depotId">
                      <a-select-option v-for="(depot,index) in depotList" :value="depot.id" :key="index">
                        {{ depot.depotName }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="操作员" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择操作员" showSearch allow-clear optionFilterProp="children" v-model="queryParam.creator">
                      <a-select-option v-for="(item,index) in userList" :key="index" :value="item.id">
                        {{ item.userName }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="结算账户" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择结算账户" showSearch allow-clear optionFilterProp="children" v-model="queryParam.accountId">
                      <a-select-option v-for="(item,index) in accountList" :key="index" :value="item.id">
                        {{ item.name }}
                      </a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="单据状态" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择单据状态" allow-clear v-model="queryParam.status">
                      <a-select-option value="0">未审核</a-select-option>
                      <a-select-option value="9" v-if="!checkFlag">审核中</a-select-option>
                      <a-select-option value="1">已审核</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="单据备注" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入单据备注" v-model="queryParam.remark"></a-input>
                  </a-form-item>
                </a-col>
              </a-row>
            </template>
          </a-form>
        </div>
        <!-- 操作按钮区域 -->
        <div class="table-operator"  style="margin-top: 5px">
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="myHandleAdd" type="primary" icon="plus">新增</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" icon="delete" @click="batchDel">删除</a-button>
          <a-button v-if="quickBtn.retailBack.indexOf(1)>-1 && btnEnableList.indexOf(1)>-1" icon="share-alt" @click="transferBill('转零售退货', quickBtn.retailBack)">转零售退货</a-button>
          <a-button v-if="checkFlag && btnEnableList.indexOf(2)>-1" icon="check" @click="batchSetStatus(1)">审核</a-button>
          <a-button v-if="checkFlag && btnEnableList.indexOf(7)>-1" icon="stop" @click="batchSetStatus(0)">反审核</a-button>
          <a-button v-if="isShowExcel && btnEnableList.indexOf(3)>-1" icon="download" @click="handleExport">导出</a-button>
          <a-tooltip placement="left" title="用于非会员和会员的单据录入，主要是用于散户使用，不能欠款。" slot="action">
            <a-icon v-if="btnEnableList.indexOf(1)>-1" type="question-circle" style="font-size:20px;float:right;" />
          </a-tooltip>
        </div>
        <!-- table区域-begin -->
        <div>
          <data-table
            ref="table"
            bordered
            draggable
            row-key="id"
            table-key="bill_retail_out"
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
  </div>
</template>
<!-- create ji sheng hua-->
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
  import { getAction, postAction } from '@/api/manage'
  import { getMpListShort } from '@/utils/util'
  export default {
    name: "RetailOutList",
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
        // 查询条件
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
        urlPath: '/bill/retail_out',
        // 手机端：当日销售单卡片数据
        mobileBills: [],
        mobileLoading: false,
        // 手机端卡片多选
        mobileSelectedIds: [],
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
    mounted () {
      // 手机端：进入页面即从最上方加载当日销售单卡片
      if (this.isMobile()) {
        this.loadTodayBills()
      }
    },
    methods: {
      // 主表汇总行改由 DataTable 的 summary 计算属性处理，覆盖 mixin 的空置实现
      tableAddTotalRow() {
      },
      // ===== 手机端：当日销售单卡片 =====
      // 加载当日零售出库单，并逐单补全商品明细
      loadTodayBills () {
        this.mobileLoading = true
        const day = moment().format('YYYY-MM-DD')
        getAction('/depotHead/list', {
          currentPage: 1,
          pageSize: 100,
          search: JSON.stringify({
            type: '出库',
            subType: '零售',
            beginTime: day + ' 00:00:00',
            endTime: day + ' 23:59:59'
          })
        }).then(res => {
          const rows = (res && res.code === 200 && res.data && res.data.rows) ? res.data.rows : []
          return Promise.all(rows.map(row => this.loadBillItems(row))).then(() => {
            this.mobileBills = rows
            this.mobileLoading = false
          })
        }).catch(() => {
          this.mobileBills = []
          this.mobileLoading = false
        })
      },
      // 单据商品明细
      loadBillItems (row) {
        return getAction('/depotItem/getDetailList', {
          headerId: row.id,
          mpList: getMpListShort(Vue.ls.get('materialPropertyList')),
          linkType: 'basic',
          isReadOnly: '0'
        }).then(res => {
          row.items = (res && res.code === 200 && res.data && res.data.rows) ? res.data.rows : []
        }).catch(() => {
          row.items = []
        })
      },
      // 商品规格：仅显示规格值，多个值用 / 分隔（如 绿/220*240）
      itemSpec (item) {
        if (!item) return ''
        let specValues = item.sku || item.specValues || ''
        try {
          const arr = typeof specValues === 'string' ? JSON.parse(specValues) : specValues
          if (Array.isArray(arr) && arr.length > 0) {
            const values = []
            arr.forEach(spec => {
              const childNames = spec.childNames || []
              const subValues = spec.subValues || {}
              if (childNames.length > 0) {
                childNames.forEach(childName => {
                  const value = subValues[childName]
                  if (value != null && String(value).trim() !== '') {
                    values.push(String(value).trim())
                  }
                })
              } else {
                const value = spec.valueText || spec.value || ''
                if (value !== '') values.push(String(value).trim())
              }
            })
            if (values.length > 0) return values.join('/')
          }
        } catch (e) { /* 非 JSON 规格值时走兜底 */ }
        return [item.standard, item.model].filter(Boolean).join('/')
      },
      // 每行实付价格：按实收占单据金额的比例分摊
      itemSettleAmount (bill, item) {
        const total = Number(bill.totalPrice) || 0
        const paid = bill.changeAmount == null ? total : Number(bill.changeAmount)
        const amount = Number(item.allPrice) || 0
        if (total <= 0) return amount.toFixed(2)
        return (amount * paid / total).toFixed(2)
      },
      // 单据合计数量
      billTotalQty (bill) {
        return (bill.items || []).reduce((sum, item) => sum + (Number(item.operNumber) || 0), 0)
      },
      // 单据零售价合计（零售价 × 数量的合计）
      billRetailAmount (bill) {
        const total = (bill.items || []).reduce((sum, item) => sum + (Number(item.allPrice) || 0), 0)
        return total.toFixed(2)
      },
      // 单据实付价合计
      billPaidAmount (bill) {
        const total = Number(this.billRetailAmount(bill)) || 0
        return (bill.changeAmount == null ? total : Number(bill.changeAmount)).toFixed(2)
      },
      // 单据会员
      billMember (bill) {
        if (!bill || !bill.organId || !Array.isArray(this.retailList)) return {}
        return this.retailList.find(m => String(m.id) === String(bill.organId)) || {}
      },
      // 会员信息文本：有会员显示会员名/电话，无会员直接显示散客
      billMemberText (bill) {
        const member = this.billMember(bill)
        const name = member.contacts || ''
        if (!name) return '散客'
        return member.telephone ? name + ' ' + member.telephone : name
      },
      // 卡片多选
      isMobileSelected (bill) {
        return this.mobileSelectedIds.some(id => String(id) === String(bill.id))
      },
      toggleMobileSelect (bill, checked) {
        const ids = this.mobileSelectedIds.filter(id => String(id) !== String(bill.id))
        if (checked) ids.push(bill.id)
        this.mobileSelectedIds = ids
      },
      // 发货：打开发货单页面（按发货模板渲染）
      mobileDeliver (bill) {
        this.$router.push({ path: '/bill/deliveryNote', query: { number: bill.number } })
      },
      // 审核 / 反审核
      mobileSetStatus (bill, status) {
        const that = this
        this.$confirm({
          title: '确认操作',
          content: status === 1 ? '是否审核该单据?' : '是否反审核该单据?',
          onOk () {
            return postAction(that.url.batchSetStatusUrl, { status: status, ids: bill.id + ',' }).then(res => {
              if (res && res.code === 200) {
                that.$message.success(status === 1 ? '审核成功' : '反审核成功')
                that.mobileSelectedIds = []
                that.loadTodayBills()
                that.loadData()
              } else if (res && res.data) {
                that.$message.warning(res.data.message || res.data)
              }
            })
          }
        })
      }
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less'

  /* 手机端：当日销售单卡片 */
  .rol-mobile {
    min-height: 100vh;
    padding: 10px 12px 16px;
    background: #f5f5f5;
    box-sizing: border-box;
  }
  .rol-mobile-tip {
    padding: 30px 0;
    text-align: center;
    font-size: 13px;
    color: #999;
  }
  .rol-card {
    margin-bottom: 10px;
    padding: 12px;
    background: #fff;
    border-radius: 10px;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  }
  .rol-card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 8px;
    border-bottom: 1px solid #f0f0f0;
  }
  .rol-card-number-wrap {
    display: flex;
    align-items: baseline;
    min-width: 0;
  }
  .rol-card-label {
    flex-shrink: 0;
    margin-right: 6px;
    font-size: 12px;
    color: #999;
  }
  .rol-card-number {
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }
  .rol-card-time {
    flex-shrink: 0;
    margin-left: 8px;
    font-size: 12px;
    color: #999;
  }
  .rol-card-check {
    flex-shrink: 0;
    margin-left: 8px;
  }
  .rol-item {
    padding: 8px 0;
    border-bottom: 1px dashed #f0f0f0;
  }
  .rol-item:last-child {
    border-bottom: none;
  }
  .rol-item-title {
    display: flex;
    align-items: baseline;
    min-width: 0;
    overflow-x: auto;
    overflow-y: hidden;
    -webkit-overflow-scrolling: touch;
  }
  .rol-item-title::-webkit-scrollbar {
    display: none;
  }
  .rol-item-name {
    flex: 0 0 auto;
    white-space: nowrap;
    font-size: 14px;
    font-weight: 500;
    color: #333;
  }
  .rol-item-spec {
    flex: 0 0 auto;
    margin-left: 6px;
    white-space: nowrap;
    font-size: 12px;
    color: #999;
  }
  .rol-item-metrics {
    display: flex;
    align-items: baseline;
    margin-top: 4px;
    min-width: 0;
  }
  .rol-item-metric {
    display: flex;
    align-items: baseline;
    min-width: 0;
  }
  .rol-item-metric:nth-child(1) {
    flex: 0 0 66px;
  }
  .rol-item-metric:nth-child(2) {
    flex: 0 0 104px;
    margin-left: 12px;
  }
  .rol-item-metric:nth-child(3) {
    flex: 1 1 auto;
    margin-left: 12px;
  }
  .rol-item-label {
    display: inline-block;
    flex-shrink: 0;
    margin-right: 4px;
    text-align: right;
    font-size: 11px;
    color: #999;
  }
  .rol-label-qty {
    min-width: 4.2em;
  }
  .rol-label-retail {
    min-width: 3.2em;
  }
  .rol-label-paid {
    min-width: 4.2em;
  }
  .rol-item-value {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    text-align: right;
    font-size: 14px;
    font-weight: 600;
    color: #333;
  }
  .rol-item-empty {
    padding: 8px 0;
    text-align: center;
    font-size: 12px;
    color: #999;
  }
  .rol-card-foot {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px solid #f0f0f0;
  }
  .rol-member-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: 6px;
  }
  .rol-member {
    flex: 1;
    min-width: 0;
    white-space: normal;
    word-break: break-all;
    font-size: 13px;
    color: #333;
  }
  .rol-totals {
    display: flex;
    align-items: baseline;
    flex-wrap: wrap;
  }
  .rol-actions {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    margin-left: 8px;
  }
  .rol-actions .ant-btn + .ant-btn {
    margin-left: 8px;
  }
</style>
