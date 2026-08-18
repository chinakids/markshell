package com.ssh.mdreader.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.ssh.mdreader.model.SshConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class PreferenceManager {
    private static final String PREF_NAME = "ssh_md_reader_prefs";
    private static final String KEY_SAVED_CONNECTIONS = "saved_connections";
    private static final String KEY_FONT_SIZE = "font_size";
    private static final String KEY_SHOW_HIDDEN = "show_hidden";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
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
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                SshConfig config = new SshConfig();
                config.setAlias(obj.optString("alias", ""));
                config.setHost(obj.optString("host", ""));
                config.setPort(obj.optInt("port", 22));
                config.setUsername(obj.optString("username", ""));
                config.setPassword(obj.optString("password", ""));
                config.setRemotePath(obj.optString("remotePath", "/"));
                list.add(config);
            }
        } catch (JSONException ignored) {
        }
        return list;
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
                obj.put("password", c.getPassword());
                obj.put("remotePath", c.getRemotePath());
                arr.put(obj);
            } catch (JSONException ignored) {
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
}
