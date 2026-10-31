<template>
  <div class="retail-sale-order">
    <!-- 操作按钮区：扫码添加 + 选择商品 -->
    <div class="rso-actions">
      <a-button type="primary" icon="scan" size="large" block @click="openScanner">扫码添加</a-button>
      <a-button icon="appstore" size="large" block @click="openMaterialSelect">选择商品</a-button>
    </div>
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
            <a-icon type="close" class="rso-cart-item-del" @click="removeCartItem(index)" />
          </div>
          <!-- SKU码 / 库存 / 零售价：与商品信息页卡片保持一致 -->
          <div class="rso-cart-item-meta">
            <span class="rso-cart-item-sku">SKU码 {{ item.skuCode || '-' }}</span>
            <span>库存 {{ item.stock == null ? '-' : item.stock }}</span>
            <span>零售价 {{ cartPrice(item) }}</span>
          </div>
          <div class="rso-cart-item-qty">
            <a-button size="small" icon="minus" @click="changeQty(index, -1)" />
            <span class="rso-cart-qty-num">{{ item.operNumber }}</span>
            <a-button size="small" icon="plus" @click="changeQty(index, 1)" />
          </div>
        </div>
      </div>
    </div>
    <!-- 底部金额栏（单据金额 / 收款金额 / 找零 + 结算按钮，一行置顶显示） -->
    <div class="rso-bottom-bar">
      <div class="rso-bottom-item">
        <span class="rso-bottom-label">单据金额</span>
        <span class="rso-bottom-value">¥{{ cartTotal }}</span>
      </div>
      <div class="rso-bottom-item">
        <span class="rso-bottom-label">收款金额</span>
        <a-input-number v-model="billForm.payAmount" :min="0" :precision="2" :controls="false"
                        size="small" class="rso-bottom-input" />
      </div>
      <div class="rso-bottom-item">
        <span class="rso-bottom-label">找零</span>
        <span class="rso-bottom-value">¥{{ changeAmount }}</span>
      </div>
      <div class="rso-bottom-settle">
        <a-button type="primary" @click="openSettle">结算</a-button>
      </div>
    </div>

    <!-- 结算弹窗：单据信息（与电脑端业务字段保持一致，单列对齐） -->
    <a-modal
      :visible="settleVisible"
      title="结算"
      @cancel="settleVisible = false"
      :footer="null"
      width="90%"
      centered
      :bodyStyle="{ padding: 0 }"
    >
      <div class="rso-billinfo">
        <div class="rso-billinfo-row">
          <span class="rso-billinfo-label">单据日期</span>
          <a-date-picker v-model="billForm.operTime" showTime format="YYYY-MM-DD HH:mm:ss"
                         class="rso-billinfo-field" />
        </div>
        <div class="rso-billinfo-row">
          <span class="rso-billinfo-label">单据编号</span>
          <a-input v-model="billForm.number" placeholder="请输入单据编号" class="rso-billinfo-field" />
        </div>
        <div class="rso-billinfo-row">
          <span class="rso-billinfo-label">收款类型</span>
          <a-select v-model="billForm.payType" class="rso-billinfo-field">
            <a-select-option value="现付">现付</a-select-option>
            <a-select-option value="预付款">预付款</a-select-option>
          </a-select>
        </div>
        <!-- 会员卡号：点击输入框或下拉箭头加载会员列表；右侧按钮新增会员 -->
        <div class="rso-billinfo-row">
          <span class="rso-billinfo-label">会员卡号</span>
          <div class="rso-member-field">
            <a-input ref="memberInput" :value="memberDisplay" readOnly placeholder="请选择会员卡号"
                     class="rso-member-input" @click="openMemberPicker">
              <a-icon slot="suffix" type="down" class="rso-member-arrow" />
            </a-input>
            <a-button icon="user-add" class="rso-member-add" @click="addMember" />
          </div>
        </div>
      </div>
      <div class="rso-settle-footer">
        <a-button @click="settleVisible = false">关闭</a-button>
        <a-button type="primary" @click="submitOrder">提交订单</a-button>
      </div>
    </a-modal>

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
    <member-modal ref="memberModalForm" @ok="onMemberAdded"></member-modal>

    <!-- 全屏扫码界面 -->
    <div v-if="scanVisible" class="rso-scan-fullscreen">
      <div class="rso-scan-stage" @click="onScanTap">
        <video ref="scanVideo" class="rso-scan-video" autoplay playsinline muted></video>
        <canvas ref="scanOverlay" class="rso-scan-overlay"></canvas>
      </div>
      <a-icon type="close" class="rso-scan-close" @click="closeScanner" />
      <!-- 页面内文字提示：识别结果、商品是否存在、异常都在这里显示，不再弹框 -->
      <div class="rso-scan-tip" :class="scanTipClass">{{ scanTip }}</div>
      <!-- 拍照识别：只分析取景框内的图像 -->
      <div v-if="!multiScanMode" class="rso-scan-capture">
        <a-button type="primary" size="large" block :loading="capturing" @click="shoot">
          {{ photoMode ? '重新拍照' : '拍照识别' }}
        </a-button>
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
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { BillListMixin } from './mixins/BillListMixin'
  import { getAction, postAction } from '@/api/manage'
  import { findBySelectRetail } from '@/api/api'
  import { getFormatDate } from '@/utils/util'
  import DataTable from '@/components/DataTable'
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
      DataTable
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
          payType: '现付',
          payAmount: 0
        },
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
        // 非 HTTPS 环境使用系统相机拍照识别（保留多码框选）
        photoMode: false,
        // 拍照模式下识别出的照片（绘制在识别层，供多码框选）
        photoImage: null,
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
      // 会员输入框显示：卡号 + 姓名
      memberDisplay () {
        const m = this.selectedMember
        if (!m) return ''
        return m.contacts ? `${m.supplier} ${m.contacts}` : m.supplier
      },
      // 找零 = 收款金额 - 单据金额（不足时为 0）
      changeAmount () {
        const diff = (this.billForm.payAmount - 0) - (this.cartTotal - 0)
        return diff > 0 ? diff.toFixed(2) : '0.00'
      },
      // 扫码提示语：所有扫码反馈（识别结果、商品是否存在、异常）只在页面内文字提示
      scanTip () {
        if (this.multiScanMode) {
          return `画面中有 ${this.detectedCodes.length} 个码，请点击要使用的方框`
        }
        if (this.photoMode) {
          return '请使用系统相机拍摄条码/二维码'
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
        // 点击「扫码添加」即解锁音频，保证后续扫描反馈提示音可播放
        this.unlockAudio()
        this.scanResult = null
        this.scanError = ''
        this.scanState = SCANNER_STATE.INITIAL
        this.searching = false
        // iPhone 在 http 非安全源下无法调用 getUserMedia，直接打开系统相机拍照识别
        if (!this.cameraAvailable()) {
          this.captureByCamera()
          return
        }
        this.$nextTick(() => {
          this.startScan()
        })
      },
      // 是否可以使用网页摄像头（非安全源或缺少 mediaDevices 时不可用）
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
        if (!this.cameraAvailable()) {
          this.captureByCamera()
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
        this.photoImage = null
        this.photoMode = false
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
      // 拍照入口：网页摄像头走框内裁剪识别，系统相机模式重新拍照
      shoot () {
        // 拍照是用户点击动作，这里解锁音频，后续提示音才能正常播放
        this.unlockAudio()
        if (this.photoMode) {
          this.captureByCamera()
          return
        }
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
      // 摄像头或识别异常：停止摄像头并给出页内提示
      onScannerError (error) {
        const message = error && error.message ? error.message : '摄像头或识别异常'
        console.error('扫码异常:', error)
        this.scanError = message.indexOf('Permission') >= 0 || message.indexOf('NotAllowed') >= 0
          ? '无法访问摄像头，请检查浏览器摄像头权限'
          : message
        this.scanState = SCANNER_STATE.ERROR
        if (this.scanStream) {
          this.stopStream(this.scanStream)
          this.scanStream = null
        }
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
        // 拍照模式：先把照片铺到识别层（此时无视频流，画面为黑底）
        if (this.photoImage) {
          ctx.drawImage(this.photoImage, offX, offY, fw * scale, fh * scale)
        }
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
      // 原生相机拍照识别：创建隐藏的 file 输入并触发（iOS http 环境下的降级方案）
      captureByCamera () {
        this.photoMode = true
        this.scanState = SCANNER_STATE.SCANNING
        const input = document.createElement('input')
        input.type = 'file'
        input.accept = 'image/*'
        input.capture = 'environment'
        input.style.display = 'none'
        document.body.appendChild(input)
        input.addEventListener('change', () => {
          const file = input.files && input.files[0]
          document.body.removeChild(input)
          if (!file) return
          this.decodeBarcodeImage(file)
        })
        input.click()
      },
      // 解码拍照得到的图片：发给服务端识别；单码直接加载，多码在照片上框选
      decodeBarcodeImage (file) {
        this.scanState = SCANNER_STATE.RECOGNIZING
        const reader = new FileReader()
        reader.onload = () => {
          const base64 = reader.result
          postAction('/material/scanFrame', { image: base64 }).then(res => {
            const codes = (res && res.data && res.data.codes) || []
            if (codes.length === 0) {
              this.scanError = '条码未识别，请将条码完整放入框内后重试'
              this.scanState = SCANNER_STATE.ERROR
              this.playFeedbackSound(false)
              return
            }
            // 去重
            const seen = {}
            const unique = codes.filter(c => {
              if (!c.code || seen[c.code]) return false
              seen[c.code] = true
              return true
            })
            if (unique.length === 1) {
              this.handleScannedContent(unique[0].code, unique[0].format || 'UNKNOWN', unique[0].type || 'UNKNOWN')
              return
            }
            // 多个码：把照片画到识别层并框选
            this.showPhotoForPick(base64, unique)
          }).catch(error => {
            this.scanError = '识别服务异常：' + (error && error.message ? error.message : '请重试')
            this.scanState = SCANNER_STATE.ERROR
            this.playFeedbackSound(false)
          })
        }
        reader.readAsDataURL(file)
      },
      // 拍照识别出多个码时：照片铺满画面并画出每个码的方框，点框选择
      showPhotoForPick (base64, codes) {
        const img = new Image()
        img.onload = () => {
          this.frameW = img.naturalWidth
          this.frameH = img.naturalHeight
          this.photoImage = img
          this.photoMode = true
          this.multiScanMode = true
          this.scanState = SCANNER_STATE.RECOGNIZING
          this.detectedCodes = codes.map(c => ({
            code: c.code,
            format: c.format || 'UNKNOWN',
            type: c.type || 'UNKNOWN',
            box: c.w ? { x: c.x, y: c.y, w: c.w, h: c.h } : null
          }))
          this.$nextTick(() => this.drawScanOverlay())
        }
        img.src = base64
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
        this.photoImage = null
        this.photoMode = false
        if (nextState) {
          this.scanState = nextState
        }
      },
      closeScanner () {
        this.stopScan(SCANNER_STATE.STOPPED)
        this.scanVisible = false
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
            // 提示只停留在扫码页，2 秒后自动收起，继续扫下一件
            this.scheduleScanReset(2000)
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
      // 成功提示在扫码页停留一段时间后自动收起
      scheduleScanReset (delay) {
        if (this.successTimer) clearTimeout(this.successTimer)
        this.successTimer = setTimeout(() => {
          this.successTimer = null
          if (!this.scanVisible || this.scanState !== SCANNER_STATE.SUCCESS) return
          this.scanResult = null
          this.scanError = ''
          this.scanState = this.scanStream ? SCANNER_STATE.SCANNING : SCANNER_STATE.STOPPED
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
      // 新增会员保存后重新加载列表，便于立即选择
      onMemberAdded () {
        this.memberKeyword = ''
        this.loadMembers()
      },
      // 打开结算弹窗
      openSettle () {
        this.settleVisible = true
      },
      // 提交订单：复用零售出库弹窗逻辑
      submitOrder () {
        if (this.cartList.length === 0) {
          this.$message.warning('请先添加商品')
          return
        }
        this.settleVisible = false
        this.$refs.modalForm.action = "add"
        if (this.btnEnableList.indexOf(2) === -1) {
          this.$refs.modalForm.isCanCheck = false
        }
        this.$refs.modalForm.add()
        this.$refs.modalForm.title = "销售开单"
        this.$refs.modalForm.disableSubmit = false
        // 将手机端录入的单据信息带入弹窗
        this.$nextTick(() => {
          const form = this.$refs.modalForm && this.$refs.modalForm.form
          if (form) {
            form.setFieldsValue({
              organId: this.billForm.organId,
              operTime: this.billForm.operTime,
              number: this.billForm.number,
              payType: this.billForm.payType
            })
          }
        })
        // 后续完善：将 cartList 注入到弹窗明细
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
// 结算弹窗内的单据信息：单列对齐
.rso-billinfo {
  background: #fff;
  padding: 4px 12px;
  .rso-billinfo-row {
    display: flex;
    align-items: center;
    min-height: 46px;
    border-bottom: 1px solid #f0f0f0;
    &:last-child {
      border-bottom: none;
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
// 底部固定金额栏
.rso-bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: flex-start;
  padding: 8px 12px;
  padding-bottom: calc(8px + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1px solid #eee;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
  .rso-bottom-item {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
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
  .rso-bottom-input {
    width: 90%;
    margin-top: 2px;
    /deep/ .ant-input-number-input {
      text-align: center;
      font-size: 16px;
      font-weight: 600;
    }
  }
  // 结算按钮固定在金额栏右侧
  .rso-bottom-settle {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    align-self: stretch;
    padding-left: 8px;
    /deep/ .ant-btn {
      height: 100%;
      min-height: 40px;
      padding: 0 18px;
      font-size: 15px;
      font-weight: 600;
    }
  }
}
// 结算弹窗底部按钮
.rso-settle-footer {
  display: flex;
  gap: 10px;
  padding: 10px 12px;
  border-top: 1px solid #f0f0f0;
  /deep/ .ant-btn {
    flex: 1;
  }
}
.rso-actions {
  display: flex;
  gap: 10px;
  padding: 12px;
  background: #fff;
  /deep/ .ant-btn {
    flex: 1;
  }
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
    .rso-cart-item-qty {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      margin-top: 8px;
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
