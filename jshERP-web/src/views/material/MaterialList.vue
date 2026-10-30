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
  import LabelPrintModal from './modules/LabelPrintModal'
  import { queryMaterialCategoryTreeList } from '@/api/api'
  import { postAction } from '@/api/manage'
  import { getMpListShort } from '@/utils/util'
  import { JeecgListMixin } from '@/mixins/JeecgListMixin'
  import MaterialTable from '@/components/material/MaterialTable'
  import { getSpecValueMerged } from '@/views/material/utils/materialDisplay'
  import Vue from 'vue'

  export default {
    name: "MaterialList",
    mixins:[JeecgListMixin],
    components: {
      MaterialModal,
      ImportFileModal,
      BatchSetInfoModal,
      LabelEditModal,
      LabelPrintModal,
      MaterialTable
    },
    data () {
      return {
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
        }
      }
    },
    created() {
      this.model = Object.assign({}, {});
      this.loadTreeData()
    },
    computed: {
      importExcelUrl: function () {
        return `${window._CONFIG['domianURL']}${this.url.importExcelUrl}`;
      },
      // 操作列按钮（是否显示由按钮权限控制）
      rowActions() {
        const canEdit = this.btnEnableList.indexOf(1) > -1
        return [
          { title: '编辑', click: record => this.handleEdit(record) },
          { title: '复制', click: record => this.handleCopyAdd(record), show: () => canEdit },
          { title: '添加规格', click: record => this.handleAddSpec(record) },
          { title: '打印标签', click: record => this.handlePrintSkuLabel(record) }
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
      // 打印SKU标签：先按模板实际尺寸预览，预览内可选择「打印」或「编辑」
      handlePrintLabel(record, sku) {
        if (!sku) {
          this.$message.warning('SKU信息缺失，无法打印')
          return
        }
        this.$refs.labelPrintModal.open(record, sku, this.categoryTree)
      },
      // 预览弹层点「编辑」：打开标签编辑弹层
      handleEditLabel({ record, sku, categoryTree }) {
        this.$refs.labelEditModal.open(record, sku, categoryTree || this.categoryTree)
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
  /* 规格显示：每个规格单独一行，单列左对齐 */
  .spec-display-cell {
    white-space: pre-line;
    line-height: 18px;
    word-break: break-all;
    text-align: left;
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
