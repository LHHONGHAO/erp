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
          <data-table
            ref="dataTable"
            row-key="skuRowKey"
            table-key="material_select"
            :extra-columns="extraColumns"
            :default-columns="defaultColumns"
            :data-source="tableRows"
            :loading="loading"
            :pagination="ipagination"
            :row-selection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange,type: getType}"
            :scroll="scrollTrigger"
            :custom-row="rowAction"
            @change="handleTableChange"
          >
            <template slot="customBarCode" slot-scope="text, record">
              {{record.mBarCode || record.barCode}}
            </template>
            <template slot="customName" slot-scope="text, record">
              {{record.name}}
              <a-tag v-if="record.enableSerialNumber==1" color="orange">序</a-tag>
              <a-tag v-if="record.enableBatchNumber==1" color="orange">批</a-tag>
            </template>
            <template slot="customSkuImg" slot-scope="text, record">
              <div v-if="record.imgName" style="text-align:center">
                <img :src="getImgUrl(record.imgName)" style="width:50px;height:50px;object-fit:cover;border:1px solid #eee;border-radius:3px;" />
              </div>
              <div v-else style="text-align:center;color:#ccc;font-size:24px;">
                <a-icon type="picture" />
              </div>
            </template>
            <template slot="customSpecValues" slot-scope="text, record">
              {{ parseSpecValues(record.specValues) }}
            </template>
            <template slot="customRenderStock" slot-scope="text, record">
              <a-tooltip :title="record.bigUnitStock">
                {{text}}
              </a-tooltip>
            </template>
          </data-table>
        </div>
      </a-col>
    </a-row>
    <material-modal ref="materialModalForm" @ok="modalFormOk"></material-modal>
  </a-modal>
</template>

<script>
  import { getAction, getFileAccessHttpUrl } from '@/api/manage'
  import {filterObj, getMpListShort} from '@/utils/util'
  import {queryMaterialCategoryTreeList} from '@/api/api'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import {mixinDevice} from '@/utils/mixin'
  import DataTable from '@/components/DataTable'
  import Vue from 'vue'

  export default {
    name: 'JSelectMaterialModal',
    mixins:[JeecgListMixin, mixinDevice],
    components: {
      MaterialModal: () => import('@/views/material/modules/MaterialModal'),
      DataTable
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
        // 商品信息 + SKU信息（一行一个SKU）
        extraColumns: [
          {title: '名称', dataIndex: 'name', width: 160, scopedSlots: { customRender: 'customName' }},
          {title: '商品编码', dataIndex: 'code', width: 130},
          {title: '图片', dataIndex: 'imgName', width: 70, align: 'center', scopedSlots: { customRender: 'customSkuImg' }},
          {title: 'SKU编码', dataIndex: 'skuCode', width: 160},
          {title: '规格值', dataIndex: 'specValues', width: 180, scopedSlots: { customRender: 'customSpecValues' }},
          {title: '单位', dataIndex: 'unit', width: 100, ellipsis:true},
          {title: '品牌', dataIndex: 'brand', width: 100, ellipsis:true},
          {title: '助记码', dataIndex: 'mnemonic', width: 80, ellipsis:true},
          {title: '类别', dataIndex: 'categoryName', width: 100, ellipsis:true},
          {title: '基础重量', dataIndex: 'weight', width: 80},
          {title: '制造商', dataIndex: 'mfrs', width: 120, ellipsis:true},
          {title: '面料', dataIndex: 'otherField1', width: 100, ellipsis:true},
          {title: '安全类别', dataIndex: 'otherField2', width: 100, ellipsis:true},
          {title: '执行标准', dataIndex: 'otherField3', width: 100, ellipsis:true},
          {title: '扩展4', dataIndex: 'otherField4', width: 100, ellipsis:true},
          {title: '扩展5', dataIndex: 'otherField5', width: 100, ellipsis:true},
          {title: '扩展6', dataIndex: 'otherField6', width: 100, ellipsis:true},
          {title: '扩展7', dataIndex: 'otherField7', width: 100, ellipsis:true},
          {title: '商品ID', dataIndex: 'id', width: 80},
          {title: '条码', dataIndex: 'mBarCode', width: 120, scopedSlots: { customRender: 'customBarCode' }},
          {title: '初始库存', dataIndex: 'initialStock', width: 80},
          {title: '库存', dataIndex: 'stock', width: 80, scopedSlots: { customRender: 'customRenderStock' }},
          {title: '采购价', dataIndex: 'purchaseDecimal', width: 80},
          {title: '零售价', dataIndex: 'commodityDecimal', width: 80},
          {title: '销售价', dataIndex: 'wholesaleDecimal', width: 80},
          {title: '最低售价', dataIndex: 'lowDecimal', width: 80},
          {title: '备注', dataIndex: 'remark', width: 120, ellipsis:true}
        ],
        // 默认显示的列（商品信息 + SKU信息）
        defaultColumns: ['name','code','imgName','skuCode','specValues','unit','brand','mnemonic','categoryName','weight','mfrs',
          'otherField1','otherField2','otherField3','mBarCode','stock','purchaseDecimal','commodityDecimal','wholesaleDecimal','lowDecimal'],
        scrollTrigger: {x: 1800},
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
      // 触发屏幕自适应
      resetScreenSize() {
        let realScreenWidth = window.screen.width
        this.modalWidth = realScreenWidth<1600?'1300px':'1550px'
        let screenWidth = document.body.clientWidth;
        if (screenWidth < 500) {
          this.scrollTrigger = {x: 1800};
        } else {
          this.scrollTrigger = {x: 1800};
        }
      },
      //动态替换扩展字段
      handleChangeOtherField() {
        let mpStr = getMpListShort(Vue.ls.get('materialPropertyList'))
        if(mpStr) {
          let mpArr = mpStr.split(',')
          if(mpArr.length ===3) {
            this.queryTitle.mp1 = mpArr[0]
            this.queryTitle.mp2 = mpArr[1]
            this.queryTitle.mp3 = mpArr[2]
            this.$nextTick(() => {
              const dt = this.$refs.dataTable
              if (dt) {
                dt.updateColumnTitle('otherField1', mpArr[0])
                dt.updateColumnTitle('otherField2', mpArr[1])
                dt.updateColumnTitle('otherField3', mpArr[2])
              }
            })
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
      getImgUrl(imgName, type) {
        if(imgName && imgName.split(',')) {
          type = type? type + '/':''
          return getFileAccessHttpUrl('systemConfig/static/' + type + imgName.split(',')[0])
        } else {
          return ''
        }
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
      /** 规格值JSON转为“颜色:蓝色 / 规格:200*230” */
      parseSpecValues(specValues) {
        if (!specValues) return ''
        try {
          const arr = JSON.parse(specValues)
          if (!Array.isArray(arr)) return specValues
          return arr.map(item => item.attributeName + ':' + item.valueText).join(' / ')
        } catch (e) {
          return specValues
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