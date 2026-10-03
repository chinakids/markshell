package com.ssh.mdreader.ui;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.CsvFindHelper;
import com.ssh.mdreader.util.UiUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CsvReaderActivity extends BaseActivity {

    private static final String KEY_SCROLL_Y = "scroll_y";
    private static final int MENU_COPY_PATH_ID = 0xA2001;
    private static final int MENU_FIND_ID = 0xA2002;
    private static final int FIND_HIGHLIGHT_COLOR = 0x66FFD600;

    private TableLayout tableLayout;
    private View loadingOverlay;
    private ScrollView scrollView;
    private HorizontalScrollView horizontalScrollView;
    private int restoredScrollY;
    private String currentFilePath;

    /** 最近一次渲染的结构化数据（查找扫描源）与对应视图引用（跳转落点）。 */
    private List<List<String>> rows = Collections.emptyList();
    private final List<TableRow> rowViews = new ArrayList<>();
    private final List<List<TextView>> cellViews = new ArrayList<>();

    // ── 查找状态（路线图 #21：单元格匹配语义见 CsvFindHelper）──────────────────
    private View findBar;
    private EditText etFindQuery;
    private TextView tvFindStatus;
    private List<CsvFindHelper.CellMatch> findMatches = Collections.emptyList();
    private int findCurrentIndex = -1;
    /** 当前高亮单元格与原始背景（跳转前恢复）。 */
    @Nullable
    private TextView findHighlightCell;
    @Nullable
    private Drawable findHighlightCellBg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_csv_reader);

        String fileName = getIntent().getStringExtra("file_name");
        setupToolbar(fileName != null ? fileName : "CSV", true);

        tableLayout = findViewById(R.id.table_layout);
        loadingOverlay = findViewById(R.id.loading_overlay);
        scrollView = findViewById(R.id.scroll_view);
        horizontalScrollView = findViewById(R.id.horizontal_scroll_view);
        if (savedInstanceState != null) {
            restoredScrollY = savedInstanceState.getInt(KEY_SCROLL_Y, 0);
        }
        initFindBar();

        loadContent();
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
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_COPY_PATH_ID) {
            UiUtils.copyRemotePath(this, currentFilePath);
            return true;
        }
        if (item.getItemId() == MENU_FIND_ID) {
            showFindBar();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── 查找栏（与 ViewerFindBar 相同的交互语义，宿主为表格故独立实现）──────────

    private void initFindBar() {
        findBar = findViewById(R.id.viewer_find_bar);
        etFindQuery = findViewById(R.id.viewer_find_query);
        tvFindStatus = findViewById(R.id.viewer_find_status);
        etFindQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                rebuildFind(false);   // 输入变化：重算并跳到第一处
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        findViewById(R.id.viewer_btn_find_prev).setOnClickListener(v -> findStep(-1));
        findViewById(R.id.viewer_btn_find_next).setOnClickListener(v -> findStep(1));
        findViewById(R.id.viewer_btn_find_close).setOnClickListener(v -> hideFindBar());
        etFindQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                findStep(1);   // 键盘搜索键 = 下一处（循环）
                return true;
            }
            return false;
        });
    }

    private void showFindBar() {
        findBar.setVisibility(View.VISIBLE);
        etFindQuery.requestFocus();
        etFindQuery.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etFindQuery, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        rebuildFind(false);
    }

    private void hideFindBar() {
        findBar.setVisibility(View.GONE);
        InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etFindQuery.getWindowToken(), 0);
        }
        findMatches = Collections.emptyList();
        findCurrentIndex = -1;
        tvFindStatus.setText("");
        clearFindHighlight();
    }

    /**
     * 重建当前查询词的全部单元格命中（CsvFindHelper.matchCells）。
     * {@code preserve=true}：保持序号不跳转；{@code false}：跳到第一处。
     */
    private void rebuildFind(boolean preserve) {
        if (findBar.getVisibility() != View.VISIBLE) return;
        String query = etFindQuery.getText().toString();
        int prev = findCurrentIndex;
        findMatches = CsvFindHelper.matchCells(rows, query, true);
        if (findMatches.isEmpty()) {
            findCurrentIndex = -1;
            tvFindStatus.setText(query.isEmpty() ? "" : "未找到");
            clearFindHighlight();
            return;
        }
        if (preserve && prev >= 0 && prev < findMatches.size()) {
            findCurrentIndex = prev;
            tvFindStatus.setText((findCurrentIndex + 1) + "/" + findMatches.size());
            return;
        }
        findCurrentIndex = 0;
        jumpToFindMatch();
    }

    private void findStep(int direction) {
        if (findMatches.isEmpty()) rebuildFind(false);
        if (findMatches.isEmpty()) {
            tvFindStatus.setText("未找到");
            return;
        }
        findCurrentIndex = CsvFindHelper.advance(findMatches, findCurrentIndex, direction);
        jumpToFindMatch();
    }

    /** 跳到当前命中单元格：高亮单元格+平滑滚动使所在行垂直居中。 */
    private void jumpToFindMatch() {
        if (findCurrentIndex < 0 || findCurrentIndex >= findMatches.size()) return;
        CsvFindHelper.CellMatch m = findMatches.get(findCurrentIndex);
        tvFindStatus.setText((findCurrentIndex + 1) + "/" + findMatches.size());
        if (m.row < 0 || m.row >= cellViews.size()) return;
        List<TextView> cells = cellViews.get(m.row);
        if (m.cell < 0 || m.cell >= cells.size()) return;
        TextView cell = cells.get(m.cell);

        clearFindHighlight();
        findHighlightCell = cell;
        findHighlightCellBg = cell.getBackground();
        cell.setBackgroundColor(FIND_HIGHLIGHT_COLOR);

        TableRow rowView = rowViews.get(m.row);
        scrollView.post(() -> {
            int top = rowView.getTop() + tableLayout.getTop() + horizontalScrollView.getTop();
            int y = Math.max(0, top + rowView.getHeight() / 2 - scrollView.getHeight() / 2);
            scrollView.smoothScrollTo(0, y);
        });
    }

    private void clearFindHighlight() {
        if (findHighlightCell != null) {
            if (findHighlightCellBg != null) {
                findHighlightCell.setBackground(findHighlightCellBg);
            } else {
                findHighlightCell.setBackgroundResource(R.color.card_background);
            }
        }
        findHighlightCell = null;
        findHighlightCellBg = null;
    }

    private void loadContent() {
        String filePath = getIntent().getStringExtra("file_path");
        if (filePath == null) { finish(); return; }
        currentFilePath = filePath;

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
                    restoreScrollPosition();
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
        // 重建结构：查找状态失效（旧视图引用已 detach），查找栏若开着则重扫
        rows = Collections.emptyList();
        rowViews.clear();
        cellViews.clear();
        clearFindHighlight();
        if (findBar.getVisibility() == View.VISIBLE) {
            findMatches = Collections.emptyList();
            findCurrentIndex = -1;
            tvFindStatus.setText("");
        }

        List<List<String>> newRows = new ArrayList<>();
        String[] lines = content.split("\n");
        int rowIdx = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            List<String> cells = parseCsvLine(line);
            newRows.add(cells);
            TableRow row = new TableRow(this);

            boolean isHeader = (rowIdx == 0);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            List<TextView> rowCellViews = new ArrayList<>();

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
                rowCellViews.add(tv);
            }

            if (isHeader) {
                row.setBackgroundResource(R.color.md_theme_primary);
            }
            tableLayout.addView(row);
            rowViews.add(row);
            cellViews.add(rowCellViews);
            rowIdx++;
        }
        rows = newRows;
        if (findBar.getVisibility() == View.VISIBLE) {
            rebuildFind(true);
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
     * after the async content load has built the table.  The framework's own
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
