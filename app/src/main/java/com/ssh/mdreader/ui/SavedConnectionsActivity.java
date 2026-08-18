package com.ssh.mdreader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;

import java.util.List;

public class SavedConnectionsActivity extends BaseActivity {

    private RecyclerView recycler;
    private TextView tvEmpty;
    private ProgressBar progressBar;
    private PreferenceManager prefManager;
    private SavedAdapter adapter;
    private List<SshConfig> savedList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_saved_connections);
        setupToolbar("最近连接", true);

        prefManager = new PreferenceManager(this);
        recycler = findViewById(R.id.recycler_saved);
        tvEmpty = findViewById(R.id.tv_empty_saved);
        progressBar = findViewById(R.id.progress_bar_saved);

        adapter = new SavedAdapter();
        adapter.setOnConnectListener(this::quickConnect);
        adapter.setOnEditListener(this::editConnection);
        adapter.setOnDeleteListener(this::deleteConnection);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        savedList = prefManager.getSavedConnections();
        adapter.setData(savedList);
        tvEmpty.setVisibility(savedList.isEmpty() ? View.VISIBLE : View.GONE);
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

        setLoading(true);
        SshManager.getInstance().connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    setLoading(false);
                    UiUtils.showToast(SavedConnectionsActivity.this, "已连接");

                    String path = config.getRemotePath();
                    if (path == null || path.isEmpty()) {
                        path = SshManager.getInstance().getHomeDirectory();
                    }
                    Intent intent = new Intent(SavedConnectionsActivity.this, FileBrowserActivity.class);
                    intent.putExtra("remote_path", path);
                    startActivity(intent);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    DialogHelper.showConfirmDialog(SavedConnectionsActivity.this,
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

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        adapter.setClickable(!loading);
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

    private static class SavedAdapter extends RecyclerView.Adapter<SavedAdapter.VH> {

        public interface OnConnectListener {
            void onConnect(SshConfig config, int position);
        }

        public interface OnEditListener {
            void onEdit(SshConfig config, int position);
        }

        public interface OnDeleteListener {
            void onDelete(int position);
        }

        private List<SshConfig> data;
        private OnConnectListener connectListener;
        private OnEditListener editListener;
        private OnDeleteListener deleteListener;
        private boolean clickable = true;

        void setOnConnectListener(OnConnectListener l) { connectListener = l; }
        void setOnEditListener(OnEditListener l) { editListener = l; }
        void setOnDeleteListener(OnDeleteListener l) { deleteListener = l; }
        void setClickable(boolean clickable) { this.clickable = clickable; }

        void setData(List<SshConfig> data) {
            this.data = data;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_saved_connection, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            SshConfig config = data.get(position);
            String alias = config.getAlias();
            if (alias != null && !alias.isEmpty()) {
                holder.tvAlias.setText(alias);
                holder.tvAlias.setVisibility(View.VISIBLE);
                holder.tvHost.setText(config.getHost() + ":" + config.getPort());
            } else {
                holder.tvAlias.setVisibility(View.GONE);
                holder.tvHost.setText(config.getHost() + ":" + config.getPort());
            }
            holder.tvUser.setText(config.getUsername());
            String path = config.getRemotePath();
            holder.tvPath.setText(path == null || path.isEmpty() ? "/" : path);

            holder.itemView.setOnClickListener(v -> {
                if (clickable && connectListener != null) {
                    connectListener.onConnect(config, holder.getAdapterPosition());
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                String name = config.getDisplayName();
                DialogHelper.showListDialog(v.getContext(), name,
                        new String[]{"编辑", "删除"},
                        new int[]{R.drawable.ic_edit, R.drawable.ic_delete},
                        (dialog, which) -> {
                            if (which == 0) {
                                if (editListener != null) editListener.onEdit(config, holder.getAdapterPosition());
                            } else if (which == 1) {
                                if (deleteListener != null) deleteListener.onDelete(holder.getAdapterPosition());
                            }
                        });
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data == null ? 0 : data.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvAlias, tvHost, tvUser, tvPath;

            VH(@NonNull View itemView) {
                super(itemView);
                tvAlias = itemView.findViewById(R.id.tv_saved_alias);
                tvHost = itemView.findViewById(R.id.tv_saved_host);
                tvUser = itemView.findViewById(R.id.tv_saved_user);
                tvPath = itemView.findViewById(R.id.tv_saved_path);
            }
        }
    }
}
