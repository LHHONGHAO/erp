package com.jsh.erp.service;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.GoodsUsageDaily;
import com.jsh.erp.datasource.entities.User;
import com.jsh.erp.datasource.mappers.GoodsUsageDailyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品最近常选 Service
 * 数据按店铺（租户）保存，店铺内所有用户共享；跨店铺完全隔离。
 */
@Service
public class GoodsUsageService {

    private Logger logger = LoggerFactory.getLogger(GoodsUsageService.class);

    /** 统计窗口：近 7 天（含当天） */
    private static final int RECENT_DAYS = 7;

    /** 数据保留天数 */
    private static final int KEEP_DAYS = 90;

    @Resource
    private GoodsUsageDailyMapper goodsUsageDailyMapper;

    @Resource
    private UserService userService;

    @Resource
    private DataSource dataSource;

    /**
     * 启动时自愈表结构，避免新库缺表导致写入异常。
     */
    @PostConstruct
    public void initSchema() {
        String createSql = "CREATE TABLE IF NOT EXISTS `jsh_goods_usage_daily` (" +
                "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "`tenant_id` bigint NOT NULL COMMENT '店铺/租户ID'," +
                "`sku_id` bigint NOT NULL COMMENT 'SKU主键'," +
                "`material_id` bigint DEFAULT NULL COMMENT '商品ID'," +
                "`stat_date` date NOT NULL COMMENT '统计日期'," +
                "`pick_count` int NOT NULL DEFAULT 0 COMMENT '当日选择次数'," +
                "`last_pick_time` datetime DEFAULT NULL COMMENT '当日最后一次选择时间'," +
                "`last_user_id` bigint DEFAULT NULL COMMENT '最后选择人'," +
                "`name` varchar(255) DEFAULT NULL COMMENT '商品名称快照'," +
                "`spec` varchar(500) DEFAULT NULL COMMENT '规格文本快照'," +
                "`snapshot` text COMMENT '商品快照JSON'," +
                "`create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                "`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                "PRIMARY KEY (`id`)," +
                "UNIQUE KEY `uk_tenant_sku_date` (`tenant_id`,`sku_id`,`stat_date`)," +
                "KEY `idx_tenant_date` (`tenant_id`,`stat_date`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品最近常选（店铺内共享）'";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createSql);
        } catch (Exception e) {
            logger.error("初始化商品最近常选表失败: " + e.getMessage(), e);
            return;
        }
        ensureColumn("name", "ALTER TABLE `jsh_goods_usage_daily` ADD COLUMN `name` varchar(255) DEFAULT NULL COMMENT '商品名称快照'");
        ensureColumn("spec", "ALTER TABLE `jsh_goods_usage_daily` ADD COLUMN `spec` varchar(500) DEFAULT NULL COMMENT '规格文本快照'");
        ensureColumn("snapshot", "ALTER TABLE `jsh_goods_usage_daily` ADD COLUMN `snapshot` text COMMENT '商品快照JSON'");
        ensureColumn("last_user_id", "ALTER TABLE `jsh_goods_usage_daily` ADD COLUMN `last_user_id` bigint DEFAULT NULL COMMENT '最后选择人'");
        // 启动时顺带清理过期记录，避免表无限增长
        try {
            int cleared = goodsUsageDailyMapper.deleteExpired(KEEP_DAYS);
            if (cleared > 0) {
                logger.info("商品最近常选清理过期记录: " + cleared + " 行");
            }
        } catch (Exception e) {
            logger.warn("商品最近常选清理过期记录失败(忽略): " + e.getMessage());
        }
    }

    private void ensureColumn(String column, String alterSql) {
        if (hasColumn(column)) return;
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
            logger.info("jsh_goods_usage_daily 已补充列: " + column);
        } catch (Exception e) {
            logger.warn("补充 jsh_goods_usage_daily." + column + " 失败(忽略): " + e.getMessage());
        }
    }

    private boolean hasColumn(String column) {
        String sql = "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = 'jsh_goods_usage_daily' AND COLUMN_NAME = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.warn("检查 jsh_goods_usage_daily 列失败: " + column + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * 记录一次选择：同店铺同 SKU 同一天累加次数，并刷新商品快照。
     * SKU 不属于当前店铺时静默忽略，避免越权写入。
     */
    public void record(JSONObject obj) throws Exception {
        if (obj == null) return;
        Long skuId = obj.getLong("skuId");
        if (skuId == null) return;
        User user = userService.getCurrentUser();
        if (user == null || user.getTenantId() == null) return;
        Long tenantId = user.getTenantId();
        if (goodsUsageDailyMapper.countSkuInTenant(tenantId, skuId) == 0) return;
        GoodsUsageDaily row = new GoodsUsageDaily();
        row.setTenantId(tenantId);
        row.setSkuId(skuId);
        row.setMaterialId(obj.getLong("materialId"));
        row.setLastUserId(user.getId());
        row.setName(obj.getString("name"));
        row.setSpec(obj.getString("spec"));
        row.setSnapshot(obj.getString("snapshot"));
        goodsUsageDailyMapper.upsert(row);
    }

    /**
     * 近 7 天最近常选：次数优先、最近时间兜底；返回商品快照供前端直接渲染。
     */
    public List<Map<String, Object>> recent(Integer limit) throws Exception {
        User user = userService.getCurrentUser();
        List<Map<String, Object>> result = new ArrayList<>();
        if (user == null || user.getTenantId() == null) return result;
        Long tenantId = user.getTenantId();
        int size = limit == null || limit <= 0 ? 20 : Math.min(limit, 50);
        List<Map<String, Object>> stats = goodsUsageDailyMapper.selectRecentStat(tenantId, RECENT_DAYS, size);
        if (stats == null || stats.isEmpty()) return result;
        List<Long> skuIds = new ArrayList<>();
        for (Map<String, Object> stat : stats) {
            Object id = stat.get("skuId");
            if (id instanceof Number) skuIds.add(((Number) id).longValue());
        }
        if (skuIds.isEmpty()) return result;
        // 同一 SKU 可能跨多天，取最近一次的快照
        Map<Long, Map<String, Object>> latest = new HashMap<>();
        List<Map<String, Object>> snapshots = goodsUsageDailyMapper.selectLatestSnapshots(tenantId, skuIds);
        if (snapshots != null) {
            for (Map<String, Object> snapshot : snapshots) {
                Object id = snapshot.get("skuId");
                if (!(id instanceof Number)) continue;
                Long skuId = ((Number) id).longValue();
                if (!latest.containsKey(skuId)) latest.put(skuId, snapshot);
            }
        }
        for (Map<String, Object> stat : stats) {
            Object id = stat.get("skuId");
            if (!(id instanceof Number)) continue;
            Long skuId = ((Number) id).longValue();
            Map<String, Object> item = new HashMap<>();
            item.put("skuId", skuId);
            Object count = stat.get("pickCount");
            item.put("count", count instanceof Number ? ((Number) count).intValue() : 0);
            item.put("lastPickTime", stat.get("lastPickTime"));
            Map<String, Object> snapshot = latest.get(skuId);
            if (snapshot != null) {
                item.put("name", snapshot.get("name"));
                item.put("spec", snapshot.get("spec"));
                item.put("snapshot", snapshot.get("snapshot"));
            }
            result.add(item);
        }
        return result;
    }

    /** 清空当前店铺的最近常选（仅管理员，权限在 Controller 校验） */
    public int clear() throws Exception {
        User user = userService.getCurrentUser();
        if (user == null || user.getTenantId() == null) return 0;
        return goodsUsageDailyMapper.deleteByTenant(user.getTenantId());
    }
}
