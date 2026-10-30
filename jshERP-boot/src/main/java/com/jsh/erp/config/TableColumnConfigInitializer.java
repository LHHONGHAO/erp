package com.jsh.erp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

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
        } catch (Exception e) {
            logger.error("初始化表格列显示配置失败: " + e.getMessage(), e);
        }
    }
}
