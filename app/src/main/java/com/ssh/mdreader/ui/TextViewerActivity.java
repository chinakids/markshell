package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;

public class TextViewerActivity extends BaseActivity {

    private TextView textContent;
    private View loadingOverlay;
    private float currentTextSize = 14f;
    private static final float MIN_TEXT_SIZE = 8f;
    private static final float MAX_TEXT_SIZE = 32f;

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
        loadingOverlay = findViewById(R.id.loading_overlay);

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
                        }
                        return true;
                    }
                });

        if (filePath == null) { finish(); return; }
        loadTextFile(filePath);
    }

    private void loadTextFile(String filePath) {
        SshManager.getInstance().readFile(filePath, new SshManager.FileContentCallback() {
            @Override
            public void onSuccess(String content) {
                runOnUiThread(() -> {
                    textContent.setText(content);
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    textContent.setText("加载失败: " + error);
                });
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scaleGestureDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
    }
}
