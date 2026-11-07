package com.jsh.erp.print;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import javax.annotation.Resource;

/**
 * 打印代理 WebSocket 入口：wss://<host>/ws/print-agent
 *
 * 代理使用子协议 erp-print-agent.v1，握手阶段由拦截器校验设备 Token。
 */
@Configuration
@EnableWebSocket
public class PrintAgentWebSocketConfig implements WebSocketConfigurer {

    /** 与代理端 wsclient.Subprotocol 保持一致 */
    private static final String SUB_PROTOCOL = "erp-print-agent.v1";

    @Resource
    private PrintAgentWebSocketHandler handler;

    @Resource
    private PrintAgentHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
        handshakeHandler.setSupportedProtocols(SUB_PROTOCOL);
        registry.addHandler(handler, "/ws/print-agent")
                .addInterceptors(handshakeInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOrigins("*");
    }
}
