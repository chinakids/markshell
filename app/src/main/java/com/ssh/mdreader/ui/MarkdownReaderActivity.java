package com.ssh.mdreader.ui;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ScaleGestureDetector;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.AnnotationHelper;
import com.ssh.mdreader.util.AnnotationOverlayHelper;
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
    private static final String KEY_SCROLL_Y   = "scroll_y";

    // ── Views ─────────────────────────────────────────────────────────────────
    private TextView    tvContent;
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

    // ── Annotation feature (批注功能层，含抽屉/span/弹窗/CSV 持久化) ──────────
    private AnnotationOverlayHelper annotationOverlay;

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_markdown_reader);

        String filePath = getIntent().getStringExtra("file_path");
        String fileName = getIntent().getStringExtra("file_name");
        setupToolbar(fileName != null ? fileName : getString(R.string.title_reader), true);

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
        }
        loadContent(filePath);
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
                annotationOverlay.refreshAnnotationDrawer();
                drawerLayout.openDrawer(drawerView);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    private void initViews() {
        tvContent    = findViewById(R.id.tv_content);
        progressBar  = findViewById(R.id.progress_bar);
        scrollView   = findViewById(R.id.scroll_view);
        drawerLayout = findViewById(R.id.drawer_layout);
        drawerView   = findViewById(R.id.drawer_annotations);

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
        if (drawerLayout != null && drawerLayout.isDrawerOpen(drawerView)) {
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
