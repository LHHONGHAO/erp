<template>
  <a-card :bordered="false" class="print-agent-page">
    <div class="pa-head">
      <div>
        <div class="pa-title">打印管理</div>
        <div class="pa-sub">主机侧：设备登记与配对</div>
      </div>
      <a-button type="primary" icon="plus" @click="openCreate">新增设备</a-button>
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
  </a-card>
</template>

<script>
  import {
    getPrintAgentList,
    deletePrintAgent,
    getPrintAgentTasks,
    generatePrintAgentPairCode,
    getPrintAgentPairStatus
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
