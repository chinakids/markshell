package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * {@link CredentialCrypto} 的 JVM 纯函数单测。
 * 不依赖 Android（无 android.util.Log / Keystore）：密钥用本地 {@link SecretKeySpec}。
 */
public class CredentialCryptoTest {

    private static final SecureRandom RNG = new SecureRandom();

    private static SecretKey newKey() {
        byte[] mk = new byte[32];
        RNG.nextBytes(mk);
        return new SecretKeySpec(mk, "AES");
    }

    @Test
    public void encryptDecrypt_roundTrip() throws Exception {
        SecretKey key = newKey();
        String plain = "s3cret@pass_中文密码!";
        String stored = CredentialCrypto.encrypt(plain, key);
        assertTrue(CredentialCrypto.isEncrypted(stored));
        assertTrue(stored.startsWith(CredentialCrypto.ENCRYPTED_PREFIX));
        assertEquals(plain, CredentialCrypto.decrypt(stored, key));
    }

    @Test
    public void encrypt_producesDifferentCiphertextEachTime() throws Exception {
        SecretKey key = newKey();
        String a = CredentialCrypto.encrypt("same-password", key);
        String b = CredentialCrypto.encrypt("same-password", key);
        assertTrue("随机 IV 应产生不同密文", !a.equals(b));
        assertEquals("same-password", CredentialCrypto.decrypt(a, key));
        assertEquals("same-password", CredentialCrypto.decrypt(b, key));
    }

    @Test
    public void decrypt_wrongKeyReturnsNull() throws Exception {
        SecretKey encryptKey = newKey();
        SecretKey wrongKey = newKey();
        String stored = CredentialCrypto.encrypt("pw", encryptKey);
        assertNull(CredentialCrypto.decrypt(stored, wrongKey));
    }

    @Test
    public void encrypt_emptyAndNullArePassthrough() throws Exception {
        SecretKey key = newKey();
        assertEquals("", CredentialCrypto.encrypt("", key));
        assertEquals("", CredentialCrypto.encrypt(null, key));
    }

    @Test
    public void decrypt_legacyPlaintextPassthrough() throws Exception {
        SecretKey key = newKey();
        assertEquals("legacy-pw", CredentialCrypto.decrypt("legacy-pw", key));
        assertEquals("", CredentialCrypto.decrypt("", key));
        assertNull(CredentialCrypto.decrypt(null, key));
    }

    @Test
    public void decrypt_corruptedCiphertextReturnsNull() throws Exception {
        SecretKey key = newKey();
        // 前缀对但内容不是合法 base64
        assertNull(CredentialCrypto.decrypt(CredentialCrypto.ENCRYPTED_PREFIX + "@@@not-base64@@@", key));
        // base64 合法但长度不足（< IV 长度）
        assertNull(CredentialCrypto.decrypt(CredentialCrypto.ENCRYPTED_PREFIX + CredentialCrypto.Base64.encode(new byte[4]), key));
    }

    @Test
    public void decrypt_tamperedCiphertextReturnsNull() throws Exception {
        SecretKey key = newKey();
        String stored = CredentialCrypto.encrypt("mypw", key);
        String payload = stored.substring(CredentialCrypto.ENCRYPTED_PREFIX.length());
        byte[] raw = CredentialCrypto.Base64.decode(payload);
        raw[raw.length - 1] ^= 0x01; // 篡改 GCM tag 最后一字节
        String tampered = CredentialCrypto.ENCRYPTED_PREFIX + CredentialCrypto.Base64.encode(raw);
        assertNull(CredentialCrypto.decrypt(tampered, key));
    }

    @Test
    public void isEncrypted_onlyPrefix() {
        assertTrue(CredentialCrypto.isEncrypted(CredentialCrypto.ENCRYPTED_PREFIX + "abc"));
        assertFalse(CredentialCrypto.isEncrypted("plaintext"));
        assertFalse(CredentialCrypto.isEncrypted(""));
        assertFalse(CredentialCrypto.isEncrypted(null));
        assertFalse(CredentialCrypto.isEncrypted("enc"));
    }

    @Test
    public void base64_roundTrip_variousSizes() {
        for (int len : new int[]{0, 1, 2, 3, 4, 5, 16, 17, 44, 64, 128, 255}) {
            byte[] data = new byte[len];
            RNG.nextBytes(data);
            byte[] decoded = CredentialCrypto.Base64.decode(CredentialCrypto.Base64.encode(data));
            assertTrue("len=" + len, Arrays.equals(data, decoded));
        }
    }

    @Test
    public void base64_standardVectors() {
        assertEquals("TWFu", CredentialCrypto.Base64.encode("Man".getBytes(StandardCharsets.UTF_8)));
        assertEquals("TWE=", CredentialCrypto.Base64.encode("Ma".getBytes(StandardCharsets.UTF_8)));
        assertEquals("TQ==", CredentialCrypto.Base64.encode("M".getBytes(StandardCharsets.UTF_8)));
        assertTrue(Arrays.equals("Man".getBytes(StandardCharsets.UTF_8), CredentialCrypto.Base64.decode("TWFu")));
        assertTrue(Arrays.equals("Ma".getBytes(StandardCharsets.UTF_8), CredentialCrypto.Base64.decode("TWE=")));
        assertTrue(Arrays.equals("M".getBytes(StandardCharsets.UTF_8), CredentialCrypto.Base64.decode("TQ==")));
    }

    @Test
    public void base64_ignoresWhitespaceAndNewlines() {
        byte[] data = "hello world".getBytes(StandardCharsets.UTF_8);
        String encoded = CredentialCrypto.Base64.encode(data);
        String withBreaks = encoded.substring(0, 4) + "\n" + encoded.substring(4) + " ";
        assertTrue(Arrays.equals(data, CredentialCrypto.Base64.decode(withBreaks)));
    }

    @Test
    public void decrypt_unicodePassword() throws Exception {
        SecretKey key = newKey();
        String plain = "帕斯沃得🔑💖"; // 含 emoji 的密码
        String stored = CredentialCrypto.encrypt(plain, key);
        assertEquals(plain, CredentialCrypto.decrypt(stored, key));
    }

    @Test
    public void encryptDecrypt_multilinePemRoundTrip() throws Exception {
        SecretKey key = newKey();
        String pem = "-----BEGIN RSA PRIVATE KEY-----\n"
                + "MIIEpAIBAAKCAQEAuD3x...ab12\n"
                + "MIIEpAIBAAKCAQEAuD3x...cd34\n"
                + "-----END RSA PRIVATE KEY-----\n";
        String stored = CredentialCrypto.encrypt(pem, key);
        assertTrue("私钥必须密文化", CredentialCrypto.isEncrypted(stored));
        assertEquals("含换行的 PEM 全文应无损往返", pem, CredentialCrypto.decrypt(stored, key));
    }
}
