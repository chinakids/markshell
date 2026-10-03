package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.SshConfig;

import org.junit.Test;

/**
 * {@link SshConnectionHelper} 连接键派生/认证要素等价/复用判定纯函数层单测。
 * 键语义：host:port:user（host trim+小写不敏感，user trim 保留大小写，port 原样）；
 * 复用=连接存活 && 同键 && 凭据等价（authMode/password/privateKey/keyPassphrase）。
 */
public class SshConnectionHelperTest {

    private static SshConfig conn(String host, int port, String user, String pass) {
        return new SshConfig("alias", host, port, user, pass, "/");
    }

    private static SshConfig keyConn(String host, int port, String user, String privateKey, String passphrase) {
        SshConfig c = new SshConfig("alias", host, port, user, null, "/");
        c.setAuthMode(SshConfig.AUTH_KEY);
        c.setPrivateKey(privateKey);
        c.setKeyPassphrase(passphrase);
        return c;
    }

    // ── 连接键派生 ───────────────────────────────────────────────────────────

    @Test
    public void deriveKey_usesHostPortUser() {
        assertEquals("192.168.1.10:22:root",
                SshConnectionHelper.deriveConnectionKey(conn("192.168.1.10", 22, "root", "p")));
        assertEquals("example.com:2222:admin",
                SshConnectionHelper.deriveConnectionKey(conn("example.com", 2222, "admin", "p")));
    }

    @Test
    public void deriveKey_normalizesHostButNotUser() {
        // host 大小写不敏感（DNS 语义）→ 规范化小写；username 大小写敏感 → 原样保留
        assertEquals("host.example:22:AppUser",
                SshConnectionHelper.deriveConnectionKey(conn("  Host.EXAMPLE  ", 22, "  AppUser  ", "p")));
    }

    @Test
    public void deriveKey_rejectsInvalidBase() {
        assertNull(SshConnectionHelper.deriveConnectionKey(null));
        assertNull(SshConnectionHelper.deriveConnectionKey(conn("", 22, "u", "p")));
        assertNull(SshConnectionHelper.deriveConnectionKey(conn("   ", 22, "u", "p")));
        assertNull(SshConnectionHelper.deriveConnectionKey(conn("h", 22, null, "p")));
        assertNull(SshConnectionHelper.deriveConnectionKey(conn("h", 22, "  ", "p")));
        assertNull(SshConnectionHelper.deriveConnectionKey(conn("h", 0, "u", "p")));
        assertNull(SshConnectionHelper.deriveConnectionKey(conn("h", -1, "u", "p")));
        assertNull(SshConnectionHelper.deriveConnectionKey(conn("h", 65536, "u", "p")));
    }

    // ── 认证要素等价 ─────────────────────────────────────────────────────────

    @Test
    public void sameAuthCredentials_equalPasswordMode() {
        assertTrue(SshConnectionHelper.sameAuthCredentials(
                conn("h", 22, "u", "pass"), conn("h", 22, "u", "pass")));
    }

    @Test
    public void sameAuthCredentials_rejectsNullAndMismatch() {
        assertFalse(SshConnectionHelper.sameAuthCredentials(null, conn("h", 22, "u", "p")));
        assertFalse(SshConnectionHelper.sameAuthCredentials(conn("h", 22, "u", "p"), null));
        // 密码不同（同一目标修正了凭据 → 必须重连）
        assertFalse(SshConnectionHelper.sameAuthCredentials(
                conn("h", 22, "u", "pass1"), conn("h", 22, "u", "pass2")));
        // 认证方式不一致
        assertFalse(SshConnectionHelper.sameAuthCredentials(
                conn("h", 22, "u", "pass"), keyConn("h", 22, "u", "KEY", null)));
    }

    @Test
    public void sameAuthCredentials_equalKeyMode() {
        assertTrue(SshConnectionHelper.sameAuthCredentials(
                keyConn("h", 22, "u", "PEM", "ph"), keyConn("h", 22, "u", "PEM", "ph")));
        // 口令可选：null 与 null 等价
        assertTrue(SshConnectionHelper.sameAuthCredentials(
                keyConn("h", 22, "u", "PEM", null), keyConn("h", 22, "u", "PEM", null)));
        // 私钥不同 → 不等价
        assertFalse(SshConnectionHelper.sameAuthCredentials(
                keyConn("h", 22, "u", "PEM1", null), keyConn("h", 22, "u", "PEM2", null)));
        // 口令不同 → 不等价
        assertFalse(SshConnectionHelper.sameAuthCredentials(
                keyConn("h", 22, "u", "PEM", "a"), keyConn("h", 22, "u", "PEM", "b")));
    }

    // ── 复用判定 ─────────────────────────────────────────────────────────────

    @Test
    public void shouldReuse_aliveSameKeySameCreds() {
        assertTrue(SshConnectionHelper.shouldReuseConnection(
                conn("h", 22, "u", "p"), conn("h", 22, "u", "p"), true));
        // host 大小写不同、端口/用户名一致仍视为同一目标
        assertTrue(SshConnectionHelper.shouldReuseConnection(
                conn("H", 22, "u", "p"), conn("h", 22, "u", "p"), true));
    }

    @Test
    public void shouldReuse_rejectsDeadNullAndMismatch() {
        assertFalse(SshConnectionHelper.shouldReuseConnection(
                conn("h", 22, "u", "p"), conn("h", 22, "u", "p"), false));
        assertFalse(SshConnectionHelper.shouldReuseConnection(
                null, conn("h", 22, "u", "p"), true));
        assertFalse(SshConnectionHelper.shouldReuseConnection(
                conn("h", 22, "u", "p"), null, true));
        // 不同 host / port / username（用户名大小写敏感）
        assertFalse(SshConnectionHelper.shouldReuseConnection(
                conn("h1", 22, "u", "p"), conn("h2", 22, "u", "p"), true));
        assertFalse(SshConnectionHelper.shouldReuseConnection(
                conn("h", 22, "u", "p"), conn("h", 2222, "u", "p"), true));
        assertFalse(SshConnectionHelper.shouldReuseConnection(
                conn("h", 22, "u", "p"), conn("h", 22, "U", "p"), true));
        // 同目标但凭据变更（修正密码）→ 不可复用
        assertFalse(SshConnectionHelper.shouldReuseConnection(
                conn("h", 22, "u", "old"), conn("h", 22, "u", "new"), true));
        // 任一侧键派生失败（空白 host）→ 不可复用
        assertFalse(SshConnectionHelper.shouldReuseConnection(
                conn("", 22, "u", "p"), conn("", 22, "u", "p"), true));
    }
}
