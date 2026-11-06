package com.jsh.erp.label.provider;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.label.LabelDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 商品标签数据适配器
 *
 * documentId = 商品 ID（jsh_material.id）
 * subId      = SKU 扩展 ID（jsh_material_extend.id），为空时取默认单位/SKU
 *
 * 业务表结构变化只改本类，模板与渲染引擎不受影响。
 */
@Component
public class ProductLabelProvider implements LabelDataProvider {

    private Logger logger = LoggerFactory.getLogger(ProductLabelProvider.class);

    @Resource
    private DataSource dataSource;

    @Override
    public String getProvider() {
        return "product.label";
    }

    @Override
    public String getName() {
        return "商品标签";
    }

    @Override
    public JSONArray getFields() {
        JSONArray fields = new JSONArray();
        fields.add(field("materialName", "商品名称", "text", true));
        fields.add(field("spuCode", "SPU商品码", "text", false));
        fields.add(field("skuCode", "SKU编码", "text", false));
        fields.add(field("barcode", "商品条码", "text", false));
        fields.add(field("specDisplay", "规格", "text", false));
        fields.add(field("categoryName", "产品类型", "text", false));
        fields.add(field("brand", "品牌", "text", false));
        fields.add(field("mfrs", "制造商", "text", false));
        fields.add(field("unit", "单位", "text", false));
        fields.add(field("retailPrice", "零售价", "number", false));
        fields.add(field("purchasePrice", "进货价", "number", false));
        fields.add(field("marketPrice", "划线价", "number", false));
        fields.add(field("wholesalePrice", "销售价", "number", false));
        fields.add(field("weight", "基础重量", "number", false));
        fields.add(field("fabric", "面料", "text", false));
        fields.add(field("safetyCategory", "安全类别", "text", false));
        fields.add(field("executiveStandard", "执行标准", "text", false));
        fields.add(field("grade", "等级", "text", false));
        fields.add(field("inspection", "检验", "text", false));
        fields.add(field("filler", "填充物", "text", false));
        fields.add(field("position", "仓位货架", "text", false));
        fields.add(field("mnemonic", "助记码", "text", false));
        fields.add(field("remark", "备注", "text", false));
        return fields;
    }

    @Override
    public JSONObject loadData(String documentId, String subId) {
        JSONObject result = new JSONObject();
        if (documentId == null || documentId.trim().isEmpty()) {
            return result;
        }
        try (Connection conn = dataSource.getConnection()) {
            Map<String, String> material = queryOne(conn,
                    "SELECT * FROM jsh_material WHERE id = ? AND IFNULL(delete_flag,'0') <> '1'",
                    documentId.trim());
            if (material == null) {
                return result;
            }
            Map<String, String> extend = queryExtend(conn, documentId.trim(), subId);
            String categoryName = queryCategoryName(conn, material.get("category_id"));

            result.put("materialName", text(material, "name"));
            result.put("spuCode", text(material, "code"));
            result.put("categoryName", categoryName);
            result.put("brand", text(material, "brand"));
            result.put("mfrs", text(material, "mfrs"));
            result.put("weight", text(material, "weight"));
            result.put("fabric", text(material, "fabric"));
            result.put("safetyCategory", text(material, "safety_category"));
            result.put("executiveStandard", text(material, "executive_standard"));
            result.put("grade", text(material, "grade"));
            result.put("inspection", text(material, "inspection"));
            result.put("filler", text(material, "filler"));
            result.put("position", text(material, "position"));
            result.put("mnemonic", text(material, "mnemonic"));
            result.put("remark", text(material, "remark"));

            if (extend != null) {
                result.put("skuCode", text(extend, "sku_code"));
                result.put("barcode", text(extend, "bar_code"));
                result.put("unit", text(extend, "commodity_unit"));
                result.put("specDisplay", specDisplay(extend));
                result.put("retailPrice", text(extend, "commodity_decimal"));
                result.put("purchasePrice", text(extend, "purchase_decimal"));
                result.put("marketPrice", text(extend, "market_price"));
                result.put("wholesalePrice", text(extend, "wholesale_decimal"));
            } else {
                result.put("unit", text(material, "unit"));
            }
        } catch (Exception e) {
            logger.error("商品标签数据加载失败: " + e.getMessage(), e);
        }
        return result;
    }

    private JSONObject field(String key, String label, String type, boolean required) {
        JSONObject item = new JSONObject();
        item.put("key", key);
        item.put("label", label);
        item.put("type", type);
        item.put("required", required);
        return item;
    }

    /**
     * 取 SKU 扩展行：指定 subId 优先，否则取默认单位（default_flag=1）第一条
     */
    private Map<String, String> queryExtend(Connection conn, String materialId, String subId) throws Exception {
        if (subId != null && !subId.trim().isEmpty()) {
            return queryOne(conn,
                    "SELECT * FROM jsh_material_extend WHERE id = ? AND material_id = ? AND IFNULL(delete_Flag,'0') <> '1'",
                    subId.trim(), materialId);
        }
        return queryOne(conn,
                "SELECT * FROM jsh_material_extend WHERE material_id = ? AND IFNULL(delete_Flag,'0') <> '1' " +
                        "ORDER BY CASE WHEN default_flag = '1' THEN 0 ELSE 1 END, id ASC LIMIT 1",
                materialId);
    }

    private String queryCategoryName(Connection conn, String categoryId) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            return "";
        }
        try {
            Map<String, String> category = queryOne(conn,
                    "SELECT * FROM jsh_material_category WHERE id = ? AND IFNULL(delete_flag,'0') <> '1'",
                    categoryId.trim());
            return category == null ? "" : text(category, "name");
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, String> queryOne(Connection conn, String sql, String... args) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setString(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return readRow(rs);
            }
        }
    }

    /** 结果集转小写列名映射，缺少的列按空串处理，避免业务表加字段时适配器报错 */
    private Map<String, String> readRow(ResultSet rs) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String key = meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
            map.put(key, rs.getString(i));
        }
        return map;
    }

    private String text(Map<String, String> row, String key) {
        if (row == null) {
            return "";
        }
        String value = row.get(key);
        return value == null ? "" : value;
    }

    /**
     * 规格显示：优先逐条规格值（子规格名：子规格值），其次多属性文本
     */
    private String specDisplay(Map<String, String> extend) {
        String specValues = text(extend, "spec_values");
        if (!specValues.isEmpty()) {
            try {
                JSONArray array = JSON.parseArray(specValues);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < array.size(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String value = item.getString("valueText");
                    if (value == null || value.isEmpty()) {
                        value = item.getString("value");
                    }
                    if (value == null || value.isEmpty()) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(" / ");
                    }
                    sb.append(value);
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
            } catch (Exception e) {
                // JSON 异常时回退多属性文本
            }
        }
        return text(extend, "sku");
    }
}
