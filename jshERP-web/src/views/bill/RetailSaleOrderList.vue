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
          <div class="rso-cart-item-info">
            <div class="rso-cart-item-name">{{ item.name }}</div>
            <div class="rso-cart-item-spec">{{ item.standard }} / {{ item.model }}</div>
            <div class="rso-cart-item-price">¥{{ item.unitPrice || item.commodityDecimal }}</div>
          </div>
          <div class="rso-cart-item-qty">
            <a-button size="small" icon="minus" @click="changeQty(index, -1)" />
            <span class="rso-cart-qty-num">{{ item.operNumber }}</span>
            <a-button size="small" icon="plus" @click="changeQty(index, 1)" />
          </div>
          <a-icon type="close" class="rso-cart-item-del" @click="removeCartItem(index)" />
        </div>
      </div>
      <div v-if="cartList.length > 0" class="rso-cart-footer">
        <span>合计：<em class="rso-cart-total">¥{{ cartTotal }}</em></span>
        <a-button type="primary" @click="submitOrder">提交订单</a-button>
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
          <span class="rso-billinfo-label">会员卡号</span>
          <a-select v-model="billForm.organId" placeholder="请选择会员卡号" allowClear showSearch
                    optionFilterProp="children" class="rso-billinfo-field">
            <a-select-option v-for="item in retailList" :key="item.id" :value="item.id">{{ item.supplier }}</a-select-option>
          </a-select>
        </div>
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
      </div>
      <div class="rso-settle-footer">
        <a-button @click="settleVisible = false">关闭</a-button>
        <a-button type="primary" @click="submitOrder">提交订单</a-button>
      </div>
    </a-modal>

    <!-- 全屏扫码界面 -->
    <div v-if="scanVisible" class="rso-scan-fullscreen">
      <div class="rso-scan-stage" @click="onScanTap">
        <video ref="scanVideo" class="rso-scan-video" autoplay playsinline muted></video>
        <canvas ref="scanCanvas" class="rso-scan-canvas"></canvas>
        <canvas ref="scanOverlay" class="rso-scan-overlay"></canvas>
      </div>
      <a-icon type="close" class="rso-scan-close" @click="closeScanner" />
      <div class="rso-scan-tip">{{ scanTip }}</div>
      <div class="rso-scan-actions">
        <a-button @click="switchCamera">{{ frontCamera ? '切换后置' : '切换前置' }}</a-button>
        <a-button @click="showManualInput = !showManualInput">手动输入</a-button>
      </div>
      <div v-if="showManualInput" class="rso-manual-input">
        <a-input v-model="manualCode" placeholder="请输入条码或SKU码" @pressEnter="handleScanResult(manualCode)" />
        <a-button type="primary" @click="handleScanResult(manualCode)">确定</a-button>
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
      :bodyStyle="{ padding: 0 }"
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
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { BillListMixin } from './mixins/BillListMixin'
  import { getMaterialByBarCode } from '@/api/api'
  import { getAction, postAction } from '@/api/manage'
  import { getMpListShort } from '@/utils/util'
  import DataTable from '@/components/DataTable'
  import Vue from 'vue'
  import moment from 'moment'
  import { getFormatDate } from '@/utils/util'

  export default {
    name: "RetailSaleOrderList",
    mixins: [JeecgListMixin, BillListMixin],
    components: {
      RetailOutModal,
      BillDetail,
      MaterialSelectPage,
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
        // 扫码相关
        scanVisible: false,
        showManualInput: false,
        manualCode: '',
        frontCamera: false,
        // 识别定时器与视频流
        scanTimer: null,
        scanStream: null,
        // 防止上一帧解码未结束时重复进入
        decoding: false,
        // 拍照模式下识别出的照片（绘制在识别层，供多码框选）
        photoImage: null,
        // 各码值已连续命中的帧数（用于判断“唯一码稳定出现”）
        codeHits: {},
        // 是否已进入多码框选模式（出现过两个以上不同码值后锁定）
        multiScanMode: false,
        // 本帧识别到的码：[{ code, box }]
        detectedCodes: [],
        // 多码模式下绘制到屏幕的框（屏幕坐标，用于点击命中判断）
        screenBoxes: [],
        // 解码帧尺寸（识别框坐标以该尺寸为准）
        frameW: 0,
        frameH: 0,
        // 唯一码连续命中多少帧后自动加载
        autoConfirmFrames: 3,
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
      // 找零 = 收款金额 - 单据金额（不足时为 0）
      changeAmount () {
        const diff = (this.billForm.payAmount - 0) - (this.cartTotal - 0)
        return diff > 0 ? diff.toFixed(2) : '0.00'
      },
      // 扫码提示语：多码模式提示点击方框
      scanTip () {
        if (this.multiScanMode) {
          return `画面中有 ${this.detectedCodes.length} 个码，请点击要使用的方框`
        }
        return '将条码/二维码对准画面，画面中只有一个码时会自动加载'
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
    },
    methods: {
      // 打开全屏扫码界面并立即启动摄像头
      openScanner () {
        this.scanVisible = true
        this.showManualInput = false
        this.manualCode = ''
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
      // 启动摄像头：每 250ms 解码一帧；唯一码稳定出现自动加载，多码进入框选
      startScan () {
        if (!this.cameraAvailable()) {
          this.captureByCamera()
          return
        }
        this.stopScan()
        this.detectedCodes = []
        this.screenBoxes = []
        this.codeHits = {}
        this.multiScanMode = false
        const constraints = {
          video: {
            facingMode: this.frontCamera ? 'user' : 'environment',
            width: { ideal: 1280 },
            height: { ideal: 720 }
          }
        }
        navigator.mediaDevices.getUserMedia(constraints).then(stream => {
          this.scanStream = stream
          const video = this.$refs.scanVideo
          if (!video) return
          video.srcObject = stream
          video.play()
          // 服务端识别每帧有网络+解码耗时，帧间隔放宽到 400ms
          this.scanTimer = setInterval(() => this.decodeFrame(), 400)
        }).catch(err => {
          console.error('摄像头开启失败:', err)
          this.$message.warning('无法访问摄像头，请检查权限或使用手动输入')
          this.showManualInput = true
        })
      },
      // 原生条码检测器已废弃：识别统一交给服务端 ZXing
      initBarcodeDetector () {},
      // 解码当前视频帧：抽帧发给服务端 ZXing 识别（识别率/多码支持远好于浏览器内 JS 库）
      decodeFrame () {
        const video = this.$refs.scanVideo
        const canvas = this.$refs.scanCanvas
        if (!this.scanVisible || !video || !canvas || this.decoding) return
        if (video.readyState < 2 || !video.videoWidth) return
        const vw = video.videoWidth
        const vh = video.videoHeight
        // 限制帧尺寸：服务端识别耗时与像素数正相关
        const targetW = Math.min(960, vw)
        const scale = targetW / vw
        const fw = targetW
        const fh = Math.round(vh * scale)
        if (canvas.width !== fw) canvas.width = fw
        if (canvas.height !== fh) canvas.height = fh
        const ctx = canvas.getContext('2d')
        ctx.drawImage(video, 0, 0, fw, fh)
        const base64 = canvas.toDataURL('image/jpeg', 0.7)
        this.decoding = true
        this.frameW = fw
        this.frameH = fh
        postAction('/material/scanFrame', { image: base64 }).then(res => {
          this.decoding = false
          if (!this.scanVisible) return
          const list = (res && res.data && res.data.codes) || []
          this.onFrameDecoded(list.map(c => ({
            code: c.code,
            box: c.w ? { x: c.x, y: c.y, w: c.w, h: c.h } : null
          })))
        }).catch(() => {
          this.decoding = false
        })
      },
      // 每帧识别结果处理：去重 → 多码判定 → 唯一码连续命中后自动加载
      onFrameDecoded (list) {
        if (!this.scanVisible) return
        // 按码值去重（同一商品贴多张相同条码仍视为唯一）
        const unique = []
        const seen = {}
        list.forEach(item => {
          if (!item.code || seen[item.code]) return
          seen[item.code] = true
          unique.push(item)
        })
        this.detectedCodes = unique
        if (unique.length >= 2) {
          // 出现两个以上不同码值：锁定多码模式，等待点击框选
          this.multiScanMode = true
        }
        const hits = {}
        unique.forEach(item => {
          hits[item.code] = (this.codeHits[item.code] || 0) + 1
        })
        this.codeHits = hits
        this.drawScanOverlay()
        if (this.multiScanMode) return
        // 唯一码连续命中达到阈值 → 自动加载
        if (unique.length === 1 && hits[unique[0].code] >= this.autoConfirmFrames) {
          const code = unique[0].code
          if (navigator.vibrate) navigator.vibrate(80)
          this.closeScanner()
          this.handleScanResult(code)
        }
      },
      // 绘制识别框：视频坐标 → 屏幕坐标（按 object-fit: cover 换算）
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
        const scale = Math.max(elW / fw, elH / fh)
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
          this.screenBoxes.push({ code: item.code, x, y, w, h })
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
            this.closeScanner()
            this.handleScanResult(this.detectedCodes[0].code)
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
        this.closeScanner()
        this.handleScanResult(hit.code)
      },
      // 原生相机拍照识别：创建隐藏的 file 输入并触发（iOS http 环境下的降级方案）
      captureByCamera () {
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
        const reader = new FileReader()
        reader.onload = () => {
          const base64 = reader.result
          postAction('/material/scanFrame', { image: base64 }).then(res => {
            const codes = (res && res.data && res.data.codes) || []
            if (codes.length === 0) {
              this.$message.warning('未识别到条码，请重试或手动输入')
              this.showManualInput = true
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
              this.handleScanResult(unique[0].code)
              return
            }
            // 多个码：把照片画到识别层并框选
            this.showPhotoForPick(base64, unique)
          }).catch(() => {
            this.$message.warning('识别服务异常，请重试')
            this.showManualInput = true
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
          this.multiScanMode = true
          this.detectedCodes = codes.map(c => ({
            code: c.code,
            box: c.w ? { x: c.x, y: c.y, w: c.w, h: c.h } : null
          }))
          this.$nextTick(() => this.drawScanOverlay())
        }
        img.src = base64
      },
      // 切换前后摄像头
      switchCamera () {
        this.frontCamera = !this.frontCamera
        this.stopScan()
        this.startScan()
      },
      // 停止扫码：清理定时器、视频流与识别状态
      stopScan () {
        if (this.scanTimer) {
          clearInterval(this.scanTimer)
          this.scanTimer = null
        }
        this.decoding = false
        if (this.scanStream) {
          this.scanStream.getTracks().forEach(track => track.stop())
          this.scanStream = null
        }
        const video = this.$refs.scanVideo
        if (video) video.srcObject = null
        const overlay = this.$refs.scanOverlay
        if (overlay) {
          const ctx = overlay.getContext('2d')
          ctx.clearRect(0, 0, overlay.width, overlay.height)
        }
        this.detectedCodes = []
        this.screenBoxes = []
        this.codeHits = {}
        this.photoImage = null
      },
      closeScanner () {
        this.stopScan()
        this.scanVisible = false
      },
      /**
       * 处理扫码结果：按位数分析
       * - 商品码（标准条码：EAN-8 8位 / EAN-13 13位，纯数字）→ 打开商品页面加载该商品码下所有商品
       * - SKU码（其他格式）→ 直接添加到欲售商品列表
       */
      handleScanResult (code) {
        if (!code) return
        code = String(code).trim()
        this.stopScan()
        // 位数分析：纯数字且长度为 8 或 13 → 商品码
        const isProductCode = /^\d+$/.test(code) && (code.length === 8 || code.length === 13)
        if (isProductCode) {
          this.$message.info('识别为商品码，正在加载商品列表...')
          this.scanVisible = false
          this.openMaterialSelect(code)
        } else {
          // SKU码 → 直接查询并添加到欲售商品
          this.addSkuToCart(code)
        }
      },
      // 按SKU码查询商品并加入欲售商品列表
      addSkuToCart (skuCode) {
        const param = {
          skuCode: skuCode,
          organId: this.queryParam.organId,
          mpList: getMpListShort(Vue.ls.get('materialPropertyList')),
          prefixNo: this.prefixNo
        }
        getMaterialByBarCode(param).then(res => {
          if (!(res && res.code === 200 && res.data && res.data.length > 0)) {
            this.$message.warning('未找到SKU码为「' + skuCode + '」的商品')
            this.scanVisible = true
            // 拍照识别模式下不自动重开相机，改为展示手动输入
            if (this.cameraAvailable()) {
              this.startScan()
            } else {
              this.showManualInput = true
            }
            return
          }
          const mInfo = res.data[0]
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
              unit: mInfo.unit
            })
          }
          this.$message.success('已添加：' + mInfo.name)
          this.closeScanner()
        })
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
              unit: item.unit
            })
          }
        })
        if (items.length > 0) {
          this.$message.success('已添加 ' + items.length + ' 个商品')
        }
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
    align-items: center;
    padding: 10px 12px;
    border-bottom: 1px solid #f5f5f5;
    .rso-cart-item-info {
      flex: 1;
      .rso-cart-item-name {
        font-size: 14px;
        font-weight: 500;
      }
      .rso-cart-item-spec {
        font-size: 12px;
        color: #999;
        margin-top: 2px;
      }
      .rso-cart-item-price {
        font-size: 13px;
        color: #ff4d4f;
        margin-top: 2px;
      }
    }
    .rso-cart-item-qty {
      display: flex;
      align-items: center;
      margin: 0 10px;
      .rso-cart-qty-num {
        display: inline-block;
        min-width: 28px;
        text-align: center;
        font-size: 14px;
      }
    }
    .rso-cart-item-del {
      color: #ccc;
      font-size: 16px;
      cursor: pointer;
    }
  }
  .rso-cart-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px;
    border-top: 1px solid #f0f0f0;
    font-size: 14px;
    .rso-cart-total {
      color: #ff4d4f;
      font-size: 18px;
      font-style: normal;
      font-weight: 600;
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
    object-fit: cover;
    background: #000;
  }
  .rso-scan-canvas {
    display: none;
  }
  /* 识别框层：只负责绘制，不拦截点击（点击由 stage 处理） */
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
    left: 0;
    width: 100%;
    z-index: 2;
    text-align: center;
    font-size: 14px;
    color: #fff;
    text-shadow: 0 1px 2px rgba(0, 0, 0, 0.6);
  }
  .rso-scan-actions {
    position: absolute;
    bottom: 96px;
    left: 0;
    width: 100%;
    z-index: 2;
    display: flex;
    justify-content: center;
    gap: 16px;
  }
  .rso-manual-input {
    position: absolute;
    bottom: 24px;
    left: 0;
    width: 100%;
    z-index: 2;
    display: flex;
    gap: 8px;
    padding: 0 12px;
  }
}
</style>
