package com.jsh.erp.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.utils.AiApiConfig;
import com.jsh.erp.utils.BaseResponseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * AI 配置：统一维护系统内用到的第三方 API 调用参数（名称、接口地址、API Key、备注）。
 *
 * 配置落盘到统一数据目录下的 config/ai-api-config.json，后续功能可按「名称」读取。
 */
@RestController
@RequestMapping(value = "/aiConfig")
public class AiConfigController {

    private Logger logger = LoggerFactory.getLogger(AiConfigController.class);

    /**
     * 列表
     */
    @GetMapping(value = "/list")
    public BaseResponseInfo list() {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            JSONArray items = readItems();
            JSONArray masked = new JSONArray();
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                JSONObject copy = new JSONObject(true);
                copy.put("id", item.getString("id"));
                copy.put("name", item.getString("name"));
                copy.put("provider", item.getString("provider"));
                copy.put("capability", item.getString("capability"));
                copy.put("apiUrl", item.getString("apiUrl"));
                copy.put("remark", item.getString("remark"));
                copy.put("updatedAt", item.getString("updatedAt"));
                String apiKey = item.getString("apiKey");
                copy.put("hasApiKey", apiKey != null && !apiKey.trim().isEmpty());
                copy.put("maskedApiKey", maskKey(apiKey));
                String apiSecret = item.getString("apiSecret");
                copy.put("hasSecretKey", apiSecret != null && !apiSecret.trim().isEmpty());
                copy.put("maskedSecretKey", maskKey(apiSecret));
                masked.add(copy);
            }
            res.code = 200;
            res.data = masked;
        } catch (Exception e) {
            logger.error("查询 AI 配置失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "查询失败：" + e.getMessage();
        }
        return res;
    }

    /**
     * 新增或修改；修改时 apiKey 留空表示沿用原值
     */
    @PostMapping(value = "/save")
    public BaseResponseInfo save(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            String name = trim(body.getString("name"));
            if (name.isEmpty()) {
                res.code = 500;
                res.data = "请输入名称";
                return res;
            }
            String id = trim(body.getString("id"));
            JSONArray items = readItems();
            JSONObject target = null;
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (!id.isEmpty() && id.equals(item.getString("id"))) {
                    target = item;
                    break;
                }
                if (id.isEmpty() && name.equals(item.getString("name"))) {
                    res.code = 500;
                    res.data = "名称已存在：" + name;
                    return res;
                }
            }
            if (target == null) {
                target = new JSONObject(true);
                target.put("id", UUID.randomUUID().toString().replace("-", ""));
                items.add(target);
            }
            target.put("name", name);
            target.put("provider", trim(body.getString("provider")));
            target.put("capability", trim(body.getString("capability")));
            target.put("apiUrl", trim(body.getString("apiUrl")));
            target.put("remark", trim(body.getString("remark")));
            String apiKey = trim(body.getString("apiKey"));
            if (!apiKey.isEmpty()) {
                target.put("apiKey", apiKey);
            }
            String apiSecret = trim(body.getString("apiSecret"));
            if (!apiSecret.isEmpty()) {
                target.put("apiSecret", apiSecret);
            }
            target.put("updatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            writeItems(items);
            res.code = 200;
            res.data = "保存成功";
        } catch (Exception e) {
            logger.error("保存 AI 配置失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "保存失败：" + e.getMessage();
        }
        return res;
    }

    /**
     * 删除
     */
    @PostMapping(value = "/delete")
    public BaseResponseInfo delete(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            String id = trim(body.getString("id"));
            if (id.isEmpty()) {
                res.code = 500;
                res.data = "缺少配置 id";
                return res;
            }
            JSONArray items = readItems();
            JSONArray remain = new JSONArray();
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (!id.equals(item.getString("id"))) {
                    remain.add(item);
                }
            }
            writeItems(remain);
            res.code = 200;
            res.data = "删除成功";
        } catch (Exception e) {
            logger.error("删除 AI 配置失败: " + e.getMessage(), e);
            res.code = 500;
            res.data = "删除失败：" + e.getMessage();
        }
        return res;
    }

    /**
     * 测试：百度优先校验 API Key / Secret Key，其余服务商检测调用地址是否可达
     */
    @PostMapping(value = "/test")
    public BaseResponseInfo test(@RequestBody JSONObject body) {
        BaseResponseInfo res = new BaseResponseInfo();
        JSONObject result = new JSONObject(true);
        try {
            String provider = trim(body.getString("provider"));
            String apiUrl = trim(body.getString("apiUrl"));
            String apiKey = trim(body.getString("apiKey"));
            String apiSecret = trim(body.getString("apiSecret"));
            String id = trim(body.getString("id"));
            // 编辑已保存的配置时，密钥留空表示沿用原值
            if ((apiKey.isEmpty() || apiSecret.isEmpty()) && !id.isEmpty()) {
                JSONObject saved = findById(id);
                if (saved != null) {
                    if (apiKey.isEmpty()) {
                        apiKey = trim(saved.getString("apiKey"));
                    }
                    if (apiSecret.isEmpty()) {
                        apiSecret = trim(saved.getString("apiSecret"));
                    }
                }
            }
            if (apiUrl.isEmpty()) {
                result.put("success", false);
                result.put("message", "请先填写调用地址");
                res.code = 200;
                res.data = result;
                return res;
            }
            long start = System.currentTimeMillis();
            if ("baidu".equals(provider) && !apiKey.isEmpty() && !apiSecret.isEmpty()) {
                String tokenUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials"
                        + "&client_id=" + URLEncoder.encode(apiKey, "UTF-8")
                        + "&client_secret=" + URLEncoder.encode(apiSecret, "UTF-8");
                JSONObject token = httpGetJson(tokenUrl);
                long cost = System.currentTimeMillis() - start;
                if (token.getString("access_token") != null) {
                    result.put("success", true);
                    result.put("message", "测试成功：密钥有效，已获取访问令牌（" + cost + " ms）");
                } else {
                    result.put("success", false);
                    result.put("message", "测试失败：" + token.getString("error_description") + "（" + token.getString("error") + "）");
                }
            } else {
                int httpCode = httpReachable(apiUrl);
                long cost = System.currentTimeMillis() - start;
                result.put("success", true);
                result.put("message", "测试成功：接口可达（HTTP " + httpCode + "，" + cost + " ms）");
            }
        } catch (Exception e) {
            logger.error("AI 配置测试失败: " + e.getMessage(), e);
            result.put("success", false);
            result.put("message", "测试失败：" + e.getMessage());
        }
        res.code = 200;
        res.data = result;
        return res;
    }

    private JSONObject findById(String id) {
        JSONArray items = readItems();
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (id.equals(item.getString("id"))) {
                return item;
            }
        }
        return null;
    }

    private JSONObject httpGetJson(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        int code = conn.getResponseCode();
        InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = readStream(is);
        conn.disconnect();
        return JSON.parseObject(text);
    }

    private int httpReachable(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "JSH-ERP-AIConfig-Test");
        int code = conn.getResponseCode();
        conn.disconnect();
        return code;
    }

    private String readStream(InputStream is) throws Exception {
        if (is == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = is.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        is.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private JSONArray readItems() {
        try {
            Path path = Paths.get(getConfigPath());
            if (!Files.exists(path)) {
                return new JSONArray();
            }
            String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            JSONArray array = JSON.parseArray(text);
            return array == null ? new JSONArray() : array;
        } catch (Exception e) {
            logger.error("读取 AI 配置失败: " + e.getMessage(), e);
            return new JSONArray();
        }
    }

    private void writeItems(JSONArray items) throws Exception {
        Path path = Paths.get(getConfigPath());
        File parent = path.getParent().toFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        Files.write(path, items.toJSONString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 配置文件路径：与 AiApiConfig 共用同一实现，保证只有一条路径
     */
    private String getConfigPath() {
        return AiApiConfig.getConfigPath();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String maskKey(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "";
        }
        String key = apiKey.trim();
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
