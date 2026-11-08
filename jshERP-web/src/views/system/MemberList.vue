<template>
  <div class="member-list-page">
    <!-- ==================== 手机端：标准信息搜索页面 ==================== -->
    <mobile-search-page
      v-if="isNarrowScreen"
      v-model="mobileKeyword"
      title="会员管理"
      placeholder="搜索姓名 / 电话"
      :show-scan="false"
      :show-camera="false"
      @back="$router.back()"
      @search="mobileSearch"
      @filter="filterDrawerVisible = true">
      <template #actions>
        <mobile-header-actions :actions="mobileHeaderActions" />
      </template>

      <!-- 会员卡片：姓名 + 电话 一行，地址换行 -->
      <div class="mm-list">
        <div v-for="item in dataSource" :key="item.id" class="mm-card">
          <!-- 多选：与商品信息页一致，无外框仅勾图标 -->
          <span
            class="mm-card-check"
            :class="{ 'is-checked': isMobileSelected(item) }"
            @click.stop="toggleMobileSelect(item)">
            <a-icon type="check" />
          </span>
          <div class="mm-card-main">
            <div class="mm-card-line">
              <span class="mm-card-name">{{ item.contacts || item.supplier || '-' }}</span>
              <span class="mm-card-phone">{{ item.telephone || '' }}</span>
              <span v-if="item.enabled === false" class="mm-card-tag">禁用</span>
            </div>
            <div class="mm-card-address">{{ item.address || '暂无地址' }}</div>
          </div>
          <!-- 操作按钮：查看（会员详情）+ 打印（直接弹打印层） -->
          <div class="mm-card-actions">
            <div class="mm-card-btn" @click.stop="openPreview(item)">
              <a-icon type="eye" />
            </div>
            <div class="mm-card-btn" @click.stop="printMember(item)">
              <a-icon type="printer" />
            </div>
          </div>
        </div>
        <div v-if="!loading && dataSource.length === 0" class="mm-empty">暂无会员</div>
      </div>

      <div v-if="ipagination.total > ipagination.pageSize" class="mm-pager">
        <a-pagination
          simple
          :current="ipagination.current"
          :page-size="ipagination.pageSize"
          :total="ipagination.total"
          @change="onMobilePageChange" />
      </div>

      <template #overlay>
        <!-- 条件筛选抽屉 -->
        <a-drawer
          :visible="filterDrawerVisible"
          placement="right"
          title="条件筛选"
          width="280"
          @close="filterDrawerVisible = false">
          <div class="mb-filter-item">
            <span class="mb-filter-label">会员卡号</span>
            <a-input v-model="queryParam.supplier" placeholder="请输入会员卡号" allow-clear />
          </div>
          <div class="mb-filter-item">
            <span class="mb-filter-label">联系人</span>
            <a-input v-model="queryParam.contacts" placeholder="请输入联系人" allow-clear />
          </div>
          <div class="mb-filter-item">
            <span class="mb-filter-label">电话</span>
            <a-input v-model="queryParam.telephone" placeholder="请输入电话" allow-clear />
          </div>
          <div class="mb-filter-footer">
            <a-button block type="primary" @click="applyMobileFilter">确定</a-button>
          </div>
        </a-drawer>
        <member-modal ref="modalForm" @ok="modalFormOk"></member-modal>
        <import-file-modal ref="modalImportForm" @ok="modalFormOk"></import-file-modal>
        <member-preview ref="memberPreview"></member-preview>
      </template>
    </mobile-search-page>

    <!-- ==================== 电脑端布局 ==================== -->
    <a-row v-else :gutter="24">
    <a-col :md="24">
      <a-card :style="cardStyle" :bordered="false">
        <!-- 查询区域 -->
        <div class="table-page-search-wrapper">
          <!-- 搜索区域 -->
          <a-form layout="inline" @keyup.enter.native="searchQuery">
            <a-row :gutter="24">
              <a-col :md="6" :sm="24">
                <a-form-item label="会员卡号" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入会员卡号查询" v-model="queryParam.supplier"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="联系人" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入联系人查询" v-model="queryParam.contacts"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="电话" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入电话查询" v-model="queryParam.telephone"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
                  <a-button type="primary" @click="searchQuery">查询</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset">重置</a-button>
                  <a @click="handleToggleSearch" style="margin-left: 8px">
                    {{ toggleSearchStatus ? '收起' : '展开' }}
                    <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
                  </a>
                </span>
              </a-col>
            </a-row>
            <template v-if="toggleSearchStatus">
              <a-row :gutter="24">
                <a-col :md="6" :sm="24">
                  <a-form-item label="电子邮箱" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入电子邮箱查询" v-model="queryParam.email"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="微信账户" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入微信账户查询" v-model="queryParam.wechatAccount"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="抖音账户" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入抖音账户查询" v-model="queryParam.douyinAccount"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="支付宝账户" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入支付宝账户查询" v-model="queryParam.alipayAccount"></a-input>
                  </a-form-item>
                </a-col>
              </a-row>
            </template>
          </a-form>
        </div>
        <!-- 操作按钮区域 -->
        <div class="table-operator"  style="margin-top: 5px">
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="handleAdd" type="primary" icon="plus">新增</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="batchDel" icon="delete">删除</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="batchSetStatus(true)" icon="check-square">启用</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="batchSetStatus(false)" icon="close-square">禁用</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="handleImportXls()" icon="import">导入</a-button>
          <a-button v-if="btnEnableList.indexOf(3)>-1" @click="handleExportXls('会员信息')" icon="download">导出</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="batchSetAdvanceIn()" icon="stock">修正预付款</a-button>
        </div>
        <!-- table区域-begin -->
        <div>
          <a-table
            ref="table"
            size="middle"
            bordered
            rowKey="id"
            :columns="columns"
            :dataSource="dataSource"
            :pagination="ipagination"
            :scroll="scroll"
            :loading="loading"
            :rowSelection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange}"
            @change="handleTableChange">
            <!-- 状态渲染模板 -->
            <template slot="customRenderFlag" slot-scope="enabled">
              <a-tag v-if="enabled" color="green">启用</a-tag>
              <a-tag v-if="!enabled" color="orange">禁用</a-tag>
            </template>
            <span slot="action" slot-scope="text, record">
              <a @click="handleEdit(record)">编辑</a>
              <a-divider type="vertical" />
              <a-popconfirm title="确定删除吗?" @confirm="() => handleDelete(record.id)">
                <a>删除</a>
              </a-popconfirm>
            </span>
          </a-table>
        </div>
        <!-- table区域-end -->
        <!-- 表单区域 -->
        <member-modal ref="modalForm" @ok="modalFormOk"></member-modal>
        <import-file-modal ref="modalImportForm" @ok="modalFormOk"></import-file-modal>
      </a-card>
    </a-col>
    </a-row>
  </div>
</template>
<!-- f r o m 7 5  2 7 1  8 9 2 0 -->
<script>
  import MemberModal from './modules/MemberModal'
  import MemberPreview from './modules/MemberPreview'
  import ImportFileModal from '@/components/tools/ImportFileModal'
  import MobileSearchPage from '@/components/mobile/MobileSearchPage'
  import MobileHeaderActions from '@/components/mobile/MobileHeaderActions'
  import { postAction } from '@/api/manage'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import JDate from '@/components/jeecg/JDate'
  import Vue from 'vue'
  export default {
    name: "MemberList",
    mixins:[JeecgListMixin],
    components: {
      MemberModal,
      MemberPreview,
      ImportFileModal,
      JDate,
      MobileSearchPage,
      MobileHeaderActions
    },
    data () {
      return {
        // 手机端：是否窄屏 + 搜索关键字
        isNarrowScreen: false,
        mobileKeyword: '',
        // 手机端条件筛选抽屉
        filterDrawerVisible: false,
        labelCol: {
          span: 5
        },
        wrapperCol: {
          span: 18,
          offset: 1
        },
        // 查询条件
        queryParam: {
          supplier:'',
          type:'会员',
          telephone:'',
          phonenum:'',
          email:'',
          wechatAccount:'',
          douyinAccount:'',
          alipayAccount:''
        },
        urlPath: '/system/member',
        ipagination:{
          pageSizeOptions: ['10', '20', '30', '100', '200']
        },
        // 表头 - 操作列固定在最后
        columns: [
          {
            title: '#',
            dataIndex: '',
            key:'rowIndex',
            width:60,
            align:"center",
            customRender:function (t,r,index) {
              return parseInt(index)+1;
            }
          },
          { title: '会员卡号',dataIndex: 'supplier',width:150,align:"left"},
          { title: '联系人', dataIndex: 'contacts',width:70,align:"left"},
          { title: '电话', dataIndex: 'telephone',width:120,align:"left"},
          { title: '电子邮箱', dataIndex: 'email',width:150,align:"left"},
          { title: '联系地址', dataIndex: 'address',width:200,align:"left"},
          { title: '微信账户', dataIndex: 'wechatAccount',width:120,align:"left"},
          { title: '抖音账户', dataIndex: 'douyinAccount',width:120,align:"left"},
          { title: '支付宝账户', dataIndex: 'alipayAccount',width:120,align:"left"},
          { title: '其它账户', dataIndex: 'otherAccount',width:120,align:"left"},
          { title: '预付款',dataIndex: 'advanceIn',width:70,align:"left"},
          { title: '排序', dataIndex: 'sort', width: 60,align:"left"},
          { title: '状态',dataIndex: 'enabled',width:60,align:"center",
            scopedSlots: { customRender: 'customRenderFlag' }
          },
          {
            title: '操作',
            dataIndex: 'action',
            width: 130,
            align:"center",
            fixed: 'right',
            scopedSlots: { customRender: 'action' },
          }
        ],
        url: {
          list: "/supplier/list",
          delete: "/supplier/delete",
          deleteBatch: "/supplier/deleteBatch",
          importExcelUrl: "/supplier/importMember",
          exportXlsUrl: "/supplier/exportExcel",
          batchSetStatusUrl: "/supplier/batchSetStatus",
          batchSetAdvanceInUrl: "/supplier/batchSetAdvanceIn"
        }
      }
    },
    mounted() {
      this.isNarrowScreen = window.innerWidth <= 768
      this._onResize = () => {
        this.isNarrowScreen = window.innerWidth <= 768
      }
      window.addEventListener('resize', this._onResize)
    },
    beforeDestroy() {
      window.removeEventListener('resize', this._onResize)
    },
    computed: {
      importExcelUrl: function () {
        return `${window._CONFIG['domianURL']}${this.url.importExcelUrl}`;
      },
      // 手机端顶栏图标按钮（与商品信息页共用 MobileHeaderActions 组件）
      mobileHeaderActions() {
        const can = this.btnEnableList
        return [
          { key: 'add', icon: 'plus', title: '新增', primary: true, visible: can.indexOf(1) > -1, click: this.handleAdd },
          { key: 'delete', icon: 'delete', title: '删除', visible: can.indexOf(1) > -1, click: this.batchDel },
          { key: 'enable', icon: 'check-square', title: '启用', visible: can.indexOf(1) > -1, click: () => this.batchSetStatus(true) },
          { key: 'disable', icon: 'close-square', title: '禁用', visible: can.indexOf(1) > -1, click: () => this.batchSetStatus(false) },
          { key: 'import', icon: 'import', title: '导入', visible: can.indexOf(1) > -1, click: () => this.handleImportXls() },
          { key: 'export', icon: 'download', title: '导出', visible: can.indexOf(3) > -1, click: () => this.handleExportXls('会员信息') }
        ]
      }
    },
    methods: {
      // 手机端搜索：数字按电话查询，其它按联系人（姓名）查询
      mobileSearch() {
        const kw = (this.mobileKeyword || '').trim()
        this.queryParam = {
          supplier: '',
          type: '会员',
          contacts: '',
          telephone: '',
          phonenum: '',
          email: '',
          wechatAccount: '',
          douyinAccount: '',
          alipayAccount: ''
        }
        if (kw) {
          if (/^[\d\s+-]+$/.test(kw)) {
            this.queryParam.telephone = kw
          } else {
            this.queryParam.contacts = kw
          }
        }
        this.loadData(1)
      },
      onMobilePageChange(page) {
        this.ipagination.current = page
        this.loadData(page)
      },
      // 手机端筛选抽屉：按条件重新查询
      applyMobileFilter() {
        this.filterDrawerVisible = false
        this.loadData(1)
      },
      searchReset() {
        this.queryParam = {
          type:'会员',
        }
        this.loadData(1);
      },
      handleImportXls() {
        let importExcelUrl = this.url.importExcelUrl
        let templateUrl = '/doc/member_template.xls'
        let templateName = '会员Excel模板[下载]'
        this.$refs.modalImportForm.initModal(importExcelUrl, templateUrl, templateName);
        this.$refs.modalImportForm.title = "会员导入";
      },
      handleEdit: function (record) {
        this.$refs.modalForm.edit(record);
        this.$refs.modalForm.title = "编辑";
        this.$refs.modalForm.disableSubmit = false;
        if(this.btnEnableList.indexOf(1)===-1) {
          this.$refs.modalForm.isReadOnly = true
        }
      },
      // 手机端：打开会员信息详情页（查看）
      openPreview(item) {
        this.$refs.memberPreview.open(item)
      },
      // 手机端：卡片「打印」直接弹出打印层，不再进入会员信息预览页
      printMember(item) {
        this.$refs.memberPreview.openPrint(item)
      },
      // 手机端卡片多选：当前行是否已选
      isMobileSelected(item) {
        return this.selectedRowKeys.indexOf(item.id) > -1
      },
      // 手机端卡片多选：切换勾选状态（复用弹窗/表格共用的 selectedRowKeys）
      toggleMobileSelect(item) {
        var keys = this.selectedRowKeys.slice()
        var idx = keys.indexOf(item.id)
        if (idx > -1) {
          keys.splice(idx, 1)
        } else {
          keys.push(item.id)
        }
        var rows = this.dataSource.filter(function(row) {
          return keys.indexOf(row.id) > -1
        })
        this.onSelectChange(keys, rows)
      },
      batchSetAdvanceIn() {
        if (this.selectedRowKeys.length <= 0) {
          this.$message.warning('请选择一条记录！');
        } else {
          let ids = "";
          for (let a = 0; a < this.selectedRowKeys.length; a++) {
            ids += this.selectedRowKeys[a] + ",";
          }
          let that = this;
          this.$confirm({
            title: "确认操作",
            content: "是否操作选中数据?",
            onOk: function () {
              that.loading = true;
              postAction(that.url.batchSetAdvanceInUrl, {ids: ids}).then((res) => {
                if(res.code === 200){
                  that.$message.info('修正预付款成功！');
                  that.loadData();
                  that.onClearSelected();
                } else {
                  that.$message.warning(res.data.message);
                }
              }).finally(() => {
                that.loading = false;
              });
            }
          });
        }
      }
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less'

  /* ==================== 手机端会员卡片 ==================== */
  .member-list-page {
    min-height: 100%;
  }
  /* 顶栏右侧图标按钮：统一由 MobileHeaderActions 组件提供样式，与商品信息页共用 */
  .mm-list {
    padding: 0;
  }
  .mm-card {
    display: flex;
    align-items: flex-start;
    padding: 10px 14px;
    margin-bottom: 10px;
    background: #fff;
    border-radius: 12px;
  }
  /* 多选勾图标：与商品信息页卡片一致（未选灰色、选中主题色） */
  .mm-card-check {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 28px;
    height: 28px;
    margin-top: 1px;
    margin-right: 4px;
    font-size: 20px;
    line-height: 1;
    color: #c8c9cc;
    cursor: pointer;
    transition: color 0.2s;
  }
  .mm-card-check.is-checked {
    color: #1890ff;
  }
  .mm-card-main {
    flex: 1;
    min-width: 0;
  }
  .mm-card-line {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
  }
  .mm-card-name {
    margin-right: 10px;
    font-size: 17px;
    font-weight: 600;
    color: #222;
  }
  .mm-card-phone {
    font-size: 15px;
    color: #444;
  }
  .mm-card-tag {
    margin-left: 8px;
    padding: 0 4px;
    font-size: 11px;
    line-height: 16px;
    color: #fa8c16;
    border: 1px solid #ffd591;
    border-radius: 4px;
  }
  .mm-card-address {
    margin-top: 4px;
    font-size: 14px;
    line-height: 1.5;
    color: #888;
    word-break: break-all;
  }
  /* 查看/打印按钮：横向排列并顶部对齐，整体上移、压缩卡片高度 */
  .mm-card-actions {
    flex-shrink: 0;
    display: flex;
    flex-direction: row;
    align-items: flex-start;
    justify-content: flex-end;
    align-self: flex-start;
    margin-top: -3px;
    margin-left: 6px;
  }
  .mm-card-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    font-size: 18px;
    color: #999;
    cursor: pointer;
  }
  .mm-card-btn + .mm-card-btn {
    margin-left: 2px;
  }
  .mm-empty {
    padding: 40px 0;
    text-align: center;
    font-size: 14px;
    color: #999;
  }
  .mm-pager {
    display: flex;
    justify-content: center;
    padding: 6px 0 12px;
  }
</style>

<style>
  /* 筛选抽屉挂载到 document.body，需使用全局样式 */
  .mb-filter-item {
    margin-bottom: 16px;
  }
  .mb-filter-label {
    display: block;
    margin-bottom: 6px;
    font-size: 13px;
    color: #666;
  }
  .mb-filter-footer {
    margin-top: 8px;
  }
</style>
