package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.datasource.entities.SpecValueSample;
import com.jsh.erp.service.SpecRecognizeService;
import com.jsh.erp.service.SpecValueSampleService;
import com.jsh.erp.utils.ErpInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import static com.jsh.erp.utils.ResponseJsonUtil.returnJson;

/**
 * 规格值样本库：比对 / 学习 / 样本列表
 */
@RestController
@RequestMapping(value = "/spec/sample")
@Api(tags = {"规格值样本库"})
public class SpecSampleController {
    private Logger logger = LoggerFactory.getLogger(SpecSampleController.class);

    @Resource
    private SpecValueSampleService specValueSampleService;

    @Resource
    private SpecRecognizeService specRecognizeService;

    @PostMapping(value = "/match")
    @ApiOperation(value = "规格值比对：识别文本 -> 标准写法")
    public String match(@RequestBody JSONObject params, HttpServletRequest request) {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            String text = params.getString("text");
            if (text == null || text.trim().isEmpty()) {
                return returnJson(objectMap, "请输入规格值", ErpInfo.ERROR.code);
            }
            JSONObject result = specValueSampleService.match(text,
                    params.getLong("categoryId"), params.getLong("attributeId"));
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("规格值比对失败", e);
            return returnJson(objectMap, "比对失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/learn")
    @ApiOperation(value = "学习规格值：记录原始写法到标准写法的对应关系")
    public String learn(@RequestBody JSONObject params, HttpServletRequest request) {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            JSONObject result = specValueSampleService.learn(
                    params.getString("rawText"),
                    params.getString("correctedValue"),
                    params.getLong("categoryId"),
                    params.getLong("attributeId"),
                    params.getLong("materialId"),
                    params.getLong("userId"));
            if (!result.getBooleanValue("success")) {
                return returnJson(objectMap, result.getString("message"), ErpInfo.ERROR.code);
            }
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("规格值学习失败", e);
            return returnJson(objectMap, "学习失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/align")
    @ApiOperation(value = "规格值对齐：以类别配置值为标准，识别文本 -> 标准写法")
    public String align(@RequestBody JSONObject params, HttpServletRequest request) {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            String text = params.getString("text");
            if (text == null || text.trim().isEmpty()) {
                return returnJson(objectMap, "请输入规格值", ErpInfo.ERROR.code);
            }
            java.util.List<String> standards = new java.util.ArrayList<>();
            com.alibaba.fastjson.JSONArray array = params.getJSONArray("standards");
            if (array != null) {
                for (int i = 0; i < array.size(); i++) {
                    String value = array.getString(i);
                    if (value != null && !value.trim().isEmpty()) {
                        standards.add(value.trim());
                    }
                }
            }
            JSONObject result = specValueSampleService.align(text,
                    params.getLong("categoryId"), params.getLong("attributeId"), standards);
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("规格值对齐失败", e);
            return returnJson(objectMap, "对齐失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/learnBatch")
    @ApiOperation(value = "批量学习规格值：商品保存时沉淀识别原文到采纳值的对应关系")
    public String learnBatch(@RequestBody JSONObject params, HttpServletRequest request) {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            JSONObject learnResult = specValueSampleService.learnBatch(params.getJSONArray("items"),
                    params.getLong("categoryId"), params.getLong("materialId"), params.getLong("userId"));
            objectMap.put("count", learnResult.getIntValue("count"));
            objectMap.put("failed", learnResult.getJSONArray("failed"));
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("批量学习规格值失败", e);
            return returnJson(objectMap, "批量学习失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/recognizeProduct")
    @ApiOperation(value = "单图商品识别：属性名归一、属性值归一与规格补全")
    public String recognizeProduct(@RequestBody JSONObject params, HttpServletRequest request) {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            JSONObject result = specRecognizeService.recognize(
                    params.getJSONObject("image"),
                    params.getLong("categoryId"),
                    params.getJSONArray("dimensions"),
                    params.getJSONObject("labelAliases"));
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("单图商品识别失败", e);
            return returnJson(objectMap, "识别失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @GetMapping(value = "/list")
    @ApiOperation(value = "规格值样本列表")
    public String list(@RequestParam(value = "keyword", required = false) String keyword,
                       @RequestParam(value = "limit", required = false) Integer limit,
                       HttpServletRequest request) {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            java.util.List<SpecValueSample> list = specValueSampleService.list(keyword, limit);
            objectMap.put("list", list);
            objectMap.put("total", list.size());
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("查询规格值样本失败", e);
            return returnJson(objectMap, "查询失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }
}
