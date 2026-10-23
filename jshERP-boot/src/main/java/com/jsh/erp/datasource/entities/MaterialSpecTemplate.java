package com.jsh.erp.datasource.entities;

/**
 * 商品规格模板
 * 定义每个商品使用哪些规格属性，支持动态数量的规格属性
 * 例如：被套关联[颜色,尺寸]，四件套关联[颜色,被套尺寸,床单尺寸,枕套尺寸]
 */
public class MaterialSpecTemplate {
    private Long id;

    private Long materialId;

    private Long attributeId;

    private Integer sort;

    private Long tenantId;

    private String deleteFlag;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getAttributeId() {
        return attributeId;
    }

    public void setAttributeId(Long attributeId) {
        this.attributeId = attributeId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
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
}
