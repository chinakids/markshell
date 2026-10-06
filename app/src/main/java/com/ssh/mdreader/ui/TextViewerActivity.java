package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.text.InputType;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.annotation.NonNull;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.GoToLineHelper;
import com.ssh.mdreader.util.LineNumberHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.ShareHelper;
import com.ssh.mdreader.util.UiUtils;
import com.ssh.mdreader.util.ViewerFindBar;
import com.ssh.mdreader.util.ViewerStartHelper;
import com.ssh.mdreader.util.ViewerTextSizeHelper;
import com.ssh.mdreader.widget.LineNumberGutterView;

public class TextViewerActivity extends BaseActivity {

    private static final String KEY_SCROLL_Y = "scroll_y";
    private static final int MENU_COPY_PATH_ID = 0xA2001;
    private static final int MENU_FIND_ID = 0xA2002;
    private static final int MENU_GOTO_LINE_ID = 0xA2003;
    private static final int MENU_SHARE_ID = 0xA2004;
    private static final int GOTO_HIGHLIGHT_COLOR = 0x66FFD600;

    private TextView textContent;
    private LineNumberGutterView lineNumberGutter;
    private View loadingOverlay;
    private ScrollView scrollView;
    private ViewerFindBar viewerFindBar;
    private int restoredScrollY;
    /** 走查 #34：本次打开是否跳到底部（设置开启+全新打开；旋转恢复态恒 false）。 */
    private boolean startAtBottom;
    private float currentTextSize = ViewerTextSizeHelper.DEFAULT_TEXT_SIZE;
    private PreferenceManager prefManager;
    private String currentFilePath;

    private ScaleGestureDetector scaleGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_viewer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_text_viewer));
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textContent = findViewById(R.id.text_content);
        lineNumberGutter = findViewById(R.id.line_number_gutter);
        lineNumberGutter.attach(textContent);
        loadingOverlay = findViewById(R.id.loading_overlay);
        scrollView = findViewById(R.id.scroll_view);
        viewerFindBar = new ViewerFindBar(this, textContent, scrollView, null,
                findViewById(R.id.viewer_find_bar),
                findViewById(R.id.viewer_find_query),
                findViewById(R.id.viewer_find_status));
        if (savedInstanceState != null) {
            restoredScrollY = savedInstanceState.getInt(KEY_SCROLL_Y, 0);
        }

        String filePath = getIntent().getStringExtra("file_path");
        String fileName = getIntent().getStringExtra("file_name");

        if (getSupportActionBar() != null && fileName != null) {
            getSupportActionBar().setSubtitle(fileName);
        }

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }

        prefManager = new PreferenceManager(this);
        // 走查 #38：查看器缩放字号持久化（与 Markdown 阅读器 saveFontSize 同型）——
        // 读上次保存的查看器字号；无记录回退默认 14sp（历史行为一致）。
        currentTextSize = ViewerTextSizeHelper.clamp(
                prefManager.getViewerTextSize(ViewerTextSizeHelper.DEFAULT_TEXT_SIZE));

        scaleGestureDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float newSize = ViewerTextSizeHelper.clamp(
                                currentTextSize * detector.getScaleFactor());
                        if (ViewerTextSizeHelper.shouldApply(currentTextSize, newSize)) {
                            currentTextSize = newSize;
                            textContent.setTextSize(newSize);
                            lineNumberGutter.refresh();
                        }
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        // 手势结束即持久化：下次打开/换文件恢复上次字号（间隙变化保存干净值）
                        prefManager.saveViewerTextSize(currentTextSize);
                    }
                });

        // 应用持久化字号：布局固化 14sp，运行时按上次缩放值恢复（行号槽 attach 内同步）
        textContent.setTextSize(currentTextSize);
        lineNumberGutter.refresh();

        if (filePath == null) { finish(); return; }
        currentFilePath = filePath;
        // 走查 #34：打开起点决策——设置「打开后跳到底部」开且全新打开=尾部（覆盖进度恢复，
        // 尾读日志/转储模式）；旋转恢复态=同会话权威（迭代40 口径，设置不覆盖会话位置）；
        // 否则=既有行为（持久化阅读进度>顶部）。
        startAtBottom = ViewerStartHelper.resolveStartMode(
                savedInstanceState != null, prefManager.getStartOnBottom())
                == ViewerStartHelper.StartMode.START_AT_BOTTOM;
        if (!startAtBottom && savedInstanceState == null && restoredScrollY <= 0) {
            restoredScrollY = persistedReadProgress();
        }
        loadTextFile(filePath);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (currentFilePath == null) return super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_FIND_ID, Menu.NONE, "查找")
                .setIcon(R.drawable.ic_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_COPY_PATH_ID, Menu.NONE, "复制路径")
                .setIcon(R.drawable.ic_content_copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_SHARE_ID, Menu.NONE, "分享")
                .setIcon(R.drawable.ic_share)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_GOTO_LINE_ID, Menu.NONE, "转到行号…")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_COPY_PATH_ID) {
            UiUtils.copyRemotePath(this, currentFilePath);
            return true;
        }
        if (item.getItemId() == MENU_FIND_ID) {
            viewerFindBar.show();
            return true;
        }
        if (item.getItemId() == MENU_GOTO_LINE_ID) {
            showGoToLineDialog();
            return true;
        }
        if (item.getItemId() == MENU_SHARE_ID) {
            UiUtils.shareText(this, textContent.getText().toString(),
                    ShareHelper.fileNameFromPath(currentFilePath));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 「转到行号…」：数字输入对话框（markor showGoToLineDialog 同型）+ 行首跳转。 */
    private void showGoToLineDialog() {
        CharSequence text = textContent.getText();
        if (text == null || text.length() == 0) {
            UiUtils.showToast(this, "内容尚未加载完成");
            return;
        }
        int total = LineNumberHelper.countLines(text);
        DialogHelper.showInputDialog(this, "转到行号",
                "输入行号（1-" + total + "）", "跳转", "取消",
                InputType.TYPE_CLASS_NUMBER, null,
                input -> {
                    int line = GoToLineHelper.parseLineNumber(input);
                    if (line < 0) {
                        UiUtils.showToast(this, "请输入有效行号");
                        return;
                    }
                    jumpToLine(line);
                });
    }

    /** 跳转到逻辑行行首：整行临时高亮（markor 文本编辑器无高亮仅滚到可见，查看器沿用查找同色系）+ 居中滚动。 */
    private void jumpToLine(int lineNumber) {
        CharSequence text = textContent.getText();
        int[] range = GoToLineHelper.lineHighlightRange(text, lineNumber);
        if (range[1] > range[0]) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            // 与查找栏同色系临时高亮；跳转后保留（与 ViewerFindBar 行为一致）
            ssb.setSpan(new BackgroundColorSpan(GOTO_HIGHLIGHT_COLOR),
                    range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textContent.setText(ssb);
            textContent.setTextIsSelectable(true);
        }
        UiUtils.scrollToOffsetCenter(scrollView, textContent, range[0]);
    }

    private void loadTextFile(String filePath) {
        SshManager.getInstance().readFile(filePath, new SshManager.FileContentCallback() {
            @Override
            public void onSuccess(String content) {
                runOnUiThread(() -> {
                    textContent.setText(content);
                    lineNumberGutter.refresh();
                    viewerFindBar.onContentChanged();
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    restoreScrollPosition();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    textContent.setText("加载失败: " + error);
                    lineNumberGutter.refresh();
                });
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scaleGestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }

    // ── Lifecycle: preserve scroll position across recreation ────────────────

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (scrollView != null) {
            outState.putInt(KEY_SCROLL_Y, scrollView.getScrollY());
        }
    }

    /**
     * Applies the scroll position saved in {@link #onSaveInstanceState}, once,
     * after the async content load has set the text.  The framework's own
     * ScrollView state restore runs before the content exists, so it is
     * ineffective here; this explicit restore keeps the reading position.
     * {@code startAtBottom}（走查 #34 设置开启）时改跳底部（标准 fullScroll），
     * 覆盖进度恢复——两分支互斥（startAtBottom 仅全新打开时成立）。
     */
    private void restoreScrollPosition() {
        if (startAtBottom) {
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
            return;
        }
        if (restoredScrollY <= 0) return;
        int y = restoredScrollY;
        restoredScrollY = 0;   // consume — apply exactly once
        scrollView.post(() -> scrollView.scrollTo(0, y));
    }

    // ── 阅读进度记忆（走查 #40：与 Markdown 阅读器/Code 查看器同型，跨会话「续读」）──

    /** 把当前阅读位置（滚动像素 Y）持久化，供下次打开同文件恢复。 */
    private void saveReadingProgress() {
        if (scrollView == null || currentFilePath == null) return;
        SshConfig cfg = SshManager.getInstance().getConfig();
        if (cfg == null) return;
        prefManager.saveReadProgress(cfg.getHost(), cfg.getPort(), cfg.getUsername(),
                currentFilePath, scrollView.getScrollY());
    }

    /** 读取持久化的阅读位置；无服务器上下文/未连接则返回 0（不恢复）。 */
    private int persistedReadProgress() {
        if (currentFilePath == null) return 0;
        SshConfig cfg = SshManager.getInstance().getConfig();
        if (cfg == null) return 0;
        return prefManager.getReadProgress(cfg.getHost(), cfg.getPort(), cfg.getUsername(),
                currentFilePath);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveReadingProgress();
    }
}
