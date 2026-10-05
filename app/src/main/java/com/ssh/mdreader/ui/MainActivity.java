package com.ssh.mdreader.ui;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ssh.mdreader.R;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.AboutHelper;
import com.ssh.mdreader.util.ConnectionCopyHelper;
import com.ssh.mdreader.util.ConnectionFormHelper;
import com.ssh.mdreader.util.ConnectionSearchHelper;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PortForwardDialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.SshConnectionHelper;
import com.ssh.mdreader.util.ThemeHelper;
import com.ssh.mdreader.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity {

    private RecyclerView recycler;
    private View layoutEmpty;
    private PreferenceManager prefManager;
    private HomeAdapter adapter;
    private List<SshConfig> savedList;
    private View connSearchBar;
    private EditText etConnSearch;
    private TextView tvConnNoMatch;
    private boolean searchActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        prefManager = new PreferenceManager(this);
        // 启动时把持久化的心跳间隔注入 SshManager（下次连接/重连生效）
        SshManager.getInstance().setHeartbeatIntervalMs(prefManager.getHeartbeatIntervalMs());
        recycler = findViewById(R.id.recycler_home);
        layoutEmpty = findViewById(R.id.layout_empty);
        connSearchBar = findViewById(R.id.conn_search_bar);
        etConnSearch = findViewById(R.id.et_conn_search);
        tvConnNoMatch = findViewById(R.id.tv_conn_no_match);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add);

        adapter = new HomeAdapter();
        adapter.setOnConnectListener(this::quickConnect);
        adapter.setOnEditListener(this::editConnection);
        adapter.setOnDeleteListener(this::deleteConnection);
        adapter.setOnPortForwardListener(this::showPortForwardManagerDialog);
        adapter.setOnDuplicateListener(this::duplicateConnection);
        adapter.setOnSettingsClickListener(this::showAppSettings);
        adapter.setOnSearchClickListener(this::toggleSearchBar);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        // ── 连接搜索栏（#26；样式/语义与文件筛选栏一致：实时过滤、关闭即清空） ──
        etConnSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (searchActive) {
                    loadData();
                }
            }
        });
        findViewById(R.id.btn_conn_search_close).setOnClickListener(v -> hideSearchBar());

        fabAdd.setOnClickListener(v -> {
            if (isLargeScreen()) {
                showConnectionDialog();
            } else {
                startActivity(new Intent(this, ConnectionActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        savedList = prefManager.getSavedConnections();
        String query = etConnSearch.getText() != null ? etConnSearch.getText().toString() : "";
        boolean filtering = searchActive && ConnectionSearchHelper.active(query);
        if (filtering) {
            List<SshConfig> filtered = ConnectionSearchHelper.filter(savedList, query);
            adapter.setData(filtered, filtered.size() + " / " + savedList.size() + " 个匹配");
            tvConnNoMatch.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
            layoutEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        } else {
            adapter.setData(savedList, null);
            tvConnNoMatch.setVisibility(View.GONE);
            if (savedList.isEmpty()) {
                layoutEmpty.setVisibility(View.VISIBLE);
                recycler.setVisibility(View.GONE);
            } else {
                layoutEmpty.setVisibility(View.GONE);
                recycler.setVisibility(View.VISIBLE);
            }
        }
    }

    /** 列表 header 搜索按钮：切换搜索栏显隐。 */
    private void toggleSearchBar() {
        if (connSearchBar.getVisibility() == View.VISIBLE) {
            hideSearchBar();
        } else {
            showSearchBar();
        }
    }

    private void showSearchBar() {
        searchActive = true;
        connSearchBar.setVisibility(View.VISIBLE);
        etConnSearch.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etConnSearch, InputMethodManager.SHOW_IMPLICIT);
        }
        loadData();
    }

    private void hideSearchBar() {
        searchActive = false;
        connSearchBar.setVisibility(View.GONE);
        if (etConnSearch.getText() != null) {
            etConnSearch.getText().clear();
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etConnSearch.getWindowToken(), 0);
        }
        loadData();
    }

    private void quickConnect(SshConfig config, int position) {
        boolean needsPasswordEntry = !config.isKeyAuth()
                && (config.getPassword() == null || config.getPassword().isEmpty());
        if (needsPasswordEntry) {
            Intent intent = new Intent(this, ConnectionActivity.class);
            intent.putExtra("alias", config.getAlias());
            intent.putExtra("host", config.getHost());
            intent.putExtra("port", config.getPort());
            intent.putExtra("username", config.getUsername());
            intent.putExtra("remotePath", config.getRemotePath());
            startActivity(intent);
            return;
        }

        adapter.setClickable(false);
        adapter.setConnecting(position);
        SshManager.getInstance().setHostKeyStore(prefManager);
        SshManager.getInstance().setPortForwardRules(
                prefManager.getPortForwardRules(SshConnectionHelper.deriveConnectionKey(config)));
        SshManager.getInstance().connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    adapter.clearConnecting();
                    adapter.setClickable(true);
                    if (SshManager.getInstance().consumeFingerprintFirstSeen()) {
                        UiUtils.showToast(MainActivity.this, getString(R.string.host_key_recorded));
                    }
                    String report = SshManager.getInstance().consumePortForwardReport();
                    if (report != null) {
                        UiUtils.showToast(MainActivity.this, report);
                    }
                    UiUtils.showToast(MainActivity.this, "已连接");
                    Intent intent = new Intent(MainActivity.this, FileBrowserActivity.class);
                    // 仅显式主目录（remotePath）才指定起始位置；未配置则交由文件浏览器
                    // 恢复上次浏览目录（「继续上次位置」）。
                    String path = config.getRemotePath();
                    if (path != null && !path.isEmpty()) {
                        intent.putExtra("remote_path", path);
                    }
                    startActivity(intent);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    adapter.clearConnecting();
                    adapter.setClickable(true);
                    DialogHelper.showConfirmDialog(MainActivity.this,
                            "连接失败", message,
                            "重新连接", "取消",
                            (d) -> quickConnect(config, position),
                            (d) -> {});
                });
            }

            @Override
            public void onHostKeyChanged(String expected, String actual) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    // 先恢复列表可点，避免弹窗期间/取消后卡片永久 connecting
                    adapter.clearConnecting();
                    adapter.setClickable(true);
                    DialogHelper.showHostKeyChangedDialog(MainActivity.this,
                            prefManager, config.getHost(), config.getPort(),
                            expected, actual,
                            () -> quickConnect(config, position),
                            () -> {});
                });
            }

            @Override
            public void onDisconnected() {
            }
        });
    }

    private void editConnection(SshConfig config, int position) {
        Intent intent = new Intent(this, ConnectionActivity.class);
        intent.putExtra("is_edit", true);
        intent.putExtra("edit_index", position);
        intent.putExtra("alias", config.getAlias());
        intent.putExtra("host", config.getHost());
        intent.putExtra("port", config.getPort());
        intent.putExtra("username", config.getUsername());
        intent.putExtra("password", config.getPassword());
        intent.putExtra("remotePath", config.getRemotePath());
        intent.putExtra("authMode", config.getAuthMode());
        intent.putExtra("privateKey", config.getPrivateKey());
        intent.putExtra("keyPassphrase", config.getKeyPassphrase());
        startActivity(intent);
    }

    /** 复制连接（#28）：以现有配置预填「新建连接」表单（别名自动加「（副本）」，可改），
     * 保存后新增条目，不覆盖原连接（edit_index=-1）。与 SavedConnectionsActivity
     * duplicateConnection 行为一致（同属连接 CRUD 复制语义）。 */
    private void duplicateConnection(SshConfig config) {
        Intent intent = new Intent(this, ConnectionActivity.class);
        intent.putExtra("alias", ConnectionCopyHelper.duplicateName(config.getAlias()));
        intent.putExtra("host", config.getHost());
        intent.putExtra("port", config.getPort());
        intent.putExtra("username", config.getUsername());
        intent.putExtra("password", config.getPassword());
        intent.putExtra("remotePath", config.getRemotePath());
        intent.putExtra("authMode", config.getAuthMode());
        intent.putExtra("privateKey", config.getPrivateKey());
        intent.putExtra("keyPassphrase", config.getKeyPassphrase());
        intent.putExtra("group", config.getGroup());
        startActivity(intent);
    }

    /** 端口转发规则管理（共享实现见 PortForwardDialogHelper；与 SavedConnectionsActivity 同源）。 */
    private void showPortForwardManagerDialog(SshConfig config) {
        PortForwardDialogHelper.showManager(this, prefManager, config);
    }

    private void deleteConnection(int position) {
        if (position < 0 || position >= savedList.size()) return;
        SshConfig config = savedList.get(position);
        String name = config.getDisplayName();
        DialogHelper.showDangerConfirmDialog(this,
                "删除连接", "确定删除 \"" + name + "\" ？",
                "删除", "取消",
                (d) -> {
                    prefManager.deleteConnection(position);
                    loadData();
                },
                (d) -> {});
    }

    // ── 设置（外观主题 + 连接保活）──────────────────────────────────────────

    private static final int[] HEARTBEAT_OPTIONS_MS = {5_000, 10_000, 30_000, 60_000};

    /**
     * 设置入口（首页 header 齿轮）：一级菜单列出设置分组，逐组二级选择。
     * 走查 #54——设置域标配入口（markor SettingsActivity 分组式；Material Files 同）：
     * 此前齿轮仅直连心跳单项，主题三态无入口；现改为「外观 · 主题 / 连接保活 · 心跳间隔」两分组。
     * 第四十二轮：补「关于 · 应用信息」分组（action_about 死字符串 + versionName/versionCode
     * 无处展示，markor MoreInfoFragment Version v%s (%d) 同型=设置域标配）。
     */
    private void showAppSettings() {
        if (isFinishing() || isDestroyed()) return;
        DialogHelper.showListDialog(this,
                "设置",
                new String[]{"外观 · 主题", "连接保活 · 心跳间隔", "关于 · 应用信息"},
                null,
                (dialog, which) -> {
                    if (which == 0) showThemeSettings();
                    else if (which == 1) showHeartbeatSettings();
                    else showAboutDialog();
                });
    }

    /**
     * 关于/版本页：应用名 + 版本行（markor MoreInfoFragment Version v%s (%d) 同型）+ 包名。
     * versionName/versionCode 经 PackageManager 读取（BuildConfig 未启用）；
     * 版本行格式化委托 AboutHelper（单一语义源）；PackageManager 异常防御回退「?」。
     */
    private void showAboutDialog() {
        if (isFinishing() || isDestroyed()) return;
        String versionName = "?";
        int versionCode = 0;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = info.versionName;
            versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? (int) info.getLongVersionCode()
                    : info.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
            // 应用自身不可能查不到自己的包信息；防御性回退，展示「v? (0)」不崩溃。
        }
        String message = getString(R.string.app_name) + "\n"
                + "Version " + AboutHelper.versionLine(versionName, versionCode) + "\n\n"
                + "包名：" + getPackageName();
        DialogHelper.showMessageDialog(this, getString(R.string.action_about),
                message, "确定", (dialog) -> {
                });
    }

    /**
     * 外观主题六态（跟随系统/自动 22:00-06:00/自动 09:00-17:00/浅色/深色/深色·纯黑）：
     * markor pref_arrkeys__app_themes 全集（system/auto/autocompat/light/dark/dark-black），
     * 走查 #57——三态扩展项 auto/autocompat/dark-black 曾按触发制观察=配置类功能误分类
     * （教训㊼ 同型），本轮纠偏实施；行为语义=GsContextUtils.applyDayNightTheme 源码实证
     * （auto→MODE_NIGHT_AUTO 22:00-06:00、autocompat→isCurrentHourOfDayBetween(9,17)、
     * dark-black→contains("dark") 同 MODE_NIGHT_YES 且无独立资源=与 dark 等价，如实标注）。
     * Material Files settings_theme 三态同型（设置域标配）。
     */
    private void showThemeSettings() {
        if (isFinishing() || isDestroyed()) return;
        String current = ThemeHelper.normalize(prefManager.getThemeMode());
        String label = themeLabelFor(current);
        DialogHelper.showListDialog(this,
                "外观 · 当前" + label,
                new String[]{"跟随系统", "自动（22:00–06:00 深色）", "自动（09:00–17:00 浅色）",
                        "浅色", "深色", "深色 · 纯黑"},
                null,
                (dialog, which) -> {
                    String mode = which == 1 ? ThemeHelper.THEME_AUTO
                            : which == 2 ? ThemeHelper.THEME_AUTOCOMPAT
                            : which == 3 ? ThemeHelper.THEME_LIGHT
                            : which == 4 ? ThemeHelper.THEME_DARK
                            : which == 5 ? ThemeHelper.THEME_DARK_BLACK
                            : ThemeHelper.THEME_SYSTEM;
                    prefManager.saveThemeMode(mode);
                    // AppCompatDelegate.setDefaultNightMode 触发全局配置变化，主界面自动重建；
                    // 主题变化本身即视觉反馈，无需 toast。映射单一语义源=BaseActivity（防漂移）。
                    AppCompatDelegate.setDefaultNightMode(resolveAppCompatNightMode(mode));
                });
    }

    private static String themeLabelFor(String mode) {
        if (ThemeHelper.THEME_LIGHT.equals(mode)) return "浅色";
        if (ThemeHelper.THEME_DARK.equals(mode)) return "深色";
        if (ThemeHelper.THEME_DARK_BLACK.equals(mode)) return "深色 · 纯黑";
        if (ThemeHelper.THEME_AUTO.equals(mode)) return "自动（22:00–06:00 深色）";
        if (ThemeHelper.THEME_AUTOCOMPAT.equals(mode)) return "自动（09:00–17:00 浅色）";
        return "跟随系统";
    }

    /**
     * 连接保活设置：用户选择心跳间隔后持久化到偏好，并立即注入 SshManager。
     * 对已建立连接不生效，下次 connect/自动重连时生效（JSch 的 setServerAliveInterval）。
     */
    private void showHeartbeatSettings() {
        if (isFinishing() || isDestroyed()) return;
        int current = prefManager.getHeartbeatIntervalMs();
        DialogHelper.showListDialog(this,
                "连接保活 · 当前心跳 " + (current / 1000) + " 秒",
                new String[]{"心跳间隔：5 秒（默认）", "心跳间隔：10 秒",
                        "心跳间隔：30 秒", "心跳间隔：60 秒"},
                null,
                (dialog, which) -> {
                    int ms = HEARTBEAT_OPTIONS_MS[which];
                    prefManager.saveHeartbeatIntervalMs(ms);
                    SshManager.getInstance().setHeartbeatIntervalMs(ms);
                    UiUtils.showToast(this, "已保存，下次连接生效");
                });
    }

    // ── Large-screen: connection dialog ──────────────────────────────────────

    /**
     * Shows the new-connection form as a brand-styled dialog instead of
     * launching the full-screen Activity (foldable-unfolded / tablet layout).
     */
    private void showConnectionDialog() {
        if (isFinishing() || isDestroyed()) return;

        Dialog dialog = new Dialog(this, R.style.BrandDialog);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_connection, null);
        dialog.setContentView(view);

        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.8f);
            lp.gravity = Gravity.CENTER;
            window.setAttributes(lp);
        }
        dialog.setCancelable(true);

        MaterialButton btnSave      = view.findViewById(R.id.btn_save_only);
        MaterialButton btnConnect   = view.findViewById(R.id.btn_connect);
        ProgressBar    progressBar  = view.findViewById(R.id.progress_bar);
        MaterialButtonToggleGroup toggleAuthMode = view.findViewById(R.id.toggle_auth_mode);
        TextInputLayout tilPassword = view.findViewById(R.id.til_password);
        View layoutKeyFields = view.findViewById(R.id.layout_key_fields);

        // 缺省=密码认证；切换时显示/隐藏对应凭据区（与 ConnectionActivity 行为一致）
        toggleAuthMode.check(R.id.btn_auth_password);
        toggleAuthMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            boolean keyMode = checkedId == R.id.btn_auth_key;
            tilPassword.setVisibility(keyMode ? View.GONE : View.VISIBLE);
            layoutKeyFields.setVisibility(keyMode ? View.VISIBLE : View.GONE);
        });

        btnSave.setOnClickListener(v -> {
            SshConfig config = validateAndBuildConfig(view, btnSave);
            if (config == null) return;
            prefManager.saveConnection(config);
            dialog.dismiss();
            UiUtils.showToast(this, "已保存");
            loadData();
        });

        btnConnect.setOnClickListener(v -> {
            SshConfig config = validateAndBuildConfig(view, btnConnect);
            if (config == null) return;
            connectFromDialog(dialog, config, progressBar, btnConnect);
        });

        dialog.show();
    }

    private void connectFromDialog(Dialog dialog, SshConfig config,
                                    ProgressBar progressBar, MaterialButton btnConnect) {
        if (isFinishing() || isDestroyed()) return;
        progressBar.setVisibility(View.VISIBLE);
        btnConnect.setEnabled(false);
        btnConnect.setText(R.string.msg_connecting);

        SshManager.getInstance().setHostKeyStore(prefManager);
        SshManager.getInstance().setPortForwardRules(
                prefManager.getPortForwardRules(SshConnectionHelper.deriveConnectionKey(config)));
        SshManager.getInstance().connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    btnConnect.setEnabled(true);
                    btnConnect.setText(R.string.btn_connect);
                    if (SshManager.getInstance().consumeFingerprintFirstSeen()) {
                        UiUtils.showToast(MainActivity.this, getString(R.string.host_key_recorded));
                    }
                    String report = SshManager.getInstance().consumePortForwardReport();
                    if (report != null) {
                        UiUtils.showToast(MainActivity.this, report);
                    }
                    prefManager.saveConnection(config);
                    dialog.dismiss();
                    UiUtils.showToast(MainActivity.this, getString(R.string.msg_connected));

                    Intent intent = new Intent(MainActivity.this, FileBrowserActivity.class);
                    // 仅显式主目录（remotePath）才指定起始位置；未配置则交由文件浏览器
                    // 恢复上次浏览目录（「继续上次位置」）。
                    String path = config.getRemotePath();
                    if (path != null && !path.isEmpty()) {
                        intent.putExtra("remote_path", path);
                    }
                    startActivity(intent);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    btnConnect.setEnabled(true);
                    btnConnect.setText(R.string.btn_connect);
                    DialogHelper.showConfirmDialog(MainActivity.this,
                            "连接失败", message,
                            "重试", "取消",
                            d -> connectFromDialog(dialog, config, progressBar, btnConnect),
                            d -> {});
                });
            }

            @Override
            public void onHostKeyChanged(String expected, String actual) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    btnConnect.setEnabled(true);
                    btnConnect.setText(R.string.btn_connect);
                    DialogHelper.showHostKeyChangedDialog(MainActivity.this,
                            prefManager, config.getHost(), config.getPort(),
                            expected, actual,
                            () -> connectFromDialog(dialog, config, progressBar, btnConnect),
                            () -> {});
                });
            }

            @Override
            public void onDisconnected() {
            }
        });
    }

    /**
     * Validates the dialog form fields and builds an {@link SshConfig}.
     * Password/私钥/口令 are NOT trimmed; all other fields are trimmed.
     * 认证方式由 {@code toggle_auth_mode} 决定（密码/私钥），凭据按模式校验。
     *
     * @return the config, or null when validation fails
     */
    private SshConfig validateAndBuildConfig(View root, View anchor) {
        TextInputEditText etAlias      = root.findViewById(R.id.et_alias);
        TextInputEditText etHost       = root.findViewById(R.id.et_host);
        TextInputEditText etPort       = root.findViewById(R.id.et_port);
        TextInputEditText etUsername   = root.findViewById(R.id.et_username);
        TextInputEditText etPassword   = root.findViewById(R.id.et_password);
        TextInputEditText etPrivateKey = root.findViewById(R.id.et_private_key);
        TextInputEditText etKeyPassphrase = root.findViewById(R.id.et_key_passphrase);
        TextInputEditText etRemotePath = root.findViewById(R.id.et_remote_path);
        MaterialButtonToggleGroup toggleAuthMode = root.findViewById(R.id.toggle_auth_mode);
        boolean keyMode = toggleAuthMode.getCheckedButtonId() == R.id.btn_auth_key;

        String alias      = text(etAlias);
        String host       = text(etHost);
        String portStr    = text(etPort);
        String username   = text(etUsername);
        String password   = rawText(etPassword);
        String remotePath = text(etRemotePath);
        String privateKey = keyMode ? rawText(etPrivateKey) : "";
        String keyPassphrase = keyMode ? rawText(etKeyPassphrase) : "";

        if (host.isEmpty()) {
            etHost.setError(getString(R.string.error_invalid_host));
            return null;
        }
        if (username.isEmpty() || (!keyMode && password.isEmpty())) {
            UiUtils.showSnackbar(anchor, getString(
                    keyMode ? R.string.error_invalid_key_credentials : R.string.error_invalid_credentials));
            return null;
        }
        if (keyMode && privateKey.trim().isEmpty()) {
            etPrivateKey.setError(getString(R.string.error_invalid_key));
            return null;
        }

        int port = ConnectionFormHelper.parsePort(portStr, ConnectionFormHelper.DEFAULT_PORT);
        if (port == ConnectionFormHelper.INVALID_PORT) {
            etPort.setError(getString(R.string.error_invalid_port));
            return null;
        }

        SshConfig config = new SshConfig(alias, host, port, username, password,
                remotePath.isEmpty() ? "" : remotePath);
        config.setAuthMode(keyMode ? SshConfig.AUTH_KEY : SshConfig.AUTH_PASSWORD);
        config.setPrivateKey(privateKey);
        config.setKeyPassphrase(keyPassphrase);
        // 最终防线：模型契约校验（与上方字段级预检同源；正常路径永不触发）。
        if (!config.isValid()) {
            UiUtils.showSnackbar(anchor, getString(R.string.error_invalid_connection));
            return null;
        }
        return config;
    }

    private String text(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String rawText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString() : "";
    }

    private static class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        public interface OnConnectListener {
            void onConnect(SshConfig config, int position);
        }

        public interface OnSettingsClickListener {
            void onSettings();
        }

        public interface OnSearchClickListener {
            void onSearch();
        }

        public interface OnEditListener {
            void onEdit(SshConfig config, int position);
        }

        public interface OnDeleteListener {
            void onDelete(int position);
        }

        public interface OnPortForwardListener {
            void onPortForward(SshConfig config);
        }

        public interface OnDuplicateListener {
            void onDuplicate(SshConfig config);
        }

        private List<SshConfig> data = new ArrayList<>();
        private OnConnectListener connectListener;
        private OnEditListener editListener;
        private OnDeleteListener deleteListener;
        private OnPortForwardListener portForwardListener;
        private OnDuplicateListener duplicateListener;
        private OnSettingsClickListener settingsListener;
        private OnSearchClickListener searchListener;
        private boolean clickable = true;
        private int connectingPosition = -1;
        /** 列表头计数文本；null=默认「N 个已保存连接」（搜索匹配时由外部传入）。 */
        private String headerText = null;

        void setOnConnectListener(OnConnectListener l) { connectListener = l; }
        void setOnEditListener(OnEditListener l) { editListener = l; }
        void setOnDeleteListener(OnDeleteListener l) { deleteListener = l; }
        void setOnPortForwardListener(OnPortForwardListener l) { portForwardListener = l; }
        void setOnDuplicateListener(OnDuplicateListener l) { duplicateListener = l; }
        void setOnSettingsClickListener(OnSettingsClickListener l) { settingsListener = l; }
        void setOnSearchClickListener(OnSearchClickListener l) { searchListener = l; }
        void setClickable(boolean clickable) { this.clickable = clickable; }
        void setConnecting(int position) { connectingPosition = position; notifyDataSetChanged(); }
        void clearConnecting() { connectingPosition = -1; notifyDataSetChanged(); }

        void setData(List<SshConfig> data, String headerText) {
            this.data = data != null ? data : new ArrayList<>();
            this.headerText = headerText;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_ITEM;
        }

        @Override
        public int getItemCount() {
            return data.size() + 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View view = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_home_header, parent, false);
                return new HeaderVH(view);
            }
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_connection, parent, false);
            return new ItemVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderVH) {
                HeaderVH h = (HeaderVH) holder;
                h.tvCount.setText(headerText != null
                        ? headerText
                        : data.size() + " 个已保存连接");
                h.btnSettings.setOnClickListener(v -> {
                    if (settingsListener != null) settingsListener.onSettings();
                });
                h.btnSearch.setOnClickListener(v -> {
                    if (searchListener != null) searchListener.onSearch();
                });
            } else if (holder instanceof ItemVH) {
                ItemVH h = (ItemVH) holder;
                int dataPos = position - 1;
                SshConfig config = data.get(dataPos);
                String alias = config.getAlias();
                if (alias != null && !alias.isEmpty()) {
                    h.tvAlias.setText(alias);
                    h.tvAlias.setVisibility(View.VISIBLE);
                    h.tvHost.setText(config.getHost() + ":" + config.getPort());
                } else {
                    h.tvAlias.setVisibility(View.GONE);
                    h.tvHost.setText(config.getHost() + ":" + config.getPort());
                }
                h.tvUser.setText(config.getUsername());
                String path = config.getRemotePath();
                h.tvPath.setText(path == null || path.isEmpty() ? "/" : path);

                // 连接中状态：显示 ProgressBar，降低文字透明度
                boolean isConnecting = (dataPos == connectingPosition);
                h.progressConnecting.setVisibility(isConnecting ? View.VISIBLE : View.GONE);
                float alpha = isConnecting ? 0.5f : 1f;
                h.tvAlias.setAlpha(alpha);
                h.tvHost.setAlpha(alpha);
                h.tvUser.setAlpha(alpha);
                h.tvPath.setAlpha(alpha);

                h.itemView.setOnClickListener(v -> {
                    if (clickable && connectListener != null) {
                        connectListener.onConnect(config, dataPos);
                    }
                });
                h.itemView.setOnLongClickListener(v -> {
                    String name = config.getDisplayName();
                    DialogHelper.showListDialog(v.getContext(), name,
                            new String[]{"编辑", "端口转发…", "复制", "删除"},
                            new int[]{R.drawable.ic_edit, R.drawable.ic_tunnel,
                                    R.drawable.ic_content_copy, R.drawable.ic_delete},
                            (dialog, which) -> {
                                if (which == 0) {
                                    if (editListener != null) editListener.onEdit(config, dataPos);
                                } else if (which == 1) {
                                    if (portForwardListener != null) portForwardListener.onPortForward(config);
                                } else if (which == 2) {
                                    if (duplicateListener != null) duplicateListener.onDuplicate(config);
                                } else if (which == 3) {
                                    if (deleteListener != null) deleteListener.onDelete(dataPos);
                                }
                            });
                    return true;
                });
            }
        }

        static class HeaderVH extends RecyclerView.ViewHolder {
            final TextView tvCount;
            final ImageButton btnSettings;
            final ImageButton btnSearch;

            HeaderVH(@NonNull View itemView) {
                super(itemView);
                tvCount = itemView.findViewById(R.id.tv_connection_count);
                btnSettings = itemView.findViewById(R.id.btn_settings);
                btnSearch = itemView.findViewById(R.id.btn_search);
            }
        }

        static class ItemVH extends RecyclerView.ViewHolder {
            final TextView tvAlias, tvHost, tvUser, tvPath;
            final ProgressBar progressConnecting;

            ItemVH(@NonNull View itemView) {
                super(itemView);
                tvAlias = itemView.findViewById(R.id.tv_saved_alias);
                tvHost = itemView.findViewById(R.id.tv_saved_host);
                tvUser = itemView.findViewById(R.id.tv_saved_user);
                tvPath = itemView.findViewById(R.id.tv_saved_path);
                progressConnecting = itemView.findViewById(R.id.progress_connecting);
            }
        }
    }
}
