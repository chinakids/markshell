package com.ssh.mdreader.util;

import com.ssh.mdreader.model.SshConfig;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * {@link SshConfig} 与 JSON 的纯函数编解码层（无 Android 依赖，可 JVM 单测）。
 *
 * <p>只负责「字段原样读写与缺省兼容」，不承担加密：私钥/口令/密码的加密落盘由
 * {@link PreferenceManager} 统一处理（AES-256-GCM，见 {@link CredentialCrypto}）。
 * 这样序列化语义（含旧数据兼容）可在 JVM 侧测试，Android 侧只负责密钥供给。</p>
 *
 * <p>兼容约定：缺失字段一律取缺省值（authMode→{@link SshConfig#AUTH_PASSWORD}、
 * privateKey/keyPassphrase→""），旧版本保存的数据无需迁移即可读取。</p>
 */
public final class SshConfigJson {

    private SshConfigJson() {}

    /**
     * 序列化为 JSON 对象。注意：调用方需自行先把敏感字段加密（本类原样写入，
     * 保证「密文不落地」的时机由 PreferenceManager 控制）。
     */
    public static JSONObject toJson(SshConfig config) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("alias", config.getAlias());
        obj.put("host", config.getHost());
        obj.put("port", config.getPort());
        obj.put("username", config.getUsername());
        obj.put("password", config.getPassword());
        obj.put("remotePath", config.getRemotePath());
        obj.put("authMode", config.getAuthMode());
        obj.put("privateKey", config.getPrivateKey());
        obj.put("keyPassphrase", config.getKeyPassphrase());
        // group 为 null/空白=未分组：不落 key（旧数据自然兼容）
        if (config.getGroup() != null && !config.getGroup().isEmpty()) {
            obj.put("group", config.getGroup());
        }
        return obj;
    }

    /**
     * 反序列化（旧数据兼容：缺省字段取默认值）。敏感字段原样读入（可能是密文），
     * 解密由 PreferenceManager（readDecryptedSecret）负责。
     */
    public static SshConfig fromJson(JSONObject obj) {
        SshConfig config = new SshConfig();
        config.setAlias(obj.optString("alias", ""));
        config.setHost(obj.optString("host", ""));
        config.setPort(obj.optInt("port", 22));
        config.setUsername(obj.optString("username", ""));
        config.setPassword(obj.optString("password", ""));
        config.setRemotePath(obj.optString("remotePath", "/"));
        config.setAuthMode(SshConfig.authModeOrDefault(obj.optString("authMode", "")));
        config.setPrivateKey(obj.optString("privateKey", ""));
        config.setKeyPassphrase(obj.optString("keyPassphrase", ""));
        // group：旧数据缺省 ""=未分组
        config.setGroup(obj.optString("group", ""));
        return config;
    }

    /** 字段是否为「残留旧明文」（非空且无加密前缀），供一次性迁移判定。 */
    public static boolean hasLegacyPlainSecret(JSONObject obj, String field) {
        String raw = obj.optString(field, "");
        return !raw.isEmpty() && !CredentialCrypto.isEncrypted(raw);
    }
}
