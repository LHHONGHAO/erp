package com.jsh.erp.label.provider;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.label.LabelDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 销售出库数据适配器
 *
 * documentId = 单据主键（jsh_depot_head.id）
 * 明细返回 items 数组，用于标签模板的明细循环渲染
 */
@Component
public class SalesDeliveryProvider implements LabelDataProvider {

    private Logger logger = LoggerFactory.getLogger(SalesDeliveryProvider.class);

    @Resource
    private DataSource dataSource;

    @Override
    public String getProvider() {
        return "sales.delivery";
    }

    @Override
    public String getName() {
        return "销售出库";
    }

    @Override
    public JSONArray getFields() {
        JSONArray fields = new JSONArray();
        fields.add(field("orderNo", "单号", "text", true));
        fields.add(field("orderDate", "单据日期", "date", false));
        fields.add(field("salesMan", "销售员", "text", false));
        fields.add(field("remark", "备注", "text", false));
        fields.add(field("totalAmount", "合计金额", "number", false));
        fields.add(field("totalQuantity", "合计数量", "number", false));
        fields.add(field("items", "商品明细", "array", false));
        return fields;
    }

    @Override
    public JSONObject loadData(String documentId, String subId) {
        JSONObject result = new JSONObject();
        if (documentId == null || documentId.trim().isEmpty()) {
            return result;
        }
        try (Connection conn = dataSource.getConnection()) {
            Map<String, String> head = queryHead(conn, documentId.trim());
            if (head == null) {
                return result;
            }
            result.put("orderNo", text(head, "number"));
            result.put("orderDate", text(head, "oper_time"));
            result.put("salesMan", text(head, "sales_man"));
            result.put("remark", text(head, "remark"));

            JSONArray items = new JSONArray();
            BigDecimal totalQuantity = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            String sql = "SELECT di.*, m.name AS material_name, me.sku_name AS sku_text " +
                    "FROM jsh_depot_item di " +
                    "LEFT JOIN jsh_material m ON m.id = di.material_id " +
                    "LEFT JOIN sku me ON me.id = di.sku_id " +
                    "WHERE di.header_id = ? AND IFNULL(di.delete_flag,'0') <> '1' ORDER BY di.id ASC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, documentId.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> row = readRow(rs);
                        String quantity = first(row, "oper_number", "basic_number");
                        String amount = first(row, "tax_last_money", "all_price", "tax_money");
                        BigDecimal qty = decimal(quantity);
                        BigDecimal money = decimal(amount);
                        totalQuantity = totalQuantity.add(qty);
                        totalAmount = totalAmount.add(money);

                        JSONObject item = new JSONObject();
                        item.put("name", text(row, "material_name"));
                        item.put("spec", text(row, "sku_text"));
                        item.put("quantity", quantity);
                        item.put("price", qty.compareTo(BigDecimal.ZERO) == 0 ? ""
                                : money.divide(qty, 2, BigDecimal.ROUND_HALF_UP).toPlainString());
                        item.put("amount", money.toPlainString());
                        items.add(item);
                    }
                }
            }
            result.put("items", items);
            result.put("totalQuantity", totalQuantity.stripTrailingZeros().toPlainString());
            result.put("totalAmount", totalAmount.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString());
        } catch (Exception e) {
            logger.error("销售出库标签数据加载失败: " + e.getMessage(), e);
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

    private Map<String, String> queryHead(Connection conn, String id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM jsh_depot_head WHERE id = ? AND IFNULL(delete_flag,'0') <> '1'")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readRow(rs) : null;
            }
        }
    }

    private Map<String, String> readRow(ResultSet rs) throws Exception {
        Map<String, String> map = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            map.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getString(i));
        }
        return map;
    }

    private String text(Map<String, String> row, String key) {
        String value = row.get(key);
        return value == null ? "" : value;
    }

    /** 按顺序取第一个非空列，兼容不同版本业务表字段差异 */
    private String first(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private BigDecimal decimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
