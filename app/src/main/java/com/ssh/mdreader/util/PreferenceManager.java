package com.ssh.mdreader.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.ssh.mdreader.model.SshConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PreferenceManager {
    private static final String TAG = "PreferenceManager";
    private static final String PREF_NAME = "ssh_md_reader_prefs";
    private static final String KEY_SAVED_CONNECTIONS = "saved_connections";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SHOW_HIDDEN = "show_hidden";
    private static final String KEY_HEARTBEAT_MS = "heartbeat_interval_ms";
    private static final String KEY_FILE_SORT_MODE = "file_sort_mode";
    private static final String KEY_BOOKMARKS = "bookmarked_dirs";
    /** 与 SshManager.DEFAULT_HEARTBEAT_MS 保持一致。 */
    private static final int DEFAULT_HEARTBEAT_MS = 5_000;

    private final SharedPreferences prefs;
    /** Keystore 密钥（懒加载）。null=Keystore 不可用，降级明文（保持功能可用）。 */
    private javax.crypto.SecretKey credentialKey;

    public PreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 懒加载 Keystore 密钥（仅加密/解密连接密码用）。 */
    private javax.crypto.SecretKey credentialKey() {
        if (credentialKey == null) {
            credentialKey = CredentialKeystore.loadOrCreate();
        }
        return credentialKey;
    }

    /** 加密密码后再写盘；密钥不可用时降级明文（并记日志，不阻塞保存）。 */
    private String encryptPassword(String password) {
        if (password == null || password.isEmpty()) return password;
        javax.crypto.SecretKey key = credentialKey();
        if (key == null) {
            Log.w(TAG, "无可用加密密钥，本条目密码以明文落盘");
            return password;
        }
        try {
            return CredentialCrypto.encrypt(password, key);
        } catch (java.security.GeneralSecurityException e) {
            Log.w(TAG, "密码加密失败，本条目以明文落盘", e);
            return password;
        }
    }

    /** 解密读取；旧明文透传；损坏密文返回 null（调用方按无密码处理，用户需重输）。 */
    private String decryptPassword(String stored) {
        javax.crypto.SecretKey key = credentialKey();
        // Keystore 不可用时不抛错：透传原始值（旧行为）
        if (key == null) return stored;
        return CredentialCrypto.decrypt(stored, key);
    }

    public void saveConnection(SshConfig config) {
        List<SshConfig> existing = getSavedConnections();
        // 去重：相同 host+port+username 的记录覆盖
        for (int i = existing.size() - 1; i >= 0; i--) {
            SshConfig c = existing.get(i);
            if (c.getHost().equals(config.getHost())
                    && c.getPort() == config.getPort()
                    && c.getUsername().equals(config.getUsername())) {
                existing.remove(i);
            }
        }
        existing.add(0, config);
        saveConnectionList(existing);
    }

    public List<SshConfig> getSavedConnections() {
        String json = prefs.getString(KEY_SAVED_CONNECTIONS, "");
        List<SshConfig> list = new ArrayList<>();
        if (json.isEmpty()) return list;
        boolean needsMigrate = false;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                SshConfig config = new SshConfig();
                config.setAlias(obj.optString("alias", ""));
                config.setHost(obj.optString("host", ""));
                config.setPort(obj.optInt("port", 22));
                config.setUsername(obj.optString("username", ""));
                config.setPassword(readDecryptedPassword(obj, config));
                if (hasLegacyPlainPassword(obj)) needsMigrate = true;
                config.setRemotePath(obj.optString("remotePath", "/"));
                list.add(config);
            }
        } catch (JSONException e) {
            Log.w(TAG, "读取已保存连接失败，已忽略损坏的配置", e);
        }
        // 一次性迁移：旧明文密码读出来后立即重写为加密存储（saveConnectionList 负责加密）
        if (needsMigrate) {
            Log.i(TAG, "检测到旧明文连接密码，正在迁移为加密存储");
            saveConnectionList(list);
        }
        return list;
    }

    /** 读取并解密密码；损坏密文按无密码处理（用户重输），并保留原值待下次覆盖。 */
    private String readDecryptedPassword(JSONObject obj, SshConfig config) {
        String raw = obj.optString("password", "");
        if (raw.isEmpty()) return "";
        String plain = decryptPassword(raw);
        if (plain == null) {
            Log.w(TAG, "连接「" + config.getHost() + "」密码解密失败（密钥变更或数据损坏），请重新输入");
            return "";
        }
        return plain;
    }

    /** 是否残留旧明文密码（无前缀且非空），供一次性迁移判定。 */
    private static boolean hasLegacyPlainPassword(JSONObject obj) {
        String raw = obj.optString("password", "");
        return !raw.isEmpty() && !CredentialCrypto.isEncrypted(raw);
    }

    public void deleteConnection(int index) {
        List<SshConfig> existing = getSavedConnections();
        if (index >= 0 && index < existing.size()) {
            existing.remove(index);
            saveConnectionList(existing);
        }
    }

    public void updateConnection(int index, SshConfig updated) {
        List<SshConfig> existing = getSavedConnections();
        if (index >= 0 && index < existing.size()) {
            existing.set(index, updated);
            saveConnectionList(existing);
        }
    }

    public void updateRemotePath(String host, int port, String username, String remotePath) {
        List<SshConfig> existing = getSavedConnections();
        for (SshConfig c : existing) {
            if (c.getHost().equals(host)
                    && c.getPort() == port
                    && c.getUsername().equals(username)) {
                c.setRemotePath(remotePath);
                break;
            }
        }
        saveConnectionList(existing);
    }

    private void saveConnectionList(List<SshConfig> list) {
        JSONArray arr = new JSONArray();
        for (SshConfig c : list) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("alias", c.getAlias());
                obj.put("host", c.getHost());
                obj.put("port", c.getPort());
                obj.put("username", c.getUsername());
                obj.put("password", encryptPassword(c.getPassword()));
                obj.put("remotePath", c.getRemotePath());
                arr.put(obj);
            } catch (JSONException e) {
                Log.w(TAG, "序列化连接配置失败，该条未保存: " + c.getHost(), e);
            }
        }
        prefs.edit().putString(KEY_SAVED_CONNECTIONS, arr.toString()).apply();
    }

    public void saveFontSize(int size) {
        prefs.edit().putInt(KEY_FONT_SIZE, size).apply();
    }

    public int getFontSize(int defaultValue) {
        return prefs.getInt(KEY_FONT_SIZE, defaultValue);
    }

    public void saveShowHidden(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply();
    }

    public boolean getShowHidden() {
        return prefs.getBoolean(KEY_SHOW_HIDDEN, false);
    }

    /** 心跳间隔（毫秒）。默认 5000，与 SshManager 默认一致。 */
    public void saveHeartbeatIntervalMs(int ms) {
        prefs.edit().putInt(KEY_HEARTBEAT_MS, ms).apply();
    }

    public int getHeartbeatIntervalMs() {
        return prefs.getInt(KEY_HEARTBEAT_MS, DEFAULT_HEARTBEAT_MS);
    }

    /** 文件列表排序模式，取值见 FileSortUtils（0=名称 1=修改时间 2=大小）。 */
    public void saveFileSortMode(int mode) {
        prefs.edit().putInt(KEY_FILE_SORT_MODE, mode).apply();
    }

    public int getFileSortMode() {
        return prefs.getInt(KEY_FILE_SORT_MODE, 0);
    }

    // ── 目录书签（按服务器 host+port+username 隔离）─────────────────────────

    /**
     * 读取指定服务器已收藏的目录路径列表（按「新收藏在前」）。
     * JSON 结构：{@code [{"host":..,"port":..,"username":..,"paths":["/a",..]},..]}。
     */
    public List<String> getBookmarkedPaths(String host, int port, String username) {
        JSONArray arr = readBookmarks();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                return pathsFrom(obj.optJSONArray("paths"));
            }
        }
        return new ArrayList<>();
    }

    /** 收藏目录（规范化后置顶、去重、上限 {@link BookmarkHelper#MAX_BOOKMARKS}），立即持久化。 */
    public void addBookmark(String host, int port, String username, String path) {
        if (host == null || username == null) return;
        String normalized = BookmarkHelper.normalizePath(path);
        if (normalized.isEmpty()) return;
        JSONArray arr = readBookmarks();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                List<String> paths = BookmarkHelper.addBookmark(
                        pathsFrom(obj.optJSONArray("paths")), normalized);
                if (putPaths(obj, paths)) writeBookmarks(arr);
                return;
            }
        }
        // 该服务器尚无书签记录：新建条目
        JSONObject obj = new JSONObject();
        try {
            obj.put("host", host)
                    .put("port", port)
                    .put("username", username);
            obj.put("paths", new JSONArray(
                    BookmarkHelper.addBookmark(null, normalized)));
            arr.put(obj);
            writeBookmarks(arr);
        } catch (JSONException e) {
            Log.w(TAG, "保存书签失败", e);
        }
    }

    /** 取消收藏（移除全部规范化匹配项）；该服务器书签清空后删除整条记录。 */
    public void removeBookmark(String host, int port, String username, String path) {
        JSONArray arr = readBookmarks();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null || !matchesServer(obj, host, port, username)) continue;
            List<String> updated = BookmarkHelper.removeBookmark(
                    pathsFrom(obj.optJSONArray("paths")), path);
            if (updated.isEmpty()) {
                arr.remove(i);
            } else if (!putPaths(obj, updated)) {
                return;
            }
            writeBookmarks(arr);
            return;
        }
    }

    /** 目录是否已收藏（按规范化后比较）。 */
    public boolean isBookmarked(String host, int port, String username, String path) {
        return BookmarkHelper.isBookmarked(
                getBookmarkedPaths(host, port, username), path);
    }

    private JSONArray readBookmarks() {
        String json = prefs.getString(KEY_BOOKMARKS, "");
        if (json.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            Log.w(TAG, "读取书签失败，已忽略损坏的数据", e);
            return new JSONArray();
        }
    }

    private void writeBookmarks(JSONArray arr) {
        prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply();
    }

    private static boolean matchesServer(JSONObject obj, String host, int port, String username) {
        return obj.optString("host", "").equals(host)
                && obj.optInt("port", 0) == port
                && obj.optString("username", "").equals(username);
    }

    private static List<String> pathsFrom(JSONArray paths) {
        List<String> list = new ArrayList<>();
        if (paths != null) {
            for (int i = 0; i < paths.length(); i++) {
                String p = paths.optString(i);
                if (!p.isEmpty()) list.add(p);
            }
        }
        return list;
    }

    private static boolean putPaths(JSONObject obj, List<String> paths) {
        try {
            obj.put("paths", new JSONArray(paths));
            return true;
        } catch (JSONException e) {
            Log.w(TAG, "序列化书签失败", e);
            return false;
        }
    }
}
