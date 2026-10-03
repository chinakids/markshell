package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ssh.mdreader.model.AnnotationEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批注连续导航（上一处/下一处）纯函数层——JVM 可测，无 Android 依赖。
 *
 * <p>两条核心规则：</p>
 * <ol>
 *   <li><b>导航序列 = 文档顺序</b>：CSV 中批注按添加先后排列（非文档序），导航必须按
 *       span 实际落点（字符偏移）升序；无法在正文中定位的条目跳过（与
 *       {@link AnnotationOverlayHelper#applyAnnotationSpans()} 的跳过语义一致）。
 *       同一起点并列时保持入参顺序（稳定排序）。</li>
 *   <li><b>当前阅读位置 = 视口顶部字符偏移</b>：下一处 = 视口之后最近的一处；上一处 =
 *       视口之前最近的一处。跳转后目标被居中，视口顶偏移会回到目标之前——若不加记忆会
 *       反复命中同一处，因此以 {@code lastVisited}（最近一次导航到的起点）做一次性
 *       「跳过重复」消歧：
 *       <ul>
 *         <li>下一处：候选中标且 == lastVisited 时再后移一位（仅当仍有更后条目）；</li>
 *         <li>上一处：仅当视口上方无任何批注且 lastVisited 存在时，取 lastVisited 的前一条
 *             （覆盖「目标被视口居中、上一条恰好也在视口内」的重复命中）；</li>
 *       </ul>
 *       即：手动滚动后按视口位置导航；连续点按按访问游标导航。</li>
 * </ol>
 *
 * <p>边界行为：无可导航批注／已到首尾（首条之前、末条之后）时返回 {@code -1}，由 UI
 * 层 toast 提示；<b>不循环</b>——阅读场景下循环跳转会丢失上下文，循环可后续按反馈再议。</p>
 */
public final class AnnotationNavigator {

    private AnnotationNavigator() {}

    /** 可导航批注：条目 + 按渲染文本解析出的实际字符区间。 */
    public static final class NavigableAnnotation {
        @NonNull public final AnnotationEntry entry;
        /** 正文中起始字符偏移（含）。 */
        public final int start;
        /** 正文中结束字符偏移（不含）。 */
        public final int end;

        public NavigableAnnotation(@NonNull AnnotationEntry entry, int start, int end) {
            this.entry = entry;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * 由批注列表 + 渲染后的正文纯文本计算文档顺序的可导航序列（升序、稳定）。
     * 跳过语义与 span 覆盖一致：原文为空／按 occurrenceIndex 定位失败／越界。
     * 定位走一次扫描索引（{@link AnnotationHelper#buildLocator}），每条目查询 O(1)，
     * 语义与 {@link AnnotationHelper#findNthOccurrence} 对拍一致（专项 B3）。
     */
    @NonNull
    public static List<NavigableAnnotation> buildNavigable(
            @Nullable List<AnnotationEntry> annotations, @Nullable String plainText) {
        List<NavigableAnnotation> result = new ArrayList<>();
        if (annotations == null || annotations.isEmpty()
                || plainText == null || plainText.isEmpty()) {
            return result;
        }
        AnnotationOccurrenceIndex.Locator locator =
                AnnotationHelper.buildLocator(annotations, plainText);
        for (AnnotationEntry e : annotations) {
            if (e == null || e.originalText == null || e.originalText.isEmpty()) continue;
            int start = locator.occurrenceStart(e.originalText, e.occurrenceIndex);
            if (start < 0) continue;
            int end = start + e.originalText.length();
            if (end > plainText.length()) continue; // 防御：理论不越界
            result.add(new NavigableAnnotation(e, start, end));
        }
        Collections.sort(result, (a, b) -> Integer.compare(a.start, b.start));
        return result;
    }

    /**
     * 「下一处」导航结果索引（无 → -1）。
     *
     * @param nav        文档顺序可导航序列（{@link #buildNavigable} 产出）
     * @param viewportTop 当前视口顶部的字符偏移（阅读位置）
     * @param lastVisited 最近一次导航到的起点（-1 = 尚无）
     */
    public static int nextIndex(@NonNull List<NavigableAnnotation> nav,
                                int viewportTop, int lastVisited) {
        int n = nav.size();
        if (n == 0) return -1;
        int i = -1;
        for (int k = 0; k < n; k++) {
            if (nav.get(k).start > viewportTop) { i = k; break; }
        }
        if (i < 0) return -1;
        // 目标恰好是上次访问的一处（跳转后居中，视口会回到它之前）→ 再后移一位
        if (lastVisited >= 0 && nav.get(i).start == lastVisited) {
            return (i + 1 < n) ? i + 1 : -1;
        }
        return i;
    }

    /**
     * 「上一处」导航结果索引（无 → -1）。
     *
     * @param nav        文档顺序可导航序列（{@link #buildNavigable} 产出）
     * @param viewportTop 当前视口顶部的字符偏移（阅读位置）
     * @param lastVisited 最近一次导航到的起点（-1 = 尚无）
     */
    public static int previousIndex(@NonNull List<NavigableAnnotation> nav,
                                    int viewportTop, int lastVisited) {
        int n = nav.size();
        if (n == 0) return -1;
        int j = -1;
        for (int k = n - 1; k >= 0; k--) {
            if (nav.get(k).start < viewportTop) { j = k; break; }
        }
        if (j >= 0) return j;
        // 视口上方无批注：若已有访问游标，取起点在 lastVisited 之前的最近一条
        // （覆盖目标居中、上一条仍在视口内的重复命中场景）
        if (lastVisited >= 0) {
            int k = indexOfStart(nav, lastVisited);
            if (k >= 0) return (k > 0) ? k - 1 : -1;
        }
        return -1;
    }

    /** 在序列中定位 start 的索引；不存在 → -1。 */
    public static int indexOfStart(@NonNull List<NavigableAnnotation> nav, int start) {
        for (int k = 0; k < nav.size(); k++) {
            if (nav.get(k).start == start) return k;
        }
        return -1;
    }
}
