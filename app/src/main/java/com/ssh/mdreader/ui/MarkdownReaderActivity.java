package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ScaleGestureDetector;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.adapter.TocAdapter;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.AnnotationHelper;
import com.ssh.mdreader.util.AnnotationOverlayHelper;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.FindHelper;
import com.ssh.mdreader.util.LinkTargetHelper;
import com.ssh.mdreader.util.OpenFileHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.TaskCheckboxHelper;
import com.ssh.mdreader.util.TocHelper;
import com.ssh.mdreader.util.UiUtils;

import io.noties.markwon.Markwon;
import io.noties.markwon.core.spans.HeadingSpan;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.ext.tasklist.TaskListSpan;
import io.noties.markwon.image.ImagesPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MarkdownReaderActivity extends BaseActivity implements AnnotationOverlayHelper.Host {

    private static final int FONT_SIZE_MIN     = 12;
    private static final int FONT_SIZE_MAX     = 40;
    private static final int FONT_SIZE_DEFAULT = 16;
    private static final int MENU_DRAWER_ID       = 0xA1011;
    private static final int MENU_EDIT_ID         = 0xA1012;
    private static final int MENU_SAVE_ID         = 0xA1013;
    private static final int MENU_ABORT_ID        = 0xA1014;
    private static final int MENU_ANNOTATION_PREV_ID = 0xA1015;
    private static final int MENU_ANNOTATION_NEXT_ID = 0xA1016;
    private static final int MENU_FIND_ID            = 0xA1017;
    private static final String KEY_SCROLL_Y   = "scroll_y";
    private static final String KEY_EDIT_MODE  = "edit_mode";
    private static final String KEY_EDIT_DRAFT = "edit_draft";

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView    tvContent;
    private EditText    etEditor;
    private ProgressBar progressBar;
    private ScrollView  scrollView;

    // ── Drawer ────────────────────────────────────────────────────────────────
    private DrawerLayout drawerLayout;
    private View         drawerView;

    // ── Rendering ─────────────────────────────────────────────────────────────
    private Markwon markwon;
    private String  markdownContent;
    private int     currentFontSize;
    private int     restoredScrollY;
    private PreferenceManager prefManager;
    private ScaleGestureDetector scaleDetector;

    // ── 任务清单 checkbox 交互（路线图 #5）────────────────────────────────────
    /** 当前渲染输入的任务行坐标（与 {@link #renderContent()} 的标记注入同源）。 */
    private List<TaskCheckboxHelper.TaskLine> currentTaskLines = Collections.emptyList();

    // ── 大纲 TOC 导航（路线图 #6）─────────────────────────────────────────────
    /** 当前渲染输入的标题元数据（与 {@link #renderContent()} 的标记注入同源）。 */
    private List<TocHelper.Heading> currentHeadings = Collections.emptyList();
    /** 标题扫描序 → 渲染文本区间 [start, end)；每次渲染后重建，点击跳转用。 */
    private final Map<Integer, int[]> headingRenderRanges = new HashMap<>();
    /** 抽屉当前高亮 Tab（true=大纲；默认批注，与旧行为一致）。 */
    private boolean tocTabActive;
    private TocAdapter tocAdapter;
    private RecyclerView rvTocList;
    private View layoutEmptyToc;
    private TextView tvTabAnnotations;
    private TextView tvTabToc;

    // ── 文档内文本查找（路线图 #8）────────────────────────────────────────────
    /** 查找栏（默认隐藏；仅阅读态可见可用，编辑模式守卫）。 */
    private View findBar;
    private EditText etFindQuery;
    private TextView tvFindStatus;
    /** 当前查询词在渲染文本上的全部匹配（文档序，FindHelper.scanAll 产出）。 */
    private List<FindHelper.Match> findMatches = Collections.emptyList();
    /** 当前高亮匹配下标（-1=无）。 */
    private int findCurrentIndex = -1;

    // ── Editing (编辑模式：远程文件在线编辑，保存经 SshManager.writeFile 覆盖写回) ──
    private String  currentFilePath;
    private String  pageTitle;
    private String  originalContent;
    private boolean editMode;
    private boolean saving;
    private String  pendingEditDraft;

    // ── Annotation feature (批注功能层，含抽屉/span/弹窗/CSV 持久化) ──────────
    private AnnotationOverlayHelper annotationOverlay;
    /** 编辑保存成功后置位：下一次批注重定位完成时提示失效批注（一次性消费）。 */
    private boolean pendingFailureNotice;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown_reader);

        String filePath = getIntent().getStringExtra("file_path");
        String fileName = getIntent().getStringExtra("file_name");
        currentFilePath = filePath;
        pageTitle = (fileName != null ? fileName : getString(R.string.title_reader));
        setupToolbar(pageTitle, true);

        prefManager = new PreferenceManager(this);
        currentFontSize = prefManager.getFontSize(FONT_SIZE_DEFAULT);

        initViews();
        initFindBar();

        String annotationFilePath = (filePath != null)
                ? AnnotationHelper.buildAnnotationFilePath(filePath) : null;
        annotationOverlay = new AnnotationOverlayHelper(this, this,
                tvContent, scrollView, drawerLayout, drawerView,
                findViewById(R.id.rv_annotation_list),
                findViewById(R.id.layout_empty_annotations),
                findViewById(R.id.tv_annotation_count),
                annotationFilePath);
        annotationOverlay.init();
        annotationOverlay.setTaskTapListener(this::toggleTaskAt);
        annotationOverlay.setLinkTapListener(this::onLinkTap);

        initTocUi();

        buildMarkwon();

        if (savedInstanceState != null) {
            restoredScrollY = savedInstanceState.getInt(KEY_SCROLL_Y, 0);
            if (savedInstanceState.getBoolean(KEY_EDIT_MODE, false)) {
                // 旋转/回收前正在编辑：内容重读后恢复编辑草稿（未保存部分）
                pendingEditDraft = savedInstanceState.getString(KEY_EDIT_DRAFT);
            }
        }
        loadContent(filePath);
    }

    // ── Toolbar menu ──────────────────────────────────────────────────────────

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (editMode) {
            menu.add(Menu.NONE, MENU_SAVE_ID, Menu.NONE, "保存")
                    .setIcon(R.drawable.ic_save)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(Menu.NONE, MENU_ABORT_ID, Menu.NONE, "放弃")
                    .setIcon(R.drawable.ic_close)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            return true;
        }
        menu.add(Menu.NONE, MENU_ANNOTATION_PREV_ID, Menu.NONE, "上一处")
                .setIcon(R.drawable.ic_annotation_prev)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_ANNOTATION_NEXT_ID, Menu.NONE, "下一处")
                .setIcon(R.drawable.ic_annotation_next)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_FIND_ID, Menu.NONE, "查找")
                .setIcon(R.drawable.ic_search)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_DRAWER_ID, Menu.NONE, "批注列表")
                .setIcon(R.drawable.ic_annotation_drawer)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_EDIT_ID, Menu.NONE, "编辑")
                .setIcon(R.drawable.ic_edit)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ANNOTATION_PREV_ID:
                annotationOverlay.navigateAnnotations(-1);
                return true;
            case MENU_ANNOTATION_NEXT_ID:
                annotationOverlay.navigateAnnotations(1);
                return true;
            case MENU_FIND_ID:
                showFindBar();
                return true;
            case MENU_DRAWER_ID:
                if (drawerLayout.isDrawerOpen(drawerView)) {
                    drawerLayout.closeDrawer(drawerView);
                } else {
                    applyDrawerTab(tocTabActive);   // 刷新当前 Tab 数据与可见性（默认批注=旧行为）
                    drawerLayout.openDrawer(drawerView);
                }
                return true;
            case MENU_EDIT_ID:
                enterEditMode();
                return true;
            case MENU_SAVE_ID:
                saveEdits();
                return true;
            case MENU_ABORT_ID:
                confirmDiscardEdits();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Editing ───────────────────────────────────────────────────────────────

    private void enterEditMode() {
        if (editMode || markdownContent == null || currentFilePath == null) return;
        enterEditModeWithDraft(markdownContent);
    }

    /** 进入编辑模式；draft 为默认内容（通常=当前文件内容，旋转恢复时=未保存草稿）。 */
    private void enterEditModeWithDraft(String draft) {
        if (editMode) return;
        hideFindBar();          // 查找栏仅阅读态可用
        editMode = true;
        saving = false;
        originalContent = markdownContent;

        if (drawerLayout.isDrawerOpen(drawerView)) {
            drawerLayout.closeDrawer(drawerView);
        }
        etEditor.setText(draft);
        etEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
        scrollView.setVisibility(View.GONE);
        etEditor.setVisibility(View.VISIBLE);
        setTitle(pageTitle + "（编辑中）");
        invalidateOptionsMenu();

        etEditor.post(() -> {
            etEditor.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etEditor, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void saveEdits() {
        if (saving || !editMode || currentFilePath == null) return;
        final String text = etEditor.getText().toString();
        if (text.equals(originalContent)) {
            // 无实际变更：直接退出编辑模式，不发起网络写
            exitEditMode(false);
            UiUtils.showToast(this, "内容未修改");
            return;
        }
        saving = true;
        SshManager.getInstance().writeFile(currentFilePath, text, false,
                new SshManager.WriteFileCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> {
                            saving = false;
                            UiUtils.showToast(MarkdownReaderActivity.this, "已保存");
                            pendingFailureNotice = true;   // 保存成功：重定位后提示失效批注
                            exitEditMode(true);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            saving = false;
                            UiUtils.showToast(MarkdownReaderActivity.this,
                                    "保存失败: " + message);
                        });
                    }
                });
    }

    private void confirmDiscardEdits() {
        if (!editMode) return;
        hideKeyboard();
        DialogHelper.showConfirmDialog(this, "放弃修改",
                "编辑内容尚未保存，放弃后将丢失所有更改。",
                "放弃", "取消",
                (d) -> exitEditMode(false),
                (d) -> {});
    }

    /** 退出编辑模式；reload=true 时从服务器重读并重新渲染（页面回到阅读状态）。 */
    private void exitEditMode(boolean reload) {
        if (!editMode) return;
        editMode = false;
        saving = false;
        hideKeyboard();
        etEditor.setVisibility(View.GONE);
        scrollView.setVisibility(View.VISIBLE);
        setTitle(pageTitle);
        invalidateOptionsMenu();
        if (reload) {
            loadContent(currentFilePath);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && etEditor != null) {
            imm.hideSoftInputFromWindow(etEditor.getWindowToken(), 0);
        }
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private void initViews() {
        tvContent    = findViewById(R.id.tv_content);
        etEditor     = findViewById(R.id.et_editor);
        progressBar  = findViewById(R.id.progress_bar);
        scrollView   = findViewById(R.id.scroll_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerView   = findViewById(R.id.drawer_annotations);

        etEditor.setMovementMethod(new ScrollingMovementMethod());

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

        tvContent.setTextIsSelectable(true);
    }

    // ── 大纲 TOC 导航（路线图 #6）─────────────────────────────────────────────

    /** 初始化大纲 Tab、列表与点击回调（drawer 布局内含「批注/大纲」双 Tab）。 */
    private void initTocUi() {
        rvTocList = findViewById(R.id.rv_toc_list);
        layoutEmptyToc = findViewById(R.id.layout_empty_toc);
        tvTabAnnotations = findViewById(R.id.tv_tab_annotations);
        tvTabToc = findViewById(R.id.tv_tab_toc);
        tocAdapter = new TocAdapter();
        rvTocList.setLayoutManager(new LinearLayoutManager(this));
        rvTocList.setAdapter(tocAdapter);
        tocAdapter.setOnItemClickListener(this::onTocItemClick);
        tvTabAnnotations.setOnClickListener(v -> applyDrawerTab(false));
        tvTabToc.setOnClickListener(v -> applyDrawerTab(true));
    }

    /**
     * 切换/刷新抽屉 Tab（tocTab=true=大纲）：更新 Tab 高亮、刷新对应列表数据与空态，
     * 隐藏另一侧视图。打开抽屉与 Tab 点击共用。
     */
    private void applyDrawerTab(boolean tocTab) {
        tocTabActive = tocTab;
        tvTabAnnotations.setTextColor(tocTab ? 0xFF888888 : 0xFFBB86FC);
        tvTabAnnotations.setTypeface(null, tocTab ? Typeface.NORMAL : Typeface.BOLD);
        tvTabToc.setTextColor(tocTab ? 0xFFBB86FC : 0xFF888888);
        tvTabToc.setTypeface(null, tocTab ? Typeface.BOLD : Typeface.NORMAL);
        if (tocTab) {
            refreshTocDrawer();
            rvTocList.setVisibility(currentHeadings.isEmpty() ? View.GONE : View.VISIBLE);
            layoutEmptyToc.setVisibility(currentHeadings.isEmpty() ? View.VISIBLE : View.GONE);
            findViewById(R.id.rv_annotation_list).setVisibility(View.GONE);
            findViewById(R.id.layout_empty_annotations).setVisibility(View.GONE);
        } else {
            annotationOverlay.refreshAnnotationDrawer();   // 数据+批注空态+计数
            rvTocList.setVisibility(View.GONE);
            layoutEmptyToc.setVisibility(View.GONE);
        }
    }

    /** 刷新大纲列表（数据=最近一次渲染的标题元数据构建的树）。 */
    private void refreshTocDrawer() {
        if (tocAdapter == null) return;
        tocAdapter.setData(TocHelper.buildTree(currentHeadings));
    }

    /**
     * 大纲条目点击：按标题扫描序定位渲染区间（HeadingSpan+U+200C 标记解码）→
     * 复用批注导航落点（关抽屉+高亮+平滑滚动居中）。编辑模式下无可跳转目标（toast）。
     */
    private void onTocItemClick(int position) {
        if (editMode) {
            UiUtils.showToast(this, "编辑模式下大纲不可用");
            return;
        }
        int[] range = headingRenderRanges.get(position);
        if (range == null) {
            UiUtils.showToast(this, "无法定位该标题");
            return;
        }
        annotationOverlay.jumpToCharOffset(range[0], range[1]);
    }

    /**
     * 渲染后重建「标题扫描序 → 渲染文本区间」映射：遍历 HeadingSpan（Markwon core
     * 默认渲染 ATX/setext 标题，span 覆盖标题文本），在 span 区间内解码首个连续
     * U+200C 段（=注入标记）取扫描序号。失败（无标记/非法）跳过——不阻塞渲染。
     */
    private void rebuildHeadingRenderRanges() {
        headingRenderRanges.clear();
        CharSequence cs = tvContent.getText();
        if (!(cs instanceof Spanned)) return;
        Spanned spanned = (Spanned) cs;
        HeadingSpan[] spans = spanned.getSpans(0, spanned.length(), HeadingSpan.class);
        for (HeadingSpan span : spans) {
            int start = spanned.getSpanStart(span);
            int end = spanned.getSpanEnd(span);
            int idx = TocHelper.decodeHeadingIndex(spanned, start, end);
            if (idx >= 0) {
                headingRenderRanges.put(idx, new int[]{start, end});
            }
        }
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

    private void loadContent(String filePath) {
        if (filePath == null) { finish(); return; }

        progressBar.setVisibility(View.VISIBLE);
        SshManager.getInstance().readFile(filePath, new SshManager.FileContentCallback() {
            @Override
            public void onSuccess(String content) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    markdownContent = content;
                    render();           // render first, then load spans
                    annotationOverlay.loadAnnotations();
                    restoreScrollPosition();
                    if (pendingEditDraft != null) {
                        // 旋转恢复路径：内容已重读，带草稿进入编辑模式
                        String draft = pendingEditDraft;
                        pendingEditDraft = null;
                        enterEditModeWithDraft(draft);
                    }
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

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * 渲染正文：字号 + markwon 重设 + span 重覆盖 + 滚动保持（Host.render 的实现，
     * 供批注层在删除最后一条后全量重渲染）。
     */
    private void renderContent() {
        if (markdownContent == null) return;

        final int savedScrollY = scrollView.getScrollY();

        // 标题/任务清单行定位：渲染前注入零宽标记（与 currentHeadings/currentTaskLines
        // 同源），点击命中使用。合并注入=标题 U+200C 标记 + 任务 U+200B 标记（坐标互不干扰）
        TocHelper.MarkedSource prepared = TocHelper.injectMarkers(markdownContent);
        currentTaskLines = prepared.taskLines;
        currentHeadings = prepared.headings;

        tvContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
        markwon.setMarkdown(tvContent, prepared.text);
        tvContent.setTextIsSelectable(true);

        annotationOverlay.applyAnnotationSpans();
        rebuildHeadingRenderRanges();
        rebuildFind(true);          // 渲染文本已变：查找结果若打开则重算（保持当前序号）

        if (savedScrollY > 0) {
            scrollView.post(() -> scrollView.scrollTo(0, savedScrollY));
        }
    }

    // ── AnnotationOverlayHelper.Host ──────────────────────────────────────────

    @Override
    public boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    public String markdownContent() {
        return markdownContent;
    }

    @Override
    public void render() {
        renderContent();
    }

    @Override
    public void refreshMarkdownText() {
        TocHelper.MarkedSource prepared = TocHelper.injectMarkers(markdownContent);
        currentTaskLines = prepared.taskLines;
        currentHeadings = prepared.headings;
        markwon.setMarkdown(tvContent, prepared.text);
        tvContent.setTextIsSelectable(true);
        rebuildHeadingRenderRanges();
        rebuildFind(true);
    }

    @Override
    public void onAnnotationsRelocated(@NonNull Set<String> failedIds) {
        // 仅「编辑保存后」路径消费一次；其余重定位（初次加载/增删批注）不提示
        if (!pendingFailureNotice) return;
        pendingFailureNotice = false;
        int n = failedIds.size();
        if (n > 0) {
            UiUtils.showToast(this, n + " 条批注因编辑未找到原文，已从渲染中移除");
        }
    }

    // ── 任务清单 checkbox 交互（路线图 #5）────────────────────────────────────

    /**
     * 阅读态单击命中回调（批注未命中时由 AnnotationOverlayHelper 转达）：命中任务项
     * （TaskListSpan 覆盖且带定位标记）→ 翻转状态并写回。返回 true=已消费本次点击。
     *
     * <p>行定位与 Markwon 渲染错位解耦：渲染前已按源任务行注入零宽标记（同序），
     * 点击命中任务区间起点 → 解码标记序号 → 映射回源任务行 → 只翻转状态字符。
     * 失败防御：无标记/越界/无任务行 → 不消费（点击落入原生行为）。</p>
     */
    private boolean toggleTaskAt(int charOffset) {
        if (editMode || markdownContent == null || currentFilePath == null) return false;
        CharSequence current = tvContent.getText();
        if (!(current instanceof Spanned)) return false;
        Spanned spanned = (Spanned) current;
        TaskListSpan[] spans = spanned.getSpans(charOffset, charOffset, TaskListSpan.class);
        if (spans.length == 0) return false;
        int spanStart = spanned.getSpanStart(spans[0]);
        int index = TaskCheckboxHelper.decodeMarkerIndex(spanned, spanStart);
        if (index < 0 || index >= currentTaskLines.size()) return false;
        TaskCheckboxHelper.TaskLine line = currentTaskLines.get(index);
        String updated = TaskCheckboxHelper.flipTaskState(markdownContent, line, !line.checked);
        if (updated.equals(markdownContent)) return false;
        // 乐观更新：先渲染（含标记注入，滚动保持由 renderContent 承担），再异步写回
        markdownContent = updated;
        renderContent();
        writeTaskToggle(updated);
        return true;
    }

    /**
     * 写回=既有 writeFile(OVERWRITE) 同口径（写操作不重试）；成功静默（勾选状态已反馈），
     * 失败 toast + 以服务器实际内容为准重读回滚（保证 UI 与盘面一致）。
     */
    private void writeTaskToggle(String content) {
        SshManager.getInstance().writeFile(currentFilePath, content, false,
                new SshManager.WriteFileCallback() {
                    @Override
                    public void onSuccess() {
                        // 乐观更新已展示勾选状态，无需额外动作
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            UiUtils.showToast(MarkdownReaderActivity.this,
                                    "保存失败: " + message);
                            loadContent(currentFilePath);
                        });
                    }
                });
    }

    // ── Markdown 链接点击（路线图第二轮 #1）─────────────────────────────────

    /**
     * 阅读态单击命中链接回调（批注优先、链接次之、任务行最后，顺序见
     * {@link AnnotationOverlayHelper.TaskTapListener} 注释）：按 {@link LinkTargetHelper}
     * 分类处理三类目标。返回 true=已消费点击。仅阅读态（编辑态有光标/选区，不消费）。
     *
     * <p>EXTERNAL_URL=系统浏览器/邮件客户端 ACTION_VIEW（无处理应用时 toast 而非崩溃）；
     * REMOTE_FILE=先 fileExists 确认存在再打开对应查看器（复用 {@link OpenFileHelper}
     * 单点分发），不存在 toast；PAGE_ANCHOR=按标题文本精确/slug 匹配跳转（锚点跳转复用
     * 批注导航落点 jumpToCharOffset，与大纲导航同机制），无匹配 toast 不跳。</p>
     */
    private boolean onLinkTap(int charOffset) {
        if (editMode || markdownContent == null || currentFilePath == null) return false;
        CharSequence current = tvContent.getText();
        if (!(current instanceof Spanned)) return false;
        Spanned spanned = (Spanned) current;
        URLSpan[] spans = spanned.getSpans(charOffset, charOffset, URLSpan.class);
        if (spans.length == 0) return false;
        String link = spans[0].getURL();
        if (link == null || link.isEmpty()) return false;

        LinkTargetHelper.Resolved target = LinkTargetHelper.classify(link, currentFilePath);
        switch (target.kind) {
            case EXTERNAL_URL:
                openExternalUrl(target.target);
                return true;
            case REMOTE_FILE:
                openRemoteLinkedFile(target.target);
                return true;
            case PAGE_ANCHOR:
                jumpToAnchor(target.target);
                return true;
            default:
                return false;
        }
    }

    /** 外部链接 → 系统浏览器/邮件客户端；无处理应用 toast（与 Markwon 默认 resolver 的
     *  ActivityNotFoundException 防御同语义）。 */
    private void openExternalUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (android.content.ActivityNotFoundException e) {
            UiUtils.showToast(this, "未找到可打开该链接的应用");
        }
    }

    /** 远端相对/绝对链接 → 先确认存在（异步，stat 口径与 fileExists 一致），再按类型打开。 */
    private void openRemoteLinkedFile(String remotePath) {
        SshManager.getInstance().fileExists(remotePath, new SshManager.ExistsCallback() {
            @Override
            public void onResult(boolean exists) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (!exists) {
                        UiUtils.showToast(MarkdownReaderActivity.this, "链接目标不存在");
                        return;
                    }
                    Intent intent = OpenFileHelper.buildViewerIntent(
                            MarkdownReaderActivity.this, remotePath);
                    if (intent == null) {
                        UiUtils.showToast(MarkdownReaderActivity.this, "暂不支持此文件类型");
                        return;
                    }
                    startActivity(intent);
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    UiUtils.showToast(MarkdownReaderActivity.this,
                            "无法打开链接目标: " + message);
                });
            }
        });
    }

    /** 页内锚点 → 按「标题文本精确（忽略大小写）/slug」匹配当前文档标题，命中跳转并高亮；未命中 toast。 */
    private void jumpToAnchor(String anchor) {
        int idx = LinkTargetHelper.findAnchorHeading(currentHeadings, anchor);
        if (idx < 0) {
            UiUtils.showToast(this, "未找到锚点对应的标题");
            return;
        }
        int[] range = headingRenderRanges.get(idx);
        if (range == null) {
            UiUtils.showToast(this, "无法定位该标题");
            return;
        }
        annotationOverlay.jumpToCharOffset(range[0], range[1]);
    }

    // ── 文档内文本查找（路线图 #8）─────────────────────────────────────────────

    /** 初始化查找栏：输入监听（变化即重算并跳第一处）、上一处/下一处/关闭按钮、键盘搜索键。 */
    private void initFindBar() {
        findBar = findViewById(R.id.find_bar);
        etFindQuery = findViewById(R.id.et_find_query);
        tvFindStatus = findViewById(R.id.tv_find_status);
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
        findViewById(R.id.btn_find_prev).setOnClickListener(v -> findStep(-1));
        findViewById(R.id.btn_find_next).setOnClickListener(v -> findStep(1));
        findViewById(R.id.btn_find_close).setOnClickListener(v -> hideFindBar());
        etFindQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                findStep(1);   // 键盘搜索键 = 下一处（循环）
                return true;
            }
            return false;
        });
    }

    /** 打开查找栏（仅阅读态；聚焦输入并弹键盘；不预填/保留上次查询词）。 */
    private void showFindBar() {
        if (editMode) return;
        findBar.setVisibility(View.VISIBLE);
        etFindQuery.requestFocus();
        etFindQuery.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etFindQuery, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        rebuildFind(false);
    }

    /** 关闭查找栏：清除查找态与导航高亮（批注/大纲导航高亮各自管理，不受影响）。 */
    private void hideFindBar() {
        findBar.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etFindQuery.getWindowToken(), 0);
        }
        findMatches = Collections.emptyList();
        findCurrentIndex = -1;
        tvFindStatus.setText("");
        annotationOverlay.clearActiveHighlight();
    }

    /**
     * 重建当前查询词的全部匹配（在<b>渲染后文本</b>上扫描，见 {@link FindHelper}）。
     * {@code preserve=true}：保持当前序号（不跳转，内容重渲染后调用——避免打断阅读位置）；
     * {@code preserve=false}：跳到第一处（输入变化/打开查找栏时）。查找栏隐藏时为空操作。
     */
    private void rebuildFind(boolean preserve) {
        if (findBar.getVisibility() != View.VISIBLE) return;
        String query = etFindQuery.getText().toString();
        int prev = findCurrentIndex;
        findMatches = FindHelper.scanAll(tvContent.getText(), query, true);
        if (findMatches.isEmpty()) {
            findCurrentIndex = -1;
            tvFindStatus.setText(query.isEmpty() ? "" : "未找到");
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

    /** 上一处/下一处（循环）；结果为空时先防御性重算（内容可能已变），仍无则提示。 */
    private void findStep(int direction) {
        if (editMode) return;
        if (findMatches.isEmpty()) rebuildFind(false);
        if (findMatches.isEmpty()) {
            tvFindStatus.setText("未找到");
            return;
        }
        findCurrentIndex = FindHelper.advance(findMatches, findCurrentIndex, direction);
        jumpToFindMatch();
    }

    /** 跳转到当前匹配：更新计数状态并复用批注导航落点（关抽屉+高亮+平滑滚动居中）。 */
    private void jumpToFindMatch() {
        if (findCurrentIndex < 0 || findCurrentIndex >= findMatches.size()) return;
        FindHelper.Match m = findMatches.get(findCurrentIndex);
        tvFindStatus.setText((findCurrentIndex + 1) + "/" + findMatches.size());
        annotationOverlay.jumpToCharOffset(m.start, m.end);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (scrollView != null) {
            outState.putInt(KEY_SCROLL_Y, scrollView.getScrollY());
        }
        if (editMode && etEditor != null) {
            outState.putBoolean(KEY_EDIT_MODE, true);
            outState.putString(KEY_EDIT_DRAFT, etEditor.getText().toString());
        }
    }

    /**
     * Applies the scroll position saved in {@link #onSaveInstanceState}, once,
     * after the async content load has rendered.  The framework's own
     * ScrollView state restore runs before the content exists, so it is
     * ineffective here; this explicit restore is what keeps the reading
     * position across rotation / background recreation.
     */
    private void restoreScrollPosition() {
        if (restoredScrollY <= 0) return;
        int y = restoredScrollY;
        restoredScrollY = 0;   // consume — apply exactly once
        scrollView.post(() -> scrollView.scrollTo(0, y));
    }

    @Override
    public void onBackPressed() {
        if (editMode) {
            confirmDiscardEdits();
        } else if (drawerLayout != null && drawerLayout.isDrawerOpen(drawerView)) {
            drawerLayout.closeDrawer(drawerView);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        annotationOverlay.dismissActivePopup();
    }
}
