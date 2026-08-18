package com.ssh.mdreader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.ssh.mdreader.R;
import com.ssh.mdreader.adapter.TreeAdapter;
import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;

import java.util.List;

public class FileBrowserActivity extends BaseActivity
        implements TreeAdapter.OnFileActionListener {

    private RecyclerView recyclerFiles;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private LinearLayout layoutError;
    private TextView tvErrorMsg;
    private MaterialButton btnRetry;
    private TreeAdapter adapter;

    private final SshManager sshManager = SshManager.getInstance();
    private String currentPath;
    private PreferenceManager prefManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);
        setupToolbar(R.string.title_files, true);

        currentPath = getIntent().getStringExtra("remote_path");
        if (currentPath == null || currentPath.isEmpty()) {
            currentPath = sshManager.getHomeDirectory();
        }

        prefManager = new PreferenceManager(this);
        initViews();
        loadFiles();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_file_browser, menu);
        // 恢复显隐状态
        boolean showHidden = prefManager.getShowHidden();
        adapter.setShowHidden(showHidden);
        MenuItem toggleItem = menu.findItem(R.id.action_toggle_hidden);
        toggleItem.setIcon(showHidden ? R.drawable.ic_visibility_on : R.drawable.ic_visibility_off);
        toggleItem.setTitle(showHidden ? "隐藏隐藏文件" : "显示隐藏文件");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_hidden) {
            boolean showHidden = !adapter.isShowHidden();
            adapter.setShowHidden(showHidden);
            prefManager.saveShowHidden(showHidden);
            item.setIcon(showHidden ? R.drawable.ic_visibility_on : R.drawable.ic_visibility_off);
            item.setTitle(showHidden ? "隐藏隐藏文件" : "显示隐藏文件");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        recyclerFiles = findViewById(R.id.recycler_files);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);
        layoutError = findViewById(R.id.layout_error);
        tvErrorMsg = findViewById(R.id.tv_error_msg);
        btnRetry = findViewById(R.id.btn_retry);

        adapter = new TreeAdapter();
        adapter.setOnFileActionListener(this);
        recyclerFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerFiles.setAdapter(adapter);

        swipeRefresh.setColorSchemeColors(
                getResources().getColor(R.color.md_theme_primary, getTheme()));
        swipeRefresh.setOnRefreshListener(this::loadFiles);

        btnRetry.setOnClickListener(v -> {
            if (sshManager.isConnectionAlive()) {
                // 连接还在，直接重新加载
                recyclerFiles.setVisibility(View.VISIBLE);
                loadFiles();
            } else {
                // 连接已断开或 zombie，重新连接
                SshConfig config = sshManager.getConfig();
                if (config != null) {
                    reconnectAndReload(config);
                } else {
                    showErrorPage("无法获取连接配置，请返回重新连接");
                }
            }
        });

        updateToolbarSubtitle();
    }

    private void updateToolbarSubtitle() {
        if (toolbar != null) {
            toolbar.setSubtitle(currentPath);
        }
    }

    private void loadFiles() {
        // Save scroll position before refresh
        final int[] savedScrollY = {-1};
        if (recyclerFiles.getLayoutManager() instanceof LinearLayoutManager) {
            View firstChild = recyclerFiles.getChildAt(0);
            if (firstChild != null) {
                savedScrollY[0] = recyclerFiles.getChildAdapterPosition(firstChild);
            }
        }

        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.GONE);

        sshManager.listFiles(currentPath, new SshManager.FileListCallback() {
            @Override
            public void onSuccess(List<RemoteFile> files) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);

                    if (files.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                    }
                    adapter.setFiles(files);

                    // Restore scroll position
                    if (savedScrollY[0] >= 0 && savedScrollY[0] < adapter.getItemCount()) {
                        recyclerFiles.scrollToPosition(savedScrollY[0]);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);

                    // If the connection died (e.g. network switch), try to reconnect automatically
                    if (!sshManager.isConnectionAlive()) {
                        SshConfig savedConfig = sshManager.getConfig();
                        if (savedConfig != null) {
                            reconnectAndReload(savedConfig);
                            return;
                        }
                    }
                    showErrorPage(message);
                });
            }
        });
    }

    private void showErrorPage(String message) {
        recyclerFiles.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        layoutError.setVisibility(View.VISIBLE);
        tvErrorMsg.setText("加载失败：" + message);
    }

    private void reconnectAndReload(SshConfig config) {
        if (isFinishing() || isDestroyed()) return;
        progressBar.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        sshManager.connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        recyclerFiles.setVisibility(View.VISIBLE);
                        loadFiles();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    showErrorPage("连接失败：" + message);
                });
            }

            @Override
            public void onDisconnected() {
            }
        });
    }

    @Override
    public void onDirectoryExpand(RemoteFile dir, TreeAdapter.ExpandCallback callback) {
        sshManager.listFiles(dir.getPath(), new SshManager.FileListCallback() {
            @Override
            public void onSuccess(List<RemoteFile> files) {
                runOnUiThread(() -> callback.onLoaded(files));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> callback.onError(message));
            }
        });
    }

    @Override
    public void onFileClick(RemoteFile file) {
        if (file.isMarkdown()) {
            Intent intent = new Intent(this, MarkdownReaderActivity.class);
            intent.putExtra("file_path", file.getPath());
            intent.putExtra("file_name", file.getName());
            startActivity(intent);
        } else if (file.isCsv()) {
            Intent intent = new Intent(this, CsvReaderActivity.class);
            intent.putExtra("file_path", file.getPath());
            intent.putExtra("file_name", file.getName());
            startActivity(intent);
        } else if (file.isCodeFile()) {
            Intent intent = new Intent(this, CodeViewerActivity.class);
            intent.putExtra("file_path", file.getPath());
            intent.putExtra("file_name", file.getName());
            startActivity(intent);
        } else if (file.isImageFile()) {
            Intent intent = new Intent(this, ImageViewerActivity.class);
            intent.putExtra("file_path", file.getPath());
            intent.putExtra("file_name", file.getName());
            startActivity(intent);
        } else {
            UiUtils.showToast(this, "暂不支持此文件类型");
        }
    }

    @Override
    public void onFileLongClick(RemoteFile file) {
        DialogHelper.showListDialog(this,
                file.getName(),
                new String[]{"删除"},
                new int[]{R.drawable.ic_delete},
                (dialog, which) -> {
                    if (which == 0) {
                        confirmDeleteFile(file);
                    }
                });
    }

    private void confirmDeleteFile(RemoteFile file) {
        DialogHelper.showDangerConfirmDialog(this,
                "删除文件",
                "确定要删除 \"" + file.getName() + "\" 吗？此操作不可恢复。",
                "删除", "取消",
                d -> deleteRemoteFile(file),
                d -> {});
    }

    private void deleteRemoteFile(RemoteFile file) {
        sshManager.deleteFile(file.getPath(), new SshManager.DeleteFileCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    UiUtils.showToast(FileBrowserActivity.this, "已删除 " + file.getName());
                    adapter.removeFile(file.getPath());
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        UiUtils.showToast(FileBrowserActivity.this, "删除失败: " + message));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh file list when returning from MarkdownReaderActivity
        // (annotation files may have been created or deleted)
        if (adapter != null && adapter.getItemCount() > 0) {
            loadFiles();
        }
    }

    @Override
    public void onDirectoryLongClick(RemoteFile dir) {
        SshConfig config = sshManager.getConfig();
        if (config == null) return;

        DialogHelper.showListDialog(this,
                dir.getName(),
                new String[]{"设为主目录"},
                new int[]{R.drawable.ic_folder_set},
                (dialog, which) -> {
                    if (which == 0) {
                        prefManager.updateRemotePath(
                                config.getHost(), config.getPort(),
                                config.getUsername(), dir.getPath());
                        UiUtils.showToast(this, "已设为主目录");
                    }
                });
    }

    @Override
    public void onBackPressed() {
        // 直接关闭，不再逐级返回上级目录
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            sshManager.disconnect();
        }
    }
}
