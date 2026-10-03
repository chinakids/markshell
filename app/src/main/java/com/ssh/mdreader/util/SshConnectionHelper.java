package com.ssh.mdreader.util;

import com.ssh.mdreader.model.SshConfig;

import java.util.Objects;

/**
 * 连接标识/复用判定纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p>背景：SshManager 为单例单连接（一个活跃连接服务全部 Activity）。多会话并发
 * （N 个 SshTransport 同时存活）经 A4 后续设计评审判定收益＜成本、暂不引入；但
 * 「同目标幂等 connect（复用存活连接，不 teardown 重建）」是确定要做的连接复用
 * 优化，且正是未来多会话注册表（Map&lt;key, transport&gt; + activeKey）的同一套
 * 规范化/归属判定原语。本类即该原语层——连接键派生、认证要素等价、复用判定。</p>
 *
 * <p>语义约定：
 * <ul>
 *   <li>连接键 = {@code host:port:user}——host 视为 DNS/主机名大小写不敏感，规范化=trim+小写；
 *       username 大小写敏感，仅 trim；port 取实际值（默认 22 也是 22）。alias/group/path 不入键。</li>
 *   <li>复用判定必须同时满足：连接存活 && 同连接键 && 认证要素等价（authMode/password/privateKey/
 *       keyPassphrase）——凭据变更（如修正密码）必须走重新建连，不得复用旧会话。</li>
 *   <li>本层不感知心跳/指纹等 manager 级状态；心跳一致性由 SshManager 的 {@code reuseEligible} 组合。</li>
 * </ul>
 */
public final class SshConnectionHelper {

    private SshConnectionHelper() {}

    /**
     * 派生规范连接键 {@code host:port:user}（host trim+小写，user trim 保留大小写，port 原样）。
     * host/username 为空或 port 越界（&lt;=0 或 &gt;65535）返回 null（基础无效，不能作为键）。
     */
    public static String deriveConnectionKey(SshConfig config) {
        if (config == null) return null;
        String host = config.getHost();
        String user = config.getUsername();
        if (host == null || host.trim().isEmpty()) return null;
        if (user == null || user.trim().isEmpty()) return null;
        int port = config.getPort();
        if (port <= 0 || port > 65535) return null;
        return host.trim().toLowerCase() + ":" + port + ":" + user.trim();
    }

    /**
     * 两连接的认证要素是否等价（authMode + password + privateKey + keyPassphrase 逐项
     * null 安全比较）。同 key 不足以复用会话——凭据变更（用户修正了密码/密钥）必须重连；
     * 本方法即该等价判定。null 入参（任一）返回 false。
     */
    public static boolean sameAuthCredentials(SshConfig a, SshConfig b) {
        if (a == null || b == null) return false;
        if (a.isKeyAuth() != b.isKeyAuth()) return false;
        if (!Objects.equals(a.getPassword(), b.getPassword())) return false;
        if (!Objects.equals(a.getPrivateKey(), b.getPrivateKey())) return false;
        return Objects.equals(a.getKeyPassphrase(), b.getKeyPassphrase());
    }

    /**
     * 复用判定（幂等 connect 的核心谓词，也是未来多会话注册表「同连接归属」判定）。
     * 仅当 {@code alive=true} 且两配置连接键相同且认证要素等价时返回 true；
     * 任何条件不满足（含 null 入参、任意一侧键派生失败）返回 false = 必须重新建连。
     */
    public static boolean shouldReuseConnection(SshConfig existing, SshConfig requested, boolean alive) {
        if (!alive || existing == null || requested == null) return false;
        String a = deriveConnectionKey(existing);
        String b = deriveConnectionKey(requested);
        if (a == null || b == null || !a.equals(b)) return false;
        return sameAuthCredentials(existing, requested);
    }
}
