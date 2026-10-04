package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.text.InputType;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.CodeHighlighter;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.GoToLineHelper;
import com.ssh.mdreader.util.LineNumberHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;
import com.ssh.mdreader.util.ViewerFindBar;
import com.ssh.mdreader.util.ViewerTextSizeHelper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CodeViewerActivity extends BaseActivity {

    private static final String TAG = "CodeViewerActivity";
    private static final String KEY_SCROLL_Y = "scroll_y";
    private static final int MENU_COPY_PATH_ID = 0xA2001;
    private static final int MENU_FIND_ID = 0xA2002;
    private static final int MENU_GOTO_LINE_ID = 0xA2003;
    private static final int GOTO_HIGHLIGHT_COLOR = 0x66FFD600;

    private TextView textLineNumbers;
    private TextView textCodeContent;
    private View loadingOverlay;
    private ScrollView scrollView;
    private HorizontalScrollView horizontalScrollView;
    private ViewerFindBar viewerFindBar;
    private int restoredScrollY;
    private float currentTextSize = ViewerTextSizeHelper.DEFAULT_TEXT_SIZE;
    private PreferenceManager prefManager;

    private ScaleGestureDetector scaleGestureDetector;
    private String rawCode;
    private String fileName;
    private String currentFilePath;
    private final ExecutorService highlightExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_code_viewer);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("代码查看器");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textLineNumbers = findViewById(R.id.text_line_numbers);
        textCodeContent = findViewById(R.id.text_code_content);
        loadingOverlay = findViewById(R.id.loading_overlay);
        scrollView = findViewById(R.id.scroll_view);
        horizontalScrollView = findViewById(R.id.horizontal_scroll_view);
        viewerFindBar = new ViewerFindBar(this, textCodeContent, scrollView, horizontalScrollView,
                findViewById(R.id.viewer_find_bar),
                findViewById(R.id.viewer_find_query),
                findViewById(R.id.viewer_find_status));
        if (savedInstanceState != null) {
            restoredScrollY = savedInstanceState.getInt(KEY_SCROLL_Y, 0);
        }

        String filePath = getIntent().getStringExtra("file_path");
        fileName = getIntent().getStringExtra("file_name");

        if (getSupportActionBar() != null && fileName != null) {
            getSupportActionBar().setSubtitle(fileName);
        }

        // Show loading overlay immediately
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
                            textLineNumbers.setTextSize(currentTextSize);
                            textCodeContent.setTextSize(currentTextSize);
                        }
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        // 手势结束即持久化：下次打开/换文件恢复上次字号（间隙变化保存干净值）
                        prefManager.saveViewerTextSize(currentTextSize);
                    }
                });

        // 应用持久化字号：布局固化 14sp，运行时按上次缩放值恢复（行号槽与正文同步）
        textLineNumbers.setTextSize(currentTextSize);
        textCodeContent.setTextSize(currentTextSize);

        if (filePath == null) { finish(); return; }
        currentFilePath = filePath;
        // 走查 #40：仅无旋转恢复态（savedInstanceState==null）时从持久化阅读进度恢复
        // （「续读」；旋转态=同会话权威优先，避免旋转后覆盖会话内位置）
        if (savedInstanceState == null && restoredScrollY <= 0) {
            restoredScrollY = persistedReadProgress();
        }
        loadCodeFile(filePath);
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
        return super.onOptionsItemSelected(item);
    }

    /** 「转到行号…」：数字输入对话框（markor showGoToLineDialog 同型）+ 行首跳转。 */
    private void showGoToLineDialog() {
        CharSequence text = textCodeContent.getText();
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

    /** 跳转到逻辑行行首：整行临时高亮 + 纵向居中 + 横向滚回左缘（行首列，整行可见）。 */
    private void jumpToLine(int lineNumber) {
        CharSequence text = textCodeContent.getText();
        int[] range = GoToLineHelper.lineHighlightRange(text, lineNumber);
        if (range[1] > range[0]) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            ssb.setSpan(new BackgroundColorSpan(GOTO_HIGHLIGHT_COLOR),
                    range[0], range[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textCodeContent.setText(ssb);
            textCodeContent.setTextIsSelectable(true);
        }
        UiUtils.scrollToOffsetCenter(scrollView, textCodeContent, range[0]);
        horizontalScrollView.smoothScrollTo(0, 0);
    }

    private void loadCodeFile(String filePath) {
        SshManager.getInstance().readFile(filePath, new SshManager.FileContentCallback() {
            @Override
            public void onSuccess(String content) {
                rawCode = content;
                runOnUiThread(() -> {
                    displayLineNumbers(content);
                    highlightAsync(content);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    textCodeContent.setText("加载失败: " + error);
                });
            }
        });
    }

    private void highlightAsync(String code) {
        highlightExecutor.execute(() -> {
            SpannableStringBuilder result;
            try {
                result = CodeHighlighter.highlight(
                        code, fileName != null ? fileName : "");
            } catch (Throwable t) {
                Log.w(TAG, "代码高亮失败（回退纯文本）: " + fileName, t);
                result = new SpannableStringBuilder(code);
            }
            final SpannableStringBuilder highlighted = result;
            runOnUiThread(() -> {
                textCodeContent.setText(highlighted);
                viewerFindBar.onContentChanged();
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                restoreScrollPosition();
            });
        });
    }

    private void displayLineNumbers(String content) {
        // 行数/序列语义单一源=LineNumberHelper（与纯文本查看器行号槽共用，防两套实现漂移）
        textLineNumbers.setText(LineNumberHelper.numberSequence(LineNumberHelper.countLines(content)));
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
     * after async content + highlighting has set the text.  The framework's
     * own ScrollView state restore runs before the content exists, so it is
     * ineffective here; this explicit restore keeps the reading position.
     */
    private void restoreScrollPosition() {
        if (restoredScrollY <= 0) return;
        int y = restoredScrollY;
        restoredScrollY = 0;   // consume — apply exactly once
        scrollView.post(() -> scrollView.scrollTo(0, y));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        highlightExecutor.shutdownNow();
    }

    // ── 阅读进度记忆（走查 #40：与 Markdown 阅读器同型，跨会话「续读」）────────

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
