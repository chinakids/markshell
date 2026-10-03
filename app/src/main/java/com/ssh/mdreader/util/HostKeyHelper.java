package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jcraft.jsch.HostKey;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 主机指纹（known_hosts）纯函数层（可 JVM 单测；引 JSch 仅用于读 {@link HostKey}）。
 *
 * <p>指纹格式：OpenSSH 风格 {@code "TYPE:BODY"}，目前支持两种类型——</p>
 * <ul>
 *   <li>{@code SHA256:...}（现行标准）：BODY 为 SHA-256 摘要的 Base64（无填充），大小写敏感；</li>
 *   <li>{@code MD5:...}（传统格式）：BODY 为十六进制（可带 {@code :} 分隔），大小写不敏感。</li>
 * </ul>
 *
 * <p>所有方法均为纯函数；比较/存储前一律经 {@link #normalizeFingerprint} 规范化：
 * 类型前缀归一为大写、去除全部空白、MD5 主体转小写并去冒号、SHA256 主体仅去空白
 * （Base64 大小写敏感，不可转小写，否则会改变指纹值）。</p>
 */
public final class HostKeyHelper {

    /** SHA-256 指纹前缀。 */
    public static final String PREFIX_SHA256 = "SHA256:";
    /** MD5 指纹前缀。 */
    public static final String PREFIX_MD5 = "MD5:";
    /** 类型标识（无冒号），供判定。 */
    private static final String TYPE_SHA256 = "SHA256";
    private static final String TYPE_MD5 = "MD5";

    private HostKeyHelper() {}

    // ── 生成 ───────────────────────────────────────────────────────────────

    /**
     * 计算 OpenSSH 风格 SHA-256 指纹：{@code "SHA256:" + Base64(SHA-256(blob))}（无填充）。
     * blob 为主公钥的完整二进制（如 {@link HostKey#getKey()} 的 Base64 解码结果）。
     * 已与 {@code ssh-keygen -E sha256} 输出对拍验证一致。
     */
    @NonNull
    public static String computeSha256Fingerprint(@NonNull byte[] keyBlob) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(keyBlob);
            return PREFIX_SHA256 + CredentialCrypto.Base64.encode(hash).replace("=", "");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是所有目标平台（JVM/ART）的必备算法，缺失属环境损坏。
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /** 从 JSch {@link HostKey} 计算指纹（用 {@code getKey()} 的 Base64 解码字节）。 */
    @Nullable
    public static String fingerprintOf(@Nullable HostKey hostKey) {
        if (hostKey == null) return null;
        String b64 = hostKey.getKey();
        if (b64 == null || b64.isEmpty()) return null;
        return computeSha256Fingerprint(CredentialCrypto.Base64.decode(b64));
    }

    // ── 规范化与解析 ───────────────────────────────────────────────────────

    /**
     * 规范化指纹：类型前缀归一为大写（SHA256/MD5），去除全部空白；MD5 主体转小写并去冒号。
     * 无法识别（无冒号 / 未知前缀 / 规范化后为空）返回空串表示「无效指纹」。
     */
    @NonNull
    public static String normalizeFingerprint(@Nullable String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int colon = s.indexOf(':');
        if (colon <= 0 || colon == s.length() - 1) {
            // 无类型前缀：无法判定算法，按无效处理
            return "";
        }
        String type = s.substring(0, colon).trim().toUpperCase();
        String body = s.substring(colon + 1).replaceAll("\\s+", "");
        if (body.isEmpty()) return "";
        if (TYPE_SHA256.equals(type)) {
            return PREFIX_SHA256 + body;
        }
        if (TYPE_MD5.equals(type)) {
            String hex = body.replace(":", "").toLowerCase();
            return hex.isEmpty() ? "" : PREFIX_MD5 + hex;
        }
        return "";
    }

    /** 校验输入是否为可识别的规范指纹（非空且能通过 {@link #normalizeFingerprint}）。 */
    public static boolean isValidFingerprint(@Nullable String raw) {
        return !normalizeFingerprint(raw).isEmpty();
    }

    // ── 比较 ───────────────────────────────────────────────────────────────

    /**
     * 两份指纹是否一致（规范化后精确比较）。类型不同（如 MD5 与 SHA256 混用）返回
     * {@code false}——保守语义：无法比对的视为不一致，交给上层走「变更警告」路径。
     */
    public static boolean matches(@Nullable String expected, @Nullable String actual) {
        String a = normalizeFingerprint(expected);
        String b = normalizeFingerprint(actual);
        if (a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b);
    }

    /** 校验结果分类（TOFU / 一致 / 变更）。 */
    public enum VerifyResult { NEW, MATCH, CHANGED }

    /**
     * 按 known_hosts 语义分类：{@code stored} 为空（从未记录）= {@link VerifyResult#NEW}（TOFU）；
     * 一致 = {@link VerifyResult#MATCH}；不一致或无法比对 = {@link VerifyResult#CHANGED}。
     */
    @NonNull
    public static VerifyResult verifyFingerprint(@Nullable String stored,
                                                 @Nullable String actual) {
        if (actual == null || normalizeFingerprint(actual).isEmpty()) {
            // 拿不到有效指纹时按「无记录」处理（调用方通常降级放行）
            return VerifyResult.NEW;
        }
        if (stored == null || normalizeFingerprint(stored).isEmpty()) {
            return VerifyResult.NEW;
        }
        return matches(stored, actual) ? VerifyResult.MATCH : VerifyResult.CHANGED;
    }

    // ── 展示辅助 ───────────────────────────────────────────────────────────

    /**
     * 把规范指纹按 {@code groupSize} 字符一组换行分组便于肉眼比对（例如 4）。
     * 形如 {@code "SHA256:\nT1F8\nUbRY\n..."}（首行类型前缀，正文每行一组）。
     * {@code groupSize <= 0} 时回退 4。
     */
    @NonNull
    public static String formatGrouped(@Nullable String fingerprint, int groupSize) {
        String normalized = normalizeFingerprint(fingerprint);
        if (normalized.isEmpty()) return "";
        int g = groupSize > 0 ? groupSize : 4;
        String body = normalized.substring(normalized.indexOf(':') + 1);
        String type = normalized.substring(0, normalized.indexOf(':') + 1);
        StringBuilder sb = new StringBuilder(type.length() + 1 + body.length() + body.length() / g);
        sb.append(type).append('\n');
        for (int i = 0; i < body.length(); i += g) {
            if (i > 0) sb.append('\n');
            sb.append(body, i, Math.min(i + g, body.length()));
        }
        return sb.toString();
    }
}
