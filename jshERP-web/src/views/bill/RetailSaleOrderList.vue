<template>
  <div class="retail-sale-order">
    <!-- 基础信息：单号/日期、店铺/开单员（均只读，绑定登录账号）、收银方式/会员 -->
    <div class="rso-billinfo">
      <div class="rso-billinfo-grid-row">
        <div class="rso-billinfo-cell">
          <span class="rso-billinfo-label">单号</span>
          <span class="rso-billinfo-readonly">{{ billForm.number || '自动生成' }}</span>
        </div>
        <div class="rso-billinfo-cell">
          <span class="rso-billinfo-label">日期</span>
          <span class="rso-billinfo-readonly">{{ billTimeText }}</span>
        </div>
      </div>
      <div class="rso-billinfo-grid-row">
        <div class="rso-billinfo-cell">
          <span class="rso-billinfo-label">店铺</span>
          <span class="rso-billinfo-readonly">{{ shopName }}</span>
        </div>
        <div class="rso-billinfo-cell">
          <span class="rso-billinfo-label">开单员</span>
          <span class="rso-billinfo-readonly">{{ operatorName }}</span>
        </div>
      </div>
      <!-- 收银方式在左，会员信息压缩在右 -->
      <div class="rso-billinfo-grid-row">
        <div class="rso-billinfo-cell rso-billinfo-cell--pay">
          <span class="rso-billinfo-label">收银方式</span>
          <a-input :value="billForm.payType" readOnly class="rso-billinfo-field rso-pay-input"
                   @click="openPayPicker">
            <a-icon slot="suffix" type="down" class="rso-field-arrow" />
          </a-input>
          <!-- 收银方式弹层：加载在输入框下方 -->
          <div v-if="payPickerVisible" class="rso-pop-panel rso-pay-panel">
            <div v-for="p in payOptions" :key="p" class="rso-pay-option"
                 :class="{ active: billForm.payType === p }" @click="pickPayType(p)">
              <span>{{ p }}</span>
              <a-icon v-if="billForm.payType === p" type="check" />
            </div>
          </div>
        </div>
        <div class="rso-billinfo-cell rso-billinfo-cell--member">
          <span class="rso-billinfo-label">会员信息</span>
          <div class="rso-member-field">
            <a-input ref="memberInput" :value="memberDisplay" readOnly placeholder="请选择会员"
                     class="rso-member-input" @click="openMemberPicker">
              <a-icon slot="suffix" type="down" class="rso-field-arrow" />
            </a-input>
            <a-button icon="user-add" class="rso-member-add" @click="addMember" />
          </div>
          <!-- 会员信息弹层：加载在输入框下方 -->
          <div v-if="memberPickerVisible" class="rso-pop-panel rso-member-panel">
            <div v-if="selectedMember" class="rso-member-current">
              <div class="rso-member-avatar">{{ (selectedMember.contacts || '会').slice(0, 1) }}</div>
              <div class="rso-member-current-main">
                <div class="rso-member-current-name">{{ selectedMember.contacts || '未填写姓名' }}</div>
                <div class="rso-member-current-sub">
                  {{ selectedMember.telephone || '未填写电话' }}
                  <span v-if="selectedMember.supplier"> · {{ selectedMember.supplier }}</span>
                </div>
              </div>
              <a class="rso-member-clear" @click="clearMember">不使用</a>
            </div>
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
          </div>
        </div>
      </div>
    </div>

    <!-- 弹层遮罩：点击空白关闭收银方式/会员信息弹层 -->
    <div v-if="payPickerVisible || memberPickerVisible" class="rso-pop-mask" @click="closePickers"></div>

    <!-- 开单商品列表 -->
    <div class="rso-cart">
      <div v-if="cartList.length === 0" class="rso-cart-empty">
        <a-icon type="shopping-cart" />
        <p>暂无商品，请扫码或选择商品</p>
      </div>
      <div v-else class="rso-cart-list">
        <div
          v-for="(item, index) in cartList"
          :key="item.id || index"
          class="rso-swipe">
          <!-- 左滑后从卡片右侧滑出的删除按钮 -->
          <div class="rso-swipe-del" @click.stop="onSwipeDelete(index)">删除</div>
          <div
            class="rso-goods-card"
            :class="{ 'is-alt': index % 2 === 1 }"
            :style="swipeTranslateStyle(index)"
            @touchstart="onSwipeStart(index, $event)"
            @touchmove="onSwipeMove($event)"
            @touchend="onSwipeEnd"
            @touchcancel="onSwipeEnd">
          <div class="rso-goods-thumb">
            <img v-if="cartImg(item)" :src="cartImg(item)" alt="" />
            <a-icon v-else type="picture" class="rso-goods-thumb-icon" />
          </div>
          <div class="rso-goods-main">
            <div class="rso-goods-head">
              <div class="rso-goods-name">{{ item.name }}</div>
              <div v-for="(line, i) in cartSpecLines(item)" :key="'spec-' + i" class="rso-goods-spec">
                <span v-if="line.label" class="rso-goods-spec-label">{{ line.label }}：</span>
                <span class="rso-goods-spec-value">{{ line.value }}</span>
              </div>
            </div>
            <!-- 信息行：¥零售价 / 数量 / 折扣 / 金额（折扣与金额可点击改写本行结算金额） -->
            <div class="rso-goods-foot">
              <span class="rso-goods-price">¥{{ cartPrice(item) }}</span>
              <span class="rso-goods-qty">
                <span class="rso-goods-kv-label">数量</span>
                <span class="rso-goods-qty-num">{{ item.operNumber }}</span>
              </span>
              <span class="rso-goods-discount" @click.stop="openLineAmountInput(index)">
                <a-icon type="edit" class="rso-goods-discount-edit" />
                <span>{{ lineDiscountText(item) }}</span>
                <span class="rso-goods-discount-unit">折</span>
              </span>
              <span class="rso-goods-amount" @click.stop="openLineAmountInput(index)">
                <span class="rso-goods-kv-label">金额</span>
                <span class="rso-goods-amount-val">¥{{ lineSettleText(item) }}</span>
                <a-icon type="edit" class="rso-goods-amount-edit" />
              </span>
            </div>
          </div>
          <!-- 数量加减：卡片右侧、上移贴近标题、横向排列 -->
          <div class="rso-goods-stepper">
            <a-button size="small" icon="minus" :disabled="item.operNumber <= 1" @click.stop="changeQty(index, -1)" />
            <span class="rso-goods-stepper-num">{{ item.operNumber }}</span>
            <a-button size="small" icon="plus" @click.stop="changeQty(index, 1)" />
          </div>
          </div>
        </div>
      </div>
    </div>
    <!-- 底部栏（标准手机底栏）：合计数量靠左，其余三项等宽均分 -->
    <mobile-bottom-bar>
      <template #left>
        <div class="rso-bottom-metrics">
          <div class="rso-bottom-item rso-bottom-item--qty">
            <span class="rso-bottom-label">合计数量</span>
            <span class="rso-bottom-value">{{ cartCount }}</span>
          </div>
          <div class="rso-bottom-item">
            <span class="rso-bottom-label">合计金额</span>
            <span class="rso-bottom-value rso-bottom-amount">¥{{ cartTotal }}</span>
          </div>
          <div class="rso-bottom-item rso-bottom-item--editable" @click="openDiscountInput">
            <span class="rso-bottom-label">折扣</span>
            <span class="rso-bottom-value rso-bottom-discount">{{ discountDisplay }}<span class="rso-bottom-unit">折</span><a-icon type="edit" class="rso-bottom-edit" /></span>
          </div>
          <div class="rso-bottom-item rso-bottom-item--editable" @click="openSettleInput">
            <span class="rso-bottom-label">结算金额</span>
            <span class="rso-bottom-value rso-bottom-amount">¥{{ settleDisplay }}<a-icon type="edit" class="rso-bottom-edit" /></span>
          </div>
        </div>
      </template>
    </mobile-bottom-bar>
    <!-- 标准底部操作栏：挂单 / 结算 / 扫码 / 添加，四段紧贴同色，固定在底栏正上方 -->
    <mobile-bottom-actions grouped :buttons="bottomActions" @click="onBottomAction" />

    <!-- 扫码：识别条码后直接带入商品选择弹层 -->
    <mobile-scan-page :visible="scanVisible" @result="onScanResult" @close="scanVisible = false" />

    <!-- 整单结算金额：标准输入弹层 -->
    <mobile-input-popup
      :visible="settleInputVisible"
      title="整单结算金额"
      :value="settleDraft"
      :placeholder="'应收合计 ¥' + cartTotal"
      input-type="number"
      inputmode="decimal"
      enterkeyhint="done"
      confirm-text="确认"
      @confirm="onSettleConfirm"
      @cancel="closeSettleInput" />

    <!-- 折扣：标准输入弹层（折，0-10，一位小数） -->
    <mobile-input-popup
      :visible="discountInputVisible"
      title="折扣"
      :value="discountDraft"
      :placeholder="'合计金额 ¥' + cartTotal"
      input-type="number"
      inputmode="decimal"
      enterkeyhint="done"
      confirm-text="确认"
      @confirm="onDiscountConfirm"
      @cancel="closeDiscountInput" />

    <!-- 本行结算金额：标准输入弹层 -->
    <mobile-input-popup
      :visible="lineAmountVisible"
      title="结算金额"
      :value="lineAmountDraft"
      :placeholder="'本行合计 ¥' + lineAmountBase"
      input-type="number"
      inputmode="decimal"
      enterkeyhint="done"
      confirm-text="确认"
      @confirm="onLineAmountConfirm"
      @cancel="closeLineAmountInput" />

    <!-- 未结算单据（挂单列表）：点顶栏「挂单」弹出，居中显示，点单据直接继续结算 -->
    <a-modal
      :visible="holdsVisible"
      :footer="null"
      :width="320"
      centered
      :bodyStyle="{ padding: '4px 16px 12px', maxHeight: '52vh', overflowY: 'auto' }"
      @cancel="holdsVisible = false">
      <div slot="title" class="rso-hold-title">
        <span>未结算单据</span>
        <span class="rso-hold-count">共 {{ holds.length }} 单</span>
      </div>
      <div v-if="holds.length === 0" class="rso-hold-empty">暂无未结算单据</div>
      <div v-for="h in holds" :key="h.id" class="rso-hold-item" @click="resumeHold(h)">
        <div class="rso-hold-main">
          <div class="rso-hold-no">{{ h.number || '未生成单号' }}</div>
          <div class="rso-hold-sub">{{ h.time }} 挂单 · {{ h.count }} 种 {{ h.qty }} 件</div>
        </div>
        <div class="rso-hold-money">¥{{ h.amount }}</div>
        <a class="rso-hold-del" @click.stop="removeHold(h)">删除</a>
      </div>
    </a-modal>

    <!-- 新增会员 -->
    <member-modal ref="memberModalForm" attach-to-body full-screen :z-index="1200" @ok="onMemberAdded"></member-modal>

    <!-- 标准添加商品弹层（与打印弹层同一套样式，含近 7 天前 20 名） -->
    <mobile-product-picker
      :visible="productPickerVisible"
      title="添加商品"
      :initial-keyword="pickerKeyword"
      :initial-selected="pickerSelectedGoods"
      recent-require-raw
      @confirm="onPickerConfirm"
      @close="productPickerVisible = false" />

    <!-- 结算成功：标准小票打印预览（80mm 小票纸） -->
    <label-print-modal ref="receiptPrintModal" />

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
  import MobileScanPage from '@/components/mobile/MobileScanPage'
  import MobileInputPopup from '@/components/mobile/MobileInputPopup'
  import LabelPrintModal from '@/views/material/modules/LabelPrintModal'
  import { USER_INFO } from '@/store/mutation-types'
  import { setMobileHeaderActions, clearMobileHeaderActions } from '@/utils/mobilePageHeader'
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
      MobileScanPage,
      MobileInputPopup,
      LabelPrintModal
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
        // 开单商品列表
        cartList: [],
        // 单据信息（与电脑端保持一致）
        billForm: {
          organId: undefined,
          operTime: null,
          number: '',
          depotId: undefined,
          payType: '现付'
        },
        // 仓库列表（默认仓库在提交时自动带出）
        depotList: [],
        // 提交订单保存中
        saving: false,
        // 会员选择：弹窗、搜索关键字、会员列表、已选会员
        memberPickerVisible: false,
        memberKeyword: '',
        memberOptions: [],
        selectedMember: null,
        // 收银方式：点击输入框弹出选择
        payPickerVisible: false,
        payOptions: ['现付', '预付款'],
        // 商品选择
        productPickerVisible: false,
        pickerKeyword: '',
        // 标准底部操作按钮：挂单 / 结算 / 扫码 / 添加
        bottomActions: [
          { key: 'hold', text: '挂单' },
          { key: 'settle', text: '结算' },
          { key: 'scan', text: '扫码' },
          { key: 'add', text: '添加' }
        ],
        // 挂单（未结算单据）：本地暂存列表与面板
        holdsVisible: false,
        holds: [],
        // 扫码页
        scanVisible: false,
        // 整单结算金额：null 表示未改写，跟随各行结算金额累加
        settleAmount: null,
        settleInputVisible: false,
        settleDraft: '',
        // 折扣（折，10 = 原价）：改写后由折扣驱动结算金额
        discount: 10,
        discountInputVisible: false,
        discountDraft: '',
        // 本行结算金额改写弹层
        lineAmountVisible: false,
        lineAmountIndex: -1,
        lineAmountDraft: '',
        lineAmountBase: '0.00',
        // 卡片左滑删除：当前滑开的行、位移、删除按钮宽度
        swipeIndex: -1,
        swipeOffset: 0,
        swipeStartX: 0,
        swipeStartY: 0,
        swipeStartOffset: 0,
        swipeActive: false,
        swipeWidth: 64
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
      // 结算金额：各行结算金额累加（未改写的行按零售价×数量计入）
      cartSettleTotal () {
        return this.cartList.reduce((sum, item) => sum + this.lineSettle(item), 0).toFixed(2)
      },
      // 整单结算显示金额：折扣改写后按折扣计算，否则等于各行结算金额累加
      settleDisplay () {
        if (this.settleAmount === null || this.settleAmount === '') {
          if (this.discount - 0 !== 10) return ((this.cartTotal - 0) * (this.discount - 0) / 10).toFixed(2)
          return this.cartSettleTotal
        }
        return (this.settleAmount - 0).toFixed(2)
      },
      // 折扣显示：一位小数
      discountDisplay () {
        const d = this.discount - 0
        return (isFinite(d) ? d : 10).toFixed(1)
      },
      // 会员输入框显示：会员名 + 电话号码（不再显示会员卡号）
      memberDisplay () {
        const m = this.selectedMember
        if (!m) return ''
        return [m.contacts, m.telephone].filter(Boolean).join(' ')
      },
      // 已选商品 → 添加商品弹层初始已选（打开即置顶展示，取消勾选即从本单移除）
      pickerSelectedGoods () {
        return this.cartList.map(item => {
          const raw = Object.assign({}, item, { id: item.mId, meId: item.meId })
          return {
            key: String(item.mId || item.id) + '_' + (item.meId || 0),
            name: item.name,
            spec: getSpecDisplay(item),
            imgUrl: getRowImgUrl(item),
            raw: raw
          }
        })
      },
      // 单据日期只读文本
      billTimeText () {
        const time = this.billForm.operTime
        if (!time) return ''
        // 兼容从挂单快照恢复的字符串日期，避免渲染异常
        return (time.format ? time : moment(time)).format('YYYY-MM-DD HH:mm')
      },
      // 店铺：登录账号绑定的默认仓库（只读）
      shopName () {
        const list = this.depotList || []
        if (list.length === 0) return '未绑定店铺'
        const target = list.find(item => String(item.id) === String(this.billForm.depotId)) || list[0]
        return target.depotName || '未绑定店铺'
      },
      // 开单员：当前登录账号姓名
      operatorName () {
        let info = null
        try {
          info = this.$ls.get(USER_INFO)
        } catch (e) {
          info = null
        }
        const user = info || {}
        return user.username || user.loginName || '未登录'
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
      this.loadShopDepot()
    },
    mounted () {
      const today = getFormatDate()
      this.queryParam.beginTime = today
      this.queryParam.endTime = today
      this.queryParam.createTimeRange = [moment(today), moment(today)]
      // 顶栏「挂单」：查看未结算单据并继续结算；「关闭」：清空本页数据并退出
      this.loadHolds()
      this.registerMobileHeader()
    },
    // keep-alive 缓存页面返回时重新注册，避免顶栏按钮丢失
    activated () {
      this.registerMobileHeader()
    },
    // 离开本页立即清空顶栏按钮，否则缓存的按钮会残留在首页等其它页面
    deactivated () {
      clearMobileHeaderActions()
    },
    beforeDestroy () {
      clearMobileHeaderActions()
    },
    methods: {
      registerMobileHeader () {
        setMobileHeaderActions([
          { key: 'hold', text: '挂单', handler: this.openHoldList },
          { key: 'close', icon: 'close', title: '关闭', handler: this.onClosePage }
        ])
      },
      // 标准底部操作栏：挂单 / 结算 / 扫码 / 添加
      onBottomAction (key) {
        if (key === 'add') {
          this.openMaterialSelect('')
        } else if (key === 'scan') {
          this.scanVisible = true
        } else if (key === 'hold') {
          this.onHoldOrder()
        } else if (key === 'settle') {
          this.submitOrder()
        }
      },
      // 清空当前开单数据（保留店铺/收银方式，单号重新生成）
      clearOrder () {
        this.cartList = []
        this.settleAmount = null
        this.discount = 10
        this.selectedMember = null
        this.billForm.organId = undefined
        this.billForm.number = ''
        this.resetSwipe()
        this.initBillForm()
      },
      // 顶栏关闭：清空本页数据并退出页面
      onClosePage () {
        this.clearOrder()
        this.$router.back()
      },
      // 挂单：暂存当前单据并清空本页，可继续开新单
      onHoldOrder () {
        if (this.cartList.length === 0) {
          this.$message.warning('请先添加商品')
          return
        }
        const snapshot = JSON.parse(JSON.stringify({
          billForm: this.billForm,
          cartList: this.cartList,
          selectedMember: this.selectedMember,
          settleAmount: this.settleAmount,
          discount: this.discount
        }))
        this.holds.push({
          id: Date.now(),
          time: moment().format('MM-DD HH:mm'),
          number: this.billForm.number,
          count: this.cartList.length,
          qty: this.cartCount,
          amount: this.settleDisplay,
          data: snapshot
        })
        this.persistHolds()
        this.clearOrder()
        this.$message.success('已挂单，可点右上角「挂单」继续结算')
      },
      // 挂单存储：按「租户 + 账号」隔离，存本地。只按用户名会在不同账套存在同名账号时串数据
      holdStorageKey () {
        let info = null
        try {
          info = this.$ls.get(USER_INFO)
        } catch (e) {
          info = null
        }
        const user = info || {}
        const tenant = user.tenantId === undefined || user.tenantId === null ? '' : user.tenantId
        const account = user.id !== undefined && user.id !== null ? user.id : (user.username || user.loginName || '')
        return 'jsh.retailSale.holds.' + (tenant || '') + '_' + (account || 'default')
      },
      loadHolds () {
        try {
          const list = JSON.parse(localStorage.getItem(this.holdStorageKey()) || '[]')
          this.holds = Array.isArray(list) ? list : []
        } catch (e) {
          this.holds = []
        }
      },
      persistHolds () {
        try {
          localStorage.setItem(this.holdStorageKey(), JSON.stringify(this.holds))
        } catch (e) {
          this.$message.warning('挂单保存失败，本地存储空间不足')
        }
      },
      openHoldList () {
        this.loadHolds()
        this.holdsVisible = true
      },
      // 继续结算：点击挂单卡片回填整单数据（商品、会员、收银方式、金额），并从列表移除
      resumeHold (hold) {
        const d = hold.data || {}
        this.cartList = Array.isArray(d.cartList) ? JSON.parse(JSON.stringify(d.cartList)) : []
        const form = Object.assign({}, this.billForm, d.billForm || {})
        // 挂单时整单快照经过 JSON 序列化，日期会变成字符串，这里还原为 moment，
        // 否则 billTimeText 渲染时报错会导致整页不刷新（卡片和弹层都停在上一次状态）
        form.operTime = (d.billForm && d.billForm.operTime) ? moment(d.billForm.operTime) : moment()
        this.billForm = form
        this.selectedMember = d.selectedMember ? Object.assign({}, d.selectedMember) : null
        this.settleAmount = d.settleAmount === undefined ? null : d.settleAmount
        this.discount = d.discount === undefined ? 10 : d.discount
        this.closePickers()
        this.resetSwipe()
        this.holds = this.holds.filter(item => item.id !== hold.id)
        this.persistHolds()
        this.holdsVisible = false
      },
      // 删除挂单：直接删除
      removeHold (hold) {
        this.holds = this.holds.filter(item => item.id !== hold.id)
        this.persistHolds()
        if (this.holds.length === 0) this.holdsVisible = false
      },
      // 点击底栏「结算金额」：标准输入弹层，可整单改写
      openSettleInput () {
        if (this.cartList.length === 0) {
          this.$message.warning('请先添加商品')
          return
        }
        this.settleDraft = this.settleDisplay
        this.settleInputVisible = true
      },
      closeSettleInput () {
        this.settleInputVisible = false
      },
      onSettleConfirm (text) {
        this.settleDraft = text
        this.confirmSettleInput()
      },
      // 确认实收金额：空或非法值不生效
      confirmSettleInput () {
        const raw = String(this.settleDraft == null ? '' : this.settleDraft).trim()
        const val = Number(raw)
        if (raw === '' || isNaN(val) || val < 0) {
          this.$message.warning('请输入正确的结算金额')
          return
        }
        this.settleAmount = val.toFixed(2)
        // 改写结算金额后反推折扣（相对合计金额）
        const total = this.cartTotal - 0
        this.discount = total > 0 ? Math.round((val / total) * 100) / 10 : 10
        this.settleInputVisible = false
      },
      // 点击底栏「折扣」：标准输入弹层，0-10 折，一位小数
      openDiscountInput () {
        if (this.cartList.length === 0) {
          this.$message.warning('请先添加商品')
          return
        }
        this.discountDraft = this.discountDisplay
        this.discountInputVisible = true
      },
      closeDiscountInput () {
        this.discountInputVisible = false
      },
      onDiscountConfirm (text) {
        this.discountDraft = text
        this.confirmDiscountInput()
      },
      // 确认折扣：按折扣自动计算结算金额
      confirmDiscountInput () {
        const raw = String(this.discountDraft == null ? '' : this.discountDraft).trim()
        let val = Number(raw)
        if (raw === '' || isNaN(val) || val < 0) {
          this.$message.warning('请输入正确的折扣')
          return
        }
        if (val > 10) val = 10
        val = Math.round(val * 10) / 10
        this.discount = val
        this.discountInputVisible = false
        // 10 折回到跟随明细累加，其余按折扣计算结算金额
        if (val === 10) {
          this.settleAmount = null
        } else {
          this.settleAmount = ((this.cartTotal - 0) * val / 10).toFixed(2)
        }
      },
      // 明细变化后：折扣不变，结算金额按折扣重算
      syncSettleWithDiscount () {
        const d = this.discount - 0
        if (!isFinite(d) || d === 10) {
          this.settleAmount = null
          return
        }
        this.settleAmount = ((this.cartTotal - 0) * d / 10).toFixed(2)
      },
      // 扫码结果：识别到的条码作为商品选择弹层的搜索关键字
      onScanResult (res) {
        this.scanVisible = false
        const text = res && res.text
        if (!text) return
        this.openMaterialSelect(text)
      },
      // 打开标准添加商品弹层
      openMaterialSelect (barCode) {
        this.pickerKeyword = barCode || ''
        this.productPickerVisible = true
      },
      // 弹层确认：以弹层已选结果同步本单商品（已有商品保留数量与改写金额，取消勾选的移除）
      onPickerConfirm (list) {
        this.productPickerVisible = false
        if (!Array.isArray(list)) return
        const keep = {}
        let added = 0
        list.forEach(goods => {
          const raw = goods && goods.raw
          if (!raw) return
          const key = String(raw.meId)
          keep[key] = true
          if (this.cartList.some(r => String(r.meId) === key)) return
          this.cartList.push(this.buildCartItem(raw, 1))
          added++
        })
        const before = this.cartList.length
        this.cartList = this.cartList.filter(r => keep[String(r.meId)])
        const removed = before - this.cartList.length
        if (added === 0 && removed === 0) return
        // 明细变化后结算金额按当前折扣重算
        this.syncSettleWithDiscount()
        const tips = []
        if (added > 0) tips.push('添加 ' + added + ' 个')
        if (removed > 0) tips.push('移除 ' + removed + ' 个')
        this.$message.success(tips.join('，') + '商品')
      },
      // 商品数据 → 购物车项（保留原始 SKU 完整字段，供卡片展示）
      buildCartItem (item, qty) {
        return {
          id: item.meId || item.id,
          meId: item.meId,
          mId: item.id,
          name: item.name,
          standard: item.standard,
          model: item.model,
          unitPrice: item.commodityDecimal,
          commodityDecimal: item.commodityDecimal,
          operNumber: qty,
          settlePrice: null,
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
        }
      },
      // 开单商品卡片：图片 / 规格 / 零售价，与标准商品卡片保持一致
      cartImg (item) {
        return getRowImgUrl(item)
      },
      cartSpec (item) {
        const spec = getSpecDisplay(item)
        if (spec) return spec
        const parts = [item.standard, item.model].filter(function (v) { return v })
        return parts.join(' / ')
      },
      /** 规格展示文本 → 每个属性一行（值只取第一个），与标准商品卡片一致 */
      cartSpecLines (item) {
        const spec = this.cartSpec(item)
        const lines = []
        String(spec || '').split('\n').forEach(function (line) {
          const text = line.trim()
          if (!text) return
          let label = ''
          let value = text
          const idx = text.search(/[：:]/)
          if (idx > -1) {
            label = text.slice(0, idx).trim()
            value = text.slice(idx + 1).trim()
          }
          value = value.split('/')[0].trim()
          if (!value) return
          lines.push({ label: label, value: value })
        })
        return lines
      },
      /** 本行合计：零售价 × 数量 */
      lineTotal (item) {
        const price = (item.unitPrice - 0) || (item.commodityDecimal - 0) || 0
        const qty = item.operNumber - 0 || 1
        return price * qty
      },
      /** 本行结算金额：手工改写值优先，否则等于本行合计 */
      lineSettle (item) {
        if (item.settlePrice === null || item.settlePrice === undefined || item.settlePrice === '') {
          return this.lineTotal(item)
        }
        return item.settlePrice - 0
      },
      lineSettleText (item) {
        return this.lineSettle(item).toFixed(2)
      },
      /** 本行折扣：结算金额 ÷ 本行合计，按 10 折制保留一位 */
      lineDiscountText (item) {
        const total = this.lineTotal(item)
        if (!total) return '10.0'
        return (this.lineSettle(item) / total * 10).toFixed(1)
      },
      // 点击卡片「金额」：标准输入弹层改写本行结算金额
      openLineAmountInput (index) {
        const item = this.cartList[index]
        if (!item) return
        this.lineAmountIndex = index
        this.lineAmountDraft = this.lineSettleText(item)
        this.lineAmountBase = this.lineTotal(item).toFixed(2)
        this.lineAmountVisible = true
      },
      closeLineAmountInput () {
        this.lineAmountVisible = false
      },
      onLineAmountConfirm (text) {
        this.lineAmountDraft = text
        this.confirmLineAmountInput()
      },
      // 确认本行结算金额：改写后计入底部结算金额
      confirmLineAmountInput () {
        const raw = String(this.lineAmountDraft == null ? '' : this.lineAmountDraft).trim()
        const val = Number(raw)
        if (raw === '' || isNaN(val) || val < 0) {
          this.$message.warning('请输入正确的结算金额')
          return
        }
        const item = this.cartList[this.lineAmountIndex]
        if (item) item.settlePrice = val.toFixed(2)
        // 逐行改写后结算金额按当前折扣重算
        this.syncSettleWithDiscount()
        this.lineAmountVisible = false
      },
      cartPrice (item) {
        if (item.retailPrice != null) return item.retailPrice
        if (item.commodityDecimal != null) return item.commodityDecimal
        return '-'
      },
      // 修改数量：最小为 1，减到 0 不删除商品
      changeQty (index, delta) {
        const item = this.cartList[index]
        const newQty = (item.operNumber - 0) + delta
        if (newQty < 1) {
          item.operNumber = 1
          return
        }
        item.operNumber = newQty
        // 数量变化后本行结算金额重置回零售价×数量，避免金额与数量不一致
        item.settlePrice = null
        this.syncSettleWithDiscount()
      },
      // 删除单个商品
      removeCartItem (index) {
        this.cartList.splice(index, 1)
        this.syncSettleWithDiscount()
        this.resetSwipe()
      },
      // 左滑删除：卡片位移 = 当前滑开的行才应用位移
      swipeTranslateStyle (index) {
        const offset = this.swipeIndex === index ? this.swipeOffset : 0
        return { transform: 'translateX(' + offset + 'px)' }
      },
      onSwipeStart (index, e) {
        const t = e.touches && e.touches[0]
        if (!t) return
        this.swipeStartX = t.clientX
        this.swipeStartY = t.clientY
        if (this.swipeIndex === index) {
          this.swipeStartOffset = this.swipeOffset
        } else {
          this.swipeIndex = index
          this.swipeOffset = 0
          this.swipeStartOffset = 0
        }
        this.swipeActive = false
      },
      onSwipeMove (e) {
        if (this.swipeIndex === -1) return
        const t = e.touches && e.touches[0]
        if (!t) return
        const dx = t.clientX - this.swipeStartX
        const dy = t.clientY - this.swipeStartY
        // 未判定方向前，纵向滑动仍交给页面滚动
        if (!this.swipeActive) {
          if (Math.abs(dx) < 6 || Math.abs(dx) <= Math.abs(dy)) return
          this.swipeActive = true
        }
        const offset = Math.min(0, Math.max(-this.swipeWidth, this.swipeStartOffset + dx))
        this.swipeOffset = offset
      },
      onSwipeEnd () {
        if (this.swipeIndex === -1) return
        const open = this.swipeOffset <= -this.swipeWidth / 2
        this.swipeOffset = open ? -this.swipeWidth : 0
        if (!open) this.swipeIndex = -1
        this.swipeActive = false
      },
      onSwipeDelete (index) {
        this.removeCartItem(index)
      },
      resetSwipe () {
        this.swipeIndex = -1
        this.swipeOffset = 0
        this.swipeActive = false
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
      // 收银方式：点击输入框弹出选择面板
      openPayPicker () {
        this.memberPickerVisible = false
        this.payPickerVisible = true
      },
      // 关闭收银方式/会员信息弹层
      closePickers () {
        this.payPickerVisible = false
        this.memberPickerVisible = false
      },
      closePayPicker () {
        this.payPickerVisible = false
      },
      pickPayType (payType) {
        this.billForm.payType = payType
        this.closePayPicker()
      },
      // 打开会员选择弹窗：点击输入框时加载会员列表
      openMemberPicker () {
        this.payPickerVisible = false
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
      // 取消会员
      clearMember () {
        this.selectedMember = null
        this.billForm.organId = undefined
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
      // 店铺（登录账号绑定仓库）：只读展示，唯一或默认仓库自动选中
      loadShopDepot () {
        getAction('/depot/findDepotByCurrentUser').then(res => {
          if (!res || res.code !== 200) return
          const list = Array.isArray(res.data) ? res.data : []
          this.depotList = list
          let target = null
          if (list.length === 1) {
            target = list[0]
          } else if (list.length > 1) {
            target = list.find(item => item.isDefault) || list[0]
          }
          if (target) {
            this.billForm.depotId = String(target.id)
            this.defaultDepotId = String(target.id)
          }
        })
      },
      // 提交订单：直接调用后端保存接口写库（等同网页版「保存」）
      submitOrder () {
        if (this.saving) return
        if (this.cartList.length === 0) {
          this.$message.warning('请先添加商品')
          return
        }
        // 未选择仓库时自动带出默认仓库（原结算页逻辑）
        if (!this.billForm.depotId) {
          const depots = this.depotList || []
          if (this.defaultDepotId) {
            this.billForm.depotId = String(this.defaultDepotId)
          } else if (depots.length > 0) {
            this.billForm.depotId = String(depots[0].id)
          }
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
        // 结算金额：默认等于各行结算金额累加（未改写行按零售价×数量），底栏仍可整单改写
        const getAmount = this.settleDisplay - 0
        const backAmount = getAmount > total ? getAmount - total : 0
        const debt = total > getAmount ? total - getAmount : 0
        const realAmount = getAmount
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
            // 本行结算金额写入明细金额（未改写时即零售价×数量）
            allPrice: this.lineSettle(item).toFixed(2)
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
          discount: this.discount,
          discountMoney: (total - getAmount).toFixed(2),
          discountLastMoney: getAmount.toFixed(2),
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
            // 先弹出标准小票打印预览（依赖当前开单数据），再清空
            this.openReceiptPrint(info.number)
            this.cartList = []
            this.settleAmount = null
            this.discount = 10
            this.selectedMember = null
            this.billForm.organId = undefined
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
      /** 结算成功：写入小票快照并弹出标准小票打印预览（receipt 类型，80mm 纸宽） */
      openReceiptPrint (number) {
        const specText = item => (this.cartSpecLines(item) || []).map(line => {
          return (line.label ? line.label + '：' : '') + line.value
        }).join('　')
        const values = {
          rc_title: '零售小票',
          rc_no: number || '',
          rc_date: moment().format('YYYY-MM-DD HH:mm'),
          rc_member: this.selectedMember ? (this.selectedMember.supplier || this.selectedMember.contacts || '') : '',
          rc_qty_total: String(this.cartCount),
          rc_amount_total: '¥' + this.settleDisplay,
          rc_remark: '',
          rc_footer: '谢谢惠顾，欢迎再次光临'
        }
        const rows = this.cartList.map(item => {
          const qty = item.operNumber - 0 || 0
          const price = (item.unitPrice - 0) || (item.commodityDecimal - 0) || 0
          const specLines = this.cartSpecLines(item) || []
          const specCell = line => (line.label ? line.label + '：' : '') + line.value
          return {
            name: item.name || '',
            spec: specText(item),
            // 规格逐项拆到 spec1/2/3，供小票明细表按列显示；超过 3 项的合并到第 3 列
            spec1: specLines[0] ? specCell(specLines[0]) : '',
            spec2: specLines[1] ? specCell(specLines[1]) : '',
            spec3: specLines.slice(2).map(specCell).join('　'),
            qty: String(qty),
            price: price.toFixed(2),
            // 小票明细金额取本行结算金额（未改写时即零售价×数量）
            amount: this.lineSettle(item).toFixed(2)
          }
        })
        try {
          sessionStorage.setItem('jsh.labelEdit.snapshot', JSON.stringify({
            type: 'receipt',
            number: number || '',
            values: values,
            rows: rows
          }))
        } catch (e) {
          // 存储异常不阻塞打印预览打开
        }
        const modal = this.$refs.receiptPrintModal
        if (modal && typeof modal.openByType === 'function') {
          this.$nextTick(() => modal.openByType('receipt'))
        }
      },
      tableAddTotalRow () {}
    }
  }
</script>

<style lang="less" scoped>
.retail-sale-order {
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  min-height: 100vh;
  background: #f5f5f5;
  // 底部只给标准底栏留位；开单卡片底边延伸到底栏上沿
  padding-bottom: calc(58px + env(safe-area-inset-bottom));
}
// 按钮组：独立组件，不加底衬，直接浮在开单卡片之上
/deep/ .mobile-bottom-actions {
  background: transparent;
}
// 基础信息卡片：单号/日期、仓库/收款类型、会员（页面顶部，开单商品上方）
.rso-billinfo {
  flex: none;
  margin: 8px 12px 6px;
  padding: 2px 12px;
  background: #fff;
  border-radius: 12px;
  .rso-billinfo-row {
    display: flex;
    align-items: center;
    min-height: 40px;
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
    padding: 4px 0;
    border-bottom: 1px solid #f0f0f0;
    &:last-child {
      border-bottom: none;
    }
  }
  .rso-billinfo-cell {
    position: relative;
    flex: 1 1 0;
    min-width: 0;
    display: flex;
    flex-direction: row;
    align-items: center;
    .rso-billinfo-label {
      flex: 0 0 48px;
      width: 48px;
      margin-right: 6px;
      color: #999;
      font-size: 12px;
      line-height: 16px;
      white-space: nowrap;
      text-align: left;
    }
    .rso-billinfo-field {
      flex: 1 1 0;
      width: auto;
      min-width: 0;
    }
    // 只读字段（单号/日期/店铺）：与登录账号绑定，不可编辑
    .rso-billinfo-readonly {
      flex: 1 1 0;
      min-width: 0;
      overflow: hidden;
      white-space: nowrap;
      text-overflow: ellipsis;
      color: #1d2129;
      font-size: 13px;
      line-height: 20px;
    }
    // 开单员/收银方式/会员：值文本左对齐
    .rso-field-arrow {
      color: #999;
      font-size: 11px;
    }
    // 收银方式：输入框固定宽度，会员信息占剩余宽度
    &.rso-billinfo-cell--pay {
      flex: 0 0 auto;
      .rso-pay-input {
        flex: 0 0 84px;
        width: 84px;
      }
    }
    &.rso-billinfo-cell--member {
      flex: 1 1 0;
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
// 会员信息弹层：已选会员卡片
.rso-member-current {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 12px;
  border-bottom: 1px solid #f0f0f0;
  .rso-member-avatar {
    flex: none;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    background: rgba(24, 144, 255, 0.12);
    color: #1890ff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 15px;
    font-weight: 600;
  }
  .rso-member-current-main {
    flex: 1;
    min-width: 0;
  }
  .rso-member-current-name {
    font-size: 15px;
    font-weight: 600;
    color: #1d2129;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .rso-member-current-sub {
    margin-top: 2px;
    font-size: 12px;
    color: #999;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
  .rso-member-clear {
    flex: none;
    font-size: 13px;
    color: #ff4d4f;
  }
}
// 选择弹层：直接加载在输入框下方
.rso-pop-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 9;
}
.rso-pop-panel {
  position: absolute;
  top: calc(100% + 6px);
  z-index: 10;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.14);
  overflow: hidden;
}
.rso-pay-panel {
  left: 54px;
  width: 150px;
  padding: 6px;
  .rso-pay-option {
    margin-top: 0;
    & + .rso-pay-option {
      margin-top: 4px;
    }
  }
}
.rso-member-panel {
  right: 0;
  width: calc(100vw - 40px);
  max-width: 420px;
}
// 收银方式选项
.rso-pay-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 44px;
  margin-top: 8px;
  padding: 0 12px;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  font-size: 14px;
  color: #1d2129;
  &.active {
    border-color: #1890ff;
    background: #e8f3ff;
    color: #1890ff;
    font-weight: 500;
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
  max-height: 40vh;
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
// 底栏指标：合计数量靠左，合计金额/折扣/结算金额三等分居中
.rso-bottom-metrics {
  flex: 1 1 100%;
  width: 100%;
  min-width: 0;
  display: flex;
  align-items: flex-end;
  .rso-bottom-item {
    flex: 1 1 0;
    min-width: 0;
    align-items: center;
    justify-content: flex-end;
  }
  .rso-bottom-item--qty {
    flex: 0 0 auto;
    padding-right: 4px;
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
// 折扣列：蓝色数字，后跟「折」单位
.rso-bottom-discount {
  color: #1890ff;
}
.rso-bottom-unit {
  margin-left: 1px;
  font-size: 11px;
  font-weight: 400;
  color: #999;
}
// 结算金额列可点击改写金额
.rso-bottom-item--editable {
  cursor: pointer;
  .rso-bottom-edit {
    margin-left: 2px;
    font-size: 12px;
    color: #f5222d;
    opacity: 0.6;
  }
}
// 挂单（未结算单据）列表
.rso-hold-count {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #8c8c8c;
}
.rso-hold-empty {
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: #999;
}
.rso-hold-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fff;
  border: 1px solid #e3e7ee;
  border-radius: 10px;
  cursor: pointer;
  &:active {
    background: #f2f7fd;
  }
  .rso-hold-main {
    flex: 1 1 auto;
    min-width: 0;
  }
  .rso-hold-no {
    font-size: 13px;
    font-weight: 500;
    color: #1f2937;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .rso-hold-sub {
    margin-top: 3px;
    font-size: 11px;
    color: #8c8c8c;
  }
  .rso-hold-money {
    flex: 0 0 auto;
    font-size: 14px;
    font-weight: 600;
    color: #1f2937;
  }
  .rso-hold-del {
    flex: 0 0 auto;
    padding: 3px 6px;
    font-size: 11px;
    color: #999;
  }
}
// 开单卡片：未填满时撑满到标准底栏上沿，填满时高度随商品卡片内容
// 开单商品区：不套白色容器卡片，商品卡左右与基础信息卡对齐
.rso-cart {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  margin: 0 12px;
  // 底部留给悬浮的按钮组，避免遮挡最后一张商品卡片
  padding-bottom: 60px;
  background: transparent;
  .rso-cart-empty {
    flex: 1 1 auto;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
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
    padding: 0;
  }
  /* 左滑容器：卡片可左右平移，右侧露出删除按钮 */
  .rso-swipe {
    position: relative;
    margin-bottom: 8px;
    border-radius: 12px;
    overflow: hidden;
  }
  .rso-swipe-del {
    position: absolute;
    top: 0;
    right: 0;
    bottom: 0;
    width: 64px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 14px;
    font-weight: 600;
    color: #fff;
    background: #ff4d4f;
    border-radius: 0 12px 12px 0;
  }
  /* 开单商品卡片：左滑删除、加减器右侧上移、底部金额可点击改写 */
  .rso-goods-card {
    position: relative;
    z-index: 1;
    display: flex;
    gap: 10px;
    padding: 8px;
    margin-bottom: 0;
    background: #fff;
    border: 1px solid #e3e7ee;
    border-radius: 12px;
    transition: transform 0.2s ease;
    &.is-alt {
      background: #eceff3;
      border-color: #e0e4ea;
    }
  }
  .rso-goods-thumb {
    flex: none;
    width: 72px;
    height: 72px;
    border-radius: 8px;
    overflow: hidden;
    background: #edf3fb;
    display: flex;
    align-items: center;
    justify-content: center;
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      object-position: center top;
      display: block;
    }
  }
  .rso-goods-thumb-icon {
    font-size: 20px;
    color: #c3d4e8;
  }
  .rso-goods-main {
    flex: 1 1 auto;
    min-width: 0;
    min-height: 72px;
    display: flex;
    flex-direction: column;
  }
  /* 标题与规格右侧预留加减器宽度，避免文字压到按钮 */
  .rso-goods-head {
    display: flex;
    flex-direction: column;
    padding-right: 100px;
  }
  .rso-goods-name {
    font-size: 15px;
    font-weight: 600;
    line-height: 18px;
    color: #1d2129;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .rso-goods-spec {
    display: flex;
    margin-top: 1px;
    font-size: 12px;
    line-height: 16px;
    color: #40a9ff;
    white-space: nowrap;
    overflow: hidden;
  }
  .rso-goods-spec-label {
    flex: none;
  }
  .rso-goods-spec-value {
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .rso-goods-foot {
    margin-top: auto;
    display: flex;
    align-items: flex-end;
    gap: 8px;
    font-size: 12px;
    line-height: 18px;
    white-space: nowrap;
  }
  .rso-goods-kv {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .rso-goods-kv-label {
    color: #40a9ff;
  }
  .rso-goods-price {
    color: #1890ff;
    font-weight: 600;
  }
  .rso-goods-discount {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    color: #1890ff;
    font-weight: 600;
    cursor: pointer;
  }
  .rso-goods-discount-edit {
    font-size: 12px;
    opacity: 0.7;
  }
  .rso-goods-discount-unit {
    font-weight: 400;
  }
  .rso-goods-qty {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .rso-goods-qty-num {
    min-width: 14px;
    color: #1d2129;
    font-weight: 600;
  }
  .rso-goods-amount {
    margin-left: auto;
    display: inline-flex;
    align-items: center;
    gap: 3px;
    cursor: pointer;
  }
  .rso-goods-amount-val {
    color: #1d2129;
    font-size: 17px;
    line-height: 22px;
    font-weight: 700;
    border-bottom: 1px dashed #1890ff;
  }
  .rso-goods-amount-edit {
    font-size: 12px;
    color: #1890ff;
    opacity: 0.7;
  }
  .rso-goods-stepper {
    position: absolute;
    right: 16px;
    top: 22px;
    display: flex;
    align-items: center;
  }
  .rso-goods-stepper-num {
    min-width: 24px;
    text-align: center;
    font-size: 14px;
    color: #1d2129;
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
