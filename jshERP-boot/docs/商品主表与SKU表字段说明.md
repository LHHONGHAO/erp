# 商品主表（jsh_material）与 SKU 表（jsh_material_extend）字段说明

> 本文档基于 `jsh_erp.sql` 建表脚本及系列升级脚本（`sku_upgrade.sql`、`sku_upgrade_v2.sql`、`product_sku_relation_upgrade.sql`、`other_field4_5_upgrade.sql`、`material_biz_fields_upgrade.sql`、`material_remove_fields_upgrade.sql`、`material_remove_fields_upgrade_v2.sql`）和 Java 实体类整理。

---

## 一、表关系总览

```
jsh_material（商品主表 / SPU）
  │  id（主键）
  │  code（SPU商品码，唯一）
  │
  ├── 1 : N ──▶ jsh_material_extend（SKU表 / 商品扩展）
  │                material_id → jsh_material.id
  │                sku_code（SKU规格组合编码，唯一）
  │
  ├── N : 1 ──▶ jsh_material_category（商品类别表）
  │                category_id → jsh_material_category.id
  │
  └── N : 1 ──▶ jsh_unit（多单位表）
                   （原 unit_id 字段已删除，单位由主表 jsh_material.unit 维护）
```

---

## 二、商品主表 `jsh_material`

**表注释：** 产品表（SPU 表，一个商品款式一条记录，不区分颜色/尺码等规格）

| 序号 | 字段名 | 类型 | 作用 | 数据来源 |
|:---:|--------|------|------|----------|
| 1 | `id` | bigint | 主键，自增 | 数据库自增，系统自动生成 |
| 2 | `category_id` | bigint | 产品类型 id，关联商品类别表 `jsh_material_category.id` | 商品资料录入界面「商品类别」下拉选择 |
| 3 | `name` | varchar(100) | 商品名称 | 商品资料录入界面「名称」手填 |
| 4 | `code` | varchar(50) | SPU 商品码（稳定唯一编码） | 新增时由系统按规则生成：`SP` + 2位类型码(类别code) + 系统短码（`model` 款号字段已删除，不再取款号）；历史空码由升级脚本 `product_sku_relation_upgrade.sql` 补齐为 `SP{类型码}-{id补零8位}`；有唯一索引 `uk_material_spu_code` |
| 5 | `bar_code` | varchar(50) | 商品条码（主表统一管理，同一商品同一条码） | 由升级脚本 `sku_upgrade_v2.sql` 从默认 SKU(`jsh_material_extend` 中 `default_flag='1'`)的 `bar_code` 回填；新录入时可手填 |
| 6 | `mfrs` | varchar(50) | 制造商 | 商品资料录入界面「制造商」手填 |
| 7 | `brand` | varchar(100) | 品牌 | 商品资料录入界面「品牌」手填 |
| 8 | `mnemonic` | varchar(100) | 助记码 | 商品资料录入界面「助记码」手填，一般由名称拼音首字母生成 |
| 9 | `unit` | varchar(50) | 基础单位（单个） | 商品资料录入界面「单位」手填 |
| 10 | `remark` | varchar(500) | 备注 | 商品资料录入界面「备注」手填 |
| 11 | `weight` | decimal(24,6) | 基础重量(kg) | 商品资料录入界面「重量」手填 |
| 12 | `other_field1` | varchar(500) | 自定义扩展字段1（原用于面料） | 商品属性配置界面可改名；由 `material_biz_fields_upgrade.sql` 回填至 `fabric` 后可废弃 |
| 13 | `other_field2` | varchar(500) | 自定义扩展字段2（原用于安全类别） | 同上；回填至 `safety_category` |
| 14 | `other_field3` | varchar(500) | 自定义扩展字段3（原用于执行标准） | 同上；回填至 `executive_standard` |
| 15 | `other_field4` | varchar(200) | 自定义扩展字段4 | 由 `other_field4_5_upgrade.sql` 新增；商品属性配置界面可改名 |
| 16 | `other_field5` | varchar(200) | 自定义扩展字段5 | 同上 |
| 17 | `other_field6` | varchar(500) | 自定义扩展字段6（原用于等级） | 回填至 `grade` |
| 18 | `other_field7` | varchar(500) | 自定义扩展字段7（原用于检验） | 回填至 `inspection` |
| 19 | `enable_serial_number` | varchar(1) | 是否开启序列号：0否，1是 | 商品资料录入界面「序列号」开关 |
| 20 | `enable_batch_number` | varchar(1) | 是否开启批号：0否，1是 | 商品资料录入界面「批号」开关；**注意**：`material_remove_fields_upgrade.sql` 计划删除该列 |
| 21 | `position` | varchar(100) | 仓位货架 | 商品资料录入界面「仓位」手填；**注意**：`material_remove_fields_upgrade.sql` 计划删除该列 |
| 22 | `fabric` | varchar(100) | 面料 | 由 `material_biz_fields_upgrade.sql` 新增；从 `other_field1` 回填，新录入直接手填 |
| 23 | `safety_category` | varchar(100) | 安全类别 | 同上；从 `other_field2` 回填 |
| 24 | `executive_standard` | varchar(100) | 执行标准 | 同上；从 `other_field3` 回填 |
| 25 | `grade` | varchar(100) | 等级 | 同上；从 `other_field6` 回填 |
| 26 | `inspection` | varchar(100) | 检验 | 同上；从 `other_field7` 回填 |
| 27 | `filler` | varchar(100) | 填充物 | 由 `material_filler_field_upgrade.sql` 新增；从 `other_field4` 回填，新录入直接手填 |
| 28 | `tenant_id` | bigint | 租户 id，关联 `jsh_tenant` | 系统根据当前登录用户所属租户自动写入 |
| 29 | `delete_flag` | varchar(1) | 删除标记：0未删除，1删除（逻辑删除） | 删除商品时由系统置为 '1'，不物理删除 |

### 索引
- `PRIMARY KEY (id)`
- `FK675951272AB6672C (category_id)` —— 关联类别表
- `tenant_id` —— 租户过滤
- `uk_material_spu_code (code)` —— SPU 码唯一约束（由 `product_sku_relation_upgrade.sql` 添加）

> 原 `UnitId (unit_id)` 索引随 `unit_id` 字段一并删除。

---

## 三、SKU 表 `jsh_material_extend`

**表注释：** 产品价格扩展（SKU 表，一个商品下的一个规格/单位组合一条记录）

| 序号 | 字段名 | 类型 | 作用 | 数据来源 |
|:---:|--------|------|------|----------|
| 1 | `id` | bigint | 主键，自增 | 数据库自增，系统自动生成 |
| 2 | `material_id` | bigint | 商品 id，关联 `jsh_material.id` | 新增 SKU 时由所属商品的 `id` 自动写入 |
| 3 | `spec_values` | text | 规格值组合 JSON，存储该 SKU 的动态规格属性 | 由 `sku_upgrade_v2.sql` 新增；格式如 `[{"attributeId":1,"attributeName":"颜色","valueText":"红","valueCode":"01"}]`；商品资料录入界面选择规格后由系统生成 |
| 4 | `sku_code` | varchar(80) | SKU 规格组合编码（唯一） | 由 `sku_upgrade.sql` 新增，`product_sku_relation_upgrade.sql` 扩为 80 位；规则：SPU码 + 各规格属性值编码拼接（如 `SP02-CNG2T2005-01-04-08`）；历史空码由升级脚本补齐为 `{SPU码}-V{id补零8位}`；有唯一索引 `uk_material_extend_sku_code` |
| 5 | `purchase_decimal` | decimal(24,6) | 采购价格 | 商品资料录入界面「采购价」手填 |
| 6 | `commodity_decimal` | decimal(24,6) | 零售价格 | 商品资料录入界面「零售价」手填 |
| 7 | `wholesale_decimal` | decimal(24,6) | 销售（批发）价格 | 商品资料录入界面「销售价」手填 |
| 8 | `low_decimal` | decimal(24,6) | 最低售价 | 商品资料录入界面「最低售价」手填 |
| 9 | `default_flag` | varchar(1) | 是否为默认单位：1是，0否 | 商品资料录入界面设置；每个商品应至少有一条 `default_flag='1'` 的记录 |
| 10 | `create_time` | datetime | 创建日期 | 新增记录时由系统自动写入当前时间 |
| 11 | `create_serial` | varchar(50) | 创建人编码 | 新增记录时由系统写入当前登录用户编码 |
| 12 | `update_serial` | varchar(50) | 更新人编码 | 修改记录时由系统写入当前登录用户编码 |
| 13 | `update_time` | bigint | 更新时间戳（毫秒） | 修改记录时由系统自动写入当前时间戳 |
| 14 | `tenant_id` | bigint | 租户 id，关联 `jsh_tenant` | 系统根据当前登录用户所属租户自动写入 |
| 15 | `delete_Flag` | varchar(1) | 删除标记：0未删除，1删除（逻辑删除） | 删除 SKU 时由系统置为 '1'，不物理删除 |

### 索引
- `PRIMARY KEY (id)`
- `material_id` —— 关联商品主表
- `tenant_id` —— 租户过滤
- `uk_material_extend_sku_code (sku_code)` —— SKU 码唯一约束
- `idx_material_extend_material_status (material_id, delete_Flag)` —— 按商品+删除标记查询

---

## 四、关键字段生成规则

### 1. SPU 商品码（`jsh_material.code`）
```
SP + 2位类型码 + 系统短码
示例：SP02-CNG2T2005
```
- 类型码取 `jsh_material_category.code`（类别类型码），不足两位前补 0
- 原款号来源字段 `jsh_material.model` 已删除，新增商品时由系统生成短码作为第三段
- 生成后保持稳定，不因名称、分类、规格调整而改写

### 2. SKU 码（`jsh_material_extend.sku_code`）
```
SPU码 + 各规格属性值编码（按品类规格顺序拼接）
示例：SP02-CNG2T2005-01-04-08
```
- 属性值编码取自 `jsh_material_attr_value.value_code`（纯数字序号，无前缀）
- 一个规格组合对应一个稳定 SKU 码
- 展示名称修改不改变已有 SKU 码

---

## 五、字段状态说明

### 已删除字段（第一批：`material_remove_fields_upgrade.sql`）
升级脚本执行前备份至 `jsh_material_removed_cols_bak`：
- `unit_id`（单位关联已移至主表 `jsh_material.unit`）
- `color`（颜色已移至 SKU 的 `spec_values`）
- `attribute`（多属性已移至 SKU 的 `spec_values`）
- `enabled`（启用标记调整）
- `enable_batch_number`（批号开关调整）
- `position`（仓位货架调整）

### 已删除字段（第二批：`material_remove_fields_upgrade_v2.sql`）
本次删除 8 个字段，执行前备份至 `jsh_material_removed_cols_bak2`，脚本按列存在性判断，可重复执行：

| 字段名 | 原类型 | 原作用 | 删除原因 / 数据去向 |
|--------|--------|--------|----------------------|
| `model` | varchar(100) | 型号 / 款号 | 不再作为 SPU 码款号来源，SPU 码第三段改由系统短码生成 |
| `standard` | varchar(100) | 规格描述 | 规格描述由 SKU 的 `spec_values` 表达，无替代字段 |
| `color` | varchar(50) | 颜色 | 颜色已迁移至 SKU 的 `spec_values`（第一批遗留，本次兜底删除） |
| `img_name` | varchar(1000) | 商品图片名称 | 图片改由 SKU 表 `img_url` 维护 |
| `unit_id` | bigint | 关联 `jsh_unit.id` | 单位改由 `jsh_material.unit` 维护（第一批遗留，本次兜底删除） |
| `expiry_num` | int | 保质期天数 | 与 SKU 体系无关联，业务不再使用，无替代字段 |
| `enabled` | bit(1) | 启用状态 | 启用控制改由其它状态字段/逻辑维护（第一批遗留，本次兜底删除） |
| `attribute` | varchar(1000) | 多属性信息 | 已迁移至 SKU 的 `spec_values`（第一批遗留，本次兜底删除） |

> 索引 `UnitId (unit_id)` 随 `unit_id` 列一并删除。

### 已删除字段（第三批：`material_extend_remove_fields_upgrade.sql`）
本次删除 SKU 表 `jsh_material_extend` 的 3 个字段，执行前备份至 `jsh_material_extend_removed_cols_bak`，脚本按列存在性判断，可重复执行：

| 字段名 | 原类型 | 原作用 | 删除原因 / 数据去向 |
|--------|--------|--------|----------------------|
| `bar_code` | varchar(50) | 商品条码 | 条码由主表 `jsh_material.bar_code` 统一维护；索引 `bar_code` 随列一并删除 |
| `commodity_unit` | varchar(50) | 商品单位 | 单位由主表 `jsh_material.unit` 统一维护 |
| `sku` | varchar(50) | 多属性文本描述（如「橙色,M」） | 规格描述由 `spec_values`（JSON）表达 |

> 删除前须先移除 `jsh_material_extend` 上的索引 `bar_code`，脚本已自动处理。
> 注意：`commodity_unit` 为多单位 SKU 记录的单位来源，删除后单位信息仅保留在主表，历史多单位数据请从备份表查询。

### 保留兼容字段
- `jsh_material.bar_code`：主表统一条码，保留使用

---

## 六、代码待同步清单

> 当前状态：**仅提供数据库升级脚本，代码尚未同步**。在执行任何删字段脚本前，必须先完成本章改造，否则报 `Unknown column`。

### 1. 字段替换规则

| 原引用 | 替换为 | 说明 |
|--------|--------|------|
| `me.bar_code` / `jme.bar_code` | `m.bar_code` / `jm.bar_code` | 主表条码已由默认 SKU 回填，可直接替代 |
| `me.commodity_unit` / `jme.commodity_unit` | `m.unit` / `jm.unit` | 统一取主表单位字符串，不依赖 `jsh_unit` 关联 |
| `me.sku` / `jme.sku` | `me.spec_values`（展示可配合 `me.sku_code`） | 规格描述改由 JSON 存储 |
| `me.bar_code is null` 类判空 | 改为 `m.bar_code` 判空 | 语义保持一致 |
| `ifnull(me.bar_code, m.bar_code)` | `m.bar_code` | 已无第二来源，简化 |
| `group by me.bar_code, me.commodity_unit` | `group by m.bar_code, m.unit` | 多单位归并口径改为主表单位 |

### 2. 待改造文件

**Mapper XML**

| 文件 | 涉及内容 |
|------|----------|
| [MaterialExtendMapper.xml](file:///D:/erp/jshERP-boot/src/main/resources/mapper_xml/MaterialExtendMapper.xml) | `resultMap`、`Base_Column_List`、`insert`、`insertSelective`、`updateByExample*`、`updateByPrimaryKey*` 全部需去掉 3 列 |
| [MaterialExtendMapperEx.xml](file:///D:/erp/jshERP-boot/src/main/resources/mapper_xml/MaterialExtendMapperEx.xml) | `getDetailList` 查询列与 `ResultMapList` 映射 |
| [MaterialMapperEx.xml](file:///D:/erp/jshERP-boot/src/main/resources/mapper_xml/MaterialMapperEx.xml) | 商品列表、SKU 列表、条码检索、库存列表等查询（`me.bar_code` / `me.commodity_unit` / `me.sku`） |
| [DepotItemMapperEx.xml](file:///D:/erp/jshERP-boot/src/main/resources/mapper_xml/DepotItemMapperEx.xml) | 单据明细关联 SKU 的条码与单位字段 |

**Java**

| 文件 | 涉及内容 |
|------|----------|
| [MaterialExtend.java](file:///D:/erp/jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/MaterialExtend.java#L66-L92) | 删除 `barCode` / `commodityUnit` / `sku` 属性及 getter/setter |
| [MaterialExtendExample.java](file:///D:/erp/jshERP-boot/src/main/java/com/jsh/erp/datasource/entities/MaterialExtendExample.java) | MyBatis 生成器产物，需删除 3 列的 Criteria 条件与 Criterion |
| [MaterialExtendService.java](file:///D:/erp/jshERP-boot/src/main/java/com/jsh/erp/service/MaterialExtendService.java#L150-L220) | 新增/编辑 SKU 时对 3 个字段的赋值、重复规格校验 |
| [MaterialService.java](file:///D:/erp/jshERP-boot/src/main/java/com/jsh/erp/service/MaterialService.java) | 商品保存、条码校验、库存判断、导出列 |
| [MaterialExtendController.java](file:///D:/erp/jshERP-boot/src/main/java/com/jsh/erp/controller/MaterialExtendController.java#L130-L140) | 接口返回的 `barCode` / `commodityUnit` / `sku` 字段 |
| [DepotItemService.java](file:///D:/erp/jshERP-boot/src/main/java/com/jsh/erp/service/DepotItemService.java) | 单据明细的条码、单位取值 |

**前端（`jshERP-web`）**

| 文件 | 涉及内容 |
|------|----------|
| `src/views/bill/modules/RetailOutModal.vue` | 零售出库 SKU 条码/单位展示 |
| `src/views/bill/mixins/BillModalMixin.js` | 单据明细字段映射 |
| `src/views/bill/mixins/DataTableDetailMixin.js` | 明细表格列 |
| `src/config/tableSceneConfig.js` | 列配置中心字段定义 |

### 3. 注意事项

1. **多单位价格会失去 SKU 级归属**：`purchase_decimal` 等价格仍按 SKU 存储，但单位只保留在主表，历史多单位数据须从 `jsh_material_extend_removed_cols_bak` 查询。
2. **`jsh_depot_item.sku` 是单据快照字段，不属于本次删除范围**，`DepotItem.getSku()` 相关逻辑保持不变。
3. **主表 8 个字段的引用仍未同步**（`MaterialMapperEx.xml` 等 3 个文件共 213 处），建议与本次改造合并成一次完整迁移，避免反复回归。
