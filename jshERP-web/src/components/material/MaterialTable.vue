<template>
  <data-table
    ref="dataTable"
    v-bind="$attrs"
    :extra-columns="tableColumns"
    :default-columns="resolvedDefaultColumns"
    v-on="$listeners">
    <!-- 父组件自定义列插槽原样透传（内置渲染以外） -->
    <template v-for="name in forwardSlotNames" v-slot:[name]="scope">
      <slot :name="name" v-bind="scope"></slot>
    </template>

    <!-- 内置：图片（100*80、圆角12） -->
    <template slot="mtImage" slot-scope="text, record">
      <img v-if="resolveImg(record)" :src="resolveImg(record)" class="mt-row-img" />
      <span v-else class="mt-row-img-placeholder">无图</span>
    </template>

    <!-- 内置：商品名称（含序/批标记） -->
    <template slot="mtName" slot-scope="text, record">
      {{ record.name }}
      <a-tag v-if="record.enableSerialNumber == 1" color="orange">序</a-tag>
      <a-tag v-if="record.enableBatchNumber == 1" color="orange">批</a-tag>
    </template>

    <!-- 内置：规格显示（每个规格单独一行） -->
    <template slot="mtSpec" slot-scope="text, record">
      <div class="mt-spec-cell">{{ resolveSpec(record) }}</div>
    </template>

    <!-- 内置：码列（商品码 / SKU码 / SN码 三合一，标题与取值随 codeMode 变化） -->
    <template slot="mtCode" slot-scope="text, record">
      {{ record._dataTableSummary ? text : resolveCode(record) }}
    </template>

    <!-- 内置：库存（悬浮显示大单位库存） -->
    <template slot="mtStock" slot-scope="text, record">
      <a-tooltip :title="record.bigUnitStock || ''">{{ text }}</a-tooltip>
    </template>

    <!-- 内置：操作列 -->
    <template slot="mtAction" slot-scope="text, record">
      <span v-if="visibleActions(record).length > 0" class="mt-action-cell">
        <template v-for="(act, index) in visibleActions(record)">
          <a :key="'act-' + index" @click="onAction(act, record)">{{ act.title }}</a>
          <a-divider v-if="index < visibleActions(record).length - 1" :key="'div-' + index" type="vertical" />
        </template>
      </span>
    </template>
  </data-table>
</template>

<script>
  import Vue from 'vue'
  import DataTable from '@/components/DataTable'
  import { MATERIAL_COLUMNS, buildTableColumns, defaultColumnKeys, actionColumnWidth, CODE_MODES } from '@/views/material/config/materialColumns'
  import { getRowImgUrl, getSpecDisplay } from '@/views/material/utils/materialDisplay'

  /** 内置插槽名：父组件透传时跳过，避免同名插槽冲突 */
  const BUILTIN_SLOTS = ['mtImage', 'mtName', 'mtSpec', 'mtCode', 'mtStock', 'mtAction']

  export default {
    name: 'MaterialTable',
    components: { DataTable },
    props: {
      /** 列配置（默认使用商品/库存统一列配置） */
      columns: { type: Array, default: () => MATERIAL_COLUMNS },
      /** 数据粒度：spu=商品(单类) / sku=SKU(单品) / skuDepot=SKU+仓库(库存)，为空不过滤 */
      dimension: { type: String, default: '' },
      /** 默认显示列（field 数组），不传则按列配置的 defaultVisible + 默认顺序 */
      defaultColumns: { type: Array, default: undefined },
      /** 操作列按钮：[{ title, click(record), show(record)? }] */
      actions: { type: Array, default: () => [] },
      /** 码列三合一：把 SPU码/SKU码 合并为一列，取值与标题随 codeMode 变化（库存页使用） */
      mergeCode: { type: Boolean, default: false },
      /** 码列显示方式：spu=商品码 / sku=SKU码 / unit=SN码 */
      codeMode: { type: String, default: 'spu' }
    },
    computed: {
      /** 按粒度过滤并转换为 a-table 列；操作列宽度按按钮数量重新计算 */
      tableColumns() {
        let cols = buildTableColumns(this.dimension, this.columns)
        // 码列三合一：去掉 SPU码 / SKU码 两列，在原位置插入一列动态码列
        if (this.mergeCode) {
          const firstIdx = cols.findIndex(col => col.dataIndex === 'code' || col.dataIndex === 'skuCode')
          cols = cols.filter(col => col.dataIndex !== 'code' && col.dataIndex !== 'skuCode')
          cols.splice(firstIdx > -1 ? firstIdx : 0, 0, {
            title: CODE_MODES.spu.title,
            dataIndex: 'materialCode',
            key: 'materialCode',
            width: 150,
            ellipsis: true,
            summaryRender: true,
            scopedSlots: { customRender: 'mtCode' }
          })
        }
        // 未配置操作按钮的场景（如选商品弹窗）不显示操作列
        if (this.actions.length === 0) {
          return cols.filter(col => col.dataIndex !== 'action')
        }
        const width = actionColumnWidth(this.actions)
        cols.forEach(col => {
          if (col.dataIndex === 'action') col.width = width
        })
        return cols
      },
      /** 默认显示列 */
      resolvedDefaultColumns() {
        const allKeys = this.tableColumns.map(c => c.dataIndex)
        let defaults = (this.defaultColumns && this.defaultColumns.length > 0)
          ? this.defaultColumns
          : defaultColumnKeys(this.dimension, this.columns)
        // 码列三合一后，原有的 code / skuCode 统一映射为 materialCode（只保留一次）
        if (this.mergeCode) {
          const mapped = []
          defaults.forEach(k => {
            if (k === 'code' || k === 'skuCode') {
              if (mapped.indexOf('materialCode') === -1) mapped.push('materialCode')
            } else {
              mapped.push(k)
            }
          })
          defaults = mapped
        }
        return defaults.filter(k => allKeys.indexOf(k) > -1)
      },
      /** 需要透传给 DataTable 的父组件插槽名 */
      forwardSlotNames() {
        return Object.keys(this.$scopedSlots).filter(name => BUILTIN_SLOTS.indexOf(name) === -1)
      }
    },
    watch: {
      codeMode() {
        this.syncCodeTitle()
      }
    },
    mounted() {
      this.syncCodeTitle()
      // 列被重建（配置中心回填、列设置调整）后重新套用码列标题，避免标题被还原
      this.$watch(
        () => (this.$refs.dataTable ? this.$refs.dataTable.displayColumns : null),
        () => this.applyCodeTitle()
      )
    },
    created() {
      // 兼容旧版本列设置：原「规格JSON」列已改为「规格显示」列
      const storageKey = this.$attrs.storageKey || this.$attrs['storage-key']
      if (!storageKey) return
      const saved = Vue.ls.get(storageKey)
      if (saved && typeof saved === 'string' && saved.indexOf('specJson') > -1) {
        Vue.ls.set(storageKey, saved.split(',').map(k => k === 'specJson' ? 'specDisplay' : k))
      }
    },
    methods: {
      resolveImg(record) {
        return getRowImgUrl(record)
      },
      resolveSpec(record) {
        return getSpecDisplay(record)
      },
      /** 码列取值：单类→SPU码 / 单品→SKU码 / 单件→SN码，缺失时回退商品条码 */
      resolveCode(record) {
        if (!record) return ''
        const mode = CODE_MODES[this.codeMode] || CODE_MODES.spu
        return record[mode.field] || record.mBarCode || ''
      },
      /** 只改码列标题（不重建列，避免切换显示方式时重置列设置） */
      syncCodeTitle() {
        if (!this.mergeCode) return
        this.$nextTick(() => this.applyCodeTitle())
      },
      /** 码列标题与当前显示方式对齐（标题已一致时不重复触发重建） */
      applyCodeTitle() {
        const dt = this.$refs.dataTable
        if (!dt || !this.mergeCode) return
        const mode = CODE_MODES[this.codeMode] || CODE_MODES.spu
        const col = (dt.allColumns || []).find(c => c.dataIndex === 'materialCode')
        if (col && col.title !== mode.title) {
          dt.updateColumnTitle('materialCode', mode.title)
        }
      },
      visibleActions(record) {
        return (this.actions || []).filter(act => !act.show || act.show(record))
      },
      onAction(act, record) {
        if (act && typeof act.click === 'function') {
          act.click(record)
        }
      },
      /** 暴露给父组件：当前实际显示的列（用于导出） */
      getVisibleColumns() {
        const dt = this.$refs.dataTable
        return (dt && dt.displayColumns) || []
      },
      /** 暴露给父组件：恢复默认列 */
      resetColumns() {
        this.$refs.dataTable.resetColumns()
      }
    }
  }
</script>

<style scoped>
  .mt-row-img {
    width: 100px;
    height: 80px;
    object-fit: cover;
    border-radius: 12px;
    vertical-align: middle;
  }
  .mt-row-img-placeholder {
    font-size: 12px;
    color: #ccc;
  }
  .mt-spec-cell {
    white-space: pre-line;
    line-height: 18px;
    word-break: break-all;
    text-align: left;
  }
  .mt-action-cell {
    white-space: nowrap;
  }
</style>

<!-- 操作列：宽度已按「按钮总宽 + 20px」计算，取消单元格内边距，保证多出来的正好 20px -->
<style>
  /* 表头另有一条 .ant-table-header > table > .ant-table-thead > tr > th 的 !important 内边距，这里用同等结构提高优先级 */
  .ant-table-header > table > .ant-table-thead > tr > th.mt-action-col,
  .ant-table-thead > tr > th.mt-action-col.mt-action-col,
  .ant-table-tbody > tr > td.mt-action-col.mt-action-col {
    padding-left: 0 !important;
    padding-right: 0 !important;
  }
</style>
