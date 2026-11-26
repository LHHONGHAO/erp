<template>
  <div>
    <!-- ==================== 手机端：标准信息搜索页面（内容卡片保持原样） ==================== -->
    <mobile-search-page
      v-if="isNarrowScreen"
      v-model="mobileKeyword"
      title="零售出库"
      placeholder="搜索单号 / 会员 / 商品"
      :filter-visible.sync="filterDrawerVisible"
      :filter-groups="mobileFilterGroups"
      scroll-body
      @back="$router.back()"
      @search="mobileSearch"
      @filter-change="onMobileFilterChange"
      @filter-clear="resetMobileFilter">
      <template #actions>
        <mobile-header-actions :actions="mobileHeaderActions" />
      </template>

      <div v-if="mobileLoading" class="rol-mobile-tip">加载中...</div>
      <div v-else-if="mobileBillList.length === 0" class="rol-mobile-tip">今日暂无销售单</div>
      <div v-for="bill in mobileBillList" :key="bill.id" class="rol-card" :class="{ 'is-expanded': isMobileCardExpanded(bill) }" @click="toggleMobileCard(bill)">
        <!-- 上方：单号 + 日期，右侧多选 -->
        <div class="rol-card-head">
          <div class="rol-card-number-wrap">
            <span class="rol-card-label">单号</span>
            <span class="rol-card-number">{{ bill.number }}</span>
            <span class="rol-card-time">{{ bill.operTimeStr }}</span>
          </div>
          <a-checkbox
            class="rol-card-check"
            :checked="isMobileSelected(bill)"
            @click.native.stop
            @change="e => toggleMobileSelect(bill, e.target.checked)"
          />
        </div>
        <!-- 点击卡片展开时再加载商品明细：与销售开单页一致的商品卡片 -->
        <div v-show="isMobileCardExpanded(bill)" class="rol-card-items" @click.stop>
          <div v-if="bill.itemsLoading" class="rol-item-empty">加载中...</div>
          <div v-for="(item, index) in (bill.items || [])" :key="item.id || index" class="rol-goods-card">
            <div class="rol-goods-thumb">
              <img v-if="itemImg(item)" :src="itemImg(item)" alt="" />
              <a-icon v-else type="picture" class="rol-goods-thumb-icon" />
            </div>
            <div class="rol-goods-main">
              <div class="rol-goods-name">{{ item.name }}</div>
              <div v-if="itemSpec(item)" class="rol-goods-spec">{{ itemSpec(item) }}</div>
              <div class="rol-goods-foot">
                <span class="rol-goods-kv">
                  <span class="rol-goods-kv-label">零售价</span>
                  <span class="rol-goods-kv-value">¥{{ item.unitPrice }}</span>
                </span>
                <span class="rol-goods-kv">
                  <span class="rol-goods-kv-label">数量</span>
                  <span class="rol-goods-kv-value">{{ item.operNumber }}</span>
                </span>
                <span class="rol-goods-amount">¥{{ itemSettleAmount(bill, item) }}</span>
              </div>
            </div>
          </div>
          <div v-if="!bill.itemsLoading && bill.items && bill.items.length === 0" class="rol-item-empty">无商品明细</div>
        </div>
        <!-- 汇总：零售价、数量、折扣、结算金额（展开时位于商品卡片下方） -->
        <div class="rol-card-summary">
          <div class="rol-summary-item">
            <span class="rol-item-label rol-label-retail">零售价</span>
            <span class="rol-item-value">¥{{ billRetailAmount(bill) }}</span>
          </div>
          <div class="rol-summary-item rol-summary-qty">
            <span class="rol-item-label rol-label-qty">数量</span>
            <span class="rol-item-value">{{ billTotalQty(bill) }}</span>
          </div>
          <div class="rol-summary-item rol-summary-discount">
            <span class="rol-item-label rol-label-discount">折扣</span>
            <span class="rol-item-value">{{ billDiscount(bill) }}</span>
          </div>
          <div class="rol-summary-item rol-summary-settle">
            <span class="rol-item-label rol-label-settle">结算金额</span>
            <span class="rol-item-value">¥{{ billPaidAmount(bill) }}</span>
          </div>
        </div>
        <!-- 下面：会员信息与操作按钮 -->
        <div class="rol-card-foot" @click.stop>
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
    </mobile-search-page>
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
    <!-- 发货单打印弹层：手机端点「发货」直接打开，不再跳转发货单页面 -->
    <label-print-modal ref="deliveryPrintModal" />
  </div>
</template>
<!-- create ji sheng hua-->
<script>
  import RetailOutModal from './modules/RetailOutModal'
  import RetailBackModal from './modules/RetailBackModal'
  import BillDetail from './dialog/BillDetail'
  import BillExcelIframe from '@/components/tools/BillExcelIframe'
  import { LabelPrintModal } from '@/views/material/labelPrint'
  import { extractDeliveryValues, extractDeliveryRows } from '@/views/material/labelPrint/config/formFields/deliveryForm'
  import { saveLabelEditSnapshot } from '@/views/material/labelPrint/utils/labelEditSnapshot'
  import MobileSearchPage from '@/components/mobile/MobileSearchPage'
  import MobileHeaderActions from '@/components/mobile/MobileHeaderActions'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { BillListMixin } from './mixins/BillListMixin'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import JDate from '@/components/jeecg/JDate'
  import DataTable from '@/components/DataTable'
  import Vue from 'vue'
  import moment from 'moment'
  import { deleteAction, getAction, postAction } from '@/api/manage'
  import { findBillDetailByNumber } from '@/api/api'
  import { getMpListShort } from '@/utils/util'
  import { getRowImgUrl } from '@/views/material/utils/materialDisplay'
  export default {
    name: "RetailOutList",
    mixins:[JeecgListMixin,BillListMixin],
    components: {
      RetailOutModal,
      RetailBackModal,
      BillDetail,
      BillExcelIframe,
      LabelPrintModal,
      JEllipsis,
      JDate,
      DataTable,
      MobileSearchPage,
      MobileHeaderActions,
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
        // 手机端：是否窄屏（与商品信息页/会员信息页标准页一致）
        isNarrowScreen: false,
        // 手机端：当日销售单卡片数据
        mobileBills: [],
        mobileLoading: false,
        // 手机端搜索关键字与筛选状态
        mobileKeyword: '',
        mobileFilterStatus: undefined,
        filterDrawerVisible: false,
        // 手机端卡片多选
        mobileSelectedIds: [],
        // 手机端已展开商品明细的单据 id（默认全部收起）
        mobileExpandedIds: [],
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
      },
      // 手机端顶栏图标按钮（与商品信息页/会员信息页共用 MobileHeaderActions 标准顺序）
      mobileHeaderActions() {
        const can = this.btnEnableList
        return [
          { key: 'add', icon: 'plus', title: '新增', visible: can.indexOf(1) > -1, click: this.myHandleAdd },
          { key: 'delete', icon: 'delete', title: '删除', visible: can.indexOf(1) > -1, click: this.mobileBatchDel },
          { key: 'enable', icon: 'check-square', title: '审核', visible: this.checkFlag && can.indexOf(2) > -1, click: () => this.mobileBatchSetStatus(1) },
          { key: 'disable', icon: 'close-square', title: '反审核', visible: this.checkFlag && can.indexOf(7) > -1, click: () => this.mobileBatchSetStatus(0) },
          { key: 'export', icon: 'download', title: '导出', visible: this.isShowExcel && can.indexOf(3) > -1, click: this.handleExport }
        ]
      },
      // 手机端卡片列表：按关键字（单号/会员/商品/备注）与单据状态过滤
      mobileBillList() {
        const kw = (this.mobileKeyword || '').trim().toLowerCase()
        const status = this.mobileFilterStatus
        return (this.mobileBills || []).filter(bill => {
          if (status !== undefined && status !== null && status !== '' && String(bill.status) !== String(status)) {
            return false
          }
          if (!kw) return true
          const member = this.billMember(bill) || {}
          const text = [
            bill.number,
            bill.organName,
            bill.materialsList,
            member.contacts,
            member.telephone,
            bill.remark
          ].concat((bill.items || []).map(item => item.materialName || item.name || ''))
            .filter(Boolean).join(' ').toLowerCase()
          return text.indexOf(kw) > -1
        })
      },
      /** 手机端统一筛选面板：单据状态 */
      mobileFilterGroups() {
        return [
          {
            key: 'status',
            label: '单据状态',
            value: this.mobileFilterStatus,
            options: [
              { text: '全部', value: '' },
              { text: '未审核', value: '0' },
              { text: '已审核', value: '1' }
            ]
          }
        ]
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
      // 手机端：与标准搜索页一致的窄屏判定（<=768px），进入页面即加载当日销售单卡片
      this.isNarrowScreen = window.innerWidth <= 768
      this._onResize = () => {
        this.isNarrowScreen = window.innerWidth <= 768
      }
      window.addEventListener('resize', this._onResize)
      if (this.isNarrowScreen) {
        this.loadTodayBills()
      }
    },
    beforeDestroy () {
      if (this._onResize) {
        window.removeEventListener('resize', this._onResize)
      }
    },
    methods: {
      // 主表汇总行改由 DataTable 的 summary 计算属性处理，覆盖 mixin 的空置实现
      tableAddTotalRow() {
      },
      // ===== 手机端：当日销售单卡片 =====
      // 加载当日零售出库单（只加载单据主表，商品明细在卡片展开时按需加载）
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
          this.mobileBills = rows.map(row => Object.assign({}, row, { items: null, itemsLoading: false }))
          this.mobileLoading = false
        }).catch(() => {
          this.mobileBills = []
          this.mobileLoading = false
        })
      },
      // 单据商品明细（展开时加载）
      loadBillItems (row) {
        return getAction('/depotItem/getDetailList', {
          headerId: row.id,
          mpList: getMpListShort(Vue.ls.get('materialPropertyList')),
          linkType: 'basic',
          isReadOnly: '0'
        }).then(res => {
          this.$set(row, 'items', (res && res.code === 200 && res.data && res.data.rows) ? res.data.rows : [])
        }).catch(() => {
          this.$set(row, 'items', [])
        })
      },
      // 商品图片（与销售开单页一致）
      itemImg (item) {
        return getRowImgUrl(item)
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
      // 单据合计数量（优先取主表数量，明细未展开时也能显示）
      billTotalQty (bill) {
        if (bill && bill.materialCount != null && bill.materialCount !== '') {
          return Number(bill.materialCount)
        }
        return (bill.items || []).reduce((sum, item) => sum + (Number(item.operNumber) || 0), 0)
      },
      // 单据零售价合计（明细未加载时直接取主表金额合计）
      billRetailAmount (bill) {
        if (bill && bill.totalPrice != null && bill.totalPrice !== '') {
          return (Number(bill.totalPrice) || 0).toFixed(2)
        }
        const items = (bill && bill.items) || []
        const total = items.reduce((sum, item) => sum + (Number(item.allPrice) || 0), 0)
        return total.toFixed(2)
      },
      // 单据实付价合计
      billPaidAmount (bill) {
        const total = Number(this.billRetailAmount(bill)) || 0
        return (bill.changeAmount == null ? total : Number(bill.changeAmount)).toFixed(2)
      },
      // 单据折扣：结算金额 / 零售价合计，按 10 折制显示
      billDiscount (bill) {
        const retail = Number(this.billRetailAmount(bill)) || 0
        if (retail <= 0) return '-'
        const paid = Number(this.billPaidAmount(bill)) || 0
        const discount = Math.round(paid / retail * 100) / 10
        return discount + '折'
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
      // 是否已展开该单的商品明细
      isMobileCardExpanded (bill) {
        return !!bill && this.mobileExpandedIds.indexOf(bill.id) > -1
      },
      // 点击卡片标题栏切换商品明细展开/收起（首次展开时才请求商品明细）
      toggleMobileCard (bill) {
        if (!bill) return
        const index = this.mobileExpandedIds.indexOf(bill.id)
        if (index > -1) {
          this.mobileExpandedIds.splice(index, 1)
          return
        }
        this.mobileExpandedIds.push(bill.id)
        if (!bill.itemsLoading && !bill.items) {
          this.$set(bill, 'itemsLoading', true)
          this.loadBillItems(bill).then(() => {
            this.$set(bill, 'itemsLoading', false)
          })
        }
      },
      // 发货：直接打开发货单打印弹层（不再跳转发货单页面）
      mobileDeliver (bill) {
        if (!bill || !bill.number) {
          this.$message.warning('单据编号缺失，无法打印发货单')
          return
        }
        const that = this
        const proceed = () => {
          // 取单据详情补全主表字段（收货人/电话/地址等），失败时回退列表行数据
          findBillDetailByNumber({ number: bill.number }).then(res => {
            const detail = (res && res.code === 200 && res.data) ? Object.assign({}, bill, res.data) : bill
            that.openDeliveryPrint(detail)
          }).catch(() => that.openDeliveryPrint(bill))
        }
        // 商品明细尚未加载时先按需加载，保证发货单含商品行
        if (bill.items) {
          proceed()
        } else {
          this.loadBillItems(bill).then(proceed)
        }
      },
      // 组装发货单打印数据（字段 key 与发货单模板一致），写入快照后打开打印弹层
      // 表单数据统一由发货单提取模块生成，快照统一由标签编辑快照模块写入
      openDeliveryPrint (bill) {
        const member = this.billMember(bill) || {}
        const items = bill.items || []
        const values = extractDeliveryValues({ bill: bill, member: member, items: items })
        const rows = extractDeliveryRows(items, { spec: item => this.itemSpec(item) })
        saveLabelEditSnapshot({
          type: 'delivery',
          number: bill.number,
          values: values,
          rows: rows
        })
        this.$refs.deliveryPrintModal.openByType('delivery')
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
      },
      // 手机端搜索：关键字实时过滤卡片（单号/会员/商品/备注）
      mobileSearch (keyword) {
        this.mobileKeyword = (keyword || '').trim()
      },
      /** 统一筛选面板标签点击：单据状态实时过滤卡片 */
      onMobileFilterChange (key, opt) {
        if (key === 'status') {
          this.mobileFilterStatus = opt.value === '' ? undefined : opt.value
        }
      },
      /** 全部关闭：清空筛选条件并关闭面板 */
      resetMobileFilter () {
        this.mobileFilterStatus = undefined
        this.filterDrawerVisible = false
      },
      // 手机端批量删除
      mobileBatchDel () {
        if (this.mobileSelectedIds.length === 0) {
          this.$message.warning('请选择一条记录！')
          return
        }
        const that = this
        this.$confirm({
          title: '确认操作',
          content: '是否删除选中数据?',
          onOk () {
            // deleteBatch 后端为 @DeleteMapping，必须用 DELETE 请求（POST 会报未知异常）
            return deleteAction(that.url.deleteBatch, { ids: that.mobileSelectedIds.join(',') }).then(res => {
              if (res && res.code === 200) {
                that.$message.success('删除成功')
                that.mobileSelectedIds = []
                that.loadTodayBills()
                that.loadData()
              } else if (res && res.data) {
                that.$message.warning(res.data.message || res.data)
              }
            })
          }
        })
      },
      // 手机端批量审核 / 反审核
      mobileBatchSetStatus (status) {
        if (this.mobileSelectedIds.length === 0) {
          this.$message.warning('请选择一条记录！')
          return
        }
        const that = this
        this.$confirm({
          title: '确认操作',
          content: status === 1 ? '是否审核选中单据?' : '是否反审核选中单据?',
          onOk () {
            return postAction(that.url.batchSetStatusUrl, { status: status, ids: that.mobileSelectedIds.join(',') + ',' }).then(res => {
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

  /* 手机端：当日销售单卡片（列表容器与内边距由标准搜索页统一提供） */
  .rol-mobile-tip {
    padding: 30px 0;
    text-align: center;
    font-size: 13px;
    color: #999;
  }
  /* 手机端条件筛选抽屉 */
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
    cursor: pointer;
  }
  .rol-card-summary {
    display: flex;
    align-items: baseline;
    padding-top: 8px;
  }
  .rol-summary-item {
    display: flex;
    align-items: baseline;
    min-width: 0;
  }
  .rol-summary-item:nth-child(1) {
    flex: 0 0 auto;
  }
  .rol-summary-qty,
  .rol-summary-discount {
    flex: 1 1 auto;
    justify-content: center;
  }
  .rol-summary-settle {
    flex: 0 0 auto;
  }
  .rol-card-summary .rol-item-value {
    flex: 0 0 auto;
    text-align: left;
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
    color: #40a9ff;
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
  /* 明细区：与销售开单页一致的商品卡片 */
  .rol-card-items {
    padding-top: 8px;
  }
  .rol-goods-card {
    display: flex;
    gap: 10px;
    padding: 8px;
    background: #fff;
    border: 1px solid #e3e7ee;
    border-radius: 10px;
  }
  .rol-goods-card + .rol-goods-card {
    margin-top: 8px;
  }
  .rol-goods-thumb {
    flex: none;
    width: 64px;
    height: 64px;
    border-radius: 8px;
    overflow: hidden;
    background: #edf3fb;
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .rol-goods-thumb img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    object-position: center top;
    display: block;
  }
  .rol-goods-thumb-icon {
    font-size: 24px;
    color: #c0c9d6;
  }
  .rol-goods-main {
    flex: 1 1 auto;
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .rol-goods-name {
    font-size: 14px;
    font-weight: 600;
    line-height: 18px;
    color: #1d2129;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .rol-goods-spec {
    margin-top: 2px;
    font-size: 12px;
    line-height: 16px;
    color: #40a9ff;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .rol-goods-foot {
    margin-top: auto;
    display: flex;
    align-items: flex-end;
    gap: 8px;
    font-size: 12px;
    line-height: 18px;
    white-space: nowrap;
  }
  .rol-goods-kv {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .rol-goods-kv-label {
    color: #40a9ff;
  }
  .rol-goods-kv-value {
    display: inline-block;
    min-width: 40px;
    padding: 0 6px;
    text-align: center;
    border-radius: 4px;
    background: rgba(24, 144, 255, 0.1);
    color: #1d2129;
  }
  .rol-goods-amount {
    margin-left: auto;
    font-weight: 600;
    color: #333;
  }
  /* 展开时汇总栏位于商品卡片下方，用虚线分隔 */
  .rol-card.is-expanded .rol-card-summary {
    margin-top: 8px;
    padding-top: 8px;
    border-top: 1px dashed #eef0f3;
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
  .rol-label-discount {
    min-width: 2.4em;
  }
  .rol-label-settle {
    min-width: 4.4em;
  }
  .rol-card-summary .rol-label-qty,
  .rol-card-summary .rol-label-discount {
    min-width: 2.4em;
  }
  .rol-card-summary .rol-label-settle {
    min-width: 4.4em;
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
    margin-top: 4px;
  }
  .rol-member-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .rol-member {
    flex: 1;
    min-width: 0;
    white-space: normal;
    word-break: break-all;
    font-size: 13px;
    color: #333;
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
