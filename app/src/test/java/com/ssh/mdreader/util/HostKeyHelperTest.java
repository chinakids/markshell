package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.jcraft.jsch.HostKey;

import org.junit.Test;

/**
 * {@link HostKeyHelper} 纯函数层单测：指纹生成（黄金向量与 OpenSSH 对拍）、
 * 规范化、比较、TOFU 分类与展示分组。
 *
 * <p>黄金向量说明：{@link #PUBLIC_KEY_B64} 为 ssh-keygen 生成的 RSA-2048 公钥
 * （wire-format blob 的 Base64，即 OpenSSH {@code authorized_keys} 第二列），
 * {@link #EXPECTED_SHA256} 为 {@code ssh-keygen -E sha256 -lf} 对同一公钥的输出，
 * 两者已在开发环境对拍一致（SHA-256 over blob → Base64 无填充）。</p>
 */
public class HostKeyHelperTest {

    /** 测试公钥（RSA-2048）wire-format blob 的 Base64。 */
    private static final String PUBLIC_KEY_B64 =
            "AAAAB3NzaC1yc2EAAAADAQABAAABAQDTUOfBwHFjcuSZpXnCc3Q8hvjkM//RfexFP2A0HxqWKG4uu/sPFVaYMjd8ZoCNez66al3q"
            + "eCgDT3ntPGiEMUJ7vx7dLP/NpxGR9KRsmex4TnTss++9aoiqhEXB/O2fD3RV9ZKGdtam3MXicdcRlJKgVEtl4xLrQD+19cV8BYd2"
            + "0SO8n7gBmml/+GuiLG+czdsAE+h97IdMyqlJRgzJc0i+mVMnWgg9NiTs/vyUrIKjASHuo01iHGAhwKkecqX4NFlC33Pdjm4Gju7p"
            + "59MQrQIaGMfxMKSjLMKoEsXq/Cy7L4Ojczpx64EbL32WkzIUw+ptiy3OjSnyi3ekb52vLyL9";

    /** OpenSSH {@code ssh-keygen -E sha256 -lf} 对 {@link #PUBLIC_KEY_B64} 的输出（已对拍）。 */
    private static final String EXPECTED_SHA256 =
            "SHA256:T1F8UbRYlzNCtVIMt0WZaawrfNhDeNDTPvBjxbt9bks";

    // ── 指纹生成 ─────────────────────────────────────────────────────────────

    @Test
    public void computeSha256Fingerprint_matchesOpenSshGoldenVector() {
        byte[] blob = CredentialCrypto.Base64.decode(PUBLIC_KEY_B64);
        assertEquals(EXPECTED_SHA256, HostKeyHelper.computeSha256Fingerprint(blob));
    }

    @Test
    public void computeSha256Fingerprint_sha256BodyHasNoPadding() {
        byte[] blob = CredentialCrypto.Base64.decode(PUBLIC_KEY_B64);
        String fp = HostKeyHelper.computeSha256Fingerprint(blob);
        assertTrue(fp.startsWith("SHA256:"));
        String body = fp.substring("SHA256:".length());
        assertEquals(43, body.length());
        assertFalse("指纹正文不应含填充符 =", body.contains("="));
    }

    @Test
    public void fingerprintOf_hostKey_matchesComputeDirect() throws Exception {
        HostKey hk = new HostKey("[test.example.com]:22",
                CredentialCrypto.Base64.decode(PUBLIC_KEY_B64));
        String fp = HostKeyHelper.fingerprintOf(hk);
        assertEquals(EXPECTED_SHA256, fp);
    }

    @Test
    public void fingerprintOf_nullReturnsNull() {
        assertNull(HostKeyHelper.fingerprintOf(null));
    }

    // ── 规范化 ───────────────────────────────────────────────────────────────

    @Test
    public void normalize_fingerprint_stripsWhitespaceAndTypeCase() {
        assertEquals("SHA256:abCD12eF",
                HostKeyHelper.normalizeFingerprint("  sha256: abCD12eF \n"));
        assertEquals("MD5:aabbccdd",
                HostKeyHelper.normalizeFingerprint("md5:AA:BB:cc:DD"));
    }

    @Test
    public void normalize_fingerprint_invalidInputsGiveEmpty() {
        assertEquals("", HostKeyHelper.normalizeFingerprint(null));
        assertEquals("", HostKeyHelper.normalizeFingerprint(""));
        assertEquals("", HostKeyHelper.normalizeFingerprint("   "));
        assertEquals("", HostKeyHelper.normalizeFingerprint("noprefixbody"));
        assertEquals("", HostKeyHelper.normalizeFingerprint("SHA256:"));
        assertEquals("", HostKeyHelper.normalizeFingerprint("SHA256:   "));
        assertEquals("", HostKeyHelper.normalizeFingerprint("ED25519:abc"));
    }

    @Test
    public void normalize_fingerprint_keepsSha256Base64CaseSensitive() {
        // Base64 大小写敏感：不能转小写（转小写会改变指纹值）
        assertEquals("SHA256:AbC", HostKeyHelper.normalizeFingerprint("SHA256:AbC"));
        assertFalse(HostKeyHelper.normalizeFingerprint("SHA256:AbC")
                .equals(HostKeyHelper.normalizeFingerprint("SHA256:abc")));
    }

    @Test
    public void isValidFingerprint_reflectsNormalize() {
        assertTrue(HostKeyHelper.isValidFingerprint("SHA256:abC"));
        assertTrue(HostKeyHelper.isValidFingerprint("MD5:aa:bb"));
        assertFalse(HostKeyHelper.isValidFingerprint("garbage"));
        assertFalse(HostKeyHelper.isValidFingerprint(null));
    }

    // ── 比较 ─────────────────────────────────────────────────────────────────

    @Test
    public void matches_equalIgnoringFormatting() {
        assertTrue(HostKeyHelper.matches("SHA256:T1F8", "  sha256: T1F8 "));
        assertTrue(HostKeyHelper.matches("MD5:aa:bb:cc", "MD5:AA:BB:CC"));
    }

    @Test
    public void matches_differentOrInvalidIsFalse() {
        assertFalse(HostKeyHelper.matches("SHA256:T1F8", "SHA256:T1F9"));
        assertFalse(HostKeyHelper.matches("SHA256:T1F8", null));
        assertFalse(HostKeyHelper.matches(null, "SHA256:T1F8"));
        assertFalse(HostKeyHelper.matches("", "SHA256:T1F8"));
        assertFalse(HostKeyHelper.matches("SHA256:T1F8", "garbage"));
    }

    @Test
    public void matches_mixedTypeIsConservativeFalse() {
        // 无法比对（MD5 vs SHA256）→ 保守判不一致，交由上层走变更警告
        assertFalse(HostKeyHelper.matches("MD5:aa:bb:cc:dd", EXPECTED_SHA256));
        assertFalse(HostKeyHelper.matches(EXPECTED_SHA256, "MD5:aa:bb:cc:dd"));
    }

    // ── TOFU 分类 ────────────────────────────────────────────────────────────

    @Test
    public void verifyFingerprint_tofuMatchChanged() {
        assertEquals(HostKeyHelper.VerifyResult.NEW,
                HostKeyHelper.verifyFingerprint(null, EXPECTED_SHA256));
        assertEquals(HostKeyHelper.VerifyResult.NEW,
                HostKeyHelper.verifyFingerprint("", EXPECTED_SHA256));
        assertEquals(HostKeyHelper.VerifyResult.MATCH,
                HostKeyHelper.verifyFingerprint("  sha256:" + EXPECTED_SHA256.substring(7),
                        EXPECTED_SHA256));
        assertEquals(HostKeyHelper.VerifyResult.CHANGED,
                HostKeyHelper.verifyFingerprint("SHA256:AAAA", EXPECTED_SHA256));
        // 拿不到有效 actual → 按 NEW（调用方降级放行）
        assertEquals(HostKeyHelper.VerifyResult.NEW,
                HostKeyHelper.verifyFingerprint("SHA256:AAAA", null));
    }

    // ── 展示分组 ─────────────────────────────────────────────────────────────

    @Test
    public void formatGrouped_groupsBodyByFourAndKeepsType() {
        String grouped = HostKeyHelper.formatGrouped(
                "SHA256:T1F8UbRYlzNCtVIMt0WZaawrfNhDeNDTPvBjxbt9bks", 4);
        String[] lines = grouped.split("\n");
        assertEquals("SHA256:", lines[0]);
        assertEquals("T1F8", lines[1]);
        assertEquals("UbRY", lines[2]);
        assertEquals(12, lines.length); // 1 + 43/4 取整=11 行（末行 3 字符）
        assertEquals("bks", lines[11]);
    }

    @Test
    public void formatGrouped_invalidOrBadGroupSizeFallsBack() {
        assertEquals("", HostKeyHelper.formatGrouped("garbage", 4));
        assertEquals("", HostKeyHelper.formatGrouped(null, 4));
        // groupSize<=0 回退 4
        assertEquals("SHA256:\nT1F8\nUbRY",
                HostKeyHelper.formatGrouped("SHA256:T1F8UbRY", 0));
        assertEquals("SHA256:\nT1F8\nUbRY",
                HostKeyHelper.formatGrouped("SHA256:T1F8UbRY", -3));
    }
}
