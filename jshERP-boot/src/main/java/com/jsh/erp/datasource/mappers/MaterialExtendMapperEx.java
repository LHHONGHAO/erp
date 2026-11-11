package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.MaterialExtend;
import com.jsh.erp.datasource.vo.MaterialExtendVo4List;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品 SKU 扩展查询（底层唯一存储：sku / sku_spec）
 * 对外仍以 MaterialExtend 视图对象暴露，避免上层调用面变化。
 */
public interface MaterialExtendMapperEx {

    int batchDeleteMaterialExtendByIds(@Param("ids") String ids[]);

    List<MaterialExtendVo4List> getDetailList(
            @Param("materialId") Long materialId);

    Long getMaxTimeByTenantAndTime(
            @Param("tenantId") Long tenantId,
            @Param("lastTime") Long lastTime,
            @Param("syncNum") Long syncNum);

    List<MaterialExtend> getListByMId(@Param("ids") Long ids[]);

    int batchDeleteMaterialExtendByMIds(@Param("ids") String ids[]);

    int specialUpdatePrice(MaterialExtend record);

    List<MaterialExtend> getBasicInfoByMid(
            @Param("materialId") Long materialId);

    List<MaterialExtend> selectListBySpuId(@Param("materialId") Long materialId);

    List<MaterialExtend> selectListBySpuIdAndDefaultFlag(@Param("materialId") Long materialId,
                                                         @Param("defaultFlag") String defaultFlag);

    Long selectIdBySpuIdAndDefaultFlag(@Param("materialId") Long materialId,
                                       @Param("defaultFlag") String defaultFlag);

    List<MaterialExtend> selectBySkuCode(@Param("skuCode") String skuCode);

    List<MaterialExtend> selectByTenantAndUpdateTime(@Param("tenantId") Long tenantId,
                                                     @Param("lastTime") Long lastTime,
                                                     @Param("maxTime") Long maxTime);
}
