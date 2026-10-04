package com.ssh.mdreader.util;

/**
 * 连接表单校验纯函数层（零 android.*，JVM 可测）。
 *
 * <p>单一语义源：{@link com.ssh.mdreader.model.SshConfig#isValid()} 的端口判定与两处表单
 * （ConnectionActivity 连接表单 / MainActivity 快速连接对话框）的端口解析均委托本类，
 * 消除「模型契约 vs 表单校验」逻辑漂移（此前 isValid 全仓 0 调用=死代码，表单仅
 * Integer.parseInt 无范围预检，0/-1/99999 均可保存后连接失败）。竞品 Termius
 * strings.xml {@code incorrect_port_value_error}「端口值不正确」=SSH 客户端表单端口
 * 预校验标配。</p>
 */
public final class ConnectionFormHelper {

    /** 缺省 SSH 端口。 */
    public static final int DEFAULT_PORT = 22;

    /** 端口非法哨兵（与 GoToLineHelper.parseLineNumber 的 -1 哨兵同型）。 */
    public static final int INVALID_PORT = -1;

    private ConnectionFormHelper() {
    }

    /**
     * 解析表单端口字符串。
     *
     * <p>null/空白 → {@code defaultPort}；trim 后非数字、整数溢出或越界（&lt;1 或 &gt;65535）
     * → {@link #INVALID_PORT}。</p>
     */
    public static int parsePort(String raw, int defaultPort) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultPort;
        }
        int port;
        try {
            port = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return INVALID_PORT;
        }
        return isValidPort(port) ? port : INVALID_PORT;
    }

    /** 端口合法范围：1-65535（与 {@link com.ssh.mdreader.model.SshConfig#isValid()} 契约一致）。 */
    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }

    /** 主机名规范化：null → ""；其余去首尾空白（幂等）。 */
    public static String normalizeHost(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
