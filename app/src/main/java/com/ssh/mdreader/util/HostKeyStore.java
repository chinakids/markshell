package com.ssh.mdreader.util;

import androidx.annotation.Nullable;

/**
 * 主机指纹存储接口：按 {@code host+port} 记录（主机密钥属于服务器，与 username 无关）。
 * 由 {@link PreferenceManager} 实现（SharedPreferences + JSON），注入 {@code SshManager}
 * 用于连接时校验（TOFU / 一致放行 / 变更警告）。
 */
public interface HostKeyStore {

    /** 返回该主机已记录的规范指纹；从未记录返回 {@code null}。 */
    @Nullable
    String getFingerprint(String host, int port);

    /** 记录/覆盖该主机指纹（内部会规范化后存储）。 */
    void saveFingerprint(String host, int port, String fingerprint);
}
