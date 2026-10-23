package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.MaterialAttrValue;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品属性值字典 Mapper
 */
public interface MaterialAttrValueMapper {

    int insertSelective(MaterialAttrValue record);

    int updateByPrimaryKeySelective(MaterialAttrValue record);

    MaterialAttrValue selectByPrimaryKey(@Param("id") Long id);

    /** 按属性名id查启用的属性值列表 */
    List<MaterialAttrValue> selectByAttributeId(@Param("attributeId") Long attributeId);

    /** 按品类和规格名查询启用的规格值 */
    List<MaterialAttrValue> selectByAttributeIdAndCategoryId(@Param("attributeId") Long attributeId,
                                                              @Param("categoryId") Long categoryId);

    /** 按 value_code 查（用于 SKU 溯源反查） */
    MaterialAttrValue selectByValueCode(@Param("valueCode") String valueCode);

    /** 查全部启用列表 */
    List<MaterialAttrValue> selectAllEnabled();

    /** 逻辑删除 */
    int updateDeleteFlagById(@Param("id") Long id);

    /** 校验同租户下 value_code 是否已存在 */
    int countByValueCode(@Param("valueCode") String valueCode, @Param("excludeId") Long excludeId);
}
