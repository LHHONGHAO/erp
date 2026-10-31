package com.jsh.erp.config;

import org.apache.catalina.connector.Connector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * 在原有 HTTP 端口之外额外开启一个 HTTPS 连接器（默认 9443）。
 * 目的：手机通过局域网访问时，浏览器仅在 HTTPS（安全上下文）下才允许调用摄像头，
 * 不修改原 HTTP 端口，避免影响现有访问方式。
 * 证书默认取数据目录下的 config/jsh-https.p12，找不到则自动跳过。
 */
@Configuration
public class HttpsConnectorConfig {
    private static final Logger logger = LoggerFactory.getLogger(HttpsConnectorConfig.class);
    private static final String HTTP11_PROTOCOL = "org.apache.coyote.http11.Http11NioProtocol";

    @Value("${jsh.https.port:9443}")
    private int httpsPort;

    @Value("${jsh.https.keystore:}")
    private String keystore;

    @Value("${jsh.https.keystore-password:changeit}")
    private String keystorePassword;

    @Value("${jsh.data.dir:}")
    private String dataDir;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpsConnectorCustomizer() {
        return factory -> {
            String path = keystore;
            if (path == null || path.trim().isEmpty()) {
                path = (dataDir == null || dataDir.trim().isEmpty() ? "config" : dataDir.trim() + "/config") + "/jsh-https.p12";
            }
            File file = new File(path);
            if (!file.exists()) {
                logger.info("未找到 HTTPS 证书 {}，跳过 HTTPS 连接器", file.getAbsolutePath());
                return;
            }
            Connector connector = new Connector(HTTP11_PROTOCOL);
            connector.setPort(httpsPort);
            connector.setSecure(true);
            connector.setScheme("https");
            connector.setProperty("SSLEnabled", "true");
            connector.setProperty("keystoreFile", file.getAbsolutePath());
            connector.setProperty("keystorePass", keystorePassword);
            connector.setProperty("keystoreType", "PKCS12");
            connector.setProperty("clientAuth", "false");
            factory.addAdditionalTomcatConnectors(connector);
            logger.info("已开启 HTTPS 连接器，端口 {}", httpsPort);
        };
    }
}
