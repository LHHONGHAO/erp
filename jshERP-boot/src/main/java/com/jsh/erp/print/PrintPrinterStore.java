package com.jsh.erp.print;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 打印机型号库
 *
 * 主机侧维护「品牌 + 型号 + 指令集 + 标签尺寸 + 份数 + 浓度/速度」，
 * 代理设置页从主机拉取型号供选择，打印时主机按所选型号的参数生成指令驱动本地打印机。
 */
@Component
public class PrintPrinterStore {

    private Logger logger = LoggerFactory.getLogger(PrintPrinterStore.class);

    @Resource
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `print_printer` (" +
                "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "`brand` varchar(64) DEFAULT NULL COMMENT '品牌'," +
                "`model` varchar(100) NOT NULL COMMENT '型号，与代理上报的型号一致'," +
                "`cmd_set` varchar(20) DEFAULT 'TSPL' COMMENT '指令集 TSPL/CPCL/DDPL/ESC-POS'," +
                "`width_mm` int DEFAULT 0 COMMENT '标签宽(mm)'," +
                "`height_mm` int DEFAULT 0 COMMENT '标签高(mm)'," +
                "`copies` int DEFAULT 1 COMMENT '默认份数'," +
                "`density` int DEFAULT 8 COMMENT '浓度 1-15，0 表示不指定'," +
                "`speed` int DEFAULT 3 COMMENT '速度 1-6，0 表示不指定'," +
                "`enabled` varchar(1) DEFAULT '1' COMMENT '状态 1启用 0停用'," +
                "`remark` varchar(255) DEFAULT NULL COMMENT '备注'," +
                "`created_at` datetime DEFAULT NULL COMMENT '创建时间'," +
                "`updated_at` datetime DEFAULT NULL COMMENT '更新时间'," +
                "PRIMARY KEY (`id`)," +
                "UNIQUE KEY `uk_model` (`model`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='打印机型号库'";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("打印机型号库 print_printer 已就绪");
        } catch (Exception e) {
            logger.error("初始化打印机型号库失败: " + e.getMessage(), e);
        }
    }

    public List<JSONObject> listAll() {
        List<JSONObject> list = new ArrayList<>();
        String sql = "SELECT * FROM print_printer ORDER BY brand ASC, model ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(read(rs));
            }
        } catch (Exception e) {
            logger.error("查询打印机型号库失败: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 代理侧可选型号：只返回启用项
     */
    public List<JSONObject> listEnabled() {
        List<JSONObject> list = new ArrayList<>();
        String sql = "SELECT * FROM print_printer WHERE enabled = '1' ORDER BY brand ASC, model ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(read(rs));
            }
        } catch (Exception e) {
            logger.error("查询打印机型号失败: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 按型号取驱动参数（型号为空或未配置时返回 null，调用方回退默认参数）
     */
    public JSONObject findByModel(String model) {
        if (model == null || model.trim().isEmpty()) {
            return null;
        }
        String sql = "SELECT * FROM print_printer WHERE model = ? AND enabled = '1' LIMIT 1";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, model.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return read(rs);
                }
            }
        } catch (Exception e) {
            logger.error("按型号查询打印机失败: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 新增或修改型号；型号唯一
     */
    public Long save(JSONObject body) throws Exception {
        Long id = body.getLong("id");
        String model = trim(body.getString("model"));
        if (model == null) {
            throw new IllegalArgumentException("打印机型号不能为空");
        }
        String brand = trim(body.getString("brand"));
        String cmdSet = trim(body.getString("cmdSet"));
        if (cmdSet == null) {
            cmdSet = "TSPL";
        }
        int widthMm = clamp(body.getIntValue("widthMm"), 0, 300);
        int heightMm = clamp(body.getIntValue("heightMm"), 0, 300);
        int copies = clamp(body.getIntValue("copies"), 1, 99);
        int density = clamp(body.getIntValue("density"), 0, 15);
        int speed = clamp(body.getIntValue("speed"), 0, 6);
        String enabled = "0".equals(body.getString("enabled")) ? "0" : "1";
        String remark = trim(body.getString("remark"));
        try (Connection conn = dataSource.getConnection()) {
            if (id != null && id > 0) {
                if (modelExists(conn, model, id)) {
                    throw new IllegalArgumentException("型号已存在：" + model);
                }
                String sql = "UPDATE print_printer SET brand = ?, model = ?, cmd_set = ?, width_mm = ?, height_mm = ?, " +
                        "copies = ?, density = ?, speed = ?, enabled = ?, remark = ?, updated_at = NOW() WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, brand);
                    ps.setString(2, model);
                    ps.setString(3, cmdSet);
                    ps.setInt(4, widthMm);
                    ps.setInt(5, heightMm);
                    ps.setInt(6, copies);
                    ps.setInt(7, density);
                    ps.setInt(8, speed);
                    ps.setString(9, enabled);
                    ps.setString(10, remark);
                    ps.setLong(11, id);
                    ps.executeUpdate();
                }
                return id;
            }
            if (modelExists(conn, model, null)) {
                throw new IllegalArgumentException("型号已存在：" + model);
            }
            String sql = "INSERT INTO print_printer (brand, model, cmd_set, width_mm, height_mm, copies, density, speed, " +
                    "enabled, remark, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, brand);
                ps.setString(2, model);
                ps.setString(3, cmdSet);
                ps.setInt(4, widthMm);
                ps.setInt(5, heightMm);
                ps.setInt(6, copies);
                ps.setInt(7, density);
                ps.setInt(8, speed);
                ps.setString(9, enabled);
                ps.setString(10, remark);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        }
        return null;
    }

    public boolean delete(Long id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM print_printer WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("删除打印机型号失败: " + e.getMessage(), e);
            return false;
        }
    }

    private boolean modelExists(Connection conn, String model, Long excludeId) throws Exception {
        String sql = "SELECT id FROM print_printer WHERE model = ?" + (excludeId != null ? " AND id <> ?" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, model);
            if (excludeId != null) {
                ps.setLong(2, excludeId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private JSONObject read(ResultSet rs) throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", rs.getLong("id"));
        item.put("brand", rs.getString("brand"));
        item.put("model", rs.getString("model"));
        item.put("cmdSet", rs.getString("cmd_set"));
        item.put("widthMm", rs.getInt("width_mm"));
        item.put("heightMm", rs.getInt("height_mm"));
        item.put("copies", rs.getInt("copies"));
        item.put("density", rs.getInt("density"));
        item.put("speed", rs.getInt("speed"));
        item.put("enabled", rs.getString("enabled"));
        item.put("remark", rs.getString("remark"));
        item.put("createdAt", rs.getString("created_at"));
        item.put("updatedAt", rs.getString("updated_at"));
        return item;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
