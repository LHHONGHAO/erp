package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.label.LabelProviderRegistry;
import com.jsh.erp.utils.BaseResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 标签数据源统一入口
 *
 * 编辑器通过这些接口获取可用数据源、字段元数据与预览数据，
 * 不直接访问任何业务数据库表。
 */
@RestController
@RequestMapping(value = "/labelProvider")
public class LabelProviderController {

    private Logger logger = LoggerFactory.getLogger(LabelProviderController.class);

    @Resource
    private LabelProviderRegistry registry;

    /**
     * 数据源列表（含字段元数据）
     *
     * @param businessType 业务标识（如 product），传入时追加该业务相关的数据表
     */
    @GetMapping(value = "/list")
    public BaseResponseInfo list(
            @RequestParam(value = "businessType", required = false) String businessType) {
        logger.info("labelProvider/list called, businessType={}", businessType);
        BaseResponseInfo res = new BaseResponseInfo();
        res.code = 200;
        res.data = registry.describe(businessType);
        logger.info("labelProvider/list done, size={}", res.data == null ? 0 : ((com.alibaba.fastjson.JSONArray) res.data).size());
        return res;
    }

    /** 指定数据源的字段元数据 */
    @GetMapping(value = "/fields")
    public BaseResponseInfo fields(@RequestParam("provider") String provider) {
        BaseResponseInfo res = new BaseResponseInfo();
        res.code = 200;
        res.data = registry.getFields(provider);
        return res;
    }

    /** 指定数据源按业务单据 ID 取统一数据 */
    @GetMapping(value = "/data")
    public BaseResponseInfo data(@RequestParam("provider") String provider,
                                 @RequestParam("documentId") String documentId,
                                 @RequestParam(value = "subId", required = false) String subId,
                                 @RequestParam(value = "businessType", required = false) String businessType) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            res.code = 200;
            res.data = registry.loadData(provider, documentId, subId, businessType);
        } catch (Exception e) {
            logger.error("标签数据加载失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = new JSONObject();
        }
        return res;
    }
}
