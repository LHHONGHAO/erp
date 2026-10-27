<template>
  <a-modal
    :width="modalWidth"
    :visible="visible"
    :title="title"
    :wrapClassName="wrapClassNameInfo()"
    @ok="handleSubmit"
    @cancel="close"
    cancelText="关闭(ESC)"
    style="top:20px;height: 95%;"
  >
    <a-row :gutter="10" style="padding: 10px; margin: -10px">
      <a-col :md="24" :sm="24">
        <!-- 查询区域 -->
        <div class="table-page-search-wrapper">
          <!-- 搜索区域 -->
          <a-form layout="inline" @keyup.enter.native="onSearch">
            <a-row :gutter="24">
              <a-col :md="6" :sm="8">
                <a-form-item label="关键词" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input ref="material" placeholder="请输入条码、名称、助记码等查询" v-model="queryParam.q"></a-input>
                </a-form-item>
              </a-col>
              <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
                <a-col :md="6" :sm="8">
                  <a-button type="primary" @click="loadMaterialData(1)">查询</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset(1)">重置</a-button>
                  <a-tooltip title="没查询到，决定新增商品！">
                    <a-button style="margin-left: 8px" @click="addMaterial">新增</a-button>
                  </a-tooltip>
                  <a @click="handleToggleSearch" style="margin-left: 8px">
                    {{ toggleSearchStatus ? '收起' : '展开' }}
                    <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
                  </a>
                </a-col>
              </span>
            </a-row>
            <template v-if="toggleSearchStatus">
              <a-row :gutter="24">
                <a-col :md="6" :sm="8">
                  <a-form-item label="品牌" :labelCol="{span: 5}" :wrapperCol="{span: 18, offset: 1}">
                    <a-input placeholder="请输入品牌" v-model="queryParam.brand"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="8">
                  <a-form-item :labelCol="labelCol" :wrapperCol="wrapperCol" label="类别">
                    <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}" allow-clear
                                   :treeData="categoryTree" v-model="queryParam.categoryId" placeholder="请选择类别">
                    </a-tree-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="8">
                  <a-form-item label="制造商" :labelCol="{span: 5}" :wrapperCol="{span: 18, offset: 1}">
                    <a-input placeholder="请输入制造商" v-model="queryParam.mfrs"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="8">
                  <a-form-item :label="queryTitle.mp1" :labelCol="{span: 5}" :wrapperCol="{span: 18, offset: 1}">
                    <a-input :placeholder="'请输入'+ queryTitle.mp1" v-model="queryParam.otherField1"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="8">
                  <a-form-item :label="queryTitle.mp2" :labelCol="{span: 5}" :wrapperCol="{span: 18, offset: 1}">
                    <a-input :placeholder="'请输入'+ queryTitle.mp2" v-model="queryParam.otherField2"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="8">
                  <a-form-item :label="queryTitle.mp3" :labelCol="{span: 5}" :wrapperCol="{span: 18, offset: 1}">
                    <a-input :placeholder="'请输入'+ queryTitle.mp3" v-model="queryParam.otherField3"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="序列号" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="有无序列号" v-model="queryParam.enableSerialNumber">
                      <a-select-option value="1">有</a-select-option>
                      <a-select-option value="0">无</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="批号" :labelCol="{span: 5}" :wrapperCol="{span: 18, offset: 1}">
                    <a-select placeholder="有无批号" v-model="queryParam.enableBatchNumber">
                      <a-select-option value="1">有</a-select-option>
                      <a-select-option value="0">无</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>
            </template>
          </a-form>
          <material-table
            ref="dataTable"
            row-key="skuRowKey"
            table-key="material_select_v2"
            dimension="sku"
            :data-source="tableRows"
            :loading="loading"
            :pagination="ipagination"
            :row-selection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange,type: getType}"
            :custom-row="rowAction"
            @change="handleTableChange"
          ></material-table>
        </div>
      </a-col>
    </a-row>
    <material-modal ref="materialModalForm" @ok="modalFormOk"></material-modal>
  </a-modal>
</template>

<script>
  import { getAction } from '@/api/manage'
  import {filterObj, getMpListShort} from '@/utils/util'
  import {queryMaterialCategoryTreeList} from '@/api/api'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import {mixinDevice} from '@/utils/mixin'
  import MaterialTable from '@/components/material/MaterialTable'
  import Vue from 'vue'

  export default {
    name: 'JSelectMaterialModal',
    mixins:[JeecgListMixin, mixinDevice],
    components: {
      MaterialModal: () => import('@/views/material/modules/MaterialModal'),
      MaterialTable
    },
    props: ['rows', 'multi', 'barCode'],
    data() {
      return {
        modalWidth: 1450,
        queryTitle: {
          mp1: '面料',
          mp2: '安全类别',
          mp3: '执行标准'
        },
        queryParam: {
          q: '',
          brand: '',
          categoryId: undefined,
          mfrs: '',
          otherField1:'',
          otherField2:'',
          otherField3:'',
          enableSerialNumber: undefined,
          enableBatchNumber: undefined
        },
        labelCol: {
          xs: { span: 24 },
          sm: { span: 5 },
        },
        wrapperCol: {
          xs: { span: 24 },
          sm: { span: 16 },
        },
        categoryTree:[],
        dataSource: [],
        selectedRowKeys: [],
        selectMaterialRows: [],
        selectMaterialIds: [],
        title: '选择商品',
        ipagination: {
          current: 1,
          pageSize: 10,
          pageSizeOptions: ['10', '20', '30'],
          showTotal: (total, range) => {
            return range[0] + '-' + range[1] + ' 共' + total + '条'
          },
          showQuickJumper: true,
          showSizeChanger: true,
          total: 0
        },
        isorter: {
          column: 'createTime',
          order: 'desc'
        },
        departTree: [],
        depotList: [],
        visible: false,
        form: this.$form.createForm(this),
        loading: false,
        expandedKeys: [],
        disableMixinCreated: true,
      }
    },
    computed: {
      // 计算属性的 getter
      getType: function () {
        return this.multi == true ? 'checkbox' : 'radio';
      },
      // 一行一个SKU，补充唯一行标识（商品ID + SKU_ID）
      tableRows() {
        return (this.dataSource || []).map(row => Object.assign({}, row, {
          skuRowKey: row.id + '_' + (row.meId != null ? row.meId : 0)
        }))
      }
    },
    watch: {
      barCode: {
        immediate: true,
        handler() {
          this.initBarCode()
        }
      },
    },
    created() {
      // 该方法触发屏幕自适应
      this.resetScreenSize()
      this.handleChangeOtherField()
    },
    methods: {
      initBarCode() {
        if (this.barCode) {
          this.$emit('initComp', this.barCode)
        } else {
          // JSelectUserByDep组件bug issues/I16634
          this.$emit('initComp', '')
        }
      },
      loadMaterialData(arg) {
        if (arg === 1) {
          this.ipagination.current = 1;
        }
        this.loading = true
        let params = this.getQueryParams()//查询条件
        getAction('/material/listSku', params).then((res) => {
          if (res && res.code === 200 && res.data) {
            this.dataSource = res.data.rows || []
            this.ipagination.total = res.data.total || 0
            if(this.ipagination.total ===1 && this.dataSource.length === 1) {
              let row = this.dataSource[0]
              let rowBarCode = row.mBarCode || row.barCode
              if(this.queryParam.q === rowBarCode||
                this.queryParam.q === row.name||
                this.queryParam.q === row.mnemonic) {
                this.title = '选择商品【再次回车可以直接选中】'
                this.$nextTick(() => this.$refs.material.focus());
              } else {
                this.title = '选择商品'
              }
            } else {
              this.title = '选择商品'
            }
          }
          this.loading = false
          this.onClearSelected()
        })
      },
      loadTreeData(){
        let that = this;
        let params = {};
        params.id='';
        queryMaterialCategoryTreeList(params).then((res)=>{
          if(res){
            that.categoryTree = [];
            for (let i = 0; i < res.length; i++) {
              let temp = res[i];
              that.categoryTree.push(temp);
            }
          }
        })
      },
      // 触发屏幕自适应（弹窗宽度按内容区宽度计算，避免超出浏览器窗口）
      resetScreenSize() {
        const avail = document.body.clientWidth || window.innerWidth
        const target = avail >= 1600 ? 1550 : avail - 20
        this.modalWidth = Math.max(Math.min(target, 1550), 900) + 'px'
      },
      //动态替换扩展字段（仅查询条件标题，列标题由统一列配置提供）
      handleChangeOtherField() {
        let mpStr = getMpListShort(Vue.ls.get('materialPropertyList'))
        if(mpStr) {
          let mpArr = mpStr.split(',')
          if(mpArr.length ===3) {
            this.queryTitle.mp1 = mpArr[0]
            this.queryTitle.mp2 = mpArr[1]
            this.queryTitle.mp3 = mpArr[2]
          }
        }
      },
      showModal(barCode) {
        this.visible = true;
        this.title = '选择商品'
        this.queryParam.q = barCode
        this.$nextTick(() => this.$refs.material.focus());
        this.loadTreeData()
        this.getDepotList()
        this.initDepotSelect()
        this.loadMaterialData();
        this.form.resetFields();
      },
      getQueryParams() {
        let searchObj = {
          materialParam: this.queryParam.q,
          brand: this.queryParam.brand,
          categoryId: this.queryParam.categoryId,
          mfrs: this.queryParam.mfrs,
          otherField1: this.queryParam.otherField1,
          otherField2: this.queryParam.otherField2,
          otherField3: this.queryParam.otherField3,
          enableSerialNumber: this.queryParam.enableSerialNumber,
          enableBatchNumber: this.queryParam.enableBatchNumber,
          mpList: getMpListShort(Vue.ls.get('materialPropertyList'))  //扩展属性
        }
        return {
          search: JSON.stringify(filterObj(searchObj)),
          column: this.isorter.column,
          order: this.isorter.order,
          currentPage: this.ipagination.current,
          pageSize: this.ipagination.pageSize
        }
      },
      getQueryField() {
        let str = 'id,';
        for (let a = 0; a < this.columns.length; a++) {
          str += ',' + this.columns[a].dataIndex;
        }
        return str;
      },
      searchReset(num) {
        let that = this;
        if (num !== 0) {
          that.queryParam = {};
          that.loadMaterialData(1);
        }
        that.selectedRowKeys = [];
        that.selectMaterialIds = [];
      },
      addMaterial() {
        this.$refs.materialModalForm.add()
        this.$refs.materialModalForm.title = '新增商品'
      },
      close() {
        this.searchReset(0);
        this.visible = false;
      },
      handleTableChange(pagination, filters, sorter) {
        if (Object.keys(sorter).length > 0) {
          this.isorter.column = sorter.field;
          this.isorter.order = 'ascend' === sorter.order ? 'asc' : 'desc';
        }
        this.ipagination = pagination;
        this.loadMaterialData();
      },
      handleSubmit() {
        let that = this;
        this.getSelectMaterialRows();
        if(that.selectMaterialRows.length>0) {
          that.$emit('ok', that.selectMaterialRows, that.selectMaterialIds);
          that.searchReset(0)
          that.close();
        } else {
          that.$message.warning('请选择商品！')
        }
      },
      //获取选择信息
      getSelectMaterialRows(rowId) {
        let dataSource = this.dataSource;
        let materialIds = "";
        this.selectMaterialRows = [];
        for (let i = 0, len = dataSource.length; i < len; i++) {
          let rowKey = dataSource[i].id + '_' + (dataSource[i].meId != null ? dataSource[i].meId : 0)
          if (this.selectedRowKeys.includes(rowKey)) {
            this.selectMaterialRows.push(dataSource[i]);
            materialIds = materialIds + "," + (dataSource[i].mBarCode || dataSource[i].barCode || '')
          }
        }
        this.selectMaterialIds = materialIds.substring(1);
      },
      getDepotList() {
        let that = this;
        getAction('/depot/findDepotByCurrentUser').then((res) => {
          if(res.code === 200){
            that.depotList = res.data
          }
        })
      },
      initDepotSelect() {
        if(this.rows) {
          if(JSON.parse(this.rows).depotId){
            this.queryParam.depotId = JSON.parse(this.rows).depotId-0
          }
        }
      },
      onDepotChange(value) {
        this.queryParam.depotId = value
      },
      onSelectChange(selectedRowKeys, selectionRows) {
        this.selectedRowKeys = selectedRowKeys;
        this.selectionRows = selectionRows;
      },
      onSearch() {
        if(this.dataSource && this.dataSource.length===1) {
          let row = this.dataSource[0]
          let rowBarCode = row.mBarCode || row.barCode
          if(this.queryParam.q === rowBarCode||
            this.queryParam.q === row.name||
            this.queryParam.q === row.mnemonic) {
            let arr = []
            arr.push(row.id + '_' + (row.meId != null ? row.meId : 0))
            this.selectedRowKeys = arr
            this.handleSubmit()
          } else {
            this.loadMaterialData(1)
          }
        } else {
          this.loadMaterialData(1)
        }
      },
      modalFormOk() {
        this.loadMaterialData()
      },
      rowAction(record, index) {
        return {
          on: {
            dblclick: () => {
              let arr = []
              arr.push(record.id + '_' + (record.meId != null ? record.meId : 0))
              this.selectedRowKeys = arr
              this.handleSubmit()
            }
          }
        }
      },
    }
  }
</script>

<style scoped>
  .ant-table-tbody .ant-table-row td {
    padding-top: 10px;
    padding-bottom: 10px;
  }

  #components-layout-demo-custom-trigger .trigger {
    font-size: 18px;
    line-height: 64px;
    padding: 0 24px;
    cursor: pointer;
    transition: color .3s;
  }

  .item-info {
    float:left;
    width:30px;
    height:30px;
    margin-left:8px
  }
  .item-img {
    cursor:pointer;
    position: static;
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
</style>