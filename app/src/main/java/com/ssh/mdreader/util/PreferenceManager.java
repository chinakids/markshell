package com.ssh.mdreader.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.ssh.mdreader.model.PortForwardRule;
import com.ssh.mdreader.model.RecentFileEntry;
import com.ssh.mdreader.model.SshConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PreferenceManager implements HostKeyStore {
    private static final String TAG = "PreferenceManager";
    private static final String PREF_NAME = "ssh_md_reader_prefs";
    private static final String KEY_SAVED_CONNECTIONS = "saved_connections";
    private static final String KEY_FONT_SIZE = "font_size";
    /** Code/Text 查看器缩放字号（sp，float）：走查 #38——查看器双指缩放持久化，与阅读器 KEY_FONT_SIZE 互不影响。 */
    private static final String KEY_VIEWER_TEXT_SIZE = "viewer_text_size";
    private static final String KEY_SHOW_HIDDEN = "show_hidden";
    private static final String KEY_HEARTBEAT_MS = "heartbeat_interval_ms";
    /** 查看器「打开后跳到底部」（布尔）：走查 #34，markor editor_start_editing_on_bottom 同型。 */
    private static final String KEY_START_ON_BOTTOM = "viewer_start_on_bottom";
    private static final String KEY_FILE_SORT_MODE = "file_sort_mode";
    /** 外观主题（string：system/light/dark，取值/归一化见 ThemeHelper）：走查 #54——DayNight 手动三态，等价 markor pref_key__app_theme。 */
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_BOOKMARKS = "bookmarked_dirs";
    /** 主机指纹库（known_hosts），按 host+port 记录。 */
    private static final String KEY_HOST_KEYS = "known_hosts";
    /** 与 SshManager.DEFAULT_HEARTBEAT_MS 保持一致。 */
    private static final int DEFAULT_HEARTBEAT_MS = 5_000;
    /** 连接分组元数据（组名列表，JSONArray of String）。 */
    private static final String KEY_CONNECTION_GROUPS = "connection_groups";
    /** 端口转发规则（按服务器连接键隔离，JSON 结构见 {@link #readPortForwardRules()}）。 */
    private static final String KEY_PORT_FORWARD_RULES = "port_forward_rules";
    /** 最近打开文件（阅读历史，按服务器 host+port+username 隔离）。 */
    private static final String KEY_RECENT_FILES = "recent_files";
    /** 阅读进度（滚动像素 Y）——按服务器隔离的文件路径复合键（走查 #40）。 */
    private static final String KEY_READ_PROGRESS = "read_progress";
    /** 上次浏览目录（「继续上次位置」，按服务器 host+port+username 隔离）。 */
    private static final String KEY_LAST_BROWSED_DIR = "last_browsed_dir";

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

    /** 加密敏感字段（密码/私钥/私钥口令）后再写盘；密钥不可用时降级明文（并记日志，不阻塞保存）。 */
    private String encryptSecret(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        javax.crypto.SecretKey key = credentialKey();
        if (key == null) {
            Log.w(TAG, "无可用加密密钥，本条目敏感字段以明文落盘");
            return plain;
        }
        try {
            return CredentialCrypto.encrypt(plain, key);
        } catch (java.security.GeneralSecurityException e) {
            Log.w(TAG, "敏感字段加密失败，本条目以明文落盘", e);
            return plain;
        }
    }

    /** 解密读取；旧明文透传；损坏密文返回 null（调用方按无值处理，用户需重输）。 */
    private String decryptSecret(String stored) {
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
                SshConfig config = SshConfigJson.fromJson(obj);
                config.setPassword(readDecryptedSecret(obj, "password", config, "密码"));
                config.setPrivateKey(readDecryptedSecret(obj, "privateKey", config, "私钥"));
                config.setKeyPassphrase(readDecryptedSecret(obj, "keyPassphrase", config, "私钥口令"));
                if (SshConfigJson.hasLegacyPlainSecret(obj, "password")
                        || SshConfigJson.hasLegacyPlainSecret(obj, "privateKey")
                        || SshConfigJson.hasLegacyPlainSecret(obj, "keyPassphrase")) {
                    needsMigrate = true;
                }
                list.add(config);
            }
        } catch (JSONException e) {
            Log.w(TAG, "读取已保存连接失败，已忽略损坏的配置", e);
        }
        // 一次性迁移：旧明文凭据读出来后立即重写为加密存储（saveConnectionList 负责加密）
        if (needsMigrate) {
            Log.i(TAG, "检测到旧明文连接凭据，正在迁移为加密存储");
            saveConnectionList(list);
        }
        return list;
    }

    /** 读取并解密某敏感字段；损坏密文按无值处理（用户重输），并保留原值待下次覆盖。 */
    private String readDecryptedSecret(JSONObject obj, String field, SshConfig config, String label) {
        String raw = obj.optString(field, "");
        if (raw.isEmpty()) return "";
        String plain = decryptSecret(raw);
        if (plain == null) {
            Log.w(TAG, "连接「" + config.getHost() + "」" + label
                    + "解密失败（密钥变更或数据损坏），请重新输入");
            return "";
        }
        return plain;
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
            try {
                JSONObject obj = SshConfigJson.toJson(c);
                obj.put("password", encryptSecret(c.getPassword()));
                obj.put("privateKey", encryptSecret(c.getPrivateKey()));
                obj.put("keyPassphrase", encryptSecret(c.getKeyPassphrase()));
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

    /** 查看器缩放字号（sp）。无记录返回 {@code defaultValue}（调用方传 ViewerTextSizeHelper.DEFAULT_TEXT_SIZE）。 */
    public float getViewerTextSize(float defaultValue) {
        return prefs.getFloat(KEY_VIEWER_TEXT_SIZE, defaultValue);
    }

    /** 保存查看器缩放字号（sp），供下一次打开 Code/Text 查看器恢复（走查 #38）。 */
    public void saveViewerTextSize(float size) {
        prefs.edit().putFloat(KEY_VIEWER_TEXT_SIZE, size).apply();
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

    /** 查看器「打开后跳到底部」（走查 #34）。默认 false（保留既有「阅读进度恢复」语义）。 */
    public void saveStartOnBottom(boolean enabled) {
        prefs.edit().putBoolean(KEY_START_ON_BOTTOM, enabled).apply();
    }

    public boolean getStartOnBottom() {
        return prefs.getBoolean(KEY_START_ON_BOTTOM, false);
    }

    /** 外观主题（string：follow system/light/dark）。写入前经 ThemeHelper.normalize，脏值一律回退默认。 */
    public void saveThemeMode(String theme) {
        prefs.edit().putString(KEY_THEME_MODE, ThemeHelper.normalize(theme)).apply();
    }

    public String getThemeMode() {
        return prefs.getString(KEY_THEME_MODE, ThemeHelper.DEFAULT_THEME);
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

    /** 清空指定服务器全部书签（删除该服务器整条记录，无记录则不动）。 */
    public void clearBookmarks(String host, int port, String username) {
        JSONArray arr = readBookmarks();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                arr.remove(i);
                writeBookmarks(arr);
                return;
            }
        }
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

    // ── 最近打开（阅读历史，按服务器 host+port+username 隔离）────────────────

    /**
     * 读取指定服务器最近打开的文件列表（按「最近在前」）。
     * JSON 结构：{@code [{"host":..,"port":..,"username":..,"files":[{"path":..,"ts":..},..]},..]}；
     * 损坏数据按空列表容错（同书签）。
     */
    public List<RecentFileEntry> getRecentFiles(String host, int port, String username) {
        JSONArray arr = readRecentFiles();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                return entriesFrom(obj.optJSONArray("files"));
            }
        }
        return new ArrayList<>();
    }

    /**
     * 记录一次打开（规范化后置顶、去重、上限 {@link RecentFilesHelper#MAX_RECENT}），
     * 立即持久化。时间戳=当前系统时间。
     */
    public void recordRecentFile(String host, int port, String username, String path) {
        if (host == null || username == null) return;
        long ts = System.currentTimeMillis();
        JSONArray arr = readRecentFiles();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                List<RecentFileEntry> updated = RecentFilesHelper.record(
                        entriesFrom(obj.optJSONArray("files")), path, ts);
                if (putEntries(obj, updated)) writeRecentFiles(arr);
                return;
            }
        }
        // 该服务器尚无历史记录：新建条目
        JSONObject obj = new JSONObject();
        try {
            obj.put("host", host)
                    .put("port", port)
                    .put("username", username);
            obj.put("files", entriesToJson(RecentFilesHelper.record(null, path, ts)));
            arr.put(obj);
            writeRecentFiles(arr);
        } catch (JSONException e) {
            Log.w(TAG, "保存最近打开失败", e);
        }
    }

    /** 移除单条历史（文件已不存在等场景）；该服务器历史清空后删除整条记录。 */
    public void removeRecentFile(String host, int port, String username, String path) {
        JSONArray arr = readRecentFiles();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null || !matchesServer(obj, host, port, username)) continue;
            List<RecentFileEntry> updated = RecentFilesHelper.remove(
                    entriesFrom(obj.optJSONArray("files")), path);
            if (updated.isEmpty()) {
                arr.remove(i);
            } else if (!putEntries(obj, updated)) {
                return;
            }
            writeRecentFiles(arr);
            return;
        }
    }

    /** 清空指定服务器全部历史（删除该服务器整条记录，无记录则不动）。 */
    public void clearRecentFiles(String host, int port, String username) {
        JSONArray arr = readRecentFiles();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                arr.remove(i);
                writeRecentFiles(arr);
                return;
            }
        }
    }

    private JSONArray readRecentFiles() {
        String json = prefs.getString(KEY_RECENT_FILES, "");
        if (json.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            Log.w(TAG, "读取最近打开失败，已忽略损坏的数据", e);
            return new JSONArray();
        }
    }

    private void writeRecentFiles(JSONArray arr) {
        prefs.edit().putString(KEY_RECENT_FILES, arr.toString()).apply();
    }

    // ── 阅读进度（「续读」，按服务器 host+port+username 隔离；走查 #40）──────────

    /**
     * 读取指定文件最近一次阅读进度（滚动像素 Y）；从未记录返回 0（无恢复需求）。
     * 值=Activity 旋转恢复 onSaveInstanceState 存 KEY_SCROLL_Y 的同一口径，仅跨会话。
     * 损坏数据按空 Map 容错（同书签）。
     */
    public int getReadProgress(String host, int port, String username, String path) {
        return ReadingProgressHelper.get(
                readReadProgress(),
                ReadingProgressHelper.key(host, port, username, path));
    }

    /**
     * 记录指定文件阅读进度（y &lt;= 0 清除条目）；无服务器上下文（host 为空）不写入。
     * 容量上限 {@link ReadingProgressHelper#MAX_ENTRIES}，溢出丢弃最旧（与最近打开自清理同决策）。
     */
    public void saveReadProgress(String host, int port, String username, String path, int y) {
        if (host == null) return;
        Map<String, Integer> next = ReadingProgressHelper.upsert(
                readReadProgress(),
                ReadingProgressHelper.key(host, port, username, path), y);
        writeReadProgress(next);
    }

    private Map<String, Integer> readReadProgress() {
        String json = prefs.getString(KEY_READ_PROGRESS, "");
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        if (json.isEmpty()) return result;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;
                String k = obj.optString("k", "");
                if (k.isEmpty()) continue;
                result.put(k, obj.optInt("v", 0));
            }
        } catch (JSONException e) {
            Log.w(TAG, "读取阅读进度失败，已忽略损坏的数据", e);
            return new LinkedHashMap<>();
        }
        return result;
    }

    private void writeReadProgress(Map<String, Integer> map) {
        JSONArray arr = new JSONArray();
        try {
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                arr.put(new JSONObject().put("k", e.getKey()).put("v", e.getValue()));
            }
        } catch (JSONException e) {
            Log.w(TAG, "序列化阅读进度失败", e);
            return;
        }
        prefs.edit().putString(KEY_READ_PROGRESS, arr.toString()).apply();
    }

    // ── 上次浏览目录（「继续上次位置」，按服务器 host+port+username 隔离）──────────

    /** 读取指定服务器上次浏览的目录路径；从未记录返回空串。损坏数据按空串容错（同书签）。 */
    public String getLastBrowsedDir(String host, int port, String username) {
        JSONArray arr = readLastBrowsedDirs();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                return BookmarkHelper.normalizePath(obj.optString("path", ""));
            }
        }
        return "";
    }

    /**
     * 记录上次浏览目录（规范化后覆盖，立即持久化）；
     * 规范化后为空（无效路径）时等价于 {@link #clearLastBrowsedDir}。
     */
    public void saveLastBrowsedDir(String host, int port, String username, String path) {
        if (host == null || username == null) return;
        String normalized = LastBrowseHelper.normalizeForSave(path);
        if (normalized.isEmpty()) {
            clearLastBrowsedDir(host, port, username);
            return;
        }
        JSONArray arr = readLastBrowsedDirs();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                try {
                    obj.put("path", normalized);
                    writeLastBrowsedDirs(arr);
                } catch (JSONException e) {
                    Log.w(TAG, "更新上次浏览目录失败", e);
                }
                return;
            }
        }
        // 该服务器尚无记录：新建条目
        JSONObject obj = new JSONObject();
        try {
            obj.put("host", host)
                    .put("port", port)
                    .put("username", username)
                    .put("path", normalized);
            arr.put(obj);
            writeLastBrowsedDirs(arr);
        } catch (JSONException e) {
            Log.w(TAG, "保存上次浏览目录失败", e);
        }
    }

    /** 清除该服务器上次浏览目录记录（目录已不存在等场景）；无记录则为无操作。 */
    public void clearLastBrowsedDir(String host, int port, String username) {
        JSONArray arr = readLastBrowsedDirs();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && matchesServer(obj, host, port, username)) {
                arr.remove(i);
                writeLastBrowsedDirs(arr);
                return;
            }
        }
    }

    private JSONArray readLastBrowsedDirs() {
        String json = prefs.getString(KEY_LAST_BROWSED_DIR, "");
        if (json.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            Log.w(TAG, "读取上次浏览目录失败，已忽略损坏的数据", e);
            return new JSONArray();
        }
    }

    private void writeLastBrowsedDirs(JSONArray arr) {
        prefs.edit().putString(KEY_LAST_BROWSED_DIR, arr.toString()).apply();
    }

    private static List<RecentFileEntry> entriesFrom(JSONArray files) {
        List<RecentFileEntry> list = new ArrayList<>();
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject obj = files.optJSONObject(i);
                if (obj == null) continue;
                String p = obj.optString("path", "");
                if (p.isEmpty()) continue;
                list.add(new RecentFileEntry(p, obj.optLong("ts", 0L)));
            }
        }
        return list;
    }

    private static JSONArray entriesToJson(List<RecentFileEntry> entries) {
        JSONArray arr = new JSONArray();
        for (RecentFileEntry e : entries) {
            if (e == null) continue;
            JSONObject obj = new JSONObject();
            try {
                obj.put("path", e.getPath()).put("ts", e.getTs());
            } catch (JSONException ex) {
                continue;
            }
            arr.put(obj);
        }
        return arr;
    }

    private static boolean putEntries(JSONObject obj, List<RecentFileEntry> entries) {
        try {
            obj.put("files", entriesToJson(entries));
            return true;
        } catch (JSONException e) {
            Log.w(TAG, "序列化最近打开失败", e);
            return false;
        }
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

    // ── 主机指纹（known_hosts，按 host+port 记录）───────────────────────────

    /**
     * 读取指定主机已记录的规范指纹；从未记录返回 null。
     * JSON 结构：{@code [{"host":..,"port":..,"fingerprint":..},..]}。
     */
    @Override
    public String getFingerprint(String host, int port) {
        JSONArray arr = readHostKeys();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && obj.optString("host", "").equals(host)
                    && obj.optInt("port", 0) == port) {
                String fp = obj.optString("fingerprint", "");
                return HostKeyHelper.isValidFingerprint(fp) ? fp : null;
            }
        }
        return null;
    }

    /** 记录/覆盖该主机指纹（规范化后存储）；非法指纹或空入参忽略。 */
    @Override
    public void saveFingerprint(String host, int port, String fingerprint) {
        if (host == null || host.isEmpty()) return;
        String normalized = HostKeyHelper.normalizeFingerprint(fingerprint);
        if (normalized.isEmpty()) return;
        JSONArray arr = readHostKeys();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && obj.optString("host", "").equals(host)
                    && obj.optInt("port", 0) == port) {
                try {
                    obj.put("fingerprint", normalized);
                    writeHostKeys(arr);
                } catch (JSONException e) {
                    Log.w(TAG, "更新主机指纹失败", e);
                }
                return;
            }
        }
        JSONObject obj = new JSONObject();
        try {
            obj.put("host", host).put("port", port).put("fingerprint", normalized);
            arr.put(obj);
            writeHostKeys(arr);
        } catch (JSONException e) {
            Log.w(TAG, "保存主机指纹失败", e);
        }
    }

    private JSONArray readHostKeys() {
        String json = prefs.getString(KEY_HOST_KEYS, "");
        if (json.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            Log.w(TAG, "读取主机指纹失败，已忽略损坏的数据", e);
            return new JSONArray();
        }
    }

    private void writeHostKeys(JSONArray arr) {
        prefs.edit().putString(KEY_HOST_KEYS, arr.toString()).apply();
    }

    // ── 连接分组元数据（组列表 CRUD；连接上的 group 字段见 ConnectionGroupHelper）──────

    /**
     * 读取分组列表（有序；已规范化并去重；不含空白「未分组」）。
     * 组列表与连接归属独立：空组（无连接）也保留，便于用户管理。
     */
    public List<String> getConnectionGroups() {
        return ConnectionGroupHelper.dedupeGroupNames(readConnectionGroupArray());
    }

    /**
     * 新增分组：组名规范化；空白忽略；已存在（规范化后）返回 false；成功返回 true。
     */
    public boolean addConnectionGroup(String name) {
        String normalized = ConnectionGroupHelper.normalizeGroupName(name);
        if (normalized.isEmpty()) return false;
        List<String> groups = getConnectionGroups();
        if (groups.contains(normalized)) return false;
        groups.add(normalized);
        writeConnectionGroups(groups);
        return true;
    }

    /**
     * 重命名分组：组内连接同步改为新组名（组名规范化；目标空白=拒绝）。
     * 若目标组名已存在 → 合并（旧组连接并入目标组，旧组从列表移除）。
     * 旧组不存在或新旧同名返回 false；成功返回 true。
     */
    public boolean renameConnectionGroup(String oldName, String newName) {
        String old = ConnectionGroupHelper.normalizeGroupName(oldName);
        String target = ConnectionGroupHelper.normalizeGroupName(newName);
        if (old.isEmpty() || target.isEmpty() || old.equals(target)) return false;

        List<String> groups = getConnectionGroups();
        int idx = groups.indexOf(old);
        if (idx < 0) return false;
        if (!groups.contains(target)) {
            groups.set(idx, target);
        } else {
            groups.remove(idx); // 目标已存在：合并，旧组直接移除
        }
        writeConnectionGroups(ConnectionGroupHelper.dedupeGroupNames(groups));

        // 同步连接归属：组内连接改为目标组
        List<SshConfig> updated = ConnectionGroupHelper.renameGroupInList(
                getSavedConnections(), old, target);
        saveConnectionList(updated);
        return true;
    }

    /**
     * 删除分组：组内连接退回「未分组」（group 置空），不级联删除连接（决策已定）。
     * 组不存在返回 false；成功返回 true。
     */
    public boolean deleteConnectionGroup(String name) {
        String group = ConnectionGroupHelper.normalizeGroupName(name);
        if (group.isEmpty()) return false;
        List<String> groups = getConnectionGroups();
        if (!groups.remove(group)) return false;
        writeConnectionGroups(groups);

        List<SshConfig> updated = ConnectionGroupHelper.ungroupAllInList(
                getSavedConnections(), group);
        saveConnectionList(updated);
        return true;
    }

    private List<String> readConnectionGroupArray() {
        String json = prefs.getString(KEY_CONNECTION_GROUPS, "");
        List<String> list = new ArrayList<>();
        if (json.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                String name = arr.optString(i, "");
                if (!name.isEmpty()) list.add(name);
            }
        } catch (JSONException e) {
            Log.w(TAG, "读取连接分组失败，已忽略损坏的数据", e);
        }
        return list;
    }

    private void writeConnectionGroups(List<String> groups) {
        prefs.edit().putString(KEY_CONNECTION_GROUPS, new JSONArray(
                ConnectionGroupHelper.dedupeGroupNames(groups)).toString()).apply();
    }

    // ── 端口转发规则（按服务器连接键隔离；连接键派生见 SshConnectionHelper）──────────

    /**
     * 读取指定服务器（连接键）的端口转发规则列表（保存顺序）。
     * JSON 结构：{@code [{"key":"host:port:user","rules":[{"name":..,"localPort":..,
     * "remoteHost":..,"remotePort":..,"bindAddress":..},..]},..]}；
     * 损坏 JSON 按空列表（与其他偏好读取容错一致）。
     */
    public List<PortForwardRule> getPortForwardRules(String serverKey) {
        if (serverKey == null || serverKey.isEmpty()) return new ArrayList<>();
        JSONArray arr = readPortForwardRules();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null || !serverKey.equals(obj.optString("key", ""))) continue;
            return rulesFrom(obj.optJSONArray("rules"));
        }
        return new ArrayList<>();
    }

    /**
     * 新增/覆盖规则：同一服务器内本地端口已存在则覆盖（规则以本地端口为唯一标识），
     * 否则追加保存。返回 true（与分组 add 不同：覆盖合法，不判重拒绝）。
     */
    public boolean savePortForwardRule(String serverKey, PortForwardRule rule) {
        if (serverKey == null || serverKey.isEmpty() || rule == null) return false;
        JSONArray arr = readPortForwardRules();
        JSONObject entry = null;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj != null && serverKey.equals(obj.optString("key", ""))) {
                entry = obj;
                break;
            }
        }
        try {
            List<PortForwardRule> rules = entry == null ? new ArrayList<>()
                    : rulesFrom(entry.optJSONArray("rules"));
            boolean replaced = false;
            for (int i = 0; i < rules.size(); i++) {
                if (rules.get(i).getLocalPort() == rule.getLocalPort()) {
                    rules.set(i, rule);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) rules.add(rule);
            if (entry == null) {
                entry = new JSONObject()
                        .put("key", serverKey)
                        .put("rules", rulesToArray(rules));
                arr.put(entry);
            } else {
                entry.put("rules", rulesToArray(rules));
            }
            writePortForwardRules(arr);
            return true;
        } catch (JSONException e) {
            Log.w(TAG, "保存端口转发规则失败", e);
            return false;
        }
    }

    /** 删除指定服务器本地端口为 {@code localPort} 的规则；失败返回 false。 */
    public boolean deletePortForwardRule(String serverKey, int localPort) {
        if (serverKey == null || serverKey.isEmpty()) return false;
        JSONArray arr = readPortForwardRules();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null || !serverKey.equals(obj.optString("key", ""))) continue;
            List<PortForwardRule> rules = rulesFrom(obj.optJSONArray("rules"));
            boolean removed = false;
            for (int j = 0; j < rules.size(); j++) {
                if (rules.get(j).getLocalPort() == localPort) {
                    rules.remove(j);
                    removed = true;
                    break;
                }
            }
            if (!removed) return false;
            try {
                if (rules.isEmpty()) {
                    arr.remove(i); // 该服务器规则已空：删除整条记录
                } else {
                    obj.put("rules", rulesToArray(rules));
                }
                writePortForwardRules(arr);
                return true;
            } catch (JSONException e) {
                Log.w(TAG, "删除端口转发规则失败", e);
                return false;
            }
        }
        return false;
    }

    private JSONArray readPortForwardRules() {
        String json = prefs.getString(KEY_PORT_FORWARD_RULES, "");
        if (json.isEmpty()) return new JSONArray();
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            Log.w(TAG, "读取端口转发规则失败，已忽略损坏的数据", e);
            return new JSONArray();
        }
    }

    private void writePortForwardRules(JSONArray arr) {
        prefs.edit().putString(KEY_PORT_FORWARD_RULES, arr.toString()).apply();
    }

    private static List<PortForwardRule> rulesFrom(JSONArray rules) {
        List<PortForwardRule> list = new ArrayList<>();
        if (rules != null) {
            for (int i = 0; i < rules.length(); i++) {
                JSONObject obj = rules.optJSONObject(i);
                if (obj == null) continue;
                PortForwardRule rule = new PortForwardRule();
                rule.setName(obj.optString("name", ""));
                rule.setLocalPort(obj.optInt("localPort", 0));
                rule.setRemoteHost(obj.optString("remoteHost", ""));
                rule.setRemotePort(obj.optInt("remotePort", 0));
                rule.setBindAddress(obj.optString("bindAddress", ""));
                list.add(rule);
            }
        }
        return list;
    }

    private static JSONArray rulesToArray(List<PortForwardRule> rules) throws JSONException {
        JSONArray array = new JSONArray();
        for (PortForwardRule rule : rules) {
            JSONObject obj = new JSONObject();
            if (rule.getName() != null && !rule.getName().isEmpty()) {
                obj.put("name", rule.getName());
            }
            obj.put("localPort", rule.getLocalPort());
            obj.put("remoteHost", rule.getRemoteHost());
            obj.put("remotePort", rule.getRemotePort());
            if (rule.getBindAddress() != null && !rule.getBindAddress().isEmpty()) {
                obj.put("bindAddress", rule.getBindAddress());
            }
            array.put(obj);
        }
        return array;
    }
}
