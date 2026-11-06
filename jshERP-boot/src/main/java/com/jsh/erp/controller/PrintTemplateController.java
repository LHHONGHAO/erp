package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.utils.BaseResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 统一标签模板存储
 *
 * 一个模板一行：基本信息 + 完整 JSON 配置（数据源、字段绑定、元素布局）。
 * 所有业务共用同一套模板表，模板不保存业务数据、不写 SQL。
 */
@RestController
@RequestMapping(value = "/printTemplate")
public class PrintTemplateController {

    private Logger logger = LoggerFactory.getLogger(PrintTemplateController.class);

    @Resource
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `print_template` (" +
                "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "`name` varchar(100) DEFAULT NULL COMMENT '模板名称'," +
                "`business_type` varchar(50) DEFAULT NULL COMMENT '适用业务类型/数据源'," +
                "`width` decimal(10,2) DEFAULT NULL COMMENT '标签宽度(mm)'," +
                "`height` decimal(10,2) DEFAULT NULL COMMENT '标签高度(mm)'," +
                "`config` longtext COMMENT '模板完整JSON配置'," +
                "`version` int DEFAULT 1 COMMENT '模板版本'," +
                "`status` varchar(1) DEFAULT '1' COMMENT '状态 1启用 0停用'," +
                "`created_at` datetime DEFAULT NULL COMMENT '创建时间'," +
                "`updated_at` datetime DEFAULT NULL COMMENT '更新时间'," +
                "PRIMARY KEY (`id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='统一标签模板'";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("统一标签模板表 print_template 已就绪");
        } catch (Exception e) {
            logger.error("初始化统一标签模板表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 模板列表（含完整配置，模板数量少，便于编辑器直接恢复）
     */
    @GetMapping(value = "/list")
    public BaseResponseInfo list() {
        BaseResponseInfo res = new BaseResponseInfo();
        JSONArray array = new JSONArray();
        String sql = "SELECT id, name, business_type, width, height, config, version, status, updated_at " +
                "FROM print_template WHERE IFNULL(status,'1') <> '0' ORDER BY id DESC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                array.add(readRow(rs));
            }
            res.code = 200;
            res.data = array;
        } catch (Exception e) {
            logger.error("查询标签模板失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = array;
        }
        return res;
    }

    @GetMapping(value = "/detail")
    public BaseResponseInfo detail(@RequestParam("id") Long id) {
        BaseResponseInfo res = new BaseResponseInfo();
        String sql = "SELECT id, name, business_type, width, height, config, version, status, updated_at " +
                "FROM print_template WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    res.code = 200;
                    res.data = readRow(rs);
                } else {
                    res.code = 500;
                    res.data = "模板不存在";
                }
            }
        } catch (Exception e) {
            logger.error("查询标签模板详情失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "查询失败";
        }
        return res;
    }

    /**
     * 保存模板：有 id 更新（版本 +1），无 id 新增
     */
    @PostMapping(value = "/save")
    public BaseResponseInfo save(@RequestBody JSONObject obj) {
        BaseResponseInfo res = new BaseResponseInfo();
        String name = obj.getString("name");
        String businessType = obj.getString("businessType");
        String width = obj.getString("width");
        String height = obj.getString("height");
        String config = configText(obj.get("config"));
        Long id = obj.getLong("id");
        try (Connection conn = dataSource.getConnection()) {
            if (id != null && id > 0) {
                String sql = "UPDATE print_template SET name = ?, business_type = ?, width = ?, height = ?, " +
                        "config = ?, version = IFNULL(version, 1) + 1, status = '1', updated_at = NOW() WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, name);
                    ps.setString(2, businessType);
                    ps.setString(3, width);
                    ps.setString(4, height);
                    ps.setString(5, config);
                    ps.setLong(6, id);
                    ps.executeUpdate();
                }
                res.code = 200;
                res.data = id;
                return res;
            }
            String sql = "INSERT INTO print_template (name, business_type, width, height, config, version, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, 1, '1', NOW(), NOW())";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setString(2, businessType);
                ps.setString(3, width);
                ps.setString(4, height);
                ps.setString(5, config);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        res.data = rs.getLong(1);
                    }
                }
            }
            res.code = 200;
            return res;
        } catch (Exception e) {
            logger.error("保存标签模板失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "保存失败";
        }
        return res;
    }

    /** 删除模板（置为停用，避免影响历史打印记录） */
    @PostMapping(value = "/delete")
    public BaseResponseInfo delete(@RequestBody JSONObject obj) {
        BaseResponseInfo res = new BaseResponseInfo();
        Long id = obj.getLong("id");
        if (id == null) {
            res.code = 500;
            res.data = "缺少模板 id";
            return res;
        }
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE print_template SET status = '0', updated_at = NOW() WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
            res.code = 200;
            res.data = "ok";
        } catch (Exception e) {
            logger.error("删除标签模板失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "删除失败";
        }
        return res;
    }

    private JSONObject readRow(ResultSet rs) throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", rs.getLong("id"));
        item.put("name", rs.getString("name"));
        item.put("businessType", rs.getString("business_type"));
        item.put("width", rs.getBigDecimal("width"));
        item.put("height", rs.getBigDecimal("height"));
        item.put("version", rs.getInt("version"));
        item.put("status", rs.getString("status"));
        item.put("updatedAt", rs.getString("updated_at"));
        String config = rs.getString("config");
        if (config != null && !config.trim().isEmpty()) {
            try {
                item.put("config", JSONObject.parseObject(config));
            } catch (Exception e) {
                item.put("config", config);
            }
        }
        return item;
    }

    private String configText(Object config) {
        if (config == null) {
            return "{}";
        }
        if (config instanceof String) {
            return (String) config;
        }
        return JSONObject.toJSONString(config);
    }
}
