package com.ssh.mdreader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
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
import com.ssh.mdreader.util.FileOpsHelper;
import com.ssh.mdreader.util.FileSortUtils;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.PreviewPaneHelper;
import com.ssh.mdreader.util.UiUtils;

import java.util.List;

public class FileBrowserActivity extends BaseActivity
        implements TreeAdapter.OnFileActionListener, FileOpsHelper.Host, PreviewPaneHelper.Host {

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
    private FileOpsHelper fileOps;
    private PreviewPaneHelper previewHelper;

    /** 多选模式底部操作栏（批量移动/权限/删除）。 */
    private View selectionBar;

    // ── Two-pane preview (layout-w600dp) ──────────────────────────────────────
    private FrameLayout previewContainer;

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
        fileOps = new FileOpsHelper(this, this);
        previewHelper = new PreviewPaneHelper(this, this);
        initViews();
        loadFiles();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (adapter != null && adapter.isSelectionMode()) {
            getMenuInflater().inflate(R.menu.menu_file_browser_selection, menu);
            return true;
        }
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
        if (id == R.id.action_select_cancel) {
            adapter.exitSelectionMode();
            return true;
        }
        if (id == R.id.action_select_all) {
            adapter.selectAllVisible();
            return true;
        }
        if (id == R.id.action_toggle_hidden) {
            boolean showHidden = !adapter.isShowHidden();
            adapter.setShowHidden(showHidden);
            prefManager.saveShowHidden(showHidden);
            item.setIcon(showHidden ? R.drawable.ic_visibility_on : R.drawable.ic_visibility_off);
            item.setTitle(showHidden ? "隐藏隐藏文件" : "显示隐藏文件");
            return true;
        }
        if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        }
        if (id == R.id.action_bookmarks) {
            showBookmarksDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── 目录书签快捷访问 ────────────────────────────────────────────────────

    /** 顶部「书签」入口：列出当前服务器已收藏目录，点击跳转。 */
    private void showBookmarksDialog() {
        SshConfig config = sshManager.getConfig();
        if (config == null) return;
        List<String> bookmarks = prefManager.getBookmarkedPaths(
                config.getHost(), config.getPort(), config.getUsername());
        if (bookmarks.isEmpty()) {
            UiUtils.showToast(this, "还没有书签，长按目录即可收藏");
            return;
        }
        String[] items = new String[bookmarks.size()];
        int[] icons = new int[bookmarks.size()];
        for (int i = 0; i < bookmarks.size(); i++) {
            items[i] = bookmarks.get(i);
            icons[i] = R.drawable.ic_bookmark;
        }
        DialogHelper.showListDialog(this,
                "书签目录（" + bookmarks.size() + "）",
                items, icons,
                (dialog, which) -> jumpToBookmark(bookmarks.get(which)));
    }

    /** 跳转到书签目录：重设当前根路径并重新加载列表。 */
    private void jumpToBookmark(String path) {
        if (isFinishing() || isDestroyed()) return;
        currentPath = path;
        updateToolbarSubtitle();
        loadFiles();
        UiUtils.showToast(this, "已跳转到 " + path);
    }

    private void showSortDialog() {
        final int current = prefManager.getFileSortMode();
        final String[] names = {"按名称", "按修改时间", "按大小"};
        DialogHelper.showListDialog(this,
                "排序方式",
                names,
                null,
                (dialog, which) -> {
                    if (which == current) return;
                    prefManager.saveFileSortMode(which);
                    UiUtils.showToast(this, "已切换为" + names[which]);
                    loadFiles();
                });
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
        adapter.setOnSelectionListener(this::refreshSelectionUi);
        recyclerFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerFiles.setAdapter(adapter);

        selectionBar = findViewById(R.id.layout_selection_bar);
        findViewById(R.id.btn_select_move).setOnClickListener(
                v -> fileOps.moveSelectedFiles(adapter.getSelectedFiles()));
        findViewById(R.id.btn_select_chmod).setOnClickListener(
                v -> fileOps.chmodSelectedFiles(adapter.getSelectedFiles()));
        findViewById(R.id.btn_select_delete).setOnClickListener(
                v -> fileOps.deleteSelectedFiles(adapter.getSelectedFiles()));

        swipeRefresh.setColorSchemeColors(
                getResources().getColor(R.color.md_theme_primary, getTheme()));
        swipeRefresh.setOnRefreshListener(this::loadFiles);

        btnRetry.setOnClickListener(v -> {
            // isConnectionAlive() is a blocking SFTP round-trip; never on main thread.
            progressBar.setVisibility(View.VISIBLE);
            sshManager.checkConnectionAlive(alive -> runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progressBar.setVisibility(View.GONE);
                if (alive) {
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
            }));
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
                    int sortMode = prefManager.getFileSortMode();
                    adapter.setFiles(FileSortUtils.sort(files, sortMode), sortMode);

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

                    // If the connection died (e.g. network switch), try to reconnect automatically.
                    // Liveness check is a blocking SFTP round-trip => run off the main thread.
                    sshManager.checkConnectionAlive(alive -> runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        if (!alive) {
                            SshConfig savedConfig = sshManager.getConfig();
                            if (savedConfig != null) {
                                reconnectAndReload(savedConfig);
                                return;
                            }
                        }
                        showErrorPage(message);
                    }));
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
                runOnUiThread(() -> callback.onLoaded(FileSortUtils.sort(files, prefManager.getFileSortMode())));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> callback.onError(message));
            }
        });
    }

    @Override
    public void onFileClick(RemoteFile file) {
        if (isTwoPane()) {
            previewHelper.loadPreviewInPane(file);
            return;
        }

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
        } else if (file.isTextFile()) {
            Intent intent = new Intent(this, TextViewerActivity.class);
            intent.putExtra("file_path", file.getPath());
            intent.putExtra("file_name", file.getName());
            startActivity(intent);
        } else {
            UiUtils.showToast(this, "暂不支持此文件类型");
        }
    }

    // ── Two-pane preview (foldable unfolded / tablet) ─────────────────────────────

    /** True when the w600dp layout is active (right preview pane exists). */
    private boolean isTwoPane() {
        return findViewById(R.id.preview_container) != null;
    }

    @Override
    public FrameLayout previewPane() {
        if (previewContainer == null) {
            previewContainer = findViewById(R.id.preview_container);
        }
        return previewContainer;
    }

    @Override
    public void onFileLongClick(RemoteFile file) {
        DialogHelper.showListDialog(this,
                file.getName(),
                new String[]{"多选", "移动", "复制", "重命名", "权限", "删除"},
                new int[]{0, R.drawable.ic_move, R.drawable.ic_copy, R.drawable.ic_edit,
                        R.drawable.ic_lock, R.drawable.ic_delete},
                (dialog, which) -> {
                    if (which == 0) {
                        adapter.enterSelectionMode(file);
                    } else if (which == 1) {
                        fileOps.moveFile(file);
                    } else if (which == 2) {
                        fileOps.copyFile(file);
                    } else if (which == 3) {
                        fileOps.renameFile(file);
                    } else if (which == 4) {
                        fileOps.showChmodDialog(file);
                    } else if (which == 5) {
                        fileOps.confirmDeleteFile(file);
                    }
                });
    }

    // ── 多选模式 UI 与批量操作 ──────────────────────────────────────────────

    /** 多选状态变化：刷新标题、底部操作栏显隐、列表底部留白与菜单。adapter 在主线程回调。 */
    private void refreshSelectionUi() {
        boolean on = adapter.isSelectionMode();
        if (selectionBar != null) {
            selectionBar.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        recyclerFiles.setPadding(
                recyclerFiles.getPaddingLeft(),
                recyclerFiles.getPaddingTop(),
                recyclerFiles.getPaddingRight(),
                on ? (int) (72 * getResources().getDisplayMetrics().density)
                   : (int) (8 * getResources().getDisplayMetrics().density));
        if (toolbar != null) {
            toolbar.setTitle(on ? ("已选 " + adapter.getSelectedCount() + " 项")
                    : getString(R.string.title_files));
        }
        invalidateOptionsMenu();
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

        boolean bookmarked = prefManager.isBookmarked(
                config.getHost(), config.getPort(),
                config.getUsername(), dir.getPath());
        DialogHelper.showListDialog(this,
                dir.getName(),
                new String[]{"多选", "移动", "复制", "重命名", "权限", "删除", "设为主目录",
                        bookmarked ? "取消收藏" : "收藏"},
                new int[]{0, R.drawable.ic_move, R.drawable.ic_copy, R.drawable.ic_edit,
                        R.drawable.ic_lock, R.drawable.ic_delete, R.drawable.ic_folder_set,
                        bookmarked ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border},
                (dialog, which) -> {
                    if (which == 0) {
                        adapter.enterSelectionMode(dir);
                    } else if (which == 1) {
                        fileOps.moveFile(dir);
                    } else if (which == 2) {
                        fileOps.copyDirectory(dir);
                    } else if (which == 3) {
                        fileOps.renameFile(dir);
                    } else if (which == 4) {
                        fileOps.showChmodDialog(dir);
                    } else if (which == 5) {
                        fileOps.confirmDeleteDirectory(dir);
                    } else if (which == 6) {
                        prefManager.updateRemotePath(
                                config.getHost(), config.getPort(),
                                config.getUsername(), dir.getPath());
                        UiUtils.showToast(this, "已设为主目录");
                    } else if (which == 7) {
                        toggleBookmark(config, dir);
                    }
                });
    }

    /** 收藏/取消收藏当前目录，立即持久化（书签按服务器隔离）。 */
    private void toggleBookmark(SshConfig config, RemoteFile dir) {
        boolean bookmarked = prefManager.isBookmarked(
                config.getHost(), config.getPort(),
                config.getUsername(), dir.getPath());
        if (bookmarked) {
            prefManager.removeBookmark(config.getHost(), config.getPort(),
                    config.getUsername(), dir.getPath());
            UiUtils.showToast(this, "已取消收藏");
        } else {
            prefManager.addBookmark(config.getHost(), config.getPort(),
                    config.getUsername(), dir.getPath());
            UiUtils.showToast(this, "已收藏 " + dir.getName());
        }
    }

    // ── FileOpsHelper.Host 回调实现 ────────────────────────────────────────

    @Override
    public boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    public void onFilesChanged() {
        loadFiles();
    }

    @Override
    public void onFileRemoved(String path) {
        adapter.removeFile(path);
    }

    @Override
    public void onFilesRemoved(List<String> paths) {
        adapter.removeFiles(paths);
    }

    @Override
    public void onFileRenamed(String path, String newName) {
        adapter.renameFile(path, newName);
    }

    @Override
    public void onSelectionExited() {
        adapter.exitSelectionMode();
    }

    @Override
    public void onBackPressed() {
        // 多选模式下返回键 = 退出多选模式
        if (adapter != null && adapter.isSelectionMode()) {
            adapter.exitSelectionMode();
            return;
        }
        // 直接关闭，不再逐级返回上级目录
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fileOps.dismissActivePicker();
        if (isFinishing()) {
            sshManager.disconnect();
        }
    }
}
