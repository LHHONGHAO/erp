<template>
  <div style="padding: 24px; margin: 0 auto;" :style="{ maxWidth: containerWidth }">
    <h2>DataTablePro 组件测试</h2>
    <p style="color: #999; margin-top: -8px;">
      独立测试页：模拟商品列表业务（规格/价格列、自定义单元格、操作列、多选、分页）。
      不依赖后端接口，数据为本地 mock。
    </p>

    <!-- 组件能力开关 -->
    <a-card size="small" style="margin-bottom: 12px;">
      <div class="switch-row">
        <span>边框：<a-switch v-model="bordered" /></span>
        <span>列宽拖拽：<a-switch v-model="draggable" /></span>
        <span>列设置：<a-switch v-model="showColumnSetting" /></span>
        <span>汇总行：<a-switch v-model="summary" /></span>
        <span>多选：<a-switch v-model="selectable" /></span>
        <span>
          尺寸：
          <a-radio-group v-model="size" size="small">
            <a-radio-button value="small">small</a-radio-button>
            <a-radio-button value="middle">middle</a-radio-button>
          </a-radio-group>
        </span>
        <span>
          容器宽度：
          <a-radio-group v-model="containerWidth" size="small">
            <a-radio-button value="100%">100%</a-radio-button>
            <a-radio-button value="900px">900px</a-radio-button>
          </a-radio-group>
        </span>
      </div>
    </a-card>

    <data-table-pro
      ref="table"
      :bordered="bordered"
      :draggable="draggable"
      :show-column-setting="showColumnSetting"
      :size="size"
      :summary="summary"
      summary-fields="purchaseDecimal,commodityDecimal,marketPrice,currentStock"
      show-width-info
      table-key="test_material_list"
      storage-key="datatable_pro_test"
      :columns="columns"
      :default-columns="defaultColumns"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :row-selection="rowSelection"
      :actions="actions"
      row-key="skuRowKey"
      @change="handleTableChange"
      @column-change="onColumnChange"
      @column-width-change="onWidthChange">
      <template #toolbar>
        <a-button type="primary" @click="loadData">刷新</a-button>
        <a-button @click="resetColumns">恢复默认列</a-button>
        <a-button @click="resetWidths">重置列宽</a-button>
        <span class="selected-info">已选 <b>{{ selectedRowKeys.length }}</b> 项</span>
      </template>

      <template slot="name" slot-scope="text, record">
        {{ record.name }}
        <a-tag v-if="record.enableBatchNumber === 1" color="orange">批</a-tag>
      </template>

      <template slot="status" slot-scope="text">
        <a-tag v-if="text === 1" color="green">启用</a-tag>
        <a-tag v-else color="red">禁用</a-tag>
      </template>

      <template slot="img" slot-scope="text, record">
        <div class="mock-img">{{ record.code.slice(-2) }}</div>
      </template>
    </data-table-pro>

    <!-- 调试信息：用于验证拖拽只影响当前列 -->
    <a-card size="small" title="当前列宽（px）" style="margin-top: 12px;">
      <pre class="width-json">{{ widthJson }}</pre>
      <div style="color: #999; font-size: 12px;">
        提示：拖动某一列后，上面的其它列数值应保持不变；容器 900px 时可观察右侧留空。
      </div>
    </a-card>
  </div>
</template>

<script>
  function mockData(count) {
    const categories = ['服装', '食品', '电子', '日用品']
    const brands = ['品牌A', '品牌B', '品牌C', '品牌D']
    const list = []
    for (let i = 0; i < count; i++) {
      const id = i + 1
      list.push({
        id: id,
        skuRowKey: id + '_0',
        img: 'img-' + id,
        name: '测试商品-' + id,
        code: 'SPU' + String(id).padStart(4, '0'),
        skuCode: 'SKU' + String(id).padStart(4, '0'),
        categoryName: categories[i % 4],
        brand: brands[i % 4],
        unit: ['件', '箱', 'kg', '个'][i % 4],
        purchaseDecimal: (Math.random() * 100 + 10).toFixed(2),
        commodityDecimal: (Math.random() * 200 + 20).toFixed(2),
        marketPrice: (Math.random() * 300 + 30).toFixed(2),
        currentStock: Math.floor(Math.random() * 500),
        status: i % 2,
        enableBatchNumber: i % 3 === 0 ? 1 : 0,
        specValuesText: '颜色:红; 尺码:L',
        remark: i % 5 === 0 ? '备注-' + i : '',
        createTime: '2026-10-' + String((i % 28) + 1).padStart(2, '0')
      })
    }
    return list
  }

  const allData = mockData(86)

  export default {
    name: 'TestDataTablePro',
    data() {
      return {
        bordered: true,
        draggable: true,
        showColumnSetting: true,
        summary: false,
        selectable: true,
        size: 'middle',
        containerWidth: '100%',
        columns: [
          { dataIndex: 'img', title: '图片', width: 70, align: 'center', scopedSlots: { customRender: 'img' } },
          { dataIndex: 'name', title: '商品名称', width: 180, ellipsis: true, scopedSlots: { customRender: 'name' } },
          { dataIndex: 'code', title: 'SPU商品码', width: 130 },
          { dataIndex: 'skuCode', title: 'SKU编码', width: 130 },
          { dataIndex: 'categoryName', title: '产品类型', width: 100 },
          { dataIndex: 'brand', title: '品牌', width: 100, ellipsis: true },
          { dataIndex: 'unit', title: '单位', width: 80, align: 'center' },
          { dataIndex: 'specValuesText', title: '规格值', width: 160, ellipsis: true },
          { dataIndex: 'purchaseDecimal', title: '进货价', width: 100, align: 'right' },
          { dataIndex: 'commodityDecimal', title: '零售价', width: 100, align: 'right' },
          { dataIndex: 'marketPrice', title: '划线价', width: 100, align: 'right' },
          { dataIndex: 'currentStock', title: '库存', width: 90, align: 'right' },
          { dataIndex: 'status', title: '状态', width: 80, align: 'center', scopedSlots: { customRender: 'status' } },
          { dataIndex: 'remark', title: '备注', width: 120, ellipsis: true },
          { dataIndex: 'createTime', title: '创建时间', width: 120 }
        ],
        defaultColumns: ['img', 'name', 'code', 'skuCode', 'categoryName', 'unit',
          'purchaseDecimal', 'commodityDecimal', 'marketPrice', 'currentStock', 'status'],
        dataSource: [],
        loading: false,
        selectedRowKeys: [],
        widthJson: '{}',
        pagination: {
          current: 1,
          pageSize: 10,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (total, range) => range[0] + '-' + range[1] + ' 共' + total + ' 条',
          total: 0
        },
        actions: [
          { title: '编辑', type: 'link', click: this.handleEdit },
          { title: '复制', type: 'link', click: this.handleCopy },
          { title: '删除', type: 'link', confirm: '确认删除？', click: this.handleDelete }
        ]
      }
    },
    computed: {
      rowSelection() {
        if (!this.selectable) return undefined
        return {
          selectedRowKeys: this.selectedRowKeys,
          onChange: this.onSelectChange,
          columnWidth: '40px'
        }
      }
    },
    created() {
      this.loadData()
    },
    mounted() {
      this.$nextTick(() => this.refreshWidthJson())
    },
    methods: {
      loadData() {
        this.loading = true
        setTimeout(() => {
          const { current, pageSize } = this.pagination
          const start = (current - 1) * pageSize
          this.dataSource = allData.slice(start, start + pageSize)
          this.pagination.total = allData.length
          this.loading = false
        }, 200)
      },
      handleTableChange(pagination) {
        this.pagination.current = pagination.current
        this.pagination.pageSize = pagination.pageSize
        this.loadData()
      },
      onSelectChange(keys) {
        this.selectedRowKeys = keys
      },
      resetColumns() {
        this.$refs.table.resetColumns()
      },
      resetWidths() {
        this.$refs.table.resetColumnWidths()
        this.$nextTick(() => this.refreshWidthJson())
      },
      onColumnChange(keys) {
        console.log('[DataTablePro] 列变化：', keys)
      },
      onWidthChange() {
        this.$nextTick(() => this.refreshWidthJson())
      },
      refreshWidthJson() {
        const widths = this.$refs.table ? this.$refs.table.getColumnWidths() : {}
        this.widthJson = JSON.stringify(widths, null, 2)
      },
      handleEdit(record) {
        this.$message.info('编辑：' + record.name)
      },
      handleCopy(record) {
        this.$message.info('复制：' + record.name)
      },
      handleDelete(record) {
        this.$message.success('已删除：' + record.name)
      }
    }
  }
</script>

<style scoped>
  .switch-row {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 20px;
  }
  .selected-info {
    margin-left: 12px;
    color: #666;
  }
  .mock-img {
    width: 40px;
    height: 40px;
    line-height: 40px;
    margin: 0 auto;
    border-radius: 4px;
    background: #e6f7ff;
    color: #1890ff;
    font-size: 12px;
    text-align: center;
  }
  .width-json {
    margin: 0;
    max-height: 260px;
    overflow: auto;
    font-size: 12px;
    color: #555;
  }
</style>
