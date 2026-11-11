package com.jsh.erp.label;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据源注册中心：收集所有业务数据适配器，按 provider 标识查找
 *
 * 同时支持「业务数据表」模式：按业务类型把相关数据库表暴露为可选数据源，
 * 选择某张表后自动加载该表全部字段，供标签编辑器点击绑定。
 */
@Component
public class LabelProviderRegistry {

    private static final Logger logger = LoggerFactory.getLogger(LabelProviderRegistry.class);

    @Autowired(required = false)
    private List<LabelDataProvider> providerList;

    @Resource
    private DataSource dataSource;

    private Map<String, LabelDataProvider> providerMap;

    /**
     * 业务类型 → 相关数据表清单
     * key:   业务标识（前端 open 时传入）
     * value: 表清单（表名, 显示名）
     */
    private static final Map<String, List<TableMeta>> BUSINESS_TABLES = new LinkedHashMap<>();

    /** 部分表不能直接按 id 查询，需要业务关联 SQL（key: 表名, value: 以单据 id 为参数的查询） */
    private static final Map<String, String> TABLE_LOAD_SQL = new LinkedHashMap<>();

    static {
        // 商品信息业务：商品主表 + SKU 扩展表
        BUSINESS_TABLES.put("product", Arrays.asList(
                new TableMeta("jsh_material", "商品信息表"),
                new TableMeta("sku", "SKU表")
        ));
        // 发货单业务：销售出库主表 + 明细表 + 会员信息表
        BUSINESS_TABLES.put("delivery", Arrays.asList(
                new TableMeta("jsh_depot_head", "销售出库主表"),
                new TableMeta("jsh_depot_item", "销售出库明细表"),
                new TableMeta("jsh_supplier", "会员信息表")
        ));
        // 会员业务：会员信息表（按会员 id 直接取数）
        BUSINESS_TABLES.put("member", Arrays.asList(
                new TableMeta("jsh_supplier", "会员信息表")
        ));
        // 会员信息不在单据上直接存 id，需要通过单据的 organ_id 关联
        // 旧调用不带业务标识时仍按表名匹配，保持发货单原有取数行为
        String deliverySupplierSql = "SELECT s.* FROM jsh_supplier s INNER JOIN jsh_depot_head h " +
                "ON h.organ_id = s.id WHERE h.id = ? LIMIT 1";
        TABLE_LOAD_SQL.put("jsh_supplier", deliverySupplierSql);
        TABLE_LOAD_SQL.put("delivery:jsh_supplier", deliverySupplierSql);
    }

    private void init() {
        if (providerMap != null) {
            return;
        }
        providerMap = new LinkedHashMap<>();
        if (providerList != null) {
            for (LabelDataProvider provider : providerList) {
                providerMap.put(provider.getProvider(), provider);
            }
        }
    }

    public LabelDataProvider get(String provider) {
        init();
        return providerMap.get(provider);
    }

    public List<LabelDataProvider> list() {
        init();
        return new ArrayList<>(providerMap.values());
    }

    /** 数据源列表（含字段元数据），供编辑器下拉与字段绑定使用 */
    public JSONArray describe() {
        return describe(null);
    }

    /**
     * 数据源列表：先返回已注册的适配器，再按业务类型追加相关数据表
     *
     * @param businessType 业务标识（如 product），为空时只返回适配器
     */
    public JSONArray describe(String businessType) {
        JSONArray array = new JSONArray();
        for (LabelDataProvider provider : list()) {
            try {
                JSONObject item = new JSONObject();
                item.put("provider", provider.getProvider());
                item.put("name", provider.getName());
                item.put("fields", provider.getFields());
                array.add(item);
            } catch (Exception e) {
                logger.error("describe provider 失败: " + provider.getProvider(), e);
            }
        }
        // 按业务类型追加数据表
        if (businessType != null && !businessType.trim().isEmpty()) {
            List<TableMeta> tables = BUSINESS_TABLES.get(businessType);
            if (tables != null) {
                for (TableMeta table : tables) {
                    try {
                        JSONObject item = new JSONObject();
                        item.put("provider", "table." + table.tableName);
                        item.put("name", table.displayName + "（" + table.tableName + "）");
                        item.put("fields", readTableFields(table.tableName));
                        array.add(item);
                    } catch (Exception e) {
                        logger.error("describe table 失败: " + table.tableName, e);
                    }
                }
            }
        }
        return array;
    }

    /** 从注册表或动态表中取字段元数据 */
    public JSONArray getFields(String provider) {
        init();
        LabelDataProvider p = providerMap.get(provider);
        if (p != null) {
            return p.getFields();
        }
        if (provider != null && provider.startsWith("table.")) {
            String tableName = provider.substring("table.".length());
            return readTableFields(tableName);
        }
        return new JSONArray();
    }

    /** 从注册表或动态表中取数据 */
    public JSONObject loadData(String provider, String documentId, String subId) {
        return loadData(provider, documentId, subId, null);
    }

    /**
     * 从注册表或动态表中取数据
     *
     * @param businessType 业务标识，同一张表在不同业务下的取数 SQL 可能不同（如 jsh_supplier）
     */
    public JSONObject loadData(String provider, String documentId, String subId, String businessType) {
        init();
        LabelDataProvider p = providerMap.get(provider);
        if (p != null) {
            return p.loadData(documentId, subId);
        }
        if (provider != null && provider.startsWith("table.")) {
            String tableName = provider.substring("table.".length());
            return readTableData(tableName, documentId, businessType);
        }
        return new JSONObject();
    }

    /** 读取指定表的全部字段（列名 + 类型 + 中文注释） */
    private JSONArray readTableFields(String tableName) {
        JSONArray fields = new JSONArray();
        if (tableName == null || tableName.trim().isEmpty()) {
            return fields;
        }
        try (Connection conn = dataSource.getConnection()) {
            Map<String, String> comments = readColumnComments(conn, tableName);
            DatabaseMetaData meta = conn.getMetaData();
            // 表名可能大小写敏感，先按原样查，再按大写查
            String catalog = conn.getCatalog();
            ResultSet rs = meta.getColumns(catalog, null, tableName, null);
            if (!rs.next()) {
                rs.close();
                rs = meta.getColumns(catalog, null, tableName.toUpperCase(Locale.ROOT), null);
            }
            if (rs.next()) {
                do {
                    String colName = rs.getString("COLUMN_NAME");
                    String colType = rs.getString("TYPE_NAME");
                    if (colName == null) continue;
                    JSONObject field = new JSONObject();
                    field.put("key", colName);
                    field.put("label", colName);
                    field.put("comment", comments.containsKey(colName.toUpperCase(Locale.ROOT))
                            ? comments.get(colName.toUpperCase(Locale.ROOT)) : "");
                    field.put("type", jdbcTypeToFieldType(colType));
                    field.put("required", false);
                    fields.add(field);
                } while (rs.next());
            }
            rs.close();
        } catch (Exception e) {
            logger.warn("读取表字段失败: " + tableName + " - " + e.getMessage());
        }
        return fields;
    }

    /** 读取表字段的中文注释：列名（大写）→ 注释 */
    private Map<String, String> readColumnComments(Connection conn, String tableName) {
        Map<String, String> comments = new LinkedHashMap<>();
        String sql = "SELECT COLUMN_NAME, COLUMN_COMMENT FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String column = rs.getString("COLUMN_NAME");
                    String comment = rs.getString("COLUMN_COMMENT");
                    if (column == null || comment == null || comment.trim().isEmpty()) continue;
                    comments.put(column.toUpperCase(Locale.ROOT), comment.trim());
                }
            }
        } catch (Exception e) {
            logger.warn("读取字段注释失败: " + tableName + " - " + e.getMessage());
        }
        return comments;
    }

    /** 按 id 查询单条数据，返回列名→值的映射 */
    private JSONObject readTableData(String tableName, String documentId, String businessType) {
        JSONObject result = new JSONObject();
        if (tableName == null || tableName.trim().isEmpty()
                || documentId == null || documentId.trim().isEmpty()) {
            return result;
        }
        // 按「业务:表名」优先匹配专用 SQL；未指定业务时保持原有表名匹配（兼容旧调用）
        String loadSql = null;
        if (businessType != null && !businessType.trim().isEmpty()) {
            loadSql = TABLE_LOAD_SQL.get(businessType.trim() + ":" + tableName);
        } else {
            loadSql = TABLE_LOAD_SQL.get(tableName);
        }
        String sql = loadSql != null ? loadSql
                : "SELECT * FROM " + tableName + " WHERE id = ? LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, documentId.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                        String key = rsmd.getColumnLabel(i);
                        Object val = rs.getObject(i);
                        result.put(key, val == null ? "" : String.valueOf(val));
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("读取表数据失败: " + tableName + " - " + e.getMessage());
        }
        return result;
    }

    private String jdbcTypeToFieldType(String jdbcType) {
        if (jdbcType == null) return "text";
        String t = jdbcType.toUpperCase(Locale.ROOT);
        if (t.contains("INT") || t.contains("DECIMAL") || t.contains("FLOAT")
                || t.contains("DOUBLE") || t.contains("NUMERIC")) {
            return "number";
        }
        if (t.contains("DATE") || t.contains("TIME")) {
            return "date";
        }
        return "text";
    }

    /** 数据表元信息 */
    private static class TableMeta {
        final String tableName;
        final String displayName;
        TableMeta(String tableName, String displayName) {
            this.tableName = tableName;
            this.displayName = displayName;
        }
    }
}
