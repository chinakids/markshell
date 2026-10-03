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
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.GoToLineHelper;
import com.ssh.mdreader.util.LineNumberHelper;
import com.ssh.mdreader.util.UiUtils;
import com.ssh.mdreader.util.ViewerFindBar;
import com.ssh.mdreader.widget.LineNumberGutterView;

public class TextViewerActivity extends BaseActivity {

    private static final String KEY_SCROLL_Y = "scroll_y";
    private static final int MENU_COPY_PATH_ID = 0xA2001;
    private static final int MENU_FIND_ID = 0xA2002;
    private static final int MENU_GOTO_LINE_ID = 0xA2003;
    private static final int GOTO_HIGHLIGHT_COLOR = 0x66FFD600;

    private TextView textContent;
    private LineNumberGutterView lineNumberGutter;
    private View loadingOverlay;
    private ScrollView scrollView;
    private ViewerFindBar viewerFindBar;
    private int restoredScrollY;
    private float currentTextSize = 14f;
    private static final float MIN_TEXT_SIZE = 8f;
    private static final float MAX_TEXT_SIZE = 32f;
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

        scaleGestureDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float newSize = currentTextSize * detector.getScaleFactor();
                        newSize = Math.max(MIN_TEXT_SIZE, Math.min(MAX_TEXT_SIZE, newSize));
                        if (Math.abs(newSize - currentTextSize) > 0.5f) {
                            currentTextSize = newSize;
                            textContent.setTextSize(newSize);
                            lineNumberGutter.refresh();
                        }
                        return true;
                    }
                });

        if (filePath == null) { finish(); return; }
        currentFilePath = filePath;
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
     */
    private void restoreScrollPosition() {
        if (restoredScrollY <= 0) return;
        int y = restoredScrollY;
        restoredScrollY = 0;   // consume — apply exactly once
        scrollView.post(() -> scrollView.scrollTo(0, y));
    }
}
