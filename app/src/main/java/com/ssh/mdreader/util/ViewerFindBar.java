package com.ssh.mdreader.util;

import android.app.Activity;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.ssh.mdreader.R;

import java.util.Collections;
import java.util.List;

/**
 * 查看器查找栏控制器（TextViewerActivity / CodeViewerActivity 共用，路线图 #21）。
 *
 * <p><b>背景</b>：Markdown 阅读器与编辑模式已有文档内查找（{@link FindHelper}+
 * MarkdownReaderActivity 查找栏），三个查看器全无。本类把「TextView 宿主 + 纵向
 * ScrollView（可选横向 ScrollView）+ 查找栏视图」的查找逻辑收口为一份实现——
 * 两处 Activity 复用同一控制器，避免「两处实现=两套 bug 面」。</p>
 *
 * <p><b>机制</b>（与 MarkdownReaderActivity 阅读态查找同机制）：扫描源=宿主 TextView
 * 当前文本（所见即所搜，Code 查看器=高亮后的文本，字符序列与原始代码一致）；匹配
 * {@link FindHelper#scanAll} 全量字面扫描+循环推进；跳转=拷贝为
 * {@link SpannableStringBuilder} 加临时 {@link BackgroundColorSpan}（0x66FFD600 黄色半透明，
 * 与批注导航高亮同色系）+ 复用 {@link UiUtils#scrollToOffsetCenter} 平滑滚动居中
 * （与 AnnotationOverlayHelper 同一算法，消除滚动实现复制）；横向宿主存在时按命中列
 * 横向平滑滚动（代码查看器长行）。</p>
 *
 * <p>查找栏打开状态下宿主内容异步更新（如 Code 高亮完成 setText）须调用
 * {@link #onContentChanged()} 重扫并保持序号；关闭/清空输入清除高亮。
 * 本类不持久化状态（旋转重建后查找栏关闭，与阅读器查找行为一致）。</p>
 */
public final class ViewerFindBar {

    private static final int HIGHLIGHT_COLOR = 0x66FFD600;

    private final Activity activity;
    private final TextView textView;
    private final ScrollView scrollView;
    @Nullable
    private final HorizontalScrollView horizontalScrollView;
    private final View findBar;
    private final EditText etQuery;
    private final TextView tvStatus;

    private List<FindHelper.Match> matches = Collections.emptyList();
    private int currentIndex = -1;
    private BackgroundColorSpan activeHighlight;

    public ViewerFindBar(Activity activity, TextView textView, ScrollView scrollView,
                         @Nullable HorizontalScrollView horizontalScrollView,
                         View findBar, EditText etQuery, TextView tvStatus) {
        this.activity = activity;
        this.textView = textView;
        this.scrollView = scrollView;
        this.horizontalScrollView = horizontalScrollView;
        this.findBar = findBar;
        this.etQuery = etQuery;
        this.tvStatus = tvStatus;
        initListeners();
    }

    private void initListeners() {
        etQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) { }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                rebuild(false);   // 输入变化：重算并跳到第一处
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        findBar.findViewById(R.id.viewer_btn_find_prev).setOnClickListener(v -> step(-1));
        findBar.findViewById(R.id.viewer_btn_find_next).setOnClickListener(v -> step(1));
        findBar.findViewById(R.id.viewer_btn_find_close).setOnClickListener(v -> hide());
        etQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                step(1);   // 键盘搜索键 = 下一处（循环）
                return true;
            }
            return false;
        });
    }

    /** 打开查找栏：显示、聚焦输入并弹键盘、按当前内容重扫。 */
    public void show() {
        findBar.setVisibility(View.VISIBLE);
        etQuery.requestFocus();
        etQuery.post(() -> {
            InputMethodManager imm =
                    (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etQuery, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        rebuild(false);
    }

    /** 关闭查找栏：隐藏、收键盘、清空查找态与高亮。 */
    public void hide() {
        findBar.setVisibility(View.GONE);
        InputMethodManager imm =
                (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etQuery.getWindowToken(), 0);
        }
        matches = Collections.emptyList();
        currentIndex = -1;
        tvStatus.setText("");
        clearHighlight();
    }

    /** 查找栏打开时宿主内容已更新（异步加载完成）：重扫并保持当前序号。 */
    public void onContentChanged() {
        if (findBar.getVisibility() == View.VISIBLE) {
            rebuild(true);
        }
    }

    public boolean isVisible() {
        return findBar.getVisibility() == View.VISIBLE;
    }

    /**
     * 重建当前查询词的全部匹配（在宿主 TextView 当前文本上扫描）。
     * {@code preserve=true}：保持当前序号不跳转（内容重渲染后调用）；{@code false}：
     * 跳到第一处（输入变化/打开时）。查找栏隐藏时为空操作。
     */
    private void rebuild(boolean preserve) {
        if (findBar.getVisibility() != View.VISIBLE) return;
        String query = etQuery.getText().toString();
        int prev = currentIndex;
        matches = FindHelper.scanAll(textView.getText(), query, true);
        if (matches.isEmpty()) {
            currentIndex = -1;
            tvStatus.setText(query.isEmpty() ? "" : "未找到");
            // 新查询无匹配：清除上一查询残留高亮（状态与画面一致）
            clearHighlight();
            return;
        }
        if (preserve && prev >= 0 && prev < matches.size()) {
            currentIndex = prev;
            tvStatus.setText((currentIndex + 1) + "/" + matches.size());
            return;
        }
        currentIndex = 0;
        jump();
    }

    /** 上一处/下一处（循环）；结果为空时先防御性重算，仍无则提示。 */
    private void step(int direction) {
        if (matches.isEmpty()) rebuild(false);
        if (matches.isEmpty()) {
            tvStatus.setText("未找到");
            return;
        }
        currentIndex = FindHelper.advance(matches, currentIndex, direction);
        jump();
    }

    /** 跳到当前匹配：临时高亮区间+纵向（可选横向）平滑滚动居中。 */
    private void jump() {
        if (currentIndex < 0 || currentIndex >= matches.size()) return;
        FindHelper.Match m = matches.get(currentIndex);
        tvStatus.setText((currentIndex + 1) + "/" + matches.size());

        CharSequence current = textView.getText();
        SpannableStringBuilder ssb = new SpannableStringBuilder(current);
        if (activeHighlight != null) ssb.removeSpan(activeHighlight);
        activeHighlight = new BackgroundColorSpan(HIGHLIGHT_COLOR);
        ssb.setSpan(activeHighlight, m.start, m.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        textView.setText(ssb);
        textView.setTextIsSelectable(true);

        scrollToOffset(m.start);
    }

    private void clearHighlight() {
        if (activeHighlight == null) return;
        CharSequence current = textView.getText();
        if (!(current instanceof Spanned)) {
            activeHighlight = null;
            return;
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder(current);
        ssb.removeSpan(activeHighlight);
        activeHighlight = null;
        textView.setText(ssb);
        textView.setTextIsSelectable(true);
    }

    /** 滚动到命中起点：纵向居中（同 AnnotationOverlayHelper 算法），横向宿主存在时按列居中。 */
    private void scrollToOffset(int charOffset) {
        UiUtils.scrollToOffsetCenter(scrollView, textView, charOffset);
        if (horizontalScrollView != null) {
            textView.post(() -> {
                Layout layout = textView.getLayout();
                if (layout == null) return;
                float x = layout.getPrimaryHorizontal(charOffset);
                int target = Math.max(0, (int) x - horizontalScrollView.getWidth() / 2);
                horizontalScrollView.smoothScrollTo(target, 0);
            });
        }
    }
}
