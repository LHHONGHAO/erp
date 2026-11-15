<template>
  <a-card :bordered="false" class="print-agent-page">
    <div class="pa-head">
      <div>
        <div class="pa-title">打印管理</div>
        <div class="pa-sub">主机侧：设备登记与配对</div>
      </div>
      <div class="pa-head-actions">
        <a-button icon="printer" @click="openPrinterLib">添加打印机</a-button>
        <a-button type="primary" icon="plus" @click="openCreate">新增设备</a-button>
      </div>
    </div>

    <div class="pa-table">
      <div class="pa-tr pa-th">
        <span>门店 / 型号</span>
        <span>设备编号</span>
        <span class="pa-col-printer">打印机</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div v-for="record in list" :key="record.deviceId" class="pa-tr">
        <div class="pa-cell">
          <div class="pa-cell-title">{{ record.storeName || record.name || '未命名门店' }}</div>
          <div class="pa-cell-sub">{{ record.printerModel || '未填写型号' }}</div>
        </div>
        <div class="pa-code" :title="'点击复制 ' + record.deviceId" @click="copyText(record.deviceId)">{{ record.deviceId }}</div>
        <div class="pa-cell-sub pa-col-printer">{{ printerSummary(record) }}</div>
        <div class="pa-status" :class="deviceStatusClass(record)">
          <span class="pa-dot"></span>{{ deviceStatusText(record) }}
        </div>
        <div class="pa-actions">
          <a @click="testPrint(record)">测试打印</a>
          <a @click="openTasks(record)">任务</a>
          <a-popconfirm title="确定删除该设备？" @confirm="removeDevice(record)">
            <a class="danger-link">删除</a>
          </a-popconfirm>
        </div>
      </div>
      <div v-if="!loading && list.length === 0" class="pa-empty">
        还没有设备。点右上角「新增设备」，把通信地址与配对码填入打印代理设置页即可接入。
      </div>
    </div>
    <div class="pa-hint">在线：已配对并连接；离线：已配对但未连接。</div>

    <!-- 新增设备：通信地址 → 配对码 → 已接入 -->
    <a-modal
      title="新增设备"
      :visible="pairVisible"
      :width="640"
      :footer="null"
      @cancel="closePair">
      <div class="pair-steps">
        <span class="pair-step" :class="{ active: pairStep === 1 }">1 通信地址</span>
        <span class="pair-step" :class="{ active: pairStep === 2 }">2 配对码</span>
        <span class="pair-step" :class="{ active: pairStep === 3 }">3 已接入</span>
      </div>

      <div v-if="pairStep === 1" class="pair-body">
        <a-form>
          <a-form-item label="通信地址" :labelCol="{ span: 6 }" :wrapperCol="{ span: 16 }">
            <a-input :value="wsUrl" readOnly />
            <div class="form-tip">填入打印代理设置页的「通信地址」，点下方生成配对码</div>
          </a-form-item>
        </a-form>
        <div class="pair-actions">
          <a-button type="primary" :loading="pairLoading" @click="createPairCode">生成配对码</a-button>
          <a-button @click="copyText(wsUrl)">复制地址</a-button>
          <a-button @click="closePair">取消</a-button>
        </div>
      </div>

      <div v-else-if="pairStep === 2" class="pair-body">
        <div class="pair-code-card">
          <div class="pair-code-label">配对码 · 10 分钟内有效 · 一次性</div>
          <div class="pair-code">{{ pairCode }}</div>
          <div class="pair-code-meta">
            <span>剩余 <b>{{ pairCountdown }}</b></span>
            <span>通信地址 {{ wsUrl }}</span>
          </div>
        </div>
        <div class="pair-actions">
          <a-button type="primary" @click="copyText(pairCode)">复制配对码</a-button>
          <a-button :loading="pairLoading" @click="createPairCode">重新生成</a-button>
          <a-button @click="closePair">取消</a-button>
        </div>
      </div>

      <div v-else class="pair-body">
        <div class="pair-done">
          <span class="pa-dot ok"></span>
          <div>
            <div class="pa-cell-title">已接入：{{ pairResult.storeName || pairResult.deviceId }}</div>
            <div class="pa-cell-sub">{{ pairResult.deviceId }} · {{ pairResult.printerModel || '未填写型号' }}</div>
          </div>
        </div>
        <div class="pair-actions">
          <a-button type="primary" @click="closePair">完成</a-button>
        </div>
      </div>
    </a-modal>

    <!-- 任务记录 -->
    <a-drawer
      title="任务记录"
      :width="860"
      :visible="taskVisible"
      @close="taskVisible = false">
      <div class="table-operator">
        <a-button size="small" icon="reload" @click="loadTasks">刷新</a-button>
        <span class="operator-tip">{{ taskDeviceId || '全部设备' }}</span>
      </div>
      <a-table
        :columns="taskColumns"
        :dataSource="tasks"
        :loading="taskLoading"
        :rowKey="record => record.taskId"
        :pagination="false"
        size="small">
        <span slot="taskStatus" slot-scope="text">
          <a-tag :color="taskStatusColor(text)">{{ taskStatusText(text) }}</a-tag>
        </span>
        <span slot="taskMessage" slot-scope="text, record">{{ record.errorCode ? record.errorCode + ' / ' : '' }}{{ text || '-' }}</span>
        <span slot="taskTime" slot-scope="text, record">{{ formatTime(record.createdAt) }}<br /><span class="muted">{{ formatTime(record.finishedAt) }}</span></span>
      </a-table>
    </a-drawer>

    <!-- 添加打印机：型号库，主机下发给代理选择，并按型号参数驱动本地打印机 -->
    <a-modal
      title="打印机配置"
      :visible="printerLibVisible && !brandPickVisible"
      :width="860"
      :footer="null"
      @cancel="printerLibVisible = false">
      <div class="plib-head">
        <div class="plib-tip">型号与指令方式保存在主机，代理设置页的「型号」从主机下发选择；打印时主机按该型号的指令方式驱动本地打印机。</div>
        <a-button type="primary" size="small" icon="plus" @click="openBrandPicker">新增打印机</a-button>
      </div>
      <a-table
        :columns="printerColumns"
        :dataSource="printerList"
        :loading="printerLoading"
        :rowKey="record => record.id"
        :pagination="false"
        size="small">
        <span slot="printerModel" slot-scope="text, record">
          <div class="plib-model">{{ text }}</div>
          <div class="plib-brand">{{ record.brand || '未填写品牌' }}</div>
        </span>
        <span slot="printerEnabled" slot-scope="text">
          <a-tag :color="text === '1' ? 'green' : 'default'">{{ text === '1' ? '启用' : '停用' }}</a-tag>
        </span>
        <span slot="printerAction" slot-scope="text, record">
          <a @click="openPrinterForm(record)">编辑</a>
          <a-divider type="vertical" />
          <a-popconfirm title="确定删除该型号？" @confirm="removePrinter(record)">
            <a class="danger-link">删除</a>
          </a-popconfirm>
        </span>
      </a-table>
      <div v-if="!printerLoading && printerList.length === 0" class="plib-empty">
        还没有配置打印机。点右上角「新增打印机」选择品牌和指令方式即可添加。
      </div>
    </a-modal>

    <!-- 新增打印机：选择品牌 + 指令方式，选中后直接添加并回到打印机配置 -->
    <a-modal
      title="选择品牌"
      :visible="brandPickVisible"
      :width="720"
      :footer="null"
      @cancel="brandPickVisible = false">
      <div class="bp-grid">
        <div v-for="b in brands" :key="b.key" class="bp-cell" @click="brandCmdBrand = b">
          <div class="bp-logo" :style="{ color: b.color }">{{ b.en }}</div>
          <div class="bp-name">{{ b.name }}</div>
          <div class="bp-cmd" @click.stop="brandCmdBrand = b">
            <span>{{ brandPickCmd[b.key] || b.cmdSet }}</span>
            <a-icon type="down" />
          </div>
        </div>
      </div>
      <!-- 指令方式弹层：点选后直接添加默认打印机 -->
      <div v-if="brandCmdBrand" class="bp-sheet-mask" @click="brandCmdBrand = null">
        <div class="bp-sheet" @click.stop>
          <div class="bp-sheet-group">
            <div class="bp-sheet-item" v-for="c in cmdSets" :key="c" @click="pickBrandCmd(c)">{{ c }}</div>
          </div>
          <div class="bp-sheet-cancel" @click="brandCmdBrand = null">取消</div>
        </div>
      </div>
    </a-modal>

    <!-- 打印机参数：仅型号、品牌与指令方式 -->
    <a-modal
      :title="printerForm.id ? '编辑打印机' : '新增打印机'"
      :visible="printerFormVisible"
      :width="520"
      :confirmLoading="printerSaving"
      okText="保存"
      @ok="savePrinter"
      @cancel="printerFormVisible = false">
      <a-form :labelCol="{ span: 7 }" :wrapperCol="{ span: 16 }">
        <a-form-item label="品牌">
          <a-select v-model="printerForm.brand" placeholder="选择品牌">
            <a-select-option v-for="b in brands" :key="b.key" :value="b.name">{{ b.name }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="型号">
          <a-input v-model.trim="printerForm.model" placeholder="如 汉印D45，需与代理选择的型号一致" />
        </a-form-item>
        <a-form-item label="指令方式">
          <a-select v-model="printerForm.cmdSet">
            <a-select-option v-for="c in cmdSets" :key="c" :value="c">{{ c }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="启用">
          <a-switch v-model="printerForm.enabled" checkedChildren="启用" unCheckedChildren="停用" />
        </a-form-item>
        <a-form-item label="备注">
          <a-input v-model.trim="printerForm.remark" placeholder="选填" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<script>
  import {
    getPrintAgentList,
    deletePrintAgent,
    getPrintAgentTasks,
    printViaAgent,
    generatePrintAgentPairCode,
    getPrintAgentPairStatus,
    getPrintPrinterList,
    savePrintPrinter,
    deletePrintPrinter
  } from '@/api/printAgent'

  const STATUS_TEXT = {
    pending: '待下发',
    sent: '已下发',
    accepted: '已接收',
    success: '成功',
    failed: '失败',
    rejected: '已拒绝',
    timeout: '超时',
    offline: '离线失败',
    cancelled: '已取消'
  }
  const STATUS_COLOR = {
    pending: 'default',
    sent: 'blue',
    accepted: 'cyan',
    success: 'green',
    failed: 'red',
    rejected: 'red',
    timeout: 'orange',
    offline: 'orange',
    cancelled: 'default'
  }
  const PAIR_CODE_SECONDS = 600

  export default {
    name: 'PrintAgentList',
    data() {
      return {
        loading: false,
        list: [],
        // 配对流程
        pairVisible: false,
        pairStep: 1,
        pairLoading: false,
        pairResult: {},
        pairCode: '',
        pairSeconds: PAIR_CODE_SECONDS,
        pairTimer: null,
        pairPollTimer: null,
        taskVisible: false,
        taskDeviceId: '',
        taskLoading: false,
        tasks: [],
        // 打印机型号库（主机下发，代理选择后按型号参数驱动打印）
        printerLibVisible: false,
        printerLoading: false,
        printerList: [],
        printerFormVisible: false,
        printerSaving: false,
        printerForm: {},
        brandPickVisible: false,
        brandPickCmd: {},
        brandCmdBrand: null,
        cmdSets: ['CPCL', 'DDPL', 'TSPL', 'ESC/POS'],
        brands: [
          { key: 'chiteng', name: '驰腾', en: '驰腾', cmdSet: 'TSPL', color: '#1d39c4' },
          { key: 'gainscha', name: '佳博', en: 'Gainscha', cmdSet: 'TSPL', color: '#1890ff' },
          { key: 'sprt', name: '思普瑞特', en: 'SPRT', cmdSet: 'TSPL', color: '#1d39c4' },
          { key: 'hprt', name: '汉印', en: '汉印 HPRT', cmdSet: 'CPCL', color: '#cf1322' },
          { key: 'clabel', name: 'Clabel', en: 'Clabel', cmdSet: 'TSPL', color: '#1d39c4' },
          { key: 'kuaimai', name: '快麦', en: '快麦', cmdSet: 'TSPL', color: '#fa541c' },
          { key: 'qirui', name: '启锐', en: '启锐', cmdSet: 'TSPL', color: '#1d39c4' },
          { key: 'tsc', name: 'TSC', en: 'TSC', cmdSet: 'TSPL', color: '#003a8c' },
          { key: 'ure', name: '优迩', en: 'URPRTCN', cmdSet: 'TSPL', color: '#fa8c16' }
        ],
        printerColumns: [
          { title: '型号 / 品牌', dataIndex: 'model', scopedSlots: { customRender: 'printerModel' } },
          { title: '指令方式', dataIndex: 'cmdSet', width: 110 },
          { title: '状态', dataIndex: 'enabled', width: 80, scopedSlots: { customRender: 'printerEnabled' } },
          { title: '操作', width: 120, scopedSlots: { customRender: 'printerAction' } }
        ],
        taskColumns: [
          { title: '任务编号', dataIndex: 'taskId', width: 200 },
          { title: '打印机', dataIndex: 'printerId', width: 100 },
          { title: '状态', dataIndex: 'status', width: 100, scopedSlots: { customRender: 'taskStatus' } },
          { title: '字节数', dataIndex: 'totalBytes', width: 90 },
          { title: '结果说明', dataIndex: 'message', scopedSlots: { customRender: 'taskMessage' } },
          { title: '创建 / 结束', width: 170, scopedSlots: { customRender: 'taskTime' } }
        ],
        timer: null
      }
    },
    computed: {
      wsUrl() {
        const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://'
        return protocol + window.location.host + '/ws/print-agent'
      },
      pairCountdown() {
        const sec = Math.max(0, this.pairSeconds)
        const m = Math.floor(sec / 60)
        const s = sec % 60
        return (m < 10 ? '0' + m : m) + ':' + (s < 10 ? '0' + s : s)
      }
    },
    created() {
      this.loadList(false)
      this.loadPrinters()
      this.timer = setInterval(() => this.loadList(true), 10000)
    },
    beforeDestroy() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = null
      }
      this.stopPairTimers()
    },
    methods: {
      loadList(silent) {
        if (!silent) {
          this.loading = true
        }
        getPrintAgentList().then(res => {
          if (res && res.code === 200) {
            this.list = (res.data || []).map(item => Object.assign({}, item, {
              enabled: item.enabled || '1',
              paired: item.paired || '0'
            }))
          } else {
            this.$message.error((res && res.data) || '加载设备失败')
          }
        }).catch(() => {
          if (!silent) {
            this.$message.error('加载设备失败')
          }
        }).finally(() => {
          this.loading = false
        })
      },
      printerSummary(record) {
        const printers = record.printers || []
        if (printers.length === 0) {
          return '未配置'
        }
        if (printers.length === 1) {
          return printers[0].name || printers[0].id || '1 台'
        }
        return printers.length + ' 台'
      },
      deviceStatusText(record) {
        if (record.paired !== '1') {
          return '待配对'
        }
        return record.online ? '在线' : '离线'
      },
      deviceStatusClass(record) {
        if (record.paired !== '1') {
          return 'warn'
        }
        return record.online ? 'ok' : 'off'
      },
      // ---------------- 配对 ----------------
      openCreate() {
        this.pairResult = {}
        this.pairCode = ''
        this.pairStep = 1
        this.pairLoading = false
        this.pairVisible = true
      },
      createPairCode() {
        this.pairLoading = true
        generatePrintAgentPairCode({}).then(res => {
          if (res && res.code === 200 && res.data) {
            this.pairCode = res.data.pairCode
            this.pairStep = 2
            this.startPairTimers()
          } else {
            this.$message.error((typeof res.data === 'string' && res.data) ? res.data : '生成配对码失败')
          }
        }).catch(() => {
          this.$message.error('生成配对码失败')
        }).finally(() => {
          this.pairLoading = false
        })
      },
      startPairTimers() {
        this.stopPairTimers()
        this.pairSeconds = PAIR_CODE_SECONDS
        this.pairTimer = setInterval(() => {
          if (this.pairSeconds <= 0) {
            this.stopPairTimers()
            return
          }
          this.pairSeconds -= 1
        }, 1000)
        this.pairPollTimer = setInterval(() => {
          getPrintAgentPairStatus({ code: this.pairCode }).then(res => {
            if (res && res.code === 200 && res.data && res.data.paired) {
              this.stopPairTimers()
              this.pairResult = res.data
              this.pairStep = 3
              this.loadList(true)
            }
          }).catch(() => {})
        }, 3000)
      },
      stopPairTimers() {
        if (this.pairTimer) {
          clearInterval(this.pairTimer)
          this.pairTimer = null
        }
        if (this.pairPollTimer) {
          clearInterval(this.pairPollTimer)
          this.pairPollTimer = null
        }
      },
      closePair() {
        this.stopPairTimers()
        this.pairVisible = false
        this.pairStep = 1
        this.loadList(true)
      },
      removeDevice(record) {
        deletePrintAgent({ id: record.id }).then(res => {
          if (res && res.code === 200) {
            this.$message.success('已删除')
            this.loadList(false)
          } else {
            this.$message.error((res && res.data) || '删除失败')
          }
        }).catch(() => {
          this.$message.error('删除失败')
        })
      },
      testPrint(record) {
        const hide = this.$message.loading('正在下发测试标签…', 0)
        const time = new Date().toLocaleString()
        // 指令方式由主机按打印机型号决定，标签内容固定 320×240
        printViaAgent({
          deviceId: record.deviceId,
          items: [
            { text: '打印测试', x: 16, y: 16, fontSize: 32 },
            { text: record.storeName || record.name || '未命名门店', x: 16, y: 90, fontSize: 20 },
            { text: time, x: 16, y: 140, fontSize: 20 }
          ]
        }).then(res => {
          hide()
          if (res && res.code === 200) {
            this.$message.success('测试标签已下发至打印机')
            this.loadList(false)
          } else {
            this.$message.error((res && res.data) || '测试打印失败')
          }
        }).catch(() => {
          hide()
          this.$message.error('测试打印失败')
        })
      },
      // ---------------- 打印机型号库 ----------------
      openPrinterLib() {
        this.printerLibVisible = true
        this.brandPickVisible = false
        this.loadPrinters()
      },
      loadPrinters() {
        this.printerLoading = true
        getPrintPrinterList().then(res => {
          if (res && res.code === 200) {
            this.printerList = res.data || []
          } else {
            this.$message.error((res && res.data) || '加载打印机型号失败')
          }
        }).catch(() => {
          this.$message.error('加载打印机型号失败')
        }).finally(() => {
          this.printerLoading = false
        })
      },
      // 选择品牌页：默认展示各品牌的常用指令方式
      openBrandPicker() {
        const defaults = {}
        this.brands.forEach(b => { defaults[b.key] = b.cmdSet })
        this.brandPickCmd = defaults
        this.brandCmdBrand = null
        this.brandPickVisible = true
      },
      // 指令方式弹层选完即添加
      pickBrandCmd(cmd) {
        const brand = this.brandCmdBrand
        this.brandCmdBrand = null
        if (brand) {
          this.addPrinterFromBrand(brand, cmd)
        }
      },
      // 型号重名时自动加序号
      defaultModelName(brand) {
        const used = {}
        this.printerList.forEach(p => {
          if (p.model) {
            used[p.model] = true
          }
        })
        let model = brand.name
        let index = 2
        while (used[model]) {
          model = brand.name + ' ' + index
          index += 1
        }
        return model
      },
      // 选择指令方式后直接添加默认打印机，并回到打印机配置页
      addPrinterFromBrand(brand, cmd) {
        this.brandPickCmd = Object.assign({}, this.brandPickCmd, { [brand.key]: cmd })
        this.printerSaving = true
        savePrintPrinter({
          brand: brand.name,
          model: this.defaultModelName(brand),
          cmdSet: cmd,
          enabled: '1'
        }).then(res => {
          if (res && res.code === 200) {
            this.$message.success('已添加 ' + brand.name + '（' + cmd + '）')
            this.brandPickVisible = false
            this.loadPrinters()
          } else {
            this.$message.error((res && res.data) || '添加失败')
          }
        }).catch(() => {
          this.$message.error('添加失败')
        }).finally(() => {
          this.printerSaving = false
        })
      },
      openPrinterForm(record) {
        this.printerForm = record ? {
          id: record.id,
          brand: record.brand || '',
          model: record.model || '',
          cmdSet: record.cmdSet || 'TSPL',
          enabled: record.enabled === '1',
          remark: record.remark || ''
        } : {
          brand: this.brands[0].name,
          model: '',
          cmdSet: 'TSPL',
          enabled: true,
          remark: ''
        }
        this.printerFormVisible = true
      },
      savePrinter() {
        if (!this.printerForm.model) {
          this.$message.warning('请填写打印机型号')
          return
        }
        const payload = Object.assign({}, this.printerForm, {
          enabled: this.printerForm.enabled ? '1' : '0'
        })
        this.printerSaving = true
        savePrintPrinter(payload).then(res => {
          if (res && res.code === 200) {
            this.$message.success('打印机参数已保存')
            this.printerFormVisible = false
            this.loadPrinters()
          } else {
            this.$message.error((res && res.data) || '保存失败')
          }
        }).catch(() => {
          this.$message.error('保存失败')
        }).finally(() => {
          this.printerSaving = false
        })
      },
      removePrinter(record) {
        deletePrintPrinter({ id: record.id }).then(res => {
          if (res && res.code === 200) {
            this.$message.success('已删除')
            this.loadPrinters()
          } else {
            this.$message.error((res && res.data) || '删除失败')
          }
        }).catch(() => {
          this.$message.error('删除失败')
        })
      },
      openTasks(record) {
        this.taskDeviceId = record.deviceId
        this.tasks = []
        this.taskVisible = true
        this.loadTasks()
      },
      loadTasks() {
        this.taskLoading = true
        getPrintAgentTasks({ deviceId: this.taskDeviceId, limit: 50 }).then(res => {
          if (res && res.code === 200) {
            this.tasks = res.data || []
          } else {
            this.$message.error((res && res.data) || '加载任务失败')
          }
        }).catch(() => {
          this.$message.error('加载任务失败')
        }).finally(() => {
          this.taskLoading = false
        })
      },
      taskStatusText(status) {
        return STATUS_TEXT[status] || status || '-'
      },
      taskStatusColor(status) {
        return STATUS_COLOR[status] || 'default'
      },
      formatTime(value) {
        if (!value) {
          return '-'
        }
        let date
        if (typeof value === 'number') {
          date = new Date(value)
        } else {
          date = new Date(String(value).replace(/-/g, '/'))
        }
        if (isNaN(date.getTime())) {
          return value
        }
        const pad = num => (num < 10 ? '0' + num : '' + num)
        return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
          + ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds())
      },
      copyText(text) {
        if (!text) {
          return
        }
        const input = document.createElement('textarea')
        input.value = text
        document.body.appendChild(input)
        input.select()
        try {
          document.execCommand('copy')
          this.$message.success('已复制')
        } catch (e) {
          this.$message.warning('复制失败，请手动选择')
        }
        document.body.removeChild(input)
      }
    }
  }
</script>

<style lang="less" scoped>
  .print-agent-page {
    .pa-head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      margin-bottom: 16px;
    }

    .pa-title {
      font-size: 16px;
      font-weight: 500;
      color: #262626;
      line-height: 24px;
    }

    .pa-sub {
      margin-top: 4px;
      font-size: 12px;
      line-height: 18px;
      color: #8c8c8c;
    }

    .pa-head-actions {
      display: flex;
      gap: 8px;
      flex: none;
    }

    .pa-table {
      border: 1px solid #f0f0f0;
      border-radius: 8px;
      overflow: hidden;
    }

    .pa-tr {
      display: grid;
      grid-template-columns: 1.8fr 1.2fr 1.3fr 0.9fr 1.6fr;
      gap: 12px;
      align-items: center;
      padding: 12px 16px;
      border-top: 1px solid #f0f0f0;

      &:first-child {
        border-top: none;
      }
    }

    .pa-th {
      background: #fafafa;
      font-size: 12px;
      line-height: 18px;
      color: #8c8c8c;
    }

    .pa-cell-title {
      font-size: 14px;
      font-weight: 500;
      color: #262626;
      line-height: 20px;
    }

    .pa-cell-sub {
      margin-top: 2px;
      font-size: 12px;
      line-height: 18px;
      color: #8c8c8c;
    }

    .pa-code {
      font-family: Consolas, Monaco, monospace;
      font-size: 13px;
      color: #262626;
      cursor: pointer;
      user-select: none;

      &:hover {
        color: #1890ff;
      }
    }

    .pa-status {
      display: inline-flex;
      align-items: center;
      font-size: 12px;
      line-height: 18px;
      font-weight: 500;

      &.ok {
        color: #52c41a;
      }

      &.warn {
        color: #faad14;
      }

      &.off {
        color: #8c8c8c;
      }
    }

    .pa-dot {
      display: inline-block;
      width: 8px;
      height: 8px;
      margin-right: 8px;
      border-radius: 50%;
      background: currentColor;
      flex: none;
    }

    .pa-actions {
      a {
        margin-right: 12px;

        &:last-child {
          margin-right: 0;
        }
      }
    }

    .pa-empty {
      padding: 32px 16px;
      text-align: center;
      font-size: 13px;
      color: #8c8c8c;
    }

    .pa-hint {
      margin-top: 12px;
      font-size: 12px;
      line-height: 18px;
      color: #8c8c8c;
    }

    .pair-steps {
      display: flex;
      gap: 8px;
      margin-bottom: 20px;
    }

    .pair-step {
      padding: 3px 12px;
      border-radius: 12px;
      border: 1px solid #f0f0f0;
      background: #fafafa;
      font-size: 12px;
      line-height: 18px;
      color: #8c8c8c;

      &.active {
        border-color: #91d5ff;
        background: #fff;
        color: #1890ff;
      }
    }

    .pair-body {
      .form-tip {
        margin-top: 4px;
      }
    }

    .pair-code-card {
      padding: 20px;
      border: 1px solid #91d5ff;
      border-radius: 8px;
      display: flex;
      flex-direction: column;
      align-items: center;
    }

    .pair-code-label {
      font-size: 12px;
      line-height: 18px;
      color: #8c8c8c;
    }

    .pair-code {
      margin: 8px 0;
      padding: 4px 16px;
      border-radius: 4px;
      background: #fafafa;
      font-family: Consolas, Monaco, monospace;
      font-size: 28px;
      font-weight: 600;
      letter-spacing: 8px;
      color: #1890ff;
    }

    .pair-code-meta {
      display: flex;
      gap: 16px;
      font-size: 12px;
      line-height: 18px;
      color: #8c8c8c;
    }

    .pair-done {
      display: flex;
      align-items: center;
      padding: 16px;
      border: 1px solid #b7eb8f;
      border-radius: 8px;
      color: #52c41a;

      .pa-cell-title,
      .pa-cell-sub {
        color: inherit;
      }

      .pa-cell-sub {
        color: #8c8c8c;
      }
    }

    .pair-actions {
      display: flex;
      gap: 8px;
      margin-top: 16px;
    }

    .table-operator {
      display: flex;
      align-items: center;
      margin-bottom: 16px;

      button {
        margin-right: 8px;
      }
    }

    .operator-tip {
      margin-left: 8px;
      color: #8c8c8c;
    }

    .form-tip {
      margin-top: 4px;
      color: #8c8c8c;
      font-size: 12px;
      line-height: 18px;
    }

    .muted {
      color: #bfbfbf;
    }

    .danger-link {
      color: #f5222d;
    }
  }

  /* 弹窗挂载在 body 下，样式必须放在 .print-agent-page 外层才能生效 */
  .plib-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 12px;
  }

  .plib-tip {
    font-size: 12px;
    line-height: 18px;
    color: #8c8c8c;
  }

  .plib-model {
    font-size: 13px;
    color: #262626;
    line-height: 18px;
  }

  .plib-brand {
    font-size: 12px;
    color: #8c8c8c;
    line-height: 18px;
  }

  .plib-empty {
    padding: 24px 0 8px;
    text-align: center;
    font-size: 13px;
    color: #8c8c8c;
  }

  .danger-link {
    color: #f5222d;
  }

  /* 沿用手机版「选择品牌」布局，尺寸适配电脑 */
  .bp-grid {
    display: flex;
    flex-wrap: wrap;
    align-content: flex-start;
    padding: 8px 6px 20px;
  }

  .bp-cell {
    width: 33.33%;
    padding: 18px 8px 14px;
    text-align: center;
    cursor: pointer;
  }

  .bp-logo {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 21px;
    font-weight: 700;
    letter-spacing: .5px;
    white-space: nowrap;
    overflow: hidden;
  }

  .bp-name {
    margin-top: 8px;
    font-size: 14px;
    color: #333;
  }

  .bp-cmd {
    margin: 8px auto 0;
    width: 92px;
    height: 28px;
    border: 1px solid #1890ff;
    border-radius: 14px;
    color: #1890ff;
    font-size: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 3px;
  }

  .bp-cmd .anticon {
    font-size: 10px;
  }

  .bp-sheet-mask {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    left: 0;
    background: rgba(0, 0, 0, .45);
    z-index: 1200;
  }

  .bp-sheet {
    position: absolute;
    left: 50%;
    bottom: 24px;
    width: 100%;
    max-width: 420px;
    transform: translateX(-50%);
  }

  .bp-sheet-group {
    background: #fff;
    border-radius: 12px;
    overflow: hidden;
  }

  .bp-sheet-item {
    padding: 15px 0;
    text-align: center;
    font-size: 16px;
    color: #1890ff;
    border-bottom: 1px solid #f0f0f0;
    cursor: pointer;
  }

  .bp-sheet-item:last-child {
    border-bottom: none;
  }

  .bp-sheet-cancel {
    margin-top: 8px;
    background: #fff;
    border-radius: 12px;
    padding: 15px 0;
    text-align: center;
    font-size: 16px;
    color: #1890ff;
    cursor: pointer;
  }

  @media (max-width: 900px) {
    .print-agent-page {
      .pa-tr {
        grid-template-columns: 1.6fr 1.2fr 0.9fr 1.4fr;
      }

      .pa-col-printer {
        display: none;
      }
    }
  }
</style>
