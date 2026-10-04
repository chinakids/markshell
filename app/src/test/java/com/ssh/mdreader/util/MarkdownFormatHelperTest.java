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

    // ── toggleQuote（迭代80：markor toggleQuote 对齐）────────────────────────

    @Test
    public void quote_plainLine_insertsQuote() {
        assertEquals("> text", MarkdownFormatHelper.toggleQuote("text", 0, 0).text);
    }

    @Test
    public void quote_alreadyQuote_removes() {
        assertEquals("text", MarkdownFormatHelper.toggleQuote("> text", 0, 0).text);
        // 无分隔空格形态同样识别为引用并移除
        assertEquals("text", MarkdownFormatHelper.toggleQuote(">text", 0, 0).text);
    }

    @Test
    public void quote_otherPrefix_replacedByQuote() {
        assertEquals("> h", MarkdownFormatHelper.toggleQuote("# h", 0, 0).text);
        assertEquals("> x", MarkdownFormatHelper.toggleQuote("- x", 0, 0).text);
        assertEquals("> x", MarkdownFormatHelper.toggleQuote("1. x", 0, 0).text);
    }

    @Test
    public void quote_taskLine_wholePrefixReplaced() {
        // checkbox 前缀整体替换为引用（markor PREFIX_PATTERNS 同序：task 前缀先于被 unordered 误判）
        assertEquals("> x", MarkdownFormatHelper.toggleQuote("- [ ] x", 0, 0).text);
        assertEquals("> x", MarkdownFormatHelper.toggleQuote("- [x] x", 0, 0).text);
    }

    @Test
    public void quote_preservesIndent() {
        assertEquals("  > text", MarkdownFormatHelper.toggleQuote("  text", 0, 0).text);
        assertEquals("  text", MarkdownFormatHelper.toggleQuote("  > text", 0, 0).text);
    }

    @Test
    public void quote_multiLine_togglesEach() {
        String text = "a\nb";
        assertEquals("> a\n> b", MarkdownFormatHelper.toggleQuote(text, 0, text.length()).text);
        assertEquals("a\nb", MarkdownFormatHelper.toggleQuote("> a\n> b", 0, 9).text);
    }

    // ── toggleOrderedList（迭代80：markor replaceWithOrderedListPrefixOrRemovePrefix 对齐）──

    @Test
    public void ordered_plainLine_insertsOrdered() {
        assertEquals("1. text", MarkdownFormatHelper.toggleOrderedList("text", 0, 0).text);
    }

    @Test
    public void ordered_alreadyOrdered_removes() {
        assertEquals("text", MarkdownFormatHelper.toggleOrderedList("1. text", 0, 0).text);
        assertEquals("text", MarkdownFormatHelper.toggleOrderedList("1) text", 0, 0).text);
        assertEquals("text", MarkdownFormatHelper.toggleOrderedList("12. text", 0, 0).text);
    }

    @Test
    public void ordered_otherPrefix_replaced() {
        assertEquals("1. x", MarkdownFormatHelper.toggleOrderedList("- x", 0, 0).text);
        assertEquals("1. h", MarkdownFormatHelper.toggleOrderedList("# h", 0, 0).text);
        assertEquals("1. q", MarkdownFormatHelper.toggleOrderedList("> q", 0, 0).text);
        assertEquals("1. x", MarkdownFormatHelper.toggleOrderedList("- [ ] x", 0, 0).text);
    }

    @Test
    public void ordered_preservesIndent() {
        assertEquals("  1. text", MarkdownFormatHelper.toggleOrderedList("  text", 0, 0).text);
        assertEquals("  text", MarkdownFormatHelper.toggleOrderedList("  1. text", 0, 0).text);
    }

    @Test
    public void ordered_multiLine_togglesEach() {
        // 迭代81 起：切换后自动重编号（markor runRenumberOrderedListIfRequired 对齐），不再恒置「1. 」
        assertEquals("1. a\n2. b", MarkdownFormatHelper.toggleOrderedList("a\nb", 0, 3).text);
        assertEquals("1. a\n2. b\n3. c", MarkdownFormatHelper.toggleOrderedList("a\nb\nc", 0, 5).text);
    }

    // ── renumberOrderedList（迭代81：markor AutoTextFormatter.renumberOrderedList 对齐）────

    @Test
    public void renumber_fixesOffNumbersInWholeList() {
        assertEquals("1. a\n2. b", MarkdownFormatHelper.renumberOrderedList("3. a\n5. b", 0, 0).text);
        assertEquals("1. a\n2. b\n3. c", MarkdownFormatHelper.renumberOrderedList("7. a\n1. b\n1. c", 0, 0).text);
    }

    @Test
    public void renumber_fromMiddleLine_fixesWholeList() {
        // 选区/光标在中间行：getOrderedListStart 向上取列表顶，整段重编（markor 同型）
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.renumberOrderedList("3. a\n4. b\n5. c", 5, 5);
        assertEquals("1. a\n2. b\n3. c", r.text);
        assertEquals(5, r.selStart);
        assertEquals(5, r.selEnd);
    }

    @Test
    public void renumber_nestedSubLists_renumberIndependently() {
        // 子列表（缩进差>2）从 1 独立重编，父列表续编（markor 栈语义）
        assertEquals("1. a\n    1. x\n    2. y\n2. b",
                MarkdownFormatHelper.renumberOrderedList("9. a\n    5. x\n    7. y\n4. b", 0, 0).text);
    }

    @Test
    public void renumber_emptyLine_doesNotBreakList() {
        // 空行是任何层子级，不中断编号（markor isEmpty 语义）
        assertEquals("1. a\n\n2. b", MarkdownFormatHelper.renumberOrderedList("5. a\n\n8. b", 0, 0).text);
    }

    @Test
    public void renumber_differentDelimiter_isSeparateListUntouched() {
        // 同级不同分隔符（. vs )）不匹配 firstLine：遍历停止，该行不动（markor isMatchingList 同型）
        assertEquals("1. a\n3) b", MarkdownFormatHelper.renumberOrderedList("1. a\n3) b", 0, 0).text);
        assertEquals("1. a\n7) b", MarkdownFormatHelper.renumberOrderedList("7. a\n7) b", 0, 0).text);
    }

    @Test
    public void renumber_sameLevelPlainLine_breaksTraversal() {
        // 同级普通行不是列表项也不是更深子级：遍历停止，其后列表行不重编（markor while 条件同型）
        assertEquals("1. a\nplain\n5. b", MarkdownFormatHelper.renumberOrderedList("3. a\nplain\n5. b", 0, 0).text);
    }

    @Test
    public void renumber_deeperPlainLine_keepsListButNotNumbered() {
        // 更深缩进普通行：保留父层栈、不编号，其后同级列表续编（markor 层级处理同型）
        assertEquals("1. a\n    plain\n2. b", MarkdownFormatHelper.renumberOrderedList("1. a\n    plain\n3. b", 0, 0).text);
    }

    @Test
    public void renumber_indentUpToSlack_sameList() {
        // markor indentSlack=2：缩进差<=2 视作同级，编码连续
        assertEquals("1. a\n  2. b", MarkdownFormatHelper.renumberOrderedList("5. a\n  3. b", 0, 0).text);
    }

    @Test
    public void renumber_firstLineNotOrdered_noop() {
        // 光标所在（向上取到的）首行非有序且非空：整体 no-op（markor !firstLine.isOrderedList 同型）
        assertEquals("hello\n5. a", MarkdownFormatHelper.renumberOrderedList("hello\n5. a", 0, 0).text);
    }

    @Test
    public void renumber_cursorOnListBelowPlainLine_renumbersFromThatList() {
        // 光标行自身有序：从该列表段顶开始重编（顶层普通行不是其父级，markor getParent 同型）
        assertEquals("hello\n1. a", MarkdownFormatHelper.renumberOrderedList("hello\n5. a", 6, 6).text);
    }

    @Test
    public void renumber_numberLengthens_shiftSelectionBeforeEdit() {
        // 编辑整体位于选区端点之前时位移（markor shifts 同型；'10'->'2' 缩短 1）
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.renumberOrderedList("9. a\n10. b", 9, 10);
        assertEquals("1. a\n2. b", r.text);
        assertEquals(8, r.selStart);
        assertEquals(9, r.selEnd);
    }

    @Test
    public void renumber_alreadySequential_noEdit() {
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.renumberOrderedList("1. a\n2. b\n3. c", 0, 0);
        assertSame("1. a\n2. b\n3. c", r.text);
    }


    // ── toggleTaskList（迭代80：markor toggleToCheckedOrUncheckedListPrefix 对齐）────

    @Test
    public void task_unchecked_becomesChecked() {
        assertEquals("- [x] a", MarkdownFormatHelper.toggleTaskList("- [ ] a", 0, 0).text);
    }

    @Test
    public void task_checked_becomesUnchecked() {
        assertEquals("- [ ] a", MarkdownFormatHelper.toggleTaskList("- [x] a", 0, 0).text);
        assertEquals("- [ ] a", MarkdownFormatHelper.toggleTaskList("- [X] a", 0, 0).text);
    }

    @Test
    public void task_plainLine_insertsUnchecked() {
        assertEquals("- [ ] text", MarkdownFormatHelper.toggleTaskList("text", 0, 0).text);
    }

    @Test
    public void task_otherPrefix_replacedByUnchecked() {
        assertEquals("- [ ] x", MarkdownFormatHelper.toggleTaskList("1. x", 0, 0).text);
        assertEquals("- [ ] q", MarkdownFormatHelper.toggleTaskList("> q", 0, 0).text);
        assertEquals("- [ ] h", MarkdownFormatHelper.toggleTaskList("# h", 0, 0).text);
        assertEquals("- [ ] y", MarkdownFormatHelper.toggleTaskList("* y", 0, 0).text);
    }

    @Test
    public void task_preservesIndent() {
        assertEquals("  - [ ] text", MarkdownFormatHelper.toggleTaskList("  text", 0, 0).text);
        assertEquals("  - [x] text", MarkdownFormatHelper.toggleTaskList("  - [ ] text", 0, 0).text);
    }

    @Test
    public void task_multiLine_togglesEach() {
        assertEquals("- [ ] a\n- [ ] b", MarkdownFormatHelper.toggleTaskList("a\nb", 0, 3).text);
        // 混合状态逐行独立翻转
        assertEquals("- [x] a\n- [ ] b",
                MarkdownFormatHelper.toggleTaskList("- [ ] a\n- [x] b", 0, 13).text);
    }

    @Test
    public void task_selectionMappedAcrossInsertions() {
        // 选区 [1,3) 跨行0末尾到文末（e-1=2 落在行1故两行都处理），位移换算回归同型
        MarkdownFormatHelper.Result r = MarkdownFormatHelper.toggleTaskList("a\nb", 1, 3);
        assertEquals("- [ ] a\n- [ ] b", r.text);
        assertEquals(7, r.selStart);
        assertEquals(15, r.selEnd);
    }

    // ── detectPrefix 扩展回归（迭代80：checkbox 先于无序列表判定，markor PREFIX_PATTERNS 同序）──

    @Test
    public void unordered_taskLine_wholePrefixReplacedByDash() {
        // 之前 checkbox 行被误识别为无序列表（markerEnd=2），导致仅移除「- 」剩「[ ] a」；
        // 对齐 markor 后整段任务前缀替换为「- 」
        assertEquals("- a", MarkdownFormatHelper.toggleUnorderedList("- [ ] a", 0, 0).text);
        assertEquals("- a", MarkdownFormatHelper.toggleUnorderedList("- [x] a", 0, 0).text);
    }

    @Test
    public void heading_taskLine_wholePrefixReplacedByHeading() {
        assertEquals("# a", MarkdownFormatHelper.toggleHeading("- [ ] a", 0, 0, 1).text);
        assertEquals("## b", MarkdownFormatHelper.toggleHeading("- [x] b", 0, 0, 2).text);
    }

    // ── 包裹动作扩展（迭代80：删除线/行内代码=wrapSelection 复用）───────────────

    @Test
    public void wrap_strikeout_toggles() {
        assertEquals("~~gone~~", MarkdownFormatHelper.wrapSelection("gone", 0, 4, "~~", "~~").text);
        assertEquals("gone", MarkdownFormatHelper.wrapSelection("~~gone~~", 0, 8, "~~", "~~").text);
    }

    @Test
    public void wrap_inlineCode_toggles() {
        assertEquals("`code`", MarkdownFormatHelper.wrapSelection("code", 0, 4, "`", "`").text);
        assertEquals("code", MarkdownFormatHelper.wrapSelection("`code`", 0, 6, "`", "`").text);
    }

    // ── 回车自动续行（迭代82：markor AutoTextFormatter.autoIndent 行为语义对位）──

    @Test
    public void newline_ordered_continuesWithNextNumber() {
        assertEquals("2. ", MarkdownFormatHelper.newlineContinuation("1. hello", 7));
        assertEquals("8. ", MarkdownFormatHelper.newlineContinuation("7. hello", 7));
        assertEquals("13. ", MarkdownFormatHelper.newlineContinuation("12. long", 8));
    }

    @Test
    public void newline_ordered_zeroPadded_parsesAsNumber() {
        // markor tryParseInt("01")=1 → 2（不保留前导零；getNextOrderedValue 数字分支同型）
        assertEquals("2. ", MarkdownFormatHelper.newlineContinuation("01. x", 5));
    }

    @Test
    public void newline_ordered_preservesIndent() {
        assertEquals("   2. ", MarkdownFormatHelper.newlineContinuation("   1. hello", 11));
        assertEquals("\t2. ", MarkdownFormatHelper.newlineContinuation("\t1. hello", 8));
    }

    @Test
    public void newline_ordered_cursorMidLine_stillContinues() {
        // 光标在行内容中部（markor 条件仅需求「光标位于前缀之后」）
        assertEquals("2. ", MarkdownFormatHelper.newlineContinuation("1. hello world", 9));
    }

    @Test
    public void newline_ordered_cursorInsidePrefix_doesNotContinue() {
        // 光标落于前缀（数字/分隔符）之内：markor dend>=groupEnd 不满足→仅缩进（此处无缩进=空）
        assertEquals("", MarkdownFormatHelper.newlineContinuation("1. hello", 2));
    }

    @Test
    public void newline_ordered_prefixOnlyLine_doesNotContinue() {
        // 行=恰好只有前缀「1. 」：markor lineEnd==groupEnd=不续（detectPrefix 去尾空白后 P_NONE 天然对齐）
        assertEquals("", MarkdownFormatHelper.newlineContinuation("1. \nnext", 3));
    }

    @Test
    public void newline_ordered_prefixOnlyWithTrailingSpaces_doesNotContinue() {
        // 行=前缀+尾随空格：markor 视为「有内容」会续（lineEnd!=groupEnd）；
        // 本项目 detectPrefix 于去尾空白后识别=不续（差异=更保守，落档已标注）
        assertEquals("", MarkdownFormatHelper.newlineContinuation("1.  \nx", 4));
    }

    @Test
    public void newline_unordered_keepsOriginalMarker() {
        assertEquals("- ", MarkdownFormatHelper.newlineContinuation("- hello", 7));
        assertEquals("* ", MarkdownFormatHelper.newlineContinuation("* hello", 7));
        assertEquals("+ ", MarkdownFormatHelper.newlineContinuation("+ hello", 7));
    }

    @Test
    public void newline_unordered_preservesIndent() {
        assertEquals("  - ", MarkdownFormatHelper.newlineContinuation("  - hello", 9));
    }

    @Test
    public void newline_task_unchecked_continuesUnchecked() {
        assertEquals("- [ ] ", MarkdownFormatHelper.newlineContinuation("- [ ] todo", 10));
    }

    @Test
    public void newline_task_checked_continuesUnchecked() {
        // 新项=未勾选（markor newItemPrefix 同型：PREFIX_CHECKBOX_LIST 左右组拼装，「x」/「X」同族）
        assertEquals("- [ ] ", MarkdownFormatHelper.newlineContinuation("- [x] done", 9));
        assertEquals("- [ ] ", MarkdownFormatHelper.newlineContinuation("- [X] done", 9));
    }

    @Test
    public void newline_task_keepsMarkerChar() {
        assertEquals("* [ ] ", MarkdownFormatHelper.newlineContinuation("* [x] done", 9));
    }

    @Test
    public void newline_task_preservesIndent() {
        assertEquals("  - [ ] ", MarkdownFormatHelper.newlineContinuation("  - [ ] todo", 12));
    }

    @Test
    public void newline_quote_doesNotContinueQuote() {
        // markor：autoIndent 仅处理有序/无序/任务，引用/标题=else 分支=仅前导缩进（此处无缩进）
        assertEquals("", MarkdownFormatHelper.newlineContinuation("> quote", 7));
    }

    @Test
    public void newline_heading_doesNotContinue() {
        assertEquals("", MarkdownFormatHelper.newlineContinuation("# title", 7));
    }

    @Test
    public void newline_plain_doesNotAddAnything() {
        assertEquals("", MarkdownFormatHelper.newlineContinuation("hello", 5));
    }

    @Test
    public void newline_indentedPlain_preservesIndent() {
        // 代码块内按回车=缩进保留（markor「智能缩进」同型）
        assertEquals("    ", MarkdownFormatHelper.newlineContinuation("    code", 8));
        assertEquals("\t", MarkdownFormatHelper.newlineContinuation("\tcode", 5));
    }

    @Test
    public void newline_indentedQuote_preservesIndentOnly() {
        // 引用带缩进：续=仅缩进，不续「> 」（markor 同）
        assertEquals("    ", MarkdownFormatHelper.newlineContinuation("    > quote", 11));
    }

    @Test
    public void newline_emptyAndNull() {
        assertEquals("", MarkdownFormatHelper.newlineContinuation("", 0));
        assertEquals("", MarkdownFormatHelper.newlineContinuation(null, 0));
    }

    @Test
    public void newline_cursorOutOfRange_clamps() {
        // cursor 越界 clamp 到文末（其余逻辑不变）
        assertEquals("2. ", MarkdownFormatHelper.newlineContinuation("1. a", 99));
    }

    @Test
    public void newline_atSecondLine_usesThatLine() {
        assertEquals("2. ", MarkdownFormatHelper.newlineContinuation("line1\n1. item", 11));
        assertEquals("", MarkdownFormatHelper.newlineContinuation("line1\nplain", 11));
    }

    @Test
    public void newline_onlyWhitespaceLine_preservesIndent() {
        // 全空白行：本项目保留其缩进（markor 空行 indentEnd=0 不保留——差异=更符合代码块内回车直觉，落档已标注）
        assertEquals("    ", MarkdownFormatHelper.newlineContinuation("    ", 4));
    }

    @Test
    public void newline_atTextEndWithoutTrailingNewline() {
        assertEquals("2. ", MarkdownFormatHelper.newlineContinuation("1. item", 7));
    }

    @Test
    public void newline_orderedParenthesesDelimiter() {
        // 分隔符「)」保留（markor delimiter 同型）
        assertEquals("2) ", MarkdownFormatHelper.newlineContinuation("1) item", 7));
    }
}