package com.ssh.mdreader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.ConnectionGroupHelper;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SavedConnectionsActivity extends BaseActivity {

    private RecyclerView recycler;
    private TextView tvEmpty;
    private ProgressBar progressBar;
    private PreferenceManager prefManager;
    private SavedAdapter adapter;
    private List<SshConfig> savedList = new ArrayList<>();

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
        adapter.setOnGroupHeaderLongClickListener(this::showGroupHeaderActions);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_saved_connections, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_group_manage) {
            showGroupManagerDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadData() {
        savedList = prefManager.getSavedConnections();
        List<String> groupNames = ConnectionGroupHelper.mergeGroupNames(
                prefManager.getConnectionGroups(), savedList);
        Map<String, List<SshConfig>> buckets = ConnectionGroupHelper.bucketByGroup(savedList);
        adapter.setData(groupNames, buckets);
        tvEmpty.setVisibility(savedList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void quickConnect(SshConfig config) {
        boolean needsPasswordEntry = !config.isKeyAuth()
                && (config.getPassword() == null || config.getPassword().isEmpty());
        if (needsPasswordEntry) {
            Intent intent = new Intent(this, ConnectionActivity.class);
            intent.putExtra("alias", config.getAlias());
            intent.putExtra("host", config.getHost());
            intent.putExtra("port", config.getPort());
            intent.putExtra("username", config.getUsername());
            intent.putExtra("remotePath", config.getRemotePath());
            intent.putExtra("group", config.getGroup());
            startActivity(intent);
            return;
        }

        setLoading(true);
        SshManager.getInstance().setHostKeyStore(prefManager);
        SshManager.getInstance().connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    setLoading(false);
                    if (SshManager.getInstance().consumeFingerprintFirstSeen()) {
                        UiUtils.showToast(SavedConnectionsActivity.this, getString(R.string.host_key_recorded));
                    }
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
                            (d) -> quickConnect(config),
                            (d) -> {});
                });
            }

            @Override
            public void onHostKeyChanged(String expected, String actual) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setLoading(false);
                    DialogHelper.showHostKeyChangedDialog(SavedConnectionsActivity.this,
                            prefManager, config.getHost(), config.getPort(),
                            expected, actual,
                            () -> quickConnect(config),
                            () -> {});
                });
            }

            @Override
            public void onDisconnected() {
            }
        });
    }

    private void editConnection(SshConfig config) {
        int index = savedList.indexOf(config);
        Intent intent = new Intent(this, ConnectionActivity.class);
        intent.putExtra("is_edit", true);
        intent.putExtra("edit_index", index);
        intent.putExtra("alias", config.getAlias());
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

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        adapter.setClickable(!loading);
    }

    private void deleteConnection(SshConfig config) {
        int index = savedList.indexOf(config);
        if (index < 0) return;
        String name = config.getDisplayName();
        DialogHelper.showDangerConfirmDialog(this,
                "删除连接", "确定删除 \"" + name + "\" ？",
                "删除", "取消",
                (d) -> {
                    prefManager.deleteConnection(index);
                    loadData();
                },
                (d) -> {});
    }

    // ── 分组管理 ─────────────────────────────────────────────────────────────

    private void showGroupManagerDialog() {
        List<String> groups = prefManager.getConnectionGroups();
        String[] items = new String[groups.size() + 1];
        items[0] = "＋ 新建分组";
        for (int i = 0; i < groups.size(); i++) {
            items[i + 1] = groups.get(i);
        }
        DialogHelper.showListDialog(this, "管理分组", items,
                new int[]{R.drawable.ic_add, 0, 0},
                (d, which) -> {
                    if (which == 0) {
                        showNewGroupDialog();
                    } else if (which - 1 < groups.size()) {
                        showGroupActionsDialog(groups.get(which - 1));
                    }
                });
    }

    private void showNewGroupDialog() {
        DialogHelper.showInputDialog(this,
                "新建分组", "分组名称（如：生产环境）", "创建", "取消",
                InputType.TYPE_CLASS_TEXT, null,
                input -> {
                    if (input.isEmpty()) {
                        UiUtils.showToast(this, "分组名称不能为空");
                        return;
                    }
                    if (prefManager.addConnectionGroup(input)) {
                        UiUtils.showToast(this, "分组已创建");
                        loadData();
                    } else {
                        UiUtils.showToast(this, "分组已存在");
                    }
                });
    }

    /** 组头长按 / 管理分组列表点选组名：弹出重命名/删除。 */
    private void showGroupActionsDialog(String group) {
        DialogHelper.showListDialog(this, group,
                new String[]{"重命名", "删除"},
                new int[]{R.drawable.ic_edit, R.drawable.ic_delete},
                (d, which) -> {
                    if (which == 0) {
                        showRenameGroupDialog(group);
                    } else if (which == 1) {
                        confirmDeleteGroup(group);
                    }
                });
    }

    private void showRenameGroupDialog(String group) {
        DialogHelper.showInputDialog(this,
                "重命名分组", "分组名称", "确定", "取消",
                InputType.TYPE_CLASS_TEXT, group,
                input -> {
                    if (input.isEmpty()) {
                        UiUtils.showToast(this, "分组名称不能为空");
                        return;
                    }
                    if (prefManager.renameConnectionGroup(group, input)) {
                        UiUtils.showToast(this, "分组已重命名");
                        loadData();
                    } else {
                        UiUtils.showToast(this, "新名称与旧名称相同");
                    }
                });
    }

    private void confirmDeleteGroup(String group) {
        int count = 0;
        for (SshConfig c : savedList) {
            if (group.equals(ConnectionGroupHelper.groupOf(c))) count++;
        }
        String message = "确定删除分组 \"" + group + "\" ？"
                + (count > 0 ? "\n组内 " + count + " 条连接将退回「未分组」。" : "");
        DialogHelper.showDangerConfirmDialog(this,
                "删除分组", message, "删除", "取消",
                (d) -> {
                    prefManager.deleteConnectionGroup(group);
                    UiUtils.showToast(this, "分组已删除");
                    loadData();
                },
                (d) -> {});
    }

    private void showGroupHeaderActions(String group) {
        if (!ConnectionGroupHelper.UNGROUPED.equals(group)) {
            showGroupActionsDialog(group);
        }
    }

    // ── Adapter（分组 header / 连接行 双 viewType） ───────────────────────────

    private static class Row {
        static final int TYPE_HEADER = 0;
        static final int TYPE_CONNECTION = 1;

        final int type;
        final String groupName;   // 仅 header 使用（""=未分组）
        final SshConfig config;   // 仅连接行使用

        Row(int type, String groupName, SshConfig config) {
            this.type = type;
            this.groupName = groupName;
            this.config = config;
        }
    }

    private static class SavedAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        public interface OnConnectListener {
            void onConnect(SshConfig config);
        }

        public interface OnEditListener {
            void onEdit(SshConfig config);
        }

        public interface OnDeleteListener {
            void onDelete(SshConfig config);
        }

        public interface OnGroupHeaderLongClickListener {
            void onGroupHeaderLongClick(String groupName);
        }

        private OnConnectListener connectListener;
        private OnEditListener editListener;
        private OnDeleteListener deleteListener;
        private OnGroupHeaderLongClickListener groupHeaderLongListener;
        private boolean clickable = true;

        private final List<Row> rows = new ArrayList<>();
        private List<String> groupNames = new ArrayList<>();
        private Map<String, List<SshConfig>> buckets = null;
        /** 折叠的组（未分组的 key "" 永不折叠：未分组区固定展示）。 */
        private final Set<String> collapsed = new HashSet<>();

        void setOnConnectListener(OnConnectListener l) { connectListener = l; }
        void setOnEditListener(OnEditListener l) { editListener = l; }
        void setOnDeleteListener(OnDeleteListener l) { deleteListener = l; }
        void setOnGroupHeaderLongClickListener(OnGroupHeaderLongClickListener l) {
            groupHeaderLongListener = l;
        }
        void setClickable(boolean clickable) { this.clickable = clickable; }

        void setData(List<String> groupNames, Map<String, List<SshConfig>> buckets) {
            this.groupNames = groupNames;
            this.buckets = buckets;
            rebuildRows();
        }

        /** 按展示顺序重建行：每组一个组头（空组也显示）+ 未折叠时的组内连接；未分组区固定在末尾。 */
        private void rebuildRows() {
            rows.clear();
            for (String group : groupNames) {
                if (group.isEmpty()) continue; // 未分组区在末尾统一追加
                rows.add(new Row(Row.TYPE_HEADER, group, null));
                if (collapsed.contains(group)) continue;
                List<SshConfig> members = buckets == null ? null : buckets.get(group);
                if (members != null) {
                    for (SshConfig c : members) {
                        rows.add(new Row(Row.TYPE_CONNECTION, null, c));
                    }
                }
            }
            List<SshConfig> ungrouped = buckets == null ? null : buckets.get(ConnectionGroupHelper.UNGROUPED);
            if (ungrouped != null && !ungrouped.isEmpty()) {
                rows.add(new Row(Row.TYPE_HEADER, ConnectionGroupHelper.UNGROUPED, null));
                for (SshConfig c : ungrouped) {
                    rows.add(new Row(Row.TYPE_CONNECTION, null, c));
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == Row.TYPE_HEADER) {
                return new HeaderVH(inflater.inflate(R.layout.item_group_header, parent, false));
            }
            return new ConnectionVH(inflater.inflate(R.layout.item_saved_connection, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (row.type == Row.TYPE_HEADER) {
                bindHeader((HeaderVH) holder, row);
            } else {
                bindConnection((ConnectionVH) holder, row);
            }
        }

        private void bindHeader(HeaderVH holder, Row row) {
            boolean ungrouped = ConnectionGroupHelper.UNGROUPED.equals(row.groupName);
            int count = 0;
            if (buckets != null) {
                List<SshConfig> members = buckets.get(row.groupName);
                if (members != null) count = members.size();
            }
            holder.tvGroupName.setText(ungrouped ? "未分组" : row.groupName);
            holder.tvGroupCount.setText("(" + count + ")");
            // 未分组区固定展示（无折叠箭头、点击不折叠）
            holder.imgGroupArrow.setVisibility(ungrouped ? View.GONE : View.VISIBLE);
            holder.imgGroupArrow.setRotation(collapsed.contains(row.groupName) ? 180f : 0f);
            holder.itemView.setOnClickListener(v -> {
                if (!clickable || ungrouped) return;
                if (collapsed.contains(row.groupName)) {
                    collapsed.remove(row.groupName);
                } else {
                    collapsed.add(row.groupName);
                }
                rebuildRows();
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (groupHeaderLongListener != null) {
                    groupHeaderLongListener.onGroupHeaderLongClick(row.groupName);
                    return true;
                }
                return false;
            });
        }

        private void bindConnection(ConnectionVH holder, Row row) {
            SshConfig config = row.config;
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
                    connectListener.onConnect(config);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                String name = config.getDisplayName();
                DialogHelper.showListDialog(v.getContext(), name,
                        new String[]{"编辑", "删除"},
                        new int[]{R.drawable.ic_edit, R.drawable.ic_delete},
                        (dialog, which) -> {
                            if (which == 0) {
                                if (editListener != null) editListener.onEdit(config);
                            } else if (which == 1) {
                                if (deleteListener != null) deleteListener.onDelete(config);
                            }
                        });
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static class HeaderVH extends RecyclerView.ViewHolder {
            final TextView tvGroupName, tvGroupCount;
            final ImageView imgGroupArrow;

            HeaderVH(@NonNull View itemView) {
                super(itemView);
                tvGroupName = itemView.findViewById(R.id.tv_group_name);
                tvGroupCount = itemView.findViewById(R.id.tv_group_count);
                imgGroupArrow = itemView.findViewById(R.id.img_group_arrow);
            }
        }

        static class ConnectionVH extends RecyclerView.ViewHolder {
            final TextView tvAlias, tvHost, tvUser, tvPath;

            ConnectionVH(@NonNull View itemView) {
                super(itemView);
                tvAlias = itemView.findViewById(R.id.tv_saved_alias);
                tvHost = itemView.findViewById(R.id.tv_saved_host);
                tvUser = itemView.findViewById(R.id.tv_saved_user);
                tvPath = itemView.findViewById(R.id.tv_saved_path);
            }
        }
    }
}
