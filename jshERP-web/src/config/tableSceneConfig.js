// 表格列显示配置中心的元数据清单
// 统一维护：表格场景（tableKey）与数据库表（含主/附表关系），供配置页面下拉选择与展示

// 数据库表清单：table=物理表名，name=中文备注，role=main(主表)/sub(附表)
export const DB_TABLES = [
  // 商品相关
  { table: 'jsh_material', name: '商品信息', role: 'main' },
  { table: 'jsh_material_extend', name: '商品SKU', role: 'sub' },
  { table: 'jsh_material_category', name: '商品类别', role: 'main' },
  { table: 'jsh_material_attribute', name: '商品属性', role: 'main' },
  { table: 'jsh_material_property', name: '商品属性设置', role: 'main' },
  { table: 'jsh_material_current_stock', name: '商品库存', role: 'main' },
  { table: 'jsh_material_initial_stock', name: '期初库存', role: 'main' },
  // 单据相关
  { table: 'jsh_depot_head', name: '单据主表', role: 'main' },
  { table: 'jsh_depot_item', name: '单据明细', role: 'sub' },
  // 财务账户相关
  { table: 'jsh_account_head', name: '账户单据主表', role: 'main' },
  { table: 'jsh_account_item', name: '账户单据明细', role: 'sub' },
  // 基础资料
  { table: 'jsh_depot', name: '仓库', role: 'main' },
  { table: 'jsh_supplier', name: '供应商/客户', role: 'main' },
  { table: 'jsh_person', name: '经手人', role: 'main' },
  { table: 'jsh_unit', name: '单位', role: 'main' },
  { table: 'jsh_in_out_item', name: '收支项目', role: 'main' },
  { table: 'jsh_account', name: '结算账户', role: 'main' },
  { table: 'jsh_organization', name: '部门', role: 'main' },
  // 系统
  { table: 'jsh_user', name: '用户', role: 'main' },
  { table: 'jsh_role', name: '角色', role: 'main' },
  { table: 'jsh_function', name: '菜单', role: 'main' },
  { table: 'jsh_tenant', name: '租户', role: 'main' },
  { table: 'jsh_system_config', name: '系统配置', role: 'main' },
  { table: 'jsh_platform_config', name: '平台配置', role: 'main' },
  { table: 'jsh_log', name: '日志', role: 'main' },
  { table: 'jsh_msg', name: '消息', role: 'main' },
  { table: 'jsh_serial_number', name: '序列号', role: 'main' }
]

// 表格场景清单：key=场景标识(tableKey)，name=页面名，remark=备注（主表名或功能）
export const TABLE_SCENES = [
  // 商品
  { key: 'material_list', name: '商品信息', remark: '商品列表', tables: ['jsh_material', 'jsh_material_extend'] },
  { key: 'material_select', name: '选择商品', remark: '选择商品弹窗', tables: ['jsh_material', 'jsh_material_extend'], physical: true },
  { key: 'material_category', name: '商品类别', remark: '商品类别', tables: ['jsh_material_category'] },
  { key: 'material_attribute', name: '多属性', remark: '商品属性', tables: ['jsh_material_attribute'] },
  // 零售
  { key: 'bill_retail_out', name: '零售出库', remark: '零售出库单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_retail_out_detail', name: '零售出库明细', remark: '零售出库新增页明细', tables: ['jsh_depot_item'] },
  { key: 'bill_retail_back', name: '零售退货', remark: '零售退货单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  // 采购
  { key: 'bill_purchase_in', name: '采购入库', remark: '采购入库单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_purchase_order', name: '采购订单', remark: '采购订单表', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_purchase_back', name: '采购退货', remark: '采购退货单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_purchase_apply', name: '请购单', remark: '请购单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  // 销售
  { key: 'bill_sale_out', name: '销售出库', remark: '销售出库单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_sale_order', name: '销售订单', remark: '销售订单表', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_sale_back', name: '销售退货', remark: '销售退货单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  // 仓库
  { key: 'bill_other_in', name: '其它入库', remark: '其它入库单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_other_out', name: '其它出库', remark: '其它出库单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_allocation_out', name: '调拨出库', remark: '调拨出库单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_assemble', name: '组装单', remark: '组装单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  { key: 'bill_disassemble', name: '拆卸单', remark: '拆卸单', tables: ['jsh_depot_head', 'jsh_depot_item'] },
  // 财务
  { key: 'financial_item_in', name: '收入单', remark: '收入单', tables: ['jsh_account_head', 'jsh_account_item'] },
  { key: 'financial_item_out', name: '支出单', remark: '支出单', tables: ['jsh_account_head', 'jsh_account_item'] },
  { key: 'financial_money_in', name: '收款单', remark: '收款单', tables: ['jsh_account_head', 'jsh_account_item'] },
  { key: 'financial_money_out', name: '付款单', remark: '付款单', tables: ['jsh_account_head', 'jsh_account_item'] },
  { key: 'financial_giro', name: '转账单', remark: '转账单', tables: ['jsh_account_head', 'jsh_account_item'] },
  { key: 'financial_advance_in', name: '收预付款', remark: '收预付款单', tables: ['jsh_account_head', 'jsh_account_item'] },
  // 基础资料
  { key: 'vendor_list', name: '供应商信息', remark: '供应商', tables: ['jsh_supplier'] },
  { key: 'customer_list', name: '客户信息', remark: '客户', tables: ['jsh_supplier'] },
  { key: 'depot_list', name: '仓库信息', remark: '仓库', tables: ['jsh_depot'] },
  { key: 'person_list', name: '经手人管理', remark: '经手人', tables: ['jsh_person'] },
  { key: 'unit_list', name: '多单位', remark: '单位', tables: ['jsh_unit'] },
  { key: 'in_out_item_list', name: '收支项目', remark: '收支项目', tables: ['jsh_in_out_item'] },
  { key: 'account_list', name: '结算账户', remark: '结算账户', tables: ['jsh_account'] },
  // 系统
  { key: 'user_list', name: '用户管理', remark: '用户', tables: ['jsh_user'] },
  { key: 'role_list', name: '角色管理', remark: '角色', tables: ['jsh_role'] },
  { key: 'function_list', name: '菜单管理', remark: '菜单', tables: ['jsh_function'] },
  { key: 'tenant_list', name: '租户管理', remark: '租户', tables: ['jsh_tenant'] },
  { key: 'log_list', name: '日志管理', remark: '日志', tables: ['jsh_log'] }
]

// 业务扩展字段字典：关联查询/计算产生的别名字段（非DB物理列）
// dataIndex 与各表格 DataTable 组件的 extraColumns.dataIndex 保持一致，
// 配置中心与列设置共用此字典，实现「字段 ↔ 表格列」联动
export const SCENE_EXTRA_FIELDS = {
  material_list: [
    { dataIndex: 'name', title: '名称' },
    { dataIndex: 'unit', title: '单位' },
    { dataIndex: 'brand', title: '品牌' },
    { dataIndex: 'mnemonic', title: '助记码' },
    { dataIndex: 'categoryName', title: '类别' },
    { dataIndex: 'weight', title: '基础重量' },
    { dataIndex: 'mfrs', title: '制造商' },
    { dataIndex: 'otherField1', title: '面料' },
    { dataIndex: 'otherField2', title: '安全类别' },
    { dataIndex: 'otherField3', title: '执行标准' },
    { dataIndex: 'otherField4', title: '扩展4' },
    { dataIndex: 'otherField5', title: '扩展5' },
    { dataIndex: 'otherField6', title: '等级' },
    { dataIndex: 'otherField7', title: '检验' },
    { dataIndex: 'id', title: '商品ID' },
    { dataIndex: 'mBarCode', title: '条码' },
    { dataIndex: 'initialStock', title: '初始库存' },
    { dataIndex: 'stock', title: '库存' },
    { dataIndex: 'purchaseDecimal', title: '采购价' },
    { dataIndex: 'commodityDecimal', title: '零售价' },
    { dataIndex: 'wholesaleDecimal', title: '销售价' },
    { dataIndex: 'lowDecimal', title: '最低售价' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'enabled', title: '状态' },
    { dataIndex: 'action', title: '操作' }
  ],
  material_select: [
    { dataIndex: 'categoryName', title: '品类' },
    { dataIndex: 'meId', title: 'SKU_ID' },
    { dataIndex: 'stock', title: '库存' }
  ],
  // 零售
  bill_retail_out: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '会员' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'payType', title: '收款类型' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'getAmount', title: '收款金额' },
    { dataIndex: 'backAmount', title: '找零' },
    { dataIndex: 'accountName', title: '收款账户' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_retail_out_detail: [
    { dataIndex: 'depotId', title: '仓库名称' },
    { dataIndex: 'imgName', title: '图片' },
    { dataIndex: 'barCode', title: '条码' },
    { dataIndex: 'name', title: '名称' },
    { dataIndex: 'standard', title: '规格' },
    { dataIndex: 'model', title: '型号' },
    { dataIndex: 'color', title: '颜色' },
    { dataIndex: 'brand', title: '品牌' },
    { dataIndex: 'mfrs', title: '制造商' },
    { dataIndex: 'otherField1', title: '扩展1' },
    { dataIndex: 'otherField2', title: '扩展2' },
    { dataIndex: 'otherField3', title: '扩展3' },
    { dataIndex: 'categoryName', title: '类别' },
    { dataIndex: 'skuCode', title: 'SKU编码' },
    { dataIndex: 'commodityUnit', title: 'SKU单位' },
    { dataIndex: 'meId', title: 'SKU_ID' },
    { dataIndex: 'stock', title: '库存' },
    { dataIndex: 'purchaseDecimal', title: '采购价' },
    { dataIndex: 'commodityDecimal', title: '零售价' },
    { dataIndex: 'wholesaleDecimal', title: '销售价' },
    { dataIndex: 'lowDecimal', title: '最低售价' },
    { dataIndex: 'unit', title: '单位' },
    { dataIndex: 'snList', title: '序列号' },
    { dataIndex: 'batchNumber', title: '批号' },
    { dataIndex: 'expirationDate', title: '有效期' },
    { dataIndex: 'sku', title: '多属性' },
    { dataIndex: 'operNumber', title: '数量' },
    { dataIndex: 'unitPrice', title: '单价' },
    { dataIndex: 'allPrice', title: '金额' },
    { dataIndex: 'remark', title: '备注' }
  ],
  bill_retail_back: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '会员' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'linkNumber', title: '关联单据' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'getAmount', title: '付款金额' },
    { dataIndex: 'backAmount', title: '找零' },
    { dataIndex: 'accountName', title: '付款账户' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  // 采购
  bill_purchase_order: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '供应商' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'linkApply', title: '关联请购单' },
    { dataIndex: 'linkNumber', title: '关联订单' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'totalTaxLastMoney', title: '含税合计' },
    { dataIndex: 'discount', title: '优惠率' },
    { dataIndex: 'discountMoney', title: '付款优惠' },
    { dataIndex: 'discountLastMoney', title: '优惠后金额' },
    { dataIndex: 'accountName', title: '结算账户' },
    { dataIndex: 'changeAmount', title: '支付订金' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_purchase_in: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '供应商' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'linkNumber', title: '关联订单' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'totalTaxLastMoney', title: '含税合计' },
    { dataIndex: 'discount', title: '优惠率' },
    { dataIndex: 'discountMoney', title: '付款优惠' },
    { dataIndex: 'otherMoney', title: '其它费用' },
    { dataIndex: 'needInMoney', title: '待付金额' },
    { dataIndex: 'accountName', title: '结算账户' },
    { dataIndex: 'deposit', title: '扣除订金' },
    { dataIndex: 'changeAmount', title: '本次付款' },
    { dataIndex: 'debt', title: '本次欠款' },
    { dataIndex: 'lastDebt', title: '最终欠款' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_purchase_back: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '供应商' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'linkNumber', title: '关联单据' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'totalTaxLastMoney', title: '含税合计' },
    { dataIndex: 'discount', title: '优惠率' },
    { dataIndex: 'discountMoney', title: '退款优惠' },
    { dataIndex: 'otherMoney', title: '其它费用' },
    { dataIndex: 'needBackMoney', title: '待退金额' },
    { dataIndex: 'accountName', title: '结算账户' },
    { dataIndex: 'changeAmount', title: '本次退款' },
    { dataIndex: 'debt', title: '本次欠款' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_purchase_apply: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  // 销售
  bill_sale_order: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '客户' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'salesManStr', title: '销售人员' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'totalTaxLastMoney', title: '含税合计' },
    { dataIndex: 'discount', title: '优惠率' },
    { dataIndex: 'discountMoney', title: '收款优惠' },
    { dataIndex: 'discountLastMoney', title: '优惠后金额' },
    { dataIndex: 'accountName', title: '结算账户' },
    { dataIndex: 'changeAmount', title: '收取订金' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'purchaseStatus', title: '采购进度' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_sale_out: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '客户' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'linkNumber', title: '关联订单' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'totalTaxLastMoney', title: '含税合计' },
    { dataIndex: 'discount', title: '优惠率' },
    { dataIndex: 'discountMoney', title: '收款优惠' },
    { dataIndex: 'otherMoney', title: '其它费用' },
    { dataIndex: 'needOutMoney', title: '待收金额' },
    { dataIndex: 'accountName', title: '结算账户' },
    { dataIndex: 'deposit', title: '扣除订金' },
    { dataIndex: 'changeAmount', title: '本次收款' },
    { dataIndex: 'debt', title: '本次欠款' },
    { dataIndex: 'lastDebt', title: '最终欠款' },
    { dataIndex: 'salesManStr', title: '销售人员' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_sale_back: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '客户' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'linkNumber', title: '关联单据' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'totalTaxLastMoney', title: '含税合计' },
    { dataIndex: 'discount', title: '优惠率' },
    { dataIndex: 'discountMoney', title: '退款优惠' },
    { dataIndex: 'otherMoney', title: '其它费用' },
    { dataIndex: 'needBackMoney', title: '待退金额' },
    { dataIndex: 'accountName', title: '结算账户' },
    { dataIndex: 'changeAmount', title: '本次退款' },
    { dataIndex: 'debt', title: '本次欠款' },
    { dataIndex: 'salesManStr', title: '销售人员' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  // 仓库
  bill_other_in: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '供应商' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'linkNumber', title: '关联单据' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_other_out: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '客户' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'linkNumber', title: '关联单据' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_allocation_out: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_assemble: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  bill_disassemble: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'number', title: '单据编号' },
    { dataIndex: 'materialsList', title: '商品信息' },
    { dataIndex: 'operTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'materialCount', title: '数量' },
    { dataIndex: 'totalPrice', title: '金额合计' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  // 财务
  financial_advance_in: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '付款会员' },
    { dataIndex: 'billNo', title: '单据编号' },
    { dataIndex: 'billTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'handsPersonName', title: '财务人员' },
    { dataIndex: 'totalPrice', title: '合计金额' },
    { dataIndex: 'changeAmount', title: '收款金额' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  financial_item_in: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '往来单位' },
    { dataIndex: 'billNo', title: '单据编号' },
    { dataIndex: 'billTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'handsPersonName', title: '财务人员' },
    { dataIndex: 'accountName', title: '收入账户' },
    { dataIndex: 'changeAmount', title: '收入金额' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  financial_item_out: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '往来单位' },
    { dataIndex: 'billNo', title: '单据编号' },
    { dataIndex: 'billTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'handsPersonName', title: '财务人员' },
    { dataIndex: 'accountName', title: '支出账户' },
    { dataIndex: 'changeAmount', title: '支出金额' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  financial_money_in: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '客户' },
    { dataIndex: 'billNo', title: '单据编号' },
    { dataIndex: 'billTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'handsPersonName', title: '财务人员' },
    { dataIndex: 'accountName', title: '收款账户' },
    { dataIndex: 'totalPrice', title: '合计收款' },
    { dataIndex: 'discountMoney', title: '优惠金额' },
    { dataIndex: 'changeAmount', title: '实际收款' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  financial_money_out: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'organName', title: '供应商' },
    { dataIndex: 'billNo', title: '单据编号' },
    { dataIndex: 'billTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'handsPersonName', title: '财务人员' },
    { dataIndex: 'accountName', title: '付款账户' },
    { dataIndex: 'totalPrice', title: '合计付款' },
    { dataIndex: 'discountMoney', title: '优惠金额' },
    { dataIndex: 'changeAmount', title: '实际付款' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ],
  financial_giro: [
    { dataIndex: 'action', title: '操作' },
    { dataIndex: 'billNo', title: '单据编号' },
    { dataIndex: 'billTimeStr', title: '单据日期' },
    { dataIndex: 'userName', title: '操作员' },
    { dataIndex: 'handsPersonName', title: '财务人员' },
    { dataIndex: 'accountName', title: '付款账户' },
    { dataIndex: 'changeAmount', title: '实付金额' },
    { dataIndex: 'remark', title: '备注' },
    { dataIndex: 'status', title: '状态' }
  ]
}

// 根据场景 key 获取业务扩展字段
export function getSceneExtraFields(key) {
  return SCENE_EXTRA_FIELDS[key] || []
}

// 根据表名获取中文备注
export function getTableName(table) {
  const found = DB_TABLES.find(t => t.table === table)
  return found ? found.name : table
}

// 根据场景 key 获取场景定义
export function getScene(key) {
  return TABLE_SCENES.find(s => s.key === key)
}
