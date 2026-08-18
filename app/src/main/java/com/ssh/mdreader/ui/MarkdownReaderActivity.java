package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.adapter.AnnotationListAdapter;
import com.ssh.mdreader.model.AnnotationEntry;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.ui.span.AnnotationSpan;
import com.ssh.mdreader.util.AnnotationHelper;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.image.ImagesPlugin;

public class MarkdownReaderActivity extends BaseActivity {

    private static final int FONT_SIZE_MIN     = 12;
    private static final int FONT_SIZE_MAX     = 40;
    private static final int FONT_SIZE_DEFAULT = 16;
    private static final int MENU_ANNOTATE_ID  = 0xA1010;
    private static final int MENU_DRAWER_ID    = 0xA1011;

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView    tvContent;
    private ProgressBar progressBar;
    private ScrollView  scrollView;

    // ── Drawer ────────────────────────────────────────────────────────────────
    private DrawerLayout          drawerLayout;
    private LinearLayout          drawerView;
    private RecyclerView          rvAnnotationList;
    private LinearLayout          layoutEmptyAnnotations;
    private TextView              tvAnnotationCount;
    private AnnotationListAdapter annotationListAdapter;

    // ── Rendering ─────────────────────────────────────────────────────────────
    // Markwon is built once; no plugin needed — spans applied post-render.
    private Markwon markwon;
    private String  markdownContent;
    private int     currentFontSize;
    private PreferenceManager prefManager;
    private ScaleGestureDetector scaleDetector;

    // ── Annotations ───────────────────────────────────────────────────────────
    private final List<AnnotationEntry> annotations = new ArrayList<>();
    private String annotationFilePath;
    private String filePath;

    // ── Active popup ──────────────────────────────────────────────────────────
    private PopupWindow activePopup;
    private float lastTouchX, lastTouchY;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown_reader);

        filePath = getIntent().getStringExtra("file_path");
        String fileName = getIntent().getStringExtra("file_name");
        setupToolbar(fileName != null ? fileName : getString(R.string.title_reader), true);

        prefManager = new PreferenceManager(this);
        currentFontSize = prefManager.getFontSize(FONT_SIZE_DEFAULT);

        if (filePath != null) {
            annotationFilePath = AnnotationHelper.buildAnnotationFilePath(filePath);
        }

        initViews();
        initDrawer();
        buildMarkwon();
        loadContent();
    }

    // ── Toolbar menu ──────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_DRAWER_ID, Menu.NONE, "批注列表")
                .setIcon(R.drawable.ic_annotation_drawer)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_DRAWER_ID) {
            if (drawerLayout.isDrawerOpen(drawerView)) {
                drawerLayout.closeDrawer(drawerView);
            } else {
                refreshAnnotationDrawer();
                drawerLayout.openDrawer(drawerView);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private void initViews() {
        tvContent   = findViewById(R.id.tv_content);
        progressBar = findViewById(R.id.progress_bar);
        scrollView  = findViewById(R.id.scroll_view);

        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector d) {
                        int ns = Math.round(currentFontSize * d.getScaleFactor());
                        ns = Math.max(FONT_SIZE_MIN, Math.min(FONT_SIZE_MAX, ns));
                        if (ns != currentFontSize) { currentFontSize = ns; render(); }
                        return true;
                    }
                    @Override
                    public void onScaleEnd(ScaleGestureDetector d) {
                        prefManager.saveFontSize(currentFontSize);
                    }
                });

        scrollView.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            return event.getPointerCount() >= 2;
        });

        tvContent.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                if (activePopup != null) { activePopup.dismiss(); activePopup = null; }
            }
            return false;
        });

        tvContent.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                menu.add(Menu.NONE, MENU_ANNOTATE_ID, Menu.FIRST, "批注")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == MENU_ANNOTATE_ID) {
                    int s = tvContent.getSelectionStart();
                    int e = tvContent.getSelectionEnd();
                    if (s >= 0 && e > s) {
                        mode.finish();
                        showAnnotationInputDialog(s, e);
                    }
                    return true;
                }
                return false;
            }
            @Override public void onDestroyActionMode(ActionMode m) {}
        });
    }

    private void initDrawer() {
        drawerLayout           = findViewById(R.id.drawer_layout);
        drawerView             = findViewById(R.id.drawer_annotations);
        rvAnnotationList       = findViewById(R.id.rv_annotation_list);
        layoutEmptyAnnotations = findViewById(R.id.layout_empty_annotations);
        tvAnnotationCount      = findViewById(R.id.tv_annotation_count);

        int drawerWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.75f);
        ViewGroup.LayoutParams lp = drawerView.getLayoutParams();
        lp.width = drawerWidth;
        drawerView.setLayoutParams(lp);

        annotationListAdapter = new AnnotationListAdapter();
        rvAnnotationList.setLayoutManager(new LinearLayoutManager(this));
        rvAnnotationList.setAdapter(annotationListAdapter);

        annotationListAdapter.setOnItemClickListener(entry -> {
            drawerLayout.closeDrawer(drawerView);
            scrollToAnnotation(entry);
        });

        annotationListAdapter.setOnItemDeleteListener(entry -> {
            drawerLayout.closeDrawer(drawerView);
            confirmDeleteAnnotation(entry);
        });

        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, drawerView);
    }

    // ── Markwon ───────────────────────────────────────────────────────────────

    /** Built once — no annotation plugin needed. */
    private void buildMarkwon() {
        markwon = Markwon.builder(this)
                .usePlugin(ImagesPlugin.create())
                .usePlugin(TablePlugin.create(this))
                .usePlugin(TaskListPlugin.create(this))
                .build();
    }

    // ── Content loading ───────────────────────────────────────────────────────

    private void loadContent() {
        if (filePath == null) { finish(); return; }

        progressBar.setVisibility(View.VISIBLE);
        SshManager.getInstance().readFile(filePath, new SshManager.FileContentCallback() {
            @Override
            public void onSuccess(String content) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    markdownContent = content;
                    render();           // render first, then load spans
                    loadAnnotations();
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    UiUtils.showSnackbar(tvContent,
                            getString(R.string.error_loading) + ": " + message);
                });
            }
        });
    }

    private void loadAnnotations() {
        if (annotationFilePath == null) return;
        SshManager.getInstance().readFile(annotationFilePath,
                new SshManager.FileContentCallback() {
                    @Override
                    public void onSuccess(String content) {
                        runOnUiThread(() -> {
                            annotations.clear();
                            annotations.addAll(AnnotationHelper.parseAnnotationFile(content));
                            applyAnnotationSpans();
                        });
                    }
                    @Override
                    public void onError(String ignored) { /* no CSV yet — normal on first open */ }
                });
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Renders {@code markdownContent} into the TextView and immediately overlays
     * annotation spans.  Scroll position is preserved.
     */
    private void render() {
        if (markdownContent == null) return;

        final int savedScrollY = scrollView.getScrollY();

        tvContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
        markwon.setMarkdown(tvContent, markdownContent);
        tvContent.setMovementMethod(LinkMovementMethod.getInstance());

        applyAnnotationSpans();

        if (savedScrollY > 0) {
            scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
        }
    }

    /**
     * Overlays {@link AnnotationSpan} objects on the rendered text.
     *
     * <p>For each annotation, {@link AnnotationHelper#findNthOccurrence} locates
     * the exact occurrence recorded by {@link AnnotationEntry#occurrenceIndex},
     * so duplicate text is handled correctly without any source-file modification.</p>
     *
     * <p>Scroll position is NOT touched here — caller is responsible if needed.</p>
     */
    private void applyAnnotationSpans() {
        if (annotations.isEmpty()) return;

        CharSequence current = tvContent.getText();
        SpannableStringBuilder ssb = new SpannableStringBuilder(current);
        String plain = ssb.toString();

        for (AnnotationEntry entry : annotations) {
            if (entry.originalText == null || entry.originalText.isEmpty()) continue;

            int spanStart = AnnotationHelper.findNthOccurrence(
                    plain, entry.originalText, entry.occurrenceIndex);
            if (spanStart < 0) continue;

            int spanEnd = spanStart + entry.originalText.length();
            if (spanEnd > ssb.length()) continue;

            ssb.setSpan(new AnnotationSpan(entry, this::onAnnotationClicked),
                    spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new AnnotationSpan.DashedUnderlineSpan(),
                    spanStart, spanEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        tvContent.setText(ssb);
        tvContent.setMovementMethod(LinkMovementMethod.getInstance());
    }

    // ── Drawer helpers ────────────────────────────────────────────────────────

    private void refreshAnnotationDrawer() {
        annotationListAdapter.setData(annotations);
        if (annotations.isEmpty()) {
            rvAnnotationList.setVisibility(View.GONE);
            layoutEmptyAnnotations.setVisibility(View.VISIBLE);
            tvAnnotationCount.setText("");
        } else {
            rvAnnotationList.setVisibility(View.VISIBLE);
            layoutEmptyAnnotations.setVisibility(View.GONE);
            tvAnnotationCount.setText(annotations.size() + " 条");
        }
    }

    /**
     * Scrolls to the annotated text by finding the AnnotationSpan with the
     * matching ID directly from the Spanned TextView output.
     */
    private void scrollToAnnotation(AnnotationEntry entry) {
        android.text.Spanned spanned = (android.text.Spanned) tvContent.getText();
        AnnotationSpan[] spans = spanned.getSpans(0, spanned.length(), AnnotationSpan.class);

        int charOffset = -1;
        for (AnnotationSpan span : spans) {
            if (span.getEntry().id.equals(entry.id)) {
                charOffset = spanned.getSpanStart(span);
                break;
            }
        }

        if (charOffset < 0) return;

        final int finalOffset = charOffset;
        tvContent.post(() -> {
            Layout layout = tvContent.getLayout();
            if (layout == null) return;

            int line    = layout.getLineForOffset(finalOffset);
            int lineTop = layout.getLineTop(line);
            int paddingTop = (int) (16 * getResources().getDisplayMetrics().density);
            int scrollY    = Math.max(0, lineTop + paddingTop - scrollView.getHeight() / 2);
            scrollView.smoothScrollTo(0, scrollY);
        });
    }

    // ── Annotation click → PopupWindow ────────────────────────────────────────

    private void onAnnotationClicked(AnnotationEntry entry, View anchor) {
        if (isFinishing() || isDestroyed()) return;
        if (activePopup != null) { activePopup.dismiss(); activePopup = null; }

        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_annotation, null);
        ((TextView) popupView.findViewById(R.id.tv_annotation_content)).setText(entry.text);

        PopupWindow popup = new PopupWindow(
                popupView, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(null);
        popup.setElevation(16f);
        popup.setOutsideTouchable(true);
        popup.setOnDismissListener(() -> { if (activePopup == popup) activePopup = null; });

        View btnDelete = popupView.findViewById(R.id.btn_popup_delete);
        if (btnDelete != null) {
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> { popup.dismiss(); confirmDeleteAnnotation(entry); });
        }

        popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        int popupW = popupView.getMeasuredWidth();
        int popupH = popupView.getMeasuredHeight();
        int sw = getResources().getDisplayMetrics().widthPixels;
        int sh = getResources().getDisplayMetrics().heightPixels;
        int m  = (int) (16 * getResources().getDisplayMetrics().density);

        int x = (sw - popupW) / 2;
        int y = (int) lastTouchY - popupH - (int) (12 * getResources().getDisplayMetrics().density);
        if (y < m) y = (int) lastTouchY + (int) (12 * getResources().getDisplayMetrics().density);
        if (y + popupH > sh - m) y = sh - popupH - m;

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
        activePopup = popup;
    }

    private void confirmDeleteAnnotation(AnnotationEntry entry) {
        if (isFinishing() || isDestroyed()) return;
        DialogHelper.showDangerConfirmDialog(this,
                "删除批注", "确定要删除这条批注吗？",
                "删除", "取消",
                d -> deleteAnnotation(entry),
                d -> {});
    }

    /**
     * Deletes an annotation: removes it from the in-memory list, rewrites the CSV
     * (or deletes the CSV file if the list is now empty), then re-applies spans.
     * The Markdown source file is NOT touched.
     */
    private void deleteAnnotation(AnnotationEntry entry) {
        if (annotationFilePath == null) return;

        annotations.removeIf(a -> a.id.equals(entry.id));

        if (annotations.isEmpty()) {
            SshManager.getInstance().deleteFile(annotationFilePath,
                    new SshManager.DeleteFileCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                render();   // re-render to clear all spans
                                UiUtils.showToast(MarkdownReaderActivity.this, "批注已删除");
                            });
                        }
                        @Override
                        public void onError(String ignored) {
                            runOnUiThread(() -> {
                                render();
                                UiUtils.showToast(MarkdownReaderActivity.this, "批注已删除");
                            });
                        }
                    });
        } else {
            String csv = AnnotationHelper.formatAnnotationFile(annotations);
            SshManager.getInstance().writeFile(annotationFilePath, csv, false,
                    new SshManager.WriteFileCallback() {
                        @Override
                        public void onSuccess() {
                            runOnUiThread(() -> {
                                // Re-apply spans only — no need to re-render Markdown
                                markwon.setMarkdown(tvContent, markdownContent);
                                tvContent.setMovementMethod(LinkMovementMethod.getInstance());
                                applyAnnotationSpans();
                                UiUtils.showToast(MarkdownReaderActivity.this, "批注已删除");
                            });
                        }
                        @Override
                        public void onError(String message) {
                            // Revert in-memory removal on failure
                            annotations.add(entry);
                            runOnUiThread(() ->
                                    UiUtils.showSnackbar(tvContent, "CSV更新失败: " + message));
                        }
                    });
        }
    }

    // ── Annotation input dialog ───────────────────────────────────────────────

    private void showAnnotationInputDialog(int tvSelStart, int tvSelEnd) {
        if (isFinishing() || isDestroyed()) return;
        if (markdownContent == null) return;

        String selectedText = "";
        try {
            CharSequence rendered = tvContent.getText();
            int safeEnd = Math.min(tvSelEnd, rendered.length());
            if (tvSelStart >= 0 && tvSelStart < safeEnd) {
                selectedText = rendered.subSequence(tvSelStart, safeEnd).toString();
            }
        } catch (Exception ignored) {}

        final String finalSelected = selectedText.trim();
        final int    finalSelStart = tvSelStart;

        DialogHelper.showInputDialog(this, "添加批注", "请输入批注内容…", "确认", "取消",
                input -> {
                    if (input.isEmpty()) return;
                    if (finalSelected.isEmpty()) {
                        UiUtils.showToast(this, "未能读取选中文本");
                        return;
                    }
                    // Count how many times the selected text appeared before this selection
                    // to determine the correct occurrenceIndex.
                    String renderedStr = tvContent.getText().toString();
                    int occurrenceIndex = AnnotationHelper.countOccurrencesBefore(
                            renderedStr, finalSelected, finalSelStart);

                    String id = AnnotationHelper.generateId();
                    AnnotationEntry entry =
                            new AnnotationEntry(id, input, finalSelected, occurrenceIndex);
                    saveAnnotation(entry);
                });
    }

    /**
     * Saves an annotation: writes only to the CSV file — the Markdown source is
     * never modified.  Then re-applies spans to the existing rendered text.
     */
    private void saveAnnotation(AnnotationEntry entry) {
        if (annotationFilePath == null) return;

        boolean isFirst = annotations.isEmpty();
        String csvContent = isFirst
                ? AnnotationHelper.CSV_HEADER + "\n" + entry.format() + "\n"
                : entry.format() + "\n";

        SshManager.getInstance().writeFile(annotationFilePath, csvContent, !isFirst,
                new SshManager.WriteFileCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            annotations.add(entry);
                            applyAnnotationSpans(); // overlay span without re-rendering
                            UiUtils.showToast(MarkdownReaderActivity.this, "批注已保存");
                        });
                    }
                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                                UiUtils.showSnackbar(tvContent, "批注保存失败: " + message));
                    }
                });
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(drawerView)) {
            drawerLayout.closeDrawer(drawerView);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (activePopup != null) { activePopup.dismiss(); activePopup = null; }
    }
}
