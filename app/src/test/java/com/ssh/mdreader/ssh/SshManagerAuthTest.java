package com.ssh.mdreader.ssh;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.ssh.mdreader.model.SshConfig;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * {@link SshManager} 密钥认证分支选择单测：用 JSch 子类截获 addIdentity 参数，
 * 不触发真实密钥解析（无需真实 PEM 密钥文件）。
 */
public class SshManagerAuthTest {

    private static final String PEM =
            "-----BEGIN RSA PRIVATE KEY-----\nMIIEpQIBAAKCAQEA...\n-----END RSA PRIVATE KEY-----\n";

    /** 截获 addIdentity 调用的 JSch 子类。 */
    private static class RecorderJSch extends JSch {
        boolean called;
        String name;
        byte[] prv;
        byte[] pub;
        byte[] pass;

        @Override
        public void addIdentity(String name, byte[] prvkey, byte[] pubkey, byte[] passphrase)
                throws JSchException {
            this.called = true;
            this.name = name;
            this.prv = prvkey;
            this.pub = pubkey;
            this.pass = passphrase;
        }
    }

    private static SshConfig keyConfig(String passphrase) {
        SshConfig c = new SshConfig("a", "h", 22, "u", null, "/");
        c.setAuthMode(SshConfig.AUTH_KEY);
        c.setPrivateKey(PEM);
        c.setKeyPassphrase(passphrase);
        return c;
    }

    @Test
    public void configureAuth_keyMode_injectsPemBytes() throws Exception {
        RecorderJSch jsch = new RecorderJSch();
        SshManager.configureAuth(jsch, keyConfig(null));

        assertTrue("私钥模式应调用 addIdentity", jsch.called);
        assertEquals(SshManager.KEY_AUTH_IDENTITY_NAME, jsch.name);
        assertArrayEquals(PEM.getBytes(StandardCharsets.UTF_8), jsch.prv);
        assertNull("公钥应由 JSch 从私钥推导", jsch.pub);
        assertNull("无口令时应传 null", jsch.pass);
    }

    @Test
    public void configureAuth_keyMode_withPassphrase_passesBytes() throws Exception {
        RecorderJSch jsch = new RecorderJSch();
        SshManager.configureAuth(jsch, keyConfig("p@ss 口令"));

        assertTrue(jsch.called);
        assertArrayEquals("p@ss 口令".getBytes(StandardCharsets.UTF_8), jsch.pass);
    }

    @Test
    public void configureAuth_keyMode_emptyPassphrase_passesNull() throws Exception {
        RecorderJSch jsch = new RecorderJSch();
        SshManager.configureAuth(jsch, keyConfig(""));

        assertTrue(jsch.called);
        assertNull(jsch.pass);
    }

    @Test
    public void configureAuth_passwordMode_doesNothing() throws Exception {
        RecorderJSch jsch = new RecorderJSch();
        SshConfig c = new SshConfig("a", "h", 22, "u", "pw", "/");
        SshManager.configureAuth(jsch, c);

        assertFalse("密码模式不应调用 addIdentity", jsch.called);
    }

    @Test
    public void configureAuth_nullConfig_noThrow() throws Exception {
        RecorderJSch jsch = new RecorderJSch();
        SshManager.configureAuth(jsch, null);
        assertFalse(jsch.called);
    }

    @Test
    public void configureAuth_keyModeWithoutPrivateKey_noThrow() throws Exception {
        RecorderJSch jsch = new RecorderJSch();
        SshConfig c = keyConfig(null);
        c.setPrivateKey("");
        SshManager.configureAuth(jsch, c);
        assertFalse("私钥为空不应注册身份（连接将失败于服务端认证）", jsch.called);
    }

    @Test
    public void usesPasswordAuth_reflectsMode() {
        SshConfig password = new SshConfig("a", "h", 22, "u", "pw", "/");
        assertTrue(SshManager.usesPasswordAuth(password));
        assertFalse(SshManager.usesPasswordAuth(keyConfig(null)));
        assertTrue("null config 保守走密码语义", SshManager.usesPasswordAuth(null));
    }

    @Test
    public void keyPassphraseBytes_emptyIsNull() {
        assertNull(SshManager.keyPassphraseBytes(null));
        assertNull(SshManager.keyPassphraseBytes(""));
        assertArrayEquals("pp".getBytes(StandardCharsets.UTF_8),
                SshManager.keyPassphraseBytes("pp"));
    }
}
