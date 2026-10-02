package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

/** PreviewPaneHelper 纯函数单测：CSV 预览对齐与解析（迭代24 从 FileBrowserActivity 抽取，行为锁定）。 */
public class PreviewPaneHelperTest {

    @Test
    public void parseSimpleFields() {
        assertEquals(Arrays.asList("a", "b", "c"), PreviewPaneHelper.parseCsvLine("a,b,c"));
    }

    @Test
    public void parseQuotedComma() {
        assertEquals(Arrays.asList("a,b", "c"), PreviewPaneHelper.parseCsvLine("\"a,b\",c"));
    }

    @Test
    public void parseEscapedQuote() {
        assertEquals(Arrays.asList("he said \"hi\"", "x"),
                PreviewPaneHelper.parseCsvLine("\"he said \"\"hi\"\"\",x"));
    }

    @Test
    public void parseTrailingEmptyField() {
        assertEquals(Arrays.asList("a", ""), PreviewPaneHelper.parseCsvLine("a,"));
    }

    @Test
    public void parseEmptyLine() {
        assertEquals(Arrays.asList(""), PreviewPaneHelper.parseCsvLine(""));
    }

    @Test
    public void parseKeepsLeadingTrailingSpaces() {
        assertEquals(Arrays.asList("a ", " b"), PreviewPaneHelper.parseCsvLine("a , b"));
    }

    @Test
    public void formatAlignedTableWithHeaderSeparator() {
        assertEquals("name   age\n------------\nalice  20\n",
                PreviewPaneHelper.formatCsvPreview("name,age\nalice,20"));
    }

    @Test
    public void formatSkipsBlankLinesAndPadsToLongestCell() {
        assertEquals("a  b\n------\nc  d\n",
                PreviewPaneHelper.formatCsvPreview("a,b\n\nc,d"));
    }

    @Test
    public void formatEmptyContentReturnsEmpty() {
        assertEquals("", PreviewPaneHelper.formatCsvPreview(""));
    }

    @Test
    public void formatQuotedCellKeepsComma() {
        assertEquals("a,b  c\n--------\n",
                PreviewPaneHelper.formatCsvPreview("\"a,b\",c"));
    }
}
