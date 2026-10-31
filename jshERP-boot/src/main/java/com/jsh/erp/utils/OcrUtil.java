package com.jsh.erp.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.jsh.erp.config.BaiduOcrConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OcrUtil {
    private static final Logger logger = LoggerFactory.getLogger(OcrUtil.class);
    /** 手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    /** 百度地址识别（自然语言）API */
    private static final String ADDRESS_TEXT_URL = "https://aip.baidubce.com/rpc/2.0/nlp/v1/address";

    @Autowired
    private BaiduOcrConfig baiduOcrConfig;

    private static BaiduOcrConfig config;

    private static String accessToken;
    private static long tokenExpireTime;

    private static Boolean runtimeEnabled;
    private static String runtimeApiKey;
    private static String runtimeSecretKey;

    /** 地址识别独立配置（与图片识别使用不同的 API Key） */
    private static Boolean runtimeAddressEnabled;
    private static String runtimeAddressApiKey;
    private static String runtimeAddressSecretKey;
    private static String addressAccessToken;
    private static long addressTokenExpireTime;

    @PostConstruct
    public void init() {
        config = baiduOcrConfig;
        loadConfigFromFile();
    }

    /** OCR 配置文件路径：优先取统一数据目录（-Djsh.data.dir），否则退回启动目录下 config/ */
    private static String getOcrConfigPath() {
        String dataDir = System.getProperty("jsh.data.dir");
        if (dataDir != null && !dataDir.trim().isEmpty()) {
            return dataDir.replace('\\', '/') + "/config/ocr-config.properties";
        }
        return System.getProperty("user.dir") + "/config/ocr-config.properties";
    }

    private static void loadConfigFromFile() {
        try {
            String configPath = getOcrConfigPath();
            Path path = Paths.get(configPath);
            if (Files.exists(path)) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(path.toString())) {
                    props.load(fis);
                }
                String enabledStr = props.getProperty("ocr.baidu.enabled", "false");
                String apiKey = props.getProperty("ocr.baidu.apiKey", "");
                String secretKey = props.getProperty("ocr.baidu.secretKey", "");
                runtimeEnabled = Boolean.parseBoolean(enabledStr);
                if (apiKey != null && !apiKey.isEmpty()) {
                    runtimeApiKey = apiKey;
                }
                if (secretKey != null && !secretKey.isEmpty()) {
                    runtimeSecretKey = secretKey;
                }
                // 地址识别独立配置
                if (props.containsKey("ocr.address.enabled")) {
                    runtimeAddressEnabled = Boolean.parseBoolean(props.getProperty("ocr.address.enabled", "false"));
                }
                String addressApiKey = props.getProperty("ocr.address.apiKey", "");
                String addressSecretKey = props.getProperty("ocr.address.secretKey", "");
                if (addressApiKey != null && !addressApiKey.isEmpty()) {
                    runtimeAddressApiKey = addressApiKey;
                }
                if (addressSecretKey != null && !addressSecretKey.isEmpty()) {
                    runtimeAddressSecretKey = addressSecretKey;
                }
                logger.info("Loaded OCR config from file: {}", configPath);
            }
        } catch (Exception e) {
            logger.warn("Failed to load OCR config from file, using default config", e);
        }
    }

    public static boolean isEnabled() {
        if (runtimeEnabled != null) {
            return runtimeEnabled;
        }
        return config != null && config.isEnabled();
    }

    public static String getAccessToken() throws Exception {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }
        if (!isEnabled()) {
            throw new RuntimeException("OCR功能未启用，请在配置文件中配置百度OCR的API Key和Secret Key");
        }
        String apiKey = runtimeApiKey != null ? runtimeApiKey : config.getApiKey();
        String secretKey = runtimeSecretKey != null ? runtimeSecretKey : config.getSecretKey();
        String tokenUrl = config.getTokenUrl();

        String params = "grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
        String result = postForm(tokenUrl, params);
        JSONObject json = JSON.parseObject(result);
        if (json.containsKey("access_token")) {
            accessToken = json.getString("access_token");
            int expiresIn = json.getIntValue("expires_in");
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 3600) * 1000L;
            return accessToken;
        } else {
            throw new RuntimeException("获取OCR访问令牌失败：" + result);
        }
    }

    /** 地址识别是否启用：独立配置优先，未单独配置时沿用图片识别配置 */
    public static boolean isAddressEnabled() {
        if (runtimeAddressEnabled != null) {
            return runtimeAddressEnabled;
        }
        return isEnabled();
    }

    /** 地址识别的 API Key：未单独配置时沿用图片识别配置 */
    public static String getAddressApiKey() {
        if (runtimeAddressApiKey != null && !runtimeAddressApiKey.trim().isEmpty()) {
            return runtimeAddressApiKey;
        }
        return runtimeApiKey != null ? runtimeApiKey : (config != null ? config.getApiKey() : null);
    }

    /** 地址识别的 Secret Key：未单独配置时沿用图片识别配置 */
    public static String getAddressSecretKey() {
        if (runtimeAddressSecretKey != null && !runtimeAddressSecretKey.trim().isEmpty()) {
            return runtimeAddressSecretKey;
        }
        return runtimeSecretKey != null ? runtimeSecretKey : (config != null ? config.getSecretKey() : null);
    }

    /** 地址识别独立令牌缓存，与图片识别区分 */
    public static String getAddressAccessToken() throws Exception {
        if (addressAccessToken != null && System.currentTimeMillis() < addressTokenExpireTime) {
            return addressAccessToken;
        }
        if (!isAddressEnabled()) {
            throw new RuntimeException("地址识别未启用，请先配置地址识别的API Key和Secret Key");
        }
        String apiKey = getAddressApiKey();
        String secretKey = getAddressSecretKey();
        String tokenUrl = config.getTokenUrl();

        String params = "grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
        String result = postForm(tokenUrl, params);
        JSONObject json = JSON.parseObject(result);
        if (json.containsKey("access_token")) {
            addressAccessToken = json.getString("access_token");
            int expiresIn = json.getIntValue("expires_in");
            addressTokenExpireTime = System.currentTimeMillis() + (expiresIn - 3600) * 1000L;
            return addressAccessToken;
        } else {
            throw new RuntimeException("获取地址识别访问令牌失败：" + result);
        }
    }

    public static Map<String, String> recognizeProductImage(String imageBase64) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (!isEnabled()) {
            throw new RuntimeException("OCR功能未启用，请在配置文件中配置百度OCR的API Key和Secret Key");
        }
        String accessToken = getAccessToken();
        String ocrUrl = config.getOcrUrl() + "?access_token=" + accessToken;

        String encodedImage = URLEncoder.encode(imageBase64, "UTF-8");
        String params = "image=" + encodedImage + "&detect_direction=false&detect_language=false&paragraph=false&probability=false";

        String response = postForm(ocrUrl, params);
        JSONObject json = JSON.parseObject(response);

        if (json.containsKey("words_result")) {
            StringBuilder allText = new StringBuilder();
            for (Object word : json.getJSONArray("words_result")) {
                JSONObject wordObj = (JSONObject) word;
                String text = wordObj.getString("words");
                allText.append(text).append("\n");
            }
            result.put("rawText", allText.toString().trim());
            parseProductInfo(allText.toString(), result);
        } else {
            throw new RuntimeException("OCR识别失败：" + response);
        }
        return result;
    }

    /**
     * 识别地址/个人信息（名片、身份证、快递单等）
     */
    public static Map<String, String> recognizeAddressImage(String imageBase64) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (!isAddressEnabled()) {
            throw new RuntimeException("地址识别未启用，请先配置地址识别的API Key和Secret Key");
        }
        String accessToken = getAddressAccessToken();
        String ocrUrl = config.getOcrUrl() + "?access_token=" + accessToken;

        String encodedImage = URLEncoder.encode(imageBase64, "UTF-8");
        String params = "image=" + encodedImage + "&detect_direction=false&detect_language=false&paragraph=false&probability=false";

        String response = postForm(ocrUrl, params);
        JSONObject json = JSON.parseObject(response);

        if (json.containsKey("words_result")) {
            StringBuilder allText = new StringBuilder();
            for (Object word : json.getJSONArray("words_result")) {
                JSONObject wordObj = (JSONObject) word;
                String text = wordObj.getString("words");
                allText.append(text).append("\n");
            }
            result.put("rawText", allText.toString().trim());
            result.putAll(parseAddressText(allText.toString().trim()));
        } else {
            throw new RuntimeException("OCR识别失败：" + response);
        }
        return result;
    }

    /**
     * 从粘贴文本中解析地址/个人信息：优先调用百度地址识别 API，失败时回退到本地规则解析
     */
    public static Map<String, String> parseAddressText(String text) {
        Map<String, String> result = new HashMap<>();
        result.put("rawText", text);
        try {
            return parseAddressTextByApi(text);
        } catch (Exception e) {
            logger.warn("地址识别API调用失败，回退本地解析：{}", e.getMessage());
            parseAddressInfo(text, result);
            return result;
        }
    }

    /**
     * 调用百度地址识别 API（rpc/2.0/nlp/v1/address）解析文本，返回结构化地址、联系人、电话
     */
    public static Map<String, String> parseAddressTextByApi(String text) throws Exception {
        Map<String, String> result = new HashMap<>();
        String accessToken = getAddressAccessToken();
        String url = ADDRESS_TEXT_URL + "?access_token=" + accessToken;

        JSONObject body = new JSONObject();
        body.put("text", text);
        String response = postJson(url, JSON.toJSONString(body));
        JSONObject json = JSON.parseObject(response);

        if (json.containsKey("error_code")) {
            throw new RuntimeException("地址识别失败：" + json.getString("error_msg"));
        }
        result.put("rawText", text);
        String address = joinAddress(json);
        if (!address.isEmpty()) {
            result.put("address", address);
        }
        String person = json.getString("person");
        if (person != null && !person.trim().isEmpty()) {
            result.put("contacts", person.trim());
        }
        String phone = json.getString("phonenum");
        if (phone != null && !phone.trim().isEmpty()) {
            result.put("telephone", phone.trim());
        }
        // API 未返回的字段用本地规则补齐
        if (!result.containsKey("contacts") || !result.containsKey("telephone")) {
            parseAddressInfo(text, result);
        }
        return result;
    }

    /** 拼接 API 返回的省/市/县/镇/详细地址，已包含的片段不重复拼接 */
    private static String joinAddress(JSONObject json) {
        String[] keys = {"province", "city", "county", "town", "detail"};
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            String part = json.getString(key);
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            part = part.trim();
            if (!sb.toString().contains(part)) {
                sb.append(part);
            }
        }
        return sb.toString();
    }

    /**
     * 从粘贴/编辑后的文本中解析商品信息
     */
    public static Map<String, String> parseProductText(String text) {
        Map<String, String> result = new HashMap<>();
        result.put("rawText", text);
        parseProductInfo(text, result);
        return result;
    }

    private static String postForm(String urlStr, String params) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json;charset=UTF-8");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        OutputStream os = conn.getOutputStream();
        os.write(params.getBytes("UTF-8"));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        BufferedReader br;
        if (responseCode >= 200 && responseCode < 300) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        conn.disconnect();

        logger.info("OCR API response code: {}, response: {}", responseCode, sb.toString());
        return sb.toString();
    }

    private static String postJson(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json;charset=UTF-8");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        OutputStream os = conn.getOutputStream();
        os.write(jsonBody.getBytes("UTF-8"));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        BufferedReader br;
        if (responseCode >= 200 && responseCode < 300) {
            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            br = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        }
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        conn.disconnect();

        logger.info("Address API response code: {}, response: {}", responseCode, sb.toString());
        return sb.toString();
    }

    private static void parseProductInfo(String text, Map<String, String> result) {
        String[] lines = text.split("\n");
        StringBuilder nameBuilder = new StringBuilder();

        // key关键词到属性字段的映射
        Map<String, String> keyMapping = new HashMap<>();
        keyMapping.put("品名", "name");
        keyMapping.put("名称", "name");
        keyMapping.put("商品名", "name");
        keyMapping.put("产品名", "name");
        keyMapping.put("条码", "barCode");
        keyMapping.put("条形码", "barCode");
        keyMapping.put("编码", "barCode");
        keyMapping.put("规格", "standard");
        keyMapping.put("净含量", "standard");
        keyMapping.put("重量", "weight");
        keyMapping.put("填充物重量", "weight");
        keyMapping.put("容量", "standard");
        keyMapping.put("品牌", "brand");
        keyMapping.put("商标", "brand");
        keyMapping.put("单位", "unit");
        keyMapping.put("型号", "model");
        keyMapping.put("颜色", "color");
        keyMapping.put("助记码", "mnemonic");
        keyMapping.put("制造商", "mfrs");
        keyMapping.put("厂家", "mfrs");
        keyMapping.put("生产商", "mfrs");
        keyMapping.put("生产厂商", "mfrs");
        keyMapping.put("面料", "fabric");
        keyMapping.put("材质", "fabric");
        keyMapping.put("安全类别", "safetyCategory");
        keyMapping.put("执行标准", "executiveStandard");
        keyMapping.put("等级", "grade");
        keyMapping.put("检验", "inspection");
        keyMapping.put("填充物", "filler");

        // 记录上一行通用兜底的 key，用于多行值合并
        // 只有通用兜底（未匹配到标准字段）的 key 才允许后续行追加值
        String lastGenericKey = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 优先按冒号分割：key：value 或 key:value
            String[] parts = null;
            if (line.contains("：")) {
                parts = line.split("：", 2);
            } else if (line.contains(":")) {
                parts = line.split(":", 2);
            }

            if (parts != null && parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (!value.isEmpty()) {
                    // 1. 精确匹配
                    String field = findFieldByKey(key, keyMapping);
                    if (field != null) {
                        if (!result.containsKey(field)) {
                            result.put(field, value);
                        }
                        lastGenericKey = null;
                        continue;
                    }
                    // 2. 模糊匹配（不覆盖已存在的字段值）
                    field = findFieldByKeyword(key, keyMapping);
                    if (field != null && !result.containsKey(field)) {
                        result.put(field, value);
                        lastGenericKey = null;
                        continue;
                    }
                    // 3. 通用兜底：将原始 key 和 value 存入结果，供前端按属性名/别名匹配
                    result.put(key, value);
                    lastGenericKey = key;
                }
                continue;
            }

            // 不含冒号的行
            // 1. 纯数字 → 条码
            if (line.matches("\\d{13}") || line.matches("\\d{8}") || line.matches("\\d{12,14}")) {
                result.put("barCode", line);
                lastGenericKey = null;
                continue;
            }
            // 2. 如果上一行是通用兜底的 key:value，当前行作为值的延续追加
            if (lastGenericKey != null && line.length() <= 50) {
                String existingValue = result.get(lastGenericKey);
                if (existingValue != null && !existingValue.isEmpty()) {
                    result.put(lastGenericKey, existingValue + " " + line);
                    continue;
                }
            }
            // 3. 短文本 → 名称拼接（仅当 name 尚未被精确匹配设置时）
            if (!result.containsKey("name") && nameBuilder.length() < 50 && line.length() >= 2 && line.length() <= 30) {
                if (nameBuilder.length() > 0) {
                    nameBuilder.append(" ");
                }
                nameBuilder.append(line);
            }
            lastGenericKey = null;
        }

        if (nameBuilder.length() > 0 && !result.containsKey("name")) {
            result.put("name", nameBuilder.toString().trim());
        }
    }

    /** 精确匹配：key 与映射表的 key 完全一致 */
    private static String findFieldByKey(String key, Map<String, String> keyMapping) {
        return keyMapping.get(key);
    }

    /** 模糊匹配：key 中包含映射表的某个关键词 */
    private static String findFieldByKeyword(String key, Map<String, String> keyMapping) {
        for (Map.Entry<String, String> entry : keyMapping.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 解析地址/个人信息（名片、快递单、身份证等）
     */
    private static void parseAddressInfo(String text, Map<String, String> result) {
        // 手机号可能夹在地址中间（如「…解放路10号18527555299段友亮」），先抽取并移除
        Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
        if (phoneMatcher.find()) {
            String phone = phoneMatcher.group();
            if (!result.containsKey("telephone")) {
                result.put("telephone", phone);
            }
            text = text.replace(phone, " ");
        }

        String[] lines = text.split("\n");

        // key关键词映射
        Map<String, String> keyMapping = new HashMap<>();
        keyMapping.put("姓名", "contacts");
        keyMapping.put("联系人", "contacts");
        keyMapping.put("名字", "contacts");
        keyMapping.put("手机", "telephone");
        keyMapping.put("手机号", "telephone");
        keyMapping.put("手机号码", "telephone");
        keyMapping.put("电话", "phoneNum");
        keyMapping.put("联系电话", "phoneNum");
        keyMapping.put("座机", "phoneNum");
        keyMapping.put("邮箱", "email");
        keyMapping.put("电子邮箱", "email");
        keyMapping.put("Email", "email");
        keyMapping.put("地址", "address");
        keyMapping.put("收货地址", "address");
        keyMapping.put("联系地址", "address");
        keyMapping.put("地址信息", "address");
        keyMapping.put("公司", "company");
        keyMapping.put("公司名称", "company");

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 冒号分割
            String[] parts = null;
            if (line.contains("：")) {
                parts = line.split("：", 2);
            } else if (line.contains(":")) {
                parts = line.split(":", 2);
            }

            if (parts != null && parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (!value.isEmpty()) {
                    String field = findFieldByKey(key, keyMapping);
                    if (field == null) {
                        field = findFieldByKeyword(key, keyMapping);
                    }
                    if (field != null && !result.containsKey(field)) {
                        result.put(field, value);
                    }
                }
                continue;
            }

            // 无冒号行：正则匹配
            // 手机号
            if (line.matches("1[3-9]\\d{9}")) {
                if (!result.containsKey("telephone")) {
                    result.put("telephone", line);
                }
                continue;
            }
            // 座机号（含区号）
            if (line.matches("0\\d{2,3}-\\d{7,8}") || line.matches("\\d{3,4}-\\d{7,8}")) {
                if (!result.containsKey("phoneNum")) {
                    result.put("phoneNum", line);
                }
                continue;
            }
            // 邮箱
            if (line.matches("[\\w.-]+@[\\w.-]+\\.\\w+")) {
                if (!result.containsKey("email")) {
                    result.put("email", line);
                }
                continue;
            }
            // 含省/市/区/路/街/号/栋/楼/室/单元等 → 地址
            if ((line.contains("省") || line.contains("市") || line.contains("区")
                    || line.contains("路") || line.contains("街") || line.contains("号")
                    || line.contains("栋") || line.contains("楼") || line.contains("室")
                    || line.contains("单元") || line.contains("大厦") || line.contains("小区"))
                    && line.length() >= 5) {
                // 地址末尾若紧跟 2~4 个中文字（如「…10号段友亮」），拆出作为联系人
                int cut = -1;
                String[] addressTails = {"号", "栋", "幢", "楼", "室", "单元", "小区", "大厦", "村", "组"};
                for (String tailKey : addressTails) {
                    int idx = line.lastIndexOf(tailKey);
                    if (idx >= 0) {
                        cut = Math.max(cut, idx + tailKey.length());
                    }
                }
                String addressLine = line;
                if (cut > 0) {
                    String nameTail = line.substring(cut).trim();
                    if (nameTail.matches("[\\u4e00-\\u9fa5]{2,4}") && !result.containsKey("contacts")) {
                        result.put("contacts", nameTail);
                        addressLine = line.substring(0, cut).trim();
                    }
                }
                if (!result.containsKey("address")) {
                    result.put("address", addressLine);
                } else {
                    result.put("address", result.get("address") + " " + addressLine);
                }
                continue;
            }
            // 短文本(2-4个纯中文字) → 可能是姓名
            if (line.matches("[\\u4e00-\\u9fa5]{2,4}") && !result.containsKey("contacts")) {
                result.put("contacts", line);
            }
        }
    }

    public static String imageFileToBase64(String imgFile) throws Exception {
        InputStream in = new FileInputStream(imgFile);
        byte[] data = new byte[in.available()];
        in.read(data);
        in.close();
        return Base64Utils.encodeToString(data);
    }

    public static String imageUrlToBase64(String imgUrl) throws Exception {
        URL url = new URL(imgUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len = 0;
        while ((len = is.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        is.close();
        byte[] data = outStream.toByteArray();
        return Base64Utils.encodeToString(data);
    }

    public static void updateConfig(boolean enabled, String apiKey, String secretKey) {
        runtimeEnabled = enabled;
        runtimeApiKey = apiKey;
        runtimeSecretKey = secretKey;
        accessToken = null;
        tokenExpireTime = 0;
    }

    /** 更新地址识别（独立 API）配置 */
    public static void updateAddressConfig(boolean enabled, String apiKey, String secretKey) {
        runtimeAddressEnabled = enabled;
        runtimeAddressApiKey = apiKey;
        runtimeAddressSecretKey = secretKey;
        addressAccessToken = null;
        addressTokenExpireTime = 0;
    }

    /** 将指定前缀（如 ocr.baidu / ocr.address）的配置写入配置文件，保留其他配置项 */
    public static synchronized void saveConfigToFile(String prefix, boolean enabled, String apiKey, String secretKey) throws Exception {
        String configPath = getOcrConfigPath();
        Path path = Paths.get(configPath);
        if (!Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        Properties props = new Properties();
        if (Files.exists(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                props.load(in);
            }
        }
        props.setProperty(prefix + ".enabled", String.valueOf(enabled));
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            props.setProperty(prefix + ".apiKey", apiKey.trim());
        }
        if (secretKey != null && !secretKey.trim().isEmpty()) {
            props.setProperty(prefix + ".secretKey", secretKey.trim());
        }
        props.setProperty(prefix + ".tokenUrl", "https://aip.baidubce.com/oauth/2.0/token");
        if (!props.containsKey(prefix + ".ocrUrl")) {
            props.setProperty(prefix + ".ocrUrl", "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic");
        }
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "JSH ERP OCR config");
        }
        logger.info("OCR config saved: {}", configPath);
    }

    /** 地址识别 API Key（脱敏） */
    public static String getMaskedAddressApiKey() {
        return maskKey(getAddressApiKey());
    }

    /** 地址识别 Secret Key（脱敏） */
    public static String getMaskedAddressSecretKey() {
        return maskKey(getAddressSecretKey());
    }

    private static String maskKey(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 8) {
            return key;
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    public static String getConfiguredApiKey() {
        return runtimeApiKey != null && !runtimeApiKey.trim().isEmpty()
                ? runtimeApiKey : (config != null ? config.getApiKey() : null);
    }

    public static String getConfiguredSecretKey() {
        return runtimeSecretKey != null && !runtimeSecretKey.trim().isEmpty()
                ? runtimeSecretKey : (config != null ? config.getSecretKey() : null);
    }

    public static String getMaskedApiKey() {
        String key = runtimeApiKey != null ? runtimeApiKey : (config != null ? config.getApiKey() : null);
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 8) {
            return key;
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    public static String getMaskedSecretKey() {
        String key = runtimeSecretKey != null ? runtimeSecretKey : (config != null ? config.getSecretKey() : null);
        if (key == null || key.isEmpty()) {
            return "";
        }
        if (key.length() <= 8) {
            return key;
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
