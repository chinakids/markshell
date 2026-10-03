package com.ssh.mdreader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ssh.mdreader.R;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.ConnectionGroupHelper;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.SshConnectionHelper;
import com.ssh.mdreader.util.UiUtils;

public class ConnectionActivity extends BaseActivity {

    private TextInputEditText etAlias, etHost, etPort, etUsername, etPassword, etRemotePath;
    private TextInputEditText etPrivateKey, etKeyPassphrase, etGroup;
    private TextInputLayout tilPassword;
    private View layoutKeyFields;
    private MaterialButtonToggleGroup toggleAuthMode;
    private MaterialButton btnConnect;
    private ProgressBar progressBar;
    private PreferenceManager prefManager;
    private int editIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        boolean isEdit = getIntent().getBooleanExtra("is_edit", false);
        editIndex = getIntent().getIntExtra("edit_index", -1);
        setupToolbar(isEdit ? "编辑连接" : "新建连接", true);

        prefManager = new PreferenceManager(this);
        initViews();
        loadSavedConfig();
    }

    private void loadSavedConfig() {
        Intent intent = getIntent();
        if (intent.hasExtra("alias")) {
            etAlias.setText(intent.getStringExtra("alias"));
        }
        if (intent.hasExtra("host")) {
            etHost.setText(intent.getStringExtra("host"));
            etPort.setText(String.valueOf(intent.getIntExtra("port", 22)));
            etUsername.setText(intent.getStringExtra("username"));
            if (intent.hasExtra("remotePath")) {
                etRemotePath.setText(intent.getStringExtra("remotePath"));
            }
            if (intent.hasExtra("group")) {
                etGroup.setText(intent.getStringExtra("group"));
            }
            if (intent.hasExtra("password")) {
                etPassword.setText(intent.getStringExtra("password"));
            }
            if (intent.hasExtra("privateKey")) {
                etPrivateKey.setText(intent.getStringExtra("privateKey"));
            }
            if (intent.hasExtra("keyPassphrase")) {
                etKeyPassphrase.setText(intent.getStringExtra("keyPassphrase"));
            }
            if (!intent.getBooleanExtra("is_edit", false)) {
                etPassword.requestFocus();
            }
        }
        // 认证方式回填：缺省=密码（旧调用方/旧数据兼容）
        setAuthMode(SshConfig.authModeOrDefault(intent.getStringExtra("authMode")));
    }

    private void initViews() {
        etAlias = findViewById(R.id.et_alias);
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etRemotePath = findViewById(R.id.et_remote_path);
        etGroup = findViewById(R.id.et_group);
        etPrivateKey = findViewById(R.id.et_private_key);
        etKeyPassphrase = findViewById(R.id.et_key_passphrase);
        tilPassword = findViewById(R.id.til_password);
        layoutKeyFields = findViewById(R.id.layout_key_fields);
        toggleAuthMode = findViewById(R.id.toggle_auth_mode);
        btnConnect = findViewById(R.id.btn_connect);
        progressBar = findViewById(R.id.progress_bar);

        toggleAuthMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) updateAuthFieldsVisibility();
        });

        btnConnect.setText("保存并连接");
        btnConnect.setOnClickListener(v -> attemptConnect());
        findViewById(R.id.btn_save_only).setOnClickListener(v -> saveOnly());
    }

    /** 按当前认证方式显示/隐藏对应输入区（密码域 vs 私钥+口令域）。 */
    private void updateAuthFieldsVisibility() {
        boolean keyMode = isKeyMode();
        tilPassword.setVisibility(keyMode ? View.GONE : View.VISIBLE);
        layoutKeyFields.setVisibility(keyMode ? View.VISIBLE : View.GONE);
    }

    private void setAuthMode(String authMode) {
        boolean keyMode = SshConfig.AUTH_KEY.equals(authMode);
        toggleAuthMode.check(keyMode ? R.id.btn_auth_key : R.id.btn_auth_password);
        updateAuthFieldsVisibility();
    }

    private boolean isKeyMode() {
        return toggleAuthMode.getCheckedButtonId() == R.id.btn_auth_key;
    }

    private void saveOnly() {
        SshConfig config = collectAndValidate();
        if (config == null) return;

        if (editIndex >= 0) {
            prefManager.updateConnection(editIndex, config);
        } else {
            prefManager.saveConnection(config);
        }
        registerGroupIfNeeded(config);
        UiUtils.showToast(this, "已保存");
        finish();
    }

    private void attemptConnect() {
        SshConfig config = collectAndValidate();
        if (config == null) return;

        setLoading(true);

        SshManager.getInstance().setHostKeyStore(prefManager);
        SshManager.getInstance().setPortForwardRules(
                prefManager.getPortForwardRules(SshConnectionHelper.deriveConnectionKey(config)));
        SshManager.getInstance().connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (SshManager.getInstance().consumeFingerprintFirstSeen()) {
                        UiUtils.showToast(ConnectionActivity.this, getString(R.string.host_key_recorded));
                    }
                    String report = SshManager.getInstance().consumePortForwardReport();
                    if (report != null) {
                        UiUtils.showToast(ConnectionActivity.this, report);
                    }
                    if (editIndex >= 0) {
                        prefManager.updateConnection(editIndex, config);
                    } else {
                        prefManager.saveConnection(config);
                    }
                    registerGroupIfNeeded(config);
                    UiUtils.showToast(ConnectionActivity.this, getString(R.string.msg_connected));

                    Intent intent = new Intent(ConnectionActivity.this, FileBrowserActivity.class);
                    // 仅显式主目录（remotePath）才指定起始位置；未配置则交由文件浏览器
                    // 恢复上次浏览目录（「继续上次位置」）。
                    String path = config.getRemotePath();
                    if (path != null && !path.isEmpty()) {
                        intent.putExtra("remote_path", path);
                    }
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    DialogHelper.showConfirmDialog(ConnectionActivity.this,
                            "连接失败", message,
                            "重新连接", "取消",
                            (d) -> attemptConnect(),
                            (d) -> {});
                });
            }

            @Override
            public void onHostKeyChanged(String expected, String actual) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    DialogHelper.showHostKeyChangedDialog(ConnectionActivity.this,
                            prefManager, config.getHost(), config.getPort(),
                            expected, actual,
                            ConnectionActivity.this::reconnectAfterTrust,
                            () -> {});
                });
            }

            @Override
            public void onDisconnected() {
            }
        });
    }

    private void reconnectAfterTrust() {
        attemptConnect();
    }

    /** 连接保存时若填写了新组名，同步注册到分组元数据（已存在则静默跳过）。 */
    private void registerGroupIfNeeded(SshConfig config) {
        String group = ConnectionGroupHelper.normalizeGroupName(config.getGroup());
        if (!group.isEmpty()) {
            prefManager.addConnectionGroup(group);
        }
    }

    /**
     * 收集表单并校验，按认证方式取凭据（密码模式取密码；私钥模式取私钥+可选口令）。
     *
     * @return 校验通过的配置；失败时已给出错误提示并返回 null。
     */
    private SshConfig collectAndValidate() {
        String alias = getText(etAlias);
        String host = getText(etHost);
        String portStr = getText(etPort);
        String username = getText(etUsername);
        String password = getRawText(etPassword);
        String remotePath = getText(etRemotePath);
        boolean keyMode = isKeyMode();
        String privateKey = keyMode ? getRawText(etPrivateKey) : "";
        String keyPassphrase = keyMode ? getRawText(etKeyPassphrase) : "";

        if (host.isEmpty()) {
            etHost.setError(getString(R.string.error_invalid_host));
            return null;
        }
        if (username.isEmpty() || (!keyMode && password.isEmpty())) {
            UiUtils.showSnackbar(btnConnect, getString(
                    keyMode ? R.string.error_invalid_key_credentials : R.string.error_invalid_credentials));
            return null;
        }
        if (keyMode && privateKey.trim().isEmpty()) {
            etPrivateKey.setError(getString(R.string.error_invalid_key));
            return null;
        }

        int port;
        try {
            port = portStr.isEmpty() ? 22 : Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            etPort.setError("端口无效");
            return null;
        }

        SshConfig config = new SshConfig(alias, host, port, username, password,
                remotePath.isEmpty() ? "" : remotePath);
        config.setAuthMode(keyMode ? SshConfig.AUTH_KEY : SshConfig.AUTH_PASSWORD);
        config.setPrivateKey(privateKey);
        config.setKeyPassphrase(keyPassphrase);
        config.setGroup(ConnectionGroupHelper.normalizeGroupName(getText(etGroup)));
        return config;
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnConnect.setEnabled(!loading);
        btnConnect.setText(loading ? R.string.msg_connecting : R.string.btn_connect);
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String getRawText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString() : "";
    }
}
