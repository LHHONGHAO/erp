package com.jsh.erp.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.utils.BaseResponseInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 表格列显示配置接口，用于统一管理各表格的默认显示列。
 * 配置为全局共享，存储在 jsh_table_column_config 表。
 */
@RestController
@RequestMapping(value = "/tableColumnConfig")
@Api(tags = {"表格列显示配置"})
public class TableColumnConfigController {
    private Logger logger = LoggerFactory.getLogger(TableColumnConfigController.class);

    @Resource
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    @ApiOperation(value = "获取所有表格列显示配置")
    public BaseResponseInfo list() {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String sql = "SELECT id, table_key, table_names, config_json, tenant_id " +
                         "FROM jsh_table_column_config WHERE delete_flag = '0' ORDER BY id";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", row.get("id"));
                item.put("tableKey", row.get("table_key"));
                item.put("tableNames", row.get("table_names"));
                item.put("config", parseConfig(String.valueOf(row.get("config_json"))));
                item.put("tenantId", row.get("tenant_id"));
                result.add(item);
            }
            info.code = 200;
            info.data = result;
        } catch (Exception e) {
            logger.error("获取表格列配置失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @GetMapping("/{tableKey}")
    @ApiOperation(value = "根据表格场景标识获取列显示配置")
    public BaseResponseInfo getByKey(@PathVariable String tableKey) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String sql = "SELECT id, table_key, table_names, config_json, tenant_id " +
                         "FROM jsh_table_column_config WHERE table_key = ? AND delete_flag = '0'";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tableKey);
            if (rows.isEmpty()) {
                info.code = 200;
                info.data = null;
            } else {
                Map<String, Object> row = rows.get(0);
                Map<String, Object> item = new HashMap<>();
                item.put("id", row.get("id"));
                item.put("tableKey", row.get("table_key"));
                item.put("tableNames", row.get("table_names"));
                item.put("config", parseConfig(String.valueOf(row.get("config_json"))));
                item.put("tenantId", row.get("tenant_id"));
                info.code = 200;
                info.data = item;
            }
        } catch (Exception e) {
            logger.error("获取表格列配置失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @PostMapping("/save")
    @ApiOperation(value = "保存表格列显示配置（存在则更新）")
    public BaseResponseInfo save(@RequestBody JSONObject obj) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String tableKey = obj.getString("tableKey");
            String tableNames = obj.getString("tableNames");
            Object config = obj.get("config");
            if (tableKey == null || tableKey.trim().isEmpty()) {
                info.code = 500;
                info.data = "tableKey 不能为空";
                return info;
            }
            String configJson = config == null ? null : JSON.toJSONString(config);
            // upsert：利用 uk_table_key 唯一索引
            String sql = "INSERT INTO jsh_table_column_config " +
                         "(table_key, table_names, config_json, tenant_id, delete_flag) " +
                         "VALUES (?, ?, ?, ?, '0') " +
                         "ON DUPLICATE KEY UPDATE " +
                         "table_names = VALUES(table_names), " +
                         "config_json = VALUES(config_json), " +
                         "tenant_id = VALUES(tenant_id), " +
                         "delete_flag = '0'";
            jdbcTemplate.update(sql, tableKey.trim(), tableNames, configJson, obj.get("tenantId"));
            info.code = 200;
            info.data = "保存成功";
        } catch (Exception e) {
            logger.error("保存表格列配置失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @DeleteMapping("/{tableKey}")
    @ApiOperation(value = "删除表格列显示配置")
    public BaseResponseInfo delete(@PathVariable String tableKey) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String sql = "UPDATE jsh_table_column_config SET delete_flag = '1' WHERE table_key = ?";
            jdbcTemplate.update(sql, tableKey);
            info.code = 200;
            info.data = "删除成功";
        } catch (Exception e) {
            logger.error("删除表格列配置失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    /** 将 config_json 字符串解析为 JSON 对象，兼容 null 与非法值 */
    private Object parseConfig(String configJson) {
        if (configJson == null || configJson.isEmpty()) {
            return null;
        }
        try {
            return JSON.parse(configJson);
        } catch (Exception e) {
            return null;
        }
    }
}
