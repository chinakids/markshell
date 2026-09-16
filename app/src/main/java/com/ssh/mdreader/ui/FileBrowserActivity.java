package com.ssh.mdreader.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
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
import com.ssh.mdreader.util.CodeHighlighter;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.image.ImagesPlugin;

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

    // ── Two-pane preview (layout-w600dp) ──────────────────────────────────────
    private FrameLayout previewContainer;
    private Markwon previewMarkwon;

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
        if (isTwoPane()) {
            loadPreviewInPane(file);
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

    // ── Two-pane preview (foldable unfolded / tablet) ─────────────────────────

    /** True when the w600dp layout is active (right preview pane exists). */
    private boolean isTwoPane() {
        return findViewById(R.id.preview_container) != null;
    }

    private FrameLayout previewPane() {
        if (previewContainer == null) {
            previewContainer = findViewById(R.id.preview_container);
        }
        return previewContainer;
    }

    private Markwon getPreviewMarkwon() {
        if (previewMarkwon == null) {
            previewMarkwon = Markwon.builder(this)
                    .usePlugin(ImagesPlugin.create())
                    .usePlugin(TablePlugin.create(this))
                    .usePlugin(TaskListPlugin.create(this))
                    .build();
        }
        return previewMarkwon;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    /**
     * Loads a preview of {@code file} into the right pane.  Builds the pane
     * view programmatically: a title bar (file name) plus a content area that
     * hosts a type-specific renderer.
     */
    private void loadPreviewInPane(RemoteFile file) {
        FrameLayout pane = previewPane();
        if (pane == null) return;
        if (isFinishing() || isDestroyed()) return;

        pane.removeAllViews();

        boolean supported = file.isMarkdown() || file.isCsv() || file.isCodeFile()
                || file.isImageFile() || file.isTextFile();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D0D0D);
        pane.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Use a Toolbar to match the left pane's ?attr/actionBarSize exactly
        com.google.android.material.appbar.MaterialToolbar previewBar =
                new com.google.android.material.appbar.MaterialToolbar(this);
        previewBar.setTitle(file.getName());
        previewBar.setTitleTextColor(0xFFE0E0E0);
        previewBar.setSubtitleTextColor(0xFFA0A0A0);
        previewBar.setBackgroundColor(0xFF1E1E1E);
        // Use wrap_content + minHeight so subtitle (file path) is not clipped on short screens
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true);
        int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
        previewBar.setMinimumHeight(actionBarHeight);
        root.addView(previewBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(this);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!supported) {
            showPreviewMessage(body, getString(R.string.preview_unsupported));
            return;
        }

        ProgressBar pb = new ProgressBar(this);
        body.addView(pb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        if (file.isImageFile()) {
            sshManager.readFileBytes(file.getPath(), new SshManager.FileBytesCallback() {
                @Override
                public void onSuccess(byte[] bytes) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        pb.setVisibility(View.GONE);
                        showImagePreview(body, bytes);
                    });
                }
                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        pb.setVisibility(View.GONE);
                        showPreviewMessage(body, "加载失败: " + message);
                    });
                }
            });
        } else {
            sshManager.readFile(file.getPath(), new SshManager.FileContentCallback() {
                @Override
                public void onSuccess(String content) {
                    if (file.isCodeFile()) {
                        // highlight off the main thread, then apply
                        new Thread(() -> {
                            SpannableStringBuilder highlighted;
                            try {
                                highlighted = CodeHighlighter.highlight(
                                        content, file.getName());
                            } catch (Throwable t) {
                                highlighted = new SpannableStringBuilder(content);
                            }
                            SpannableStringBuilder result = highlighted;
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                pb.setVisibility(View.GONE);
                                showCodePreview(body, content, result);
                            });
                        }, "preview-highlight").start();
                    } else {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) return;
                            pb.setVisibility(View.GONE);
                            if (file.isMarkdown()) {
                                showMarkdownPreview(body, content);
                            } else if (file.isCsv()) {
                                showCsvPreview(body, content);
                            } else {
                                showTextPreview(body, content);
                            }
                        });
                    }
                }
                @Override
                public void onError(String message) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        pb.setVisibility(View.GONE);
                        showPreviewMessage(body, "加载失败: " + message);
                    });
                }
            });
        }
    }

    private void showPreviewMessage(FrameLayout body, String message) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextColor(0xFF666666);
        tv.setTextSize(16f);
        tv.setGravity(Gravity.CENTER);
        body.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
    }

    private void showMarkdownPreview(FrameLayout body, String content) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        tv.setTextColor(0xFFE0E0E0);
        tv.setLineSpacing(0, 1.5f);
        sv.addView(tv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getPreviewMarkwon().setMarkdown(tv, content);
    }

    private void showTextPreview(FrameLayout body, String content) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        tv.setTextColor(0xFFE0E0E0);
        tv.setLineSpacing(0, 1.4f);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        tv.setText(content);
        sv.addView(tv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * Code preview: line-number column + highlighted code, fixed 14sp,
     * horizontally and vertically scrollable (same structure as the
     * full-screen CodeViewerActivity, without pinch zoom).
     */
    private void showCodePreview(FrameLayout body, String rawContent,
                                  SpannableStringBuilder highlighted) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setFillViewport(true);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView lineNums = new TextView(this);
        lineNums.setTypeface(Typeface.MONOSPACE);
        lineNums.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        lineNums.setTextColor(0xFF666666);
        lineNums.setBackgroundColor(0xFF1E1E1E);
        lineNums.setPadding(dp(8), dp(8), dp(8), dp(8));
        lineNums.setGravity(Gravity.END);
        lineNums.setMinWidth(dp(48));
        String[] lines = rawContent.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines.length; i++) sb.append(i).append('\n');
        lineNums.setText(sb);
        row.addView(lineNums, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView code = new TextView(this);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        code.setTextColor(0xFFE0E0E0);
        code.setLineSpacing(0, 1.4f);
        code.setTextIsSelectable(true);
        code.setPadding(dp(8), dp(8), dp(8), dp(8));
        code.setText(highlighted);
        row.addView(code, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sv.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hsv.addView(sv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(hsv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showImagePreview(FrameLayout body, byte[] bytes) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        int imgW = opts.outWidth, imgH = opts.outHeight;

        if (imgW <= 0 || imgH <= 0) {
            showPreviewMessage(body, "无法解码图片");
            return;
        }

        int maxDim = 2048, sample = 1;
        while (imgW / sample > maxDim || imgH / sample > maxDim) sample *= 2;

        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOpts);

        if (bitmap == null) {
            showPreviewMessage(body, "图片解码失败");
            return;
        }

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(bitmap);
        body.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showCsvPreview(FrameLayout body, String content) {
        ScrollView sv = new ScrollView(this);
        TextView tv = new TextView(this);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTextColor(0xFFE0E0E0);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        tv.setText(formatCsvPreview(content));
        sv.addView(tv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Formats CSV content as a monospace aligned table for preview. */
    private String formatCsvPreview(String content) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : content.split("\n")) {
            if (line.trim().isEmpty()) continue;
            rows.add(parseCsvLine(line));
        }
        if (rows.isEmpty()) return "";

        int cols = 0;
        for (List<String> r : rows) cols = Math.max(cols, r.size());

        int[] widths = new int[cols];
        for (List<String> r : rows) {
            for (int i = 0; i < r.size(); i++) {
                widths[i] = Math.max(widths[i], r.get(i).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> r = rows.get(rowIndex);
            for (int i = 0; i < r.size(); i++) {
                String cell = r.get(i);
                sb.append(cell);
                if (i < r.size() - 1) {
                    int pad = widths[i] - cell.length() + 2;
                    for (int p = 0; p < pad; p++) sb.append(' ');
                }
            }
            sb.append('\n');
            // separator line under the header row
            if (rowIndex == 0) {
                for (int i = 0; i < cols; i++) {
                    for (int p = 0; p < widths[i] + 2; p++) sb.append('-');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields;
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
