package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * {@link LineNumberHelper} 纯函数层单测：行数计数（null/空/尾随换行/CRLF）、行号序列
 * 与位数计算。#18 行号列语义单一源——CodeViewer split("\n",-1) 语义对拍锁定。
 */
public class LineNumberHelperTest {

    // ── countLines ──────────────────────────────────────────────────────────

    @Test
    public void count_null_returnsZero() {
        assertEquals(0, LineNumberHelper.countLines(null));
    }

    @Test
    public void count_empty_returnsOne() {
        assertEquals(1, LineNumberHelper.countLines(""));
    }

    @Test
    public void count_singleLine_noNewline_returnsOne() {
        assertEquals(1, LineNumberHelper.countLines("abc"));
        assertEquals(1, LineNumberHelper.countLines("a"));
    }

    @Test
    public void count_multipleLines() {
        assertEquals(2, LineNumberHelper.countLines("a\nb"));
        assertEquals(3, LineNumberHelper.countLines("a\nb\nc"));
        assertEquals(4, LineNumberHelper.countLines("a\nb\nc\n"));
    }

    @Test
    public void count_trailingNewline_addsEmptyLastLine() {
        // split("\n",-1) 语义：尾随换行=末尾一个空行
        assertEquals(2, LineNumberHelper.countLines("a\n"));
        assertEquals(3, LineNumberHelper.countLines("\na\n"));
        assertEquals(2, LineNumberHelper.countLines("\n"));
    }

    @Test
    public void count_crlfCountedByLf() {
        // CRLF 行尾 \r 保留在行内容，行数按 LF 计
        assertEquals(2, LineNumberHelper.countLines("a\r\nb"));
        assertEquals(3, LineNumberHelper.countLines("a\r\nb\r\n"));
    }

    @Test
    public void count_matchesSplitMinusOneSemantics() {
        String[] samples = {"", "x", "x\n", "\n", "x\ny", "x\ny\n", "a\r\nb\r\n", "\n\n\n"};
        for (String s : samples) {
            assertEquals("sample=" + s.replace("\n", "\\n"), s.split("\n", -1).length, LineNumberHelper.countLines(s));
        }
    }

    // ── numberSequence ──────────────────────────────────────────────────────

    @Test
    public void sequence_nonPositive_returnsEmpty() {
        assertEquals("", LineNumberHelper.numberSequence(0));
        assertEquals("", LineNumberHelper.numberSequence(-3));
    }

    @Test
    public void sequence_countOne_returnsSingleNumberWithNewline() {
        assertEquals("1\n", LineNumberHelper.numberSequence(1));
    }

    @Test
    public void sequence_countThree_ascendingWithNewlinePerLine() {
        assertEquals("1\n2\n3\n", LineNumberHelper.numberSequence(3));
    }

    @Test
    public void sequence_largeCount_consistentDigitCount() {
        String seq = LineNumberHelper.numberSequence(1500);
        String[] lines = seq.split("\n", -1);
        assertEquals(1500, lines.length - 1); // 尾部有最后一个换行→split -1 多一个空段
        assertEquals("1", lines[0]);
        assertEquals("1500", lines[lines.length - 2]);
        assertTrue(seq.endsWith("\n"));
    }

    // ── digits ──────────────────────────────────────────────────────────────

    @Test
    public void digits_nonPositive_returnsOne() {
        assertEquals(1, LineNumberHelper.digits(0));
        assertEquals(1, LineNumberHelper.digits(-1));
    }

    @Test
    public void digits_decimalLengths() {
        assertEquals(1, LineNumberHelper.digits(1));
        assertEquals(1, LineNumberHelper.digits(9));
        assertEquals(2, LineNumberHelper.digits(10));
        assertEquals(2, LineNumberHelper.digits(99));
        assertEquals(3, LineNumberHelper.digits(100));
        assertEquals(3, LineNumberHelper.digits(999));
        assertEquals(4, LineNumberHelper.digits(1000));
        assertEquals(5, LineNumberHelper.digits(10000));
    }
}
