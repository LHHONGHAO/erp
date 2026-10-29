package com.jsh.erp.datasource.vo;

import com.jsh.erp.datasource.entities.MaterialExtend;

import java.math.BigDecimal;

public class MaterialExtendVo4List extends MaterialExtend {

    private String supplier;

    private String originPlace;

    private String unit;

    private String brandName;

    private BigDecimal guaranteePeriod;

    private BigDecimal memberDecimal;

    private BigDecimal currentStock;

    private String attribute;

    // 库存预警阈值与对应仓库（取自期初库存表，用于编辑回显）
    private BigDecimal lowSafeStock;

    private BigDecimal highSafeStock;

    private Long depotId;

    public BigDecimal getLowSafeStock() {
        return lowSafeStock;
    }

    public void setLowSafeStock(BigDecimal lowSafeStock) {
        this.lowSafeStock = lowSafeStock;
    }

    public BigDecimal getHighSafeStock() {
        return highSafeStock;
    }

    public void setHighSafeStock(BigDecimal highSafeStock) {
        this.highSafeStock = highSafeStock;
    }

    public Long getDepotId() {
        return depotId;
    }

    public void setDepotId(Long depotId) {
        this.depotId = depotId;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public String getOriginPlace() {
        return originPlace;
    }

    public void setOriginPlace(String originPlace) {
        this.originPlace = originPlace;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public BigDecimal getGuaranteePeriod() {
        return guaranteePeriod;
    }

    public void setGuaranteePeriod(BigDecimal guaranteePeriod) {
        this.guaranteePeriod = guaranteePeriod;
    }

    public BigDecimal getMemberDecimal() {
        return memberDecimal;
    }

    public void setMemberDecimal(BigDecimal memberDecimal) {
        this.memberDecimal = memberDecimal;
    }

    public BigDecimal getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(BigDecimal currentStock) {
        this.currentStock = currentStock;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }
}
