package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.SpecValueSample;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规格值样本库 Mapper
 */
public interface SpecValueSampleMapper {

    int insertSelective(SpecValueSample record);

    int updateByPrimaryKeySelective(SpecValueSample record);

    SpecValueSample selectByPrimaryKey(@Param("id") Long id);

    /** 按归一化键精确匹配（attributeId 为空时不限规格名，childName 为空时不限子规格） */
    SpecValueSample selectByNormKey(@Param("normKey") String normKey,
                                    @Param("attributeId") Long attributeId,
                                    @Param("categoryId") Long categoryId,
                                    @Param("childName") String childName,
                                    @Param("tenantId") Long tenantId);

    /** 按数值部分模糊匹配：200*230 可命中 200*230|cm */
    SpecValueSample selectByNumericKey(@Param("numericKey") String numericKey,
                                       @Param("attributeId") Long attributeId,
                                       @Param("categoryId") Long categoryId,
                                       @Param("childName") String childName,
                                       @Param("tenantId") Long tenantId);

    /** 同一作用域内的样本（用于非数值属性的相似度兜底） */
    List<SpecValueSample> selectByScope(@Param("attributeId") Long attributeId,
                                        @Param("categoryId") Long categoryId,
                                        @Param("childName") String childName,
                                        @Param("tenantId") Long tenantId,
                                        @Param("limit") Integer limit);

    /** 样本列表（管理/排查用） */
    List<SpecValueSample> selectList(@Param("keyword") String keyword,
                                     @Param("limit") Integer limit);

    int increaseHitCount(@Param("id") Long id);
}
