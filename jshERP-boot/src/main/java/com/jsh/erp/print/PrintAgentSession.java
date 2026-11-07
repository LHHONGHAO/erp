package com.jsh.erp.print;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * 一个已认证的打印代理连接
 *
 * 服务端下发的文本帧与二进制帧必须严格串行（协议要求同一时刻只传输一个任务），
 * 因此所有写操作都在同一把锁内完成。
 */
public class PrintAgentSession {

    private final WebSocketSession session;
    private final String deviceId;
    private volatile String version;
    private volatile String clientIp;
    private volatile long connectedAt;
    private volatile long lastSeen;
    private volatile String printers;
    private volatile int queueDepth;

    public PrintAgentSession(WebSocketSession session, String deviceId) {
        this.session = session;
        this.deviceId = deviceId;
        this.connectedAt = System.currentTimeMillis();
        this.lastSeen = this.connectedAt;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public boolean isOpen() {
        return session.isOpen();
    }

    public void sendText(String payload) throws IOException {
        synchronized (this) {
            if (!session.isOpen()) {
                throw new IOException("打印代理连接已关闭");
            }
            session.sendMessage(new TextMessage(payload));
        }
    }

    public void sendBinary(byte[] data) throws IOException {
        synchronized (this) {
            if (!session.isOpen()) {
                throw new IOException("打印代理连接已关闭");
            }
            session.sendMessage(new BinaryMessage(data));
        }
    }

    public void touch() {
        this.lastSeen = System.currentTimeMillis();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public String getPrinters() {
        return printers;
    }

    public void setPrinters(String printers) {
        this.printers = printers;
    }

    public int getQueueDepth() {
        return queueDepth;
    }

    public void setQueueDepth(int queueDepth) {
        this.queueDepth = queueDepth;
    }
}
