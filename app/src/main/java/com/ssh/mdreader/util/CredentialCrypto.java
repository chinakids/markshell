package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * 连接凭据加解密（纯函数层，无 Android 依赖，可 JVM 单测）。
 *
 * <p>方案：AES-256-GCM（平台 {@link Cipher}，Android API 21+ / JVM 均可用）。
 * 存储格式：{@code "enc$" + Base64( iv(12B) || ciphertext+tag )}，
 * 每次加密生成随机 IV；GCM 默认 128-bit tag。</p>
 *
 * <p>密钥由调用方从外部注入（Android 侧用 {@link CredentialKeystore} 取 Keystore
 * 密钥，测试侧用任意 {@link SecretKey}），本类不持有、不持久化密钥。</p>
 *
 * <p>兼容性约定：{@link #decrypt(String, SecretKey)} 对「无 {@value #ENCRYPTED_PREFIX}
 * 前缀」的值原样透传（旧明文数据/空值），对「有前缀但解密失败」返回 {@code null}
 * （调用方视为无密码，避免把密文当明文传给 JSch）。</p>
 */
public final class CredentialCrypto {

    /** 密文存储前缀：以它开头代表该值已加密。 */
    public static final String ENCRYPTED_PREFIX = "enc$";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialCrypto() {}

    /** 值是否为加密形态（以 {@value #ENCRYPTED_PREFIX} 开头）。空/null 返回 false。 */
    public static boolean isEncrypted(@Nullable String value) {
        return value != null && value.startsWith(ENCRYPTED_PREFIX);
    }

    /**
     * 加密明文。空/ {@code null} 原样返回（不产生密文）；非空返回
     * {@code enc$ + Base64(iv || ciphertext)}，每次调用独立随机 IV。
     *
     * @throws GeneralSecurityException 加密失败（密钥不对/算法不可用）——由调用方兜底降级
     */
    @NonNull
    public static String encrypt(@Nullable String plain, @NonNull SecretKey key)
            throws java.security.GeneralSecurityException {
        if (plain == null || plain.isEmpty()) return plain == null ? "" : plain;
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

        byte[] out = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(ct, 0, out, iv.length, ct.length);
        return ENCRYPTED_PREFIX + Base64.encode(out);
    }

    /**
     * 解密存储值。语义：
     * <ul>
     *   <li>空/ {@code null} → 原样返回；</li>
     *   <li>无 {@value #ENCRYPTED_PREFIX} 前缀 → 原样透传（旧明文数据）；</li>
     *   <li>有前缀且解密成功 → 明文；</li>
     *   <li>有前缀但失败（密钥不匹配/数据损坏）→ {@code null}。</li>
     * </ul>
     */
    @Nullable
    public static String decrypt(@Nullable String stored, @NonNull SecretKey key) {
        if (stored == null || stored.isEmpty()) return stored;
        if (!isEncrypted(stored)) return stored;
        try {
            byte[] raw = Base64.decode(stored.substring(ENCRYPTED_PREFIX.length()));
            if (raw.length <= IV_LENGTH) return null;
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] ct = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ct);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 静默失败：返回 null 由调用方处理（PreferenceManager 层已记日志）。
            // 不使用 android.util.Log —— 保持本类纯 JVM 可测。
            return null;
        }
    }

    /**
     * 内置最小 Base64 编解码（标准字母表 + {@code =} 填充）。
     * <p>minSdk 24 无 {@link java.util.Base64}（API 26+），且 android.util.Base64
     * 在 JVM 单元测试中不可用（stub），因此自带纯 Java 实现（约 60 行）。</p>
     */
    static final class Base64 {

        private static final char[] ALPHABET =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();
        private static final int[] DECODE = new int[256];

        static {
            for (int i = 0; i < DECODE.length; i++) DECODE[i] = -1;
            for (int i = 0; i < ALPHABET.length; i++) DECODE[ALPHABET[i]] = i;
            DECODE['='] = 0;
        }

        private Base64() {}

        @NonNull
        static String encode(@NonNull byte[] data) {
            StringBuilder sb = new StringBuilder(((data.length + 2) / 3) * 4);
            int i = 0;
            while (i + 2 < data.length) {
                int n = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8) | (data[i + 2] & 0xff);
                sb.append(ALPHABET[(n >> 18) & 0x3f])
                        .append(ALPHABET[(n >> 12) & 0x3f])
                        .append(ALPHABET[(n >> 6) & 0x3f])
                        .append(ALPHABET[n & 0x3f]);
                i += 3;
            }
            int rem = data.length - i;
            if (rem == 1) {
                int n = (data[i] & 0xff) << 16;
                sb.append(ALPHABET[(n >> 18) & 0x3f])
                        .append(ALPHABET[(n >> 12) & 0x3f])
                        .append("==");
            } else if (rem == 2) {
                int n = ((data[i] & 0xff) << 16) | ((data[i + 1] & 0xff) << 8);
                sb.append(ALPHABET[(n >> 18) & 0x3f])
                        .append(ALPHABET[(n >> 12) & 0x3f])
                        .append(ALPHABET[(n >> 6) & 0x3f])
                        .append('=');
            }
            return sb.toString();
        }

        @NonNull
        static byte[] decode(@NonNull String s) {
            // 先剔除空白与非字母表字符，仅保留标准 base64 字符与 '=' 填充
            StringBuilder clean = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '=' || (c < 256 && DECODE[c] >= 0)) clean.append(c);
            }
            String body = clean.toString();
            if (body.isEmpty()) return new byte[0];
            int pad = body.endsWith("==") ? 2 : (body.endsWith("=") ? 1 : 0);
            int outLen = (body.length() / 4) * 3 - pad;
            if (outLen < 0) outLen = 0;
            byte[] out = new byte[outLen];
            int outPos = 0, buffer = 0, bits = 0;
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c == '=') break; // 填充符：结束
                int v = DECODE[c];
                if (v < 0) continue;
                buffer = (buffer << 6) | v;
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    if (outPos < outLen) out[outPos++] = (byte) ((buffer >> bits) & 0xff);
                }
            }
            return out;
        }
    }
}
