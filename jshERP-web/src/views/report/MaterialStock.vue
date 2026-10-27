<!-- from 7 5 2 7 1 8 9 2 0 -->
<template>
  <a-row :gutter="24">
    <a-col :md="24">
      <a-card :style="cardStyle" :bordered="false">
        <!-- 查询区域 -->
        <div class="table-page-search-wrapper">
          <a-form layout="inline" @keyup.enter.native="searchQuery">
            <a-row :gutter="24">
              <a-col :md="5" :sm="24">
                <a-form-item label="仓库" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-select
                    mode="multiple" :maxTagCount="1"
                    optionFilterProp="children"
                    showSearch style="width: 100%"
                    placeholder="请选择仓库"
                    v-model="depotSelected">
                    <a-select-option v-for="(depot,index) in depotList" :key="index" :value="depot.id">
                      {{ depot.depotName }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="商品信息" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入条码、名称、助记码、规格、型号等信息" v-model="queryParam.materialParam"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24" >
                <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
                  <a-button type="primary" @click="searchQuery">查询</a-button>
                  <a-button type="primary" style="margin-left: 8px" icon="plus" @click="addInitialStock">添加期初库存</a-button>
                  <a-button style="margin-left: 8px" v-print="'#reportPrint'" icon="printer">打印</a-button>
                  <a-button style="margin-left: 8px" @click="exportExcel" icon="download">导出</a-button>
                  <a @click="handleToggleSearch" style="margin-left: 8px">
                    {{ toggleSearchStatus ? '收起' : '展开' }}
                    <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
                  </a>
                </span>
              </a-col>
              <a-col :md="9" :sm="24">
                <a-form-item>
                  <span v-if="showStockPrice">总库存：{{currentStock}}，总库存金额：{{currentStockPrice}}，总重量：{{currentWeight}}</span>
                  <span v-if="!showStockPrice">总库存：{{currentStock}}，总重量：{{currentWeight}}</span>
                </a-form-item>
              </a-col>
              <a-col :md="15" :sm="24">
                <a-form-item label="显示方式">
                  <a-radio-group v-model="displayMode" @change="onDisplayModeChange">
                    <a-radio-button value="spu">单类显示</a-radio-button>
                    <a-radio-button value="sku">单品显示</a-radio-button>
                    <a-radio-button value="unit">单件显示</a-radio-button>
                  </a-radio-group>
                </a-form-item>
              </a-col>
            </a-row>
            <template v-if="toggleSearchStatus">
              <a-row :gutter="24">
                <a-col :md="5" :sm="24">
                  <a-form-item label="类别" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}" allow-clear
                                   :treeData="categoryTree" v-model="queryParam.categoryId" placeholder="请选择类别">
                    </a-tree-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="仓位货架" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input style="width: 100%" placeholder="请输入仓位货架查询" v-model="queryParam.position"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="零库存" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select v-model="queryParam.zeroStock">
                      <a-select-option value="0">隐藏</a-select-option>
                      <a-select-option value="1">显示</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>
            </template>
          </a-form>
        </div>
        <!-- table区域-begin -->
        <section ref="print" id="reportPrint">
          <material-table
            ref="table"
            bordered
            size="middle"
            table-key="material_stock"
            storage-key="columns_material_stock"
            dimension="skuDepot"
            merge-code
            :code-mode="displayMode"
            :default-columns="defaultColumns"
            :actions="rowActions"
            :row-key="getRowKey"
            :data-source="dataSource"
            :pagination="ipagination"
            :scroll="scroll"
            :loading="loading"
            :summary="true"
            summary-fields="initialStock,currentStock,currentStockPrice,currentWeight"
            @change="handleTableChange">
          </material-table>
        </section>
        <!-- table区域-end -->
        <material-in-out-list ref="materialInOutList" @ok="modalFormOk"></material-in-out-list>
        <material-depot-stock-list ref="materialDepotStockList" @ok="modalFormOk"></material-depot-stock-list>
        <!-- 添加期初库存：打开新增（其它）入库弹层 -->
        <other-in-modal ref="otherInModal" @ok="modalFormOk" @close="modalFormClose"></other-in-modal>
      </a-card>
    </a-col>
  </a-row>
</template>
<script>
  import MaterialInOutList from './modules/MaterialInOutList'
  import MaterialDepotStockList from './modules/MaterialDepotStockList'
  import OtherInModal from '../bill/modules/OtherInModal'
  import MaterialTable from '@/components/material/MaterialTable'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { getAction } from '@/api/manage'
  import {queryMaterialCategoryTreeList} from '@/api/api'
  import { getMpListShort } from "@/utils/util"
  import { CODE_MODES, STOCK_DEFAULT_COLUMNS } from '@/views/material/config/materialColumns'
  import Vue from 'vue'
  export default {
    name: "MaterialStock",
    mixins:[JeecgListMixin],
    components: {
      MaterialInOutList,
      MaterialDepotStockList,
      OtherInModal,
      MaterialTable
    },
    data () {
      return {
        labelCol: {
          span: 5
        },
        wrapperCol: {
          span: 18,
          offset: 1
        },
        // 查询条件
        queryParam: {
          categoryId: undefined,
          materialParam:'',
          position:'',
          zeroStock: '0',
          mpList: getMpListShort(Vue.ls.get('materialPropertyList'))  //扩展属性
        },
        depotSelected:[],
        depotList: [],
        categoryTree:[],
        currentStock: '',
        currentStockPrice: '',
        currentWeight: '',
        showStockPrice: false,
        skipFirstStockActivation: true,
        displayMode: 'spu',
        // 单类/单品/单件三种显示方式共用同一套列设置，默认列只在首次加载时生效
        defaultColumns: STOCK_DEFAULT_COLUMNS,
        url: {
          list: "/material/getListWithStock"
        }
      }
    },
    computed: {
      /** 操作列按钮：流水（需有库存金额权限且非单件）、分布（非单件） */
      rowActions() {
        const mode = this.displayMode
        return [
          {
            title: '流水',
            click: record => this.showMaterialInOutList(record),
            show: () => this.showStockPrice && mode !== 'unit'
          },
          {
            title: '分布',
            click: record => this.showMaterialDepotStockList(record),
            show: () => mode !== 'unit'
          }
        ]
      }
    },
    created() {
      this.getDepotData()
      this.loadCategoryTreeData()
    },
    activated() {
      // 首次创建时 JeecgListMixin 已加载列表；缓存页面再次打开时主动刷新库存。
      if (this.skipFirstStockActivation) {
        this.skipFirstStockActivation = false
        return
      }
      this.loadData(1)
    },
    methods: {
      getQueryParams() {
        let param = Object.assign({}, this.queryParam, this.isorter);
        if(this.depotSelected && this.depotSelected.length>0) {
          param.depotIds = this.depotSelected.join()
        }
        param.currentPage = this.ipagination.current;
        param.pageSize = this.ipagination.pageSize;
        return param;
      },
      getDepotData() {
        getAction('/depot/findDepotByCurrentUser').then((res)=>{
          if(res.code === 200){
            this.depotList = res.data;
          }else{
            this.$message.info(res.data);
          }
        })
      },
      loadCategoryTreeData(){
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
      searchQuery() {
        this.loadData(1);
      },
      // 添加期初库存：直接打开新增（其它）入库弹层，保存后生成入库单并更新库存
      addInitialStock() {
        let modal = this.$refs.otherInModal
        modal.action = 'add'
        modal.isCanCheck = true
        modal.title = '新增入库'
        modal.add()
      },
      onDisplayModeChange() {
        // 三种显示方式共用同一套列设置，切换时只重新取数，不增删列
        this.loadData(1)
      },
      displayMaterialCode(record) {
        if (!record) return ''
        const mode = CODE_MODES[this.displayMode] || CODE_MODES.spu
        return record[mode.field] || record.mBarCode || ''
      },
      getRowKey(record) {
        if (!record) return ''
        if (record._dataTableSummary) return '_summary'
        return record.unitCode || record.meId || record.id
      },
      loadData(arg) {
        //加载数据 若传入参数1则加载第一页的内容
        if (arg === 1) {
          this.ipagination.current = 1;
        }
        let params = this.getQueryParams();//查询条件
        params.displayMode = this.displayMode
        this.loading = true;
        getAction(this.url.list, params).then((res) => {
          if (res.code===200) {
            this.dataSource = res.data.rows;
            this.ipagination.total = res.data.total;
            this.currentStock = res.data.currentStock.toFixed(2)
            this.currentStockPrice = res.data.currentStockPrice.toFixed(2)
            this.currentWeight = res.data.currentWeight.toFixed(2)
            this.showStockPrice = res.data.showStockPrice
          } else if(res.code===510){
            this.$message.warning(res.data)
          } else {
            this.$message.warning(res.data.message)
          }
          this.loading = false;
        })
      },
      showMaterialInOutList(record) {
        let depotIds = ''
        if(this.depotSelected && this.depotSelected.length>0) {
          depotIds = this.depotSelected.join()
        }
        this.$refs.materialInOutList.show(record, depotIds);
        this.$refs.materialInOutList.title = "查看商品库存流水";
        this.$refs.materialInOutList.disableSubmit = false;
      },
      showMaterialDepotStockList(record) {
        let depotIds = ''
        if(this.depotSelected && this.depotSelected.length>0) {
          depotIds = this.depotSelected.join()
        }
        this.$refs.materialDepotStockList.show(record, depotIds);
        this.$refs.materialDepotStockList.title = "查看商品库存分布（条码：" + record.mBarCode + "，名称：" + record.name + "）";
        this.$refs.materialDepotStockList.disableSubmit = false;
      },
      exportExcel() {
        // 按当前实际显示的列导出（列顺序、勾选结果与界面一致）
        const visible = (this.$refs.table && this.$refs.table.getVisibleColumns()) || []
        const cols = visible.filter(col => col.dataIndex && col.dataIndex !== 'action' && col.dataIndex !== 'imgName')
        const head = cols.map(col => col.title).join(',')
        const list = this.dataSource.map(ds => cols.map(col => {
          return col.dataIndex === 'materialCode' ? this.displayMaterialCode(ds) : ds[col.dataIndex]
        }))
        this.handleExportXlsPost('商品库存', '商品库存', head, '商品库存查询', list)
      }
    }
  }
</script>

<style scoped>
  @import '~@assets/less/common.less'
</style>
