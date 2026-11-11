package com.jsh.erp.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AI 配置读取工具
 *
 * 读取「系统管理 - AI配置」维护的 ai-api-config.json，
 * 供 OCR 等业务按「服务商 + 能力」取用接口地址与密钥。
 */
public class AiApiConfig {

    private static final Logger logger = LoggerFactory.getLogger(AiApiConfig.class);

    /** 配置路径唯一来源：数据目录下的 config/ai-api-config.json（默认 ./data/config） */
    public static String getConfigPath() {
        String dataDir = System.getProperty("jsh.data.dir");
        if (dataDir == null || dataDir.trim().isEmpty()) {
            dataDir = "./data";
        }
        return dataDir.replace('\\', '/') + "/config/ai-api-config.json";
    }

    public static JSONArray readItems() {
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

    /**
     * 按能力查找已配置密钥的条目，百度优先。
     * capabilities 按优先级传入，如 ("text", "image")。
     */
    public static JSONObject find(String... capabilities) {
        JSONArray items = readItems();
        for (String capability : capabilities) {
            JSONObject fallback = null;
            for (int i = 0; i < items.size(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (!capability.equals(item.getString("capability"))) {
                    continue;
                }
                if (!hasKeys(item)) {
                    continue;
                }
                if ("baidu".equals(item.getString("provider"))) {
                    return item;
                }
                if (fallback == null) {
                    fallback = item;
                }
            }
            if (fallback != null) {
                return fallback;
            }
        }
        return null;
    }

    public static boolean hasKeys(JSONObject item) {
        if (item == null) {
            return false;
        }
        String apiKey = item.getString("apiKey");
        String apiSecret = item.getString("apiSecret");
        return apiKey != null && !apiKey.trim().isEmpty()
                && apiSecret != null && !apiSecret.trim().isEmpty();
    }

    public static String urlOrDefault(JSONObject item, String defaultUrl) {
        String url = item == null ? null : item.getString("apiUrl");
        return url == null || url.trim().isEmpty() ? defaultUrl : url.trim();
    }
}
