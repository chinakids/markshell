package com.ssh.mdreader.util;

import android.content.Context;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.adapter.AnnotationListAdapter;
import com.ssh.mdreader.model.AnnotationEntry;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.ui.span.AnnotationSpan;

import java.util.ArrayList;
import java.util.List;

/**
 * MarkdownReaderActivity 的批注功能层：CSV 持久化、内联 span 覆盖、抽屉列表、
 * 点击命中检测、PopupWindow、添加/删除对话框与触摸/选区菜单都在这里。
 * 渲染（Markwon/字号/滚动）仍由 Activity 持有，helper 仅通过 {@link Host} 最小回调面
 * 请求重渲染，使 Activity 只保留阅读编排。
 *
 * <p>行为契约（重构维护，2026-10-02）：文案、回调顺序与拆分前逐字一致；
 * 所有文案改动必须视为行为变更。</p>
 */
public class AnnotationOverlayHelper {

    /** 宿主（MarkdownReaderActivity）向 helper 暴露的最小回调面。 */
    public interface Host {
        /** Activity 仍存活（!isFinishing() && !isDestroyed()）。 */
        boolean isAlive();

        /** 等价原 Activity.runOnUiThread（保持同线程即时执行语义）。 */
        void runOnUiThread(Runnable r);

        /** 已加载的 Markdown 源文本（未加载时为 null）。 */
        String markdownContent();

        /** Activity.render()：字号 + markwon 重设 + span 重覆盖 + 滚动保持。 */
        void render();

        /** 等价 deleteAnnotation 非空分支的 markwon.setMarkdown + setTextIsSelectable（不动字号/滚动）。 */
        void refreshMarkdownText();
    }

    /** 与拆分前一致的 logcat tag（原 MarkdownReaderActivity）。 */
    private static final String TAG = "MarkdownReaderActivity";
    private static final int MENU_ANNOTATE_ID = 0xA1010;

    private final Context context;
    private final Host host;
    private final TextView tvContent;
    private final ScrollView scrollView;
    private final DrawerLayout drawerLayout;
    private final View drawerView;
    private final RecyclerView rvAnnotationList;
    private final LinearLayout layoutEmptyAnnotations;
    private final TextView tvAnnotationCount;
    private final String annotationFilePath;
    private final List<AnnotationEntry> annotations = new ArrayList<>();

    private AnnotationListAdapter annotationListAdapter;
    private PopupWindow activePopup;
    private float lastTouchX, lastTouchY;
    private long lastDownTime;
    private float lastDownX, lastDownY;

    public AnnotationOverlayHelper(Context context, Host host,
                                   TextView tvContent, ScrollView scrollView,
                                   DrawerLayout drawerLayout, View drawerView,
                                   RecyclerView rvAnnotationList,
                                   LinearLayout layoutEmptyAnnotations,
                                   TextView tvAnnotationCount,
                                   String annotationFilePath) {
        this.context = context;
        this.host = host;
        this.tvContent = tvContent;
        this.scrollView = scrollView;
        this.drawerLayout = drawerLayout;
        this.drawerView = drawerView;
        this.rvAnnotationList = rvAnnotationList;
        this.layoutEmptyAnnotations = layoutEmptyAnnotations;
        this.tvAnnotationCount = tvAnnotationCount;
        this.annotationFilePath = annotationFilePath;
    }

    /**
     * 装配批注 UI（原 initDrawer + initViews 中的批注触摸/选区菜单部分）：
     * 抽屉宽度/锁、列表适配器与条目回调、正文点击命中检测、文本选择菜单「批注」。
     */
    public void init() {
        // ── Drawer ──────────────────────────────────────────────────────────
        int drawerWidth = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.75f);
        ViewGroup.LayoutParams lp = drawerView.getLayoutParams();
        lp.width = drawerWidth;
        drawerView.setLayoutParams(lp);

        annotationListAdapter = new AnnotationListAdapter();
        rvAnnotationList.setLayoutManager(new LinearLayoutManager(context));
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

        // ── Tap detection on content ────────────────────────────────────────
        tvContent.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                    lastDownTime = event.getEventTime();
                    lastDownX = event.getRawX();
                    lastDownY = event.getRawY();
                    if (activePopup != null) { activePopup.dismiss(); activePopup = null; }
                    break;
                case MotionEvent.ACTION_UP: {
                    long dt = event.getEventTime() - lastDownTime;
                    float dx = Math.abs(event.getRawX() - lastDownX);
                    float dy = Math.abs(event.getRawY() - lastDownY);
                    float slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                    // Treat as click only if: short press, no significant movement,
                    // and no text is currently selected (so we don't steal selection taps)
                    if (dt < ViewConfiguration.getLongPressTimeout()
                            && dx < slop && dy < slop
                            && tvContent.getSelectionStart() == tvContent.getSelectionEnd()) {
                        handleAnnotationClick(event);
                        return true;
                    }
                    break;
                }
            }
            return false;
        });

        // ── Selection action menu「批注」──────────────────────────────
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

    /** 读取批注 CSV（异步），解析后覆盖 span。 */
    public void loadAnnotations() {
        if (annotationFilePath == null) return;
        SshManager.getInstance().readFile(annotationFilePath,
                new SshManager.FileContentCallback() {
                    @Override
                    public void onSuccess(String content) {
                        host.runOnUiThread(() -> {
                            annotations.clear();
                            annotations.addAll(AnnotationHelper.parseAnnotationFile(content));
                            applyAnnotationSpans();
                        });
                    }
                    @Override
                    public void onError(String ignored) { /* no CSV yet — normal on first open */ }
                });
    }

    /**
     * Overlays {@link AnnotationSpan} objects on the rendered text.
     *
     * <p>For each annotation, {@link AnnotationHelper#findNthOccurrence} locates
     * the exact occurrence recorded by {@link AnnotationEntry#occurrenceIndex},
     * so duplicate text is handled correctly without any source-file modification.</p>
     */
    public void applyAnnotationSpans() {
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
                    spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new AnnotationSpan.DashedUnderlineSpan(),
                    spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        tvContent.setText(ssb);
        tvContent.setTextIsSelectable(true);
    }

    /** 刷新抽屉列表与空态（菜单打开前调用）。 */
    public void refreshAnnotationDrawer() {
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

    /** 关闭并清空当前弹窗（onPause 时调用）。 */
    public void dismissActivePopup() {
        if (activePopup != null) { activePopup.dismiss(); activePopup = null; }
    }

    /**
     * Scrolls to the annotated text by finding the AnnotationSpan with the
     * matching ID directly from the Spanned TextView output.
     */
    private void scrollToAnnotation(AnnotationEntry entry) {
        Spanned spanned = (Spanned) tvContent.getText();
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
            int paddingTop = (int) (16 * context.getResources().getDisplayMetrics().density);
            int scrollY    = Math.max(0, lineTop + paddingTop - scrollView.getHeight() / 2);
            scrollView.smoothScrollTo(0, scrollY);
        });
    }

    /**
     * Hit-tests the touch position against any AnnotationSpan in the rendered
     * text.  If a span is found, fires its click callback.
     */
    private void handleAnnotationClick(MotionEvent event) {
        Layout layout = tvContent.getLayout();
        if (layout == null) return;

        // Convert screen coordinates to local TextView coordinates
        float x = event.getX();
        float y = event.getY();

        // Account for text padding
        x -= tvContent.getTotalPaddingLeft();
        y -= tvContent.getTotalPaddingTop();

        // Convert to relative scroll offset
        x += tvContent.getScrollX();
        y += tvContent.getScrollY();

        int line = layout.getLineForVertical((int) y);
        int charOffset = layout.getOffsetForHorizontal(line, x);
        if (charOffset < 0 || charOffset >= tvContent.getText().length()) return;

        Spanned spanned = (Spanned) tvContent.getText();
        AnnotationSpan[] spans = spanned.getSpans(charOffset, charOffset, AnnotationSpan.class);
        if (spans.length > 0) {
            spans[0].onClick(tvContent);
        }
    }

    private void onAnnotationClicked(AnnotationEntry entry, View anchor) {
        if (!host.isAlive()) return;
        dismissActivePopup();

        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_annotation, null);
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

        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        PopupPosition pos = computePopupPosition(
                popupView.getMeasuredWidth(), popupView.getMeasuredHeight(),
                dm.widthPixels, dm.heightPixels,
                (int) (16 * dm.density), (int) (12 * dm.density), (int) lastTouchY);

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, pos.x, pos.y);
        activePopup = popup;
    }

    private void confirmDeleteAnnotation(AnnotationEntry entry) {
        if (!host.isAlive()) return;

        // Show what is being deleted (annotated text + source snippet), so the
        // confirmation is not a blind "are you sure".
        String text = entry.text != null ? entry.text.trim() : "";
        if (text.isEmpty()) text = "（空批注）";
        if (text.length() > 40) text = text.substring(0, 40) + "…";

        String orig = entry.originalText != null ? entry.originalText.trim() : "";
        if (orig.length() > 40) orig = orig.substring(0, 40) + "…";

        String message = "批注：「" + text + "」"
                + (orig.isEmpty() ? "" : "\n原文：「" + orig + "」")
                + "\n\n确定要删除这条批注吗？";

        DialogHelper.showDangerConfirmDialog(context,
                "删除批注", message,
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
                            host.runOnUiThread(() -> {
                                host.render();   // re-render to clear all spans
                                UiUtils.showToast(context, "批注已删除");
                            });
                        }
                        @Override
                        public void onError(String ignored) {
                            host.runOnUiThread(() -> {
                                host.render();
                                UiUtils.showToast(context, "批注已删除");
                            });
                        }
                    });
        } else {
            String csv = AnnotationHelper.formatAnnotationFile(annotations);
            SshManager.getInstance().writeFile(annotationFilePath, csv, false,
                    new SshManager.WriteFileCallback() {
                        @Override
                        public void onSuccess() {
                            host.runOnUiThread(() -> {
                                // Re-apply spans only — no need to re-render Markdown
                                host.refreshMarkdownText();
                                applyAnnotationSpans();
                                UiUtils.showToast(context, "批注已删除");
                            });
                        }
                        @Override
                        public void onError(String message) {
                            // Revert in-memory removal on failure
                            annotations.add(entry);
                            host.runOnUiThread(() ->
                                    UiUtils.showSnackbar(tvContent, "CSV更新失败: " + message));
                        }
                    });
        }
    }

    private void showAnnotationInputDialog(int tvSelStart, int tvSelEnd) {
        if (!host.isAlive()) return;
        if (host.markdownContent() == null) return;

        String selectedText = "";
        try {
            CharSequence rendered = tvContent.getText();
            int safeEnd = Math.min(tvSelEnd, rendered.length());
            if (tvSelStart >= 0 && tvSelStart < safeEnd) {
                selectedText = rendered.subSequence(tvSelStart, safeEnd).toString();
            }
        } catch (Exception e) {
            Log.d(TAG, "读取选中文本失败", e);
        }

        final String finalSelected = selectedText.trim();
        final int    finalSelStart = tvSelStart;

        DialogHelper.showInputDialog(context, "添加批注", "请输入批注内容…", "确认", "取消",
                input -> {
                    if (input.isEmpty()) return;
                    if (finalSelected.isEmpty()) {
                        UiUtils.showToast(context, "未能读取选中文本");
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
                        host.runOnUiThread(() -> {
                            annotations.add(entry);
                            applyAnnotationSpans(); // overlay span without re-rendering
                            UiUtils.showToast(context, "批注已保存");
                        });
                    }
                    @Override
                    public void onError(String message) {
                        host.runOnUiThread(() ->
                                UiUtils.showSnackbar(tvContent, "批注保存失败: " + message));
                    }
                });
    }

    // ── 纯函数（供单测） ───────────────────────────────────────────────────

    /**
     * 弹窗显示位置：水平居中；默认显示在触摸点上方（offset），越界时翻到下方，
     * 仍越界则钳到屏幕内（与原 Activity 算法逐字一致）。
     */
    static PopupPosition computePopupPosition(int popupW, int popupH,
                                              int screenW, int screenH,
                                              int marginPx, int offsetPx, int touchY) {
        int x = (screenW - popupW) / 2;
        int y = touchY - popupH - offsetPx;
        if (y < marginPx) y = touchY + offsetPx;
        if (y + popupH > screenH - marginPx) y = screenH - popupH - marginPx;
        return new PopupPosition(x, y);
    }

    static final class PopupPosition {
        final int x;
        final int y;
        PopupPosition(int x, int y) { this.x = x; this.y = y; }
    }
}
