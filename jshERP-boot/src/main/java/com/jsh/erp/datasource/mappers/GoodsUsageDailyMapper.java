package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.GoodsUsageDaily;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 商品最近常选：SQL 内已显式带 tenant_id，租户插件对该 Mapper 跳过（见 TenantConfig）。
 */
public interface GoodsUsageDailyMapper {

    @Insert("INSERT INTO jsh_goods_usage_daily " +
            "(tenant_id, sku_id, material_id, stat_date, pick_count, last_pick_time, last_user_id, name, spec, snapshot) " +
            "VALUES (#{tenantId}, #{skuId}, #{materialId}, CURDATE(), 1, NOW(), #{lastUserId}, #{name}, #{spec}, #{snapshot}) " +
            "ON DUPLICATE KEY UPDATE pick_count = pick_count + 1, last_pick_time = NOW(), " +
            "last_user_id = VALUES(last_user_id), material_id = VALUES(material_id), " +
            "name = VALUES(name), spec = VALUES(spec), snapshot = VALUES(snapshot)")
    int upsert(GoodsUsageDaily record);

    // 只统计仍然有效的 SKU 与商品：商品被删除或 SKU 被删除后不再出现在最近常选
    @Select("SELECT g.sku_id AS skuId, SUM(g.pick_count) AS pickCount, MAX(g.last_pick_time) AS lastPickTime " +
            "FROM jsh_goods_usage_daily g " +
            "JOIN sku s ON s.id = g.sku_id AND s.deleted_at IS NULL " +
            "JOIN jsh_material m ON m.id = s.spu_id AND IFNULL(m.delete_flag,'0') != '1' " +
            "WHERE g.tenant_id = #{tenantId} AND g.stat_date >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY g.sku_id ORDER BY pickCount DESC, lastPickTime DESC LIMIT #{limit}")
    List<Map<String, Object>> selectRecentStat(@Param("tenantId") Long tenantId,
                                               @Param("days") Integer days,
                                               @Param("limit") Integer limit);

    @Select({"<script>",
            "SELECT g.sku_id AS skuId, g.name, g.spec, g.snapshot, g.last_pick_time AS lastPickTime ",
            "FROM jsh_goods_usage_daily g ",
            "JOIN sku s ON s.id = g.sku_id AND s.deleted_at IS NULL ",
            "JOIN jsh_material m ON m.id = s.spu_id AND IFNULL(m.delete_flag,'0') != '1' ",
            "WHERE g.tenant_id = #{tenantId} AND g.sku_id IN ",
            "<foreach collection='skuIds' item='skuId' open='(' separator=',' close=')'>#{skuId}</foreach>",
            " ORDER BY g.last_pick_time DESC",
            "</script>"})
    List<Map<String, Object>> selectLatestSnapshots(@Param("tenantId") Long tenantId,
                                                    @Param("skuIds") List<Long> skuIds);

    @Select("SELECT COUNT(*) FROM sku WHERE id = #{skuId} AND tenant_id = #{tenantId}")
    int countSkuInTenant(@Param("tenantId") Long tenantId, @Param("skuId") Long skuId);

    @Delete("DELETE FROM jsh_goods_usage_daily WHERE tenant_id = #{tenantId}")
    int deleteByTenant(@Param("tenantId") Long tenantId);

    @Delete("DELETE FROM jsh_goods_usage_daily WHERE stat_date < DATE_SUB(CURDATE(), INTERVAL #{days} DAY)")
    int deleteExpired(@Param("days") Integer days);
}
