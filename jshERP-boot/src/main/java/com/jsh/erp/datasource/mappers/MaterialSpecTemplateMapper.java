package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.MaterialSpecTemplate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface MaterialSpecTemplateMapper {
    int deleteByPrimaryKey(Long id);

    int insert(MaterialSpecTemplate record);

    int insertSelective(MaterialSpecTemplate record);

    MaterialSpecTemplate selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(MaterialSpecTemplate record);

    int updateByPrimaryKey(MaterialSpecTemplate record);

    List<MaterialSpecTemplate> selectByMaterialId(@Param("materialId") Long materialId);

    int deleteByMaterialId(@Param("materialId") Long materialId);
}
