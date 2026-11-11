package com.jsh.erp.controller;

import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.service.SpecValueSampleService;
import com.jsh.erp.utils.ErpInfo;
import com.jsh.erp.utils.OcrUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Base64Utils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import static com.jsh.erp.utils.ResponseJsonUtil.returnJson;

@RestController
@RequestMapping(value = "/ocr")
@Api(tags = {"OCR图片识别"})
public class OcrController {
    private Logger logger = LoggerFactory.getLogger(OcrController.class);

    @Resource
    private SpecValueSampleService specValueSampleService;

    @PostMapping(value = "/recognizeProduct")
    @ApiOperation(value = "识别商品图片信息")
    public String recognizeProduct(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            logger.info("OCR recognizeProduct called");
            if (!OcrUtil.isEnabled()) {
                logger.warn("OCR is not enabled");
                return returnJson(objectMap, "未配置百度文字识别密钥，请到「系统管理 - AI配置」添加：服务商选百度，能力选文字识别", ErpInfo.ERROR.code);
            }
            if (file.isEmpty()) {
                return returnJson(objectMap, "请上传图片文件", ErpInfo.ERROR.code);
            }
            byte[] bytes = file.getBytes();
            String imageBase64 = Base64Utils.encodeToString(bytes);
            Map<String, String> result = OcrUtil.recognizeProductImage(imageBase64);
            specValueSampleService.enhanceRecognizedStandard(result);
            logger.info("OCR result: {}", result);
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("识别商品图片失败", e);
            return returnJson(objectMap, "识别失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/recognizeProductByBase64")
    @ApiOperation(value = "通过Base64识别商品图片信息")
    public String recognizeProductByBase64(@RequestBody JSONObject params, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            logger.info("OCR recognizeProductByBase64 called");
            if (!OcrUtil.isEnabled()) {
                logger.warn("OCR is not enabled");
                return returnJson(objectMap, "未配置百度文字识别密钥，请到「系统管理 - AI配置」添加：服务商选百度，能力选文字识别", ErpInfo.ERROR.code);
            }
            String imageBase64 = params.getString("imageBase64");
            if (imageBase64 == null || imageBase64.isEmpty()) {
                logger.warn("imageBase64 is empty");
                return returnJson(objectMap, "请提供图片数据", ErpInfo.ERROR.code);
            }
            if (imageBase64.contains(",")) {
                imageBase64 = imageBase64.split(",")[1];
            }
            logger.info("imageBase64 length: {}", imageBase64.length());
            Map<String, String> result = OcrUtil.recognizeProductImage(imageBase64);
            specValueSampleService.enhanceRecognizedStandard(result);
            logger.info("OCR result: {}", result);
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("识别商品图片失败", e);
            return returnJson(objectMap, "识别失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/recognizeAddress")
    @ApiOperation(value = "识别地址/个人信息（名片、快递单等）")
    public String recognizeAddress(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            logger.info("OCR recognizeAddress called");
            if (!OcrUtil.isAddressEnabled()) {
                return returnJson(objectMap, "未配置识别密钥，请到「系统管理 - AI配置」添加：文字识别、地址识别", ErpInfo.ERROR.code);
            }
            if (file.isEmpty()) {
                return returnJson(objectMap, "请上传图片文件", ErpInfo.ERROR.code);
            }
            byte[] bytes = file.getBytes();
            String imageBase64 = Base64Utils.encodeToString(bytes);
            Map<String, String> result = OcrUtil.recognizeAddressImage(imageBase64);
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("识别地址信息失败", e);
            return returnJson(objectMap, "识别失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/parseAddressText")
    @ApiOperation(value = "解析粘贴文本中的地址/个人信息")
    public String parseAddressText(@RequestBody JSONObject params, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            String text = params.getString("text");
            if (text == null || text.trim().isEmpty()) {
                return returnJson(objectMap, "请输入文本内容", ErpInfo.ERROR.code);
            }
            Map<String, String> result = OcrUtil.parseAddressText(text);
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("解析地址文本失败", e);
            return returnJson(objectMap, "解析失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }

    @PostMapping(value = "/parseProductText")
    @ApiOperation(value = "解析商品文本中的商品信息")
    public String parseProductText(@RequestBody JSONObject params, HttpServletRequest request) throws Exception {
        Map<String, Object> objectMap = new HashMap<>();
        try {
            String text = params.getString("text");
            if (text == null || text.trim().isEmpty()) {
                return returnJson(objectMap, "请输入文本内容", ErpInfo.ERROR.code);
            }
            Map<String, String> result = OcrUtil.parseProductText(text);
            specValueSampleService.enhanceRecognizedStandard(result);
            objectMap.putAll(result);
            return returnJson(objectMap, ErpInfo.OK.name, ErpInfo.OK.code);
        } catch (Exception e) {
            logger.error("解析商品文本失败", e);
            return returnJson(objectMap, "解析失败：" + e.getMessage(), ErpInfo.ERROR.code);
        }
    }
}
