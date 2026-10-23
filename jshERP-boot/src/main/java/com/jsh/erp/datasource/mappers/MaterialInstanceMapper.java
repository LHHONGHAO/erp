package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.MaterialInstance;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MaterialInstanceMapper {
    MaterialInstance selectByPrimaryKey(Long id);

    MaterialInstance selectByUnitCode(@Param("unitCode") String unitCode);

    List<MaterialInstance> selectBySkuAndDepot(@Param("materialExtendId") Long materialExtendId,
                                               @Param("depotId") Long depotId,
                                               @Param("tenantId") Long tenantId);

    MaterialInstance selectByInboundItemId(@Param("inboundItemId") Long inboundItemId);

    int insertSelective(MaterialInstance record);

    int updateByPrimaryKeySelective(MaterialInstance record);
}
