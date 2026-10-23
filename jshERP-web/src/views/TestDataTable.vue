<template>
  <div style="padding: 24px; max-width: 1500px; margin: 0 auto;">
    <h2>DataTable 组件测试</h2>

    <!-- 组件能力开关 -->
    <a-card size="small" style="margin-bottom: 12px;">
      <div style="display: flex; flex-wrap: wrap; align-items: center;">
        <span style="margin-right: 20px;">边框：<a-switch v-model="bordered" /></span>
        <span style="margin-right: 20px;">列宽拖拽：<a-switch v-model="draggable" /></span>
        <span style="margin-right: 20px;">汇总行：<a-switch v-model="summary" /></span>
        <span style="margin-right: 20px;">可编辑：<a-switch v-model="editable" /></span>
        <span>尺寸：
          <a-radio-group v-model="size" size="small" style="margin-left: 6px;">
            <a-radio-button value="small">small</a-radio-button>
            <a-radio-button value="middle">middle</a-radio-button>
          </a-radio-group>
        </span>
      </div>
      <div v-if="lastEdit" style="margin-top: 8px; color: #666;">
        最后编辑：记录 #{{ lastEdit.id }} 的「{{ lastEdit.key }}」 = {{ lastEdit.value }}
      </div>
    </a-card>

    <data-table
      ref="testTable"
      :bordered="bordered"
      :draggable="draggable"
      :size="size"
      :summary="summary"
      :editable="editable"
      summary-fields="price,stock"
      :detail-columns="detailColumns"
      :detail-actions="detailActions"
      detail-row-key="id"
      :extra-columns="defColumns"
      :default-columns="defDataIndex"
      storage-key="columns_TestDataTable"
      :data-source="dataSource"
      :loading="loading"
      :pagination="pagination"
      :row-selection="{ selectedRowKeys: selectedRowKeys, onChange: onSelectChange, type: 'checkbox' }"
      :actions="tableActions"
      :expanded-row-keys="expandedRowKeys"
      row-key="id"
      @expand="onExpand"
      @change="handleTableChange"
      @edit-change="onEditChange"
    >
      <template #toolbar>
        <a-button type="primary" @click="loadData">刷新</a-button>
        <a-button style="margin-left: 8px" @click="reset">重置列</a-button>
        <span style="margin-left: 12px; color: #666;">
          已选 <b>{{ selectedRowKeys.length }}</b> 项
        </span>
      </template>

      <template slot="img" slot-scope="text, record">
        <img v-if="record.img" :src="record.img" style="width: 40px;height:40px;border-radius:4px;object-fit:cover;" />
        <span v-else style="color:#ccc;">-</span>
      </template>

      <template slot="status" slot-scope="text, record">
        <a-tag v-if="record.status === 1" color="green">启用</a-tag>
        <a-tag v-else color="red">禁用</a-tag>
      </template>

      <template slot="name" slot-scope="text, record">
        <a>{{ record.name }}</a>
        <a-tag v-if="record.vip" color="gold" style="margin-left: 4px;">VIP</a-tag>
      </template>
    </data-table>
  </div>
</template>

<script>
import DataTable from '@/components/DataTable'

// 模拟数据
function mockData(count) {
  const list = []
  for (let i = 0; i < count; i++) {
    const id = i + 1
    list.push({
      id: id,
      name: '商品名称-' + id,
      code: 'CODE' + String(id).padStart(3, '0'),
      category: ['服装', '食品', '电子', '日用品'][i % 4],
      brand: ['品牌A', '品牌B', '品牌C', '品牌D'][i % 4],
      price: (Math.random() * 1000 + 10).toFixed(2),
      stock: Math.floor(Math.random() * 500),
      unit: ['件', '箱', 'kg', '个'][i % 4],
      status: i % 2, // 0禁用 1启用
      vip: i % 3 === 0,
      img: i % 4 === 0 ? 'https://img.alicdn.com/imgextra/i3/2200724983589/O1CN01K5f8HP1rZQFu4fR3y_!!2200724983589.jpg' : '',
      createTime: '2026-' + String(Math.floor(Math.random() * 12) + 1).padStart(2, '0') + '-01',
      remark: i % 5 === 0 ? '备注信息-' + i : '',
      loading: false,
      childrens: []
    })
  }
  return list
}

const allData = mockData(86)

export default {
  name: 'DataTableTest',
  components: { DataTable },
  data() {
    return {
      bordered: true,
      draggable: false,
      summary: false,
      editable: false,
      size: 'middle',
      defColumns: [
        { dataIndex: 'img', title: '图片', width: 60, align: 'center', scopedSlots: { customRender: 'img' } },
        { dataIndex: 'name', title: '名称', width: 160, scopedSlots: { customRender: 'name' } },
        { dataIndex: 'code', title: '编码', width: 100 },
        { dataIndex: 'category', title: '类别', width: 90, editType: 'select', editOptions: ['服装', '食品', '电子', '日用品'] },
        { dataIndex: 'brand', title: '品牌', width: 90, editType: 'select', editOptions: ['品牌A', '品牌B', '品牌C', '品牌D'] },
        { dataIndex: 'price', title: '价格', width: 90, align: 'right', editType: 'inputNumber', editProps: { precision: 2, min: 0 } },
        { dataIndex: 'stock', title: '库存', width: 80, align: 'right', editType: 'inputNumber', editProps: { precision: 0, min: 0 } },
        { dataIndex: 'unit', title: '单位', width: 70, align: 'center', editType: 'select', editOptions: ['件', '箱', 'kg', '个'] },
        { dataIndex: 'status', title: '状态', width: 70, scopedSlots: { customRender: 'status' } },
        { dataIndex: 'createTime', title: '创建时间', width: 110 },
        { dataIndex: 'remark', title: '备注', ellipsis: true, width: 120, editType: 'input' }
      ],
      defDataIndex: ['img', 'name', 'code', 'category', 'brand', 'price', 'stock', 'unit', 'status', 'createTime', 'remark'],
      detailColumns: [
        { dataIndex: 'batch', title: '批次', width: 120 },
        { dataIndex: 'serial', title: '序列号', width: 140 },
        { dataIndex: 'qty', title: '数量', width: 80, align: 'right' },
        { dataIndex: 'warehouse', title: '仓库', width: 100 }
      ],
      detailActions: [
        { title: '打印', click: this.handlePrintLabel }
      ],
      dataSource: [],
      loading: false,
      selectedRowKeys: [],
      expandedRowKeys: [],
      lastEdit: null,
      pagination: {
        current: 1,
        pageSize: 10,
        pageSizeOptions: ['10', '20', '30'],
        showTotal: (total, range) => range[0] + '-' + range[1] + ' 共' + total + '条',
        showQuickJumper: true,
        showSizeChanger: true,
        total: 0
      },
      tableActions: [
        { title: '查看', icon: 'eye', type: 'link', click: this.handleView },
        { title: '编辑', icon: 'edit', type: 'link', click: this.handleEdit },
        { title: '删除', icon: 'delete', type: 'link', confirm: '确认删除？', click: this.handleDelete }
      ]
    }
  },
  created() {
    this.loadData()
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
      }, 300)
    },
    handleTableChange(pagination) {
      this.pagination.current = pagination.current
      this.pagination.pageSize = pagination.pageSize
      this.loadData()
    },
    onSelectChange(keys) {
      this.selectedRowKeys = keys
    },
    onExpand(expanded, record) {
      if (expanded) {
        this.expandedRowKeys = [...new Set([...this.expandedRowKeys, record.id])]
        if (!record.childrens || record.childrens.length === 0) {
          this.loadDetail(record)
        }
      } else {
        this.expandedRowKeys = this.expandedRowKeys.filter(key => key !== record.id)
      }
    },
    loadDetail(record) {
      record.loading = true
      setTimeout(() => {
        record.childrens = [
          { id: record.id * 10 + 1, batch: 'PC2026-01', serial: 'SN' + String(record.id).padStart(3, '0') + '-01', qty: 10, warehouse: '主仓' },
          { id: record.id * 10 + 2, batch: 'PC2026-02', serial: 'SN' + String(record.id).padStart(3, '0') + '-02', qty: 20, warehouse: '次仓' }
        ]
        record.loading = false
      }, 400)
    },
    reset() {
      this.$refs.testTable.resetColumns()
    },
    onEditChange({ record, key, value }) {
      this.lastEdit = { id: record.id, key: key, value: value }
    },
    handleView(record) {
      this.$message.info('查看: ' + record.name)
    },
    handleEdit(record) {
      this.$message.info('编辑: ' + record.name)
    },
    handleDelete(record) {
      this.$message.success('已删除: ' + record.name)
    },
    handlePrintLabel(record, row) {
      this.$message.info('打印标签: ' + record.name + ' / ' + row.serial)
    }
  }
}
</script>
