<template>
  <div class="retail-sale-order">
    <!-- 顶部标题栏 -->
    <div class="rso-header">
      <span class="rso-title">销售开单</span>
    </div>
    <!-- 分割线 -->
    <a-divider class="rso-divider" />
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

    <!-- 历史单据列表（保留原业务逻辑，方便核对） -->
    <div class="rso-history">
      <div class="rso-history-title">历史单据</div>
      <a-form layout="inline" class="rso-search" @keyup.enter.native="searchQuery">
        <a-input placeholder="单据编号/商品信息" v-model="queryParam.number" style="width: 60%" />
        <a-button type="primary" @click="searchQuery">查询</a-button>
      </a-form>
      <data-table
        ref="table"
        bordered
        size="small"
        row-key="id"
        table-key="bill_retail_sale_order"
        :extra-columns="defColumns"
        :default-columns="defDataIndex"
        storage-key="columns_LSCK"
        :data-source="dataSource"
        :pagination="ipagination"
        :loading="loading"
        @change="handleTableChange">
        <span slot="action" slot-scope="text, record">
          <a @click="myHandleDetail(record, '零售出库', prefixNo)">查看</a>
        </span>
        <template slot="customRenderStatus" slot-scope="status">
          <a-tag v-if="status == '0'" color="red">未审核</a-tag>
          <a-tag v-if="status == '1'" color="green">已审核</a-tag>
        </template>
      </data-table>
    </div>

    <!-- 扫码弹窗 -->
    <a-modal
      :visible="scanVisible"
      title="扫码添加"
      @cancel="closeScanner"
      :footer="null"
      width="90%"
      centered
      destroyOnClose
    >
      <div class="rso-scan-box">
        <video ref="scanVideo" class="rso-scan-video" autoplay playsinline muted></video>
        <canvas ref="scanCanvas" class="rso-scan-canvas"></canvas>
        <div class="rso-scan-tip">将条码/二维码对准框内即可自动识别</div>
        <div class="rso-scan-actions">
          <a-button @click="switchCamera">{{ frontCamera ? '切换后置' : '切换前置' }}</a-button>
          <a-button @click="showManualInput = !showManualInput">手动输入</a-button>
        </div>
        <div v-if="showManualInput" class="rso-manual-input">
          <a-input v-model="manualCode" placeholder="请输入条码或SKU码" @pressEnter="handleScanResult(manualCode)" />
          <a-button type="primary" @click="handleScanResult(manualCode)">确定</a-button>
        </div>
      </div>
    </a-modal>

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
      <material-select-page @back="materialVisible = false" @select="handleMaterialSelect" />
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
        // 扫码相关
        scanVisible: false,
        showManualInput: false,
        manualCode: '',
        frontCamera: false,
        scanStream: null,
        scanInterval: null,
        barcodeDetector: null,
        // 商品选择
        materialVisible: false
      }
    },
    computed: {
      cartTotal () {
        return this.cartList.reduce((sum, item) => {
          const price = item.unitPrice - 0 || item.commodityDecimal - 0 || 0
          return sum + price * (item.operNumber - 0 || 1)
        }, 0).toFixed(2)
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
      // 打开扫码弹窗并启动摄像头
      openScanner () {
        this.scanVisible = true
        this.showManualInput = false
        this.manualCode = ''
        this.$nextTick(() => {
          this.startScan()
        })
      },
      // 启动摄像头扫码
      startScan () {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          this.$message.warning('当前浏览器不支持摄像头扫码，请使用手动输入')
          this.showManualInput = true
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
            this.startDetectLoop()
          }
        }).catch(err => {
          console.error('摄像头开启失败:', err)
          this.$message.warning('无法访问摄像头，请检查权限或使用手动输入')
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
                  const code = results[0].rawValue
                  this.handleScanResult(code)
                }
              }).catch(() => {})
            }
          }
          this.scanInterval = requestAnimationFrame(detect)
        }
        detect()
      },
      // 切换前后摄像头
      switchCamera () {
        this.frontCamera = !this.frontCamera
        this.stopScan()
        this.startScan()
      },
      // 停止扫码
      stopScan () {
        if (this.scanInterval) {
          cancelAnimationFrame(this.scanInterval)
          this.scanInterval = null
        }
        if (this.scanStream) {
          this.scanStream.getTracks().forEach(track => track.stop())
          this.scanStream = null
        }
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
            this.startScan()
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
        this.materialVisible = true
        // 后续完善：传递 barCode 参数到商品页面进行筛选
      },
      // 商品页面选择商品回调
      handleMaterialSelect (item) {
        const exist = this.cartList.find(r => String(r.meId) === String(item.meId))
        if (exist) {
          exist.operNumber = (exist.operNumber - 0) + 1
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
            operNumber: 1,
            skuCode: item.skuCode,
            barCode: item.barCode,
            unit: item.unit
          })
        }
        this.$message.success('已添加：' + item.name)
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
  padding-bottom: 20px;
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
/* 扫码弹窗样式 */
.rso-scan-box {
  text-align: center;
  .rso-scan-video {
    width: 100%;
    max-height: 50vh;
    background: #000;
    border-radius: 6px;
  }
  .rso-scan-canvas {
    display: none;
  }
  .rso-scan-tip {
    margin: 12px 0;
    font-size: 13px;
    color: #666;
  }
  .rso-scan-actions {
    display: flex;
    justify-content: center;
    gap: 10px;
    margin-bottom: 12px;
  }
  .rso-manual-input {
    display: flex;
    gap: 8px;
    padding: 0 10px;
  }
}
</style>
