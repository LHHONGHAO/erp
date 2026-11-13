<template>
  <div class="mmd">
    <div class="mmd-bar">
      <span class="mmd-bar__title">标准弹层页面预览</span>
      <a-select :value="currentKey" size="small" style="width: 180px" @change="showPage">
        <a-select-option v-for="p in pages" :key="p.key" :value="p.key">{{ p.label }}</a-select-option>
      </a-select>
    </div>

    <!-- transform 为 fixed 子元素创建包含块，使标准弹层限制在预览框内 -->
    <div class="mmd-frame">
      <mobile-modal-page
        :key="currentKey"
        :visible="true"
        :title="current.title"
        :subtitle="current.subtitle"
        @close="handleClose">
        <template v-if="current.actions" #actions>
          <a-icon type="edit" class="mmp-icon" title="编辑模板" @click="handleEdit" />
        </template>

        <!-- 地址打印 / 标签打印 -->
        <div v-if="isPaperPage" class="mmd-body">
          <div class="mmd-paper" :class="{ 'mmd-paper--small': currentKey === 'label' }">
            <div v-for="line in current.paper" :key="line">{{ line }}</div>
          </div>
          <div class="mmd-card mmd-mt">
            <div class="mmd-row">
              <span>设备状态</span>
              <span class="mmd-val">远程：标签打印机 ›</span>
            </div>
            <div v-if="currentKey === 'address'" class="mmd-row">
              <span>打印方向</span>
              <span class="mmd-val">
                <span class="mmd-seg">
                  <span v-for="r in rotations" :key="r" :class="{ on: rotation === r }" @click="rotation = r">{{ r }}</span>
                </span>
              </span>
            </div>
            <div class="mmd-row">
              <span>打印份数</span>
              <span class="mmd-val">
                <span class="mmd-qty">
                  <i @click="copies = Math.max(1, copies - 1)">−</i>
                  <b>{{ copies }} 份</b>
                  <i @click="copies = Math.min(99, copies + 1)">+</i>
                </span>
              </span>
            </div>
          </div>
        </div>

        <!-- 商品选择 -->
        <div v-else-if="currentKey === 'picker'" class="mmd-body">
          <div v-for="item in goods" :key="item.name" class="mmd-item">
            <div class="mmd-thumb">{{ item.short }}</div>
            <div class="mmd-item__main">
              <div class="mmd-name">{{ item.name }}</div>
              <div class="mmd-spec">{{ item.spec }}</div>
            </div>
            <div class="mmd-price">
              <b>¥{{ item.price }}</b>
              <span>库存 {{ item.stock }}</span>
            </div>
          </div>
        </div>

        <!-- 设备列表 -->
        <div v-else-if="currentKey === 'devices'" class="mmd-body">
          <div class="mmd-card mmd-card--pad">
            <div v-for="d in devices" :key="d.name" class="mmd-dev">
              <i class="mmd-dot" :class="{ off: !d.online }"></i>
              <div class="mmd-item__main">
                <div class="mmd-name">{{ d.name }}</div>
                <div class="mmd-spec">{{ d.desc }}</div>
              </div>
              <span class="mmd-meta">{{ d.state }}</span>
            </div>
          </div>
          <a-button block class="mmd-add-btn" icon="plus" @click="handleAddDevice">添加打印机</a-button>
        </div>

        <!-- 选择品牌 -->
        <div v-else-if="currentKey === 'brand'" class="mmd-body">
          <div class="mmd-card">
            <div v-for="b in brands" :key="b" class="mmd-row mmd-row--tap" @click="brand = b">
              <span>{{ b }}</span>
              <i class="mmd-radio" :class="{ on: brand === b }"></i>
            </div>
          </div>
        </div>

        <!-- 会员信息 -->
        <div v-else-if="currentKey === 'member'" class="mmd-body">
          <div class="mmd-member">
            <div class="mmd-avatar">张</div>
            <div class="mmd-item__main">
              <div class="mmd-name">
                {{ member.name }}<span class="mmd-tag">{{ member.level }}</span>
              </div>
              <div class="mmd-spec">{{ member.phone }}</div>
            </div>
          </div>
          <div class="mmd-card">
            <div class="mmd-row"><span>累计消费</span><span class="mmd-val">{{ member.total }}</span></div>
            <div class="mmd-row"><span>会员积分</span><span class="mmd-val">{{ member.points }}</span></div>
            <div class="mmd-row"><span>会员等级</span><span class="mmd-val">{{ member.level }}</span></div>
          </div>
        </div>

        <!-- 网络打印配置 / 新增品类 / 编辑设备 -->
        <div v-else class="mmd-body">
          <div class="mmd-card">
            <div v-for="row in formRows" :key="row.label" class="mmd-row">
              <span>{{ row.label }}</span>
              <span class="mmd-input">{{ row.value }}</span>
            </div>
          </div>
          <div v-if="currentKey === 'network'" class="mmd-tip">请确保手机与打印机在同一局域网</div>
        </div>

        <template v-if="current.footer !== 'none'" #footer>
          <div v-if="current.footer === 'print' || current.footer === 'save'" class="mmd-footer">
            <a-button type="primary" block size="large" @click="handlePrimary">
              {{ current.footer === 'print' ? '打印' : '保存' }}
            </a-button>
          </div>
          <div v-else-if="current.footer === 'picker'" class="mmd-footer mmd-footer--picker">
            <div class="mmd-selbar">
              <span>已选 <b>2</b> 个商品</span>
              <a-button type="primary" size="small" @click="handlePrimary">确定</a-button>
            </div>
            <mobile-bottom-bar :fixed="false" left-auto>
              <mobile-search-bar
                v-model="keyword"
                placeholder="搜索商品名称 / 条码"
                @search="handleSearch"
                @camera="handleCamera"
                @filter="handleFilter" />
            </mobile-bottom-bar>
          </div>
          <div v-else-if="current.footer === 'devices'" class="mmd-btabs">
            <div
              v-for="t in deviceTabs"
              :key="t.key"
              class="mmd-btab"
              :class="{ on: deviceTab === t.key }"
              @click="deviceTab = t.key">{{ t.label }}</div>
          </div>
        </template>
      </mobile-modal-page>
    </div>
  </div>
</template>

<script>
import MobileModalPage from '@/components/mobile/MobileModalPage'
import MobileBottomBar from '@/components/mobile/MobileBottomBar'
import MobileSearchBar from '@/components/mobile/MobileSearchBar'

/** 标准弹层页面测试配置：新增页面只需追加一项并补充对应内容插槽 */
const DEMO_PAGES = [
  {
    key: 'address', label: '地址打印', title: '地址打印', subtitle: '60mm × 40mm',
    actions: true, footer: 'print',
    paper: ['联系人：张三', '电话：138****8000', '地址：浙江省杭州市余杭区', '商品：纯棉T恤 ×2']
  },
  {
    key: 'label', label: '标签打印', title: '标签打印', subtitle: '70mm × 40mm',
    actions: true, footer: 'print',
    paper: ['会员标签', '商品：纯棉T恤', '规格：白色 / L', '价格：¥ 99.00']
  },
  { key: 'picker', label: '商品选择', title: '添加商品', subtitle: '', actions: false, footer: 'picker' },
  { key: 'devices', label: '设备列表', title: '设备列表', subtitle: '', actions: false, footer: 'devices' },
  { key: 'brand', label: '选择品牌', title: '选择品牌', subtitle: '', actions: false, footer: 'none' },
  { key: 'network', label: '网络打印配置', title: '网络打印配置', subtitle: '', actions: false, footer: 'save' },
  { key: 'member', label: '会员信息', title: '会员信息', subtitle: '', actions: false, footer: 'none' },
  { key: 'category', label: '新增品类', title: '新增品类', subtitle: '', actions: false, footer: 'save' },
  { key: 'deviceEdit', label: '编辑设备', title: '编辑设备', subtitle: '', actions: false, footer: 'save' }
]

const FORM_ROWS = {
  network: [
    { label: '打印机名称', value: '前台 WiFi 打印机' },
    { label: 'IP 地址', value: '192.168.1.20' },
    { label: '端口', value: '9100' }
  ],
  category: [
    { label: '品类名称', value: '夏季新品' },
    { label: '排序', value: '10' }
  ],
  deviceEdit: [
    { label: '设备名称', value: '仓库蓝牙打印机' },
    { label: '连接方式', value: '蓝牙' },
    { label: '设备地址', value: 'DC:1F:2A:8B:6C:90' }
  ]
}

export default {
  name: 'MobileModalDemo',
  components: { MobileModalPage, MobileBottomBar, MobileSearchBar },
  data() {
    return {
      pages: DEMO_PAGES,
      currentKey: 'address',
      rotation: '0°',
      rotations: ['0°', '90°', '180°'],
      copies: 1,
      keyword: '',
      deviceTab: 'cloud',
      deviceTabs: [
        { key: 'cloud', label: '云打印' },
        { key: 'wifi', label: '网络打印' },
        { key: 'ble', label: '本地打印' }
      ],
      brand: '启锐 QR-386A',
      brands: ['启锐 QR-386A', '汉印 HPRT N41', '得力 DL-888B', '芯烨 XP-365B'],
      devices: [
        { name: '远程：标签打印机', desc: '云打印 · 在线', state: '已连接', online: true },
        { name: '前台 WiFi 打印机', desc: '网络打印 · 192.168.1.20', state: '在线', online: true },
        { name: '仓库蓝牙打印机', desc: '本地打印 · 未连接', state: '离线', online: false }
      ],
      goods: [
        { name: '纯棉圆领T恤', spec: '白色 / L', price: '99', stock: 32, short: 'T恤' },
        { name: '直筒牛仔裤', spec: '蓝色 / 32', price: '159', stock: 18, short: '牛仔' },
        { name: '刺绣棒球帽', spec: '黑色 / 均码', price: '49', stock: 56, short: '帽' }
      ]
    }
  },
  computed: {
    current() {
      return DEMO_PAGES.find(p => p.key === this.currentKey) || DEMO_PAGES[0]
    },
    isPaperPage() {
      return this.currentKey === 'address' || this.currentKey === 'label'
    },
    formRows() {
      return FORM_ROWS[this.currentKey] || []
    },
    member() {
      return { name: '张小明', level: '黄金会员', phone: '138****8000', total: '¥ 12,680.00', points: '3,260' }
    }
  },
  created() {
    const page = this.$route.query.page
    if (page && DEMO_PAGES.some(p => p.key === page)) this.currentKey = page
  },
  methods: {
    /** 标准页面切换入口：工具栏与路由 query 共用 */
    showPage(key) {
      if (!DEMO_PAGES.some(p => p.key === key)) return
      this.currentKey = key
      this.$router.replace({ query: { page: key } }).catch(() => {})
    },
    handleClose() {
      this.$message.info('关闭弹层（预览页不退出）')
    },
    handleEdit() {
      this.$message.info('编辑模板（示例）')
    },
    handlePrimary() {
      this.$message.success(`${this.current.label}：主操作已触发（示例）`)
    },
    handleAddDevice() {
      this.$message.info('添加打印机（示例）')
    },
    handleSearch(value) {
      this.$message.info(`搜索：${value}`)
    },
    handleCamera() {
      this.$message.info('拍照识别（示例）')
    },
    handleFilter() {
      this.$message.info('打开筛选（示例）')
    }
  }
}
</script>

<style scoped>
.mmd {
  min-height: 100vh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16px;
  background: #f0f2f5;
}
.mmd-bar {
  width: 390px;
  max-width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.mmd-bar__title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
}
/* transform 使内部 position:fixed 弹层相对预览框定位 */
.mmd-frame {
  position: relative;
  width: 390px;
  height: 720px;
  max-height: calc(100vh - 100px);
  overflow: hidden;
  border-radius: 10px;
  background: #f2f4f7;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.15);
  transform: translateZ(0);
}
.mmd-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  overflow-y: auto;
}
.mmd-paper {
  flex: none;
  min-height: 200px;
  padding: 10px 12px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: #fff;
  color: #434343;
  font-size: 13px;
  line-height: 26px;
}
.mmd-paper--small {
  min-height: 170px;
}
.mmd-mt {
  margin-top: auto;
}
.mmd-card {
  flex: none;
  padding: 0 12px;
  border-radius: 10px;
  background: #fff;
}
.mmd-card--pad {
  display: flex;
  flex-direction: column;
  padding: 0 12px;
}
.mmd-row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  border-bottom: 1px solid #f2f3f5;
  color: #262626;
  font-size: 14px;
}
.mmd-row:last-child {
  border-bottom: none;
}
.mmd-row--tap {
  cursor: pointer;
}
.mmd-val {
  margin-left: auto;
  display: flex;
  align-items: center;
  color: #8c8c8c;
  font-size: 13px;
}
.mmd-seg {
  display: flex;
  gap: 6px;
}
.mmd-seg span {
  min-width: 44px;
  height: 26px;
  line-height: 24px;
  text-align: center;
  border: 1px solid #d9d9d9;
  border-radius: 13px;
  color: #595959;
  font-size: 12px;
  cursor: pointer;
}
.mmd-seg span.on {
  border-color: #1890ff;
  background: #1890ff;
  color: #fff;
}
.mmd-qty {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #262626;
  font-size: 14px;
}
.mmd-qty i {
  width: 26px;
  height: 26px;
  line-height: 24px;
  text-align: center;
  border: 1px solid #d9d9d9;
  border-radius: 50%;
  color: #8c8c8c;
  font-style: normal;
  cursor: pointer;
}
.mmd-item {
  flex: none;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #fff;
}
.mmd-thumb {
  flex: none;
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eaf4ff;
  color: #1890ff;
  font-size: 12px;
}
.mmd-item__main {
  min-width: 0;
  flex: 1;
}
.mmd-name {
  font-size: 14px;
  line-height: 1.3;
  color: #262626;
}
.mmd-spec {
  margin-top: 2px;
  font-size: 12px;
  color: #999;
}
.mmd-price {
  margin-left: auto;
  text-align: right;
}
.mmd-price b {
  display: block;
  color: #1890ff;
  font-size: 14px;
}
.mmd-price span,
.mmd-meta {
  color: #bfbfbf;
  font-size: 11px;
}
.mmd-meta {
  margin-left: auto;
  color: #999;
  font-size: 12px;
}
.mmd-dev {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 58px;
  border-bottom: 1px solid #f2f3f5;
}
.mmd-dev:last-child {
  border-bottom: none;
}
.mmd-dot {
  flex: none;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #52c41a;
}
.mmd-dot.off {
  background: #bfbfbf;
}
.mmd-add-btn {
  flex: none;
  height: 40px;
  border-style: dashed;
  color: #1890ff;
}
.mmd-radio {
  margin-left: auto;
  width: 18px;
  height: 18px;
  box-sizing: border-box;
  border: 1px solid #d9d9d9;
  border-radius: 50%;
}
.mmd-radio.on {
  border: 5px solid #1890ff;
}
.mmd-input {
  flex: 1;
  height: 32px;
  margin-left: auto;
  padding: 0 10px;
  display: flex;
  align-items: center;
  border: 1px solid #e6e8eb;
  border-radius: 6px;
  background: #fafafa;
  color: #595959;
  font-size: 13px;
}
.mmd-tip {
  color: #999;
  font-size: 12px;
  text-align: center;
}
.mmd-member {
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 12px;
  border-radius: 10px;
  background: #fff;
}
.mmd-avatar {
  flex: none;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e6f4ff;
  color: #1890ff;
  font-size: 16px;
  font-weight: 600;
}
.mmd-tag {
  display: inline-block;
  margin-left: 6px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #fff7e6;
  color: #fa8c16;
  font-size: 10px;
  vertical-align: 1px;
}
.mmd-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
}
.mmd-footer--picker {
  flex-direction: column;
  align-items: stretch;
  gap: 0;
  padding: 0;
}
.mmd-selbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid #f2f3f5;
  color: #595959;
  font-size: 13px;
}
.mmd-selbar b {
  color: #1890ff;
}
.mmd-btabs {
  display: flex;
  width: 100%;
  height: 100%;
  align-items: stretch;
}
.mmd-btab {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8c8c8c;
  font-size: 14px;
  cursor: pointer;
}
.mmd-btab.on {
  color: #1890ff;
  font-weight: 600;
}
.mmd-btab.on::after {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  width: 28px;
  height: 3px;
  border-radius: 0 0 2px 2px;
  background: #1890ff;
  transform: translateX(-50%);
}
@media (max-width: 768px) {
  .mmd {
    height: 100vh;
    padding: 0;
    align-items: stretch;
  }
  .mmd-bar {
    flex: none;
    width: 100%;
    margin: 0;
    padding: 8px 12px;
    box-sizing: border-box;
    background: #fff;
  }
  .mmd-frame {
    width: 100%;
    height: calc(100vh - 52px);
    max-height: none;
    border-radius: 0;
    box-shadow: none;
  }
}
</style>
