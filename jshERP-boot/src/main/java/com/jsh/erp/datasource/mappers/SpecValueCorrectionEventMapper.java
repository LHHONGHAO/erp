package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.SpecValueCorrectionEvent;
import com.jsh.erp.datasource.entities.SpecValueCorrectionStat;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 识别值学习事件 Mapper
 */
public interface SpecValueCorrectionEventMapper {

    int insertSelective(SpecValueCorrectionEvent record);

    /** 统计同一作用域、同一识别原文下各最终值在最近 days 天内的出现次数 */
    List<SpecValueCorrectionStat> statByRaw(@Param("tenantId") Long tenantId,
                                            @Param("categoryId") Long categoryId,
                                            @Param("attributeId") Long attributeId,
                                            @Param("childName") String childName,
                                            @Param("rawNormKey") String rawNormKey,
                                            @Param("days") int days);
}
