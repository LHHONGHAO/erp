<!-- by 7527189 2 0 -->
<template>
  <!-- ==================== 手机端布局（标准信息搜索页面） ==================== -->
  <mobile-search-page
    v-if="isNarrowScreen"
    ref="mobileSearchPage"
    v-model="mobileSearchKeyword"
    :auto-scan-close="false"
    title="商品信息"
    placeholder="搜索商品 / 条码"
    @back="$router.back()"
    @search="mobileSearch"
    @scan-result="onProductScanResult"
    @camera="ocrVisible = true"
    @filter="filterDrawerVisible = true">
      <template #actions>
        <mobile-header-actions :actions="mobileHeaderActions" />
      </template>

    <!-- 信息列表 -->
    <material-table
      ref="mobileTable"
      table-key="material_list_mobile"
      storage-key="columns_material_mobile"
      :dimension="displayMode"
      :actions="rowActions"
      :data-source="tableRows"
      :pagination="ipagination"
      :scroll="scroll"
      :loading="loading"
      @row-selection-change="onMobileSelectionChange"
      @change="handleTableChange">
    </material-table>

    <!-- 浮层：筛选抽屉、扫码、弹窗 -->
    <template #overlay>
    <!-- 条件筛选抽屉 -->
    <a-drawer
      :visible="filterDrawerVisible"
      placement="right"
      title="条件筛选"
      width="280"
      @close="filterDrawerVisible = false">
      <div class="ml-filter-item">
        <span class="ml-filter-label">显示方式</span>
        <a-radio-group v-model="displayMode" @change="onDisplayModeChange">
          <a-radio-button value="spu">单类</a-radio-button>
          <a-radio-button value="sku">单品</a-radio-button>
        </a-radio-group>
      </div>
      <div class="ml-filter-item">
        <span class="ml-filter-label">商品类别</span>
        <a-tree-select style="width:100%" :dropdownStyle="{maxHeight:'200px',overflow:'auto'}" allow-clear
          :treeData="categoryTree" v-model="queryParam.categoryId" placeholder="请选择类别">
        </a-tree-select>
      </div>
      <div class="ml-filter-item">
        <span class="ml-filter-label">状态</span>
        <a-select style="width:100%" placeholder="请选择状态" v-model="queryParam.enabled">
          <a-select-option value="1">启用</a-select-option>
          <a-select-option value="0">禁用</a-select-option>
        </a-select>
      </div>
      <div class="ml-filter-footer">
        <a-button block type="primary" @click="applyMobileFilter">确定</a-button>
      </div>
    </a-drawer>

    <!-- 标准手机底边栏由 mobile-search-page 统一提供 -->

    <!-- 弹窗组件（手机端也复用） -->
    <material-modal ref="modalForm" @ok="modalFormOk"></material-modal>
    <import-file-modal ref="modalImportForm" @ok="modalFormOk"></import-file-modal>
    <label-edit-modal ref="labelEditModal"></label-edit-modal>
    <label-print-modal ref="labelPrintModal" @edit="handleEditLabel"></label-print-modal>
    <ocr-recognize-modal
      :visible="ocrVisible"
      :extractOnly="true"
      @ok="onOcrOk"
      @cancel="ocrVisible = false"
    />
    </template>
  </mobile-search-page>

  <!-- ==================== 电脑端布局（保持原样） ==================== -->
  <a-row v-else :gutter="24" class="material-list-page">
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
        </div>
        <!-- table区域-begin -->
        <div>
          <material-table
            ref="table"
            bordered
            draggable
            row-key="skuRowKey"
            table-key="material_list"
            storage-key="columns_material"
            :dimension="displayMode"
            :actions="rowActions"
            :data-source="tableRows"
            :pagination="ipagination"
            :scroll="scroll"
            :loading="loading"
            :row-selection="{selectedRowKeys: selectedRowKeys, onChange: onSelectChange, columnWidth:'40px'}"
            @change="handleTableChange">
          </material-table>
        </div>
        <!-- table区域-end -->
        <!-- 表单区域 -->
        <material-modal ref="modalForm" @ok="modalFormOk"></material-modal>
        <import-file-modal ref="modalImportForm" @ok="modalFormOk"></import-file-modal>
        <label-edit-modal ref="labelEditModal"></label-edit-modal>
        <label-print-modal ref="labelPrintModal" @edit="handleEditLabel"></label-print-modal>
      </a-card>
    </a-col>
  </a-row>
</template>
<script>
  import MaterialModal from './modules/MaterialModal'
  import ImportFileModal from '@/components/tools/ImportFileModal'
  import LabelEditModal from './modules/LabelEditModal'
  import LabelPrintModal from './modules/LabelPrintModal'
  import OcrRecognizeModal from './modules/OcrRecognizeModal'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { postAction } from '@/api/manage'
  import { getMpListShort } from '@/utils/util'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import MaterialTable from '@/components/material/MaterialTable'
  import MobileSearchPage from '@/components/mobile/MobileSearchPage'
  import MobileHeaderActions from '@/components/mobile/MobileHeaderActions'
  import { getSpecValueMerged } from '@/views/material/utils/materialDisplay'
  import { searchContentByCode } from '@/services/search/contentSearch'
  import { buildSearchCandidates, parseProductText, classifyCode } from '@/views/material/utils/productTextScan'
  import Vue from 'vue'

  export default {
    name: "MaterialList",
    mixins:[JeecgListMixin],
    components: {
      MaterialModal,
      ImportFileModal,
      LabelEditModal,
      LabelPrintModal,
      OcrRecognizeModal,
      MaterialTable,
      MobileSearchPage,
      MobileHeaderActions
    },
    data () {
      return {
        /** 是否是窄屏（<=768px），避免与 mixin 的 isMobile() 方法重名 */
        isNarrowScreen: false,
        categoryTree:[],
        mPropertyListShort: '',
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
          mp1: '自定义1',
          mp2: '自定义2',
          mp3: '自定义3',
          mp4: '自定义4',
          mp5: '自定义5',
          mp6: '自定义6',
          mp7: '自定义7'
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
        url: {
          list: "/material/listSku",
          delete: "/material/delete",
          deleteBatch: "/material/deleteBatch",
          importExcelUrl: "/material/importExcel",
          exportXlsUrl: "/material/exportExcel",
          batchSetStatusUrl: "/material/batchSetStatus",
          batchSetMaterialCurrentStockUrl: "/material/batchSetMaterialCurrentStock",
          batchSetMaterialCurrentUnitPriceUrl: "/material/batchSetMaterialCurrentUnitPrice",
        },

        // ============ 手机端专属数据 ============
        /** 条件筛选抽屉是否可见 */
        filterDrawerVisible: false,
        /** 底边栏搜索框内的关键词（手机端用，实时同步到 queryParam.materialParam） */
        mobileSearchKeyword: '',
        /** 手机端多选的商品ID数组（去重后） */
        mobileSelectedMaterialIds: [],
        /** OCR 文字提取弹层 */
        ocrVisible: false,
      }
    },
    created() {
      this.model = Object.assign({}, {});
      this.loadTreeData()
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
      this.closeScanner()
    },
    computed: {
      importExcelUrl: function () {
        return `${window._CONFIG['domianURL']}${this.url.importExcelUrl}`;
      },
      // 操作列按钮（是否显示由按钮权限控制）
      rowActions() {
        const canEdit = this.btnEnableList.indexOf(1) > -1
        return [
          { title: '编辑', icon: 'edit', click: record => this.handleEdit(record) },
          { title: '打印标签', icon: 'printer', click: record => this.handlePrintSkuLabel(record) }
        ]
      },
      // 手机端顶栏图标按钮（与会员信息页共用 MobileHeaderActions 组件）
      mobileHeaderActions() {
        const can = this.btnEnableList
        return [
          { key: 'add', icon: 'plus', title: '新增', primary: true, visible: can.indexOf(1) > -1, click: this.handleMobileAdd },
          { key: 'delete', icon: 'delete', title: '删除', visible: can.indexOf(1) > -1, click: this.mobileBatchDel },
          { key: 'enable', icon: 'check-square', title: '启用', visible: can.indexOf(1) > -1, click: () => this.mobileBatchSetStatus(true) },
          { key: 'disable', icon: 'close-square', title: '禁用', visible: can.indexOf(1) > -1, click: () => this.mobileBatchSetStatus(false) },
          { key: 'import', icon: 'import', title: '导入', visible: can.indexOf(1) > -1, click: () => this.handleImportXls() },
          { key: 'export', icon: 'download', title: '导出', visible: can.indexOf(3) > -1, click: () => this.handleExportXls('商品信息') }
        ]
      },
      // SKU维度行数据：为每个SKU行生成唯一key
      tableRows() {
        return (this.dataSource || []).map(function(row) {
          return Object.assign({}, row, {
            skuRowKey: row.id + '_' + (row.meId != null ? row.meId : 0)
          })
        })
      }
    },
    methods: {
      // ============ 通用方法（电脑端） ============
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
      // 规格值合并（打印标签用），统一取自公共展示工具
      getSpecValueMerged,
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
      handleAdd: function () {
        this.$refs.modalForm.action = "add";
        this.$refs.modalForm.add();
        this.$refs.modalForm.title = "新增";
        this.$refs.modalForm.disableSubmit = false;
      },
      // 手机端新增：统一跳转商品编辑页（页内自动进入新增模式）
      handleMobileAdd: function () {
        sessionStorage.removeItem('materialEditRecord')
        sessionStorage.removeItem('materialEditFrom')
        this.$router.push('/material/edit')
      },
      handleEdit: function (record) {
        // 手机端：跳转到商品编辑页，加载当前商品及其所有SKU
        sessionStorage.setItem('materialEditRecord', JSON.stringify(record))
        // 记录来源页面，编辑页返回按钮可回到商品列表
        sessionStorage.setItem('materialEditFrom', this.$route.fullPath)
        this.$router.push('/material/edit')
      },
      handleCopyAdd(record) {
        this.$refs.modalForm.action = "copyAdd";
        this.$refs.modalForm.edit(record);
        this.$refs.modalForm.title = "复制新增";
        this.$refs.modalForm.disableSubmit = false;
      },
      // 打印SKU标签：先按模板实际尺寸预览，预览内可选择「打印」或「编辑」
      handlePrintLabel(record, sku) {
        if (!sku) {
          this.$message.warning('SKU信息缺失，无法打印')
          return
        }
        this.$refs.labelPrintModal.open(record, sku, this.categoryTree)
      },
      // 预览弹层点「编辑」：打开标签编辑弹层，并按其当前模板加载标签数据
      handleEditLabel({ record, sku, categoryTree, templateId }) {
        var isMobile = window.innerWidth <= 768
        if (isMobile) {
          // 手机端编辑页是独立路由，商品数据需要经 sessionStorage 带过去，
          // 否则编辑页拿不到 record/sku，标签字段会全部为空
          try {
            sessionStorage.setItem('jsh.labelEdit.context', JSON.stringify({
              record: record || {},
              sku: sku || {},
              provider: 'product.label',
              businessType: 'product'
            }))
          } catch (e) { /* 忽略 */ }
          this.$router.push({ path: '/label/edit-mobile', query: { type: 'label', templateId: templateId, businessType: 'product' } })
        } else {
          this.$refs.labelEditModal.open(record, sku, categoryTree || this.categoryTree, templateId, 'label', 'product')
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

      // ============ 手机端专属方法 ============
      /** 手机端多选变化回调：MaterialTable 发出 */
      onMobileSelectionChange(keys, rows) {
        var ids = []
        for (var i = 0; i < rows.length; i++) {
          if (ids.indexOf(rows[i].id) === -1) ids.push(rows[i].id)
        }
        this.mobileSelectedMaterialIds = ids
      },
      /** 手机端批量删除 */
      mobileBatchDel() {
        if (this.mobileSelectedMaterialIds.length <= 0) {
          this.$message.warning('请先勾选要删除的商品！')
          return
        }
        let ids = this.mobileSelectedMaterialIds.join(',')
        let that = this
        this.$confirm({
          title: "确认操作",
          content: `确定删除选中的 ${this.mobileSelectedMaterialIds.length} 个商品？`,
          onOk() {
            that.loading = true
            postAction(that.url.deleteBatch, { ids }).then((res) => {
              if (res.code === 200) {
                that.$message.info('删除成功！')
                that.loadData()
                that.clearMobileSelection()
              } else {
                that.$message.warning(res.data && res.data.message ? res.data.message : '删除失败')
              }
            }).finally(() => { that.loading = false })
          }
        })
      },
      /** 手机端批量启/禁用 */
      mobileBatchSetStatus(status) {
        if (this.mobileSelectedMaterialIds.length <= 0) {
          this.$message.warning('请先勾选商品！')
          return
        }
        let ids = this.mobileSelectedMaterialIds.join(',')
        let that = this
        this.$confirm({
          title: "确认操作",
          content: `确定${status ? '启用' : '禁用'}选中的 ${this.mobileSelectedMaterialIds.length} 个商品？`,
          onOk() {
            that.loading = true
            postAction(that.url.batchSetStatusUrl, { ids, status }).then((res) => {
              if (res.code === 200) {
                that.$message.info((status ? '启用' : '禁用') + '成功！')
                that.loadData()
                that.clearMobileSelection()
              } else {
                that.$message.warning(res.data && res.data.message ? res.data.message : '操作失败')
              }
            }).finally(() => { that.loading = false })
          }
        })
      },
      /** 清空手机端选中 */
      clearMobileSelection() {
        this.mobileSelectedMaterialIds = []
        if (this.$refs.mobileTable && this.$refs.mobileTable.clearMobileSelection) {
          this.$refs.mobileTable.clearMobileSelection()
        }
      },
      /** 底边栏搜索 */
      mobileSearch() {
        this.queryParam.materialParam = this.mobileSearchKeyword
        this.loadData(1)
      },
      /** 标准扫码页识别结果：条码精确查询；文字按「条码→品名→品类」级联查询，命中回列表展示 */
      onProductScanResult(payload) {
        var text = ((payload && payload.text) || '').trim()
        if (!text) return
        var that = this
        if (payload.type === 'barcode') {
          this.searchExactCode(text).then(function(result) {
            if (!result) {
              that.stayOnScanPage('未找到对应商品，请继续扫描')
              return
            }
            that.applyExactCodeResult(text, result)
          })
          return
        }
        // 文字识别：单行按品名；多行先解析再级联
        var lines = text.split(/\r?\n/).map(function(line) { return line.trim() }).filter(Boolean)
        if (lines.length <= 1) {
          this.runCascadeCandidates(buildSearchCandidates(text, null))
          return
        }
        parseProductText(text).then(function(res) {
          var parsed = (res && res.code === 200 && res.data) || {}
          that.runCascadeCandidates(buildSearchCandidates(text, parsed))
        }).catch(function() {
          that.stayOnScanPage('文字解析失败，请重新扫描')
        })
      },
      /** 按候选字段顺序级联查询，前一个字段命中即停止 */
      runCascadeCandidates(candidates) {
        if (!candidates || candidates.length === 0) {
          this.stayOnScanPage('未识别到有效商品信息，请重新扫描')
          return
        }
        var that = this
        var index = 0
        var next = function() {
          if (index >= candidates.length) {
            that.stayOnScanPage('未找到对应商品，请继续扫描')
            return
          }
          var candidate = candidates[index++]
          if (candidate.field === 'barCode') {
            that.searchExactCode(candidate.value).then(function(result) {
              if (result) {
                that.applyExactCodeResult(candidate.value, result)
              } else {
                next()
              }
            })
          } else if (candidate.field === 'name') {
            that.searchByKeyword(candidate.value).then(function(hit) {
              if (hit) {
                that.closeScanPage()
              } else {
                next()
              }
            })
          } else if (candidate.field === 'category') {
            var node = that.findCategoryNodeByName(candidate.value)
            if (!node) {
              next()
              return
            }
            that.searchByCategory(node.value).then(function(hit) {
              if (hit) {
                that.closeScanPage()
              } else {
                next()
              }
            })
          } else {
            next()
          }
        }
        next()
      },
      /** 精确码查询：条码 / SKU码 / SPU商品码 */
      searchExactCode(content) {
        return searchContentByCode({
          content: content,
          mpList: this.queryParam.mpList
        }).then(function(result) {
          if (result && result.success && result.rows && result.rows.length > 0) {
            return result
          }
          return null
        }).catch(function() {
          return null
        })
      },
      /** 精确码命中后回到商品信息页：普通码走列表查询，SPU 商品码直接展示精确结果 */
      applyExactCodeResult(content, result) {
        var that = this
        this.mobileSearchKeyword = content
        if (classifyCode(content) === 'spu') {
          this.showExactRows(result.rows)
          this.closeScanPage()
          return
        }
        this.queryParam.materialParam = content
        this.loadData(1).then(function() {
          if (!that.dataSource || that.dataSource.length === 0) {
            that.showExactRows(result.rows)
          }
          that.closeScanPage()
        }).catch(function() {
          that.showExactRows(result.rows)
          that.closeScanPage()
        })
      },
      /** 列表接口不覆盖 m.code 时，用精确返回行直接填充列表 */
      showExactRows(rows) {
        var list = (rows || []).map(function(row) {
          return Object.assign({}, row, {
            mBarCode: row.mBarCode || row.barCode || row.mBar_code || '',
            sku: row.sku || row.specValues || '',
            currentStock: row.currentStock != null ? row.currentStock : row.stock
          })
        })
        this.dataSource = list
        this.ipagination.total = list.length
      },
      /** 品名模糊查询（沿用底边栏搜索的查询条件） */
      searchByKeyword(keyword) {
        var that = this
        this.mobileSearchKeyword = keyword
        this.queryParam.materialParam = keyword
        return this.loadData(1).then(function() {
          return (that.dataSource || []).length > 0
        }).catch(function() {
          return false
        })
      },
      /** 按商品分类查询 */
      searchByCategory(categoryId) {
        var that = this
        this.mobileSearchKeyword = ''
        this.queryParam.materialParam = ''
        this.queryParam.categoryId = categoryId
        return this.loadData(1).then(function() {
          return (that.dataSource || []).length > 0
        }).catch(function() {
          return false
        })
      },
      /** 在分类树中按名称查找节点（优先精确，其次包含） */
      findCategoryNodeByName(keyword) {
        var target = String(keyword || '').trim().toLowerCase()
        if (!target) return null
        var exact = null
        var contains = null
        var walk = function(nodes) {
          for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i]
            var title = String(node.title || node.name || '').trim().toLowerCase()
            var value = node.value != null ? node.value : node.id
            if (value != null && title === target && !exact) {
              exact = { value: value }
            }
            if (value != null && !contains && title.indexOf(target) > -1) {
              contains = { value: value }
            }
            if (node.children && node.children.length) {
              walk(node.children)
            }
            if (exact) return
          }
        }
        walk(this.categoryTree || [])
        return exact || contains
      },
      /** 未命中时停留在标准扫码页并提示 */
      stayOnScanPage(message) {
        if (this.$refs.mobileSearchPage && this.$refs.mobileSearchPage.setScanTip) {
          this.$refs.mobileSearchPage.setScanTip(message, true)
        }
      },
      /** 关闭标准扫码页 */
      closeScanPage() {
        if (this.$refs.mobileSearchPage && this.$refs.mobileSearchPage.closeScanPage) {
          this.$refs.mobileSearchPage.closeScanPage()
        }
      },
      /** OCR 文字提取完成：把识别到的文字填入搜索框并自动搜索 */
      onOcrOk(res) {
        this.ocrVisible = false
        var text = (res && res.rawText || '').trim()
        if (!text) {
          this.$message.warning('未识别到有效文字')
          return
        }
        this.mobileSearchKeyword = text
        this.queryParam.materialParam = text
        this.loadData(1)
      },
      /** 筛选抽屉确定 */
      applyMobileFilter() {
        this.filterDrawerVisible = false
        this.loadData(1)
      },
    }
  }
</script>
<style scoped>
  @import '~@assets/less/common.less'
</style>
<style>
  /* ==================== 手机端布局 ==================== */
  /* 顶栏右侧操作按钮：统一由 MobileHeaderActions 组件提供样式 */
  /* 条件筛选抽屉样式 */
  .ml-filter-item {
    margin-bottom: 20px;
  }
  .ml-filter-label {
    display: block;
    margin-bottom: 8px;
    font-size: 14px;
    color: #333;
    font-weight: 500;
  }
  .ml-filter-footer {
    margin-top: 24px;
  }

  /* ==================== 电脑端：删除查询条件后保持的原有样式 ==================== */
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
    width: 100px;
    height: 80px;
    object-fit: cover;
    border-radius: 12px;
    vertical-align: middle;
  }
  .row-sku-img-placeholder {
    font-size: 12px;
    color: #ccc;
  }
  .spec-display-cell {
    white-space: pre-line;
    line-height: 18px;
    word-break: break-all;
    text-align: left;
  }

  /* 主表表头：抖音来客风格 */
  .ant-table-thead > tr > th,
  .ant-table-header > table > .ant-table-thead > tr > th {
    background: #F7F9FC !important;
    color: #1D2129 !important;
    font-size: 14px !important;
    font-weight: 500 !important;
    padding: 9px 16px !important;
    border-bottom: 0 none !important;
  }
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
  .sku-cell-img { width: 80px; flex-shrink: 0; text-align: center; }
  .sku-cell-code { width: 170px; flex-shrink: 0; text-align: center; font-family: Consolas, Monaco, monospace; }
  .sku-cell-spec { flex: 1.5; min-width: 100px; text-align: center; }
  .sku-cell-stock { width: 90px; flex-shrink: 0; text-align: center; }
  .sku-cell-price { width: 90px; flex-shrink: 0; text-align: center; }
  .sku-cell-action { width: 180px; flex-shrink: 0; text-align: center; margin-left: auto; }
  .sku-print-link { color: #1890ff; cursor: pointer; }
  .sku-print-link:hover { color: #40a9ff; }
  .sku-img { cursor: pointer; width: 54px; height: 54px; object-fit: cover; border-radius: 4px; vertical-align: middle; }
  .sku-img-placeholder { display: inline-block; width: 54px; height: 54px; line-height: 54px; text-align: center; font-size: 12px; color: #ccc; background: #f5f5f5; border-radius: 4px; }
  .sku-empty { text-align: center; padding: 16px; color: #999; font-size: 13px; border-bottom: 1px solid #F0F2F5; }
  .sku-resize-handle { position: absolute; right: 0; top: 0; bottom: 0; width: 6px; cursor: col-resize; z-index: 1; }
  .sku-resize-handle:hover { background: #1890ff; }
  .sku-row-header .sku-cell { position: relative; user-select: none; }
</style>
<!-- 操作列：宽度已按「按钮总宽 + 20px」计算 -->
<style>
  .ant-table-header > table > .ant-table-thead > tr > th.mt-action-col,
  .ant-table-thead > tr > th.mt-action-col.mt-action-col,
  .ant-table-tbody > tr > td.mt-action-col.mt-action-col {
    padding-left: 0 !important;
    padding-right: 0 !important;
  }
</style>
