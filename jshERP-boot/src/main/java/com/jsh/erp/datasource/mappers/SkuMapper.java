package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.Sku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SkuMapper {

    int insert(Sku record);

    /**
     * 仅更新规格冗余字段与展示名（由 SkuSpecUtils 生成）
     */
    int updateSpecFields(Sku record);

    Sku selectByPrimaryKey(Long id);

    List<Sku> selectBySpuId(@Param("spuId") Long spuId, @Param("tenantId") Long tenantId);

    /**
     * 组合搜索：spec_summary LIKE '%颜色:红%尺码:L%'
     */
    List<Sku> searchBySummary(@Param("spuId") Long spuId,
                              @Param("summary") String summary,
                              @Param("tenantId") Long tenantId);

    /**
     * 软删除（禁止物理删除）
     */
    int softDelete(@Param("id") Long id);
}
