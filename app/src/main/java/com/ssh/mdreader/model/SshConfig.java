package com.ssh.mdreader.model;

public class SshConfig {
    private String alias;
    private String host;
    private int port;
    private String username;
    private String password;
    private String remotePath;

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

    public String getDisplayName() {
        if (alias != null && !alias.isEmpty()) return alias;
        return host + ":" + port;
    }

    public boolean isValid() {
        return host != null && !host.isEmpty()
                && username != null && !username.isEmpty()
                && password != null && !password.isEmpty()
                && port > 0 && port <= 65535;
    }
}
