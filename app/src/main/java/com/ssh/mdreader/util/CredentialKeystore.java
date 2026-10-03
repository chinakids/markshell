package com.ssh.mdreader.util;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * Android Keystore 密钥供给层（仅 Android 侧；JVM 单测用 {@link CredentialCrypto} +
 * 自造测试密钥，不经过本类）。
 *
 * <p>密钥别名 {@code ssh_md_credentials}，AES-256-GCM，生成后密钥材料保存在
 * Keystore 内不可导出（备份/导出会被系统拦截），用于
 * {@link CredentialCrypto#encrypt(String, SecretKey)} 加密连接密码。</p>
 */
public final class CredentialKeystore {

    private static final String TAG = "CredentialKeystore";
    private static final String KEY_ALIAS = "ssh_md_credentials";

    private CredentialKeystore() {}

    /**
     * 读取或创建 Keystore AES 密钥。
     *
     * @return 可用的 {@link SecretKey}；Keystore 不可用（极老设备/模拟器异常）时返回
     *         {@code null}，此时调用方降级为明文存储（保持功能可用）。
     */
    public static SecretKey loadOrCreate() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) {
                return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
            KeyGenerator generator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(
                            KEY_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        } catch (Exception e) {
            Log.w(TAG, "Android Keystore 不可用，连接密码回退明文存储", e);
            return null;
        }
    }
}
