package com.jsh.erp.datasource.entities;

import java.math.BigDecimal;

public class Material {
    private Long id;

    private Long categoryId;

    private String name;

    private String code;

    private String barCode;

    private String mfrs;

    private String model;

    private String standard;

    private String brand;

    private String mnemonic;

    private String color;

    private String unit;

    private String remark;

    private Long unitId;

    private Integer expiryNum;

    private BigDecimal weight;

    private Boolean enabled;

    private String otherField1;

    private String otherField2;

    private String otherField3;

    private String otherField4;

    private String otherField5;

    private String otherField6;

    private String otherField7;

    private String enableSerialNumber;

    private String enableBatchNumber;

    private String position;

    private String attribute;

    private String fabric;

    private String safetyCategory;

    private String executiveStandard;

    private String grade;

    private String inspection;

    private Long tenantId;

    private String deleteFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.trim();
    }

    public String getBarCode() {
        return barCode;
    }

    public void setBarCode(String barCode) {
        this.barCode = barCode == null ? null : barCode.trim();
    }

    public String getMfrs() {
        return mfrs;
    }

    public void setMfrs(String mfrs) {
        this.mfrs = mfrs == null ? null : mfrs.trim();
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model == null ? null : model.trim();
    }

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard == null ? null : standard.trim();
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand == null ? null : brand.trim();
    }

    public String getMnemonic() {
        return mnemonic;
    }

    public void setMnemonic(String mnemonic) {
        this.mnemonic = mnemonic == null ? null : mnemonic.trim();
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color == null ? null : color.trim();
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit == null ? null : unit.trim();
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark == null ? null : remark.trim();
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public Integer getExpiryNum() {
        return expiryNum;
    }

    public void setExpiryNum(Integer expiryNum) {
        this.expiryNum = expiryNum;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getOtherField1() {
        return otherField1;
    }

    public void setOtherField1(String otherField1) {
        this.otherField1 = otherField1 == null ? null : otherField1.trim();
    }

    public String getOtherField2() {
        return otherField2;
    }

    public void setOtherField2(String otherField2) {
        this.otherField2 = otherField2 == null ? null : otherField2.trim();
    }

    public String getOtherField3() {
        return otherField3;
    }

    public void setOtherField3(String otherField3) {
        this.otherField3 = otherField3 == null ? null : otherField3.trim();
    }

    public String getOtherField4() {
        return otherField4;
    }

    public void setOtherField4(String otherField4) {
        this.otherField4 = otherField4 == null ? null : otherField4.trim();
    }

    public String getOtherField5() {
        return otherField5;
    }

    public void setOtherField5(String otherField5) {
        this.otherField5 = otherField5 == null ? null : otherField5.trim();
    }

    public String getOtherField6() {
        return otherField6;
    }

    public void setOtherField6(String otherField6) {
        this.otherField6 = otherField6 == null ? null : otherField6.trim();
    }

    public String getOtherField7() {
        return otherField7;
    }

    public void setOtherField7(String otherField7) {
        this.otherField7 = otherField7 == null ? null : otherField7.trim();
    }

    public String getEnableSerialNumber() {
        return enableSerialNumber;
    }

    public void setEnableSerialNumber(String enableSerialNumber) {
        this.enableSerialNumber = enableSerialNumber == null ? null : enableSerialNumber.trim();
    }

    public String getEnableBatchNumber() {
        return enableBatchNumber;
    }

    public void setEnableBatchNumber(String enableBatchNumber) {
        this.enableBatchNumber = enableBatchNumber == null ? null : enableBatchNumber.trim();
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position == null ? null : position.trim();
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute == null ? null : attribute.trim();
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(String deleteFlag) {
        this.deleteFlag = deleteFlag == null ? null : deleteFlag.trim();
    }

    public String getFabric() {
        return fabric;
    }

    public void setFabric(String fabric) {
        this.fabric = fabric == null ? null : fabric.trim();
    }

    public String getSafetyCategory() {
        return safetyCategory;
    }

    public void setSafetyCategory(String safetyCategory) {
        this.safetyCategory = safetyCategory == null ? null : safetyCategory.trim();
    }

    public String getExecutiveStandard() {
        return executiveStandard;
    }

    public void setExecutiveStandard(String executiveStandard) {
        this.executiveStandard = executiveStandard == null ? null : executiveStandard.trim();
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade == null ? null : grade.trim();
    }

    public String getInspection() {
        return inspection;
    }

    public void setInspection(String inspection) {
        this.inspection = inspection == null ? null : inspection.trim();
    }
}
