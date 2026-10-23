package com.jsh.erp.datasource.mappers;

import com.jsh.erp.datasource.entities.*;
import com.jsh.erp.datasource.vo.MaterialDepotStock;
import com.jsh.erp.datasource.vo.MaterialVoSearch;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @Author: cjl
 * @Date: 2019/1/22 14:54
 */
public interface MaterialMapperEx {

    List<MaterialVo4Unit> selectByConditionMaterial(
            @Param("materialParam") String materialParam,
            @Param("standard") String standard,
            @Param("model") String model,
            @Param("color") String color,
            @Param("brand") String brand,
            @Param("mfrs") String mfrs,
            @Param("otherField1") String otherField1,
            @Param("otherField2") String otherField2,
            @Param("otherField3") String otherField3,
            @Param("otherField4") String otherField4,
            @Param("otherField5") String otherField5,
            @Param("otherField6") String otherField6,
            @Param("otherField7") String otherField7,
            @Param("weight") String weight,
            @Param("expiryNum") String expiryNum,
            @Param("enableSerialNumber") String enableSerialNumber,
            @Param("enableBatchNumber") String enableBatchNumber,
            @Param("position") String position,
            @Param("enabled") String enabled,
            @Param("remark") String remark,
            @Param("idList") List<Long> idList,
            @Param("mpList") String mpList);

    List<MaterialVo4Unit> selectSkuListByConditionMaterial(
            @Param("materialParam") String materialParam,
            @Param("standard") String standard,
            @Param("model") String model,
            @Param("color") String color,
            @Param("brand") String brand,
            @Param("mfrs") String mfrs,
            @Param("otherField1") String otherField1,
            @Param("otherField2") String otherField2,
            @Param("otherField3") String otherField3,
            @Param("otherField4") String otherField4,
            @Param("otherField5") String otherField5,
            @Param("otherField6") String otherField6,
            @Param("otherField7") String otherField7,
            @Param("weight") String weight,
            @Param("expiryNum") String expiryNum,
            @Param("enableSerialNumber") String enableSerialNumber,
            @Param("enableBatchNumber") String enableBatchNumber,
            @Param("position") String position,
            @Param("enabled") String enabled,
            @Param("remark") String remark,
            @Param("idList") List<Long> idList);

    Long insertSelectiveEx(Material record);

    List<Unit> findUnitList(@Param("mId") Long mId);

    List<MaterialVo4Unit> findById(@Param("id") Long id);

    List<MaterialVo4Unit> findByIdWithBarCode(@Param("meId") Long meId);

    List<MaterialVoSearch> getMaterialByParam(@Param("materialParam") String materialParam);

    List<MaterialVo4Unit> findBySelectWithBarCode(@Param("idList") List<Long> idList,
                                                  @Param("q") String q,
                                                  @Param("standardOrModel") String standardOrModel,
                                                  @Param("color") String color,
                                                  @Param("brand") String brand,
                                                  @Param("mfrs") String mfrs,
                                                  @Param("otherField1") String otherField1,
                                                  @Param("otherField2") String otherField2,
                                                  @Param("otherField3") String otherField3,
            @Param("otherField4") String otherField4,
            @Param("otherField5") String otherField5,
            @Param("otherField6") String otherField6,
            @Param("otherField7") String otherField7,
                                                  @Param("enableSerialNumber") String enableSerialNumber,
                                                  @Param("enableBatchNumber") String enableBatchNumber,
                                                  @Param("offset") Integer offset,
                                                  @Param("rows") Integer rows);

    int findBySelectWithBarCodeCount(@Param("idList") List<Long> idList,
                                     @Param("q") String q,
                                     @Param("standardOrModel") String standardOrModel,
                                     @Param("color") String color,
                                     @Param("brand") String brand,
                                     @Param("mfrs") String mfrs,
                                     @Param("otherField1") String otherField1,
                                     @Param("otherField2") String otherField2,
                                     @Param("otherField3") String otherField3,
            @Param("otherField4") String otherField4,
            @Param("otherField5") String otherField5,
            @Param("otherField6") String otherField6,
            @Param("otherField7") String otherField7,
                                     @Param("enableSerialNumber") String enableSerialNumber,
                                     @Param("enableBatchNumber") String enableBatchNumber);

    List<MaterialVo4Unit> exportExcel(
            @Param("materialParam") String materialParam,
            @Param("color") String color,
            @Param("materialOther") String materialOther,
            @Param("weight") String weight,
            @Param("expiryNum") String expiryNum,
            @Param("enabled") String enabled,
            @Param("enableSerialNumber") String enableSerialNumber,
            @Param("enableBatchNumber") String enableBatchNumber,
            @Param("remark") String remark,
            @Param("idList") List<Long> idList);

    List<MaterialExtend> getOtherMaterialList();

    /**
     * 通过商品名称查询商品信息
     * */
    List<Material> findByMaterialName(@Param("name") String name);
    /**
     * 获取开启序列号并且状态正常的商品列表
     * */
    List<MaterialVo4Unit> getMaterialEnableSerialNumberList(@Param("q") String q,
                                                     @Param("offset") Integer offset,
                                                     @Param("rows") Integer rows);

    Long getMaterialEnableSerialNumberCount(@Param("q") String q);

    int batchDeleteMaterialByIds(@Param("updateTime") Date updateTime, @Param("updater") Long updater, @Param("ids") String ids[]);

    List<Material> getMaterialListByCategoryIds(@Param("categoryIds") String[] categoryIds);

    List<Material> getMaterialListByUnitIds(@Param("unitIds") String[] unitIds);

    List<String> getBarCodeList();

    List<MaterialVo4Unit> getMaterialByMeId(
            @Param("meId") Long meId);

    List<String> getMaterialNameList();

    List<MaterialVo4Unit> getMaterialByBarCode(@Param("barCodeArray") String [] barCodeArray);

    List<MaterialVo4Unit> getMaterialByMeIds(@Param("meIdArray") String [] meIdArray);

    List<MaterialVo4Unit> getMaterialByBarCodeAndWithOutMId(
            @Param("barCodeArray") String [] barCodeArray,
            @Param("mId") Long mId);

    List<MaterialInitialStockWithMaterial> getInitialStockWithMaterial(
            @Param("depotList") List<Long> depotList);

    List<MaterialVo4Unit> getListWithStock(
            @Param("depotList") List<Long> depotList,
            @Param("idList") List<Long> idList,
            @Param("position") String position,
            @Param("materialParam") String materialParam,
            @Param("zeroStock") Integer zeroStock,
            @Param("column") String column,
            @Param("order") String order,
            @Param("offset") Integer offset,
            @Param("rows") Integer rows);

    List<MaterialVo4Unit> getSkuListWithStock(
            @Param("depotList") List<Long> depotList,
            @Param("idList") List<Long> idList,
            @Param("position") String position,
            @Param("materialParam") String materialParam,
            @Param("zeroStock") Integer zeroStock,
            @Param("column") String column,
            @Param("order") String order,
            @Param("offset") Integer offset,
            @Param("rows") Integer rows);

    int getSkuListWithStockCount(
            @Param("depotList") List<Long> depotList,
            @Param("idList") List<Long> idList,
            @Param("position") String position,
            @Param("materialParam") String materialParam,
            @Param("zeroStock") Integer zeroStock);

    List<MaterialVo4Unit> getUnitListWithStock(
            @Param("tenantId") Long tenantId,
            @Param("depotList") List<Long> depotList,
            @Param("idList") List<Long> idList,
            @Param("position") String position,
            @Param("materialParam") String materialParam,
            @Param("column") String column,
            @Param("order") String order,
            @Param("offset") Integer offset,
            @Param("rows") Integer rows);

    int getUnitListWithStockCount(
            @Param("tenantId") Long tenantId,
            @Param("depotList") List<Long> depotList,
            @Param("idList") List<Long> idList,
            @Param("position") String position,
            @Param("materialParam") String materialParam);

    MaterialVo4Unit getUnitTotalStockAndPrice(
            @Param("tenantId") Long tenantId,
            @Param("depotList") List<Long> depotList,
            @Param("idList") List<Long> idList,
            @Param("position") String position,
            @Param("materialParam") String materialParam);

    int getListWithStockCount(
            @Param("depotList") List<Long> depotList,
            @Param("idList") List<Long> idList,
            @Param("position") String position,
            @Param("materialParam") String materialParam,
            @Param("zeroStock") Integer zeroStock);

    MaterialVo4Unit getTotalStockAndPrice(
            @Param("depotList") List<Long> depotList,
            @Param("idList") List<Long> idList,
            @Param("position") String position,
            @Param("materialParam") String materialParam);

    int checkIsExist(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("model") String model,
            @Param("color") String color,
            @Param("standard") String standard,
            @Param("mfrs") String mfrs,
            @Param("otherField1") String otherField1,
            @Param("otherField2") String otherField2,
            @Param("otherField3") String otherField3,
            @Param("otherField4") String otherField4,
            @Param("otherField5") String otherField5,
            @Param("otherField6") String otherField6,
            @Param("otherField7") String otherField7,
            @Param("unit") String unit,
            @Param("unitId") Long unitId);

    MaterialExtend getMaterialExtendBySerialNumber(
            @Param("serialNumber") String serialNumber);

    List<MaterialDepotStock> getMaterialDepotStock(
            @Param("depotIdArray") Long[] depotIdArray,
            @Param("mId") Long mId);

    int countByBarCode(@Param("barCode") String barCode);

    int countMaterialByCode(@Param("code") String code);

    int updateMaterialCodeById(@Param("id") Long id, @Param("code") String code);
}
