<template>
  <div class="retail-sale-order">
    <!-- 欲售商品列表 -->
    <div class="rso-cart">
      <div class="rso-cart-header">
        <span class="rso-cart-title">欲售商品（{{ cartList.length }}）</span>
        <a v-if="cartList.length > 0" @click="clearCart" class="rso-cart-clear">清空</a>
      </div>
      <div v-if="cartList.length === 0" class="rso-cart-empty">
        <a-icon type="shopping-cart" />
        <p>暂无商品，请扫码或选择商品</p>
      </div>
      <div v-else class="rso-cart-list">
        <div v-for="(item, index) in cartList" :key="item.id || index" class="rso-cart-item">
          <div class="rso-cart-item-top">
            <img v-if="cartImg(item)" :src="cartImg(item)" class="rso-cart-item-img" />
            <div v-else class="rso-cart-item-img rso-cart-item-img-empty">无图</div>
            <div class="rso-cart-item-info">
              <div class="rso-cart-item-name">{{ item.name }}</div>
              <div class="rso-cart-item-spec">{{ cartSpec(item) }}</div>
            </div>
            <!-- 数量调节：紧挨卡片右侧边框，垂直居中 -->
            <div class="rso-cart-item-qty">
              <a-button size="small" icon="minus" @click="changeQty(index, -1)" />
              <span class="rso-cart-qty-num">{{ item.operNumber }}</span>
              <a-button size="small" icon="plus" @click="changeQty(index, 1)" />
            </div>
            <a-icon type="close" class="rso-cart-item-del" @click="removeCartItem(index)" />
          </div>
          <!-- SKU码 / 库存 / 零售价：与商品信息页卡片保持一致 -->
          <div class="rso-cart-item-meta">
            <span class="rso-cart-item-sku">SKU码 {{ item.skuCode || '-' }}</span>
            <span>库存 {{ item.stock == null ? '-' : item.stock }}</span>
            <span>零售价 {{ cartPrice(item) }}</span>
          </div>
        </div>
      </div>
    </div>
    <!-- 底部栏（标准手机底栏）：数量合计/金额合计/实际结算三列等宽 + 右侧结算按钮 -->
    <mobile-bottom-bar>
      <template #left>
        <div class="rso-bottom-metrics">
          <div class="rso-bottom-item">
            <span class="rso-bottom-label">数量合计</span>
            <span class="rso-bottom-value">{{ cartCount }}</span>
          </div>
          <div class="rso-bottom-item">
            <span class="rso-bottom-label">金额合计</span>
            <span class="rso-bottom-value rso-bottom-amount">¥{{ cartTotal }}</span>
          </div>
          <div class="rso-bottom-item">
            <span class="rso-bottom-label">实际结算</span>
            <span class="rso-bottom-value rso-bottom-amount">¥{{ cartTotal }}</span>
          </div>
        </div>
      </template>
      <template #right>
        <a-button type="primary" @click="openSettle">结算</a-button>
      </template>
    </mobile-bottom-bar>
    <!-- 标准底部操作栏：选择添加在左，扫码添加在右，固定在底栏正上方 -->
    <mobile-bottom-actions :buttons="bottomActions" @click="onBottomAction" />

    <!-- 结算页面：标准手机全屏页面（顶栏返回/标题 + 单据信息置顶 + 商品明细 + 合计实收 + 底部提交） -->
    <div v-if="settleVisible" class="rso-settle-page">
      <mobile-top-bar title="结算" @back="settleVisible = false" />
      <div class="rso-settle-body">
        <!-- 单据信息：单号/日期、仓库/收款类型按两列并排置于页面最顶端 -->
        <div class="rso-billinfo">
          <div class="rso-billinfo-grid-row">
            <div class="rso-billinfo-cell">
              <span class="rso-billinfo-label">单号</span>
              <a-input v-model="billForm.number" placeholder="请输入单号" class="rso-billinfo-field" />
            </div>
            <div class="rso-billinfo-cell">
              <span class="rso-billinfo-label">日期</span>
              <a-date-picker v-model="billForm.operTime" showTime format="YYYY-MM-DD HH:mm:ss"
                             class="rso-billinfo-field" />
            </div>
          </div>
          <div class="rso-billinfo-grid-row">
            <div class="rso-billinfo-cell">
              <span class="rso-billinfo-label">仓库</span>
              <a-select v-model="billForm.depotId" placeholder="请选择仓库" class="rso-billinfo-field"
                        :dropdownMatchSelectWidth="false" allowClear>
                <a-select-option v-for="item in depotList" :key="item.id" :value="String(item.id)">
                  {{ item.depotName }}
                </a-select-option>
              </a-select>
            </div>
            <div class="rso-billinfo-cell">
              <span class="rso-billinfo-label">收款类型</span>
              <a-select v-model="billForm.payType" class="rso-billinfo-field">
                <a-select-option value="现付">现付</a-select-option>
                <a-select-option value="预付款">预付款</a-select-option>
              </a-select>
            </div>
          </div>
          <!-- 会员信息：点击输入框或下拉箭头加载会员列表；右侧按钮新增会员 -->
          <div class="rso-billinfo-row">
            <span class="rso-billinfo-label">会员信息</span>
            <div class="rso-member-field">
              <a-input ref="memberInput" :value="memberDisplay" readOnly placeholder="请选择会员"
                       class="rso-member-input" @click="openMemberPicker">
                <a-icon slot="suffix" type="down" class="rso-member-arrow" />
              </a-input>
              <a-button icon="user-add" class="rso-member-add" @click="addMember" />
            </div>
          </div>
        </div>
        <!-- 商品明细：名称 / 规格 / 数量 / 金额 -->
        <div class="rso-settle-items">
          <div v-for="(item, index) in cartList" :key="item.id || index" class="rso-settle-item">
            <div class="rso-settle-item-row">
              <span class="rso-settle-item-name">{{ item.name }}</span>
              <span class="rso-settle-item-amount">¥{{ itemAmount(item) }}</span>
            </div>
            <div class="rso-settle-item-row">
              <span class="rso-settle-item-spec">{{ cartSpec(item) || '-' }}</span>
              <span class="rso-settle-item-qty">x{{ item.operNumber }}</span>
            </div>
          </div>
          <div v-if="cartList.length === 0" class="rso-settle-empty">暂无商品</div>
        </div>
      </div>
      <!-- 底部栏（标准手机底栏）：数量/金额/实收 竖排指标 + 右侧提交订单 -->
      <mobile-bottom-bar>
        <template #left>
          <div class="rso-settle-metrics">
            <div class="rso-bottom-item">
              <span class="rso-bottom-label">数量</span>
              <span class="rso-bottom-value">{{ cartCount }}</span>
            </div>
            <div class="rso-bottom-item">
              <span class="rso-bottom-label">金额</span>
              <span class="rso-bottom-value rso-bottom-amount">¥{{ cartTotal }}</span>
            </div>
            <div class="rso-bottom-item">
              <span class="rso-bottom-label">实收</span>
              <a-input :value="payAmountDisplay" inputmode="decimal" class="rso-bottom-pay-input"
                       @change="onPayAmountChange" />
            </div>
          </div>
        </template>
        <template #right>
          <a-button type="primary" :loading="saving" @click="submitOrder">提交订单</a-button>
        </template>
      </mobile-bottom-bar>
    </div>

    <!-- 选择会员：顶部按手机号搜索，列表可下滑 -->
    <a-modal
      :visible="memberPickerVisible"
      title="选择会员"
      :footer="null"
      width="90%"
      centered
      :bodyStyle="{ padding: 0 }"
      :maskClosable="true"
      @cancel="closeMemberPicker"
    >
      <div class="rso-member-search">
        <a-input v-model="memberKeyword" placeholder="请输入手机号搜索" allowClear @pressEnter="searchMembers" />
        <a-button type="primary" icon="search" @click="searchMembers">搜索</a-button>
      </div>
      <div class="rso-member-list">
        <div v-for="item in memberOptions" :key="item.id" class="rso-member-item" @click="pickMember(item)">
          <div class="rso-member-item-main">
            <span class="rso-member-item-supplier">{{ item.supplier }}</span>
            <span class="rso-member-item-name">{{ item.contacts || '未填写姓名' }}</span>
          </div>
          <div class="rso-member-item-phone">{{ item.telephone || '' }}</div>
        </div>
        <div v-if="memberOptions.length === 0" class="rso-member-empty">暂无会员</div>
      </div>
    </a-modal>

    <!-- 新增会员 -->
    <member-modal ref="memberModalForm" attach-to-body full-screen :z-index="1200" @ok="onMemberAdded"></member-modal>

    <!-- 标准添加商品弹层（与打印弹层同一套样式，含近 7 天前 20 名） -->
    <mobile-product-picker
      :visible="productPickerVisible"
      title="添加商品"
      :initial-keyword="pickerKeyword"
      recent-require-raw
      @pick="onPickerPick"
      @confirm="onPickerConfirm"
      @close="productPickerVisible = false" />

    <!-- 单据弹窗（复用零售出库业务逻辑） -->
    <retail-out-modal ref="modalForm" @ok="modalFormOk" @close="modalFormClose"></retail-out-modal>
    <bill-detail ref="modalDetail" @ok="modalFormOk" @close="modalFormClose"></bill-detail>
  </div>
</template>

<script>
  import RetailOutModal from './modules/RetailOutModal'
  import BillDetail from './dialog/BillDetail'
  import MemberModal from '@/views/system/modules/MemberModal'
  import MobileProductPicker from '@/components/mobile/MobileProductPicker'
  import MobileBottomActions from '@/components/mobile/MobileBottomActions'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { BillListMixin } from './mixins/BillListMixin'
  import { getAction, postAction } from '@/api/manage'
  import { findBySelectRetail } from '@/api/api'
  import { getFormatDate } from '@/utils/util'
  import DataTable from '@/components/DataTable'
  import MobileBottomBar from '@/components/mobile/MobileBottomBar'
  import MobileTopBar from '@/components/mobile/MobileTopBar'
  import moment from 'moment'
  import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'

  export default {
    name: "RetailSaleOrderList",
    mixins: [JeecgListMixin, BillListMixin],
    components: {
      RetailOutModal,
      BillDetail,
      MemberModal,
      MobileProductPicker,
      MobileBottomActions,
      DataTable,
      MobileBottomBar,
      MobileTopBar
    },
    data () {
      return {
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
        defDataIndex: ['action', 'organName', 'number', 'materialsList', 'operTimeStr', 'userName', 'materialCount', 'totalPrice', 'status'],
        defColumns: [
          { title: '操作', dataIndex: 'action', align: "center", width: 120, scopedSlots: { customRender: 'action' } },
          { title: '单据编号', dataIndex: 'number', width: 140 },
          { title: '商品信息', dataIndex: 'materialsList', width: 160, ellipsis: true },
          { title: '单据日期', dataIndex: 'operTimeStr', width: 120 },
          { title: '数量', dataIndex: 'materialCount', width: 60 },
          { title: '金额合计', dataIndex: 'totalPrice', width: 80 },
          { title: '状态', dataIndex: 'status', width: 70, align: "center", scopedSlots: { customRender: 'customRenderStatus' } }
        ],
        url: {
          list: "/depotHead/list",
          delete: "/depotHead/delete",
          deleteBatch: "/depotHead/deleteBatch",
          batchSetStatusUrl: "/depotHead/batchSetStatus"
        },
        // 欲售商品列表
        cartList: [],
        // 单据信息（与电脑端保持一致）
        billForm: {
          organId: undefined,
          operTime: null,
          number: '',
          depotId: undefined,
          payType: '现付',
          payAmount: 0
        },
        // 仓库列表（默认仓库在打开结算时自动带出）
        depotList: [],
        // 提交订单保存中
        saving: false,
        // 会员选择：弹窗、搜索关键字、会员列表、已选会员
        memberPickerVisible: false,
        memberKeyword: '',
        memberOptions: [],
        selectedMember: null,
        // 商品选择
        productPickerVisible: false,
        pickerKeyword: '',
        // 标准底部操作按钮：添加商品
        bottomActions: [
          { key: 'select', text: '添加商品' }
        ],
        // 结算弹窗（单据信息）
        settleVisible: false
      }
    },
    computed: {
      cartTotal () {
        return this.cartList.reduce((sum, item) => {
          const price = item.unitPrice - 0 || item.commodityDecimal - 0 || 0
          return sum + price * (item.operNumber - 0 || 1)
        }, 0).toFixed(2)
      },
      // 底部栏数量：所有商品数量累加
      cartCount () {
        return this.cartList.reduce((sum, item) => sum + (item.operNumber - 0 || 0), 0)
      },
      // 会员输入框显示：会员名 + 电话号码（不再显示会员卡号）
      memberDisplay () {
        const m = this.selectedMember
        if (!m) return ''
        return [m.contacts, m.telephone].filter(Boolean).join(' ')
      },
      // 实收输入框显示：保留两位小数
      payAmountDisplay () {
        const n = Number(this.billForm.payAmount)
        return Number.isFinite(n) ? (Math.max(n, 0)).toFixed(2) : '0.00'
      }
    },
    created () {
      this.initSystemConfig()
      this.initBillForm()
      this.initRetail()
      this.getDepotData()
      this.initUser()
      this.initAccount()
      this.initQuickBtn()
      this.getDepotByCurrentUser()
    },
    mounted () {
      const today = getFormatDate()
      this.queryParam.beginTime = today
      this.queryParam.endTime = today
      this.queryParam.createTimeRange = [moment(today), moment(today)]
    },
    methods: {
      // 标准底部操作栏：添加商品
      onBottomAction (key) {
        if (key === 'select') {
          this.openMaterialSelect('')
        }
      },
      // 打开标准添加商品弹层
      openMaterialSelect (barCode) {
        this.pickerKeyword = barCode || ''
        this.productPickerVisible = true
      },
      // 弹层单选：点击商品直接加入并关闭
      onPickerPick (goods) {
        this.addPickedGoods([goods])
        this.productPickerVisible = false
      },
      // 弹层多选：点「确定」后批量加入并关闭
      onPickerConfirm (list) {
        this.addPickedGoods(list)
        this.productPickerVisible = false
      },
      // 标准商品弹层回传数据转购物车数据（保留原始 SKU 完整字段）
      addPickedGoods (list) {
        if (!Array.isArray(list)) return
        const items = []
        list.forEach(goods => {
          const raw = goods && goods.raw
          if (!raw) return
          items.push(Object.assign({}, raw, { operNumber: 1 }))
        })
        this.handleMaterialSelect(items)
      },
      // 商品页面选择商品回调（接收已选商品数组，含 operNumber 数量）
      handleMaterialSelect (items) {
        if (!Array.isArray(items)) return
        items.forEach(item => {
          const qty = item.operNumber - 0 || 1
          const exist = this.cartList.find(r => String(r.meId) === String(item.meId))
          if (exist) {
            exist.operNumber = (exist.operNumber - 0) + qty
          } else {
            this.cartList.push({
              id: item.meId || item.id,
              meId: item.meId,
              mId: item.id,
              name: item.name,
              standard: item.standard,
              model: item.model,
              unitPrice: item.commodityDecimal,
              commodityDecimal: item.commodityDecimal,
              operNumber: qty,
              skuCode: item.skuCode,
              sku: item.sku,
              barCode: item.mBarCode || item.barCode,
              unit: item.unit,
              stock: item.stock,
              // 卡片展示用：图片与规格明细（与商品信息页一致）
              imgName: item.imgName,
              attribute: item.attribute,
              specValues: item.specValues,
              specJson: item.specJson,
              specSummary: item.specSummary,
              retailPrice: item.retailPrice
            })
          }
        })
        if (items.length > 0) {
          this.$message.success('已添加 ' + items.length + ' 个商品')
        }
      },
      // 欲售商品卡片：图片 / 规格 / 零售价，与商品信息页卡片保持一致
      cartImg (item) {
        return getRowImgUrl(item)
      },
      cartSpec (item) {
        const spec = getSpecDisplay(item)
        if (spec) return spec
        const parts = [item.standard, item.model].filter(function (v) { return v })
        return parts.join(' / ')
      },
      cartPrice (item) {
        if (item.retailPrice != null) return item.retailPrice
        if (item.commodityDecimal != null) return item.commodityDecimal
        return '-'
      },
      // 结算明细：单个商品金额 = 单价 x 数量（与 cartTotal 的取价逻辑一致）
      itemAmount (item) {
        const price = item.unitPrice - 0 || item.commodityDecimal - 0 || 0
        return (price * (item.operNumber - 0 || 1)).toFixed(2)
      },
      // 修改数量
      changeQty (index, delta) {
        const item = this.cartList[index]
        const newQty = (item.operNumber - 0) + delta
        if (newQty <= 0) {
          this.cartList.splice(index, 1)
        } else {
          item.operNumber = newQty
        }
      },
      // 删除单个商品
      removeCartItem (index) {
        this.cartList.splice(index, 1)
      },
      // 清空欲售商品
      clearCart () {
        this.$confirm({
          title: '确认清空',
          content: '确定清空所有欲售商品吗？',
          onOk: () => {
            this.cartList = []
          }
        })
      },
      // 提交订单：复用零售出库弹窗逻辑
      // 初始化单据信息：默认日期为当前时间，单据编号由后端序列生成
      initBillForm () {
        this.billForm.operTime = moment()
        getAction('/sequence/buildNumber', {prefixNo: this.prefixNo}).then(res => {
          if (res && res.code === 200) {
            this.billForm.number = res.data.defaultNumber
          }
        })
      },
      // 打开会员选择弹窗：点击输入框时加载会员列表
      openMemberPicker () {
        if (this.memberPickerVisible) return
        this.memberPickerVisible = true
        if (this.memberOptions.length === 0) {
          this.loadMembers()
        }
      },
      // 关闭会员选择弹窗：同时让输入框失焦，避免焦点回到输入框后再次触发弹窗
      closeMemberPicker () {
        this.memberPickerVisible = false
        const input = this.$refs.memberInput
        if (input && input.blur) input.blur()
      },
      // 加载会员（卡号 + 姓名），可滚动查看
      loadMembers () {
        findBySelectRetail({ key: this.memberKeyword, limit: 1 }).then(res => {
          this.memberOptions = Array.isArray(res) ? res : []
        })
      },
      // 按手机号搜索会员
      searchMembers () {
        this.loadMembers()
      },
      // 选中会员
      pickMember (item) {
        this.selectedMember = item
        this.billForm.organId = item.id
        this.closeMemberPicker()
      },
      // 打开新增会员页面
      addMember () {
        const ref = this.$refs.memberModalForm
        if (!ref) return
        ref.title = '新增会员'
        ref.add()
      },
      // 新增会员保存后，将刚新增的会员回填到会员卡号输入框
      onMemberAdded (member) {
        this.memberKeyword = ''
        const supplierNo = member && member.supplier
        if (!supplierNo) {
          this.loadMembers()
          return
        }
        // 先用新增表单数据展示卡号与姓名，再用接口结果补全会员id（绑定单据organId）
        this.selectedMember = {
          supplier: supplierNo,
          contacts: member.contacts,
          telephone: member.telephone
        }
        findBySelectRetail({ key: supplierNo, limit: 1 }).then(res => {
          const list = Array.isArray(res) ? res : []
          const hit = list.find(item => String(item.supplier) === String(supplierNo)) || list[0]
          if (hit) {
            this.selectedMember = hit
            this.billForm.organId = hit.id
          }
          if (list.length > 0) {
            this.memberOptions = list
          } else {
            this.loadMembers()
          }
        })
      },
      // 打开结算页面：自动带出默认仓库，实收默认填单据金额
      openSettle () {
        if (!this.billForm.depotId) {
          const depots = this.depotList || []
          if (this.defaultDepotId) {
            this.billForm.depotId = String(this.defaultDepotId)
          } else if (depots.length > 0) {
            this.billForm.depotId = String(depots[0].id)
          }
        }
        this.billForm.payAmount = this.cartTotal - 0
        this.settleVisible = true
      },
      // 实收输入：只保留数字和小数点，失焦时回写金额并格式化为两位小数
      onPayAmountChange (e) {
        const raw = (e.target.value || '').replace(/[^\d.]/g, '')
        const num = Math.max(parseFloat(raw) || 0, 0)
        this.billForm.payAmount = Math.round(num * 100) / 100
        e.target.value = this.billForm.payAmount.toFixed(2)
      },
      // 提交订单：直接调用后端保存接口写库（等同网页版「保存」）
      submitOrder () {
        if (this.saving) return
        if (this.cartList.length === 0) {
          this.$message.warning('请先添加商品')
          return
        }
        if (!this.billForm.depotId) {
          this.$message.warning('请选择仓库')
          return
        }
        if (!this.billForm.number) {
          this.$message.warning('请填写单据编号')
          return
        }
        const total = this.cartTotal - 0
        const getAmount = this.billForm.payAmount - 0
        // 找零：收款金额超出单据金额的部分
        const backAmount = Math.max(getAmount - total, 0)
        // 欠款：收款金额不足单据金额的部分
        const debt = Math.max(total - getAmount, 0)
        // 实收金额：不超过单据金额，用于按行金额占比分摊到明细
        const realAmount = Math.min(getAmount, total)
        const rows = this.cartList.map(item => {
          const price = (item.unitPrice - 0) || (item.commodityDecimal - 0) || 0
          const qty = item.operNumber - 0 || 1
          return {
            barCode: item.barCode,
            skuCode: item.skuCode,
            sku: item.sku,
            unit: item.unit,
            depotId: this.billForm.depotId,
            operNumber: qty,
            unitPrice: price,
            allPrice: (price * qty).toFixed(2)
          }
        })
        const info = {
          type: '出库',
          subType: '零售',
          number: this.billForm.number,
          operTime: moment(this.billForm.operTime).format('YYYY-MM-DD HH:mm:ss'),
          organId: this.billForm.organId,
          payType: this.billForm.payType,
          totalPrice: total.toFixed(2),
          changeAmount: total.toFixed(2),
          getAmount: getAmount.toFixed(2),
          backAmount: backAmount.toFixed(2),
          debt: debt.toFixed(2),
          realAmount: realAmount.toFixed(2),
          discount: 0,
          discountMoney: 0,
          discountLastMoney: total.toFixed(2),
          otherMoney: 0,
          remark: '',
          status: '0'
        }
        this.saving = true
        postAction('/depotHead/addDepotHeadAndDetail', {
          info: JSON.stringify(info),
          rows: JSON.stringify(rows)
        }).then(res => {
          this.saving = false
          if (res && res.code === 200) {
            this.$message.success('销售单保存成功：' + info.number)
            this.settleVisible = false
            this.cartList = []
            this.selectedMember = null
            this.billForm.organId = undefined
            this.billForm.payAmount = 0
            this.billForm.number = ''
            this.initBillForm()
          } else {
            this.$message.warning((res && res.data && res.data.message) || '保存失败')
          }
        }).catch(err => {
          this.saving = false
          const msg = err && err.response && err.response.data && err.response.data.message
          this.$message.error(msg || '保存失败，请查看系统日志')
        })
      },
      tableAddTotalRow () {}
    }
  }
</script>

<style lang="less" scoped>
.retail-sale-order {
  min-height: 100vh;
  background: #f5f5f5;
  // 给底部固定金额栏 + 标准双按钮操作栏留出空间
  padding-bottom: calc(118px + env(safe-area-inset-bottom));
}
// 结算页面顶部单据信息：单列对齐（卡片）
.rso-billinfo {
  flex-shrink: 0;
  margin: 10px 12px 0;
  padding: 4px 12px;
  background: #fff;
  border-radius: 8px;
  .rso-billinfo-row {
    display: flex;
    align-items: center;
    min-height: 46px;
    border-bottom: 1px solid #f0f0f0;
    &:last-child {
      border-bottom: none;
    }
  }
  // 两列并排的单据字段：标题与组件同行并列，两条竖列等宽
  .rso-billinfo-grid-row {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 8px 0;
    border-bottom: 1px solid #f0f0f0;
    &:last-child {
      border-bottom: none;
    }
  }
  .rso-billinfo-cell {
    flex: 1 1 0;
    min-width: 0;
    display: flex;
    flex-direction: row;
    align-items: center;
    .rso-billinfo-label {
      flex: 0 0 auto;
      width: auto;
      margin-right: 4px;
      color: #999;
      font-size: 12px;
      line-height: 16px;
      white-space: nowrap;
    }
    .rso-billinfo-field {
      flex: 1 1 0;
      width: auto;
      min-width: 0;
    }
    // 两列内空间有限：组件强制占满剩余宽度并允许收缩，缩小字号和内边距，保证单号、日期完整显示
    /deep/ .ant-input,
    /deep/ .ant-select,
    /deep/ .ant-calendar-picker {
      flex: 1 1 0;
      width: auto;
      min-width: 0;
    }
    /deep/ .ant-calendar-picker {
      flex: 1 1 0 !important;
      width: 0 !important;
      min-width: 0 !important;
    }
    /deep/ .ant-input {
      padding-left: 2px;
      padding-right: 2px;
      font-size: 13px !important;
    }
    /deep/ .ant-calendar-picker-input {
      width: 100%;
      padding-right: 18px !important;
      font-size: 13px !important;
    }
    /deep/ .ant-calendar-picker-icon {
      right: 4px;
    }
    /deep/ .ant-select-selection__rendered {
      margin-left: 2px;
      margin-right: 2px;
    }
    /deep/ .ant-select-selection-selected-value {
      font-size: 13px !important;
    }
  }
  .rso-billinfo-label {
    flex: 0 0 76px;
    width: 76px;
    color: #333;
    font-size: 14px;
    text-align: left;
  }
  .rso-billinfo-field {
    flex: 1;
    min-width: 0;
  }
  // 会员卡号：输入框 + 右侧新增会员按钮
  .rso-member-field {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
  }
  .rso-member-input {
    flex: 1;
    min-width: 0;
  }
  .rso-member-add {
    flex-shrink: 0;
    margin-left: 8px;
  }
}
// 选择会员弹窗
.rso-member-search {
  display: flex;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  .ant-btn {
    margin-left: 8px;
    flex-shrink: 0;
  }
}
.rso-member-list {
  max-height: 60vh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}
.rso-member-item {
  padding: 10px 12px;
  border-bottom: 1px solid #f5f5f5;
  cursor: pointer;
  &.active {
    background: #e6f7ff;
  }
  .rso-member-item-main {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .rso-member-item-supplier {
    font-size: 15px;
    font-weight: 600;
    color: rgba(0, 0, 0, 0.85);
  }
  .rso-member-item-name {
    font-size: 13px;
    color: #666;
  }
  .rso-member-item-phone {
    margin-top: 4px;
    font-size: 12px;
    color: #999;
  }
}
.rso-member-empty {
  padding: 40px 0;
  text-align: center;
  color: #999;
}
// 底栏指标：数量合计/金额合计/实际结算三列等宽均分，标题与数据均按列居中对齐
.rso-bottom-metrics {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: stretch;
  .rso-bottom-item {
    flex: 1 1 0;
    min-width: 0;
    align-items: center;
    justify-content: center;
  }
  .rso-bottom-label,
  .rso-bottom-value {
    width: 100%;
    text-align: center;
  }
}
// 标准手机底栏内的指标：上面标题，下面数据（底栏高度与样式统一在 MobileBottomBar 组件）
.rso-bottom-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}
.rso-bottom-label {
  color: #999;
  font-size: 12px;
  line-height: 18px;
}
.rso-bottom-value {
  color: #333;
  font-size: 16px;
  font-weight: 600;
  line-height: 24px;
}
.rso-bottom-amount {
  color: #f5222d;
}
// 实收输入框：与其它数据同高、同字号，宽度占满所在列，保证标题与数据左右居中对齐
.rso-bottom-pay-input {
  width: 100%;
  max-width: 100%;
  height: 22px;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
  font-size: 14px;
  font-weight: 600;
  line-height: 22px;
  text-align: center;
  &:focus {
    border: none;
    box-shadow: none;
  }
}
// 结算底栏指标：数量/金额/实收三列等宽均分，标题与数据均按列居中对齐，字号适当缩小
.rso-settle-metrics {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: stretch;
  margin-left: -10px;
  .rso-bottom-item {
    flex: 1 1 0;
    min-width: 0;
    align-items: center;
    justify-content: center;
  }
  .rso-bottom-label {
    width: 100%;
    font-size: 11px;
    line-height: 16px;
    text-align: center;
  }
  .rso-bottom-value {
    width: 100%;
    font-size: 14px;
    line-height: 22px;
    text-align: center;
  }
}
// 结算页面：固定全屏，顶栏与全局手机页面顶栏一致（49px 蓝底）
.rso-settle-page {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 500;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  // 页面主体：可滚动；底部标准底栏固定，留出与底栏等高的空间
  .rso-settle-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    padding-bottom: calc(58px + env(safe-area-inset-bottom));
  }
}
// 商品明细列表：卡片样式，随商品数量增长
.rso-settle-items {
  margin: 10px 12px;
  background: #fff;
  border-radius: 8px;
}
.rso-settle-item {
  padding: 8px 12px;
  border-bottom: 1px solid #f5f5f5;
  &:last-child {
    border-bottom: none;
  }
  .rso-settle-item-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .rso-settle-item-name {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    font-weight: 500;
    color: rgba(0, 0, 0, 0.85);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .rso-settle-item-amount {
    flex-shrink: 0;
    margin-left: 8px;
    font-size: 14px;
    font-weight: 600;
    color: #f5222d;
  }
  .rso-settle-item-spec {
    flex: 1;
    min-width: 0;
    margin-top: 2px;
    font-size: 12px;
    color: #999;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .rso-settle-item-qty {
    flex-shrink: 0;
    margin-left: 8px;
    margin-top: 2px;
    font-size: 12px;
    color: #666;
  }
}
.rso-settle-empty {
  padding: 24px 0;
  text-align: center;
  color: #999;
  font-size: 13px;
}
.rso-cart {
  margin: 10px 12px;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  .rso-cart-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px;
    border-bottom: 1px solid #f0f0f0;
    .rso-cart-title {
      font-size: 15px;
      font-weight: 600;
    }
    .rso-cart-clear {
      color: #999;
      font-size: 13px;
      cursor: pointer;
    }
  }
  .rso-cart-empty {
    text-align: center;
    padding: 40px 0;
    color: #bbb;
    .anticon {
      font-size: 40px;
    }
    p {
      margin-top: 10px;
      font-size: 13px;
    }
  }
  .rso-cart-list {
    max-height: 40vh;
    overflow-y: auto;
  }
  .rso-cart-item {
    position: relative;
    display: flex;
    flex-direction: column;
    padding: 8px;
    margin-bottom: 8px;
    border: 1px solid #F0F2F5;
    border-radius: 8px;
    .rso-cart-item-top {
      display: flex;
      flex-direction: row;
      align-items: flex-start;
    }
    .rso-cart-item-img {
      width: 88px;
      height: 88px;
      object-fit: cover;
      border-radius: 8px;
      flex-shrink: 0;
      margin: 0 10px 0 0;
    }
    .rso-cart-item-img-empty {
      display: flex;
      align-items: center;
      justify-content: center;
      background: #FAFBFD;
      color: #bbb;
      font-size: 12px;
    }
    .rso-cart-item-info {
      flex: 1;
      min-width: 0;
      // 预留右侧数量组件的空间，避免规格文字被遮挡
      padding-right: 96px;
      .rso-cart-item-name {
        font-size: 14px;
        font-weight: 500;
      }
      .rso-cart-item-spec {
        font-size: 12px;
        color: #999;
        margin-top: 4px;
        line-height: 18px;
        white-space: pre-line;
      }
    }
    .rso-cart-item-del {
      color: #ccc;
      font-size: 16px;
      cursor: pointer;
      margin-left: 8px;
    }
    /* SKU码 / 库存 / 零售价：一行左对齐图片框 */
    .rso-cart-item-meta {
      display: flex;
      gap: 12px;
      margin-top: 6px;
      font-size: 12px;
      color: #666;
      white-space: nowrap;
      overflow: hidden;
    }
    /* 数量调节：紧挨卡片右侧边框，垂直居中 */
    .rso-cart-item-qty {
      position: absolute;
      right: 8px;
      top: 50%;
      transform: translateY(-50%);
      display: flex;
      align-items: center;
      .rso-cart-qty-num {
        display: inline-block;
        min-width: 28px;
        text-align: center;
        font-size: 14px;
      }
    }
  }
}
.rso-history {
  margin: 10px 12px;
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  .rso-history-title {
    font-size: 15px;
    font-weight: 600;
    margin-bottom: 10px;
  }
  .rso-search {
    margin-bottom: 10px;
  }
}
</style>
