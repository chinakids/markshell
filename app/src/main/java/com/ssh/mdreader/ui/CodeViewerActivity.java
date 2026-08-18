package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.CodeHighlighter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CodeViewerActivity extends BaseActivity {

    private TextView textLineNumbers;
    private TextView textCodeContent;
    private View loadingOverlay;
    private float currentTextSize = 14f;
    private static final float MIN_TEXT_SIZE = 8f;
    private static final float MAX_TEXT_SIZE = 32f;

    private ScaleGestureDetector scaleGestureDetector;
    private String rawCode;
    private String fileName;
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
        loadCodeFile(filePath);
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
                result = new SpannableStringBuilder(code);
            }
            final SpannableStringBuilder highlighted = result;
            runOnUiThread(() -> {
                textCodeContent.setText(highlighted);
                if (loadingOverlay != null) {
                    loadingOverlay.setVisibility(View.GONE);
                }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        highlightExecutor.shutdownNow();
    }
}
