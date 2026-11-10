package com.jsh.erp.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.Base64Utils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OcrUtil {
    private static final Logger logger = LoggerFactory.getLogger(OcrUtil.class);
    /** 手机号 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    /** 百度开放平台令牌地址 */
    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    /** 百度通用文字识别（图片转文字），可在 AI 配置中覆盖 */
    private static final String DEFAULT_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic";
    /** 百度地址识别（自然语言），可在 AI 配置中覆盖 */
    private static final String DEFAULT_ADDRESS_URL = "https://aip.baidubce.com/rpc/2.0/nlp/v1/address";

    private static String accessToken;
    private static String accessTokenKey;
    private static long tokenExpireTime;

    /** 密钥判空：null 或仅空白字符都视为未配置 */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 文字识别配置：AI配置中「能力=文字识别」，未配置时回退「图片识别」 */
    private static JSONObject ocrEntry() {
        return AiApiConfig.find("text", "image");
    }

    /** 地址识别配置：AI配置中「能力=地址识别」 */
    private static JSONObject addressEntry() {
        return AiApiConfig.find("address");
    }

    public static boolean isEnabled() {
        return AiApiConfig.hasKeys(ocrEntry());
    }

    /** 地址识别是否可用：图片转文字需要文字识别密钥，地址解析需要地址识别密钥 */
    public static boolean isAddressEnabled() {
        return AiApiConfig.hasKeys(ocrEntry()) || AiApiConfig.hasKeys(addressEntry());
    }

    public static String getAccessToken() throws Exception {
        JSONObject entry = ocrEntry();
        if (!AiApiConfig.hasKeys(entry)) {
            throw new RuntimeException("未配置百度文字识别密钥，请到「系统管理 - AI配置」添加：服务商选百度，能力选文字识别");
        }
        return getToken(entry.getString("apiKey"), entry.getString("apiSecret"));
    }

    /**
     * 获取百度访问令牌，按密钥缓存，密钥变更后自动重新获取
     */
    private static String getToken(String apiKey, String apiSecret) throws Exception {
        String cacheKey = apiKey + "|" + apiSecret;
        if (!isBlank(accessToken) && cacheKey.equals(accessTokenKey) && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken;
        }
        String params = "grant_type=client_credentials&client_id=" + URLEncoder.encode(apiKey, "UTF-8")
                + "&client_secret=" + URLEncoder.encode(apiSecret, "UTF-8");
        String result = postForm(TOKEN_URL, params);
        JSONObject json = JSON.parseObject(result);
        if (json.containsKey("access_token")) {
            accessToken = json.getString("access_token");
            accessTokenKey = cacheKey;
            int expiresIn = json.getIntValue("expires_in");
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 3600) * 1000L;
            return accessToken;
        }
        String error = json.getString("error_description");
        throw new RuntimeException("获取百度访问令牌失败：" + (isBlank(error) ? result : error));
    }

    public static Map<String, String> recognizeProductImage(String imageBase64) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (!isEnabled()) {
            throw new RuntimeException("未配置百度文字识别密钥，请到「系统管理 - AI配置」添加：服务商选百度，能力选文字识别");
        }
        String accessToken = getAccessToken();
        String ocrUrl = AiApiConfig.urlOrDefault(ocrEntry(), DEFAULT_OCR_URL) + "?access_token=" + accessToken;

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
            throw new RuntimeException("未配置识别密钥，请到「系统管理 - AI配置」添加：文字识别、地址识别");
        }
        // 图片转文字用的是「通用文字识别」API，地址解析用的是「地址识别」API，两者密钥各用各的：
        // 优先使用文字识别密钥；未配置时才退回地址识别密钥（同一百度应用开通了两项也能用）。
        JSONObject ocrEntry = ocrEntry();
        JSONObject addressEntry = addressEntry();
        boolean hasOcrKeys = AiApiConfig.hasKeys(ocrEntry);
        String ocrAccessToken;
        String ocrUrl;
        if (hasOcrKeys) {
            ocrAccessToken = getToken(ocrEntry.getString("apiKey"), ocrEntry.getString("apiSecret"));
            ocrUrl = AiApiConfig.urlOrDefault(ocrEntry, DEFAULT_OCR_URL);
        } else {
            ocrAccessToken = getToken(addressEntry.getString("apiKey"), addressEntry.getString("apiSecret"));
            ocrUrl = DEFAULT_OCR_URL;
        }
        ocrUrl = ocrUrl + "?access_token=" + ocrAccessToken;

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
            String errorMsg = json.getString("error_msg");
            if (errorMsg != null && !errorMsg.isEmpty()) {
                throw new RuntimeException("OCR识别失败：" + errorMsg + "（error_code=" + json.getString("error_code") + "）");
            }
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
        JSONObject entry = addressEntry();
        if (!AiApiConfig.hasKeys(entry)) {
            throw new RuntimeException("未配置百度地址识别密钥，请到「系统管理 - AI配置」添加");
        }
        String accessToken = getToken(entry.getString("apiKey"), entry.getString("apiSecret"));
        String url = AiApiConfig.urlOrDefault(entry, DEFAULT_ADDRESS_URL) + "?access_token=" + accessToken;

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
        // 单独成行的属性标题（如「名称」「名称：」），等待下一行作为其值
        String pendingLabel = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 0. 上一行是单独标题：本行不是标题、也不是 key:value 时，本行即该标题的值
            if (pendingLabel != null) {
                String labelOnly = line.replaceFirst("[\\s:：=＝|｜\\-—–、，,.;；。]+$", "");
                boolean isLabelLine = keyMapping.containsKey(labelOnly);
                boolean isKeyValueLine = line.contains("：") || line.contains(":");
                if (!isLabelLine && !isKeyValueLine) {
                    String field = keyMapping.get(pendingLabel);
                    if (field != null) {
                        if (!result.containsKey(field)) {
                            result.put(field, line);
                        }
                    } else {
                        result.put(pendingLabel, line);
                    }
                    pendingLabel = null;
                    lastGenericKey = null;
                    continue;
                }
                pendingLabel = null;
            }

            // 1. 行首为已知属性标题：标题后跟随任意分隔符（： : = ｜ - 、 ， 空格等）都提取为值
            String knownKey = matchKnownKeyPrefix(line, keyMapping);
            if (knownKey != null) {
                String value = line.substring(knownKey.length())
                        .replaceFirst("^[\\s:：=＝|｜\\-—–、，,.;；。]+", "").trim();
                if (!value.isEmpty()) {
                    String field = keyMapping.get(knownKey);
                    if (field != null) {
                        if (!result.containsKey(field)) {
                            result.put(field, value);
                        }
                    } else {
                        result.put(knownKey, value);
                    }
                    lastGenericKey = null;
                    continue;
                }
            }

            // 1.5 整行只有属性标题（如「名称」「名称：」）：记录标题，下一行作为它的值
            String labelOnly = line.replaceFirst("[\\s:：=＝|｜\\-—–、，,.;；。]+$", "");
            if (keyMapping.containsKey(labelOnly)) {
                pendingLabel = labelOnly;
                continue;
            }

            // 2. 优先按冒号分割：key：value 或 key:value
            String[] parts = null;
            if (line.contains("：")) {
                parts = line.split("：", 2);
            } else if (line.contains(":")) {
                parts = line.split(":", 2);
            } else {
                // 3. 其它分隔符：key=value、key-value、key｜value、key value 等
                parts = splitByOtherSeparator(line);
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

    /** 非冒号分隔符的行解析：短标题 + 分隔符 + 内容（标题不能以数字开头） */
    private static final Pattern OTHER_SEPARATOR_PATTERN = Pattern.compile(
            "^([^0-9\\s:：=＝|｜\\-—–、，,.;；。][^\\s:：=＝|｜\\-—–、，,.;；。]{0,11})[\\s:：=＝|｜\\-—–、，,.;；。]+(.+)$");

    /**
     * 行首是否为已知属性标题，且标题后紧跟分隔符或空白。
     * 例如“名称：xxx”“名称=xxx”“名称|xxx”“名称 xxx”都视为该标题的值。
     */
    private static String matchKnownKeyPrefix(String line, Map<String, String> keyMapping) {
        String matched = null;
        for (String key : keyMapping.keySet()) {
            if (line.startsWith(key) && (matched == null || key.length() > matched.length())) {
                matched = key;
            }
        }
        if (matched == null || line.length() <= matched.length()) {
            return null;
        }
        String rest = line.substring(matched.length());
        if (!rest.matches("^[\\s:：=＝|｜\\-—–、，,.;；]。*")) {
            return null;
        }
        return matched;
    }

    /** 非冒号分隔符分割：用于映射表中没有的标题，结果供前端按属性别名匹配 */
    private static String[] splitByOtherSeparator(String line) {
        Matcher m = OTHER_SEPARATOR_PATTERN.matcher(line);
        if (m.matches()) {
            return new String[]{m.group(1), m.group(2)};
        }
        return null;
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

}
