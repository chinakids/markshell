package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.AnnotationEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** AnnotationNavigator 纯函数单测：文档序构建 + 上一处/下一处定位 + 边界（迭代34 B1）。 */
public class AnnotationNavigatorTest {

    /** "aaa" 出现于偏移 0/8/20（bbb@4 ccc@12 ddd@16）。 */
    private static final String TEXT = "aaa bbb aaa ccc ddd aaa";

    private static AnnotationEntry entry(String id, String original, int occ) {
        return new AnnotationEntry(id, "批注-" + id, original, occ);
    }

    private static List<AnnotationEntry> entries(AnnotationEntry... es) {
        return new ArrayList<>(Arrays.asList(es));
    }

    private static List<AnnotationNavigator.NavigableAnnotation> navOf(AnnotationEntry... es) {
        return AnnotationNavigator.buildNavigable(entries(es), TEXT);
    }

    // ── buildNavigable：文档序构建 ────────────────────────────────────────────

    @Test
    public void ordersByDocumentPositionNotInsertionOrder() {
        // CSV 顺序 = 添加顺序（末处、首处、中部）→ 返回序列必须按文档位置升序
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("c", "aaa", 2), entry("a", "aaa", 0), entry("b", "aaa", 1));
        assertEquals(3, nav.size());
        assertEquals("a", nav.get(0).entry.id);
        assertEquals(0, nav.get(0).start);
        assertEquals("b", nav.get(1).entry.id);
        assertEquals(8, nav.get(1).start);
        assertEquals("c", nav.get(2).entry.id);
        assertEquals(20, nav.get(2).start);
    }

    @Test
    public void skipsUnresolvableAnnotation() {
        // 原文片段在正文不存在 → 跳过（与 span 覆盖语义一致）
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("ok", "aaa", 0), entry("gone", "不存在文本", 0));
        assertEquals(1, nav.size());
        assertEquals("ok", nav.get(0).entry.id);
    }

    @Test
    public void skipsOccurrenceBeyondDocument() {
        // occurrenceIndex 超出实际出现次数 → 定位失败 → 跳过
        List<AnnotationNavigator.NavigableAnnotation> nav = navOf(entry("x", "aaa", 99));
        assertTrue(nav.isEmpty());
    }

    @Test
    public void skipsEmptyOriginalText() {
        List<AnnotationNavigator.NavigableAnnotation> nav = navOf(entry("e", "", 0));
        assertTrue(nav.isEmpty());
    }

    @Test
    public void resolvesOccurrenceIndex() {
        // 同一原文不同出现序号 → 不同落点
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("o0", "aaa", 0), entry("o2", "aaa", 2));
        assertEquals(2, nav.size());
        assertEquals(0, nav.get(0).start);
        assertEquals(20, nav.get(1).start);
    }

    @Test
    public void keepsInsertionOrderForEqualStart() {
        // 同一起点（重复条目）保持入参顺序（稳定排序）
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("dup1", "aaa", 0), entry("dup2", "aaa", 0));
        assertEquals(2, nav.size());
        assertEquals("dup1", nav.get(0).entry.id);
        assertEquals("dup2", nav.get(1).entry.id);
        assertEquals(nav.get(0).start, nav.get(1).start);
    }

    @Test
    public void emptyInputsProduceEmpty() {
        assertTrue(AnnotationNavigator.buildNavigable(null, TEXT).isEmpty());
        assertTrue(AnnotationNavigator.buildNavigable(
                entries(entry("e", "aaa", 0)), null).isEmpty());
        assertTrue(AnnotationNavigator.buildNavigable(
                entries(entry("e", "aaa", 0)), "").isEmpty());
        assertTrue(AnnotationNavigator.buildNavigable(
                Collections.<AnnotationEntry>emptyList(), TEXT).isEmpty());
    }

    // ── nextIndex：下一处 ─────────────────────────────────────────────────────

    @Test
    public void nextIndexEmptyListIsMinusOne() {
        assertEquals(-1, AnnotationNavigator.nextIndex(Collections.emptyList(), 0, -1));
    }

    @Test
    public void nextIndexFromTopSkipsVisibleFirst() {
        // 视口在文档顶（0 处批注可见）→ 下一处 = 之后最近一处（8）
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(1, AnnotationNavigator.nextIndex(nav, 0, -1));
    }

    @Test
    public void nextIndexFromMiddleViewport() {
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(1, AnnotationNavigator.nextIndex(nav, 5, -1));
    }

    @Test
    public void nextIndexPastLastIsMinusOne() {
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(-1, AnnotationNavigator.nextIndex(nav, 20, -1));
    }

    @Test
    public void nextIndexSkipsLastVisitedRepeat() {
        // 跳到 8 后目标居中、视口回到其前 → 下一处必须跳过 8 取 16，否则反复命中
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(2, AnnotationNavigator.nextIndex(nav, 3, 8));
    }

    @Test
    public void nextIndexLastVisitedAtEndIsMinusOne() {
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(-1, AnnotationNavigator.nextIndex(nav, 19, 20));
    }

    @Test
    public void nextIndexAtExactAnnotationStart() {
        // 视口顶恰在 8 的起点：该处视为当前可见，下一处 = 16
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(2, AnnotationNavigator.nextIndex(nav, 8, -1));
    }

    // ── previousIndex：上一处 ─────────────────────────────────────────────────

    @Test
    public void previousIndexBelowAllReturnsLast() {
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(2, AnnotationNavigator.previousIndex(nav, 25, -1));
    }

    @Test
    public void previousIndexBeforeFirstIsMinusOne() {
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1));
        assertEquals(-1, AnnotationNavigator.previousIndex(nav, 0, -1));
    }

    @Test
    public void previousIndexAfterVisitUsesBeforeVisitedWhenNothingAbove() {
        // 视口上方无批注、游标=b(8)：上一处 = b 之前的一处（a），即使 a 在视口内
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(0, AnnotationNavigator.previousIndex(nav, 0, 8));
    }

    @Test
    public void previousIndexPrefersNearestAboveViewport() {
        // 视口在 20 之后：上一处 = 视口上方最近（=20），不受游标 b 影响
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(2, AnnotationNavigator.previousIndex(nav, 25, 8));
    }

    @Test
    public void previousIndexFromAboveVisited() {
        // 视口顶=5（a 已滚出视口上方）：上一处 = a（start<5 的最近一处）
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1), entry("c", "aaa", 2));
        assertEquals(0, AnnotationNavigator.previousIndex(nav, 5, 8));
    }

    @Test
    public void previousIndexFirstVisitedIsMinusOne() {
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1));
        assertEquals(-1, AnnotationNavigator.previousIndex(nav, 0, 0));
    }

    // ── indexOfStart ──────────────────────────────────────────────────────────

    @Test
    public void indexOfStartFoundAndMissing() {
        List<AnnotationNavigator.NavigableAnnotation> nav =
                navOf(entry("a", "aaa", 0), entry("b", "aaa", 1));
        assertEquals(1, AnnotationNavigator.indexOfStart(nav, 8));
        assertEquals(-1, AnnotationNavigator.indexOfStart(nav, 99));
        assertEquals(-1, AnnotationNavigator.indexOfStart(Collections.emptyList(), 0));
    }
}
