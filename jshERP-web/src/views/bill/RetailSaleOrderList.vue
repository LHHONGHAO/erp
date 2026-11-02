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
    <!-- 底部栏（标准手机底栏）：数量/金额左对齐 + 居中扫码 + 右侧结算按钮 -->
    <mobile-bottom-bar>
      <template #left>
        <div class="rso-bottom-item">
          <span class="rso-bottom-label">数量</span>
          <span class="rso-bottom-value">{{ cartCount }}</span>
        </div>
        <div class="rso-bottom-item">
          <span class="rso-bottom-label">金额</span>
          <span class="rso-bottom-value rso-bottom-amount">¥{{ cartTotal }}</span>
        </div>
      </template>
      <div class="rso-bottom-scan" @click="openScanner">
        <a-icon type="scan" />
      </div>
      <template #right>
        <a-button type="primary" @click="openSettle">结算</a-button>
      </template>
    </mobile-bottom-bar>

    <!-- 结算页面：标准手机全屏页面（顶栏返回/标题 + 单据信息置顶 + 商品明细 + 合计实收 + 底部提交） -->
    <div v-if="settleVisible" class="rso-settle-page">
      <div class="rso-settle-header">
        <a-icon type="left" class="rso-settle-back" @click="settleVisible = false" />
        <span class="rso-settle-title">结算</span>
      </div>
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

    <!-- 全屏扫码界面 -->
    <div v-if="scanVisible" class="rso-scan-fullscreen">
      <!-- 无法获取摄像头权限时：展示证书安装下载说明 -->
      <cert-install-guide v-if="certGuideVisible" @close="closeCertGuide" @retry="retryScan" />
      <div class="rso-scan-stage" @click="onScanTap">
        <video ref="scanVideo" class="rso-scan-video" autoplay playsinline muted></video>
        <canvas ref="scanOverlay" class="rso-scan-overlay"></canvas>
      </div>
      <a-icon type="close" class="rso-scan-close" @click="closeScanner" />
      <!-- 手动输入条码搜索：取景框下方 -->
      <div v-if="!multiScanMode" class="rso-scan-manual" :class="{ 'rso-scan-manual-focused': manualFocused }">
        <a-input v-model="manualCode" placeholder="手动输入条码搜索" allowClear
                 class="rso-scan-manual-input" @focus="manualFocused = true" @blur="manualFocused = false"
                 @pressEnter="submitManualCode" />
        <a-button type="primary" @click="submitManualCode">搜索</a-button>
      </div>
      <!-- 页面内文字提示：识别结果、商品是否存在、异常都在这里显示，不再弹框 -->
      <div class="rso-scan-tip" :class="scanTipClass">{{ scanTip }}</div>
      <!-- 拍照识别：只分析取景框内的图像 -->
      <div v-if="!multiScanMode" class="rso-scan-capture">
        <a-button type="primary" size="large" block :loading="capturing" @click="shoot">拍照识别</a-button>
      </div>
      <!-- 焦距调节：默认后置摄像头，初始焦距为原生焦距的三分之一 -->
      <div v-if="!multiScanMode && zoomSupported" class="rso-scan-zoom">
        <a-button shape="circle" icon="zoom-out" @click="changeZoom(-1)" />
        <span class="rso-scan-zoom-value">{{ zoomText }}</span>
        <a-button shape="circle" icon="zoom-in" @click="changeZoom(1)" />
      </div>
    </div>

    <!-- 商品选择页面（共用页面，弹窗形式加载） -->
    <a-modal
      :visible="materialVisible"
      title="选择商品"
      @cancel="materialVisible = false"
      :footer="null"
      width="90%"
      centered
      destroyOnClose
      :bodyStyle="{ padding: 0, height: 'calc(100vh - 120px)', overflow: 'hidden' }"
    >
      <material-select-page :bar-code="scanBarCode" @back="materialVisible = false" @select="handleMaterialSelect" />
    </a-modal>

    <!-- 单据弹窗（复用零售出库业务逻辑） -->
    <retail-out-modal ref="modalForm" @ok="modalFormOk" @close="modalFormClose"></retail-out-modal>
    <bill-detail ref="modalDetail" @ok="modalFormOk" @close="modalFormClose"></bill-detail>
  </div>
</template>

<script>
  import RetailOutModal from './modules/RetailOutModal'
  import BillDetail from './dialog/BillDetail'
  import MaterialSelectPage from '@/views/material/MaterialSelectPage'
  import MemberModal from '@/views/system/modules/MemberModal'
  import CertInstallGuide from './modules/CertInstallGuide'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { BillListMixin } from './mixins/BillListMixin'
  import { getAction, postAction } from '@/api/manage'
  import { findBySelectRetail } from '@/api/api'
  import { getFormatDate } from '@/utils/util'
  import DataTable from '@/components/DataTable'
  import MobileBottomBar from '@/components/mobile/MobileBottomBar'
  import moment from 'moment'
  import { searchContentByCode } from '@/services/search/contentSearch'
  import { SCANNER_STATE } from '@/types/scanner'
  import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'

  export default {
    name: "RetailSaleOrderList",
    mixins: [JeecgListMixin, BillListMixin],
    components: {
      RetailOutModal,
      BillDetail,
      MaterialSelectPage,
      MemberModal,
      CertInstallGuide,
      DataTable,
      MobileBottomBar
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
        // 扫码相关
        scanVisible: false,
        // 扫码状态机
        scanState: SCANNER_STATE.INITIAL,
        scanResult: null,
        scanError: '',
        searching: false,
        // 成功提示自动消失的定时器
        successTimer: null,
        // 扫描反馈提示音（Web Audio 合成，不依赖音频文件）
        audioContext: null,
        scanStream: null,
        capturing: false,
        // 焦距（变焦）调节：默认后置摄像头，初始焦距取当前值的三分之一（受设备最小焦距限制）
        zoomSupported: false,
        zoomValue: 1,
        zoomMin: 1,
        zoomMax: 1,
        zoomStepValue: 0.5,
        // 无法获取摄像头权限时展示证书安装下载说明
        certGuideVisible: false,
        // 是否已进入多码框选模式（出现过两个以上不同码值后锁定）
        multiScanMode: false,
        // 本帧识别到的码：[{ code, box }]
        detectedCodes: [],
        // 多码模式下绘制到屏幕的框（屏幕坐标，用于点击命中判断）
        screenBoxes: [],
        // 解码帧尺寸（识别框坐标以该尺寸为准）
        frameW: 0,
        frameH: 0,
        // 商品选择
        materialVisible: false,
        scanBarCode: '',
        // 扫码页手动输入条码
        manualCode: '',
        manualFocused: false,
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
      },
      // 扫码提示语：所有扫码反馈（识别结果、商品是否存在、异常）只在页面内文字提示
      scanTip () {
        if (this.multiScanMode) {
          return `画面中有 ${this.detectedCodes.length} 个码，请点击要使用的方框`
        }
        if (this.scanState === 'SUCCESS') {
          const name = this.scanResult && this.scanResult.name
          return name ? `商品添加成功：${name}` : '商品添加成功'
        }
        if (this.scanState === 'NOT_FOUND') {
          return this.scanResult
            ? `条码 ${this.scanResult.content} 商品不存在`
            : (this.scanError || '条码未识别，请将条码完整放入框内后重试')
        }
        if (this.scanState === 'ERROR') {
          return this.scanError || '摄像头或识别异常，可点击「手动输入」'
        }
        const tips = {
          INITIAL: '正在准备扫码...',
          REQUESTING: '正在请求摄像头权限...',
          SCANNING: '将条码/二维码完整放入框内，点击「拍照识别」',
          RECOGNIZING: '正在识别框内图像...',
          SEARCHING: '正在搜索商品...',
          STOPPED: '已停止扫描'
        }
        return tips[this.scanState] || '将条码/二维码完整放入框内'
      },
      // 提示文字颜色：成功绿色、失败/异常红色
      scanTipClass () {
        if (this.scanState === 'SUCCESS') return 'rso-scan-tip-success'
        if (this.scanState === 'NOT_FOUND' || this.scanState === 'ERROR') return 'rso-scan-tip-error'
        return ''
      },
      // 当前焦距显示（倍数）
      zoomText () {
        return this.zoomValue.toFixed(1) + 'x'
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
    beforeDestroy () {
      this.stopScan()
      if (this.audioContext) {
        try { this.audioContext.close() } catch (e) {}
        this.audioContext = null
      }
    },
    methods: {
      // 打开全屏扫码界面并立即启动摄像头
      openScanner () {
        this.scanVisible = true
        // 点击扫码图标即解锁音频，保证后续扫描反馈提示音可播放
        this.unlockAudio()
        this.scanResult = null
        this.scanError = ''
        this.scanState = SCANNER_STATE.INITIAL
        this.searching = false
        this.certGuideVisible = false
        this.$nextTick(() => {
          this.startScan()
        })
      },
      // 是否具备网页摄像头调用条件（必须 HTTPS 安全连接且浏览器支持 getUserMedia）
      cameraAvailable () {
        return !!(window.isSecureContext && navigator.mediaDevices && navigator.mediaDevices.getUserMedia)
      },
      // 初始化/唤醒音频上下文（iOS 需在用户点击手势中解锁）
      unlockAudio () {
        if (this.audioContext) return this.audioContext
        const Ctx = window.AudioContext || window.webkitAudioContext
        if (!Ctx) return null
        try {
          this.audioContext = new Ctx()
        } catch (e) {
          return null
        }
        return this.audioContext
      },
      // 扫描反馈提示音：识别成功为两声上扬短音，识别失败为两声下沉短音
      playFeedbackSound (success) {
        const ctx = this.unlockAudio()
        if (!ctx) return
        if (ctx.state === 'suspended') {
          ctx.resume().catch(() => {})
        }
        try {
          const now = ctx.currentTime
          const tones = success
            ? [[880, 0], [1320, 0.1]]
            : [[440, 0], [300, 0.14]]
          tones.forEach(tone => {
            const freq = tone[0]
            const delay = tone[1]
            const duration = success ? 0.11 : 0.16
            const osc = ctx.createOscillator()
            const gain = ctx.createGain()
            osc.type = 'sine'
            osc.frequency.value = freq
            gain.gain.setValueAtTime(0.0001, now + delay)
            gain.gain.exponentialRampToValueAtTime(0.22, now + delay + 0.02)
            gain.gain.exponentialRampToValueAtTime(0.0001, now + delay + duration)
            osc.connect(gain)
            gain.connect(ctx.destination)
            osc.start(now + delay)
            osc.stop(now + delay + duration + 0.02)
          })
        } catch (e) {}
      },
      // 启动摄像头：只做预览，不自动解码；识别在点击「拍照识别」时只分析取景框区域
      startScan () {
        // 统一申请摄像头权限：非 HTTPS 安全连接下浏览器不会提供摄像头，直接展示证书安装说明
        if (!this.cameraAvailable()) {
          this.scanState = SCANNER_STATE.ERROR
          this.scanError = '当前页面不是 HTTPS 安全连接，无法申请摄像头权限'
          this.certGuideVisible = true
          return
        }
        const video = this.$refs.scanVideo
        if (!video) return
        this.stopScan()
        this.scanResult = null
        this.scanError = ''
        this.detectedCodes = []
        this.screenBoxes = []
        this.multiScanMode = false
        this.scanState = SCANNER_STATE.REQUESTING
        // 固定使用后置摄像头（扫码更清晰）
        const constraints = {
          audio: false,
          video: {
            width: { ideal: 1920 },
            height: { ideal: 1080 },
            facingMode: { ideal: 'environment' }
          }
        }
        navigator.mediaDevices.getUserMedia(constraints).then(stream => {
          if (!this.scanVisible) {
            this.stopStream(stream)
            return
          }
          this.scanStream = stream
          video.srcObject = stream
          return video.play().then(() => {
            this.scanState = SCANNER_STATE.SCANNING
            this.drawGuideFrame()
            // 部分浏览器 play 之后才有真实分辨率，延迟重绘一次保证取景框位置准确
            setTimeout(() => {
              if (this.scanVisible && this.scanStream) this.drawGuideFrame()
            }, 300)
            this.detectZoom()
          })
        }).catch(error => {
          console.error('摄像头开启失败:', error)
          this.onScannerError(error)
        })
      },
      // 检测焦距（变焦）能力，并把初始焦距设为当前的三分之一（设备支持下限为准）
      detectZoom () {
        const track = this.scanStream && this.scanStream.getVideoTracks()[0]
        if (!track || !track.getCapabilities) {
          this.zoomSupported = false
          return
        }
        const caps = track.getCapabilities()
        const zoom = caps && caps.zoom
        if (!zoom || !isFinite(zoom.min) || !isFinite(zoom.max) || zoom.max <= zoom.min) {
          this.zoomSupported = false
          return
        }
        this.zoomSupported = true
        this.zoomMin = zoom.min
        this.zoomMax = zoom.max
        this.zoomStepValue = Math.max(0.1, Math.round((zoom.max - zoom.min) / 10 * 100) / 100)
        // 默认焦距固定为 2.4x（低于设备下限或高于上限时自动取边界值）
        this.applyZoom(2.4)
      },
      // 应用焦距：受设备最小/最大值限制
      applyZoom (value) {
        const track = this.scanStream && this.scanStream.getVideoTracks()[0]
        if (!track || !this.zoomSupported) return
        const next = Math.round(Math.max(this.zoomMin, Math.min(this.zoomMax, value)) * 100) / 100
        track.applyConstraints({ advanced: [{ zoom: next }] }).then(() => {
          this.zoomValue = next
        }).catch(() => {
          // 部分浏览器声明了能力但不支持设置，隐藏焦距按钮
          this.zoomSupported = false
        })
      },
      // 焦距调节按钮：direction 为 -1 拉远、1 拉近
      changeZoom (direction) {
        this.applyZoom(this.zoomValue + direction * this.zoomStepValue)
      },
      // 拍照入口：截取取景框内的画面交给后端识别
      shoot () {
        // 拍照是用户点击动作，这里解锁音频，后续提示音才能正常播放
        this.unlockAudio()
        this.captureFrame()
      },
      // 拍照识别：只截取取景框内的画面，放大后交给后端识别
      captureFrame () {
        if (this.searching || this.capturing) return
        const video = this.$refs.scanVideo
        if (!video || !video.videoWidth) {
          this.scanState = SCANNER_STATE.ERROR
          this.scanError = '摄像头尚未就绪，请稍候再试'
          return
        }
        this.capturing = true
        this.scanState = SCANNER_STATE.RECOGNIZING
        try {
          const rect = this.getFrameSourceRect(video)
          // 框内区域放大后再识别，小条码更容易解出
          const scale = Math.min(3, Math.max(1, 1280 / rect.w))
          const canvas = document.createElement('canvas')
          canvas.width = Math.round(rect.w * scale)
          canvas.height = Math.round(rect.h * scale)
          canvas.getContext('2d').drawImage(video, rect.x, rect.y, rect.w, rect.h, 0, 0, canvas.width, canvas.height)
          const image = canvas.toDataURL('image/jpeg', 0.92)
          postAction('/material/scanFrame', { image }).then(response => {
            this.capturing = false
            if (!this.scanVisible) return
            const codes = (response && response.code === 200 && response.data && response.data.codes) || []
            const unique = []
            codes.forEach(item => {
              if (item && item.code && !unique.some(u => u.code === item.code && u.format === item.format)) {
                unique.push(item)
              }
            })
            if (unique.length === 0) {
              this.scanState = SCANNER_STATE.NOT_FOUND
              this.scanError = '条码未识别，请将条码完整放入框内后重试'
              this.playFeedbackSound(false)
              return
            }
            this.handleScannedContent(unique[0].code, unique[0].format || 'UNKNOWN', unique[0].type || 'UNKNOWN')
          }).catch(error => {
            this.capturing = false
            if (!this.scanVisible) return
            this.scanState = SCANNER_STATE.ERROR
            this.scanError = '识别失败：' + (error && error.message ? error.message : '未知错误')
            this.playFeedbackSound(false)
          })
        } catch (error) {
          this.capturing = false
          this.scanState = SCANNER_STATE.ERROR
          this.scanError = '截取画面失败：' + (error && error.message ? error.message : '未知错误')
          this.playFeedbackSound(false)
        }
      },
      // 取景框：宽度不变（屏幕内容宽 86%），高度缩为原来高度的 2/3，中线位于屏幕高度 1/3 处
      getGuideBox (elW, elH) {
        const video = this.$refs.scanVideo
        const vw = (video && video.videoWidth) || elW
        const vh = (video && video.videoHeight) || elH
        const scale = Math.min(elW / vw, elH / vh)
        const dispW = vw * scale
        const dispH = vh * scale
        const offX = (elW - dispW) / 2
        // 宽度保持原值，只压缩高度
        const w = dispW * 0.86
        const oldH = Math.min(w / 2.2, dispH * 0.52)
        const h = oldH * 2 / 3
        // 取景框垂直中线对齐屏幕高度的三分之一处
        let y = elH / 3 - h / 2
        if (y < 0) y = 0
        if (y + h > elH - 8) y = Math.max(0, elH - h - 8)
        return { x: offX + (dispW - w) / 2, y, w, h }
      },
      // 取景框对应的视频原始像素区域（拍照时按这个区域裁剪）
      getFrameSourceRect (video) {
        const elW = video.clientWidth
        const elH = video.clientHeight
        const vw = video.videoWidth
        const vh = video.videoHeight
        const scale = Math.min(elW / vw, elH / vh)
        const offX = (elW - vw * scale) / 2
        const offY = (elH - vh * scale) / 2
        const box = this.getGuideBox(elW, elH)
        const x = Math.max(0, (box.x - offX) / scale)
        const y = Math.max(0, (box.y - offY) / scale)
        const w = Math.min(vw - x, box.w / scale)
        const h = Math.min(vh - y, box.h / scale)
        return { x, y, w, h }
      },
      // 绘制取景框：框外半透明遮罩 + 绿色边框与四角标记
      drawGuideFrame () {
        const video = this.$refs.scanVideo
        const overlay = this.$refs.scanOverlay
        if (!video || !overlay) return
        const elW = video.clientWidth
        const elH = video.clientHeight
        if (!elW || !elH) return
        overlay.width = elW
        overlay.height = elH
        const ctx = overlay.getContext('2d')
        ctx.clearRect(0, 0, elW, elH)
        const box = this.getGuideBox(elW, elH)
        ctx.fillStyle = 'rgba(0, 0, 0, 0.55)'
        ctx.fillRect(0, 0, elW, elH)
        ctx.clearRect(box.x, box.y, box.w, box.h)
        ctx.strokeStyle = '#52c41a'
        ctx.lineWidth = 3
        ctx.strokeRect(box.x, box.y, box.w, box.h)
        // 四角加粗，便于对齐条码
        const len = Math.min(box.w, box.h) / 6
        ctx.lineWidth = 5
        const corners = [
          [box.x, box.y, 1, 1],
          [box.x + box.w, box.y, -1, 1],
          [box.x, box.y + box.h, 1, -1],
          [box.x + box.w, box.y + box.h, -1, -1]
        ]
        corners.forEach(c => {
          ctx.beginPath()
          ctx.moveTo(c[0], c[1] + c[3] * len)
          ctx.lineTo(c[0], c[1])
          ctx.lineTo(c[0] + c[2] * len, c[1])
          ctx.stroke()
        })
      },
      // 摄像头异常：没有权限时展示证书安装下载说明，其余异常在页面内提示
      onScannerError (error) {
        const message = error && error.message ? error.message : '摄像头或识别异常'
        const name = (error && error.name) || ''
        console.error('扫码异常:', error)
        const denied = name === 'NotAllowedError' || name === 'PermissionDeniedError' || name === 'SecurityError'
        this.scanError = denied ? '无法访问摄像头，请允许摄像头权限' : message
        this.scanState = SCANNER_STATE.ERROR
        if (this.scanStream) {
          this.stopStream(this.scanStream)
          this.scanStream = null
        }
        if (denied) this.certGuideVisible = true
      },
      // 释放视频流
      stopStream (stream) {
        if (stream) {
          stream.getTracks().forEach(track => track.stop())
        }
      },
      // 绘制识别框：视频坐标 → 屏幕坐标（按 object-fit: contain 换算）
      drawScanOverlay () {
        const video = this.$refs.scanVideo
        const overlay = this.$refs.scanOverlay
        if (!video || !overlay) return
        const elW = video.clientWidth
        const elH = video.clientHeight
        if (!elW || !elH) return
        if (overlay.width !== elW) overlay.width = elW
        if (overlay.height !== elH) overlay.height = elH
        const ctx = overlay.getContext('2d')
        ctx.clearRect(0, 0, elW, elH)
        this.screenBoxes = []
        if (this.detectedCodes.length === 0) return
        const fw = this.frameW || video.videoWidth
        const fh = this.frameH || video.videoHeight
        if (!fw || !fh) return
        const scale = Math.min(elW / fw, elH / fh)
        const offX = (elW - fw * scale) / 2
        const offY = (elH - fh * scale) / 2
        this.detectedCodes.forEach((item, index) => {
          if (!item.box) return
          const b = item.box
          const w = Math.max(b.w * scale, 70)
          const h = Math.max(b.h * scale, 48)
          const x = Math.min(Math.max(offX + b.x * scale, 0), elW - 10)
          const y = Math.min(Math.max(offY + b.y * scale, 0), elH - 10)
          this.screenBoxes.push({ code: item.code, format: item.format, type: item.type, x, y, w, h })
          ctx.fillStyle = 'rgba(82, 196, 26, 0.16)'
          ctx.fillRect(x, y, w, h)
          ctx.strokeStyle = '#52c41a'
          ctx.lineWidth = 3
          ctx.strokeRect(x, y, w, h)
          // 左上角序号，便于说明“点击哪个方框”
          ctx.fillStyle = '#52c41a'
          ctx.fillRect(x, y - 20, 20, 20)
          ctx.fillStyle = '#fff'
          ctx.font = '12px sans-serif'
          ctx.fillText(String(index + 1), x + 6, y - 5)
        })
      },
      // 点击画面：多码模式下命中哪个框就用哪个码；单码时点击即确认
      onScanTap (event) {
        if (this.detectedCodes.length === 0) return
        if (!this.multiScanMode) {
          if (this.detectedCodes.length === 1) {
            const item = this.detectedCodes[0]
            this.handleScannedContent(item.code, item.format || 'UNKNOWN', item.type || 'UNKNOWN')
          }
          return
        }
        const video = this.$refs.scanVideo
        if (!video) return
        const rect = video.getBoundingClientRect()
        const cx = event.clientX - rect.left
        const cy = event.clientY - rect.top
        const hit = (this.screenBoxes || []).find(b =>
          cx >= b.x && cx <= b.x + b.w && cy >= b.y && cy <= b.y + b.h
        )
        if (!hit) return
        this.handleScannedContent(hit.code, hit.format || 'UNKNOWN', hit.type || 'UNKNOWN')
      },
      // 停止扫码：释放视频流、画面资源与状态
      stopScan (nextState) {
        if (this.successTimer) {
          clearTimeout(this.successTimer)
          this.successTimer = null
        }
        if (this.scanStream) {
          this.stopStream(this.scanStream)
          this.scanStream = null
        }
        const video = this.$refs.scanVideo
        if (video) {
          try { video.pause() } catch (e) {}
          video.srcObject = null
        }
        this.capturing = false
        this.zoomSupported = false
        this.zoomValue = 1
        const overlay = this.$refs.scanOverlay
        if (overlay) {
          const ctx = overlay.getContext('2d')
          ctx.clearRect(0, 0, overlay.width, overlay.height)
        }
        this.detectedCodes = []
        this.screenBoxes = []
        this.multiScanMode = false
        if (nextState) {
          this.scanState = nextState
        }
      },
      closeScanner () {
        this.stopScan(SCANNER_STATE.STOPPED)
        this.scanVisible = false
        this.certGuideVisible = false
        this.manualCode = ''
      },
      // 手动输入条码搜索：与扫码识别走同一套搜索逻辑
      submitManualCode () {
        const code = (this.manualCode || '').trim()
        if (!code) {
          this.scanError = '请输入条码'
          this.scanState = SCANNER_STATE.ERROR
          return
        }
        this.handleScannedContent(code, 'MANUAL', 'MANUAL')
      },
      // 关闭证书安装说明
      closeCertGuide () {
        this.certGuideVisible = false
      },
      // 安装证书后重新申请摄像头权限
      retryScan () {
        this.certGuideVisible = false
        this.scanState = SCANNER_STATE.INITIAL
        this.scanError = ''
        this.$nextTick(() => this.startScan())
      },
      /**
       * 识别内容统一入口（拍照识别、多码框选共用）。
       * content 原样提交，不截断、不转换、不删除前导零。
       */
      handleScannedContent (content, format, type) {
        if (!content || this.searching) return
        this.searching = true
        this.scanResult = {
          content: content,
          format: format || 'UNKNOWN',
          type: type || 'UNKNOWN',
          timestamp: Date.now()
        }
        // 搜索期间保留摄像头画面，避免出现黑屏；成功后再释放
        if (navigator.vibrate) navigator.vibrate(60)
        searchContentByCode({
          content: content,
          format: this.scanResult.format,
          type: this.scanResult.type,
          organId: this.queryParam.organId,
          prefixNo: this.prefixNo
        }).then(result => {
          this.searching = false
          if (!this.scanVisible) return
          if (!result.success || result.rows.length === 0) {
            this.scanState = SCANNER_STATE.NOT_FOUND
            this.playFeedbackSound(false)
            return
          }
          if (result.rows.length === 1) {
            this.addMaterialToCart(result.rows[0])
            this.scanResult = Object.assign({}, this.scanResult, { name: result.rows[0].name })
            this.scanState = SCANNER_STATE.SUCCESS
            this.playFeedbackSound(true)
            // 识别成功：短暂展示成功提示后退出扫码页面
            this.scheduleScanClose(600)
            return
          }
          this.scanState = SCANNER_STATE.SUCCESS
          this.playFeedbackSound(true)
          this.closeScanner()
          this.openMaterialSelect(content)
        }).catch(error => {
          this.searching = false
          if (!this.scanVisible) return
          this.scanState = SCANNER_STATE.ERROR
          this.scanError = '搜索商品失败：' + (error && error.message ? error.message : '未知错误')
          this.playFeedbackSound(false)
        })
      },
      // 将搜索结果加入欲售商品列表
      addMaterialToCart (mInfo) {
        const exist = this.cartList.find(r => String(r.meId) === String(mInfo.meId))
        if (exist) {
          exist.operNumber = (exist.operNumber - 0) + 1
        } else {
          this.cartList.push({
            id: mInfo.meId || mInfo.id,
            meId: mInfo.meId,
            mId: mInfo.id,
            name: mInfo.name,
            standard: mInfo.standard,
            model: mInfo.model,
            unitPrice: mInfo.commodityDecimal,
            commodityDecimal: mInfo.commodityDecimal,
            operNumber: 1,
            skuCode: mInfo.skuCode,
            sku: mInfo.sku,
            barCode: mInfo.barCode,
            depotId: mInfo.depotId,
            stock: mInfo.stock,
            unit: mInfo.unit,
            // 卡片展示用：图片与规格明细（与商品信息页一致）
            imgName: mInfo.imgName,
            attribute: mInfo.attribute,
            specValues: mInfo.specValues,
            specJson: mInfo.specJson,
            specSummary: mInfo.specSummary,
            retailPrice: mInfo.retailPrice
          })
        }
      },
      // 识别成功后延时退出扫码页面
      scheduleScanClose (delay) {
        if (this.successTimer) clearTimeout(this.successTimer)
        this.successTimer = setTimeout(() => {
          this.successTimer = null
          this.closeScanner()
        }, delay)
      },
      // 打开商品选择页面（共用页面）
      openMaterialSelect (barCode) {
        this.scanBarCode = barCode || ''
        this.materialVisible = true
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
        getAction('/sequence/buildNumber').then(res => {
          if (res && res.code === 200) {
            this.billForm.number = this.prefixNo + res.data.defaultNumber
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
  // 给底部固定金额栏留出空间
  padding-bottom: 92px;
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
// 实收输入框：与其它数据同高（24px）、同字号，保证标题与数据上下对齐；inputmode 拉起纯数字键盘
.rso-bottom-pay-input {
  width: 88px;
  height: 24px;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  box-shadow: none;
  font-weight: 600;
  line-height: 24px;
  text-align: center;
  &:focus {
    border: none;
    box-shadow: none;
  }
}
// 结算底栏指标：三个指标在底栏空白处均分，标题底边对齐、数据顶边对齐且与标题居中对齐
.rso-settle-metrics {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: stretch;
  .rso-bottom-item {
    flex: 1 1 0;
    align-items: center;
  }
}
// 扫码入口：固定在底栏中间，直径与底栏高度一致
.rso-bottom-scan {
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  transform: translateX(-50%);
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  width: auto;
  height: auto;
  aspect-ratio: 1;
  border-radius: 50%;
  background: #1890ff;
  color: #fff;
  font-size: 26px;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.35);
  cursor: pointer;
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
  .rso-settle-header {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    height: 49px;
    background: #1890ff;
    color: #fff;
    box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  }
  .rso-settle-back {
    padding: 0 14px;
    height: 49px;
    line-height: 49px;
    font-size: 22px;
    color: #fff;
  }
  .rso-settle-title {
    padding-left: 2px;
    font-size: 16px;
    color: #fff;
  }
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
/* 全屏扫码界面样式 */
.rso-scan-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: #000;
  overflow: hidden;
  /* 扫码舞台：视频 + 隐藏解码画布 + 识别框绘制层 */
  .rso-scan-stage {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    overflow: hidden;
    background: #000;
    z-index: 1;
  }
  .rso-scan-video {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #000;
  }
  /* 显示层：只负责绘制取景框，不拦截点击（点击由 stage 处理） */
  .rso-scan-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    pointer-events: none;
    z-index: 2;
  }
  .rso-scan-close {
    position: absolute;
    top: 12px;
    right: 16px;
    z-index: 3;
    color: #fff;
    font-size: 24px;
  }
  .rso-scan-tip {
    position: absolute;
    top: 60px;
    left: 50%;
    transform: translateX(-50%);
    max-width: 86%;
    z-index: 2;
    padding: 6px 12px;
    border-radius: 6px;
    background: rgba(0, 0, 0, 0.45);
    text-align: center;
    font-size: 14px;
    line-height: 20px;
    color: #fff;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.6);
    word-break: break-all;
    &.rso-scan-tip-success {
      color: #52c41a;
      font-weight: 600;
    }
    &.rso-scan-tip-error {
      color: #ff7875;
      font-weight: 600;
    }
  }
  /* 焦距（变焦）调节：取景框右下角，圆形按钮 + 当前倍数 */
  .rso-scan-zoom {
    position: absolute;
    right: 12px;
    bottom: 170px;
    z-index: 2;
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 4px 8px;
    border-radius: 20px;
    background: rgba(0, 0, 0, 0.45);
    .rso-scan-zoom-value {
      min-width: 36px;
      text-align: center;
      font-size: 12px;
      color: #fff;
    }
  }
  /* 手动输入条码搜索：取景框下方，输入时自动上移避开软键盘 */
  .rso-scan-manual {
    position: absolute;
    left: 50%;
    bottom: 210px;
    transform: translateX(-50%);
    z-index: 3;
    display: flex;
    align-items: center;
    width: 86%;
    gap: 8px;
    transition: bottom 0.2s;
    .rso-scan-manual-input {
      flex: 1;
      height: 36px !important;
      padding: 0 10px !important;
      border: 1px solid rgba(255, 255, 255, 0.6) !important;
      border-radius: 6px !important;
      background: #fff !important;
      box-shadow: none !important;
      color: #333;
    }
    &.rso-scan-manual-focused {
      bottom: 45%;
    }
  }
  /* 拍照识别按钮：页面底部居中 */
  .rso-scan-capture {
    position: absolute;
    bottom: 96px;
    left: 50%;
    width: 60%;
    transform: translateX(-50%);
    z-index: 3;
  }
}
</style>
