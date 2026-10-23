package com.jsh.erp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ocr.baidu")
public class BaiduOcrConfig {
    private boolean enabled = false;
    private String apiKey;
    private String secretKey;
    private String tokenUrl;
    private String ocrUrl;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getOcrUrl() {
        return ocrUrl;
    }

    public void setOcrUrl(String ocrUrl) {
        this.ocrUrl = ocrUrl;
    }
}
