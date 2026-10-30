<template>
  <div class="retail-sale-order">
    <!-- 顶部标题栏 -->
    <div class="rso-header">
      <span class="rso-title">销售开单</span>
    </div>
    <!-- 分割线 -->
    <a-divider class="rso-divider" />
    <!-- 单据信息（与电脑端业务字段保持一致，单列对齐） -->
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

    <!-- 底部金额栏（单据金额 / 收款金额 / 找零，一行置顶显示） -->
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
    </div>

    <!-- 全屏扫码界面 -->
    <div v-if="scanVisible" class="rso-scan-fullscreen">
      <video ref="scanVideo" class="rso-scan-video" autoplay playsinline muted></video>
      <canvas ref="scanCanvas" class="rso-scan-canvas"></canvas>
      <canvas ref="scanOverlay" class="rso-scan-overlay" @click="confirmDetectedCode"></canvas>
      <a-icon type="close" class="rso-scan-close" @click="closeScanner" />
      <div class="rso-scan-tip">{{ detectedCode ? '已识别，点击绿色方框使用' : '将条码/二维码对准画面，识别后点击方框' }}</div>
      <div v-if="detectedCode" class="rso-scan-detected">
        <span class="rso-scan-code">{{ detectedCode }}</span>
        <a-button type="primary" size="small" @click.stop="confirmDetectedCode">使用此条码</a-button>
      </div>
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
  import { getAction } from '@/api/manage'
  import { getMpListShort } from '@/utils/util'
  import DataTable from '@/components/DataTable'
  import { BrowserMultiFormatReader } from '@zxing/library'
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
        scanStream: null,
        scanInterval: null,
        barcodeDetector: null,
        // 已识别但未确认的条码：框选位置（视频像素坐标）与内容
        detectedCode: '',
        detectedBox: null,
        // ZXing 解码器（浏览器不支持 BarcodeDetector 时使用）
        zxingReader: null,
        // 商品选择
        materialVisible: false,
        scanBarCode: ''
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
      // 初始化 BarcodeDetector（如浏览器支持）
      if (window.BarcodeDetector) {
        this.barcodeDetector = new window.BarcodeDetector({
          formats: ['code_128', 'code_39', 'code_93', 'codabar', 'ean_13', 'ean_8', 'itf', 'upc_a', 'upc_e', 'qr_code']
        })
      }
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
        this.detectedCode = ''
        this.detectedBox = null
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
      // 启动摄像头扫码
      startScan () {
        if (!this.cameraAvailable()) {
          this.captureByCamera()
          return
        }
        const constraints = {
          video: { facingMode: this.frontCamera ? 'user' : 'environment' }
        }
        navigator.mediaDevices.getUserMedia(constraints).then(stream => {
          this.scanStream = stream
          const video = this.$refs.scanVideo
          if (video) {
            video.srcObject = stream
            video.play()
            // Chrome/Android 使用原生 BarcodeDetector，其余浏览器使用 ZXing
            if (this.barcodeDetector) {
              this.startDetectLoop()
            } else {
              this.startZxingLoop(video)
            }
          }
        }).catch(err => {
          console.error('摄像头开启失败:', err)
          this.$message.warning('无法访问摄像头，请检查权限或使用手动输入')
          this.showManualInput = true
        })
      },
      // 使用 ZXing 对同一个视频流做识别（iPhone Safari 等不支持 BarcodeDetector 的浏览器）
      startZxingLoop (video) {
        if (!this.zxingReader) {
          this.zxingReader = new BrowserMultiFormatReader()
        }
        this.zxingReader.decodeFromStream(this.scanStream, video, (result) => {
          if (!result) return
          // 识别到条码：只框选，不自动确认；用户点击方框后再处理
          const points = (result.getResultPoints && result.getResultPoints()) || []
          let box = null
          if (points.length > 0) {
            const xs = points.map(p => p.getX())
            const ys = points.map(p => p.getY())
            box = {
              x: Math.min.apply(null, xs),
              y: Math.min.apply(null, ys),
              w: Math.max.apply(null, xs) - Math.min.apply(null, xs),
              h: Math.max.apply(null, ys) - Math.min.apply(null, ys)
            }
          }
          this.detectedCode = result.getText()
          this.detectedBox = box
          this.drawOverlay(video.videoWidth, video.videoHeight)
        }).catch(err => {
          console.error('ZXing 扫码失败:', err)
        })
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
      // 解码拍照得到的条码图片
      decodeBarcodeImage (file) {
        const url = URL.createObjectURL(file)
        if (!this.zxingReader) {
          this.zxingReader = new BrowserMultiFormatReader()
        }
        this.zxingReader.decodeFromImageUrl(url).then(result => {
          URL.revokeObjectURL(url)
          if (result) {
            this.handleScanResult(result.getText())
          }
        }).catch(() => {
          URL.revokeObjectURL(url)
          this.$message.warning('未识别到条码，请重试或手动输入')
          this.showManualInput = true
        })
      },
      // 扫码识别循环
      startDetectLoop () {
        const video = this.$refs.scanVideo
        const canvas = this.$refs.scanCanvas
        if (!video || !canvas) return
        const ctx = canvas.getContext('2d')
        const detect = () => {
          if (!this.scanVisible) return
          if (video.readyState === video.HAVE_ENOUGH_DATA) {
            canvas.width = video.videoWidth
            canvas.height = video.videoHeight
            ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
            if (this.barcodeDetector) {
              this.barcodeDetector.detect(canvas).then(results => {
                if (results && results.length > 0) {
                  const r = results[0]
                  // 识别到条码：只记录内容与位置，等用户点击框选区域后确认
                  this.detectedCode = r.rawValue
                  const bb = r.boundingBox
                  this.detectedBox = bb ? { x: bb.x, y: bb.y, w: bb.width, h: bb.height } : null
                  this.drawOverlay(canvas.width, canvas.height)
                }
              }).catch(() => {})
            }
          }
          this.scanInterval = requestAnimationFrame(detect)
        }
        detect()
      },
      // 在视频上绘制识别结果的选框（视频像素坐标 → 屏幕显示坐标）
      drawOverlay (videoW, videoH) {
        const video = this.$refs.scanVideo
        const overlay = this.$refs.scanOverlay
        if (!video || !overlay || !videoW || !videoH) return
        const elW = video.clientWidth
        const elH = video.clientHeight
        if (overlay.width !== elW) overlay.width = elW
        if (overlay.height !== elH) overlay.height = elH
        const ctx = overlay.getContext('2d')
        ctx.clearRect(0, 0, elW, elH)
        const box = this.detectedBox
        if (!box) return
        // 视频使用 object-fit: contain，按等比缩放 + 居中偏移换算
        const scale = Math.min(elW / videoW, elH / videoH)
        const offX = (elW - videoW * scale) / 2
        const offY = (elH - videoH * scale) / 2
        const x = offX + box.x * scale
        const y = offY + box.y * scale
        const w = Math.max(box.w * scale, 24)
        const h = Math.max(box.h * scale, 24)
        ctx.fillStyle = 'rgba(82, 196, 26, 0.18)'
        ctx.fillRect(x, y, w, h)
        ctx.strokeStyle = '#52c41a'
        ctx.lineWidth = 3
        ctx.strokeRect(x, y, w, h)
      },
      // 点击识别框确认：退出相机并按该条码搜索商品
      confirmDetectedCode () {
        if (!this.detectedCode) return
        const code = this.detectedCode
        this.closeScanner()
        this.handleScanResult(code)
      },
      // 切换前后摄像头
      switchCamera () {
        this.frontCamera = !this.frontCamera
        this.detectedCode = ''
        this.detectedBox = null
        this.stopScan()
        this.startScan()
      },
      // 停止扫码：动画循环、ZXing 解码循环与视频轨道都要释放
      stopScan () {
        if (this.scanInterval) {
          cancelAnimationFrame(this.scanInterval)
          this.scanInterval = null
        }
        if (this.zxingReader) {
          this.zxingReader.reset()
          this.zxingReader = null
        }
        if (this.scanStream) {
          this.scanStream.getTracks().forEach(track => track.stop())
          this.scanStream = null
        }
        if (this.$refs.scanVideo) {
          this.$refs.scanVideo.srcObject = null
        }
      },
      closeScanner () {
        this.stopScan()
        this.detectedCode = ''
        this.detectedBox = null
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
      // 提交订单：复用零售出库弹窗逻辑
      submitOrder () {
        if (this.cartList.length === 0) {
          this.$message.warning('请先添加商品')
          return
        }
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
// 单据信息：单列对齐
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
}
.rso-header {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  background: #fff;
  border-bottom: 1px solid #eee;
  .rso-title {
    font-size: 17px;
    font-weight: 600;
  }
}
.rso-divider {
  margin: 0;
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
  .rso-scan-video {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #000;
  }
  .rso-scan-canvas {
    display: none;
  }
  /* 识别框选层：占满屏幕，点击方框确认条码 */
  .rso-scan-overlay {
    position: absolute;
    top: 0;
    left: 0;
    width: 100%;
    height: 100%;
    z-index: 2;
  }
  .rso-scan-detected {
    position: absolute;
    bottom: 160px;
    left: 0;
    width: 100%;
    z-index: 3;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 0 16px;
    .rso-scan-code {
      max-width: 60%;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
      color: #52c41a;
      background: rgba(0, 0, 0, 0.6);
      padding: 4px 8px;
      border-radius: 4px;
    }
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
