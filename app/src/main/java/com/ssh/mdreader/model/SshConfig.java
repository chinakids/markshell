package com.ssh.mdreader.model;

import com.ssh.mdreader.util.ConnectionFormHelper;

public class SshConfig {
    /** 认证方式：密码认证（默认，兼容旧数据）。 */
    public static final String AUTH_PASSWORD = "password";
    /** 认证方式：私钥认证（PEM 私钥 + 可选口令）。 */
    public static final String AUTH_KEY = "key";

    private String alias;
    private String host;
    private int port;
    private String username;
    private String password;
    private String remotePath;
    /** 认证方式，取值 {@link #AUTH_PASSWORD} / {@link #AUTH_KEY}；旧数据缺省=password。 */
    private String authMode = AUTH_PASSWORD;
    /** 私钥 PEM 全文（内存态明文；落盘由 PreferenceManager 加密，断不可明文存储）。 */
    private String privateKey;
    /** 私钥口令（可选；仅加密私钥使用）。 */
    private String keyPassphrase;
    /** 所属分组名；null/空白=未分组（旧数据兼容）。 */
    private String group;

    public SshConfig() {
        this.port = 22;
        this.remotePath = "/";
    }

    public SshConfig(String alias, String host, int port, String username, String password, String remotePath) {
        this.alias = alias;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.remotePath = remotePath;
    }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRemotePath() { return remotePath; }
    public void setRemotePath(String remotePath) { this.remotePath = remotePath; }

    public String getAuthMode() { return authMode; }
    public void setAuthMode(String authMode) { this.authMode = authMode; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public String getKeyPassphrase() { return keyPassphrase; }
    public void setKeyPassphrase(String keyPassphrase) { this.keyPassphrase = keyPassphrase; }

    /** 所属分组名；null/空白=未分组。 */
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }

    /** 是否私钥认证模式（authMode=key）。 */
    public boolean isKeyAuth() {
        return AUTH_KEY.equals(authMode);
    }

    /** 认证方式取值是否合法。 */
    public static boolean isValidAuthMode(String mode) {
        return AUTH_PASSWORD.equals(mode) || AUTH_KEY.equals(mode);
    }

    /** 读取持久化的 authMode 并过滤：非法/缺省值一律回退 password（旧数据兼容）。 */
    public static String authModeOrDefault(String stored) {
        return isValidAuthMode(stored) ? stored : AUTH_PASSWORD;
    }

    public String getDisplayName() {
        if (alias != null && !alias.isEmpty()) return alias;
        return host + ":" + port;
    }

    /**
     * 连接配置可用性校验：base（host/username/port）必须有效；认证凭据按模式校验——
     * 私钥模式要求 privateKey 非空（口令可选）；密码模式要求 password 非空。
     * 端口判定委托 {@link ConnectionFormHelper#isValidPort}（与表单预检单一语义源）。
     */
    public boolean isValid() {
        if (host == null || host.isEmpty()
                || username == null || username.isEmpty()
                || !ConnectionFormHelper.isValidPort(port)) {
            return false;
        }
        if (isKeyAuth()) {
            return privateKey != null && !privateKey.isEmpty();
        }
        return password != null && !password.isEmpty();
    }
}
