<template>
  <div class="member-list-page">
    <!-- ==================== 手机端：标准信息搜索页面 ==================== -->
    <mobile-search-page
      v-if="isNarrowScreen"
      v-model="mobileKeyword"
      title="会员信息"
      placeholder="搜索姓名 / 电话"
      :show-scan="false"
      :show-camera="false"
      :show-filter="false"
      @back="$router.back()"
      @search="mobileSearch">
      <template #actions>
        <div class="mm-header-actions">
          <a-button v-if="btnEnableList.indexOf(1)>-1" type="primary" size="small" icon="plus" title="新增" @click="handleAdd" />
          <a-button v-if="btnEnableList.indexOf(1)>-1" size="small" icon="import" title="导入" @click="handleImportXls" />
          <a-button v-if="btnEnableList.indexOf(3)>-1" size="small" icon="download" title="导出" @click="handleExportXls('会员信息')" />
        </div>
      </template>

      <!-- 会员卡片：姓名 + 电话 一行，地址换行 -->
      <div class="mm-list">
        <div v-for="item in dataSource" :key="item.id" class="mm-card">
          <div class="mm-card-main">
            <div class="mm-card-line">
              <span class="mm-card-name">{{ item.contacts || item.supplier || '-' }}</span>
              <span class="mm-card-phone">{{ item.telephone || '' }}</span>
              <span v-if="item.enabled === false" class="mm-card-tag">禁用</span>
            </div>
            <div class="mm-card-address">{{ item.address || '暂无地址' }}</div>
          </div>
          <div v-if="btnEnableList.indexOf(1)>-1" class="mm-card-edit" @click.stop="handleEdit(item)">
            <a-icon type="edit" />
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
        <member-modal ref="modalForm" @ok="modalFormOk"></member-modal>
        <import-file-modal ref="modalImportForm" @ok="modalFormOk"></import-file-modal>
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
  import ImportFileModal from '@/components/tools/ImportFileModal'
  import MobileSearchPage from '@/components/mobile/MobileSearchPage'
  import { postAction } from '@/api/manage'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import JDate from '@/components/jeecg/JDate'
  import Vue from 'vue'
  export default {
    name: "MemberList",
    mixins:[JeecgListMixin],
    components: {
      MemberModal,
      ImportFileModal,
      JDate,
      MobileSearchPage
    },
    data () {
      return {
        // 手机端：是否窄屏 + 搜索关键字
        isNarrowScreen: false,
        mobileKeyword: '',
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
  /* 顶栏右侧图标按钮 */
  .mm-header-actions {
    display: flex;
    align-items: center;
    height: 49px;
  }
  .mm-header-actions .ant-btn {
    width: 36px;
    height: 36px;
    margin-left: 6px;
    padding: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    background: transparent;
    border-color: transparent;
    box-shadow: none;
    color: #fff;
    font-size: 18px;
  }
  .mm-header-actions .ant-btn:hover,
  .mm-header-actions .ant-btn:focus,
  .mm-header-actions .ant-btn:active {
    background: rgba(255, 255, 255, 0.2);
    border-color: transparent;
    color: #fff;
  }
  .mm-list {
    padding: 0;
  }
  .mm-card {
    display: flex;
    align-items: center;
    padding: 16px;
    margin-bottom: 10px;
    background: #fff;
    border-radius: 12px;
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
    margin-top: 8px;
    font-size: 14px;
    line-height: 1.5;
    color: #888;
    word-break: break-all;
  }
  .mm-card-edit {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    width: 34px;
    height: 34px;
    margin-left: 8px;
    font-size: 18px;
    color: #999;
    cursor: pointer;
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
