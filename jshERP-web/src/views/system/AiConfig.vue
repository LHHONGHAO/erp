<template>
  <div class="ai-config-page">
    <a-card :bordered="false" class="ai-card">
      <div class="ai-head">
        <div class="ai-head__text">
          <div class="ai-head__title">AI配置</div>
          <div class="ai-head__sub">统一维护系统调用的第三方 API 地址与密钥，供各功能按名称读取</div>
        </div>
        <a-button type="primary" icon="plus" @click="openEdit()">新增 API</a-button>
      </div>

      <!-- 手机端：卡片列表 -->
      <div v-if="isMobile()" class="ai-mobile-list">
        <a-spin :spinning="loading">
          <div v-for="item in list" :key="item.id" class="ai-mobile-item">
            <div class="ai-mobile-item__top">
              <span class="ai-mobile-item__name">{{ item.name }}</span>
              <div class="ai-mobile-item__ops">
                <a @click="openEdit(item)">编辑</a>
                <a class="ai-del" @click="remove(item)">删除</a>
              </div>
            </div>
            <div class="ai-mobile-item__row">
              <span>能力</span><em>{{ capabilityLabel(item.capability) }}</em>
            </div>
            <div class="ai-mobile-item__row">
              <span>调用地址</span><em>{{ item.apiUrl || '-' }}</em>
            </div>
            <div class="ai-mobile-item__row">
              <span>API Key</span><em>{{ item.maskedApiKey || '未设置' }}</em>
            </div>
            <div v-if="item.maskedSecretKey" class="ai-mobile-item__row">
              <span>Secret</span><em>{{ item.maskedSecretKey }}</em>
            </div>
            <div v-if="item.remark" class="ai-mobile-item__row">
              <span>备注</span><em>{{ item.remark }}</em>
            </div>
          </div>
          <div v-if="!loading && list.length === 0" class="ai-empty">暂无配置，点击右上角「新增 API」添加</div>
        </a-spin>
      </div>

      <!-- 电脑端：表格 -->
      <a-table
        v-else
        :columns="columns"
        :dataSource="list"
        :loading="loading"
        :pagination="false"
        rowKey="id"
        size="middle">
        <template slot="capability" slot-scope="text">{{ capabilityLabel(text) }}</template>
        <template slot="apiUrl" slot-scope="text">{{ text || '-' }}</template>
        <template slot="apiKey" slot-scope="text, record">
          <div>{{ text || '未设置' }}</div>
          <div v-if="record.maskedSecretKey" class="ai-sub-key">Secret: {{ record.maskedSecretKey }}</div>
        </template>
        <template slot="remark" slot-scope="text">{{ text || '-' }}</template>
        <template slot="action" slot-scope="text, record">
          <a @click="openEdit(record)">编辑</a>
          <a-divider type="vertical" />
          <a class="ai-del" @click="remove(record)">删除</a>
        </template>
      </a-table>
    </a-card>

    <a-modal
      :visible="editVisible"
      :title="form.id ? '编辑 API' : '新增 API'"
      :width="isMobile() ? '100%' : 520"
      :footer="null"
      :maskClosable="false"
      :zIndex="2000"
      @cancel="closeEdit">
      <div class="ai-form">
        <div class="ai-form__row">
          <span class="ai-form__label">服务商</span>
          <a-select
            v-model="form.provider"
            class="ai-form__control"
            placeholder="请选择服务商"
            :dropdownStyle="{ zIndex: 2100 }"
            @change="onProviderChange">
            <a-select-option v-for="p in providers" :key="p.value" :value="p.value">{{ p.label }}</a-select-option>
          </a-select>
        </div>
        <div class="ai-form__row">
          <span class="ai-form__label">能力</span>
          <a-select
            v-model="form.capability"
            class="ai-form__control"
            placeholder="请选择识别能力"
            :dropdownStyle="{ zIndex: 2100 }"
            @change="onCapabilityChange">
            <a-select-option v-for="c in capabilities" :key="c.value" :value="c.value">{{ c.label }}</a-select-option>
          </a-select>
        </div>
        <div class="ai-form__row">
          <span class="ai-form__label">调用地址</span>
          <a-input v-model="form.apiUrl" class="ai-form__control" placeholder="https://" />
        </div>
        <div class="ai-form__row">
          <span class="ai-form__label">API Key</span>
          <a-input
            v-model="form.apiKey"
            class="ai-form__control"
            allowClear
            :placeholder="form.maskedApiKey ? ('已保存 ' + form.maskedApiKey + '，留空则沿用') : '请输入 API Key'" />
        </div>
        <div class="ai-form__row">
          <span class="ai-form__label">Secret Key</span>
          <a-input
            v-model="form.apiSecret"
            class="ai-form__control"
            allowClear
            :placeholder="form.maskedSecretKey ? ('已保存 ' + form.maskedSecretKey + '，留空则沿用') : '百度等部分服务商需要填写'" />
        </div>
        <div class="ai-form__row">
          <span class="ai-form__label">备注</span>
          <a-textarea v-model="form.remark" class="ai-form__control" :rows="3" placeholder="用途说明（可选）" />
        </div>
        <div class="ai-form__actions">
          <a-button icon="thunderbolt" :loading="testing" @click="test">测试</a-button>
          <div class="ai-form__btns">
            <a-button @click="closeEdit">取消</a-button>
            <a-button type="primary" :loading="saving" @click="save">保存</a-button>
          </div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script>
import { getAction, postAction } from '@/api/manage'
import { mixinDevice } from '@/utils/mixin'

/**
 * AI配置：统一维护第三方 API 的调用参数（名称、接口地址、API Key、备注）
 */
export default {
  name: 'AiConfig',
  mixins: [mixinDevice],
  data() {
    return {
      loading: false,
      saving: false,
      testing: false,
      list: [],
      editVisible: false,
      form: {
        id: '',
        provider: undefined,
        capability: undefined,
        name: '',
        apiUrl: '',
        apiKey: '',
        apiSecret: '',
        maskedApiKey: '',
        maskedSecretKey: '',
        remark: ''
      },
      providers: [
        { value: 'baidu', label: '百度智能云' },
        { value: 'aliyun', label: '阿里云' },
        { value: 'tencent', label: '腾讯云' },
        { value: 'xunfei', label: '科大讯飞' },
        { value: 'openai', label: 'OpenAI' },
        { value: 'custom', label: '自定义' }
      ],
      capabilities: [
        { value: 'image', label: '图片识别' },
        { value: 'text', label: '文字识别' },
        { value: 'address', label: '地址识别' },
        { value: 'speech', label: '语音识别' },
        { value: 'llm', label: '大模型对话' },
        { value: 'custom', label: '自定义' }
      ],
      // 服务商 + 能力对应的默认调用地址，选择后自动带出
      apiPresets: {
        'baidu|image': 'https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic',
        'baidu|text': 'https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic',
        'baidu|address': 'https://aip.baidubce.com/rpc/2.0/nlp/v1/address',
        'baidu|speech': 'https://vop.baidu.com/server_api',
        'baidu|llm': 'https://qianfan.baidubce.com/v2/chat/completions',
        'aliyun|image': 'https://ocr-api.cn-hangzhou.aliyuncs.com/',
        'aliyun|text': 'https://ocr-api.cn-hangzhou.aliyuncs.com/',
        'aliyun|address': 'https://address-purification.cn-hangzhou.aliyuncs.com/',
        'aliyun|speech': 'https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/asr',
        'aliyun|llm': 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
        'tencent|image': 'https://ocr.tencentcloudapi.com',
        'tencent|text': 'https://ocr.tencentcloudapi.com',
        'tencent|address': 'https://apis.map.qq.com/ws/geocoder/v1/',
        'tencent|speech': 'https://asr.tencentcloudapi.com',
        'tencent|llm': 'https://api.hunyuan.cloud.tencent.com/v1/chat/completions',
        'xunfei|image': 'https://api.xfyun.cn/v1/service/v1/ocr/general',
        'xunfei|text': 'https://api.xfyun.cn/v1/service/v1/ocr/general',
        'xunfei|speech': 'https://api.xfyun.cn/v1/service/v1/iat',
        'xunfei|llm': 'https://spark-api-open.xf-yun.com/v1/chat/completions',
        'openai|llm': 'https://api.openai.com/v1/chat/completions'
      },
      columns: [
        { title: '名称', dataIndex: 'name', width: 180 },
        { title: '能力', dataIndex: 'capability', width: 110, scopedSlots: { customRender: 'capability' } },
        { title: '调用地址', dataIndex: 'apiUrl', scopedSlots: { customRender: 'apiUrl' } },
        { title: '密钥', dataIndex: 'maskedApiKey', width: 180, scopedSlots: { customRender: 'apiKey' } },
        { title: '备注', dataIndex: 'remark', scopedSlots: { customRender: 'remark' } },
        { title: '操作', width: 140, scopedSlots: { customRender: 'action' } }
      ]
    }
  },
  created() {
    this.load()
  },
  methods: {
    load() {
      this.loading = true
      getAction('/aiConfig/list').then(res => {
        if (res && res.code === 200) {
          this.list = res.data || []
        } else {
          this.$message.error((res && res.data) || '加载失败')
        }
      }).finally(() => {
        this.loading = false
      })
    },
    providerLabel(value) {
      const hit = this.providers.find(item => item.value === value)
      return hit ? hit.label : (value || '-')
    },
    capabilityLabel(value) {
      const hit = this.capabilities.find(item => item.value === value)
      return hit ? hit.label : (value || '-')
    },
    onProviderChange() {
      this.fillPresetUrl()
    },
    onCapabilityChange() {
      this.fillPresetUrl()
    },
    fillPresetUrl() {
      if (!this.form.provider || !this.form.capability) return
      const url = this.apiPresets[this.form.provider + '|' + this.form.capability]
      if (url) {
        this.form.apiUrl = url
      }
    },
    buildName() {
      const base = (this.providerLabel(this.form.provider) + ' ' + this.capabilityLabel(this.form.capability)).trim()
      let name = base
      let index = 2
      while (this.list.some(item => item.name === name && item.id !== this.form.id)) {
        name = base + ' ' + index
        index++
      }
      return name
    },
    openEdit(record) {
      this.form = record
        ? {
          id: record.id,
          provider: record.provider || undefined,
          capability: record.capability || undefined,
          name: record.name,
          apiUrl: record.apiUrl,
          apiKey: '',
          apiSecret: '',
          maskedApiKey: record.maskedApiKey,
          maskedSecretKey: record.maskedSecretKey,
          remark: record.remark
        }
        : { id: '', provider: undefined, capability: undefined, name: '', apiUrl: '', apiKey: '', apiSecret: '', maskedApiKey: '', maskedSecretKey: '', remark: '' }
      this.editVisible = true
    },
    closeEdit() {
      this.editVisible = false
    },
    test() {
      if (!this.form.apiUrl || !this.form.apiUrl.trim()) {
        this.$message.warning('请先填写调用地址')
        return
      }
      this.testing = true
      postAction('/aiConfig/test', this.form).then(res => {
        const data = (res && res.data) || {}
        if (res && res.code === 200 && data.success) {
          this.$message.success(data.message || '测试成功')
        } else {
          const msg = data.message || (typeof (res && res.data) === 'string' ? res.data : '测试失败')
          this.$message.error(msg)
        }
      }).finally(() => {
        this.testing = false
      })
    },
    save() {
      if (!this.form.provider) {
        this.$message.warning('请选择服务商')
        return
      }
      if (!this.form.capability) {
        this.$message.warning('请选择能力')
        return
      }
      if (!this.form.apiUrl || !this.form.apiUrl.trim()) {
        this.$message.warning('请输入调用地址')
        return
      }
      this.form.name = this.buildName()
      this.saving = true
      postAction('/aiConfig/save', this.form).then(res => {
        if (res && res.code === 200) {
          this.$message.success('保存成功')
          this.editVisible = false
          this.load()
        } else {
          this.$message.error((res && res.data) || '保存失败')
        }
      }).finally(() => {
        this.saving = false
      })
    },
    remove(record) {
      this.$confirm({
        title: '确定删除该配置？',
        content: record.name,
        okText: '删除',
        okType: 'danger',
        cancelText: '取消',
        onOk: () => {
          return postAction('/aiConfig/delete', { id: record.id }).then(res => {
            if (res && res.code === 200) {
              this.$message.success('删除成功')
              this.load()
            } else {
              this.$message.error((res && res.data) || '删除失败')
            }
          })
        }
      })
    }
  }
}
</script>

<style lang="less" scoped>
.ai-config-page {
  padding: 8px;
}
.ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 4px 16px;
}
.ai-head__title {
  font-size: 18px;
  font-weight: 600;
  color: #262626;
}
.ai-head__sub {
  margin-top: 4px;
  font-size: 12px;
  color: #8c8c8c;
}
.ai-del {
  color: #ff4d4f;
}
.ai-sub-key {
  font-size: 12px;
  color: #8c8c8c;
}
.ai-mobile-list {
  padding: 0 2px;
}
.ai-mobile-item {
  padding: 12px 14px;
  margin-bottom: 10px;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 10px;
}
.ai-mobile-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.ai-mobile-item__name {
  font-size: 15px;
  font-weight: 600;
  color: #262626;
}
.ai-mobile-item__ops a {
  margin-left: 14px;
}
.ai-mobile-item__row {
  display: flex;
  margin-top: 4px;
  font-size: 13px;
  line-height: 20px;
  color: #595959;
}
.ai-mobile-item__row span {
  flex: none;
  width: 68px;
  color: #8c8c8c;
}
.ai-mobile-item__row em {
  flex: 1;
  font-style: normal;
  word-break: break-all;
}
.ai-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: #8c8c8c;
}
.ai-form__row {
  display: flex;
  align-items: flex-start;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}
.ai-form__label {
  flex: none;
  width: 76px;
  padding-top: 6px;
  font-size: 14px;
  color: #595959;
}
.ai-form__control {
  flex: 1;
  min-width: 0;
}
.ai-form__control.ant-select {
  width: 100%;
}
.ai-form__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 18px;
}
.ai-form__btns .ant-btn + .ant-btn {
  margin-left: 10px;
}
</style>
