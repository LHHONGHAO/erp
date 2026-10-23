package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONArray;
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
 * 表格元数据接口，用于前端 DataTable 组件自动发现数据库表字段。
 * 支持逗号分隔的多表名（如 material,material_extend），结果去重后按主表字段优先排序。
 */
@RestController
@RequestMapping(value = "/tableMeta")
@Api(tags = {"表格元数据"})
public class TableMetaController {
    private Logger logger = LoggerFactory.getLogger(TableMetaController.class);

    @Resource
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/{tableName}")
    @ApiOperation(value = "获取表的字段元数据，支持逗号分隔多表名")
    public BaseResponseInfo getTableColumns(@PathVariable String tableName) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String[] tables = tableName.split(",");
            List<Map<String, String>> columns = new ArrayList<>();
            Set<String> seenColumns = new HashSet<>();

            String sql = "SELECT column_name, column_comment, data_type " +
                         "FROM information_schema.COLUMNS " +
                         "WHERE table_schema = 'jsh_erp' AND table_name = ? " +
                         "ORDER BY ordinal_position";

            for (String t : tables) {
                String name = t.trim();
                if (name.isEmpty()) continue;
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, name);
                for (Map<String, Object> row : rows) {
                    String colName = String.valueOf(row.get("column_name"));
                    // 转换为 camelCase，与 JSON 数据中的属性名一致
                    String camelName = toCamelCase(colName);
                    // 去重：以 camelCase 后的字段名为准（主表优先），
                    // 避免 delete_flag/delete_Flag 这类大小写差异在 camelCase 后产生重复字段
                    if (seenColumns.contains(camelName)) continue;
                    seenColumns.add(camelName);

                    Map<String, String> col = new HashMap<>();
                    col.put("columnName", camelName);
                    col.put("rawColumnName", String.valueOf(row.get("column_name")));
                    col.put("columnComment", row.get("column_comment") != null
                            ? String.valueOf(row.get("column_comment")) : camelName);
                    col.put("dataType", String.valueOf(row.get("data_type")));
                    columns.add(col);
                }
            }
            info.code = 200;
            info.data = columns;
        } catch (Exception e) {
            logger.error("获取表元数据失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @GetMapping("/listTables")
    @ApiOperation(value = "获取数据库所有基础表及注释（供配置中心动态选择数据表）")
    public BaseResponseInfo listTables() {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String sql = "SELECT table_name, table_comment FROM information_schema.TABLES " +
                         "WHERE table_schema = 'jsh_erp' AND table_type = 'BASE TABLE' " +
                         "ORDER BY table_name";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            List<Map<String, String>> result = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, String> item = new HashMap<>();
                item.put("table", String.valueOf(row.get("table_name")));
                item.put("comment", row.get("table_comment") == null ? "" : String.valueOf(row.get("table_comment")));
                result.add(item);
            }
            info.code = 200;
            info.data = result;
        } catch (Exception e) {
            logger.error("获取数据表列表失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @PostMapping("/createTable")
    @ApiOperation(value = "新建真实数据表（DDL，自动包含自增主键 id）")
    public BaseResponseInfo createTable(@RequestBody JSONObject obj) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String tableName = trim(obj.getString("tableName"));
            String tableComment = obj.getString("tableComment");
            if (!isValidIdentifier(tableName)) {
                info.code = 500;
                info.data = "表名不合法（仅允许字母、数字、下划线）";
                return info;
            }
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE table_schema = 'jsh_erp' AND table_name = ?",
                    Integer.class, tableName);
            if (cnt != null && cnt > 0) {
                info.code = 500;
                info.data = "数据表已存在";
                return info;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE `").append(tableName).append("` (");
            sb.append("`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'");
            JSONArray columns = obj.getJSONArray("columns");
            if (columns != null) {
                for (int i = 0; i < columns.size(); i++) {
                    JSONObject col = columns.getJSONObject(i);
                    String colName = trim(col.getString("columnName"));
                    String colComment = col.getString("columnComment");
                    if (!isValidIdentifier(colName)) {
                        info.code = 500;
                        info.data = "字段名不合法: " + colName;
                        return info;
                    }
                    sb.append(",`").append(colName).append("` ").append(resolveSqlType(col.getString("dataType")));
                    if (colComment != null && !colComment.trim().isEmpty()) {
                        sb.append(" COMMENT '").append(escape(colComment)).append("'");
                    }
                }
            }
            sb.append(",PRIMARY KEY (`id`)");
            sb.append(") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            if (tableComment != null && !tableComment.trim().isEmpty()) {
                sb.append(" COMMENT='").append(escape(tableComment)).append("'");
            }
            jdbcTemplate.execute(sb.toString());
            info.code = 200;
            info.data = "创建成功";
        } catch (Exception e) {
            logger.error("新建数据表失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @PostMapping("/addColumn")
    @ApiOperation(value = "给数据表添加字段（DDL）")
    public BaseResponseInfo addColumn(@RequestBody JSONObject obj) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String tableName = trim(obj.getString("tableName"));
            String columnName = trim(obj.getString("columnName"));
            String columnComment = obj.getString("columnComment");
            if (!isValidIdentifier(tableName) || !isValidIdentifier(columnName)) {
                info.code = 500;
                info.data = "表名或字段名不合法（仅允许字母、数字、下划线）";
                return info;
            }
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE table_schema = 'jsh_erp' AND table_name = ? AND column_name = ?",
                    Integer.class, tableName, columnName);
            if (cnt != null && cnt > 0) {
                info.code = 500;
                info.data = "字段已存在";
                return info;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("ALTER TABLE `").append(tableName).append("` ADD COLUMN `").append(columnName).append("` ")
              .append(resolveSqlType(obj.getString("dataType")));
            if (columnComment != null && !columnComment.trim().isEmpty()) {
                sb.append(" COMMENT '").append(escape(columnComment)).append("'");
            }
            jdbcTemplate.execute(sb.toString());
            info.code = 200;
            info.data = "添加成功";
        } catch (Exception e) {
            logger.error("添加字段失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    @PostMapping("/dropColumn")
    @ApiOperation(value = "删除数据表字段（DDL，禁止删除系统字段）")
    public BaseResponseInfo dropColumn(@RequestBody JSONObject obj) {
        BaseResponseInfo info = new BaseResponseInfo();
        try {
            String tableName = trim(obj.getString("tableName"));
            String columnName = trim(obj.getString("columnName"));
            if (!isValidIdentifier(tableName) || !isValidIdentifier(columnName)) {
                info.code = 500;
                info.data = "表名或字段名不合法（仅允许字母、数字、下划线）";
                return info;
            }
            if ("id".equalsIgnoreCase(columnName) || "delete_flag".equalsIgnoreCase(columnName)
                    || "tenant_id".equalsIgnoreCase(columnName)) {
                info.code = 500;
                info.data = "系统字段不允许删除";
                return info;
            }
            jdbcTemplate.execute("ALTER TABLE `" + tableName + "` DROP COLUMN `" + columnName + "`");
            info.code = 200;
            info.data = "删除成功";
        } catch (Exception e) {
            logger.error("删除字段失败: " + e.getMessage(), e);
            info.code = 500;
            info.data = e.getMessage();
        }
        return info;
    }

    /** 表名/字段名白名单：仅允许字母、数字、下划线，防止 SQL 注入 */
    private boolean isValidIdentifier(String s) {
        return s != null && s.matches("[A-Za-z0-9_]{1,64}");
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** 转义单引号，防止注释内容破坏 DDL */
    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    /** 将前端数据类型映射为 MySQL 列类型 */
    private String resolveSqlType(String type) {
        if (type == null) {
            return "varchar(255)";
        }
        switch (type) {
            case "int": return "int(11)";
            case "bigint": return "bigint";
            case "decimal": return "decimal(20,2)";
            case "double": return "double";
            case "datetime": return "datetime";
            case "date": return "date";
            case "text": return "text";
            default: return "varchar(255)";
        }
    }

    /** snake_case → camelCase */
    private String toCamelCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }
}
