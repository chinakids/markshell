package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

public class CsvReaderActivity extends BaseActivity {

    private TableLayout tableLayout;
    private View loadingOverlay;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_reader);

        String fileName = getIntent().getStringExtra("file_name");
        setupToolbar(fileName != null ? fileName : "CSV", true);

        tableLayout = findViewById(R.id.table_layout);
        loadingOverlay = findViewById(R.id.loading_overlay);
        scrollView = findViewById(R.id.scroll_view);

        loadContent();
    }

    private void loadContent() {
        String filePath = getIntent().getStringExtra("file_path");
        if (filePath == null) { finish(); return; }

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        SshManager.getInstance().readFile(filePath, new SshManager.FileContentCallback() {
            @Override
            public void onSuccess(String content) {
                runOnUiThread(() -> {
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    renderCsv(content);
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (loadingOverlay != null) {
                        loadingOverlay.setVisibility(View.GONE);
                    }
                    UiUtils.showSnackbar(tableLayout, "加载失败: " + message);
                });
            }
        });
    }

    private void renderCsv(String content) {
        tableLayout.removeAllViews();

        String[] lines = content.split("\n");
        int rowIdx = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            List<String> cells = parseCsvLine(line);
            TableRow row = new TableRow(this);

            boolean isHeader = (rowIdx == 0);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);

            for (String cell : cells) {
                TextView tv = new TextView(this);
                tv.setText(cell);
                tv.setPadding(pad, pad / 2, pad, pad / 2);
                tv.setTextSize(13);
                if (isHeader) {
                    tv.setTextColor(getResources().getColor(R.color.md_theme_primary, getTheme()));
                    tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                } else {
                    tv.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
                }
                // Vertical separator
                tv.setBackgroundResource(R.color.card_background);
                TableRow.LayoutParams lp = new TableRow.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMarginEnd(1);
                tv.setLayoutParams(lp);
                row.addView(tv);
            }

            if (isHeader) {
                row.setBackgroundResource(R.color.md_theme_primary);
            }
            tableLayout.addView(row);
            rowIdx++;
        }
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
}
