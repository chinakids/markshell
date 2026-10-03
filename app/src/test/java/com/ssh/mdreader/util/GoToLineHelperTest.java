package com.ssh.mdreader.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * GoToLineHelper 单测：行号解析（markor &lt;1→1 语义）/行首偏移/高亮区间。
 * 越界语义与 markor getIndexFromLineOffset 对拍（循环结束返回 length）。
 */
public class GoToLineHelperTest {

    // ── parseLineNumber ──────────────────────────────────────────────────────

    @Test
    public void parse_nullReturnsInvalid() {
        assertEquals(-1, GoToLineHelper.parseLineNumber(null));
    }

    @Test
    public void parse_emptyReturnsInvalid() {
        assertEquals(-1, GoToLineHelper.parseLineNumber(""));
        assertEquals(-1, GoToLineHelper.parseLineNumber("   "));
    }

    @Test
    public void parse_nonNumericReturnsInvalid() {
        assertEquals(-1, GoToLineHelper.parseLineNumber("abc"));
        assertEquals(-1, GoToLineHelper.parseLineNumber("12abc"));
        assertEquals(-1, GoToLineHelper.parseLineNumber("1.5"));
    }

    @Test
    public void parse_overflowReturnsInvalid() {
        assertEquals(-1, GoToLineHelper.parseLineNumber("999999999999"));
    }

    @Test
    public void parse_belowOneClampsToMarkorSemantics() {
        // markor: lineNumber < 1 → 1（0、负数、前导正号一致）
        assertEquals(1, GoToLineHelper.parseLineNumber("0"));
        assertEquals(1, GoToLineHelper.parseLineNumber("-3"));
    }

    @Test
    public void parse_trimsAndKeepsValue() {
        assertEquals(42, GoToLineHelper.parseLineNumber(" 42 "));
        assertEquals(3, GoToLineHelper.parseLineNumber("003"));
    }

    // ── lineStartOffset ──────────────────────────────────────────────────────

    @Test
    public void lineStart_nullText() {
        assertEquals(0, GoToLineHelper.lineStartOffset(null, 3));
    }

    @Test
    public void lineStart_emptyText() {
        // 空串=1 行（LineNumberHelper 语义）；第 1 行首=0；越界=length=0
        assertEquals(0, GoToLineHelper.lineStartOffset("", 1));
        assertEquals(0, GoToLineHelper.lineStartOffset("", 5));
    }

    @Test
    public void lineStart_regular() {
        String t = "line1\nline2\nline3";
        assertEquals(0, GoToLineHelper.lineStartOffset(t, 1));
        assertEquals(6, GoToLineHelper.lineStartOffset(t, 2));
        assertEquals(12, GoToLineHelper.lineStartOffset(t, 3));
    }

    @Test
    public void lineStart_beyondTotalLinesGoesToEnd() {
        // markor 同型：越界返回 length（去末尾），非 clamp 到末行
        String t = "line1\nline2\nline3";
        assertEquals(17, GoToLineHelper.lineStartOffset(t, 4));
        assertEquals(17, GoToLineHelper.lineStartOffset(t, 100));
    }

    @Test
    public void lineStart_emptyLineInMiddle() {
        String t = "a\n\nb";
        assertEquals(0, GoToLineHelper.lineStartOffset(t, 1));
        assertEquals(2, GoToLineHelper.lineStartOffset(t, 2));
        assertEquals(3, GoToLineHelper.lineStartOffset(t, 3));
        assertEquals(4, GoToLineHelper.lineStartOffset(t, 4)); // 越界→length
    }

    @Test
    public void lineStart_leadingNewline() {
        String t = "\nabc";
        assertEquals(0, GoToLineHelper.lineStartOffset(t, 1));
        assertEquals(1, GoToLineHelper.lineStartOffset(t, 2));
        assertEquals(4, GoToLineHelper.lineStartOffset(t, 3)); // 越界→length
    }

    @Test
    public void lineStart_trailingNewlineAddsEmptyLastLine() {
        String t = "a\n";
        assertEquals(0, GoToLineHelper.lineStartOffset(t, 1));
        assertEquals(2, GoToLineHelper.lineStartOffset(t, 2)); // 末尾空行
        assertEquals(2, GoToLineHelper.lineStartOffset(t, 3)); // 越界→length
    }

    @Test
    public void lineStart_crlfCountsByLf() {
        String t = "a\r\nb\r\nc";
        // CRLF 按 LF 计（LineNumberHelper 同型）；\r 留在行尾不影响行首
        assertEquals(0, GoToLineHelper.lineStartOffset(t, 1));
        assertEquals(3, GoToLineHelper.lineStartOffset(t, 2));
        assertEquals(6, GoToLineHelper.lineStartOffset(t, 3));
        assertEquals(7, GoToLineHelper.lineStartOffset(t, 4)); // 越界→length=7
    }

    // ── lineHighlightRange ───────────────────────────────────────────────────

    @Test
    public void highlight_regularLinesExcludeNewline() {
        String t = "line1\nline2\nline3";
        assertArrayEquals(new int[]{0, 5}, GoToLineHelper.lineHighlightRange(t, 1));
        assertArrayEquals(new int[]{6, 11}, GoToLineHelper.lineHighlightRange(t, 2));
        // 最后一行：行尾=length
        assertArrayEquals(new int[]{12, 17}, GoToLineHelper.lineHighlightRange(t, 3));
    }

    @Test
    public void highlight_emptyLineIsEmptyRange() {
        String t = "a\n\nb";
        assertArrayEquals(new int[]{2, 2}, GoToLineHelper.lineHighlightRange(t, 2));
    }

    @Test
    public void highlight_lastLineWithoutTrailingNewline() {
        String t = "a\nb";
        assertArrayEquals(new int[]{0, 1}, GoToLineHelper.lineHighlightRange(t, 1));
        assertArrayEquals(new int[]{2, 3}, GoToLineHelper.lineHighlightRange(t, 2)); // "b"
    }

    @Test
    public void highlight_beyondLastLineIsEmptyAtEnd() {
        String t = "a\nb";
        assertArrayEquals(new int[]{3, 3}, GoToLineHelper.lineHighlightRange(t, 9));
    }

    @Test
    public void highlight_trailingNewlineLastEmptyLine() {
        String t = "a\n";
        assertArrayEquals(new int[]{0, 1}, GoToLineHelper.lineHighlightRange(t, 1));
        assertArrayEquals(new int[]{2, 2}, GoToLineHelper.lineHighlightRange(t, 2));
    }

    @Test
    public void highlight_nullText() {
        assertArrayEquals(new int[]{0, 0}, GoToLineHelper.lineHighlightRange(null, 1));
    }

    // ── 语义对拍：lineStartOffset 与 LineNumberHelper.countLines 同源 ─────────

    @Test
    public void consistencyWithCountLines() {
        // 任意文本：第 N 行（N<=countLines）行首偏移 <= length，且 N 增大偏移单调不减
        String[] samples = {"", "abc", "a\n", "\n", "a\n\nb\n", "x\ny\nz"};
        for (String s : samples) {
            int total = LineNumberHelper.countLines(s);
            int prev = -1;
            for (int n = 1; n <= total; n++) {
                int off = GoToLineHelper.lineStartOffset(s, n);
                if (off >= prev) {
                    prev = off;
                } else {
                    throw new AssertionError("offset regressed for " + s + " @" + n + ": " + off);
                }
                if (off < 0 || off > s.length()) {
                    throw new AssertionError("offset out of range for " + s + " @" + n);
                }
            }
            // 越界返回 length
            assertEquals(s.length(), GoToLineHelper.lineStartOffset(s, total + 1));
        }
    }
}
