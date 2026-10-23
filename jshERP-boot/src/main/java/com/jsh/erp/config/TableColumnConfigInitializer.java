package com.jsh.erp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 表格列显示配置表初始化器。
 * 启动时自动创建 jsh_table_column_config 表，保证配置中心功能开箱即用。
 */
@Component
public class TableColumnConfigInitializer implements ApplicationRunner {
    private Logger logger = LoggerFactory.getLogger(TableColumnConfigInitializer.class);

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String sql = "CREATE TABLE IF NOT EXISTS `jsh_table_column_config` (" +
                    "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                    "`table_key` varchar(100) NOT NULL COMMENT '表格场景标识，如 material_select'," +
                    "`table_names` varchar(500) DEFAULT NULL COMMENT '关联数据库表，逗号分隔'," +
                    "`config_json` text COMMENT '列配置JSON'," +
                    "`tenant_id` bigint DEFAULT NULL COMMENT '租户id，null表示全局共享'," +
                    "`delete_flag` varchar(1) DEFAULT '0' COMMENT '删除标记，0未删除，1删除'," +
                    "PRIMARY KEY (`id`)," +
                    "UNIQUE KEY `uk_table_key` (`table_key`)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='表格列显示配置表'";
            jdbcTemplate.execute(sql);
            logger.info("表格列显示配置表 jsh_table_column_config 已就绪");

            // 在「系统管理」菜单组下添加入口（幂等，已存在则跳过）
            String checkMenuSql = "SELECT COUNT(*) FROM jsh_function WHERE url = '/system/tableColumnConfig'";
            Integer menuCount = jdbcTemplate.queryForObject(checkMenuSql, Integer.class);
            if (menuCount == null || menuCount == 0) {
                String insertMenuSql = "INSERT INTO jsh_function " +
                        "(number, name, parent_number, url, component, state, sort, enabled, type, push_btn, icon, delete_flag) " +
                        "VALUES ('000114', '列显示配置', '0001', '/system/tableColumnConfig', " +
                        "'/system/TableColumnConfig', b'0', '0176', b'1', '电脑版', '1', 'profile', '0')";
                jdbcTemplate.update(insertMenuSql);
                logger.info("已添加「列显示配置」菜单入口");
            }

            // 自动为管理类角色分配该菜单权限（幂等），否则登录后看不到叶子菜单
            grantMenuToAdminRoles();
        } catch (Exception e) {
            logger.error("初始化表格列显示配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 为管理类角色（管理员、租户）的 RoleFunctions 追加本菜单权限。
     * 幂等：若已包含该菜单 id 则跳过。
     */
    private void grantMenuToAdminRoles() {
        try {
            Long menuId = jdbcTemplate.queryForObject(
                    "SELECT id FROM jsh_function WHERE url = '/system/tableColumnConfig'", Long.class);
            if (menuId == null) {
                return;
            }
            String marker = "[" + menuId + "]";
            List<Long> roleIds = jdbcTemplate.queryForList(
                    "SELECT id FROM jsh_role WHERE name IN ('管理员', '租户') AND ifnull(delete_flag,'0') != '1'", Long.class);
            for (Long roleId : roleIds) {
                List<String> values = jdbcTemplate.queryForList(
                        "SELECT value FROM jsh_user_business WHERE type = 'RoleFunctions' AND key_id = ? AND ifnull(delete_flag,'0') != '1'",
                        String.class, roleId.toString());
                for (String value : values) {
                    if (value == null || !value.contains(marker)) {
                        String newValue = (value == null ? "" : value) + marker;
                        jdbcTemplate.update(
                                "UPDATE jsh_user_business SET value = ? WHERE type = 'RoleFunctions' AND key_id = ?",
                                newValue, roleId.toString());
                        logger.info("已为角色(id=" + roleId + ")分配「列显示配置」菜单权限");
                    }
                }
            }
        } catch (Exception e) {
            logger.error("为管理类角色分配列显示配置权限失败: " + e.getMessage(), e);
        }
    }
}
