package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.SpecValueAlias;
import org.apache.ibatis.annotations.Param;

/**
 * 规格值别名 Mapper
 */
public interface SpecValueAliasMapper {

    int insertSelective(SpecValueAlias record);

    SpecValueAlias selectByNormKey(@Param("normKey") String normKey);

    int increaseHitCount(@Param("id") Long id);
}
