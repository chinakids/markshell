package com.ssh.mdreader.ui;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.CsvFindHelper;
import com.ssh.mdreader.util.CsvGoToLineHelper;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.GoToLineHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.ShareHelper;
import com.ssh.mdreader.util.UiUtils;
import com.ssh.mdreader.util.ViewerTextSizeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CsvReaderActivity extends BaseActivity {

    private static final String KEY_SCROLL_Y = "scroll_y";
    private static final int MENU_COPY_PATH_ID = 0xA2001;
    private static final int MENU_FIND_ID = 0xA2002;
    private static final int MENU_SHARE_ID = 0xA2003;
    private static final int MENU_GOTO_LINE_ID = 0xA2004;
    private static final int FIND_HIGHLIGHT_COLOR = 0x66FFD600;
    /** 转到行号临时高亮色（与 Text/Code 查看器 GOTO_HIGHLIGHT_COLOR 同值 0x66FFD600）。 */
    private static final int GOTO_HIGHLIGHT_COLOR = 0x66FFD600;
    /** 单元格默认字号（sp）：历史行为一致（renderCsv 原硬编码 13）。 */
    private static final float DEFAULT_CELL_TEXT_SIZE = 13f;

    private TableLayout tableLayout;
    private View loadingOverlay;
    private ScrollView scrollView;
    private HorizontalScrollView horizontalScrollView;
    private int restoredScrollY;
    private String currentFilePath;
    private PreferenceManager prefManager;
    /** 走查 #40：本次创建是否带旋转恢复态（onCreate 记录；seed 数据仅在无旋转态时使用）。 */
    private boolean hasInstanceState;

    // ── 查看器字号缩放（走查 #52）──────────────────────────────────────────────
    /** 双指缩放检测器（与 Text/Code 查看器同款范式）。 */
    private ScaleGestureDetector scaleDetector;
    /** 当前字号（sp）；clamp 与持久化全部委托 ViewerTextSizeHelper=单一语义源。 */
    private float currentTextSize;

    /** 最近一次渲染的结构化数据（查找扫描源）与对应视图引用（跳转落点）。 */
    private List<List<String>> rows = Collections.emptyList();
    private final List<TableRow> rowViews = new ArrayList<>();
    private final List<List<TextView>> cellViews = new ArrayList<>();
    /** 原始 CSV 文本（分享用；表格重建后原字符串不再存在于结构层，故保留）。 */
    private String rawCsvContent;

    // ── 查找状态（路线图 #21：单元格匹配语义见 CsvFindHelper）──────────────────
    private View findBar;
    private EditText etFindQuery;
    private TextView tvFindStatus;
    private List<CsvFindHelper.CellMatch> findMatches = Collections.emptyList();
    private int findCurrentIndex = -1;
    /** 当前高亮单元格与原始背景（跳转前恢复）。 */
    @Nullable
    private TextView highlightCell;
    @Nullable
    private Drawable highlightCellBg;

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
            hasInstanceState = true;
        }
        initFindBar();

        prefManager = new PreferenceManager(this);
        // 走查 #52：CSV 查看器补齐同组缩放（Text/Code 查看器迭代71 #38 已实现，
        // CSV 无=同组能力不一致）——读取上次保存的查看器字号，无记录回退默认 13sp
        // （历史行为一致）；与 Text/Code 共用 KEY_VIEWER_TEXT_SIZE 全局一键
        // （markor pref_key__view_font_size 等价物，迭代71 范围裁剪同语义）。
        currentTextSize = ViewerTextSizeHelper.clamp(
                prefManager.getViewerTextSize(DEFAULT_CELL_TEXT_SIZE));
        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float newSize = ViewerTextSizeHelper.clamp(
                                currentTextSize * detector.getScaleFactor());
                        if (ViewerTextSizeHelper.shouldApply(currentTextSize, newSize)) {
                            currentTextSize = newSize;
                            applyCellTextSize();
                        }
                        return true;
                    }

                    @Override
                    public void onScaleEnd(ScaleGestureDetector detector) {
                        // 手势结束即持久化：下次打开/换文件恢复上次字号（与 Text/Code 同款）
                        prefManager.saveViewerTextSize(currentTextSize);
                    }
                });
        loadContent();
    }

    /**
     * 把当前字号应用到全部单元格（双指缩放实时；TableLayout 对子项 wrap_content
     * 高度自动重测量，行高随字号增长）。渲染建单元格时也直接使用 {@link #currentTextSize}。
     */
    private void applyCellTextSize() {
        for (List<TextView> cells : cellViews) {
            for (TextView cell : cells) {
                cell.setTextSize(currentTextSize);
            }
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scaleDetector.onTouchEvent(ev);
        return super.dispatchTouchEvent(ev);
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
            showFindBar();
            return true;
        }
        if (item.getItemId() == MENU_SHARE_ID) {
            UiUtils.shareText(this, rawCsvContent != null ? rawCsvContent : "",
                    ShareHelper.fileNameFromPath(currentFilePath));
            return true;
        }
        if (item.getItemId() == MENU_GOTO_LINE_ID) {
            showGoToLineDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── 转到行号（走查 #55：Text/Code 查看器已有，CSV 无=同组能力不一致）────────

    /** 「转到行号…」：数字输入对话框（markor showGoToLineDialog 同型语义）+ 行定位居中跳转。 */
    private void showGoToLineDialog() {
        int total = rowViews.size();
        if (total == 0) {
            UiUtils.showToast(this, "文件没有数据行");
            return;
        }
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

    /** 跳到第 {@code lineNumber} 行（1-based，含表头行=第 1 行）：首单元格临时高亮+居中滚动。 */
    private void jumpToLine(int lineNumber) {
        int rowIndex = CsvGoToLineHelper.rowIndexFor(lineNumber, rowViews.size());
        if (rowIndex < 0 || rowIndex >= rowViews.size()) {
            return;
        }
        List<TextView> cells = cellViews.get(rowIndex);
        clearTemporaryHighlight();
        TextView cell = cells.isEmpty() ? null : cells.get(0);
        if (cell != null) {
            highlightCell = cell;
            highlightCellBg = cell.getBackground();
            cell.setBackgroundColor(GOTO_HIGHLIGHT_COLOR);
        }
        scrollToRow(rowIndex);
    }

    /** 平滑滚动使第 {@code rowIndex} 行垂直居中（查找命中与转到行号共用=单一滚动语义源）。 */
    private void scrollToRow(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rowViews.size()) return;
        TableRow rowView = rowViews.get(rowIndex);
        scrollView.post(() -> {
            int top = rowView.getTop() + tableLayout.getTop() + horizontalScrollView.getTop();
            int y = Math.max(0, top + rowView.getHeight() / 2 - scrollView.getHeight() / 2);
            scrollView.smoothScrollTo(0, y);
        });
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
        clearTemporaryHighlight();
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
            clearTemporaryHighlight();
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

        clearTemporaryHighlight();
        highlightCell = cell;
        highlightCellBg = cell.getBackground();
        cell.setBackgroundColor(FIND_HIGHLIGHT_COLOR);

        scrollToRow(m.row);
    }

    private void clearTemporaryHighlight() {
        if (highlightCell != null) {
            if (highlightCellBg != null) {
                highlightCell.setBackground(highlightCellBg);
            } else {
                highlightCell.setBackgroundResource(R.color.card_background);
            }
        }
        highlightCell = null;
        highlightCellBg = null;
    }

    private void loadContent() {
        String filePath = getIntent().getStringExtra("file_path");
        if (filePath == null) { finish(); return; }
        currentFilePath = filePath;
        // 走查 #40：仅无旋转恢复态时从持久化阅读进度恢复（「续读」；旋转态=同会话权威优先）
        if (!hasInstanceState && restoredScrollY <= 0) {
            restoredScrollY = persistedReadProgress();
        }

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
        rawCsvContent = content;
        tableLayout.removeAllViews();
        // 重建结构：查找状态失效（旧视图引用已 detach），查找栏若开着则重扫
        rows = Collections.emptyList();
        rowViews.clear();
        cellViews.clear();
        clearTemporaryHighlight();
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
                // 走查 #43：单元格与 Code/Text/Markdown 查看器同款可选中复制（长按→选择/复制）。
                // 无点击行为冲突（单元格无 OnClickListener），选择柄与 HorizontalScrollView 共存
                // 与 Markdown 阅读器 ScrollView 内 selectable 为同一平台机制（同类已验证）。
                tv.setTextIsSelectable(true);
                tv.setPadding(pad, pad / 2, pad, pad / 2);
                // 走查 #52：字号=查看器持久化值（双指缩放同源；原硬编码 13 改为默认常量）
                tv.setTextSize(currentTextSize);
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

    // ── 阅读进度记忆（走查 #40：与 Markdown 阅读器/Code/Text 查看器同型，跨会话「续读」）──

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
