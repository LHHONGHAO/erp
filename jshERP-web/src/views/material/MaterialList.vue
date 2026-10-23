<!-- by 7527189 2 0 -->
<template>
  <a-row :gutter="24">
    <a-col :md="24">
      <a-card :style="cardStyle" :bordered="false">
        <!-- 查询区域 -->
        <div class="table-page-search-wrapper">
          <!-- 搜索区域 -->
          <a-form layout="inline" @keyup.enter.native="searchQuery">
            <a-row :gutter="24">
              <a-col :md="6" :sm="24">
                <a-form-item label="类别" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}" allow-clear
                   :treeData="categoryTree" v-model="queryParam.categoryId" placeholder="请选择类别">
                  </a-tree-select>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="关键词" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-input placeholder="请输入条码、名称、助记码等查询" v-model="queryParam.materialParam"></a-input>
                </a-form-item>
              </a-col>
              <a-col :md="6" :sm="24">
                <a-form-item label="显示方式" :labelCol="labelCol" :wrapperCol="wrapperCol">
                  <a-radio-group v-model="displayMode" @change="onDisplayModeChange">
                    <a-radio-button value="spu">单类显示</a-radio-button>
                    <a-radio-button value="sku">单品显示</a-radio-button>
                  </a-radio-group>
                </a-form-item>
              </a-col>
              <span style="float: left;overflow: hidden;" class="table-page-search-submitButtons">
                <a-col :md="6" :sm="24">
                  <a-button type="primary" @click="searchQuery">查询</a-button>
                  <a-button style="margin-left: 8px" @click="searchReset">重置</a-button>
                  <a @click="handleToggleSearch" style="margin-left: 8px">
                    {{ toggleSearchStatus ? '收起' : '展开' }}
                    <a-icon :type="toggleSearchStatus ? 'up' : 'down'"/>
                  </a>
                </a-col>
              </span>
            </a-row>
            <template v-if="toggleSearchStatus">
              <a-row :gutter="24">
                <a-col :md="6" :sm="24">
                  <a-form-item label="品牌" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入品牌查询" v-model="queryParam.brand"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="制造商" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入制造商查询" v-model="queryParam.mfrs"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="助记码" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入助记码查询" v-model="queryParam.mnemonic"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="商品ID" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input-number style="width: 100%" placeholder="请输入商品ID查询" v-model="queryParam.id"></a-input-number>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item :label="queryTitle.mp1" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input :placeholder="'请输入'+ queryTitle.mp1 +'查询'" v-model="queryParam.otherField1"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item :label="queryTitle.mp2" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input :placeholder="'请输入'+ queryTitle.mp2 +'查询'" v-model="queryParam.otherField2"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item :label="queryTitle.mp3" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input :placeholder="'请输入'+ queryTitle.mp3 +'查询'" v-model="queryParam.otherField3"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item :label="queryTitle.mp4" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input :placeholder="'请输入'+ queryTitle.mp4 +'查询'" v-model="queryParam.otherField4"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item :label="queryTitle.mp5" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input :placeholder="'请输入'+ queryTitle.mp5 +'查询'" v-model="queryParam.otherField5"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item :label="queryTitle.mp6" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input :placeholder="'请输入'+ queryTitle.mp6 +'查询'" v-model="queryParam.otherField6"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item :label="queryTitle.mp7" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input :placeholder="'请输入'+ queryTitle.mp7 +'查询'" v-model="queryParam.otherField7"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="状态" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="请选择状态" v-model="queryParam.enabled">
                      <a-select-option value="1">启用</a-select-option>
                      <a-select-option value="0">禁用</a-select-option>
                    </a-select>
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
                  <a-form-item label="批号" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-select placeholder="有无批号" v-model="queryParam.enableBatchNumber">
                      <a-select-option value="1">有</a-select-option>
                      <a-select-option value="0">无</a-select-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="仓库" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input style="width: 100%" placeholder="请输入仓库查询" v-model="queryParam.position"></a-input>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="基础重量" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input-number style="width: 100%" placeholder="请输入基础重量查询" v-model="queryParam.weight"></a-input-number>
                  </a-form-item>
                </a-col>
                <a-col :md="6" :sm="24">
                  <a-form-item label="备注" :labelCol="labelCol" :wrapperCol="wrapperCol">
                    <a-input placeholder="请输入备注查询" v-model="queryParam.remark"></a-input>
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
          <a-button v-if="btnEnableList.indexOf(3)>-1" @click="handleExportXls('商品信息')" icon="download">导出</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="batchEdit()" icon="edit">批量编辑</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="batchSetMaterialCurrentStock()" icon="stock">修正库存</a-button>
          <a-button v-if="btnEnableList.indexOf(1)>-1" @click="batchSetMaterialCurrentUnitPrice()" icon="fund">修正成本</a-button>
        </div>
        <!-- table区域-begin -->
        <div>
          <data-table
            ref="table"
            bordered
            draggable
            row-key="skuRowKey"
            table-key="material_list"
            :extra-columns="extraColumnsForTable"
            :default-columns="defaultColumnsForTable"
            storage-key="columns_material"
            :data-source="tableRows"
            :pagination="ipagination"
            :scroll="scroll"
            :loading="loading"
            :row-selection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange, columnWidth:'40px'}"
            @change="handleTableChange">
            <span slot="action" slot-scope="text, record">
              <a @click="handleEdit(record)">编辑</a>
              <a-divider v-if="btnEnableList.indexOf(1)>-1" type="vertical" />
              <a v-if="btnEnableList.indexOf(1)>-1" @click="handleCopyAdd(record)">复制</a>
              <a-divider type="vertical" />
              <a @click="handleAddSpec(record)">添加规格</a>
              <a-divider type="vertical" />
              <a @click="handlePrintSkuLabel(record)">打印标签</a>
            </span>
            <template slot="customSkuImg" slot-scope="text, record">
              <img v-if="getRowImgUrl(record)" :src="getRowImgUrl(record)" class="row-sku-img" />
              <span v-else class="row-sku-img-placeholder">无图</span>
            </template>
            <template slot="customSpecValues" slot-scope="text, record">
              {{ getSpecValueMerged(record) }}
            </template>
            <template slot="customBarCode" slot-scope="text, record">
              {{record.mBarCode}}
            </template>
            <template slot="customName" slot-scope="text, record">
              {{record.name}}
              <a-tag v-if="record.enableSerialNumber==1" color="orange">序</a-tag>
              <a-tag v-if="record.enableBatchNumber==1" color="orange">批</a-tag>
            </template>
            <template slot="customRenderInitialStock" slot-scope="text, record">
              <a-tooltip :title="record.bigUnitInitialStock">
                {{text}}
              </a-tooltip>
            </template>
            <template slot="customRenderStock" slot-scope="text, record">
              <a-tooltip :title="record.bigUnitStock">
                {{text}}
              </a-tooltip>
            </template>
            <template slot="customRenderEnabled" slot-scope="enabled">
              <a-tag v-if="enabled" color="green">启用</a-tag>
              <a-tag v-if="!enabled" color="orange">禁用</a-tag>
            </template>
          </data-table>
        </div>
        <!-- table区域-end -->
        <!-- 表单区域 -->
        <material-modal ref="modalForm" @ok="modalFormOk"></material-modal>
        <import-file-modal ref="modalImportForm" @ok="modalFormOk"></import-file-modal>
        <label-edit-modal ref="labelEditModal"></label-edit-modal>
        <batch-set-info-modal ref="batchSetInfoModalForm" @ok="modalFormOk"></batch-set-info-modal>
      </a-card>
    </a-col>
  </a-row>
</template>
<script>
  import MaterialModal from './modules/MaterialModal'
  import ImportFileModal from '@/components/tools/ImportFileModal'
  import BatchSetInfoModal from './modules/BatchSetInfoModal'
  import LabelEditModal from './modules/LabelEditModal'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { postAction, getAction, getFileAccessHttpUrl } from '@/api/manage'
  import { getMpListShort } from '@/utils/util'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import DataTable from '@/components/DataTable'
  import JEllipsis from '@/components/jeecg/JEllipsis'
  import JDate from '@/components/jeecg/JDate'
  import Vue from 'vue'

  export default {
    name: "MaterialList",
    mixins:[JeecgListMixin],
    components: {
      MaterialModal,
      ImportFileModal,
      BatchSetInfoModal,
      LabelEditModal,
      JEllipsis,
      JDate,
      DataTable
    },
    data () {
      return {
        categoryTree:[],
        mPropertyListShort: '',
        expandedRowKeys: [],
        // SKU数据独立存储，避免AntD 1.5.2 expandedRowRender 不响应record属性变化的坑
        skuMap: {},
        skuColumns: [
          {title: '图片', dataIndex: 'imgUrl', width: 60, scopedSlots: { customRender: 'skuImage' }},
          {title: 'SKU编码', dataIndex: 'skuCode', width: 160},
          {title: '规格值', dataIndex: 'specValuesText', width: 200, ellipsis: true},
          {title: '库存', dataIndex: 'currentStock', width: 80, align: 'center'},
          {title: '采购价', dataIndex: 'purchaseDecimal', width: 80},
          {title: '零售价', dataIndex: 'commodityDecimal', width: 80},
          {title: '销售价', dataIndex: 'wholesaleDecimal', width: 80},
          {title: '最低售价', dataIndex: 'lowDecimal', width: 80}
        ],
        skuColSettings: {
          img: true, skuCode: true, spec: true, stock: true, purchase: true, retail: true, wholesale: true, low: true, action: true
        },
        // SKU列可见列的顺序（不含操作列，操作列固定末尾）
        skuColOrder: ['img', 'skuCode', 'spec', 'stock', 'purchase', 'retail', 'wholesale', 'low'],
        // SKU列定义（用于按顺序渲染）
        skuColDefs: {
          img: { key: 'img', label: '图片', cls: 'sku-cell-img' },
          skuCode: { key: 'skuCode', label: 'SKU编码', cls: 'sku-cell-code' },
          spec: { key: 'spec', label: '规格', cls: 'sku-cell-spec' },
          stock: { key: 'stock', label: '库存', cls: 'sku-cell-stock' },
          purchase: { key: 'purchase', label: '采购价', cls: 'sku-cell-price' },
          retail: { key: 'retail', label: '零售价', cls: 'sku-cell-price' },
          wholesale: { key: 'wholesale', label: '销售价', cls: 'sku-cell-price' },
          low: { key: 'low', label: '最低售价', cls: 'sku-cell-price' },
          action: { key: 'action', label: '操作', cls: 'sku-cell-action' }
        },
        skuColWidths: {},  // SKU列宽：{img:80, spec:150, ...}
        skuResizing: null, // 当前拖拽中的列key
        selectedMaterialIds: [], // 选中行对应的商品ID（去重）
        // 显示方式：spu=单类显示（每个商品一行），sku=单品显示（每个SKU一行）
        displayMode: 'sku',
        model: {},
        labelCol: {
          span: 5
        },
        wrapperCol: {
          span: 18,
          offset: 1
        },
        queryTitle: {
          mp1: '面料',
          mp2: '安全类别',
          mp3: '执行标准',
          mp4: '扩展4',
          mp5: '扩展5',
          mp6: '等级',
          mp7: '检验'
        },
        // 查询条件
        queryParam: {
          categoryId: undefined,
          materialParam:'',
          standard:'',
          model:'',
          color:'',
          brand:'',
          mfrs:'',
          mnemonic:'',
          id: undefined,
          otherField1:'',
          otherField2:'',
          otherField3:'',
          otherField4:'',
          otherField5:'',
          otherField6:'',
          otherField7:'',
          weight:'',
          expiryNum:'',
          enabled: undefined,
          enableSerialNumber: undefined,
          enableBatchNumber: undefined,
          position: '',
          remark:'',
          mpList: getMpListShort(Vue.ls.get('materialPropertyList'))  //扩展属性
        },
        urlPath: '/material/material',
        ipagination:{
          pageSizeOptions: ['10', '20', '30', '50', '100', '200']
        },
        // 实际索引
        settingDataIndex:[],
        // 实际列
        columns:[],
        // 商品信息+SKU信息属性的 dataIndex 列表（用于列设置）
        baseDataIndex:['name','code','imgName','skuCode','specValues','unit','brand','mnemonic','categoryName','weight','mfrs',
          'otherField1','otherField2','otherField3','id','mBarCode','otherField4','otherField5','otherField6','otherField7',
          'fabric','safetyCategory','executiveStandard','grade','inspection',
          'initialStock','stock','purchaseDecimal','commodityDecimal','wholesaleDecimal','lowDecimal','remark','enabled'],
        // 默认显示：商品信息 + SKU信息
        defDataIndex:['name','code','imgName','skuCode','specValues','unit','brand','mnemonic','categoryName','weight','mfrs',
          'fabric','safetyCategory','executiveStandard','mBarCode','stock','purchaseDecimal','commodityDecimal','wholesaleDecimal','lowDecimal'],
        // 默认列（列名固定不可改，除扩展字段用 anotherName 可配置别名）
        defColumns: [
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
          {title: '等级', dataIndex: 'otherField6', width: 100, ellipsis:true},
          {title: '检验', dataIndex: 'otherField7', width: 100, ellipsis:true},
          {title: '面料', dataIndex: 'fabric', width: 100, ellipsis:true},
          {title: '安全类别', dataIndex: 'safetyCategory', width: 100, ellipsis:true},
          {title: '执行标准', dataIndex: 'executiveStandard', width: 100, ellipsis:true},
          {title: '等级', dataIndex: 'grade', width: 100, ellipsis:true},
          {title: '检验', dataIndex: 'inspection', width: 100, ellipsis:true},
          {title: '商品ID', dataIndex: 'id', width: 80},
          {title: '条码', dataIndex: 'mBarCode', width: 120, scopedSlots: { customRender: 'customBarCode' }},
          {title: '初始库存', dataIndex: 'initialStock', width: 80,
            scopedSlots: { customRender: 'customRenderInitialStock' }
          },
          {title: '库存', dataIndex: 'stock', width: 80,
            scopedSlots: { customRender: 'customRenderStock' }
          },
          {title: '采购价', dataIndex: 'purchaseDecimal', width: 80},
          {title: '零售价', dataIndex: 'commodityDecimal', width: 80},
          {title: '销售价', dataIndex: 'wholesaleDecimal', width: 80},
          {title: '最低售价', dataIndex: 'lowDecimal', width: 80},
          {title: '备注', dataIndex: 'remark', width: 120, ellipsis:true},
          {title: '状态', dataIndex: 'enabled', align: "center", width: 60,
            scopedSlots: { customRender: 'customRenderEnabled' }
          },
          {
            title: '操作',
            dataIndex: 'action',
            align: "center",
            width: 180,
            scopedSlots: { customRender: 'action' },
          }
        ],
        url: {
          list: "/material/listSku",
          delete: "/material/delete",
          deleteBatch: "/material/deleteBatch",
          importExcelUrl: "/material/importExcel",
          exportXlsUrl: "/material/exportExcel",
          batchSetStatusUrl: "/material/batchSetStatus",
          batchSetMaterialCurrentStockUrl: "/material/batchSetMaterialCurrentStock",
          batchSetMaterialCurrentUnitPriceUrl: "/material/batchSetMaterialCurrentUnitPrice",
        }
      }
    },
    created() {
      this.model = Object.assign({}, {});
      this.initColumnsSetting()
      this.loadTreeData()
      this.handleChangeOtherField(1)
    },
    computed: {
      importExcelUrl: function () {
        return `${window._CONFIG['domianURL']}${this.url.importExcelUrl}`;
      },
      // DataTable 默认显示列：基础信息属性 + 固定末尾的操作列
      defaultColumnsForTable() {
        const base = this.displayMode === 'spu'
          ? this.defDataIndex.filter(k => k !== 'skuCode' && k !== 'specValues')
          : this.defDataIndex
        return [...base, 'action']
      },
      // DataTable 实际列：单类显示时隐藏 SKU 编码/规格值列（SPU 接口不返回这些字段）
      extraColumnsForTable() {
        if (this.displayMode === 'spu') {
          return this.defColumns.filter(c => c.dataIndex !== 'skuCode' && c.dataIndex !== 'specValues')
        }
        return this.defColumns
      },
      // SKU维度行数据：为每个SKU行生成唯一key
      tableRows() {
        return (this.dataSource || []).map(function(row) {
          return Object.assign({}, row, {
            skuRowKey: row.id + '_' + (row.meId != null ? row.meId : 0)
          })
        })
      },
      // 列设置弹窗中可选的列：仅商品基础信息属性（图片/操作列固定显示，不在选项中）
      baseColumnsForSetting() {
        return this.defColumns.filter(item => this.baseDataIndex.includes(item.dataIndex))
      },
      // SKU列设置：当前可见的列key数组
      visibleSkuCols() {
        var result = []
        var nonActionKeys = Object.keys(this.skuColDefs).filter(function(k) { return k !== 'action' })
        for (var i = 0; i < nonActionKeys.length; i++) {
          if (this.isSkuColVisible(nonActionKeys[i])) {
            result.push(nonActionKeys[i])
          }
        }
        return result
      },
      // SKU列设置：所有可选列key（不含action，action固定在末尾显示）
      allSkuColKeys() {
        return Object.keys(this.skuColDefs).filter(function(k) { return k !== 'action' })
      },
      // SKU列按顺序排列的可见列key（不含action）
      orderedVisibleSkuCols() {
        var that = this
        return this.skuColOrder.filter(function(k) { return that.isSkuColVisible(k) })
      },
    },
    methods: {
      // 选中行：转为商品ID（同一商品多个SKU行时去重）
      onSelectChange(selectedRowKeys, selectionRows) {
        this.selectedRowKeys = selectedRowKeys
        this.selectionRows = selectionRows
        var ids = []
        for (var i = 0; i < selectionRows.length; i++) {
          if (ids.indexOf(selectionRows[i].id) === -1) {
            ids.push(selectionRows[i].id)
          }
        }
        this.selectedMaterialIds = ids
      },
      onClearSelected() {
        this.selectedRowKeys = []
        this.selectionRows = []
        this.selectedMaterialIds = []
      },
      // 批量操作时使用去重后的商品ID
      runBatchWithMaterialIds(methodName, arg) {
        var originKeys = this.selectedRowKeys
        this.selectedRowKeys = this.selectedMaterialIds.slice()
        try {
          JeecgListMixin.methods[methodName].call(this, arg)
        } finally {
          this.selectedRowKeys = originKeys
        }
      },
      batchDel() {
        this.runBatchWithMaterialIds('batchDel')
      },
      batchSetStatus(status) {
        this.runBatchWithMaterialIds('batchSetStatus', status)
      },
      // 行内SKU图片：优先 extend.img_url，为空时从规格图映射中匹配
      getRowImgUrl(record) {
        if (!record) return ''
        if (record.imgName) return this.getSkuImgUrl(record.imgName)
        if (record.attribute && record.specValues) {
          try {
            var attrObj = JSON.parse(record.attribute)
            var modules = attrObj.specModules || []
            var specVals = JSON.parse(record.specValues)
            for (var i = 0; i < specVals.length; i++) {
              var text = specVals[i].valueText
              for (var j = 0; j < modules.length; j++) {
                var values = modules[j].values || []
                for (var k = 0; k < values.length; k++) {
                  if (values[k].text === text && values[k].image) {
                    return this.getSkuImgUrl(values[k].image)
                  }
                }
              }
            }
          } catch (e) { /* ignore */ }
        }
        return ''
      },
      // 打印当前SKU行标签
      handlePrintSkuLabel(record) {
        var sku = {
          specValuesText: this.getSpecValueMerged(record),
          skuCode: record.skuCode,
          purchaseDecimal: record.purchaseDecimal,
          commodityDecimal: record.commodityDecimal,
          wholesaleDecimal: record.wholesaleDecimal,
          lowDecimal: record.lowDecimal,
          currentStock: record.stock
        }
        this.handlePrintLabel(record, sku)
      },
      //加载初始化列
      initColumnsSetting(){
        let columnsStr = Vue.ls.get('materialColumns')
        if(columnsStr && columnsStr.indexOf(',')>-1) {
          // 过滤掉非基础属性的 dataIndex（兼容旧版本本地存储）
          let parsed = columnsStr.split(',').filter(idx => this.baseDataIndex.includes(idx))
          // 兼容旧版本：自动补入新增的"商品编码"列
          if (parsed.length > 0 && parsed.indexOf('code') === -1) {
            let nameIdx = parsed.indexOf('name')
            parsed.splice(nameIdx === -1 ? 0 : nameIdx + 1, 0, 'code')
          }
          this.settingDataIndex = parsed.length > 0 ? parsed : this.defDataIndex
        } else {
          this.settingDataIndex = this.defDataIndex
        }
        this.columns = this.buildColumns(this.settingDataIndex)
        this.loadSkuColWidths()
        // 加载SKU列设置（从localStorage恢复到reactive数据，确保action始终可见）
        var savedSkuSettings = Vue.ls.get('skuColSettings')
        if (savedSkuSettings) {
          this.skuColSettings = Object.assign({}, this.skuColSettings, savedSkuSettings)
          this.skuColSettings.action = true
        }
        // 加载SKU列顺序
        var savedOrder = Vue.ls.get('skuColOrder')
        if (savedOrder && savedOrder.length) {
          // 兼容旧配置：补充新增的SKU编码列
          if (savedOrder.indexOf('skuCode') === -1) {
            var imgIdx = savedOrder.indexOf('img')
            savedOrder.splice(imgIdx === -1 ? 0 : imgIdx + 1, 0, 'skuCode')
          }
          this.skuColOrder = savedOrder
        }
      },
      //列设置更改事件：按勾选顺序构建列（checkedValues 已按用户勾选顺序排列）
      onColChange (checkedValues) {
        this.settingDataIndex = checkedValues
        this.columns = this.buildColumns(checkedValues)
        let columnsStr = checkedValues.join()
        Vue.ls.set('materialColumns', columnsStr)
      },
      //恢复默认
      handleRestDefault() {
        Vue.ls.remove('materialColumns')
        this.initColumnsSetting()
      },
      // 根据 settingDataIndex 顺序构建实际列（操作列固定在尾）
      buildColumns(selectedDataIndex) {
        const selectedColumns = selectedDataIndex.map(idx =>
          this.defColumns.find(item => item.dataIndex === idx)
        ).filter(Boolean)
        const actionCol = this.defColumns.find(item => item.dataIndex === 'action')
        return [...selectedColumns, actionCol].filter(Boolean)
      },
      // 动态替换扩展字段：覆盖 mixin 的 handleChangeOtherField
      // 规则：mp1=面料、mp2=安全类别、mp3=执行标准（固定，不可通过属性配置改名称）
      //       mp4=扩展4、mp5=扩展5（可改名称，从 materialPropertyList 获取）
      handleChangeOtherField(showQuery) {
        // 1. 先固定前三个（面料/安全类别/执行标准）
        if (showQuery) {
          this.queryTitle.mp1 = '面料'
          this.queryTitle.mp2 = '安全类别'
          this.queryTitle.mp3 = '执行标准'
        }
        for (let i = 0; i < this.defColumns.length; i++) {
          if (this.defColumns[i].dataIndex === 'otherField1') {
            this.defColumns[i].title = '面料'
          }
          if (this.defColumns[i].dataIndex === 'otherField2') {
            this.defColumns[i].title = '安全类别'
          }
          if (this.defColumns[i].dataIndex === 'otherField3') {
            this.defColumns[i].title = '执行标准'
          }
        }
        // 2. 再从 materialPropertyList 应用 mp4/mp5/mp6/mp7 动态别名（如果有）
        const mpList = Vue.ls.get('materialPropertyList')
        let mp4Name = '扩展4'
        let mp5Name = '扩展5'
        let mp6Name = '等级'
        let mp7Name = '检验'
        if (mpList && Array.isArray(mpList)) {
          for (let i = 0; i < mpList.length; i++) {
            if (mpList[i].nativeName === '扩展4' || mpList[i].id === 4) {
              mp4Name = mpList[i].anotherName || '扩展4'
            }
            if (mpList[i].nativeName === '扩展5' || mpList[i].id === 5) {
              mp5Name = mpList[i].anotherName || '扩展5'
            }
            if (mpList[i].nativeName === '扩展6' || mpList[i].id === 6) {
              mp6Name = mpList[i].anotherName || '等级'
            }
            if (mpList[i].nativeName === '扩展7' || mpList[i].id === 7) {
              mp7Name = mpList[i].anotherName || '检验'
            }
          }
        }
        if (showQuery) {
          this.queryTitle.mp4 = mp4Name
          this.queryTitle.mp5 = mp5Name
          this.queryTitle.mp6 = mp6Name
          this.queryTitle.mp7 = mp7Name
        }
        for (let i = 0; i < this.defColumns.length; i++) {
          if (this.defColumns[i].dataIndex === 'otherField4') {
            this.defColumns[i].title = mp4Name
          }
          if (this.defColumns[i].dataIndex === 'otherField5') {
            this.defColumns[i].title = mp5Name
          }
          if (this.defColumns[i].dataIndex === 'otherField6') {
            this.defColumns[i].title = mp6Name
          }
          if (this.defColumns[i].dataIndex === 'otherField7') {
            this.defColumns[i].title = mp7Name
          }
        }
        // 3. 刷新实际列（让表头标题重新渲染，并保持 settingDataIndex 顺序）
        this.columns = this.buildColumns(this.settingDataIndex)
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
      batchSetMaterialCurrentStock () {
        if (this.selectedMaterialIds.length <= 0) {
          this.$message.warning('请选择一条记录！');
        } else {
          let ids = "";
          for (let a = 0; a < this.selectedMaterialIds.length; a++) {
            ids += this.selectedMaterialIds[a] + ",";
          }
          let that = this;
          this.$confirm({
            title: "确认操作",
            content: "是否操作选中数据?",
            onOk: function () {
              that.loading = true;
              postAction(that.url.batchSetMaterialCurrentStockUrl, {ids: ids}).then((res) => {
                if(res.code === 200){
                  that.$message.info('修正库存成功！');
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
      },
      batchSetMaterialCurrentUnitPrice () {
        if (this.selectedMaterialIds.length <= 0) {
          this.$message.warning('请选择一条记录！');
        } else {
          let ids = "";
          for (let a = 0; a < this.selectedMaterialIds.length; a++) {
            ids += this.selectedMaterialIds[a] + ",";
          }
          let that = this;
          this.$confirm({
            title: "确认操作",
            content: "是否操作选中数据?",
            onOk: function () {
              that.loading = true;
              postAction(that.url.batchSetMaterialCurrentUnitPriceUrl, {ids: ids}).then((res) => {
                if(res.code === 200){
                  that.$message.info('修正成本成功！');
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
      },
      batchEdit() {
        if (this.selectedMaterialIds.length <= 0) {
          this.$message.warning('请选择一条记录！');
        } else {
          let ids = "";
          for (let a = 0; a < this.selectedMaterialIds.length; a++) {
            if(a === this.selectedMaterialIds.length-1) {
              ids += this.selectedMaterialIds[a]
            } else {
              ids += this.selectedMaterialIds[a] + ','
            }
          }
          this.$refs.batchSetInfoModalForm.edit(ids);
          this.$refs.batchSetInfoModalForm.title = "批量编辑";
        }
      },
      handleAdd: function () {
        this.$refs.modalForm.action = "add";
        this.$refs.modalForm.add();
        this.$refs.modalForm.title = "新增";
        this.$refs.modalForm.disableSubmit = false;
      },
      handleEdit: function (record) {
        this.$refs.modalForm.action = "edit";
        this.$refs.modalForm.edit(record);
        this.$refs.modalForm.title = "编辑";
        this.$refs.modalForm.disableSubmit = false;
        if(this.btnEnableList.indexOf(1)===-1) {
          this.$refs.modalForm.showOkFlag = false
        }
      },
      handleCopyAdd(record) {
        this.$refs.modalForm.action = "copyAdd";
        this.$refs.modalForm.edit(record);
        this.$refs.modalForm.title = "复制新增";
        this.$refs.modalForm.disableSubmit = false;
      },
      handleAddSpec(record) {
        this.$refs.modalForm.action = "edit";
        this.$refs.modalForm.autoAddSpec = true;
        this.$refs.modalForm.edit(record);
        this.$refs.modalForm.title = "添加规格";
        this.$refs.modalForm.disableSubmit = false;
        if(this.btnEnableList.indexOf(1)===-1) {
          this.$refs.modalForm.showOkFlag = false
        }
      },
      // 打印SKU标签：打开标签编辑Modal，加载SKU属性和商品基础信息
      handlePrintLabel(record, sku) {
        if (!sku) {
          this.$message.warning('SKU信息缺失，无法打印')
          return
        }
        this.$refs.labelEditModal.open(record, sku, this.categoryTree)
      },
      getImgUrl(imgName, type) {
        if(imgName && imgName.split(',')) {
          type = type? type + '/':''
          return getFileAccessHttpUrl('systemConfig/static/' + type + imgName.split(',')[0])
        } else {
          return ''
        }
      },
      handleImportXls() {
        let importExcelUrl = this.url.importExcelUrl
        let templateUrl = '/doc/goods_template.xls'
        let templateName = '商品Excel模板[下载]'
        this.$refs.modalImportForm.initModal(importExcelUrl, templateUrl, templateName);
        this.$refs.modalImportForm.title = "商品导入";
      },
      // 显示方式切换：spu=单类显示（/material/list），sku=单品显示（/material/listSku）
      onDisplayModeChange() {
        this.url.list = this.displayMode === 'spu' ? '/material/list' : '/material/listSku'
        this.loadData(1)
      },
      searchReset() {
        this.queryParam = {
          mpList: getMpListShort(Vue.ls.get('materialPropertyList'))  //扩展属性
        }
        this.loadData(1);
      },
      // 获取SKU加载状态（独立响应式存储，规避AntD 1.5.2 expandedRowRender不响应record属性变化的坑）
      getSkuLoading(materialId) {
        const item = this.skuMap[materialId]
        return item ? item.skuLoading : false
      },
      // 获取SKU列表
      getSkuList(materialId) {
        const item = this.skuMap[materialId]
        return item && item.skuList ? item.skuList : []
      },
      // 获取SKU图片完整URL（不使用getFileAccessHttpUrl，因其过滤了含'['的路径）
      getSkuImgUrl(imgUrl) {
        if (!imgUrl) return ''
        if (imgUrl.startsWith('http')) return imgUrl
        const base = (window._CONFIG && window._CONFIG['domianURL']) ? window._CONFIG['domianURL'] : ''
        // 后端图片预览接口为 /systemConfig/static/**，imgUrl存储的是相对路径
        if (imgUrl.indexOf('systemConfig/static/') === -1) {
          return base + '/systemConfig/static/' + imgUrl
        }
        return base + '/' + imgUrl
      },
      // 展开/折叠 SKU 子表
      onExpand(expanded, record) {
        const self = this
        if (expanded) {
          this.expandedRowKeys = [...new Set([...this.expandedRowKeys, record.id])]
          // 已加载过：无需再处理（操作列不再 fixed:right，无需同步 fixed-right 展开行）
          if (this.skuMap[record.id] && this.skuMap[record.id].skuList && this.skuMap[record.id].skuList.length > 0) {
            return
          }
          // 初始化：用$set确保响应式，同时存储record供打印标签使用
          this.$set(this.skuMap, record.id, { skuList: [], skuLoading: true, record: record })
          getAction('/materialsExtend/getDetailList', { materialId: record.id }).then(function(res) {
            let rows = []
            // 兼容各种返回结构
            if (res && typeof res === 'object') {
              if (Array.isArray(res)) {
                rows = res
              } else if (res.code === 200) {
                if (res.data && Array.isArray(res.data.rows)) {
                  rows = res.data.rows
                } else if (res.result && Array.isArray(res.result.rows)) {
                  rows = res.result.rows
                } else if (Array.isArray(res.rows)) {
                  rows = res.rows
                } else if (res.data && Array.isArray(res.data)) {
                  rows = res.data
                }
              } else if (Array.isArray(res.rows)) {
                rows = res.rows
              }
            }
            for (let i = 0; i < rows.length; i++) {
              rows[i].specValuesText = self.parseSkuSpecValues(rows[i].specValues)
            }
            self.$set(self.skuMap, record.id, { skuList: rows, skuLoading: false, record: record })
            // AntD 1.5.2 expandedRowRender 缓存渲染结果不响应数据变化
            // 通过先收起再展开强制 expandedRowRender 重新渲染
            self.expandedRowKeys = self.expandedRowKeys.filter(function(key) { return key !== record.id })
            self.$nextTick(function() {
              self.expandedRowKeys = [...new Set([...self.expandedRowKeys, record.id])]
            })
          }).catch(function(err) {
            console.error('[onExpand] 请求SKU失败', err)
            self.$set(self.skuMap, record.id, { skuList: [], skuLoading: false, record: record })
            self.expandedRowKeys = self.expandedRowKeys.filter(function(key) { return key !== record.id })
            self.$nextTick(function() {
              self.expandedRowKeys = [...new Set([...self.expandedRowKeys, record.id])]
            })
          })
        } else {
          this.expandedRowKeys = this.expandedRowKeys.filter(function(key) { return key !== record.id })
        }
      },
      // 将 specValues JSON 解析为 "颜色:蓝色 / 规格:200*230" 格式
      parseSkuSpecValues(specValues) {
        if (!specValues) return ''
        try {
          let arr = JSON.parse(specValues)
          if (!Array.isArray(arr)) return ''
          return arr.map(function(item) {
            return item.attributeName + ':' + item.valueText
          }).join(' / ')
        } catch (e) {
          return specValues
        }
      },
      // 从 SKU 列表中解析规格大项名列表（如 ['颜色','尺寸']）
      getSpecKeys(recordId) {
        var skuData = this.skuMap[recordId]
        if (!skuData || !skuData.skuList || skuData.skuList.length === 0) return []
        var firstSku = skuData.skuList[0]
        if (!firstSku || !firstSku.specValues) return []
        try {
          var arr = JSON.parse(firstSku.specValues)
          if (!Array.isArray(arr)) return []
          return arr.map(function(item) { return item.attributeName || '' }).filter(function(n) { return n })
        } catch (e) {
          return []
        }
      },
      // 获取规格名称合并文本，如 "颜色/被套/床单"
      getSpecKeysText(recordId) {
        var keys = this.getSpecKeys(recordId)
        return keys.length > 0 ? keys.join('/') : '规格'
      },
      // 从 SKU 中获取指定规格大项的值
      getSpecValueText(sku, key) {
        if (!sku || !sku.specValues) return ''
        try {
          var arr = JSON.parse(sku.specValues)
          if (!Array.isArray(arr)) return ''
          var item = arr.find(function(i) { return i.attributeName === key })
          return item ? (item.valueText || '') : ''
        } catch (e) {
          return ''
        }
      },
      // SKU规格值合并为一列，用 / 分割，如 "蓝色/200*230/250*230"
      getSpecValueMerged(sku) {
        if (!sku || !sku.specValues) return ''
        try {
          var arr = JSON.parse(sku.specValues)
          if (!Array.isArray(arr)) return sku.specValues || ''
          return arr.map(function(item) { return item.valueText || '' }).filter(function(v) { return v !== '' }).join('/')
        } catch (e) {
          return sku.specValues || ''
        }
      },
      // SKU列设置：是否可见（读reactive数据，保持响应式）
      isSkuColVisible(key) {
        return this.skuColSettings[key] !== false
      },
      // SKU列设置：切换可见性
      toggleSkuCol(key, checked) {
        var saved = Vue.ls.get('skuColSettings')
        if (!saved) {
          saved = Object.assign({}, this.skuColSettings)
        }
        saved[key] = checked
        Vue.ls.set('skuColSettings', saved, 365 * 24 * 60 * 60 * 1000)
        // 强制刷新展开状态触发重渲染
        this.expandedRowKeys = [...this.expandedRowKeys]
      },
      // SKU列设置：按勾选顺序更新列顺序
      onSkuColOrderChange(checkedValues) {
        // checkedValues 按用户勾选顺序排列
        var allKeys = this.allSkuColKeys
        // 更新reactive数据（先更新，触发computed重算）
        for (var i = 0; i < allKeys.length; i++) {
          this.skuColSettings[allKeys[i]] = checkedValues.indexOf(allKeys[i]) > -1
        }
        // 同步到localStorage（始终保留action:true）
        var toSave = Object.assign({}, this.skuColSettings)
        toSave.action = true
        Vue.ls.set('skuColSettings', toSave, 365 * 24 * 60 * 60 * 1000)
        // 更新列顺序
        this.skuColOrder = checkedValues.slice()
        Vue.ls.set('skuColOrder', this.skuColOrder, 365 * 24 * 60 * 60 * 1000)
      },
      // SKU列宽默认值
      getDefaultSkuWidth(key) {
        var map = { img: 80, skuCode: 170, spec: 150, stock: 90, purchase: 90, retail: 90, wholesale: 90, low: 90, action: 180 }
        return map[key] || 80
      },
      // 加载SKU列宽配置
      loadSkuColWidths() {
        var saved = Vue.ls.get('skuColWidths')
        if (saved) {
          this.skuColWidths = saved
        }
      },
      // 保存SKU列宽配置
      saveSkuColWidths() {
        Vue.ls.set('skuColWidths', Object.assign({}, this.skuColWidths), 365 * 24 * 60 * 60 * 1000)
      },
      // 获取SKU列宽内联样式
      skuColWidthStyle(key) {
        var w = this.skuColWidths[key] || this.getDefaultSkuWidth(key)
        return { width: w + 'px', flex: key === 'spec' ? 'none' : undefined, flexShrink: '0' }
      },
      // 开始拖拽SKU列宽
      startSkuResize(e, key) {
        this.skuResizing = key
        var startX = e.clientX
        var startW = this.skuColWidths[key] || this.getDefaultSkuWidth(key)
        var that = this
        var onMove = function(ev) {
          var delta = ev.clientX - startX
          var newW = Math.max(30, startW + delta)
          that.$set(that.skuColWidths, key, newW)
        }
        var onUp = function() {
          document.removeEventListener('mousemove', onMove)
          document.removeEventListener('mouseup', onUp)
          that.skuResizing = null
          that.saveSkuColWidths()
        }
        document.addEventListener('mousemove', onMove)
        document.addEventListener('mouseup', onUp)
      },
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less'
</style>
<style>
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
  .row-sku-img {
    width: 38px;
    height: 38px;
    object-fit: cover;
    border-radius: 4px;
    vertical-align: middle;
  }
  .row-sku-img-placeholder {
    font-size: 12px;
    color: #ccc;
  }

  /* ============================================================
     抖音来客提券库存页风格（操作列不固定，无 fixed-right 同步逻辑）
     主表表头 #F7F9FC / 展开行 #FAFBFD / SKU子表头 #F7F9FC / 数据行白底
     分割线 #F0F2F5
     ============================================================ */

  /* 主表表头：抖音来客风格（浅灰蓝背景、无边框、14px/500） */
  .ant-table-thead > tr > th,
  .ant-table-header > table > .ant-table-thead > tr > th {
    background: #F7F9FC !important;
    color: #1D2129 !important;
    font-size: 14px !important;
    font-weight: 500 !important;
    padding: 9px 16px !important;
    border-bottom: 0 none !important;
  }

  /* 展开行：背景 #FAFBFD，无 padding（SKU子表自控制间距） */
  .ant-table-tbody > tr.ant-table-expanded-row > td {
    padding: 0 !important;
    background: #FAFBFD !important;
    border-bottom: 1px solid #F0F2F5 !important;
  }
  .sku-list-container {
    padding: 0 !important;
    margin: 0 !important;
    width: 100%;
    box-sizing: border-box;
  }

  /* === SKU 列表样式（div flex布局，抖音来客风格） === */
  .sku-row {
    display: flex;
    align-items: center;
    padding: 0;
    border-bottom: 1px solid #F0F2F5;
    box-sizing: border-box;
    width: 100%;
    position: relative;
  }
  .sku-row-header {
    font-weight: 500;
    color: #4F535C;
    background: #F7F9FC;
    font-size: 14px;
    height: 40px;
  }
  .sku-row-header:hover {
    background: #F7F9FC !important;
  }
  .sku-row-data {
    color: #1D2129;
    background: #FFFFFF;
    padding: 12px 16px;
  }
  .sku-row-data:hover {
    background: #FAFBFD;
  }
  .sku-cell {
    padding: 0 8px;
    text-align: center;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    border: 0 none;
    box-sizing: border-box;
    position: relative;
  }
  .sku-cell-img {
    width: 80px;
    flex-shrink: 0;
    text-align: center;
  }
  .sku-cell-code {
    width: 170px;
    flex-shrink: 0;
    text-align: center;
    font-family: Consolas, Monaco, monospace;
  }
  .sku-cell-spec {
    flex: 1.5;
    min-width: 100px;
    text-align: center;
  }
  .sku-cell-stock {
    width: 90px;
    flex-shrink: 0;
    text-align: center;
  }
  .sku-cell-price {
    width: 90px;
    flex-shrink: 0;
    text-align: center;
  }
  .sku-cell-action {
    width: 180px;
    flex-shrink: 0;
    text-align: center;
    margin-left: auto;
  }
  .sku-print-link {
    color: #1890ff;
    cursor: pointer;
  }
  .sku-print-link:hover {
    color: #40a9ff;
  }
  .sku-img {
    cursor: pointer;
    width: 54px;
    height: 54px;
    object-fit: cover;
    border-radius: 4px;
    vertical-align: middle;
  }
  .sku-img-placeholder {
    display: inline-block;
    width: 54px;
    height: 54px;
    line-height: 54px;
    text-align: center;
    font-size: 12px;
    color: #ccc;
    background: #f5f5f5;
    border-radius: 4px;
  }
  .sku-empty {
    text-align: center;
    padding: 16px;
    color: #999;
    font-size: 13px;
    border-bottom: 1px solid #F0F2F5;
  }
  /* SKU列宽拖拽手柄 */
  .sku-resize-handle {
    position: absolute;
    right: 0;
    top: 0;
    bottom: 0;
    width: 6px;
    cursor: col-resize;
    z-index: 1;
  }
  .sku-resize-handle:hover {
    background: #1890ff;
  }
  .sku-row-header .sku-cell {
    position: relative;
    user-select: none;
  }
</style>
