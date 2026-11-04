package com.jsh.erp.datasource.mappers;

import org.apache.ibatis.annotations.Param;

public interface SequenceMapperEx {

    /**
     * 序列不存在时初始化一行（已存在则不做任何修改）
     */
    void insertSequenceIfAbsent(@Param("seqName") String seqName, @Param("remark") String remark);

    /**
     * 指定序列自增1
     */
    void updateBuildOnlyNumberBySeqName(@Param("seqName") String seqName);

    /**
     * 获取指定序列的当前值
     */
    Long getBuildOnlyNumberBySeqName(@Param("seqName") String seqName);
}
