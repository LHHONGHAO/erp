<template>
  <a-card :bordered="false" class="print-agent-page">
    <div class="table-operator">
      <a-button type="primary" icon="plus" @click="openCreate">新增设备</a-button>
      <a-button icon="reload" @click="loadList(false)">刷新</a-button>
      <span class="operator-tip">代理连接地址：{{ wsUrl }}</span>
    </div>

    <a-table
      :columns="columns"
      :dataSource="list"
      :loading="loading"
      :rowKey="record => record.deviceId"
      :pagination="false"
      :scroll="{ x: 1180 }"
      size="middle">
      <span slot="online" slot-scope="text, record">
        <a-tag :color="record.online ? 'green' : 'red'">{{ record.online ? '在线' : '离线' }}</a-tag>
      </span>
      <span slot="printers" slot-scope="text, record">
        <a-tag v-for="printer in (record.printers || [])" :key="printer.id">
          {{ printer.name || printer.id }}（{{ printer.host }}:{{ printer.port }}）
        </a-tag>
        <span v-if="!record.printers || record.printers.length === 0" class="muted">未配置</span>
      </span>
      <span slot="lastSeen" slot-scope="text, record">{{ formatTime(record.online ? (record.lastSeen || record.connectedAt) : record.lastSeen) }}</span>
      <span slot="enabled" slot-scope="text, record">
        <a-tag :color="record.enabled === '0' ? 'default' : 'blue'">{{ record.enabled === '0' ? '停用' : '启用' }}</a-tag>
      </span>
      <span slot="action" slot-scope="text, record">
        <a @click="openEdit(record)">编辑</a>
        <a-divider type="vertical" />
        <a v-if="record.online" @click="openTest(record)">测试打印</a>
        <span v-else class="muted">测试打印</span>
        <a-divider type="vertical" />
        <a @click="openTasks(record)">任务记录</a>
        <a-divider type="vertical" />
        <a-popconfirm title="重置后原 Token 立即失效，需要同步修改代理配置" @confirm="resetToken(record)">
          <a>重置Token</a>
        </a-popconfirm>
        <a-divider type="vertical" />
        <a-popconfirm title="确定删除该设备？" @confirm="removeDevice(record)">
          <a class="danger-link">删除</a>
        </a-popconfirm>
      </span>
    </a-table>

    <!-- 设备配置 -->
    <a-modal
      :title="form.id ? '编辑设备' : '新增设备'"
      :visible="editVisible"
      :confirmLoading="saving"
      :width="720"
      @ok="submitDevice"
      @cancel="editVisible = false">
      <a-form :form="deviceForm" class="device-form">
        <a-form-item label="设备名称" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <a-input v-model="form.name" placeholder="如 仓库标签打印机" />
        </a-form-item>
        <a-form-item label="设备编号" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <div class="copy-text" title="点击复制" @click="copyText(form.deviceId)">
            <span>{{ form.deviceId }}</span>
            <a-icon type="copy" class="copy-text-icon" />
          </div>
          <div class="form-tip">设备编号即代理 config.json 中的 deviceId（代理页面「设备 ID」），自动生成，点击可复制；两处必须一致</div>
        </a-form-item>
        <a-form-item label="配对码" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <div class="copy-text" title="点击复制" @click="copyText(form.token)">
            <span>{{ form.token }}</span>
            <a-icon type="copy" class="copy-text-icon" />
          </div>
          <div class="form-tip">打开弹窗时自动生成，对应代理配置 token；点击可复制</div>
        </a-form-item>
        <a-form-item label="通信地址" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <div class="copy-text" title="点击复制" @click="copyText(form.serverUrl)">
            <span>{{ form.serverUrl }}</span>
            <a-icon type="copy" class="copy-text-icon" />
          </div>
          <div class="form-tip">对应代理配置 server.url，默认按当前访问地址生成；点击可复制</div>
        </a-form-item>
        <a-form-item label="证书主机名" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <a-input v-model="form.certHostname" placeholder="www.innas.xyz" />
          <div class="form-tip">对应代理配置 server.serverName，用于校验证书 SAN；证书不含 IP 时必须与证书主机名一致</div>
          <div class="form-tip">服务端为自签名证书，生成的配置默认跳过证书校验；如在代理页面手动填写，请勾选「跳过证书校验」</div>
        </a-form-item>
        <a-form-item label="启用" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <a-switch v-model="form.enabled" checkedChildren="启用" unCheckedChildren="停用" />
        </a-form-item>
        <a-form-item label="打印机" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <div class="printer-fields">
            <div class="pf-item">
              <span class="pf-label">打印机 ID</span>
              <div class="printer-id" :title="'点击复制 ' + form.printers[0].id" @click="copyText(form.printers[0].id)">{{ form.printers[0].id }}</div>
            </div>
            <div class="pf-item">
              <span class="pf-label">打印机名称</span>
              <a-input v-model="form.printers[0].name" placeholder="如 标签打印机" />
            </div>
            <div class="pf-item">
              <span class="pf-label">主机地址</span>
              <a-input v-model="form.printers[0].host" placeholder="127.0.0.1" />
            </div>
            <div class="pf-item">
              <span class="pf-label">端口</span>
              <a-input-number v-model="form.printers[0].port" :min="1" :max="65535" style="width: 100%" />
            </div>
          </div>
          <div class="form-tip">打印机 ID 自动生成且不可编辑，点击可复制；主机地址为代理所在主机到 p910nd 的地址，一般填 127.0.0.1，端口 9100</div>
        </a-form-item>
        <a-form-item label="代理配置" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <a-button icon="file-text" @click="showAgentConfig">查看 / 复制 config.json</a-button>
          <a-button icon="download" @click="downloadAgentConfig">下载 config.json</a-button>
          <div class="form-tip">按打印代理 /etc/erp-print-agent/config.json 的字段名生成，可直接覆盖使用</div>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 代理配置文件 -->
    <a-modal
      title="打印代理 config.json"
      :visible="configVisible"
      :width="680"
      :footer="null"
      @cancel="configVisible = false">
      <a-textarea :value="agentConfigText" :rows="18" readOnly class="config-text" />
      <div class="config-actions">
        <a-button type="primary" icon="copy" @click="copyText(agentConfigText)">复制全部</a-button>
        <a-button icon="download" @click="downloadAgentConfig">下载 config.json</a-button>
      </div>
      <div class="form-tip">保存设备后 Token 才会生成；设备未保存时配置中的 token 为空。</div>
    </a-modal>

    <!-- 测试打印 -->
    <a-modal
      title="测试打印"
      :visible="testVisible"
      :confirmLoading="testing"
      @ok="submitTest"
      @cancel="testVisible = false">
      <a-form :form="testForm">
        <a-form-item label="设备" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <a-input :value="testRecord.deviceId" readOnly />
        </a-form-item>
        <a-form-item label="打印机" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <a-select v-model="testData.printerId">
            <a-select-option v-for="printer in (testRecord.printers || [])" :key="printer.id" :value="printer.id">
              {{ printer.name || printer.id }}（{{ printer.host }}:{{ printer.port }}）
            </a-select-option>
            <a-select-option v-if="!testRecord.printers || testRecord.printers.length === 0" value="default">default</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="份数" :labelCol="{ span: 5 }" :wrapperCol="{ span: 17 }">
          <a-input-number v-model="testData.copies" :min="1" :max="20" />
        </a-form-item>
      </a-form>
      <div class="form-tip">将下发一张内置测试标签：标题、连接说明与当前时间。</div>
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
          <a-tag :color="statusColor(text)">{{ statusText(text) }}</a-tag>
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
    savePrintAgent,
    deletePrintAgent,
    resetPrintAgentToken,
    testPrintAgent,
    getPrintAgentTasks
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

  export default {
    name: 'PrintAgentList',
    data() {
      return {
        loading: false,
        saving: false,
        testing: false,
        list: [],
        columns: [
          { title: '设备编号', dataIndex: 'deviceId', width: 140 },
          { title: '名称', dataIndex: 'name', width: 140 },
          { title: '在线状态', dataIndex: 'online', width: 100, scopedSlots: { customRender: 'online' } },
          { title: '启用', dataIndex: 'enabled', width: 80, scopedSlots: { customRender: 'enabled' } },
          { title: '版本', dataIndex: 'version', width: 90 },
          { title: '打印机', dataIndex: 'printers', width: 280, scopedSlots: { customRender: 'printers' } },
          { title: '最后在线', dataIndex: 'lastSeen', width: 170, scopedSlots: { customRender: 'lastSeen' } },
          { title: '操作', width: 330, scopedSlots: { customRender: 'action' } }
        ],
        editVisible: false,
        configVisible: false,
        form: this.emptyForm(),
        deviceForm: this.$form.createForm(this, 'deviceForm'),
        testVisible: false,
        testRecord: {},
        testData: { printerId: 'default', copies: 1 },
        testForm: this.$form.createForm(this, 'testForm'),
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
      // 打印代理 config.json 内容，字段名与代理配置完全一致
      agentConfigText() {
        return JSON.stringify(this.buildAgentConfig(), null, 2)
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
    },
    methods: {
      emptyForm() {
        const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://'
        return {
          id: null,
          deviceId: '',
          name: '',
          token: '',
          // 默认按当前访问地址生成，字段名与代理 config.json 一致
          serverUrl: protocol + window.location.host + '/ws/print-agent',
          certHostname: window.location.hostname,
          enabled: true,
          printers: [{ id: 'default', name: '标签打印机', host: '127.0.0.1', port: 9100, enabled: true }]
        }
      },
      loadList(silent) {
        if (!silent) {
          this.loading = true
        }
        getPrintAgentList().then(res => {
          if (res && res.code === 200) {
            this.list = (res.data || []).map(item => Object.assign({}, item, {
              enabled: item.enabled || '1'
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
      openCreate() {
        this.form = this.emptyForm()
        // 打开弹窗即自动生成设备编号与配对码（Token），无需点击
        this.form.deviceId = this.generateDeviceId()
        this.form.token = this.generateToken()
        this.editVisible = true
      },
      // 生成设备编号：agent- + 时间戳36进制 + 随机串，保证唯一性
      generateDeviceId() {
        return 'agent-' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5)
      },
      // 生成配对码（Token）：32 位十六进制，与后端 generateToken 规则一致
      generateToken() {
        const hex = '0123456789abcdef'
        let s = ''
        for (let i = 0; i < 32; i++) {
          s += hex.charAt(Math.floor(Math.random() * 16))
        }
        return s
      },
      openEdit(record) {
        this.form = {
          id: record.id,
          deviceId: record.deviceId,
          name: record.name || '',
          token: record.token || '',
          serverUrl: record.serverUrl || this.wsUrl,
          certHostname: record.certHostname || window.location.hostname,
          enabled: record.enabled !== '0',
          printers: JSON.parse(JSON.stringify((record.printers && record.printers.length) ? [record.printers[0]] : this.emptyForm().printers))
        }
        this.editVisible = true
      },
      // 按打印代理 config.json 的字段结构生成配置
      buildAgentConfig() {
        const form = this.form || {}
        return {
          deviceId: form.deviceId || '',
          token: form.token || '',
          server: {
            url: form.serverUrl || this.wsUrl,
            caFile: '/etc/erp-print-agent/ca.pem',
            // 服务端为自签名证书，默认跳过校验，避免代理一直离线
            insecureSkipVerify: true,
            serverName: form.certHostname || window.location.hostname
          },
          printers: (form.printers || []).map(function(p) {
            return {
              id: p.id,
              name: p.name || p.id,
              host: p.host,
              port: Number(p.port) || 9100,
              connectTimeoutMs: 3000,
              writeTimeoutMs: 15000,
              enabled: p.enabled !== false
            }
          }),
          limits: { maxTaskBytes: 4194304, queueSize: 8 },
          reconnect: { minMs: 1000, maxMs: 30000 },
          log: { level: 'info', file: '', maxSizeKB: 512 }
        }
      },
      showAgentConfig() {
        this.configVisible = true
      },
      downloadAgentConfig() {
        const blob = new Blob([this.agentConfigText], { type: 'application/json' })
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = 'config.json'
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
      },
      submitDevice() {
        if (this.saving) {
          return
        }
        if (!this.form.deviceId || !this.form.deviceId.trim()) {
          this.$message.warning('请输入设备编号')
          return
        }
        for (let i = 0; i < this.form.printers.length; i++) {
          const printer = this.form.printers[i]
          if (!printer.id || !printer.id.trim() || !printer.host || !printer.host.trim()) {
            this.$message.warning('打印机 ID 与主机地址不能为空')
            return
          }
        }
        this.saving = true
        const parameter = {
          id: this.form.id,
          deviceId: this.form.deviceId.trim(),
          name: this.form.name,
          token: this.form.token,
          enabled: this.form.enabled ? '1' : '0',
          printers: this.form.printers
        }
        savePrintAgent(parameter).then(res => {
          if (res && res.code === 200) {
            this.$message.success(parameter.id ? '保存成功' : '保存成功，请把设备编号、配对码与通信地址填入代理配置')
            this.editVisible = false
            this.loadList(false)
          } else {
            this.$message.error((typeof res.data === 'string' && res.data) ? res.data : '保存失败，请稍后重试')
          }
        }).catch(err => {
          const body = err && err.response && err.response.data
          if (body === 'loginOut') {
            this.$message.error('登录已过期，请重新登录后再保存')
          } else {
            this.$message.error('保存失败：' + ((err && err.message) || '网络异常，请稍后重试'))
          }
        }).finally(() => {
          this.saving = false
        })
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
      resetToken(record) {
        resetPrintAgentToken({ id: record.id }).then(res => {
          if (res && res.code === 200) {
            this.$message.success('Token 已重置：' + res.data)
            this.loadList(true)
          } else {
            this.$message.error((res && res.data) || '重置失败')
          }
        }).catch(() => {
          this.$message.error('重置失败')
        })
      },
      openTest(record) {
        this.testRecord = record
        const printers = record.printers || []
        this.testData = {
          printerId: printers.length ? printers[0].id : 'default',
          copies: 1
        }
        this.testVisible = true
      },
      submitTest() {
        this.testing = true
        testPrintAgent({
          deviceId: this.testRecord.deviceId,
          printerId: this.testData.printerId,
          copies: this.testData.copies
        }).then(res => {
          if (res && res.code === 200) {
            this.$message.success('测试打印已下发，任务号：' + (res.data && res.data.taskId))
            this.testVisible = false
          } else {
            this.$message.error((res && res.data) || '测试打印失败')
          }
        }).catch(() => {
          this.$message.error('测试打印失败')
        }).finally(() => {
          this.testing = false
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
      statusText(status) {
        return STATUS_TEXT[status] || status || '-'
      },
      statusColor(status) {
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

    .printer-row {
      display: flex;
      align-items: center;
      margin-bottom: 8px;

      > * {
        margin-right: 8px;
      }
    }

    .form-tip {
      margin-top: 6px;
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

  /* 设备弹层：单列排列，压缩行间距 */
  .device-form {
    .ant-form-item {
      margin-bottom: 10px;
    }

    /* 单列打印机的字段块 */
    .printer-fields {
      display: flex;
      flex-direction: column;
    }

    .pf-item {
      margin-bottom: 8px;

      &:last-child {
        margin-bottom: 0;
      }
    }

    .pf-label {
      display: block;
      margin-bottom: 2px;
      font-size: 12px;
      line-height: 18px;
      color: #595959;
    }
  }

  .form-tip {
    margin-top: 4px;
    color: #8c8c8c;
    font-size: 12px;
    line-height: 18px;
  }

  /* 设备编号：纯文本展示，不可编辑，点击复制 */
  .copy-text {
    display: inline-flex;
    align-items: center;
    padding: 4px 10px;
    border-radius: 4px;
    background: #fafafa;
    border: 1px solid #f0f0f0;
    font-family: Consolas, Monaco, monospace;
    font-size: 13px;
    color: #262626;
    cursor: pointer;
    user-select: none;
  }

  .copy-text:hover {
    background: #f0f7ff;
    border-color: #bae0ff;
  }

  .copy-text-icon {
    margin-left: 8px;
    color: #1890ff;
  }

  /* 打印机 ID：自动生成，纯文本展示，点击复制 */
  .printer-id {
    display: block;
    width: 100%;
    padding: 4px 11px;
    border-radius: 4px;
    background: #fafafa;
    border: 1px solid #f0f0f0;
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
    line-height: 22px;
    color: #262626;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    cursor: pointer;
    user-select: none;
    box-sizing: border-box;
  }

  .printer-id:hover {
    background: #f0f7ff;
    border-color: #bae0ff;
  }

  /* 代理配置文件弹层 */
  .config-text {
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
  }

  .config-actions {
    margin-top: 12px;
    text-align: right;
  }

  .config-actions button {
    margin-left: 8px;
  }
</style>
