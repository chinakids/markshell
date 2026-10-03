package com.ssh.mdreader.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * MarkdownFormatHelper 单测（路线图 #17 编辑格式工具栏纯函数层）。
 *
 * <p>覆盖语义（与 markor MarkdownActionButtons 源码实证对齐）：
 * wrapSelection=选区包裹切换（空选区插入光标置中/外包裹解包/内包裹解包/trim 保空白）；
 * toggleHeading=行级标题切换（同级别移除/异级别替换/无前缀插入/缩进保留/多行逐行）；
 * toggleUnorderedList=行级列表切换（移除/替换其它前缀/插入）。
 */
public class MarkdownFormatHelperTest {

    // ── wrapSelection：空选区 ───────────────────────────────────────────────

    @Test
    public void wrap_emptySelection_insertsPairAndPlacesCursorInside() {
        // 空选区：插入 open+close（** + ** = ****），光标落在中间（输入即成为 **文本**）
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("hello", 2, 2, "**", "**");
        assertEquals("he****llo", r.text);
        assertEquals(4, r.selStart);
        assertEquals(4, r.selEnd);
    }

    @Test
    public void wrap_emptySelection_atStartAndEnd() {
        MarkdownFormatHelper.Result s = MarkdownFormatHelper.wrapSelection("abc", 0, 0, "*", "*");
        assertEquals("**abc", s.text);
        assertEquals(1, s.selStart);

        MarkdownFormatHelper.Result e = MarkdownFormatHelper.wrapSelection("abc", 3, 3, "**", "**");
        assertEquals("abc****", e.text);
        assertEquals(5, e.selStart);
    }

    // ── wrapSelection：普通包裹 ─────────────────────────────────────────────

    @Test
    public void wrap_plainSelection_wrapsBothSides() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("hello world", 0, 5, "**", "**");
        assertEquals("**hello** world", r.text);
        assertEquals(2, r.selStart);
        assertEquals(7, r.selEnd);
    }

    @Test
    public void wrap_middleSelection_wrapsOnlySelection() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("say hello to", 4, 9, "_", "_");
        assertEquals("say _hello_ to", r.text);
        assertEquals(5, r.selStart);
        assertEquals(10, r.selEnd);
    }

    @Test
    public void wrap_asyncSelectionWithSurroundingWhitespace_keepsWhitespaceOutside() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("a  text  b", 1, 8, "*", "*");
        // 选区="  text  "（下标1..8）：trim 后核心 text，首尾空白留在包裹符外
        assertEquals("a  *text*  b", r.text);
        assertEquals(2, r.selStart);
        assertEquals(9, r.selEnd);
    }

    @Test
    public void wrap_allWhitespaceSelection_wrapsWhole() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("ax   b", 2, 5, "*", "*");
        assertEquals("ax*   *b", r.text);
        assertEquals(3, r.selStart);
        assertEquals(6, r.selEnd);
    }

    // ── wrapSelection：解包 ─────────────────────────────────────────────────

    @Test
    public void wrap_alreadyWrappedOutside_unwraps() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("**bold** here", 2, 6, "**", "**");
        // 选区外紧邻 **…**：解包后文本="bold here"（去外层包裹）
        assertEquals("bold here", r.text);
        assertEquals(0, r.selStart);
        assertEquals(4, r.selEnd);
    }

    @Test
    public void wrap_alreadyWrappedInside_unwraps() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("a **bold** b", 2, 10, "**", "**");
        // 选区本身以 ** 开头且以 ** 结尾：解包内层包裹
        assertEquals("a bold b", r.text);
        assertEquals(2, r.selStart);
        assertEquals(6, r.selEnd);
    }

    // ── wrapSelection：边界/防御 ────────────────────────────────────────────

    @Test
    public void wrap_nullOrEmptyMarkers_noOp() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("abc", 1, 2, "", "");
        assertEquals("abc", r.text);
        assertEquals(1, r.selStart);
        assertEquals(2, r.selEnd);
    }

    @Test
    public void wrap_nullText_returnsNullText() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection(null, 0, 0, "**", "**");
        assertSame(null, r.text);
    }

    @Test
    public void wrap_outOfRangeSelection_clamped() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("abc", -2, 99, "*", "*");
        assertEquals("*abc*", r.text);
        assertEquals(1, r.selStart);
    }

    @Test
    public void wrap_reversedSelection_normalized() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.wrapSelection("abcd", 3, 1, "**", "**");
        assertEquals("a**bc**d", r.text);
        assertEquals(3, r.selStart);
        assertEquals(5, r.selEnd);
    }

    // ── toggleHeading：单行 ─────────────────────────────────────────────────

    @Test
    public void heading_noPrefix_inserts() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleHeading("hello", 0, 0, 2);
        assertEquals("## hello", r.text);
    }

    @Test
    public void heading_sameLevel_removes() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleHeading("## hello", 7, 7, 2);
        assertEquals("hello", r.text);
    }

    @Test
    public void heading_differentLevel_replaces() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleHeading("### hello", 9, 9, 2);
        assertEquals("## hello", r.text);
        assertEquals("# hello", MarkdownFormatHelper.toggleHeading("### hello", 0, 0, 1).text);
    }

    @Test
    public void heading_indentedLine_preservesIndent() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleHeading("  deep", 2, 2, 3);
        assertEquals("  ### deep", r.text);
        // 缩进 + 既有标题 → 移除后缩进保留
        assertEquals("  abc", MarkdownFormatHelper.toggleHeading("  ## abc", 6, 6, 2).text);
    }

    @Test
    public void heading_quoteListPrefix_replacedByHeading() {
        assertEquals("# item", MarkdownFormatHelper.toggleHeading("- item", 0, 0, 1).text);
        assertEquals("## quoted", MarkdownFormatHelper.toggleHeading("> quoted", 0, 0, 2).text);
        assertEquals("## ordered", MarkdownFormatHelper.toggleHeading("1. ordered", 0, 0, 2).text);
    }

    @Test
    public void heading_levelBounds_clamped() {
        assertEquals("# x", MarkdownFormatHelper.toggleHeading("x", 0, 0, 0).text);
        assertEquals("###### x", MarkdownFormatHelper.toggleHeading("x", 0, 0, 9).text);
        assertEquals("###### x", MarkdownFormatHelper.toggleHeading("x", 0, 0, 99).text);
    }

    @Test
    public void heading_emptyLine_inserts() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleHeading("\n", 0, 0, 2);
        assertEquals("## \n", r.text);
    }

    // ── toggleHeading：多行选区逐行切换 ─────────────────────────────────────

    @Test
    public void heading_multiLineSelection_togglesEachLineIndependently() {
        String text = "one\n## two\nthree";
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleHeading(text, 0, text.length(), 2);
        // 行1 无前缀 → 加 ##；行2 同级别 → 移除；行3 无前缀 → 加 ##
        assertEquals("## one\ntwo\n## three", r.text);
    }

    @Test
    public void heading_selectionPreservedAfterBatch() {
        String text = "hello\nworld";
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleHeading(text, 0, 5, 1);
        assertEquals("# hello\nworld", r.text);
        // 插入 # 使选区 [0,5) 后移 2：0+2..5+2；但选区内容仍为 "hello"（原 0..5）
        assertEquals(2, r.selStart);
        assertEquals(7, r.selEnd);
    }

    // ── toggleUnorderedList ─────────────────────────────────────────────────

    @Test
    public void list_noPrefix_inserts() {
        assertEquals("- item", MarkdownFormatHelper.toggleUnorderedList("item", 0, 0).text);
    }

    @Test
    public void list_alreadyUnordered_removes() {
        assertEquals("item", MarkdownFormatHelper.toggleUnorderedList("- item", 0, 0).text);
        assertEquals("item", MarkdownFormatHelper.toggleUnorderedList("* item", 0, 0).text);
        assertEquals("item", MarkdownFormatHelper.toggleUnorderedList("+ item", 0, 0).text);
    }

    @Test
    public void list_otherPrefix_replaced() {
        assertEquals("- x", MarkdownFormatHelper.toggleUnorderedList("1. x", 0, 0).text);
        assertEquals("- h", MarkdownFormatHelper.toggleUnorderedList("# h", 0, 0).text);
        assertEquals("- quote", MarkdownFormatHelper.toggleUnorderedList("> quote", 0, 0).text);
    }

    @Test
    public void list_indentedLine_keepsIndent() {
        assertEquals("  - deep", MarkdownFormatHelper.toggleUnorderedList("  deep", 0, 0).text);
        assertEquals("  deep", MarkdownFormatHelper.toggleUnorderedList("  - deep", 3, 3).text);
    }

    @Test
    public void list_multiLineAllPlain_addsAll() {
        String text = "a\nb";
        assertEquals("- a\n- b", MarkdownFormatHelper.toggleUnorderedList(text, 0, text.length()).text);
    }

    @Test
    public void list_multiLineMixed_togglesEach() {
        String text = "- a\nb";
        assertEquals("a\n- b", MarkdownFormatHelper.toggleUnorderedList(text, 0, text.length()).text);
    }

    @Test
    public void list_emptyLines_insertPrefixEach() {
        // "a\n\nb" 全选：a 与空行与 b 三行都插入 -（markor 逐行语义）
        assertEquals("- a\n- \n- b", MarkdownFormatHelper.toggleUnorderedList("a\n\nb", 0, 4).text);
        // 单空行文本=一行空内容
        assertEquals("- \n", MarkdownFormatHelper.toggleUnorderedList("\n", 0, 0).text);
    }

    @Test
    public void toggle_multilineInsertions_selectionInLaterIndent_mappedCorrectly() {
        // 行0 无前缀插入 (- at 0) 且行1 缩进也插入：选区 [1,3) 跨两行且端点落在行1 前导空白内，
        // 位移换算必须用原坐标（回归：曾把已位移 p 与后续编辑原位置比较而错加一次位移）
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleUnorderedList("a\n  b", 1, 3);
        assertEquals("- a\n  - b", r.text);
        assertEquals(3, r.selStart);
        assertEquals(5, r.selEnd);
    }

    // ── 防御 ────────────────────────────────────────────────────────────────

    @Test
    public void toggle_nullText_returnsNullText() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleUnorderedList(null, 0, 0);
        assertEquals(null, r.text);
        assertEquals(null, MarkdownFormatHelper.toggleHeading(null, 0, 0, 1).text);
    }

    @Test
    public void toggle_outOfRangeSelection_clamped() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleUnorderedList("abc", -5, 999);
        assertEquals("- abc", r.text);
    }
}