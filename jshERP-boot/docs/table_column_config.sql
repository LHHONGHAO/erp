-- ----------------------------
-- 表格列显示配置表（全局共享）
-- 用于统一管理各表格的默认显示列、顺序与列标题覆盖
-- ----------------------------
DROP TABLE IF EXISTS `jsh_table_column_config`;
CREATE TABLE `jsh_table_column_config`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `table_key` varchar(100) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '表格场景标识，如 material_select',
  `table_names` varchar(500) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT NULL COMMENT '关联数据库表，逗号分隔',
  `config_json` text CHARACTER SET utf8 COLLATE utf8_general_ci NULL COMMENT '列配置JSON，格式 {"columns":["name"],"titles":{"sku":"规格"}}',
  `tenant_id` bigint(0) NULL DEFAULT NULL COMMENT '租户id，null表示全局共享',
  `delete_flag` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '0' COMMENT '删除标记，0未删除，1删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_table_key`(`table_key`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8 COLLATE = utf8_general_ci COMMENT = '表格列显示配置表' ROW_FORMAT = Dynamic;
