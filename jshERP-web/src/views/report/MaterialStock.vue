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
                  <a-button style="margin-left: 8px" v-print="'#reportPrint'" icon="printer">打印</a-button>
                  <a-button style="margin-left: 8px" @click="exportExcel" icon="download">导出</a-button>
                  <a @click="handleToggleSearch" style="margin-left: 8px">
                    {{ toggleSearchStatus ? '收起' : '展开' }}
                    <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
                  </a>
                </span>
              </a-col>
              <a-col :md="7" :sm="24">
                <a-form-item>
                  <span v-if="showStockPrice">总库存：{{currentStock}}，总库存金额：{{currentStockPrice}}，总重量：{{currentWeight}}</span>
                  <span v-if="!showStockPrice">总库存：{{currentStock}}，总重量：{{currentWeight}}</span>
                </a-form-item>
              </a-col>
              <a-col :md="18" :sm="24">
                <a-form-item label="显示方式">
                  <a-radio-group v-model="displayMode" @change="onDisplayModeChange">
                    <a-radio-button value="spu">单类显示</a-radio-button>
                    <a-radio-button value="sku">单品显示</a-radio-button>
                    <a-radio-button value="unit">单件显示</a-radio-button>
                  </a-radio-group>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24" class="column-setting-col">
                <a-popover trigger="click" placement="bottomRight">
                  <template slot="content">
                    <a-checkbox-group @change="onColChange" v-model="settingDataIndex" :defaultValue="settingDataIndex">
                      <a-row style="width: 600px">
                        <template v-for="item in defColumns">
                          <a-col :span="6" :key="item.dataIndex">
                            <a-checkbox :value="item.dataIndex" v-if="item.dataIndex==='rowIndex'" disabled></a-checkbox>
                            <a-checkbox :value="item.dataIndex" v-else>
                              <j-ellipsis :value="item.title" :length="10"></j-ellipsis>
                            </a-checkbox>
                          </a-col>
                        </template>
                      </a-row>
                    </a-checkbox-group>
                    <a-row style="padding-top: 10px;">
                        <a-col>
                          <a-checkbox :checked="columnsAllChecked" @change="handleSelectAllColumns">全选</a-checkbox>
                          恢复默认列配置：<a-button @click="handleRestDefault" type="link" size="small">恢复默认</a-button>
                        </a-col>
                      </a-row>
                    
                  </template>
                  <a-button icon="setting">列设置</a-button>
                </a-popover>
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
          <a-table
            bordered
            ref="table"
            size="middle"
            :rowKey="getRowKey"
            :columns="columns"
            :dataSource="dataSource"
            :components="handleDrag(columns)"
            :pagination="false"
            :scroll="scroll"
            :loading="loading"
            @change="handleTableChange">
            <span slot="customTitle">
              <a-popover trigger="click" placement="right">
                <template slot="content">
                  <a-checkbox-group @change="onColChange" v-model="settingDataIndex" :defaultValue="settingDataIndex">
                    <a-row style="width: 600px">
                      <template v-for="(item,index) in defColumns">
                        <template>
                          <a-col :span="6">
                            <a-checkbox :value="item.dataIndex" v-if="item.dataIndex==='rowIndex'" disabled></a-checkbox>
                            <a-checkbox :value="item.dataIndex" v-if="item.dataIndex!=='rowIndex'">
                              <j-ellipsis :value="item.title" :length="10"></j-ellipsis>
                            </a-checkbox>
                          </a-col>
                        </template>
                      </template>
                    </a-row>
                  </a-checkbox-group>
                  <a-row style="padding-top: 10px;">
                    <a-col>
                      <a-checkbox :checked="columnsAllChecked" @change="handleSelectAllColumns">全选</a-checkbox>
                      恢复默认列配置：<a-button @click="handleRestDefault" type="link" size="small">恢复默认</a-button>
                    </a-col>
                  </a-row>
                </template>
                <a-icon type="setting" />
              </a-popover>
            </span>
            <span slot="action" slot-scope="text, record">
              <a @click="showMaterialInOutList(record)" v-if="showStockPrice && displayMode !== 'unit'">{{record.id?'流水':''}}</a>
              <a-divider type="vertical" v-if="showStockPrice && displayMode !== 'unit'" />
              <a @click="showMaterialDepotStockList(record)" v-if="displayMode !== 'unit'">{{record.id?'分布':''}}</a>
            </span>
            <span slot="materialCode" slot-scope="text, record">
              {{ displayMaterialCode(record) }}
            </span>
            <template slot="customPic" slot-scope="text, record">
              <a-popover placement="right" trigger="click">
                <template slot="content">
                  <img :src="getImgUrl(record.imgName, record.imgLarge)" width="500px" />
                </template>
                <div class="item-info" v-if="record.imgName">
                  <img v-if="record.imgName" :src="getImgUrl(record.imgName, record.imgSmall)" class="item-img" title="查看大图" />
                </div>
              </a-popover>
            </template>
            <template slot="customRenderStock" slot-scope="text, record">
              <a-tooltip :title="record.bigUnitStock">
                {{text}}
              </a-tooltip>
            </template>
          </a-table>
          <a-row :gutter="24" style="margin-top: 8px;text-align:right;">
            <a-col :md="24" :sm="24">
              <a-pagination @change="paginationChange" @showSizeChange="paginationShowSizeChange"
                size="small"
                show-size-changer
                :showQuickJumper="true"
                :current="ipagination.current"
                :page-size="ipagination.pageSize"
                :page-size-options="ipagination.pageSizeOptions"
                :total="ipagination.total"
                :show-total="(total, range) => `共 ${total-Math.ceil(total/ipagination.pageSize)} 条`">
                <template slot="buildOptionText" slot-scope="props">
                  <span>{{ props.value-1 }}条/页</span>
                </template>
              </a-pagination>
            </a-col>
          </a-row>
        </section>
        <!-- table区域-end -->
        <material-in-out-list ref="materialInOutList" @ok="modalFormOk"></material-in-out-list>
        <material-depot-stock-list ref="materialDepotStockList" @ok="modalFormOk"></material-depot-stock-list>
      </a-card>
    </a-col>
  </a-row>
</template>
<script>
  import MaterialInOutList from './modules/MaterialInOutList'
  import MaterialDepotStockList from './modules/MaterialDepotStockList'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import { getAction, getFileAccessHttpUrl } from '@/api/manage'
  import {queryMaterialCategoryTreeList} from '@/api/api'
  import { getMpListShort } from "@/utils/util"
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import moment from 'moment'
  import Vue from 'vue'
  export default {
    name: "MaterialStock",
    mixins:[JeecgListMixin],
    components: {
      MaterialInOutList,
      MaterialDepotStockList,
      JEllipsis
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
        ipagination:{
          pageSize: 11,
          pageSizeOptions: ['11', '21', '31', '101', '201', '301', '1001', '2001', '3001']
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
        unitModeAddedColumns: [],
        pageName: 'materialStock',
        // 默认索引
        defDataIndex:['rowIndex','action','mBarCode','name','sku','standard','model','color','categoryName', 'position','unitName',
          'purchaseDecimal','initialStock','currentStock','currentStockPrice','currentWeight'],
        // 默认列
        defColumns: [
          {
            dataIndex: 'rowIndex', width:40, align:"center",
            customRender:function (t,r,index) {
              return (t !== '合计') ? (parseInt(index) + 1) : t
            }
          },
          {title: '图片', dataIndex: 'pic', width: 45, scopedSlots: { customRender: 'customPic' }},
          {title: '商品编码', dataIndex: 'mBarCode', width: 120, ellipsis:true, scopedSlots: { customRender: 'materialCode' }},
          {title: '商品ID', dataIndex: 'id', width: 90},
          {title: '商品名称', dataIndex: 'name', width: 140, ellipsis:true},
          {title: '商品条码', dataIndex: 'barCode', width: 120, ellipsis:true},
          {title: '规格', dataIndex: 'standard', width: 100, ellipsis:true},
          {title: '型号', dataIndex: 'model', width: 100, ellipsis:true},
          {title: '颜色', dataIndex: 'color', width: 60, ellipsis:true},
          {title: '品牌', dataIndex: 'brand', width: 100, ellipsis:true},
          {title: '助记码', dataIndex: 'mnemonic', width: 100, ellipsis:true},
          {title: '制造商', dataIndex: 'mfrs', width: 120, ellipsis:true},
          {title: '类别', dataIndex: 'categoryName', width: 100, ellipsis:true},
          {title: '类别ID', dataIndex: 'categoryId', width: 90},
          {title: '单位', dataIndex: 'unitName', width: 80},
          {title: '基础单位', dataIndex: 'unit', width: 80},
          {title: '基础重量', dataIndex: 'weight', width: 90},
          {title: '当前单价', dataIndex: 'currentUnitPrice', width: 90},
          {title: '保质期天数', dataIndex: 'expiryNum', width: 100},
          {title: '仓位货架', dataIndex: 'position', width: 100, ellipsis:true},
          {title: '商品属性', dataIndex: 'attribute', width: 100},
          {title: '备注', dataIndex: 'remark', width: 140, ellipsis:true},
          {title: '序列号管理', dataIndex: 'enableSerialNumber', width: 100},
          {title: '批号管理', dataIndex: 'enableBatchNumber', width: 100},
          {title: 'SKU规格', dataIndex: 'sku', width: 120, ellipsis:true},
          {title: 'SKU ID', dataIndex: 'meId', width: 90},
          {title: 'SKU规格值', dataIndex: 'specValues', width: 160, ellipsis:true},
          {title: '单件状态', dataIndex: 'unitStatus', width: 80},
          {title: '单件入库时间', dataIndex: 'inboundTime', width: 140},
          {title: '单件所在仓库', dataIndex: 'depotName', width: 110},
          {title: '单件仓库ID', dataIndex: 'depotId', width: 100},
          {title: '面料', dataIndex: 'otherField1', width: 100, ellipsis:true},
          {title: '安全类别', dataIndex: 'otherField2', width: 100, ellipsis:true},
          {title: '执行标准', dataIndex: 'otherField3', width: 100, ellipsis:true},
          {title: '填充物', dataIndex: 'otherField4', width: 100, ellipsis:true},
          {title: '扩展5', dataIndex: 'otherField5', width: 100, ellipsis:true},
          {title: '等级', dataIndex: 'otherField6', width: 100, ellipsis:true},
          {title: '检验', dataIndex: 'otherField7', width: 100, ellipsis:true},
          {title: '成本价', dataIndex: 'purchaseDecimal', sorter: (a, b) => a.purchaseDecimal - b.purchaseDecimal, width: 60},
          {title: '初始库存', dataIndex: 'initialStock', width: 60},
          {title: '库存', dataIndex: 'currentStock', sorter: (a, b) => a.currentStock - b.currentStock, width: 60,
            scopedSlots: { customRender: 'customRenderStock' }
          },
          {title: '库存金额', dataIndex: 'currentStockPrice', sorter: (a, b) => a.currentStockPrice - b.currentStockPrice, width: 80},
          {title: '重量', dataIndex: 'currentWeight', sorter: (a, b) => a.currentWeight - b.currentWeight, width: 60},
          {title: '库存详情', dataIndex: 'action', align:"center", width: 80,
            fixed: 'right', scopedSlots: { customRender: 'action' }
          }
        ],
        url: {
          list: "/material/getListWithStock"
        }
      }
    },
    created() {
      this.getDepotData()
      this.loadCategoryTreeData()
      this.syncMaterialPropertyTitles()
      this.loadMaterialStockColumnWidths()
      this.initColumnsSetting()
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
      moment,
      getQueryParams() {
        let param = Object.assign({}, this.queryParam, this.isorter);
        if(this.depotSelected && this.depotSelected.length>0) {
          param.depotIds = this.depotSelected.join()
        }
        param.field = this.getQueryField();
        param.currentPage = this.ipagination.current;
        param.pageSize = this.ipagination.pageSize-1;
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
      getImgUrl(imgName, type) {
        if(imgName && imgName.split(',')) {
          type = type? type + '/':''
          return getFileAccessHttpUrl('systemConfig/static/' + type + imgName.split(',')[0])
        } else {
          return ''
        }
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
      onDisplayModeChange() {
        this.updateMaterialCodeTitle()
        this.columns = this.defColumns.filter(col => this.settingDataIndex.includes(col.dataIndex))
        this.loadData(1)
      },
      updateMaterialCodeTitle() {
        const codeColumn = this.defColumns.find(col => col.dataIndex === 'mBarCode')
        if (codeColumn) {
          codeColumn.title = this.displayMode === 'unit' ? '单件商品码' : (this.displayMode === 'sku' ? 'SKU码' : '商品编码')
        }
      },
      displayMaterialCode(record) {
        if (!record) return ''
        if (this.displayMode === 'unit') return record.unitCode || record.mBarCode || ''
        if (this.displayMode === 'sku') return record.skuCode || record.mBarCode || ''
        return record.code || ''
      },
      syncMaterialPropertyTitles() {
        const properties = Vue.ls.get('materialPropertyList') || []
        this.defColumns.forEach(col => {
          if (!/^otherField[1-7]$/.test(col.dataIndex)) return
          const property = properties.find(item => item.field === col.dataIndex)
          if (property && property.anotherName) col.title = property.anotherName
        })
        this.updateMaterialCodeTitle()
      },
      loadMaterialStockColumnWidths() {
        const savedWidths = Vue.ls.get(this.pageName + '_column_widths') || {}
        this.defColumns.forEach(col => {
          const width = Number(savedWidths[col.dataIndex])
          if (Number.isFinite(width) && width > 0) col.width = width
        })
      },
      onColumnWidthsChanged() {
        const widths = {}
        this.defColumns.forEach(col => {
          const width = Number(col.width)
          if (Number.isFinite(width) && width > 0) widths[col.dataIndex] = width
        })
        Vue.ls.set(this.pageName + '_column_widths', widths)
      },
      getRowKey(record) {
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
            this.tableAddTotalRow(this.columns, this.dataSource)
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
        let list = []
        let headArray = this.defColumns
          .filter(col => col.dataIndex !== 'rowIndex' && col.dataIndex !== 'action' && col.dataIndex !== 'pic')
          .map(col => col.title)
        let head = headArray.join(',')

        for (let i = 0; i < this.dataSource.length; i++) {
          let item = []
          let ds = this.dataSource[i]
          this.defColumns.forEach(col => {
            if (col.dataIndex !== 'rowIndex' && col.dataIndex !== 'action' && col.dataIndex !== 'pic') {
              item.push(col.dataIndex === 'mBarCode' ? this.displayMaterialCode(ds) : ds[col.dataIndex])
            }
          })
          list.push(item)
        }
        let tip = '商品库存查询'
        this.handleExportXlsPost('商品库存', '商品库存', head, tip, list)
      }
    }
  }
</script>

<style scoped>
  @import '~@assets/less/common.less'
</style>
<style scoped>
  .column-setting-col {
    display: flex;
    justify-content: flex-end;
    align-items: flex-start;
    margin-right: -12px;
  }
  .item-info {
    float:left;
    width:38px;
    height:38px;
    margin-left:6px
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
