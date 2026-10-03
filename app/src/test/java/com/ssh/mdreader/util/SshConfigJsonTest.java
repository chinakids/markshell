package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.SshConfig;

import org.json.JSONObject;
import org.junit.Test;

/**
 * {@link SshConfigJson} 序列化/旧数据兼容单测（使用真实 org.json：仅 JVM 测试依赖）。
 */
public class SshConfigJsonTest {

    private static final String PEM =
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----\n";

    @Test
    public void roundTrip_preservesAllFields() throws Exception {
        SshConfig c = new SshConfig("alias", "10.0.0.5", 2222, "user", "pw", "/home/user");
        c.setAuthMode(SshConfig.AUTH_KEY);
        c.setPrivateKey(PEM);
        c.setKeyPassphrase("p@ss 口令");

        SshConfig out = SshConfigJson.fromJson(SshConfigJson.toJson(c));
        assertEquals("alias", out.getAlias());
        assertEquals("10.0.0.5", out.getHost());
        assertEquals(2222, out.getPort());
        assertEquals("user", out.getUsername());
        assertEquals("pw", out.getPassword());
        assertEquals("/home/user", out.getRemotePath());
        assertEquals(SshConfig.AUTH_KEY, out.getAuthMode());
        assertEquals(PEM, out.getPrivateKey());
        assertEquals("p@ss 口令", out.getKeyPassphrase());
    }

    @Test
    public void fromJson_legacyData_defaultsAuthModeToPassword() throws Exception {
        // 旧版本数据结构：无 authMode/privateKey/keyPassphrase 字段
        JSONObject legacy = new JSONObject()
                .put("alias", "a")
                .put("host", "h")
                .put("port", 22)
                .put("username", "u")
                .put("password", "enc$xxx")
                .put("remotePath", "/");
        SshConfig c = SshConfigJson.fromJson(legacy);
        assertEquals(SshConfig.AUTH_PASSWORD, c.getAuthMode());
        assertEquals("", c.getPrivateKey());
        assertEquals("", c.getKeyPassphrase());
        assertEquals("h", c.getHost());
        assertEquals("u", c.getUsername());
    }

    @Test
    public void fromJson_invalidAuthMode_fallsBackToPassword() throws Exception {
        JSONObject obj = new JSONObject()
                .put("host", "h").put("username", "u").put("authMode", "weird");
        assertEquals(SshConfig.AUTH_PASSWORD, SshConfigJson.fromJson(obj).getAuthMode());
    }

    @Test
    public void fromJson_missingBaseFields_takeDefaults() throws Exception {
        SshConfig c = SshConfigJson.fromJson(new JSONObject());
        assertEquals("", c.getHost());
        assertEquals("", c.getPassword());
        assertEquals(22, c.getPort());
        assertEquals("/", c.getRemotePath());
        assertEquals(SshConfig.AUTH_PASSWORD, c.getAuthMode());
    }

    @Test
    public void toJson_containsAuthFields() throws Exception {
        SshConfig c = new SshConfig("a", "h", 22, "u", "p", "/");
        c.setAuthMode(SshConfig.AUTH_KEY);
        c.setPrivateKey(PEM);
        c.setKeyPassphrase("pp");
        JSONObject obj = SshConfigJson.toJson(c);
        assertEquals(SshConfig.AUTH_KEY, obj.getString("authMode"));
        assertEquals(PEM, obj.getString("privateKey"));
        assertEquals("pp", obj.getString("keyPassphrase"));
    }

    @Test
    public void hasLegacyPlainSecret_detectsOnlyPlainNonEmpty() throws Exception {
        JSONObject plain = new JSONObject().put("privateKey", "-----BEGIN...");
        assertTrue(SshConfigJson.hasLegacyPlainSecret(plain, "privateKey"));
        JSONObject encrypted = new JSONObject().put("privateKey", "enc$AAAA");
        assertFalse(SshConfigJson.hasLegacyPlainSecret(encrypted, "privateKey"));
        JSONObject empty = new JSONObject().put("privateKey", "");
        assertFalse(SshConfigJson.hasLegacyPlainSecret(empty, "privateKey"));
        JSONObject missing = new JSONObject();
        assertFalse(SshConfigJson.hasLegacyPlainSecret(missing, "privateKey"));
    }

    @Test
    public void roundTrip_preservesGroup() throws Exception {
        SshConfig c = new SshConfig("a", "h", 22, "u", "p", "/");
        c.setGroup("生产环境");
        SshConfig out = SshConfigJson.fromJson(SshConfigJson.toJson(c));
        assertEquals("生产环境", out.getGroup());
        // 未分组：不落 key（旧数据兼容），读出为空串
        c.setGroup(null);
        assertFalse(SshConfigJson.toJson(c).has("group"));
        assertEquals("", SshConfigJson.fromJson(SshConfigJson.toJson(c)).getGroup());
        assertEquals("", SshConfigJson.fromJson(new JSONObject()).getGroup());
    }

    @Test
    public void fromJson_legacyData_missingGroupBecomesEmpty() throws Exception {
        JSONObject legacy = new JSONObject()
                .put("alias", "a")
                .put("host", "h")
                .put("port", 22)
                .put("username", "u")
                .put("password", "enc$xxx")
                .put("remotePath", "/");
        assertEquals("", SshConfigJson.fromJson(legacy).getGroup());
    }
}
