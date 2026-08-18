package com.ssh.mdreader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ssh.mdreader.R;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;

public class ConnectionActivity extends BaseActivity {

    private TextInputEditText etAlias, etHost, etPort, etUsername, etPassword, etRemotePath;
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
            if (intent.hasExtra("password")) {
                etPassword.setText(intent.getStringExtra("password"));
            }
            if (!intent.getBooleanExtra("is_edit", false)) {
                etPassword.requestFocus();
            }
        }
    }

    private void initViews() {
        etAlias = findViewById(R.id.et_alias);
        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etRemotePath = findViewById(R.id.et_remote_path);
        btnConnect = findViewById(R.id.btn_connect);
        progressBar = findViewById(R.id.progress_bar);

        btnConnect.setText("保存并连接");
        btnConnect.setOnClickListener(v -> attemptConnect());
        findViewById(R.id.btn_save_only).setOnClickListener(v -> saveOnly());
    }

    private void saveOnly() {
        String alias = getText(etAlias);
        String host = getText(etHost);
        String portStr = getText(etPort);
        String username = getText(etUsername);
        String password = getRawText(etPassword);
        String remotePath = getText(etRemotePath);

        if (host.isEmpty()) {
            etHost.setError(getString(R.string.error_invalid_host));
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            UiUtils.showSnackbar(btnConnect, getString(R.string.error_invalid_credentials));
            return;
        }

        int port = portStr.isEmpty() ? 22 : Integer.parseInt(portStr);
        SshConfig config = new SshConfig(alias, host, port, username, password, remotePath.isEmpty() ? "" : remotePath);

        if (editIndex >= 0) {
            prefManager.updateConnection(editIndex, config);
        } else {
            prefManager.saveConnection(config);
        }
        UiUtils.showToast(this, "已保存");
        finish();
    }

    private void attemptConnect() {
        String alias = getText(etAlias);
        String host = getText(etHost);
        String portStr = getText(etPort);
        String username = getText(etUsername);
        String password = getRawText(etPassword);
        String remotePath = getText(etRemotePath);

        if (host.isEmpty()) {
            etHost.setError(getString(R.string.error_invalid_host));
            return;
        }
        if (username.isEmpty() || password.isEmpty()) {
            UiUtils.showSnackbar(btnConnect, getString(R.string.error_invalid_credentials));
            return;
        }

        int port = portStr.isEmpty() ? 22 : Integer.parseInt(portStr);
        SshConfig config = new SshConfig(alias, host, port, username, password, remotePath.isEmpty() ? "" : remotePath);

        setLoading(true);

        SshManager.getInstance().connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (editIndex >= 0) {
                        prefManager.updateConnection(editIndex, config);
                    } else {
                        prefManager.saveConnection(config);
                    }
                    UiUtils.showToast(ConnectionActivity.this, getString(R.string.msg_connected));

                    String path = config.getRemotePath();
                    if (path == null || path.isEmpty()) {
                        path = SshManager.getInstance().getHomeDirectory();
                    }
                    Intent intent = new Intent(ConnectionActivity.this, FileBrowserActivity.class);
                    intent.putExtra("remote_path", path);
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
            public void onDisconnected() {
            }
        });
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
