package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * CsvGoToLineHelper 单测：行号→行索引映射（1-based 语义、边界、越界、空表）。
 */
public class CsvGoToLineHelperTest {

    @Test
    public void normalLineMapsToZeroBasedIndex() {
        assertEquals(0, CsvGoToLineHelper.rowIndexFor(1, 10));
        assertEquals(4, CsvGoToLineHelper.rowIndexFor(5, 10));
        assertEquals(9, CsvGoToLineHelper.rowIndexFor(10, 10));
    }

    @Test
    public void lineBelowOneMapsToFirstRow() {
        assertEquals(0, CsvGoToLineHelper.rowIndexFor(0, 10));
        assertEquals(0, CsvGoToLineHelper.rowIndexFor(-3, 10));
    }

    @Test
    public void lineBeyondTotalMapsToLastRow() {
        assertEquals(9, CsvGoToLineHelper.rowIndexFor(11, 10));
        assertEquals(1, CsvGoToLineHelper.rowIndexFor(500, 2));
    }

    @Test
    public void emptyTableReturnsMinusOne() {
        assertEquals(-1, CsvGoToLineHelper.rowIndexFor(1, 0));
        assertEquals(-1, CsvGoToLineHelper.rowIndexFor(100, 0));
        assertEquals(-1, CsvGoToLineHelper.rowIndexFor(-1, 0));
    }

    @Test
    public void singleRowTable() {
        assertEquals(0, CsvGoToLineHelper.rowIndexFor(1, 1));
        assertEquals(0, CsvGoToLineHelper.rowIndexFor(2, 1));
        assertEquals(0, CsvGoToLineHelper.rowIndexFor(0, 1));
    }
}
