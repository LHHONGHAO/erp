package com.jsh.erp.print;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 打印代理与打印任务的存储
 *
 * 沿用项目内原生 JDBC 的方式，启动时自动建表，不引入 MyBatis 映射。
 * print_agent：一台主机（路由器/工控机）上的打印代理配置，token 用于握手认证。
 * print_task：每次下发的打印任务及最终状态，用于页面查询与问题排查。
 */
@Component
public class PrintAgentStore {

    private Logger logger = LoggerFactory.getLogger(PrintAgentStore.class);

    /** 终态集合，SQL 中复用 */
    private static final String FINAL_STATES = "'success','failed','timeout','offline','cancelled','rejected'";

    @Resource
    private DataSource dataSource;

    @PostConstruct
    public void init() {
        createAgentTable();
        ensureAgentColumns();
        createTaskTable();
    }

    private void createAgentTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `print_agent` (" +
                "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "`device_id` varchar(64) NOT NULL COMMENT '设备编号，与代理配置一致'," +
                "`name` varchar(100) DEFAULT NULL COMMENT '设备名称'," +
                "`token` varchar(64) DEFAULT NULL COMMENT '设备 Token'," +
                "`printers` longtext COMMENT '打印机列表 JSON'," +
                "`version` varchar(32) DEFAULT NULL COMMENT '代理版本'," +
                "`last_seen` datetime DEFAULT NULL COMMENT '最后在线时间'," +
                "`client_ip` varchar(64) DEFAULT NULL COMMENT '最近连接 IP'," +
                "`enabled` varchar(1) DEFAULT '1' COMMENT '状态 1启用 0停用'," +
                "`paired` varchar(1) DEFAULT '0' COMMENT '0待配对 1已配对'," +
                "`revoked` varchar(1) DEFAULT '0' COMMENT '0正常 1已吊销'," +
                "`pair_code` varchar(32) DEFAULT NULL COMMENT '一次性配对码'," +
                "`pair_expire_at` datetime DEFAULT NULL COMMENT '配对码过期时间'," +
                "`server_url` varchar(255) DEFAULT NULL COMMENT '代理连接的服务端地址'," +
                "`token_expire_at` datetime DEFAULT NULL COMMENT '当前 Token 过期时间'," +
                "`prev_token` varchar(64) DEFAULT NULL COMMENT '上一次 Token（续期宽限）'," +
                "`prev_expire_at` datetime DEFAULT NULL COMMENT '上一次 Token 过期时间'," +
                "`created_at` datetime DEFAULT NULL COMMENT '创建时间'," +
                "`updated_at` datetime DEFAULT NULL COMMENT '更新时间'," +
                "PRIMARY KEY (`id`)," +
                "UNIQUE KEY `uk_device_id` (`device_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='打印代理设备'";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("打印代理设备表 print_agent 已就绪");
        } catch (Exception e) {
            logger.error("初始化打印代理设备表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 旧库升级：补齐配对与状态列，不中断启动。
     * 列不存在才 ALTER，避免 MySQL 8 不支持 ADD COLUMN IF NOT EXISTS 的问题；
     * 单列失败只记 warn，兼容多实例并发启动。
     */
    private void ensureAgentColumns() {
        String[][] cols = {
                {"pair_code", "varchar(32) DEFAULT NULL COMMENT '一次性配对码'"},
                {"pair_expire_at", "datetime DEFAULT NULL COMMENT '配对码过期时间'"},
                {"paired", "varchar(1) DEFAULT '0' COMMENT '0待配对 1已配对'"},
                {"revoked", "varchar(1) DEFAULT '0' COMMENT '0正常 1已吊销'"},
                {"server_url", "varchar(255) DEFAULT NULL COMMENT '代理连接的服务端地址'"},
                {"token_expire_at", "datetime DEFAULT NULL COMMENT '当前 Token 过期时间'"},
                {"prev_token", "varchar(64) DEFAULT NULL COMMENT '上一次 Token（续期宽限）'"},
                {"prev_expire_at", "datetime DEFAULT NULL COMMENT '上一次 Token 过期时间'"}
        };
        for (String[] col : cols) {
            if (!hasColumn(col[0])) {
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE print_agent ADD COLUMN `" + col[0] + "` " + col[1]);
                    logger.info("print_agent 表新增列: " + col[0]);
                } catch (Exception e) {
                    logger.warn("print_agent 表新增列失败(忽略): " + col[0] + " - " + e.getMessage());
                }
            }
        }
        // 兼容旧数据：升级前已有 Token 的设备视为已配对，否则握手会被判为「待配对」而拒连
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            int updated = stmt.executeUpdate("UPDATE print_agent SET paired = '1' " +
                    "WHERE paired = '0' AND token IS NOT NULL AND token <> ''");
            if (updated > 0) {
                logger.info("print_agent 已配对状态回填: " + updated + " 台");
            }
        } catch (Exception e) {
            logger.warn("print_agent 已配对状态回填失败(忽略): " + e.getMessage());
        }
    }

    private boolean hasColumn(String column) {
        String sql = "SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
                "AND TABLE_NAME = 'print_agent' AND COLUMN_NAME = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.warn("检查 print_agent 列失败: " + column + " - " + e.getMessage());
            return false;
        }
    }

    private void createTaskTable() {
        String sql = "CREATE TABLE IF NOT EXISTS `print_task` (" +
                "`id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键'," +
                "`task_id` varchar(64) NOT NULL COMMENT '任务编号'," +
                "`device_id` varchar(64) DEFAULT NULL COMMENT '目标设备'," +
                "`printer_id` varchar(64) DEFAULT NULL COMMENT '目标打印机'," +
                "`status` varchar(20) DEFAULT 'pending' COMMENT '任务状态'," +
                "`total_bytes` int DEFAULT 0 COMMENT '数据字节数'," +
                "`sha256` varchar(64) DEFAULT NULL COMMENT '数据校验值'," +
                "`error_code` varchar(64) DEFAULT NULL COMMENT '错误码'," +
                "`message` varchar(500) DEFAULT NULL COMMENT '错误描述'," +
                "`created_at` datetime DEFAULT NULL COMMENT '创建时间'," +
                "`updated_at` datetime DEFAULT NULL COMMENT '更新时间'," +
                "`finished_at` datetime DEFAULT NULL COMMENT '结束时间'," +
                "PRIMARY KEY (`id`)," +
                "UNIQUE KEY `uk_task_id` (`task_id`)," +
                "KEY `idx_device` (`device_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='打印任务记录'";
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            logger.info("打印任务表 print_task 已就绪");
        } catch (Exception e) {
            logger.error("初始化打印任务表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 按设备编号查询代理（握手鉴权用）
     */
    public JSONObject findByDeviceId(String deviceId) {
        String sql = "SELECT * FROM print_agent WHERE device_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readAgent(rs);
                }
            }
        } catch (Exception e) {
            logger.error("查询打印代理失败: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 设备列表（含 token，页面需要展示给用户配置代理）
     */
    public List<JSONObject> listAgents() {
        List<JSONObject> list = new ArrayList<>();
        String sql = "SELECT * FROM print_agent ORDER BY id ASC";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(readAgent(rs));
            }
        } catch (Exception e) {
            logger.error("查询打印代理列表失败: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 保存设备：有 id 更新，无 id 新增；新增时未指定 token 自动生成
     */
    public Long saveAgent(JSONObject body) throws Exception {
        Long id = body.getLong("id");
        String deviceId = trim(body.getString("deviceId"));
        if (deviceId == null) {
            throw new IllegalArgumentException("设备编号不能为空");
        }
        String name = trim(body.getString("name"));
        String token = trim(body.getString("token"));
        String printers = printersText(body.get("printers"));
        String enabled = "0".equals(body.getString("enabled")) ? "0" : "1";
        try (Connection conn = dataSource.getConnection()) {
            if (id != null && id > 0) {
                if (token == null) {
                    JSONObject old = findById(id);
                    token = old == null ? generateToken() : old.getString("token");
                }
                String sql = "UPDATE print_agent SET device_id = ?, name = ?, token = ?, printers = ?, " +
                        "enabled = ?, updated_at = NOW() WHERE id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, deviceId);
                    ps.setString(2, name);
                    ps.setString(3, token);
                    ps.setString(4, printers);
                    ps.setString(5, enabled);
                    ps.setLong(6, id);
                    ps.executeUpdate();
                }
                return id;
            }
            if (findByDeviceId(deviceId) != null) {
                throw new IllegalArgumentException("设备编号已存在：" + deviceId);
            }
            if (token == null) {
                token = generateToken();
            }
            String sql = "INSERT INTO print_agent (device_id, name, token, printers, enabled, paired, revoked, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, '1', '0', NOW(), NOW())";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, deviceId);
                ps.setString(2, name);
                ps.setString(3, token);
                ps.setString(4, printers);
                ps.setString(5, enabled);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        }
        return null;
    }

    public JSONObject findById(Long id) {
        String sql = "SELECT * FROM print_agent WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readAgent(rs);
                }
            }
        } catch (Exception e) {
            logger.error("查询打印代理失败: " + e.getMessage(), e);
        }
        return null;
    }

    public boolean deleteAgent(Long id) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM print_agent WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("删除打印代理失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 重置设备 Token，返回新 Token
     */
    public String resetToken(Long id) throws Exception {
        String token = generateToken();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE print_agent SET token = ?, prev_token = NULL, prev_expire_at = NULL, " +
                             "token_expire_at = DATE_ADD(NOW(), INTERVAL 30 DAY), updated_at = NOW() WHERE id = ?")) {
            ps.setString(1, token);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
        return token;
    }

    /**
     * 更新设备最近连接信息（认证成功、状态上报时调用）
     */
    public void updateRuntime(String deviceId, String version, String clientIp, String printers) {
        StringBuilder sql = new StringBuilder("UPDATE print_agent SET last_seen = NOW(), updated_at = NOW()");
        List<Object> params = new ArrayList<>();
        if (version != null && !version.isEmpty()) {
            sql.append(", version = ?");
            params.add(version);
        }
        if (clientIp != null && !clientIp.isEmpty()) {
            sql.append(", client_ip = ?");
            params.add(clientIp);
        }
        if (printers != null && !printers.isEmpty()) {
            sql.append(", printers = ?");
            params.add(mergePrinterConfig(deviceId, printers));
        }
        sql.append(" WHERE device_id = ?");
        params.add(deviceId);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("更新打印代理在线信息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 代理上线时会上报自身打印机列表，这里把 ERP 侧维护的指令集/品牌合并回去，
     * 避免每次重连都覆盖掉用户设置的打印指令。
     */
    private String mergePrinterConfig(String deviceId, String incoming) {
        JSONObject agent = findByDeviceId(deviceId);
        if (agent == null) {
            return incoming;
        }
        JSONArray saved = agent.getJSONArray("printers");
        if (saved == null || saved.isEmpty()) {
            return incoming;
        }
        try {
            JSONArray runtime = JSONArray.parseArray(incoming);
            java.util.Map<String, JSONObject> savedMap = new java.util.HashMap<>();
            for (int i = 0; i < saved.size(); i++) {
                JSONObject item = saved.getJSONObject(i);
                String id = item.getString("id");
                if (id != null) {
                    savedMap.put(id, item);
                }
            }
            for (int i = 0; i < runtime.size(); i++) {
                JSONObject item = runtime.getJSONObject(i);
                JSONObject old = savedMap.get(item.getString("id"));
                if (old != null) {
                    if (old.getString("cmdSet") != null) {
                        item.put("cmdSet", old.getString("cmdSet"));
                    }
                    if (old.getString("brand") != null) {
                        item.put("brand", old.getString("brand"));
                    }
                }
            }
            return runtime.toJSONString();
        } catch (Exception e) {
            logger.error("合并打印机配置失败: " + e.getMessage());
            return incoming;
        }
    }

    // ------------------------------------------------------------------ 配对与凭据

    /**
     * 配对预登记：ERP 页面填写「设备ID + 配对码」后调用。
     * 设备此时还没有 Token，等代理拿配对码来换取；已存在的设备允许重新配对，旧 Token 立即作废。
     */
    public JSONObject preparePairing(String deviceId, String pairCode, int minutes) throws Exception {
        int expire = minutes > 0 ? minutes : 10;
        try (Connection conn = dataSource.getConnection()) {
            JSONObject old = findByDeviceId(deviceId);
            if (old == null) {
                String sql = "INSERT INTO print_agent (device_id, name, token, printers, enabled, paired, revoked, " +
                        "pair_code, pair_expire_at, created_at, updated_at) " +
                        "VALUES (?, ?, NULL, '[]', '1', '0', '0', ?, DATE_ADD(NOW(), INTERVAL ? MINUTE), NOW(), NOW())";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, deviceId);
                    ps.setString(2, deviceId);
                    ps.setString(3, pairCode);
                    ps.setInt(4, expire);
                    ps.executeUpdate();
                }
            } else {
                String sql = "UPDATE print_agent SET pair_code = ?, " +
                        "pair_expire_at = DATE_ADD(NOW(), INTERVAL ? MINUTE), paired = '0', revoked = '0', " +
                        "enabled = '1', token = NULL, prev_token = NULL, prev_expire_at = NULL, " +
                        "token_expire_at = NULL, updated_at = NOW() WHERE device_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, pairCode);
                    ps.setInt(2, expire);
                    ps.setString(3, deviceId);
                    ps.executeUpdate();
                }
            }
        }
        JSONObject agent = findByDeviceId(deviceId);
        JSONObject result = new JSONObject(true);
        result.put("deviceId", deviceId);
        result.put("pairExpireAt", agent == null ? null : agent.getString("pairExpireAt"));
        return result;
    }

    /**
     * 代理凭配对码换取 Token；成功返回设备信息（含新 Token）。
     * 条件全部写在 UPDATE 里，避免并发下重复配对。
     */
    public JSONObject confirmPairing(String deviceId, String pairCode, String serverUrl) {
        String token = generateToken();
        String sql = "UPDATE print_agent SET token = ?, paired = '1', pair_code = NULL, pair_expire_at = NULL, " +
                "server_url = ?, token_expire_at = DATE_ADD(NOW(), INTERVAL 30 DAY), " +
                "prev_token = NULL, prev_expire_at = NULL, last_seen = NOW(), updated_at = NOW() " +
                "WHERE device_id = ? AND pair_code = ? AND pair_expire_at > NOW() " +
                "AND enabled = '1' AND revoked = '0'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setString(2, serverUrl);
            ps.setString(3, deviceId);
            ps.setString(4, pairCode);
            if (ps.executeUpdate() == 0) {
                throw new IllegalArgumentException(pairFailureReason(deviceId, pairCode));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("设备配对失败: " + e.getMessage(), e);
            throw new IllegalStateException("配对失败：" + e.getMessage(), e);
        }
        JSONObject agent = findByDeviceId(deviceId);
        if (agent == null) {
            throw new IllegalStateException("配对失败：设备记录不存在");
        }
        agent.put("token", token);
        return agent;
    }

    /**
     * 配对失败原因，用于给设备端/页面返回可读提示
     */
    private String pairFailureReason(String deviceId, String pairCode) {
        JSONObject agent = findByDeviceId(deviceId);
        if (agent == null) {
            return "该设备ID未在 ERP 登记，请先在打印管理中填写设备ID和配对码";
        }
        if ("1".equals(agent.getString("revoked"))) {
            return "该设备已被吊销，请重新登记配对";
        }
        if (!"1".equals(agent.getString("enabled"))) {
            return "该设备已停用";
        }
        String sql = "SELECT pair_code, (pair_expire_at IS NOT NULL AND pair_expire_at > NOW()) AS alive " +
                "FROM print_agent WHERE device_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String saved = rs.getString("pair_code");
                    if (saved == null || saved.trim().isEmpty()) {
                        return "该设备尚未发起配对，请先在打印管理页填写设备ID和配对码";
                    }
                    if (!saved.equalsIgnoreCase(pairCode)) {
                        return "配对码不正确";
                    }
                    if (rs.getInt("alive") != 1) {
                        return "配对码已过期，请在设备端刷新配对码后重试";
                    }
                }
            }
        } catch (Exception e) {
            logger.error("查询配对状态失败: " + e.getMessage(), e);
        }
        return "配对失败，请重试";
    }

    /**
     * 校验设备 Token（WebSocket 握手、续期接口共用）
     *
     * @return ok / not_registered / revoked / disabled / pending / expired / wrong_token / error
     */
    public String checkToken(String deviceId, String token) {
        String sql = "SELECT token, prev_token, enabled, paired, revoked, " +
                "(token_expire_at IS NULL OR token_expire_at > NOW()) AS token_alive, " +
                "(prev_expire_at IS NULL OR prev_expire_at > NOW()) AS prev_alive " +
                "FROM print_agent WHERE device_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "not_registered";
                }
                if ("1".equals(rs.getString("revoked"))) {
                    return "revoked";
                }
                if (!"1".equals(rs.getString("enabled"))) {
                    return "disabled";
                }
                if (!"1".equals(rs.getString("paired"))) {
                    return "pending";
                }
                if (token == null || token.trim().isEmpty()) {
                    return "wrong_token";
                }
                String current = rs.getString("token");
                if (token.equals(current)) {
                    return rs.getInt("token_alive") == 1 ? "ok" : "expired";
                }
                String prev = rs.getString("prev_token");
                if (prev != null && token.equals(prev)) {
                    return rs.getInt("prev_alive") == 1 ? "ok" : "expired";
                }
                return "wrong_token";
            }
        } catch (Exception e) {
            logger.error("校验设备 Token 失败: " + e.getMessage(), e);
            return "error";
        }
    }

    /**
     * Token 续期：换发新 Token，旧 Token 保留 1 天宽限，避免响应丢失后设备被锁死
     */
    public String renewToken(String deviceId, String token) {
        String newToken = generateToken();
        String sql = "UPDATE print_agent SET prev_token = ?, " +
                "prev_expire_at = DATE_ADD(NOW(), INTERVAL 1 DAY), token = ?, " +
                "token_expire_at = DATE_ADD(NOW(), INTERVAL 30 DAY), last_seen = NOW(), updated_at = NOW() " +
                "WHERE device_id = ? AND (token = ? OR (prev_token = ? AND prev_expire_at > NOW())) " +
                "AND enabled = '1' AND revoked = '0' AND paired = '1'";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, token);
            ps.setString(2, newToken);
            ps.setString(3, deviceId);
            ps.setString(4, token);
            ps.setString(5, token);
            if (ps.executeUpdate() == 0) {
                return null;
            }
            return newToken;
        } catch (Exception e) {
            logger.error("设备 Token 续期失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 吊销设备：清空 Token 并标记，代理将无法再次连接
     */
    public boolean revokeAgent(String deviceId) {
        String sql = "UPDATE print_agent SET revoked = '1', paired = '0', token = NULL, prev_token = NULL, " +
                "prev_expire_at = NULL, token_expire_at = NULL, pair_code = NULL, pair_expire_at = NULL, " +
                "updated_at = NOW() WHERE device_id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            logger.error("吊销打印设备失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 记录配对后下发的服务端地址
     */
    public void updateServerUrl(String deviceId, String serverUrl) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE print_agent SET server_url = ?, updated_at = NOW() WHERE device_id = ?")) {
            ps.setString(1, serverUrl);
            ps.setString(2, deviceId);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.warn("记录打印代理服务端地址失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 任务

    public void createTask(String taskId, String deviceId, String printerId, int totalBytes, String sha256) throws Exception {
        String sql = "INSERT INTO print_task (task_id, device_id, printer_id, status, total_bytes, sha256, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'pending', ?, ?, NOW(), NOW())";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            ps.setString(2, deviceId);
            ps.setString(3, printerId);
            ps.setInt(4, totalBytes);
            ps.setString(5, sha256);
            ps.executeUpdate();
        }
    }

    /**
     * 更新任务状态；已到终态的任务不再改写，避免代理补报造成回退
     */
    public void updateTaskStatus(String taskId, String status, String errorCode, String message) {
        boolean finished = isFinal(status);
        String sql = "UPDATE print_task SET status = ?, error_code = ?, message = ?, updated_at = NOW()" +
                (finished ? ", finished_at = NOW()" : "") +
                " WHERE task_id = ? AND status NOT IN (" + FINAL_STATES + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, errorCode);
            ps.setString(3, message);
            ps.setString(4, taskId);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("更新打印任务状态失败: " + e.getMessage(), e);
        }
    }

    /**
     * 任务是否仍处于未完成状态（等待代理结果）
     */
    public boolean isUnfinished(String taskId) {
        String sql = "SELECT 1 FROM print_task WHERE task_id = ? AND status NOT IN (" + FINAL_STATES + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            logger.error("查询打印任务状态失败: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 连接断开时，把该设备未完成的任务统一置为失败
     */
    public void failUnfinishedByDevice(String deviceId, String status, String message) {
        String sql = "UPDATE print_task SET status = ?, error_code = ?, message = ?, updated_at = NOW(), finished_at = NOW() " +
                "WHERE device_id = ? AND status NOT IN (" + FINAL_STATES + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, status != null ? status.toUpperCase() : null);
            ps.setString(3, message);
            ps.setString(4, deviceId);
            ps.executeUpdate();
        } catch (Exception e) {
            logger.error("更新打印任务离线状态失败: " + e.getMessage(), e);
        }
    }

    public List<JSONObject> listTasks(String deviceId, int limit) {
        List<JSONObject> list = new ArrayList<>();
        int size = limit > 0 ? Math.min(limit, 500) : 50;
        String sql = "SELECT * FROM print_task" +
                (deviceId != null && !deviceId.isEmpty() ? " WHERE device_id = ?" : "") +
                " ORDER BY id DESC LIMIT ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            if (deviceId != null && !deviceId.isEmpty()) {
                ps.setString(index++, deviceId);
            }
            ps.setInt(index, size);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JSONObject item = new JSONObject();
                    item.put("taskId", rs.getString("task_id"));
                    item.put("deviceId", rs.getString("device_id"));
                    item.put("printerId", rs.getString("printer_id"));
                    item.put("status", rs.getString("status"));
                    item.put("totalBytes", rs.getInt("total_bytes"));
                    item.put("sha256", rs.getString("sha256"));
                    item.put("errorCode", rs.getString("error_code"));
                    item.put("message", rs.getString("message"));
                    item.put("createdAt", rs.getString("created_at"));
                    item.put("finishedAt", rs.getString("finished_at"));
                    list.add(item);
                }
            }
        } catch (Exception e) {
            logger.error("查询打印任务失败: " + e.getMessage(), e);
        }
        return list;
    }

    // ------------------------------------------------------------------ 工具

    private JSONObject readAgent(ResultSet rs) throws Exception {
        JSONObject item = new JSONObject();
        item.put("id", rs.getLong("id"));
        item.put("deviceId", rs.getString("device_id"));
        item.put("name", rs.getString("name"));
        item.put("token", rs.getString("token"));
        item.put("enabled", rs.getString("enabled"));
        item.put("paired", "1".equals(rs.getString("paired")) ? "1" : "0");
        item.put("revoked", "1".equals(rs.getString("revoked")) ? "1" : "0");
        item.put("serverUrl", rs.getString("server_url"));
        item.put("tokenExpireAt", rs.getString("token_expire_at"));
        item.put("pairExpireAt", rs.getString("pair_expire_at"));
        item.put("version", rs.getString("version"));
        item.put("clientIp", rs.getString("client_ip"));
        item.put("lastSeen", rs.getString("last_seen"));
        item.put("createdAt", rs.getString("created_at"));
        String printers = rs.getString("printers");
        if (printers != null && !printers.trim().isEmpty()) {
            try {
                item.put("printers", JSONArray.parseArray(printers));
            } catch (Exception e) {
                item.put("printers", new JSONArray());
            }
        } else {
            item.put("printers", new JSONArray());
        }
        return item;
    }

    private String printersText(Object printers) {
        if (printers == null) {
            return "[]";
        }
        if (printers instanceof String) {
            return (String) printers;
        }
        return JSONObject.toJSONString(printers);
    }

    public static String generateToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean isFinal(String status) {
        if (status == null) {
            return false;
        }
        return "success".equals(status) || "failed".equals(status) || "timeout".equals(status)
                || "offline".equals(status) || "cancelled".equals(status) || "rejected".equals(status);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
