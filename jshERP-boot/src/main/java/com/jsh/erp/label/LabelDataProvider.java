package com.jsh.erp.label;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 业务数据适配器
 *
 * 各业务模块实现本接口，把业务表数据转换成统一标签数据结构；
 * 标签模板与渲染引擎只依赖统一字段，不感知业务表结构。
 */
public interface LabelDataProvider {

    /** 数据源标识，如 product.label */
    String getProvider();

    /** 数据源名称，如 商品标签 */
    String getName();

    /** 字段元数据：[{ key, label, type, required }] */
    JSONArray getFields();

    /**
     * 按业务单据 ID 取统一数据
     *
     * @param documentId 业务单据 ID（如商品 ID）
     * @param subId      可选子 ID（如 SKU 扩展 ID），为空时取默认记录
     */
    JSONObject loadData(String documentId, String subId);
}
