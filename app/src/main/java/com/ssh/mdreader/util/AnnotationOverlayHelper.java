package com.ssh.mdreader.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.adapter.AnnotationListAdapter;
import com.ssh.mdreader.model.AnnotationEntry;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.ui.span.AnnotationSpan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        /**
         * 批注列表重新读取 CSV 并重定位（{@link AnnotationOverlayHelper#applyAnnotationSpans()}）
         * 完成后回调失效批注 id 集合——供宿主在「编辑保存后」路径提示失效批注。
         * 默认空实现（非编辑保存路径无副作用）；宿主如需提示应覆盖并在消费后复位。
         */
        default void onAnnotationsRelocated(@NonNull Set<String> failedIds) {}
    }

    /**
     * 正文单击命中但未命中批注时的回调（任务清单 checkbox 翻转用，路线图 #5）：
     * 返回 true 表示已消费本次点击；null=未注册（行为与原来一致=无任何动作）。
     */
    public interface TaskTapListener {
        boolean onTaskTap(int charOffset);
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
    /** 任务行点击回调（未命中批注时触发；见 {@link TaskTapListener}）。 */
    private TaskTapListener taskTapListener;

    /** 注册任务行点击回调（checkbox 翻转）；null 可注销。 */
    public void setTaskTapListener(TaskTapListener listener) {
        this.taskTapListener = listener;
    }

    // ── 连续导航状态（上一处/下一处）────────────────────────────────────────
    /** 最近一次导航到的批注起点（字符偏移）；-1 = 尚无。 */
    private int lastVisitedStart = -1;
    /** 当前高亮的批注区间（导航目标）；重新渲染/再次导航时替换。 */
    private BackgroundColorSpan activeHighlight;

    // ── 批注重定位失效状态（编辑保存后未找到原文的条目）─────────────────────
    /** 当前渲染文本下无法定位（失效）的批注 id 集合——每次重定位（applyAnnotationSpans）重算。 */
    private final Set<String> failedIds = new HashSet<>();

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

        // ── Export (抽屉头部「导出」按钮) ────────────────────────────────────
        View btnExport = drawerView.findViewById(R.id.btn_annotation_export);
        if (btnExport != null) {
            btnExport.setOnClickListener(v -> openExportDialog());
        }

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
                            lastVisitedStart = -1;   // 内容重载，访问游标失效
                            applyAnnotationSpans();
                            // 重定位完成：通知宿主失效集合（编辑保存后提示路径；默认空实现）
                            host.onAnnotationsRelocated(getFailedIds());
                        });
                    }
                    @Override
                    public void onError(String ignored) { /* no CSV yet — normal on first open */ }
                });
    }

    /**
     * Overlays {@link AnnotationSpan} objects on the rendered text.
     *
     * <p>For each annotation, {@link AnnotationHelper#buildLocator} builds a
     * single-pass occurrence index over the rendered text once; each entry's
     * occurrence is then resolved in O(1), so total cost is O(M + hits) instead
     * of O(N×M) repeated whole-text scans (专项 B3). The located start matches
     * {@link AnnotationHelper#findNthOccurrence} character-for-character, so the
     * rendered output is byte-identical to the pre-B3 behaviour.</p>
     */
    public void applyAnnotationSpans() {
        // 每次重定位都基于「当前渲染文本」重算失效集合——失效是相对文本的派生状态，
        // 不落盘：文本变化/新增删除批注后重新计算即自然清除或更新。
        failedIds.clear();
        String plain = tvContent.getText().toString();
        AnnotationOccurrenceIndex.Locator locator = AnnotationHelper.buildLocator(annotations, plain);
        failedIds.addAll(AnnotationHelper.nonFindableIds(annotations, locator));
        if (annotations.isEmpty()) return;

        CharSequence current = tvContent.getText();
        SpannableStringBuilder ssb = new SpannableStringBuilder(current);

        for (AnnotationEntry entry : annotations) {
            if (entry.originalText == null || entry.originalText.isEmpty()) continue;

            int spanStart = locator.occurrenceStart(
                    entry.originalText, entry.occurrenceIndex);
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
        List<AnnotationHelper.AnnotationStatus> statuses =
                AnnotationHelper.drawerStatuses(annotations, tvContent.getText().toString());
        annotationListAdapter.setData(annotations, statuses);
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

    /** 当前失效批注 id 集合（只读使用，调用方不得修改）。 */
    @NonNull
    public Set<String> getFailedIds() {
        return failedIds;
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

        if (charOffset < 0) {
            UiUtils.showToast(context, "该批注未找到原文（可能已被编辑修改）");
            return;
        }
        lastVisitedStart = charOffset;   // 抽屉跳转亦属于导航，同步访问游标防重复命中
        scrollToOffset(charOffset);
    }

    /** 平滑滚动到指定字符偏移所在行（居中对齐）。 */
    private void scrollToOffset(int charOffset) {
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

    // ── 连续导航（上一处/下一处）─────────────────────────────────────────────

    /**
     * 连续导航入口（工具栏「上一处/下一处」调用）：
     * {@code direction < 0} 上一处、{@code direction > 0} 下一处。
     *
     * <p>导航序列 = 文档顺序（{@link AnnotationNavigator#buildNavigable}），当前位置 =
     * 视口顶部字符偏移 + 最近访问游标（{@link AnnotationNavigator#nextIndex /
     * previousIndex} 语义）。无批注/无法定位时 toast 提示；到达首尾 <b>不循环</b>，
     * toast 提示边界（2026-10-03 设计决策：阅读场景循环跳转会丢上下文）。</p>
     */
    public void navigateAnnotations(int direction) {
        if (!host.isAlive()) return;
        if (annotations.isEmpty()) {
            UiUtils.showToast(context, "暂无批注");
            return;
        }

        String plain = tvContent.getText().toString();
        List<AnnotationNavigator.NavigableAnnotation> nav =
                AnnotationNavigator.buildNavigable(annotations, plain);
        if (nav.isEmpty()) {
            UiUtils.showToast(context, "暂无可定位的批注");
            return;
        }

        int anchor = lastVisitedStart;
        if (anchor >= 0 && AnnotationNavigator.indexOfStart(nav, anchor) < 0) {
            anchor = -1;   // 访问游标已失效（内容重载/删除/文本变化）→ 回到视口语义
        }
        int viewportTop = viewportTopOffset();
        if (viewportTop < 0) viewportTop = anchor >= 0 ? anchor : 0;

        int idx = (direction < 0)
                ? AnnotationNavigator.previousIndex(nav, viewportTop, anchor)
                : AnnotationNavigator.nextIndex(nav, viewportTop, anchor);
        if (idx < 0) {
            UiUtils.showToast(context,
                    direction < 0 ? "已是第一条批注" : "已是最后一条批注");
            return;
        }

        AnnotationNavigator.NavigableAnnotation target = nav.get(idx);
        lastVisitedStart = target.start;
        jumpToAnnotation(target);
    }

    /** 导航落点：关抽屉（若开）、临时高亮批注区间、平滑滚动到目标。 */
    private void jumpToAnnotation(@NonNull AnnotationNavigator.NavigableAnnotation target) {
        jumpToCharOffset(target.start, target.end);
    }

    /**
     * 通用跳转落点（批注导航与 TOC 大纲导航共用）：关抽屉（若开）、临时高亮区间
     * （与批注下划线同色系 #FFD600 40% 透明背景）、平滑滚动居中到区间起点。
     * {@code start/end} 为渲染文本（TextView 当前内容）字符区间。
     */
    public void jumpToCharOffset(int start, int end) {
        if (!host.isAlive()) return;
        if (drawerLayout.isDrawerOpen(drawerView)) {
            drawerLayout.closeDrawer(drawerView);
        }

        CharSequence current = tvContent.getText();
        SpannableStringBuilder ssb = new SpannableStringBuilder(current);
        if (activeHighlight != null) ssb.removeSpan(activeHighlight);
        activeHighlight = new BackgroundColorSpan(0x66FFD600);
        ssb.setSpan(activeHighlight, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvContent.setText(ssb);
        tvContent.setTextIsSelectable(true);

        scrollToOffset(start);
    }

    /** 视口顶部所在行的行首字符偏移（导航「当前阅读位置」）；布局未就绪时返回 -1。 */
    private int viewportTopOffset() {
        Layout layout = tvContent.getLayout();
        if (layout == null) return -1;
        int y = scrollView.getScrollY() + tvContent.getTotalPaddingTop();
        if (y < 0) y = 0;
        int line = layout.getLineForVertical(y);
        return layout.getLineStart(line);
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
            return;
        }
        // 未命中批注：交给任务行点击回调（checkbox 翻转；返回 false 表示未处理=无动作）
        if (taskTapListener != null) {
            taskTapListener.onTaskTap(charOffset);
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
        lastVisitedStart = -1;   // 删除后位置变化，访问游标失效

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

    // ── Export (导出流程：最新重读 → 格式选择 → 分享/复制) ──────────────────

    /**
     * 抽屉「导出」入口：先异步重读批注 CSV（回调式，保证导出的是最新数据而非
     * 内存缓存），空/读取失败时提示，否则弹格式选择对话框。
     * 结果通过系统分享（ACTION_SEND）或剪贴板交付；不导出为文件、不修改源文档。
     */
    private void openExportDialog() {
        if (!host.isAlive() || annotationFilePath == null) return;
        SshManager.getInstance().readFile(annotationFilePath,
                new SshManager.FileContentCallback() {
                    @Override
                    public void onSuccess(String content) {
                        List<AnnotationEntry> latest = AnnotationHelper.parseAnnotationFile(content);
                        host.runOnUiThread(() -> showExportFormatDialog(latest));
                    }
                    @Override
                    public void onError(String ignored) {
                        // CSV 不存在（首次打开）→ 视为暂无批注
                        host.runOnUiThread(() ->
                                UiUtils.showToast(context, "暂无批注可导出"));
                    }
                });
    }

    private void showExportFormatDialog(@NonNull List<AnnotationEntry> entries) {
        if (!host.isAlive()) return;
        if (entries.isEmpty()) {
            UiUtils.showToast(context, "暂无批注可导出");
            return;
        }
        DialogHelper.showListDialog(context, "导出批注",
                new String[]{"纯文本报告", "HTML 批注块", "Markdown 附录"},
                null,
                (dialog, which) -> onExportFormatSelected(entries, which));
    }

    private void onExportFormatSelected(@NonNull List<AnnotationEntry> entries, int which) {
        if (!host.isAlive()) return;
        AnnotationHelper.ExportFormat format;
        switch (which) {
            case 1:  format = AnnotationHelper.ExportFormat.HTML;     break;
            case 2:  format = AnnotationHelper.ExportFormat.MARKDOWN; break;
            default: format = AnnotationHelper.ExportFormat.TEXT;
        }
        String text = AnnotationHelper.buildExportText(
                entries, sourceNameForExport(), format);
        showExportActionDialog(text, format);
    }

    private void showExportActionDialog(@NonNull String text,
                                        @NonNull AnnotationHelper.ExportFormat format) {
        if (!host.isAlive()) return;
        DialogHelper.showListDialog(context, "导出内容（" + formatLabel(format) + "）",
                new String[]{"分享…", "复制到剪贴板"},
                null,
                (dialog, which) -> {
                    if (!host.isAlive()) return;
                    if (which == 0) shareExport(text);
                    else copyExport(text);
                });
    }

    private void shareExport(@NonNull String text) {
        if (!host.isAlive()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(intent, "分享批注"));
    }

    private void copyExport(@NonNull String text) {
        ClipboardManager cm = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            UiUtils.showToast(context, "复制失败");
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText("批注导出", text));
        UiUtils.showToast(context, "批注已复制到剪贴板");
    }

    /** 导出时展示的来源名：由批注 CSV 路径反推原 Markdown 文件名。 */
    @Nullable
    private String sourceNameForExport() {
        return AnnotationHelper.buildMarkdownFilePath(annotationFilePath);
    }

    private static String formatLabel(@NonNull AnnotationHelper.ExportFormat format) {
        switch (format) {
            case HTML:     return "HTML 批注块";
            case MARKDOWN: return "Markdown 附录";
            default:       return "纯文本报告";
        }
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
