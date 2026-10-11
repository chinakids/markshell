package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * {@link ReplaceHelper} 纯函数层单测：字面量替换（全部/单处/越界防御/大小写/空入参/
 * 替换文本再含查询词不递归/中英文/跨行）——替换语义与 FindHelper.scanAll 一致，
 * 本层保证「只替换扫描结果、不做任何转义展开」。
 */
public class ReplaceHelperTest {

    // ── replaceAll: 基本 ──────────────────────────────────────────────────────

    @Test
    public void replaceAll_singleOccurrence() {
        assertEquals("hello WORLD", ReplaceHelper.replaceAll("hello world", "world", "WORLD", false));
    }

    @Test
    public void replaceAll_multipleOccurrences() {
        assertEquals("XX", ReplaceHelper.replaceAll("abcabc", "abc", "X", false));
        assertEquals("a--b--c--", ReplaceHelper.replaceAll("a-b-c-", "-", "--", false));
    }

    @Test
    public void replaceAll_noMatch_returnsSameContent() {
        String src = "hello world";
        String out = ReplaceHelper.replaceAll(src, "xyz", "Z", false);
        assertEquals(src, out);
    }

    @Test
    public void replaceAll_queryEmpty_returnsSameContent() {
        assertEquals("abc", ReplaceHelper.replaceAll("abc", "", "X", false));
    }

    @Test
    public void replaceAll_queryNull_returnsSameContent() {
        assertEquals("abc", ReplaceHelper.replaceAll("abc", null, "X", false));
    }

    @Test
    public void replaceAll_textNull_returnsNull() {
        assertNull(ReplaceHelper.replaceAll(null, "a", "b", false));
    }

    @Test
    public void replaceAll_replacementNull_treatedAsEmpty() {
        assertEquals("bc", ReplaceHelper.replaceAll("abc", "a", null, false));
    }

    @Test
    public void replaceAll_ignoreCase_mixedCase() {
        // 大小写不敏感匹配（regionMatches 逐字符），输出按原文剩余部分原样保留
        assertEquals("X world X", ReplaceHelper.replaceAll("HeLLo world hElLo", "hello", "X", true));
    }

    @Test
    public void replaceAll_replacementContainsQuery_noRecursion() {
        // 原文 1 处匹配，替换成"aa"（再含查询串）——只替换原扫描的 1 处，不无限循环
        assertEquals("aa", ReplaceHelper.replaceAll("a", "a", "aa", false));
        assertEquals("kaa", ReplaceHelper.replaceAll("ka", "ka", "kaa", false));
    }

    @Test
    public void replaceAll_chineseText() {
        assertEquals("写代码很很好玩",
                ReplaceHelper.replaceAll("写代码很开心", "开心", "很好玩", false));
    }

    @Test
    public void replaceAll_crossLine() {
        assertEquals("第一行\n替换 之后的文本",
                ReplaceHelper.replaceAll("第一行\n目标 之后的文本", "行\n目标", "行\n替换", false));
    }

    @Test
    public void replaceAll_nonOverlappingMatches() {
        // scanAll 语义："aaa" 中 "aa" 仅 1 处 [0,2) → 替换后 "Xa"
        assertEquals("Xa", ReplaceHelper.replaceAll("aaa", "aa", "X", false));
    }

    @Test
    public void replaceAll_emptyText() {
        assertEquals("", ReplaceHelper.replaceAll("", "a", "X", false));
    }

    // ── replaceOccurrence: 单处替换 ───────────────────────────────────────────

    @Test
    public void replaceOccurrence_singleMatch() {
        assertEquals("aXc", ReplaceHelper.replaceOccurrence("abc", "b", "X", false, 0));
    }

    @Test
    public void replaceOccurrence_multipleOnlyNth() {
        assertEquals("Xab", ReplaceHelper.replaceOccurrence("abab", "ab", "X", false, 0));
        assertEquals("abX", ReplaceHelper.replaceOccurrence("abab", "ab", "X", false, 1));
    }

    @Test
    public void replaceOccurrence_indexOutOfRange_unchanged() {
        String src = "abab";
        assertEquals(src, ReplaceHelper.replaceOccurrence(src, "ab", "X", false, 2));
        assertEquals(src, ReplaceHelper.replaceOccurrence(src, "ab", "X", false, -1));
    }

    @Test
    public void replaceOccurrence_noMatch_unchanged() {
        assertEquals("abc", ReplaceHelper.replaceOccurrence("abc", "z", "X", false, 0));
    }

    @Test
    public void replaceOccurrence_ignoreCase_sensitiveDists() {
        assertEquals("aXc", ReplaceHelper.replaceOccurrence("aBc", "b", "X", true, 0));
        assertEquals("aBc", ReplaceHelper.replaceOccurrence("aBc", "b", "X", false, 0));
    }

    @Test
    public void replaceOccurrence_nullInputs() {
        assertNull(ReplaceHelper.replaceOccurrence(null, "a", "b", false, 0));
        assertEquals("abc", ReplaceHelper.replaceOccurrence("abc", "", "X", false, 0));
    }

    // ── 整词替换（wholeWord，与 FindHelper.scanAll(..., wholeWord) 同口径）──────

    @Test
    public void replaceAll_wholeWord_onlyReplacesStandalone() {
        // catalog 内 cat 不替换，独立 cat 替换
        assertEquals("catalog X", ReplaceHelper.replaceAll("catalog cat", "cat", "X", true, true));
        // 全匹配模式（wholeWord=false）catalog 内也替换
        assertEquals("Xalog X", ReplaceHelper.replaceAll("catalog cat", "cat", "X", true, false));
    }

    @Test
    public void replaceOccurrence_wholeWord_indexCountsFiltered() {
        // "cat catalog cat"：整词过滤后仅 2 处（首尾），occurrenceIndex=1 → 替换末处
        String out = ReplaceHelper.replaceOccurrence(
                "cat catalog cat", "cat", "X", true, true, 1);
        assertEquals("cat catalog X", out);
        // 越界（过滤后仅 2 处）→ 原样
        assertEquals("cat catalog cat",
                ReplaceHelper.replaceOccurrence("cat catalog cat", "cat", "X", true, true, 2));
    }

    @Test
    public void replaceAll_wholeWord_combinedWithIgnoreCase() {
        assertEquals("X dog X", ReplaceHelper.replaceAll("Cat dog CAT", "cat", "X", true, true));
        // 大小写敏感 + 整词：两处均不命中（小写 cat 不存在独立形式）
        assertEquals("Cat dog CAT", ReplaceHelper.replaceAll("Cat dog CAT", "cat", "X", false, true));
    }

    @Test
    public void replaceAll_wholeWord_cjkSeparator() {
        // 中文=分隔符：「测试」命中（CJK 相邻仍整词）
        assertEquals("中文X文本", ReplaceHelper.replaceAll("中文测试文本", "测试", "X", true, true));
        // 与 ASCII 字母相邻 → 不替换
        assertEquals("abc测试def", ReplaceHelper.replaceAll("abc测试def", "测试", "X", true, true));
    }
}
