package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.CodeHighlighter;
import com.ssh.mdreader.util.UiUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CodeViewerActivity extends BaseActivity {

    private static final String TAG = "CodeViewerActivity";
    private static final String KEY_SCROLL_Y = "scroll_y";
    private static final int MENU_COPY_PATH_ID = 0xA2001;

    private TextView textLineNumbers;
    private TextView textCodeContent;
    private View loadingOverlay;
    private ScrollView scrollView;
    private int restoredScrollY;
    private float currentTextSize = 14f;
    private static final float MIN_TEXT_SIZE = 8f;
    private static final float MAX_TEXT_SIZE = 32f;

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

        scaleGestureDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float newSize = currentTextSize * detector.getScaleFactor();
                        newSize = Math.max(MIN_TEXT_SIZE, Math.min(MAX_TEXT_SIZE, newSize));
                        if (Math.abs(newSize - currentTextSize) > 0.5f) {
                            currentTextSize = newSize;
                            textLineNumbers.setTextSize(currentTextSize);
                            textCodeContent.setTextSize(currentTextSize);
                        }
                        return true;
                    }
                });

        if (filePath == null) { finish(); return; }
        currentFilePath = filePath;
        loadCodeFile(filePath);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (currentFilePath == null) return super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_COPY_PATH_ID, Menu.NONE, "复制路径")
                .setIcon(R.drawable.ic_content_copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_COPY_PATH_ID) {
            UiUtils.copyRemotePath(this, currentFilePath);
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
                restoreScrollPosition();
            });
        });
    }

    private void displayLineNumbers(String content) {
        String[] lines = content.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines.length; i++) {
            sb.append(i).append('\n');
        }
        textLineNumbers.setText(sb.toString());
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
}
