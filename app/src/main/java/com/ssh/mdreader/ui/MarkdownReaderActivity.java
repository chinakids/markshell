package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ScaleGestureDetector;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.AnnotationHelper;
import com.ssh.mdreader.util.AnnotationOverlayHelper;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.UiUtils;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.image.ImagesPlugin;

public class MarkdownReaderActivity extends BaseActivity implements AnnotationOverlayHelper.Host {

    private static final int FONT_SIZE_MIN     = 12;
    private static final int FONT_SIZE_MAX     = 40;
    private static final int FONT_SIZE_DEFAULT = 16;
    private static final int MENU_DRAWER_ID    = 0xA1011;
    private static final int MENU_EDIT_ID      = 0xA1012;
    private static final int MENU_SAVE_ID      = 0xA1013;
    private static final int MENU_ABORT_ID     = 0xA1014;
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

    // ── Editing (编辑模式：远程文件在线编辑，保存经 SshManager.writeFile 覆盖写回) ──
    private String  currentFilePath;
    private String  pageTitle;
    private String  originalContent;
    private boolean editMode;
    private boolean saving;
    private String  pendingEditDraft;

    // ── Annotation feature (批注功能层，含抽屉/span/弹窗/CSV 持久化) ──────────
    private AnnotationOverlayHelper annotationOverlay;

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

        String annotationFilePath = (filePath != null)
                ? AnnotationHelper.buildAnnotationFilePath(filePath) : null;
        annotationOverlay = new AnnotationOverlayHelper(this, this,
                tvContent, scrollView, drawerLayout, drawerView,
                findViewById(R.id.rv_annotation_list),
                findViewById(R.id.layout_empty_annotations),
                findViewById(R.id.tv_annotation_count),
                annotationFilePath);
        annotationOverlay.init();

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
            case MENU_DRAWER_ID:
                if (drawerLayout.isDrawerOpen(drawerView)) {
                    drawerLayout.closeDrawer(drawerView);
                } else {
                    annotationOverlay.refreshAnnotationDrawer();
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

        tvContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentFontSize);
        markwon.setMarkdown(tvContent, markdownContent);
        tvContent.setTextIsSelectable(true);

        annotationOverlay.applyAnnotationSpans();

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
        markwon.setMarkdown(tvContent, markdownContent);
        tvContent.setTextIsSelectable(true);
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
