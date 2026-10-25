package com.jsh.erp.datasource.entities;

import lombok.Data;

import java.util.Date;

/** 单件商品进出库档案，一行对应一件可独立扫码追踪的实物。 */
@Data
public class MaterialInstance {
    private Long id;
    private String unitCode;
    private Long materialId;
    private Long materialExtendId;
    private String skuCode;
    private Long inboundDepotId;
    /** 门店（期初入库时选择，随单件档案保存） */
    private String store;
    private Long currentDepotId;
    /** INITIAL、PURCHASE、OTHER */
    private String inboundType;
    private Long inboundHeadId;
    private Long inboundItemId;
    private Date inboundTime;
    private Long outboundDepotId;
    /** SALE、TRANSFER、OTHER */
    private String outboundType;
    private Long outboundHeadId;
    private Long outboundItemId;
    private Date outboundTime;
    /** IN_STOCK、OUTBOUND、SOLD、SCRAPPED、LOST */
    private String status;
    private Long tenantId;
    private String deleteFlag;
    private String remark;
    private Date createTime;
    private Date updateTime;
}
