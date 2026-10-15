package com.ssh.mdreader.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.ssh.mdreader.R;
import com.ssh.mdreader.adapter.TreeAdapter;
import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.model.RecentFileEntry;
import com.ssh.mdreader.model.SearchResult;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.BookmarkHelper;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.DownloadHelper;
import com.ssh.mdreader.util.FileMetaHelper;
import com.ssh.mdreader.util.FileOpsHelper;
import com.ssh.mdreader.util.FilePropsHelper;
import com.ssh.mdreader.util.FileSortUtils;
import com.ssh.mdreader.util.GoToPathHelper;
import com.ssh.mdreader.util.LastBrowseHelper;
import com.ssh.mdreader.util.LargeFileHelper;
import com.ssh.mdreader.util.NewFileHelper;
import com.ssh.mdreader.util.OpenFileHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.PreviewPaneHelper;
import com.ssh.mdreader.util.RecursiveSearchHelper;
import com.ssh.mdreader.util.SshConnectionHelper;
import com.ssh.mdreader.util.UiUtils;
import com.ssh.mdreader.util.UploadHelper;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    /** 全局递归搜索进行中标志（SshManager 单 worker 串行，防重复触发）。 */
    private boolean isSearching;
    /** 搜索取消令牌（第二十四轮 #35；null=当前无搜索，onDestroy 时置 true 防 worker 泄漏）。 */
    private AtomicBoolean searchCancelToken;
    /** 搜索进行中 Snackbar（markor bindSnackBar 同型；完成/取消时报错前统一 dismiss）。 */
    private Snackbar searchSnackbar;
    /** 服务器主目录（连接配置远程路径或 home，「回到主目录」入口用）。 */
    private String homePath;
    private PreferenceManager prefManager;
    private FileOpsHelper fileOps;
    private PreviewPaneHelper previewHelper;
    /** SAF「另存为」请求（下载到本地入口；uri 回调里用 {@link #pendingDownloadFile} 定位远端文件）。 */
    private androidx.activity.result.ActivityResultLauncher<String> downloadLauncher;

    /** SAF「打开」请求（上传入口；OpenDocument 单选本地文件，零存储权限）。 */
    private androidx.activity.result.ActivityResultLauncher<String[]> uploadLauncher;
    /** 等待 SAF 用户选择位置的远端文件（无则忽略回调——旋转等重建后取消下载）。 */
    private RemoteFile pendingDownloadFile;

    /** 多选模式底部操作栏（批量移动/权限/删除）。 */
    private View selectionBar;

    // ── 文件名称筛选（第三轮能力发现） ─────────────────────────────────────────
    private View filterBar;
    private EditText etFilterQuery;
    private ImageButton btnFilterClose;

    /** 过滤生效时的来源目录（目录切换后自动清除过滤）。 */
    private String filterPath = null;

    /** 最近一次加载的原始列表是否为空（空态文案区分「空目录/没有匹配」）。 */
    private boolean lastListEmpty = false;

    // ── Two-pane preview (layout-w600dp) ──────────────────────────────────────
    private FrameLayout previewContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_browser);
        setupToolbar(R.string.title_files, true);

        // 「继续上次位置」：显式路径（连接配置主目录/外部指定）优先；
        // 否则恢复该服务器上次浏览目录（异步校验存在性），无记录则主目录。
        String explicitPath = getIntent().getStringExtra("remote_path");
        prefManager = new PreferenceManager(this);
        SshConfig config = sshManager.getConfig();
        String last = config == null ? ""
                : prefManager.getLastBrowsedDir(
                        config.getHost(), config.getPort(), config.getUsername());
        homePath = sshManager.getHomeDirectory();
        String home = homePath;
        if (BookmarkHelper.normalizePath(explicitPath).isEmpty() && !last.isEmpty()) {
            // 无显式路径且有上次记录：先校验存在性再决定（恢复语义）
            currentPath = home; // 占位，回调中再切换为上次目录
            restoreLastBrowsedDir(config, last, home);
        } else {
            currentPath = LastBrowseHelper.resolveStartPath(explicitPath, last, home);
        }
        fileOps = new FileOpsHelper(this, this);
        previewHelper = new PreviewPaneHelper(this, this);
        downloadLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.CreateDocument("*/*"),
                uri -> {
                    if (uri == null) {
                        // 用户在系统「另存为」中取消：静默返回（pending 清空）。
                        pendingDownloadFile = null;
                        return;
                    }
                    startRemoteDownload(uri);
                });
        uploadLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri == null) {
                        // 用户在系统文件选择器中取消：静默返回。
                        return;
                    }
                    startRemoteUpload(uri);
                });
        initViews();
        loadFiles();
    }

    /**
     * 恢复上次浏览目录：先 stat 校验存在（目录被删除/移动则清除记录并回退主目录），
     * 校验期间连接问题按「保持上次目录」处理，交给 {@link #loadFiles()} 的统一错误流程。
     */
    private void restoreLastBrowsedDir(SshConfig config, String lastPath, String homePath) {
        sshManager.fileExists(lastPath, new SshManager.ExistsCallback() {
            @Override
            public void onResult(final boolean exists) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!exists) {
                        prefManager.clearLastBrowsedDir(
                                config.getHost(), config.getPort(), config.getUsername());
                        currentPath = homePath;
                        updateToolbarSubtitle();
                        UiUtils.showToast(FileBrowserActivity.this,
                                getString(R.string.toast_last_dir_gone));
                    } else {
                        currentPath = lastPath;
                        updateToolbarSubtitle();
                    }
                    loadFiles();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    currentPath = lastPath;
                    updateToolbarSubtitle();
                    loadFiles();
                });
            }
        });
    }

    /** 每次都落盘当前浏览目录（启动恢复的数据源，按服务器隔离）。 */
    private void recordLastBrowsedDir() {
        SshConfig config = sshManager.getConfig();
        if (config == null) return;
        prefManager.saveLastBrowsedDir(
                config.getHost(), config.getPort(), config.getUsername(), currentPath);
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
        toggleItem.setTitle(showHidden ? getString(R.string.menu_hide_hidden_files) : getString(R.string.menu_show_hidden_files));
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
            item.setTitle(showHidden ? getString(R.string.menu_hide_hidden_files) : getString(R.string.menu_show_hidden_files));
            return true;
        }
        if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        }
        if (id == R.id.action_filter) {
            toggleFilterBar();
            return true;
        }
        if (id == R.id.action_search_files) {
            showGlobalSearchDialog();
            return true;
        }
        if (id == R.id.action_bookmarks) {
            showBookmarksDialog();
            return true;
        }
        if (id == R.id.action_history) {
            showHistoryDialog();
            return true;
        }
        if (id == R.id.action_new) {
            showCreateDialog();
            return true;
        }
        if (id == R.id.action_upload) {
            startUploadFromPicker();
            return true;
        }
        if (id == R.id.action_home) {
            navigateToHome();
            return true;
        }
        if (id == R.id.action_goto_path) {
            showGotoPathDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── 新建文件/目录（能力发现循环） ───────────────────────────────────────

    /** 顶部更多菜单「新建」：选择新建文件或文件夹，再输入名称。 */
    private void showCreateDialog() {
        if (isFinishing() || isDestroyed()) return;
        DialogHelper.showListDialog(this,
                getString(R.string.dialog_create_title, currentPath),
                new String[]{getString(R.string.menu_new_file), getString(R.string.menu_new_folder)},
                new int[]{R.drawable.ic_file, R.drawable.ic_folder},
                (dialog, which) -> {
                    if (which == 0) {
                        createNewFile();
                    } else {
                        createNewDirectory();
                    }
                });
    }

    /** 新建文件：输入名称 → 校验 → 服务器上「不存在才创建」→ 刷新列表。 */
    private void createNewFile() {
        if (isFinishing() || isDestroyed()) return;
        DialogHelper.showInputDialog(this,
                getString(R.string.menu_new_file), getString(R.string.hint_new_file_name), getString(R.string.btn_create), getString(R.string.btn_cancel),
                name -> {
                    String error = NewFileHelper.validateName(name);
                    if (error != null) {
                        UiUtils.showToast(this, error);
                        return;
                    }
                    final String path = NewFileHelper.joinPath(currentPath, name);
                    sshManager.createFile(path, new SshManager.WriteFileCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_created, path));
                                loadFiles();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() ->
                                    UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_create_failed, message)));
                        }
                    });
                });
    }

    /** 新建文件夹：输入名称 → 校验 → 服务器上「不存在才创建」→ 刷新列表。 */
    private void createNewDirectory() {
        if (isFinishing() || isDestroyed()) return;
        DialogHelper.showInputDialog(this,
                getString(R.string.menu_new_folder), getString(R.string.hint_new_folder_name), getString(R.string.btn_create), getString(R.string.btn_cancel),
                name -> {
                    String error = NewFileHelper.validateName(name);
                    if (error != null) {
                        UiUtils.showToast(this, error);
                        return;
                    }
                    final String path = NewFileHelper.joinPath(currentPath, name);
                    sshManager.createDirectory(path, new SshManager.DeleteFileCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_created, path));
                                loadFiles();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            runOnUiThread(() ->
                                    UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_create_failed, message)));
                        }
                    });
                });
    }

    // ── 目录书签快捷访问 ────────────────────────────────────────────────────

    /** 顶部「书签」入口：列出当前服务器已收藏目录，点击跳转；长按取消收藏，底部清空。 */
    private void showBookmarksDialog() {
        SshConfig config = sshManager.getConfig();
        if (config == null) return;
        List<String> bookmarks = prefManager.getBookmarkedPaths(
                config.getHost(), config.getPort(), config.getUsername());
        if (bookmarks.isEmpty()) {
            UiUtils.showToast(this, getString(R.string.toast_no_bookmarks));
            return;
        }
        String[] items = new String[bookmarks.size()];
        int[] icons = new int[bookmarks.size()];
        for (int i = 0; i < bookmarks.size(); i++) {
            items[i] = bookmarks.get(i);
            icons[i] = R.drawable.ic_bookmark;
        }
        DialogHelper.showListDialog(this,
                getString(R.string.dialog_bookmarks_title, bookmarks.size()),
                items, icons,
                (dialog, which) -> jumpToBookmark(bookmarks.get(which)),
                (dialog, which) -> {
                    dialog.dismiss();
                    confirmRemoveBookmark(config, bookmarks.get(which));
                },
                getString(R.string.btn_clear_all),
                (dialog) -> {
                    dialog.dismiss();
                    confirmClearBookmarks(config);
                });
    }

    /** 取消收藏确认：移除单条书签后重开对话框（列表已变）。 */
    private void confirmRemoveBookmark(SshConfig config, String path) {
        DialogHelper.showDangerConfirmDialog(this,
                getString(R.string.menu_remove_bookmark),
                getString(R.string.msg_remove_bookmark, path),
                getString(R.string.menu_remove_bookmark), getString(R.string.btn_back),
                (d) -> {
                    prefManager.removeBookmark(config.getHost(), config.getPort(),
                            config.getUsername(), path);
                    UiUtils.showToast(this, getString(R.string.toast_bookmark_removed, path));
                    showBookmarksDialog();
                },
                (d) -> {});
    }

    /** 清空书签确认：移除当前服务器全部书签。 */
    private void confirmClearBookmarks(SshConfig config) {
        DialogHelper.showDangerConfirmDialog(this,
                getString(R.string.menu_clear_bookmarks),
                getString(R.string.msg_clear_bookmarks),
                getString(R.string.btn_clear), getString(R.string.btn_back),
                (d) -> {
                    prefManager.clearBookmarks(config.getHost(), config.getPort(),
                            config.getUsername());
                    UiUtils.showToast(this, getString(R.string.toast_bookmarks_cleared));
                },
                (d) -> {});
    }

    /** 跳转到书签目录：重设当前根路径并重新加载列表。 */
    private void jumpToBookmark(String path) {
        if (isFinishing() || isDestroyed()) return;
        currentPath = path;
        updateToolbarSubtitle();
        loadFiles();
        UiUtils.showToast(this, getString(R.string.toast_jumped_to, path));
    }

    // ── 最近打开（阅读历史，第五轮能力发现 #13）─────────────────────────────

    /** 顶部「历史」入口：列出当前服务器最近打开的文件，点击重新打开；长按移除，底部清空。 */
    private void showHistoryDialog() {
        SshConfig config = sshManager.getConfig();
        if (config == null) return;
        List<RecentFileEntry> recent = prefManager.getRecentFiles(
                config.getHost(), config.getPort(), config.getUsername());
        if (recent.isEmpty()) {
            UiUtils.showToast(this, getString(R.string.toast_no_recent));
            return;
        }
        String[] items = new String[recent.size()];
        int[] icons = new int[recent.size()];
        for (int i = 0; i < recent.size(); i++) {
            items[i] = recent.get(i).getPath();
            icons[i] = R.drawable.ic_history;
        }
        DialogHelper.showListDialog(this,
                getString(R.string.dialog_recent_title, recent.size()),
                items, icons,
                (dialog, which) -> openRecentFile(recent.get(which).getPath()),
                (dialog, which) -> {
                    dialog.dismiss();
                    confirmRemoveRecent(config, recent.get(which).getPath());
                },
                getString(R.string.btn_clear_all),
                (dialog) -> {
                    dialog.dismiss();
                    confirmClearRecent(config);
                });
    }

    /** 移除单条历史确认：删除后重开对话框（列表已变）。 */
    private void confirmRemoveRecent(SshConfig config, String path) {
        DialogHelper.showDangerConfirmDialog(this,
                getString(R.string.menu_remove_recent),
                getString(R.string.msg_remove_recent, path),
                getString(R.string.btn_remove), getString(R.string.btn_back),
                (d) -> {
                    prefManager.removeRecentFile(config.getHost(), config.getPort(),
                            config.getUsername(), path);
                    UiUtils.showToast(this, getString(R.string.toast_recent_removed));
                    showHistoryDialog();
                },
                (d) -> {});
    }

    /** 清空历史确认：移除当前服务器全部历史。 */
    private void confirmClearRecent(SshConfig config) {
        DialogHelper.showDangerConfirmDialog(this,
                getString(R.string.menu_clear_recent),
                getString(R.string.msg_clear_recent),
                getString(R.string.btn_clear), getString(R.string.btn_back),
                (d) -> {
                    prefManager.clearRecentFiles(config.getHost(), config.getPort(),
                            config.getUsername());
                    UiUtils.showToast(this, getString(R.string.toast_recent_cleared));
                },
                (d) -> {});
    }

    /**
     * 从历史重新打开文件：stat 确认存在并取类型/大小（一次往返，替代原 fileExists
     * 两渠道并补齐 size），不存在则从历史移除该条并提示；文件分支经大文件护栏 #33
     * （真实 size 判定）后按类型分发到查看器（全屏打开语义保持不变，不记录历史置顶）。
     */
    private void openRecentFile(String path) {
        if (isFinishing() || isDestroyed()) return;
        SshConfig config = sshManager.getConfig();
        if (config == null) return;
        sshManager.statPath(path, new SshManager.StatCallback() {
            @Override
            public void onResult(final boolean isDirectory, final long size) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    RemoteFile file = new RemoteFile(
                            GoToPathHelper.fileName(path), path, isDirectory, size, 0, 0);
                    maybeConfirmLargeFile(file, () -> {
                        Intent intent = OpenFileHelper.buildViewerIntent(
                                FileBrowserActivity.this, path);
                        if (intent == null) {
                            showUnsupportedDownloadDialog(file);
                            return;
                        }
                        startActivity(intent);
                    });
                });
            }

            @Override
            public void onNotFound() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    prefManager.removeRecentFile(config.getHost(), config.getPort(),
                            config.getUsername(), path);
                    UiUtils.showToast(FileBrowserActivity.this,
                            getString(R.string.toast_recent_file_gone));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    UiUtils.showToast(FileBrowserActivity.this,
                            getString(R.string.toast_recent_open_failed, message));
                });
            }
        });
    }

    private void showSortDialog() {
        final int current = prefManager.getFileSortMode();
        final String[] names = {getString(R.string.sort_by_name), getString(R.string.sort_by_mtime), getString(R.string.sort_by_size)};
        DialogHelper.showListDialog(this,
                getString(R.string.sort_dialog_title),
                names,
                null,
                (dialog, which) -> {
                    if (which == current) return;
                    prefManager.saveFileSortMode(which);
                    UiUtils.showToast(this, getString(R.string.toast_sort_changed, names[which]));
                    loadFiles();
                });
    }

    // ── 文件名称筛选（第三轮能力发现） ────────────────────────────────────────

    private void toggleFilterBar() {
        if (filterBar.getVisibility() == View.VISIBLE) {
            hideFilterBar();
        } else {
            showFilterBar();
        }
    }

    private void showFilterBar() {
        filterBar.setVisibility(View.VISIBLE);
        etFilterQuery.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etFilterQuery, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideFilterBar() {
        // 清空（触达 watcher → adapter.setFilterQuery("")），再收起栏与键盘
        etFilterQuery.setText("");
        filterBar.setVisibility(View.GONE);
        filterPath = null;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etFilterQuery.getWindowToken(), 0);
        }
    }

    /** 空态统一处理：过滤生效且原始列表非空 →「没有匹配的文件」，否则「空目录」。 */
    private void updateEmptyState() {
        if (adapter.getItemCount() > 0) {
            tvEmpty.setVisibility(View.GONE);
            return;
        }
        tvEmpty.setText(adapter.isFilterActive() && !lastListEmpty ? getString(R.string.empty_no_match) : getString(R.string.empty_directory));
        tvEmpty.setVisibility(View.VISIBLE);
    }

    // ── 全局递归搜索（第十六轮能力发现 #12；markor recursive_search_in_location 对标） ──

    /** 顶部更多菜单「全局搜索…」：输入查询关键词（按名称、大小写不敏感）。 */
    private void showGlobalSearchDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (currentPath == null || currentPath.isEmpty()) {
            UiUtils.showToast(this, getString(R.string.toast_current_path_unknown_search));
            return;
        }
        DialogHelper.showInputDialog(this, getString(R.string.search_dialog_title),
                getString(R.string.search_hint, currentPath),
                getString(R.string.btn_search), getString(R.string.btn_cancel), query -> {
                    if (query == null || query.isEmpty()) {
                        UiUtils.showToast(this, getString(R.string.toast_search_query_empty));
                        return;
                    }
                    showSearchDepthDialog(query);
                });
    }

    /** 搜索深度选择（markor max_search_depth 同型选项；0=无限制）。 */
    private void showSearchDepthDialog(String query) {
        String[] labels = {getString(R.string.search_depth_3_default), getString(R.string.search_depth_5), getString(R.string.search_depth_10), getString(R.string.search_depth_unlimited)};
        int[] depths = {3, 5, 10, 0};
        DialogHelper.showListDialog(this, getString(R.string.search_depth_dialog_title), labels, null,
                (dialog, which) -> doRecursiveSearch(query, depths[which]));
    }

    /** 执行搜索：SshManager 工作线程遍历，回调回主线程更新 UI（既有回调纪律）。 */
    private void doRecursiveSearch(String query, int depthOption) {
        if (isSearching) {
            UiUtils.showToast(this, getString(R.string.toast_search_in_progress));
            return;
        }
        isSearching = true;
        searchCancelToken = new AtomicBoolean(false);
        progressBar.setVisibility(View.VISIBLE);
        // markor bindSnackBar 同型：搜索中常驻 Snackbar + 「取消」动作（非模态可中止）
        searchSnackbar = UiUtils.showSnackbarIndefiniteWithAction(
                findViewById(android.R.id.content),
                getString(R.string.msg_searching, query),
                getString(R.string.btn_cancel),
                () -> {
                    AtomicBoolean token = searchCancelToken;
                    if (token != null) {
                        token.set(true);
                    }
                });
        final String root = currentPath;
        final AtomicBoolean token = searchCancelToken;
        sshManager.searchFiles(root, query, RecursiveSearchHelper.resolveMaxDepth(depthOption),
                token,
                new SshManager.SearchCallback() {
                    @Override
                    public void onSuccess(List<SearchResult> results) {
                        runOnUiThread(() -> {
                            isSearching = false;
                            if (isFinishing() || isDestroyed()) return;
                            dismissSearchUi();
                            if (results.isEmpty()) {
                                UiUtils.showToast(FileBrowserActivity.this,
                                        getString(R.string.toast_search_no_match, query));
                            } else {
                                showSearchResults(query, results);
                            }
                        });
                    }

                    @Override
                    public void onCancelled(int partialCount) {
                        runOnUiThread(() -> {
                            isSearching = false;
                            if (isFinishing() || isDestroyed()) return;
                            dismissSearchUi();
                            UiUtils.showToast(FileBrowserActivity.this,
                                    partialCount > 0
                                            ? getString(R.string.toast_search_cancelled_partial, partialCount)
                                            : getString(R.string.toast_search_cancelled));
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            isSearching = false;
                            if (isFinishing() || isDestroyed()) return;
                            dismissSearchUi();
                            UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_search_failed, message));
                        });
                    }
                });
    }

    /** 搜索结束（成功/取消/失败）统一收尾：停进度条 + 收起搜索 Snackbar。 */
    private void dismissSearchUi() {
        progressBar.setVisibility(View.GONE);
        if (searchSnackbar != null) {
            searchSnackbar.dismiss();
            searchSnackbar = null;
        }
    }

    /** 结果列表：相对路径展示，目录带「（目录）」标注，文件附可读大小（已取数据全量展出）。 */
    private void showSearchResults(String query, List<SearchResult> results) {
        String[] labels = new String[results.size()];
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            labels[i] = r.getRelativePath()
                    + (r.isDirectory() ? getString(R.string.search_result_dir_suffix)
                    : getString(R.string.search_result_file_sep) + DownloadHelper.formatBytes(r.getSize()));
        }
        DialogHelper.showListDialog(this,
                getString(R.string.dialog_search_results_title, results.size(), query), labels, null,
                (dialog, which) -> openSearchResult(results.get(which)));
    }

    /** 点结果：目录 → 直接跳转并加载；文件 → 复用 onFileClick（单一语义源，size 真实传导）。 */
    private void openSearchResult(SearchResult result) {
        if (isFinishing() || isDestroyed()) return;
        if (result.isDirectory()) {
            currentPath = result.getPath();
            updateToolbarSubtitle();
            loadFiles();
            UiUtils.showToast(this, getString(R.string.toast_jumped_to, result.getPath()));
        } else {
            onFileClick(new RemoteFile(result.getName(), result.getPath(), false,
                    result.getSize(), 0, 0));
        }
    }

    // ── 前往路径（第十七轮能力发现 #25；Material Files navigate_to 对标） ────

    /** 顶部更多菜单「前往路径…」：输入远端路径（绝对/相对当前目录），直达目录或打开文件。 */
    private void showGotoPathDialog() {
        if (isFinishing() || isDestroyed()) return;
        if (currentPath == null || currentPath.isEmpty()) {
            UiUtils.showToast(this, getString(R.string.toast_current_path_unknown_goto));
            return;
        }
        DialogHelper.showInputDialog(this, getString(R.string.goto_path_dialog_title),
                getString(R.string.goto_path_hint),
                getString(R.string.btn_goto), getString(R.string.btn_cancel), input -> {
                    String target = GoToPathHelper.resolve(currentPath, input);
                    if (target == null) {
                        UiUtils.showToast(this, getString(R.string.toast_path_empty));
                        return;
                    }
                    gotoPath(target);
                });
    }

    /** 执行前往：先 stat 判定类型（目录→跳转加载；文件→复用 onFileClick 单一语义源）。 */
    private void gotoPath(final String target) {
        if (isFinishing() || isDestroyed()) return;
        sshManager.statPath(target, new SshManager.StatCallback() {
            @Override
            public void onResult(boolean isDirectory, long size) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (isDirectory) {
                        currentPath = target;
                        updateToolbarSubtitle();
                        loadFiles();
                        UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_jumped_to, target));
                    } else {
                        onFileClick(new RemoteFile(
                                GoToPathHelper.fileName(target), target, false, size, 0, 0));
                    }
                });
            }

            @Override
            public void onNotFound() {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_path_not_found, target));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_goto_failed, message));
                });
            }
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
        findViewById(R.id.btn_select_copy).setOnClickListener(
                v -> fileOps.copySelectedFiles(adapter.getSelectedFiles()));
        findViewById(R.id.btn_select_chmod).setOnClickListener(
                v -> fileOps.chmodSelectedFiles(adapter.getSelectedFiles()));
        findViewById(R.id.btn_select_delete).setOnClickListener(
                v -> fileOps.deleteSelectedFiles(adapter.getSelectedFiles()));

        swipeRefresh.setColorSchemeColors(
                getResources().getColor(R.color.md_theme_primary, getTheme()));
        swipeRefresh.setOnRefreshListener(this::loadFiles);

        // ── 文件名称筛选栏（第三轮能力发现） ──────────────────────────────────
        filterBar = findViewById(R.id.filter_bar);
        etFilterQuery = findViewById(R.id.et_filter_query);
        btnFilterClose = findViewById(R.id.btn_filter_close);
        etFilterQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString();
                adapter.setFilterQuery(query);
                filterPath = query.isEmpty() ? null : currentPath;
                updateEmptyState();
            }
        });
        btnFilterClose.setOnClickListener(v -> hideFilterBar());

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
                        showErrorPage(getString(R.string.msg_no_config));
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
        // 目录已切换（如书签跳转）：过滤只作用于来源目录，自动清除
        if (filterPath != null && !currentPath.equals(filterPath)) {
            etFilterQuery.setText("");
            filterBar.setVisibility(View.GONE);
            filterPath = null;
        }

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

                    lastListEmpty = files.isEmpty();
                    int sortMode = prefManager.getFileSortMode();
                    adapter.setFiles(FileSortUtils.sort(files, sortMode), sortMode);
                    updateEmptyState();
                    recordLastBrowsedDir();

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
        tvErrorMsg.setText(getString(R.string.msg_load_failed, message));
    }

    private void reconnectAndReload(SshConfig config) {
        if (isFinishing() || isDestroyed()) return;
        progressBar.setVisibility(View.VISIBLE);
        layoutError.setVisibility(View.GONE);
        SshManager.getInstance().setHostKeyStore(prefManager);
        SshManager.getInstance().setPortForwardRules(
                prefManager.getPortForwardRules(SshConnectionHelper.deriveConnectionKey(config)));
        sshManager.connect(config, new SshManager.ConnectionListener() {
            @Override
            public void onConnected() {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        if (SshManager.getInstance().consumeFingerprintFirstSeen()) {
                            UiUtils.showToast(FileBrowserActivity.this, getString(R.string.host_key_recorded));
                        }
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
                    showErrorPage(getString(R.string.msg_connect_failed, message));
                });
            }

            @Override
            public void onHostKeyChanged(String expected, String actual) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBar.setVisibility(View.GONE);
                    DialogHelper.showHostKeyChangedDialog(FileBrowserActivity.this,
                            prefManager, config.getHost(), config.getPort(),
                            expected, actual,
                            () -> reconnectAndReload(config),
                            () -> showErrorPage(getString(R.string.host_key_changed_rejected)));
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
        maybeConfirmLargeFile(file, () -> openRemoteFileInternal(file));
    }

    /**
     * 大文件打开护栏（能力发现 #33）：文件大小超过阈值（4MB）时先确认再打开，
     * 避免无提示全量加载导致卡顿/ANR/内存风险；目录与大小未知（&lt;=0）直接放行
     * （path-only 入口行为零变化，护栏保守化）。
     */
    private void maybeConfirmLargeFile(RemoteFile file, Runnable onOpen) {
        if (file.isDirectory() || !LargeFileHelper.isLargeFile(file.getSize())) {
            onOpen.run();
            return;
        }
        if (isFinishing() || isDestroyed()) return;
        DialogHelper.showConfirmDialog(this,
                getString(R.string.large_file_dialog_title),
                LargeFileHelper.buildConfirmMessage(file.getSize()),
                getString(R.string.btn_open),
                getString(R.string.btn_cancel),
                d -> onOpen.run(),
                d -> { });
    }

    /**
     * 不支持类型的点击出口（能力发现循环第卌五轮 #39a）：应用内无查看器可开时
     * 不再以纯 toast 结束（点击死路），改为确认「下载到本地查看」（复用长按菜单
     * 下载语义 {@link #startDownloadToLocal}，SAF 另存为）。目录不会走到此分支。
     */
    private void showUnsupportedDownloadDialog(RemoteFile file) {
        if (isFinishing() || isDestroyed()) return;
        if (file.isDirectory()) {
            // 目录不应走到「暂不支持」分支（历史/前往路径 stat 后未分流目录时兜底）
            UiUtils.showToast(this, getString(R.string.msg_unsupported));
            return;
        }
        DialogHelper.showConfirmDialog(this,
                getString(R.string.msg_unsupported),
                getString(R.string.msg_unsupported_download, file.getName()),
                getString(R.string.btn_download_local),
                getString(R.string.btn_cancel),
                (d) -> startDownloadToLocal(file),
                (d) -> { });
    }

    private void openRemoteFileInternal(RemoteFile file) {
        if (isTwoPane()) {
            previewHelper.loadPreviewInPane(file);
            return;
        }

        Intent intent = OpenFileHelper.buildViewerIntent(this, file.getPath());
        if (intent == null) {
            showUnsupportedDownloadDialog(file);
            return;
        }
        recordRecentOpen(file.getPath());
        startActivity(intent);
    }

    /** 打开文件时记录「最近打开」（按服务器隔离，仅真实打开时记录，预览不记录）。 */
    private void recordRecentOpen(String path) {
        SshConfig config = sshManager.getConfig();
        if (config == null) return;
        prefManager.recordRecentFile(config.getHost(), config.getPort(),
                config.getUsername(), path);
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

    /**
     * 属性详情对话框（只读）：类型/路径/大小/修改时间/权限（符号串 + 四位八进制）。
     * 目录大小 SFTP 一般不返回有意义值（子项懒加载不统计），显示「—」占位；
     * 修改时间复用 FileMetaHelper（未知「—」）。
     */
    private void showFileProperties(RemoteFile file) {
        String type = file.isDirectory() ? getString(R.string.type_directory) : getString(R.string.type_file);
        String size = file.isDirectory() ? "—" : file.getFormattedSize();
        String message = getString(R.string.prop_type, type) + "\n"
                + getString(R.string.prop_path, file.getPath()) + "\n"
                + getString(R.string.prop_size, size) + "\n"
                + getString(R.string.prop_mtime, FileMetaHelper.formatMtime(file.getMtime())) + "\n"
                + getString(R.string.prop_permissions, FilePropsHelper.formatModeWithOctal(file.getPermissions()));
        DialogHelper.showMessageDialog(this, file.getName(), message, getString(R.string.btn_close), d -> { });
    }

    @Override
    public void onFileLongClick(RemoteFile file) {
        DialogHelper.showListDialog(this,
                file.getName(),
                new String[]{getString(R.string.menu_select_mode), getString(R.string.menu_move), getString(R.string.menu_copy), getString(R.string.menu_copy_path), getString(R.string.btn_download_local), getString(R.string.menu_properties), getString(R.string.menu_rename), getString(R.string.menu_permissions), getString(R.string.menu_delete)},
                new int[]{0, R.drawable.ic_move, R.drawable.ic_copy, R.drawable.ic_content_copy,
                        R.drawable.ic_download, R.drawable.ic_info_outline, R.drawable.ic_edit,
                        R.drawable.ic_lock, R.drawable.ic_delete},
                (dialog, which) -> {
                    if (which == 0) {
                        adapter.enterSelectionMode(file);
                    } else if (which == 1) {
                        fileOps.moveFile(file);
                    } else if (which == 2) {
                        fileOps.copyFile(file);
                    } else if (which == 3) {
                        UiUtils.copyRemotePath(this, file.getPath());
                    } else if (which == 4) {
                        startDownloadToLocal(file);
                    } else if (which == 5) {
                        showFileProperties(file);
                    } else if (which == 6) {
                        fileOps.renameFile(file);
                    } else if (which == 7) {
                        fileOps.showChmodDialog(file);
                    } else if (which == 8) {
                        fileOps.confirmDeleteFile(file);
                    }
                });
    }

    /**
     * 下载到本地入口：SAF（ActivityResultContracts.CreateDocument）让用户选保存位置，
     * 默认名=远端文件名（DownloadHelper.suggestFileName，可改）。仅文件（目录下载需
     * 递归打包，成本高另议，见路线图观察项）。SAF 零存储权限（Android 10+ 免弹窗）。
     */
    private void startDownloadToLocal(RemoteFile file) {
        if (file == null || file.isDirectory()) return;
        pendingDownloadFile = file;
        String suggested = DownloadHelper.suggestFileName(file.getPath());
        try {
            downloadLauncher.launch(suggested);
        } catch (RuntimeException e) {
            pendingDownloadFile = null;
            UiUtils.showToast(this, getString(R.string.toast_save_dialog_open_failed, UiUtils.errorMessage(e)));
        }
    }

    /** SAF 返回目标 uri 后执行下载：openOutputStream → SshManager.downloadFile（worker 线程流式拷贝）。 */
    private void startRemoteDownload(android.net.Uri uri) {
        RemoteFile file = pendingDownloadFile;
        pendingDownloadFile = null;
        if (file == null || file.isDirectory()) return;
        UiUtils.showToast(this, getString(R.string.msg_downloading, file.getName()));
        java.io.OutputStream out;
        try {
            out = getContentResolver().openOutputStream(uri);
        } catch (Exception e) {
            UiUtils.showToast(this, getString(R.string.toast_download_failed, UiUtils.errorMessage(e)));
            return;
        }
        if (out == null) {
            UiUtils.showToast(this, getString(R.string.toast_write_failed));
            return;
        }
        sshManager.downloadFile(file.getPath(), out, new SshManager.DownloadFileCallback() {
            @Override
            public void onSuccess(long bytes) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        UiUtils.showToast(FileBrowserActivity.this,
                                getString(R.string.toast_saved, file.getName(), DownloadHelper.formatBytes(bytes)));
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_download_failed, message));
                    }
                });
            }
        });
    }

    // ── 上传到服务器（本地→远端，SAF OpenDocument）────────────────────────

    /** 上传入口：系统文件选择器（OpenDocument 单选，零存储权限），仅文件（目录上传需递归遍历，观察项）。 */
    private void startUploadFromPicker() {
        try {
            uploadLauncher.launch(new String[]{"*/*"});
        } catch (RuntimeException e) {
            UiUtils.showToast(this, getString(R.string.toast_picker_failed, UiUtils.errorMessage(e)));
        }
    }

    /** 解析 SAF 返回 uri 的显示名（OpenableColumns.DISPLAY_NAME），provider 未提供→null（走兜底链）。 */
    private String queryDisplayName(android.net.Uri uri) {
        try (android.database.Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && !n.trim().isEmpty()) {
                        return n;
                    }
                }
            }
        } catch (Exception ignored) {
            // provider 查询失败：返回 null，走 lastPathSegment/DEFAULT 兜底链（不阻断上传）。
        }
        return null;
    }

    /**
     * SAF 返回 uri 后执行上传：解析显示名 → 构建目标路径 → 先查同名（存在则确认覆盖，
     * 与复制/编辑保存的确认流程同口径）→ 打开输入流上传。
     */
    private void startRemoteUpload(android.net.Uri uri) {
        String name = UploadHelper.resolveDisplayName(queryDisplayName(uri), uri.getLastPathSegment());
        final String targetPath = UploadHelper.buildTargetPath(currentPath, name);
        sshManager.fileExists(targetPath, new SshManager.ExistsCallback() {
            @Override
            public void onResult(final boolean exists) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (exists) {
                        DialogHelper.showConfirmDialog(FileBrowserActivity.this,
                                getString(R.string.msg_same_name_exists),
                                getString(R.string.msg_overwrite_confirm, targetPath),
                                getString(R.string.btn_overwrite), getString(R.string.btn_cancel),
                                d -> doUpload(uri, targetPath),
                                d -> { });
                    } else {
                        doUpload(uri, targetPath);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_upload_failed, message));
                    }
                });
            }
        });
    }

    /** 打开本地输入流并上传（worker 线程流式；成功刷新列表使新文件可见）。 */
    private void doUpload(android.net.Uri uri, String targetPath) {
        java.io.InputStream in;
        try {
            in = getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            UiUtils.showToast(this, getString(R.string.msg_cannot_read_file_reason, UiUtils.errorMessage(e)));
            return;
        }
        if (in == null) {
            UiUtils.showToast(this, getString(R.string.msg_cannot_read_file));
            return;
        }
        sshManager.uploadFile(targetPath, in, new SshManager.UploadFileCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        UiUtils.showToast(FileBrowserActivity.this,
                                getString(R.string.toast_uploaded, DownloadHelper.suggestFileName(targetPath)));
                        loadFiles();
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        UiUtils.showToast(FileBrowserActivity.this, getString(R.string.toast_upload_failed, message));
                    }
                });
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
            toolbar.setTitle(on ? (getString(R.string.title_selected, adapter.getSelectedCount()))
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
                new String[]{getString(R.string.menu_select_mode), getString(R.string.menu_move), getString(R.string.menu_copy), getString(R.string.menu_copy_path), getString(R.string.menu_properties), getString(R.string.menu_rename), getString(R.string.menu_permissions), getString(R.string.menu_delete),
                        getString(R.string.menu_set_home),
                        bookmarked ? getString(R.string.menu_remove_bookmark) : getString(R.string.menu_add_bookmark)},
                new int[]{0, R.drawable.ic_move, R.drawable.ic_copy, R.drawable.ic_content_copy,
                        R.drawable.ic_info_outline, R.drawable.ic_edit, R.drawable.ic_lock,
                        R.drawable.ic_delete,
                        R.drawable.ic_folder_set,
                        bookmarked ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border},
                (dialog, which) -> {
                    if (which == 0) {
                        adapter.enterSelectionMode(dir);
                    } else if (which == 1) {
                        fileOps.moveFile(dir);
                    } else if (which == 2) {
                        fileOps.copyDirectory(dir);
                    } else if (which == 3) {
                        UiUtils.copyRemotePath(this, dir.getPath());
                    } else if (which == 4) {
                        showFileProperties(dir);
                    } else if (which == 5) {
                        fileOps.renameFile(dir);
                    } else if (which == 6) {
                        fileOps.showChmodDialog(dir);
                    } else if (which == 7) {
                        fileOps.confirmDeleteDirectory(dir);
                    } else if (which == 8) {
                        prefManager.updateRemotePath(
                                config.getHost(), config.getPort(),
                                config.getUsername(), dir.getPath());
                        UiUtils.showToast(this, getString(R.string.toast_home_set));
                    } else if (which == 9) {
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
            UiUtils.showToast(this, getString(R.string.toast_bookmark_removed_plain));
        } else {
            prefManager.addBookmark(config.getHost(), config.getPort(),
                    config.getUsername(), dir.getPath());
            UiUtils.showToast(this, getString(R.string.toast_bookmarked, dir.getName()));
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

    // ── PreviewPaneHelper.Host 回调实现 ────────────────────────────────────

    /** 预览 pane 工具栏「打开」（#37）：经 OpenFileHelper 单点分发进入全量查看器。 */
    @Override
    public void onPreviewOpenRequested(RemoteFile file) {
        if (isFinishing() || isDestroyed()) return;
        Intent intent = OpenFileHelper.buildViewerIntent(this, file.getPath());
        if (intent == null) {
            UiUtils.showToast(this, getString(R.string.msg_unsupported));
            return;
        }
        recordRecentOpen(file.getPath());
        startActivity(intent);
    }

    /** 预览 pane 工具栏「下载」（#39a）：不支持查看的类型经此另存到本地（复用下载语义）。 */
    @Override
    public void onPreviewDownloadRequested(RemoteFile file) {
        if (isFinishing() || isDestroyed()) return;
        if (file.isDirectory()) {
            UiUtils.showToast(this, getString(R.string.msg_dir_download_unsupported));
            return;
        }
        startDownloadToLocal(file);
    }

    @Override
    public void onBackPressed() {
        // 多选模式下返回键 = 退出多选模式
        if (adapter != null && adapter.isSelectionMode()) {
            adapter.exitSelectionMode();
            return;
        }
        // 上一级导航（#20）：非根目录时返回上级目录，到根目录才关闭
        if (canGoUp()) {
            goUp();
            return;
        }
        super.onBackPressed();
    }

    // ── 上一级/主目录导航（能力发现循环第十轮 #20）─────────────────────────

    /** 当前目录是否还有上一级（根目录「/」或未知路径时不可再上）。 */
    private boolean canGoUp() {
        return currentPath != null && !currentPath.isEmpty() && !"/".equals(currentPath);
    }

    /** 返回上一级目录：重设当前路径并重新加载（目录切换语义，筛选自动清除）。 */
    private void goUp() {
        if (!canGoUp()) return;
        currentPath = SshManager.parentOf(currentPath);
        updateToolbarSubtitle();
        loadFiles();
    }

    /**
     * 跳到主目录：连接配置的远程路径优先，其次服务器 home（与启动恢复
     * {@link LastBrowseHelper#resolveStartPath} 的「显式主目录 > home」语义一致）。
     * 重设当前根路径并重新加载。
     */
    private void navigateToHome() {
        if (isFinishing() || isDestroyed()) return;
        SshConfig config = sshManager.getConfig();
        String target = config != null
                ? BookmarkHelper.normalizePath(config.getRemotePath()) : "";
        if (target.isEmpty()) {
            target = homePath == null ? "" : homePath;
        }
        if (target.isEmpty()) {
            UiUtils.showToast(this, getString(R.string.msg_home_unavailable));
            return;
        }
        currentPath = target;
        updateToolbarSubtitle();
        loadFiles();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fileOps.dismissActivePicker();
        // 搜索在进行中则取消：SshManager 单 worker 串行，退页不取消会让遍历继续
        // 占用队列直至结束（第二十四轮 #35；拼接的 token 为当前搜索的引用）。
        AtomicBoolean token = searchCancelToken;
        if (token != null) {
            token.set(true);
        }
        if (isFinishing()) {
            sshManager.disconnect();
        }
    }
}
