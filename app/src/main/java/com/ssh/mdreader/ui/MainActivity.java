package com.ssh.mdreader.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.ssh.mdreader.R;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends BaseActivity {

    private RecyclerView recycler;
    private View layoutEmpty;
    private PreferenceManager prefManager;
    private HomeAdapter adapter;
    private List<SshConfig> savedList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        prefManager = new PreferenceManager(this);
        recycler = findViewById(R.id.recycler_home);
        layoutEmpty = findViewById(R.id.layout_empty);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add);

        adapter = new HomeAdapter();
        adapter.setOnConnectListener(this::quickConnect);
        adapter.setOnEditListener(this::editConnection);
        adapter.setOnDeleteListener(this::deleteConnection);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

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
        adapter.setData(savedList);
        if (savedList.isEmpty()) {
            layoutEmpty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            layoutEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }

    private void quickConnect(SshConfig config, int position) {
        if (config.getPassword() == null || config.getPassword().isEmpty()) {
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
        SshManager.getInstance().connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    adapter.clearConnecting();
                    adapter.setClickable(true);
                    UiUtils.showToast(MainActivity.this, "已连接");
                    String path = config.getRemotePath();
                    if (path == null || path.isEmpty()) {
                        path = SshManager.getInstance().getHomeDirectory();
                    }
                    Intent intent = new Intent(MainActivity.this, FileBrowserActivity.class);
                    intent.putExtra("remote_path", path);
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
        startActivity(intent);
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

        TextInputEditText etAlias      = view.findViewById(R.id.et_alias);
        TextInputEditText etHost       = view.findViewById(R.id.et_host);
        TextInputEditText etPort       = view.findViewById(R.id.et_port);
        TextInputEditText etUsername   = view.findViewById(R.id.et_username);
        TextInputEditText etPassword   = view.findViewById(R.id.et_password);
        TextInputEditText etRemotePath = view.findViewById(R.id.et_remote_path);
        MaterialButton btnSave      = view.findViewById(R.id.btn_save_only);
        MaterialButton btnConnect   = view.findViewById(R.id.btn_connect);
        ProgressBar    progressBar  = view.findViewById(R.id.progress_bar);

        btnSave.setOnClickListener(v -> {
            SshConfig config = validateAndBuildConfig(
                    etAlias, etHost, etPort, etUsername, etPassword, etRemotePath, btnSave);
            if (config == null) return;
            prefManager.saveConnection(config);
            dialog.dismiss();
            UiUtils.showToast(this, "已保存");
            loadData();
        });

        btnConnect.setOnClickListener(v -> {
            SshConfig config = validateAndBuildConfig(
                    etAlias, etHost, etPort, etUsername, etPassword, etRemotePath, btnConnect);
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

        SshManager.getInstance().connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    btnConnect.setEnabled(true);
                    btnConnect.setText(R.string.btn_connect);
                    prefManager.saveConnection(config);
                    dialog.dismiss();
                    UiUtils.showToast(MainActivity.this, getString(R.string.msg_connected));

                    String path = config.getRemotePath();
                    if (path == null || path.isEmpty()) {
                        path = SshManager.getInstance().getHomeDirectory();
                    }
                    Intent intent = new Intent(MainActivity.this, FileBrowserActivity.class);
                    intent.putExtra("remote_path", path);
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
            public void onDisconnected() {
            }
        });
    }

    /**
     * Validates the dialog form fields and builds an {@link SshConfig}.
     * Password is NOT trimmed; all other fields are trimmed.
     *
     * @return the config, or null when validation fails
     */
    private SshConfig validateAndBuildConfig(
            TextInputEditText etAlias, TextInputEditText etHost, TextInputEditText etPort,
            TextInputEditText etUsername, TextInputEditText etPassword,
            TextInputEditText etRemotePath, View anchor) {

        String alias      = text(etAlias);
        String host       = text(etHost);
        String portStr    = text(etPort);
        String username   = text(etUsername);
        String password   = rawText(etPassword);
        String remotePath = text(etRemotePath);

        if (host.isEmpty()) {
            etHost.setError(getString(R.string.error_invalid_host));
            return null;
        }
        if (username.isEmpty() || password.isEmpty()) {
            UiUtils.showSnackbar(anchor, getString(R.string.error_invalid_credentials));
            return null;
        }

        int port;
        try {
            port = portStr.isEmpty() ? 22 : Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            etPort.setError("端口无效");
            return null;
        }

        return new SshConfig(alias, host, port, username, password,
                remotePath.isEmpty() ? "" : remotePath);
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

        public interface OnEditListener {
            void onEdit(SshConfig config, int position);
        }

        public interface OnDeleteListener {
            void onDelete(int position);
        }

        private List<SshConfig> data = new ArrayList<>();
        private OnConnectListener connectListener;
        private OnEditListener editListener;
        private OnDeleteListener deleteListener;
        private boolean clickable = true;
        private int connectingPosition = -1;

        void setOnConnectListener(OnConnectListener l) { connectListener = l; }
        void setOnEditListener(OnEditListener l) { editListener = l; }
        void setOnDeleteListener(OnDeleteListener l) { deleteListener = l; }
        void setClickable(boolean clickable) { this.clickable = clickable; }
        void setConnecting(int position) { connectingPosition = position; notifyDataSetChanged(); }
        void clearConnecting() { connectingPosition = -1; notifyDataSetChanged(); }

        void setData(List<SshConfig> data) {
            this.data = data != null ? data : new ArrayList<>();
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
                h.tvCount.setText(data.size() + " 个已保存连接");
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
                            new String[]{"编辑", "删除"},
                            new int[]{R.drawable.ic_edit, R.drawable.ic_delete},
                            (dialog, which) -> {
                                if (which == 0) {
                                    if (editListener != null) editListener.onEdit(config, dataPos);
                                } else if (which == 1) {
                                    if (deleteListener != null) deleteListener.onDelete(dataPos);
                                }
                            });
                    return true;
                });
            }
        }

        static class HeaderVH extends RecyclerView.ViewHolder {
            final TextView tvCount;

            HeaderVH(@NonNull View itemView) {
                super(itemView);
                tvCount = itemView.findViewById(R.id.tv_connection_count);
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
