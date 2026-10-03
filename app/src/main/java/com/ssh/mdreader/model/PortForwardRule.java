package com.ssh.mdreader.model;

/**
 * 本地端口转发（隧道）规则模型：把服务器侧 {@code remoteHost:remotePort} 经 SSH 隧道
 * 暴露到本机 {@code bindAddress:localPort}。
 *
 * <p>规则字段：名称（可选，仅展示用）、本地端口、远端主机、远端端口、绑定地址（可选）。
 * 归属=服务器（按连接键 host:port:user 隔离，见 {@link com.ssh.mdreader.util.SshConnectionHelper#deriveConnectionKey}），
 * 不随连接配置存储（规则属于服务器档案，与目录书签同范式，避免与 SshConfigJson 兼容逻辑纠缠）。</p>
 */
public class PortForwardRule {

    private String name;
    private int localPort;
    private String remoteHost;
    private int remotePort;
    private String bindAddress;

    public PortForwardRule() {
    }

    public PortForwardRule(String name, int localPort, String remoteHost, int remotePort, String bindAddress) {
        this.name = name;
        this.localPort = localPort;
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.bindAddress = bindAddress;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getLocalPort() { return localPort; }
    public void setLocalPort(int localPort) { this.localPort = localPort; }

    public String getRemoteHost() { return remoteHost; }
    public void setRemoteHost(String remoteHost) { this.remoteHost = remoteHost; }

    public int getRemotePort() { return remotePort; }
    public void setRemotePort(int remotePort) { this.remotePort = remotePort; }

    /** 绑定地址（监听接口）；null/空白=未填写（应用时回退 {@link com.ssh.mdreader.util.PortForwardHelper#DEFAULT_BIND_ADDRESS}）。 */
    public String getBindAddress() { return bindAddress; }
    public void setBindAddress(String bindAddress) { this.bindAddress = bindAddress; }
}
