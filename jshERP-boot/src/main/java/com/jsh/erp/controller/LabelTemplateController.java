package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.utils.BaseResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * 标签模板云端存储。
 * 模板列表整体以 JSON 形式存为单行记录，所有登录设备（PC/手机）共享同一份数据；
 * 前端 localStorage 仅作离线缓存兜底。
 */
@RestController
@RequestMapping(value = "/labelTemplate")
public class LabelTemplateController {

    private Logger logger = LoggerFactory.getLogger(LabelTemplateController.class);

    @Resource
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `jsh_label_template_config` (" +
                "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "`content` longtext COMMENT '模板列表JSON'," +
                "`update_time` datetime DEFAULT NULL COMMENT '更新时间'," +
                "PRIMARY KEY (`id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='标签模板云端存储'";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("标签模板存储表 jsh_label_template_config 已就绪");
        } catch (Exception e) {
            logger.error("初始化标签模板存储表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取全部标签模板（JSON 字符串，空表示还没有模板）
     */
    @GetMapping(value = "/list")
    public BaseResponseInfo list() {
        BaseResponseInfo res = new BaseResponseInfo();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT content FROM jsh_label_template_config WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            String content = "";
            if (rs.next() && rs.getString(1) != null) {
                content = rs.getString(1);
            }
            res.code = 200;
            res.data = content;
        } catch (Exception e) {
            logger.error("查询标签模板失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "";
        }
        return res;
    }

    /**
     * 保存全部标签模板（整表覆盖）
     */
    @PostMapping(value = "/save")
    public BaseResponseInfo save(@RequestBody JSONObject obj) {
        BaseResponseInfo res = new BaseResponseInfo();
        try (Connection conn = dataSource.getConnection()) {
            String content = obj.getJSONArray("templates") == null
                    ? "[]" : obj.getJSONArray("templates").toJSONString();
            PreparedStatement update = conn.prepareStatement(
                    "UPDATE jsh_label_template_config SET content = ?, update_time = NOW() WHERE id = 1");
            update.setString(1, content);
            int updated = update.executeUpdate();
            update.close();
            if (updated == 0) {
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO jsh_label_template_config (id, content, update_time) VALUES (1, ?, NOW())");
                insert.setString(1, content);
                insert.executeUpdate();
                insert.close();
            }
            res.code = 200;
            res.data = "ok";
        } catch (Exception e) {
            logger.error("保存标签模板失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "保存失败";
        }
        return res;
    }
}
