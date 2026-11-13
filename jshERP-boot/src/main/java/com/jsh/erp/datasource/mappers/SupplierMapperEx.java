package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.Supplier;
import com.jsh.erp.datasource.entities.SupplierExample;
import com.jsh.erp.datasource.vo.SupplierSimple;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface SupplierMapperEx {

    List<Supplier> selectByConditionSupplier(
            @Param("supplier") String supplier,
            @Param("type") String type,
            @Param("contacts") String contacts,
            @Param("phonenum") String phonenum,
            @Param("telephone") String telephone,
            @Param("creatorArray") String[] creatorArray);

    List<Supplier> findByAll(
            @Param("supplier") String supplier,
            @Param("type") String type,
            @Param("phonenum") String phonenum,
            @Param("telephone") String telephone);

    int batchDeleteSupplierByIds(@Param("updateTime") Date updateTime, @Param("updater") Long updater, @Param("ids") String ids[]);

    Supplier getSupplierByNameAndType(
            @Param("supplier") String supplier,
            @Param("type") String type);

    List<SupplierSimple> getAllCustomer();

    List<Supplier> findByTypeAndKey(
            @Param("type") String type,
            @Param("key") String key,
            @Param("limit") Integer limit);

    Supplier getInfoById(
            @Param("id") Long id);

    Supplier getInfoByName(
            @Param("name") String name,
            @Param("type") String type);

    Integer countMemberToday(
            @Param("datePrefix") String datePrefix);

    String getMaxMemberSeqToday(
            @Param("datePrefix") String datePrefix);

    List<Supplier> findByContactsOrPhone(
            @Param("contacts") String contacts,
            @Param("telephone") String telephone,
            @Param("type") String type);

    /**
     * 地址打印保存会员前的候选会员粗筛（联系人/电话/平台账号任一命中）
     */
    List<Supplier> findMemberCandidates(
            @Param("contacts") String contacts,
            @Param("phones") List<String> phones,
            @Param("wechatAccount") String wechatAccount,
            @Param("douyinAccount") String douyinAccount,
            @Param("alipayAccount") String alipayAccount,
            @Param("otherAccount") String otherAccount);
}