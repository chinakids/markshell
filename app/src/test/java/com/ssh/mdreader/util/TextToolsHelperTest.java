package com.ssh.mdreader.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * TextToolsHelper 单测（能力发现第七十轮：编辑模式文本工具纯函数层，markor TextActions 对齐）。
 *
 * <p>覆盖：大小写 5 操作（全大写/全小写/逐词首字母大写/逐句首字母大写/大小写互换——空选区 no-op、
 * 选区随动全选变换结果、markor 两种 capitalize 语义差异逐项锁定）；行级 4 操作
 * （复制行/删除行/上移/下移——行块=选区覆盖行不含行尾换行、复制选区跟随复制块、删除含换行、
 * 首行上移与末行下移 no-op、多行选区整块移动、光标列跟随）。
 */
public class TextToolsHelperTest {

    // ── 大小写：基本 ────────────────────────────────────────────────────────

    @Test
    public void upper_fullSelection() {
        TextToolsHelper.Result r = TextToolsHelper.toUpperCase("hello, world!", 0, 13);
        assertEquals("HELLO, WORLD!", r.text);
        assertEquals(0, r.selStart);
        assertEquals(13, r.selEnd);
    }

    @Test
    public void upper_partialSelection() {
        TextToolsHelper.Result r = TextToolsHelper.toUpperCase("abcd", 1, 3);
        assertEquals("aBCd", r.text);
        assertEquals(1, r.selStart);
        assertEquals(3, r.selEnd);
    }

    @Test
    public void upper_emptySelection_noop() {
        TextToolsHelper.Result r = TextToolsHelper.toUpperCase("hello", 2, 2);
        assertEquals("hello", r.text);
        assertEquals(2, r.selStart);
        assertEquals(2, r.selEnd);
    }

    @Test
    public void upper_emptyText_noop() {
        TextToolsHelper.Result r = TextToolsHelper.toUpperCase("", 0, 0);
        assertEquals("", r.text);
    }

    @Test
    public void lower_partialSelection() {
        TextToolsHelper.Result r = TextToolsHelper.toLowerCase("HELLO WORLD", 6, 11);
        assertEquals("HELLO world", r.text);
        assertEquals(6, r.selStart);
        assertEquals(11, r.selEnd);
    }

    @Test
    public void caseChange_crossLine() {
        TextToolsHelper.Result r = TextToolsHelper.toUpperCase("a\nb", 0, 3);
        assertEquals("A\nB", r.text);
    }

    @Test
    public void upper_selectionFollowsGrowth() {
        // Locale.ROOT：ß 大写展开为 SS——变换后选区=变换结果全选（长度变化亦正确）
        TextToolsHelper.Result r = TextToolsHelper.toUpperCase("straße", 0, 6);
        assertEquals("STRASSE", r.text);
        assertEquals(0, r.selStart);
        assertEquals(7, r.selEnd);
    }

    @Test
    public void swapSelection_invertedAndClamped() {
        // 反向选区：交换端序后只作用于 [1,3)（"bc"→"BC"）
        TextToolsHelper.Result r = TextToolsHelper.toUpperCase("abc", 3, 1);
        assertEquals("aBC", r.text);
        assertEquals(1, r.selStart);
        assertEquals(3, r.selEnd);
    }

    // ── 大小写：capitalizeWords（词首大写其余小写，markor 语义）──────────────

    @Test
    public void capitalizeWords_basic() {
        TextToolsHelper.Result r = TextToolsHelper.capitalizeWords("hello world", 0, 11);
        assertEquals("Hello World", r.text);
        assertEquals(0, r.selStart);
        assertEquals(11, r.selEnd);
    }

    @Test
    public void capitalizeWords_lowersRest() {
        // markor：非词首字符 toLowerCase——全大写输入被归正
        TextToolsHelper.Result r = TextToolsHelper.capitalizeWords("HELLO WORLD", 0, 11);
        assertEquals("Hello World", r.text);
    }

    @Test
    public void capitalizeWords_digitsAndSymbols() {
        TextToolsHelper.Result r = TextToolsHelper.capitalizeWords("the 42nd floor", 0, 15);
        assertEquals("The 42nd Floor", r.text);
    }

    @Test
    public void capitalizeWords_multiWhitespace() {
        TextToolsHelper.Result r = TextToolsHelper.capitalizeWords("a\t b  c", 0, 7);
        assertEquals("A\t B  C", r.text);
    }

    // ── 大小写：capitalizeSentences（仅句首大写其余原样，markor 语义）────────

    @Test
    public void capitalizeSentences_basic() {
        TextToolsHelper.Result r = TextToolsHelper.capitalizeSentences("hello world. goodbye world", 0, 27);
        assertEquals("Hello world. Goodbye world", r.text);
    }

    @Test
    public void capitalizeSentences_keepsOtherCase() {
        // markor：句中大小写原样（不大写也不小写）
        TextToolsHelper.Result r = TextToolsHelper.capitalizeSentences("HELLO WORLD", 0, 11);
        assertEquals("HELLO WORLD", r.text);
    }

    @Test
    public void capitalizeSentences_afterPunctuation() {
        TextToolsHelper.Result r = TextToolsHelper.capitalizeSentences("hello! goodbye?\nnext line", 0, 28);
        assertEquals("Hello! Goodbye?\nNext line", r.text);
    }

    @Test
    public void capitalizeSentences_quotesNotSentenceEnd() {
        TextToolsHelper.Result r = TextToolsHelper.capitalizeSentences("say \"hi\"", 0, 9);
        assertEquals("Say \"hi\"", r.text);
    }

    // ── 大小写：switchCase ──────────────────────────────────────────────────

    @Test
    public void switchCase_mixed() {
        TextToolsHelper.Result r = TextToolsHelper.switchCase("Hello World 123", 0, 15);
        assertEquals("hELLO wORLD 123", r.text);
    }

    @Test
    public void switchCase_symbolsUnchanged() {
        TextToolsHelper.Result r = TextToolsHelper.switchCase("a-b_c", 0, 6);
        assertEquals("A-B_C", r.text);
    }

    // ── 行操作：duplicateLines ─────────────────────────────────────────────

    @Test
    public void duplicate_cursorLineMiddle() {
        // "ab\ncd\nef"，光标在 cd 行内——复制该行到其下，光标跟随到复制块同列
        TextToolsHelper.Result r = TextToolsHelper.duplicateLines("ab\ncd\nef", 3, 3);
        assertEquals("ab\ncd\ncd\nef", r.text);
        assertEquals(6, r.selStart);
        assertEquals(6, r.selEnd);
    }

    @Test
    public void duplicate_lastLine() {
        // 光标在末行行内（列保持跟随到复制块）："ab\ncd" 光标 4（d 前）→ 7（复制块同列）
        TextToolsHelper.Result r = TextToolsHelper.duplicateLines("ab\ncd", 4, 4);
        assertEquals("ab\ncd\ncd", r.text);
        assertEquals(7, r.selStart);
        assertEquals(7, r.selEnd);
    }

    @Test
    public void duplicate_multiLineSelection() {
        // 选区覆盖两行：复制两块，选区跟随到复制块且保持选中
        TextToolsHelper.Result r = TextToolsHelper.duplicateLines("a\nb\nc\nd", 0, 3);
        assertEquals("a\nb\na\nb\nc\nd", r.text);
        assertEquals(4, r.selStart);
        assertEquals(7, r.selEnd);
    }

    @Test
    public void duplicate_emptyLine() {
        // 光标在空行：复制出另一个空行
        TextToolsHelper.Result r = TextToolsHelper.duplicateLines("a\n\nb", 2, 2);
        assertEquals("a\n\n\nb", r.text);
        assertEquals(3, r.selStart);
    }

    @Test
    public void duplicate_selectionToLineEnd() {
        // 选区=整行（到行尾不含换行）：内容同整行复制
        TextToolsHelper.Result r = TextToolsHelper.duplicateLines("ab\ncd", 0, 2);
        assertEquals("ab\nab\ncd", r.text);
        assertEquals(3, r.selStart);
        assertEquals(5, r.selEnd);
    }

    // ── 行操作：deleteLines ────────────────────────────────────────────────

    @Test
    public void delete_cursorLineMiddle() {
        TextToolsHelper.Result r = TextToolsHelper.deleteLines("ab\ncd\nef", 3, 3);
        assertEquals("ab\nef", r.text);
        assertEquals(3, r.selStart);
        assertEquals(3, r.selEnd);
    }

    @Test
    public void delete_lastLine() {
        TextToolsHelper.Result r = TextToolsHelper.deleteLines("ab\ncd", 4, 4);
        assertEquals("ab\n", r.text);
        assertEquals(3, r.selStart);
    }

    @Test
    public void delete_singleLineFile() {
        TextToolsHelper.Result r = TextToolsHelper.deleteLines("hello", 2, 2);
        assertEquals("", r.text);
        assertEquals(0, r.selStart);
    }

    @Test
    public void delete_multiLineSelection() {
        TextToolsHelper.Result r = TextToolsHelper.deleteLines("a\nb\nc", 0, 3);
        assertEquals("c", r.text);
        assertEquals(0, r.selStart);
    }

    // ── 行操作：moveLinesUp / moveLinesDown ────────────────────────────────

    @Test
    public void moveUp_middleLine() {
        TextToolsHelper.Result r = TextToolsHelper.moveLinesUp("a\nb\nc", 2, 2);
        assertEquals("b\na\nc", r.text);
        assertEquals(0, r.selStart);
        assertEquals(0, r.selEnd);
    }

    @Test
    public void moveUp_firstLine_noop() {
        TextToolsHelper.Result r = TextToolsHelper.moveLinesUp("a\nb", 0, 0);
        assertEquals("a\nb", r.text);
        assertEquals(0, r.selStart);
    }

    @Test
    public void moveDown_lastLine_noop() {
        TextToolsHelper.Result r = TextToolsHelper.moveLinesDown("a\nb", 3, 3);
        assertEquals("a\nb", r.text);
        assertEquals(3, r.selStart);
    }

    @Test
    public void moveDown_middleLine() {
        TextToolsHelper.Result r = TextToolsHelper.moveLinesDown("a\nb\nc", 2, 2);
        assertEquals("a\nc\nb", r.text);
        assertEquals(4, r.selStart);
        assertEquals(4, r.selEnd);
    }

    @Test
    public void moveUp_multiLineBlock() {
        TextToolsHelper.Result r = TextToolsHelper.moveLinesUp("a\nb\nc\nd", 2, 5);
        assertEquals("b\nc\na\nd", r.text);
        assertEquals(0, r.selStart);
        assertEquals(3, r.selEnd);
    }

    @Test
    public void moveDown_multiLineBlock() {
        // 块 2 行、下块 1 行：不等长交换，选区按块内偏移跟随（块新起点=4）
        TextToolsHelper.Result r = TextToolsHelper.moveLinesDown("a\nb\nc\nd", 2, 5);
        assertEquals("a\nd\nb\nc", r.text);
        assertEquals(4, r.selStart);
        assertEquals(7, r.selEnd);
    }

    @Test
    public void move_cursorColumnFollows() {
        // 光标在行中部（列保持）："ab\ncde\nf" 光标 5（cde 行的 e）
        TextToolsHelper.Result r = TextToolsHelper.moveLinesUp("ab\ncde\nf", 5, 5);
        assertEquals("cde\nab\nf", r.text);
        assertEquals(2, r.selStart); // 新块起点 0 + 偏移 (5-3)=2 → 仍指向 e
    }

    @Test
    public void move_blankLineDown() {
        // 空行与下块交换：空行下移为尾空行（"a\n\nb" → "a\nb\n"），光标随行块（块新起点=4）
        TextToolsHelper.Result r = TextToolsHelper.moveLinesDown("a\n\nb", 2, 2);
        assertEquals("a\nb\n", r.text);
        assertEquals(4, r.selStart);
    }

    // ── 防御：null / 越界 ──────────────────────────────────────────────────

    @Test
    public void nullInput_noopResults() {
        assertEquals(null, TextToolsHelper.duplicateLines(null, 0, 0).text);
        assertEquals(null, TextToolsHelper.deleteLines(null, 0, 0).text);
        assertEquals(null, TextToolsHelper.moveLinesUp(null, 0, 0).text);
        assertEquals(null, TextToolsHelper.moveLinesDown(null, 0, 0).text);
        assertEquals(null, TextToolsHelper.toUpperCase(null, 0, 0).text);
        assertEquals(null, TextToolsHelper.toLowerCase(null, 0, 0).text);
        assertEquals(null, TextToolsHelper.capitalizeWords(null, 0, 0).text);
        assertEquals(null, TextToolsHelper.capitalizeSentences(null, 0, 0).text);
        assertEquals(null, TextToolsHelper.switchCase(null, 0, 0).text);
    }

    @Test
    public void outOfRangeSelection_clamped() {
        // 越界 clamp 后为空选区：no-op（输入区始终合法，防御场景）
        TextToolsHelper.Result r = TextToolsHelper.toUpperCase("ab", 5, 9);
        assertEquals("ab", r.text);
        assertEquals(2, r.selStart);
        assertEquals(2, r.selEnd);
    }

    @Test
    public void duplicate_selectionClampSafe() {
        TextToolsHelper.Result r = TextToolsHelper.duplicateLines("ab", 0, 10);
        assertEquals("ab\nab", r.text);
        assertEquals(3, r.selStart);
        assertEquals(5, r.selEnd);
    }
}
