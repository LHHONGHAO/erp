package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.SkuSpec;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SkuSpecMapper {

    /**
     * 覆盖式保存：先清除该 SKU 的旧规格
     */
    int deleteBySkuId(@Param("skuId") Long skuId);

    int insert(SkuSpec record);

    List<SkuSpec> selectBySkuId(@Param("skuId") Long skuId);

    /**
     * 单规格精准统计（对应事实表查询）
     */
    List<SkuSpec> selectByNameValue(@Param("specName") String specName,
                                    @Param("specValue") String specValue,
                                    @Param("tenantId") Long tenantId);
}
