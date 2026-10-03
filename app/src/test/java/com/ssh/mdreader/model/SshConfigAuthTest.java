package com.ssh.mdreader.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link SshConfig} 认证方式相关纯逻辑单测（authMode 缺省/过滤/校验）。
 */
public class SshConfigAuthTest {

    private static SshConfig baseValid() {
        SshConfig c = new SshConfig("alias", "192.168.1.10", 22, "root", "secret", "/");
        return c;
    }

    @Test
    public void defaultValue_authModeIsPassword() {
        SshConfig c = new SshConfig();
        assertEquals(SshConfig.AUTH_PASSWORD, c.getAuthMode());
        assertFalse(c.isKeyAuth());
    }

    @Test
    public void authModeOrDefault_passesValidModesThrough() {
        assertEquals(SshConfig.AUTH_PASSWORD, SshConfig.authModeOrDefault("password"));
        assertEquals(SshConfig.AUTH_KEY, SshConfig.authModeOrDefault("key"));
    }

    @Test
    public void authModeOrDefault_invalidFallsBackToPassword() {
        assertEquals(SshConfig.AUTH_PASSWORD, SshConfig.authModeOrDefault(null));
        assertEquals(SshConfig.AUTH_PASSWORD, SshConfig.authModeOrDefault(""));
        assertEquals(SshConfig.AUTH_PASSWORD, SshConfig.authModeOrDefault("Key"));
        assertEquals(SshConfig.AUTH_PASSWORD, SshConfig.authModeOrDefault("token"));
    }

    @Test
    public void isValidAuthMode_onlyPasswordAndKey() {
        assertTrue(SshConfig.isValidAuthMode("password"));
        assertTrue(SshConfig.isValidAuthMode("key"));
        assertFalse(SshConfig.isValidAuthMode(""));
        assertFalse(SshConfig.isValidAuthMode(null));
        assertFalse(SshConfig.isValidAuthMode("KEY"));
    }

    @Test
    public void isValid_keyModeRequiresPrivateKey() {
        SshConfig c = baseValid();
        c.setAuthMode(SshConfig.AUTH_KEY);
        assertFalse("私钥模式缺私钥应无效", c.isValid());
        c.setPrivateKey("-----BEGIN RSA PRIVATE KEY-----\nabc\n-----END RSA PRIVATE KEY-----");
        assertTrue("私钥模式带私钥应有效", c.isValid());
    }

    @Test
    public void isValid_passwordModeRequiresPassword() {
        SshConfig c = baseValid();
        assertTrue("密码模式带密码应有效", c.isValid());
        c.setPassword("");
        assertFalse("密码模式缺密码应无效", c.isValid());
    }

    @Test
    public void isValid_rejectsIncompleteBaseRegardlessOfMode() {
        SshConfig c = baseValid();
        c.setHost("");
        c.setAuthMode(SshConfig.AUTH_KEY);
        c.setPrivateKey("x");
        assertFalse("缺 host 即使私钥齐全也应无效", c.isValid());
        c.setHost("1.1.1.1");
        c.setUsername("");
        assertFalse("缺 username 应无效", c.isValid());
    }

    @Test
    public void isKeyAuth_reflectsMode() {
        SshConfig c = baseValid();
        c.setAuthMode(SshConfig.AUTH_KEY);
        assertTrue(c.isKeyAuth());
        c.setAuthMode(SshConfig.AUTH_PASSWORD);
        assertFalse(c.isKeyAuth());
    }
}
