package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.AnnotationEntry;

import org.junit.Test;

import java.util.List;

/**
 * JVM unit tests for {@link AnnotationHelper}.
 * Pure string logic — no Android dependencies.
 */
public class AnnotationHelperTest {

    // ── buildAnnotationFilePath ────────────────────────────────────────────

    @Test
    public void buildPathWithDirectory() {
        assertEquals("/sdcard/notes/读书笔记_批注.csv",
                AnnotationHelper.buildAnnotationFilePath("/sdcard/notes/读书笔记.md"));
    }

    @Test
    public void buildPathWithoutDirectory() {
        assertEquals("README_批注.csv",
                AnnotationHelper.buildAnnotationFilePath("README.md"));
    }

    @Test
    public void buildPathWithoutExtension() {
        assertEquals("/data/note_批注.csv",
                AnnotationHelper.buildAnnotationFilePath("/data/note"));
    }

    @Test
    public void buildPathWithWindowsSeparator() {
        assertEquals("C:\\docs\\plan_批注.csv",
                AnnotationHelper.buildAnnotationFilePath("C:\\docs\\plan.md"));
    }

    @Test
    public void buildPathMonolithicHidden() {
        // ".hidden" → empty base name, only the suffix remains
        assertEquals("/x/_批注.csv",
                AnnotationHelper.buildAnnotationFilePath("/x/.hidden"));
    }

    // ── countOccurrencesBefore ─────────────────────────────────────────────

    @Test
    public void countBeforeSimple() {
        assertEquals(2, AnnotationHelper.countOccurrencesBefore("ababab", "ab", 4));
    }

    @Test
    public void countBeforeBoundaryIsExclusive() {
        // 2nd occurrence starts exactly at beforeIndex (=3) → not counted
        assertEquals(1, AnnotationHelper.countOccurrencesBefore("abcabc", "a", 3));
    }

    @Test
    public void countBeforeBeyondLength() {
        assertEquals(3, AnnotationHelper.countOccurrencesBefore("ababab", "ab", 99));
    }

    @Test
    public void countBeforeEmptyNeedle() {
        assertEquals(0, AnnotationHelper.countOccurrencesBefore("abc", "", 3));
    }

    @Test
    public void countBeforeNegativeIndex() {
        assertEquals(0, AnnotationHelper.countOccurrencesBefore("abc", "b", -5));
    }

    @Test
    public void countBeforeNoMatch() {
        assertEquals(0, AnnotationHelper.countOccurrencesBefore("abc", "z", 3));
    }

    // ── findNthOccurrence ──────────────────────────────────────────────────

    @Test
    public void findNthFirst() {
        assertEquals(1, AnnotationHelper.findNthOccurrence("abcabcabc", "bc", 0));
    }

    @Test
    public void findNthLater() {
        assertEquals(4, AnnotationHelper.findNthOccurrence("abcabcabc", "bc", 1));
    }

    @Test
    public void findNthOutOfRange() {
        assertEquals(-1, AnnotationHelper.findNthOccurrence("abcabcabc", "bc", 5));
    }

    @Test
    public void findNthEmptyNeedle() {
        assertEquals(-1, AnnotationHelper.findNthOccurrence("abc", "", 0));
    }

    @Test
    public void findNthNegativeN() {
        assertEquals(-1, AnnotationHelper.findNthOccurrence("abc", "a", -1));
    }

    // ── parseAnnotationFile ─────────────────────────────────────────────────

    @Test
    public void parseSkipsHeaderAndParsesRows() {
        String content = "\"id\",\"批注内容\",\"原文片段\",\"出现序号\"\n"
                + "\"a1\",\"批注1\",\"原文1\",2\n";
        List<AnnotationEntry> list = AnnotationHelper.parseAnnotationFile(content);
        assertEquals(1, list.size());
        assertEquals("a1", list.get(0).id);
        assertEquals("批注1", list.get(0).text);
        assertEquals("原文1", list.get(0).originalText);
        assertEquals(2, list.get(0).occurrenceIndex);
    }

    @Test
    public void parseLegacyThreeColumnsDefaultsIndexZero() {
        String content = "\"a2\",\"批注2\",\"原文2\"\n";
        List<AnnotationEntry> list = AnnotationHelper.parseAnnotationFile(content);
        assertEquals(1, list.size());
        assertEquals(0, list.get(0).occurrenceIndex);
    }

    @Test
    public void parseSkipsLegacyLineColAndMalformedRows() {
        String content = "L3:5-12\n"
                + "\"\",\"text\",\"orig\",0\n"
                + "not-a-row\n";
        assertTrue(AnnotationHelper.parseAnnotationFile(content).isEmpty());
    }

    @Test
    public void parseSkipsBlankLines() {
        String content = "\n\n\"a3\",\"x\",\"y\",0\n\n";
        assertEquals(1, AnnotationHelper.parseAnnotationFile(content).size());
    }

    @Test
    public void parseHandlesQuotedCommas() {
        String content = "\"a4\",\"含,逗号\",\"原文\",0\n";
        List<AnnotationEntry> list = AnnotationHelper.parseAnnotationFile(content);
        assertEquals(1, list.size());
        assertEquals("含,逗号", list.get(0).text);
    }

    // ── formatAnnotationFile + round-trip ──────────────────────────────────

    @Test
    public void formatStartsWithHeader() {
        String csv = AnnotationHelper.formatAnnotationFile(
                new java.util.ArrayList<>());
        assertTrue(csv.startsWith(AnnotationHelper.CSV_HEADER + "\n"));
    }

    @Test
    public void roundTripPreservesEntries() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(new AnnotationEntry("a1", "批注,含逗号", "\"原文'", 1));
        entries.add(new AnnotationEntry("a2", "换行\n测试", "原文2", 0));

        String csv = AnnotationHelper.formatAnnotationFile(entries);
        List<AnnotationEntry> parsed = AnnotationHelper.parseAnnotationFile(csv);

        assertEquals(2, parsed.size());
        assertEquals("a1", parsed.get(0).id);
        assertEquals("批注,含逗号", parsed.get(0).text);
        assertEquals("\"原文'", parsed.get(0).originalText);
        assertEquals(1, parsed.get(0).occurrenceIndex);
        assertEquals("换行\n测试", parsed.get(1).text);
    }

    // ── buildMarkdownFilePath ──────────────────────────────────────────────

    @Test
    public void buildMarkdownPathReversesSuffix() {
        assertEquals("/sdcard/notes/读书笔记.md",
                AnnotationHelper.buildMarkdownFilePath("/sdcard/notes/读书笔记_批注.csv"));
    }

    @Test
    public void buildMarkdownPathNoSuffixPassthrough() {
        assertEquals("unknown.csv",
                AnnotationHelper.buildMarkdownFilePath("unknown.csv"));
    }

    // ── buildExportText: TEXT ───────────────────────────────────────────────

    @Test
    public void exportTextFormatBasicWithSource() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(new AnnotationEntry("a1", "这是批注", "这是原文", 0));
        entries.add(new AnnotationEntry("a2", "批注2", "原文2", 2));

        String out = AnnotationHelper.buildExportText(
                entries, "/notes/a.md", AnnotationHelper.ExportFormat.TEXT);

        String expected = "批注导出（共 2 条）\n"
                + "来源：/notes/a.md\n"
                + "================================\n"
                + "\n[1] 原文（第 1 次出现）：「这是原文」\n"
                + "    批注：「这是批注」\n"
                + "\n[2] 原文（第 3 次出现）：「原文2」\n"
                + "    批注：「批注2」\n";
        assertEquals(expected, out);
    }

    @Test
    public void exportTextFormatNoSourceOmitsSourceLine() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(new AnnotationEntry("a1", "批注1", "原文1", 0));

        String out = AnnotationHelper.buildExportText(
                entries, AnnotationHelper.ExportFormat.TEXT);

        String expected = "批注导出（共 1 条）\n"
                + "================================\n"
                + "\n[1] 原文（第 1 次出现）：「原文1」\n"
                + "    批注：「批注1」\n";
        assertEquals(expected, out);
    }

    @Test
    public void exportTextEmptyListStillHasHeader() {
        String out = AnnotationHelper.buildExportText(
                new java.util.ArrayList<>(), AnnotationHelper.ExportFormat.TEXT);
        assertEquals("批注导出（共 0 条）\n"
                + "================================\n", out);
    }

    @Test
    public void exportTextNormalizesNewlines() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(new AnnotationEntry("a1", "第一行\n第二行", "line1\r\nline2", 0));

        String out = AnnotationHelper.buildExportText(
                entries, AnnotationHelper.ExportFormat.TEXT);

        assertTrue(out.contains("原文（第 1 次出现）：「line1\\nline2」"));
        assertTrue(out.contains("批注：「第一行\\n第二行」"));
    }

    // ── buildExportText: HTML ───────────────────────────────────────────────

    @Test
    public void exportHtmlFormatEmitsBlockquotes() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(new AnnotationEntry("a1", "批注1", "原文1", 0));
        entries.add(new AnnotationEntry("a2", "批注2", "原文2", 1));

        String out = AnnotationHelper.buildExportText(
                entries, "/notes/a.md", AnnotationHelper.ExportFormat.HTML);

        String expected = "<!-- 批注导出（共 2 条）；来源：/notes/a.md -->\n"
                + "<blockquote>\n"
                + "<p><strong>批注 1</strong>：批注1</p>\n"
                + "<p>原文（第 1 次出现）：原文1</p>\n"
                + "</blockquote>\n"
                + "<blockquote>\n"
                + "<p><strong>批注 2</strong>：批注2</p>\n"
                + "<p>原文（第 2 次出现）：原文2</p>\n"
                + "</blockquote>\n";
        assertEquals(expected, out);
    }

    @Test
    public void exportHtmlFormatEscapesSpecialChars() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(new AnnotationEntry("a1", "<b>&\"x\"</b>", "1<2&3", 0));

        String out = AnnotationHelper.buildExportText(
                entries, AnnotationHelper.ExportFormat.HTML);

        String expected = "<!-- 批注导出（共 1 条） -->\n"
                + "<blockquote>\n"
                + "<p><strong>批注 1</strong>：&lt;b&gt;&amp;&quot;x&quot;&lt;/b&gt;</p>\n"
                + "<p>原文（第 1 次出现）：1&lt;2&amp;3</p>\n"
                + "</blockquote>\n";
        assertEquals(expected, out);
    }

    // ── buildExportText: MARKDOWN ───────────────────────────────────────────

    @Test
    public void exportMarkdownFormatAppendsNotes() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(new AnnotationEntry("a1", "批注1", "原文1", 0));
        entries.add(new AnnotationEntry("a2", "批注2", "原文2", 2));

        String out = AnnotationHelper.buildExportText(
                entries, "/notes/a.md", AnnotationHelper.ExportFormat.MARKDOWN);

        String expected = "> **批注于 /notes/a.md**：原文「原文1」 → 批注「批注1」\n"
                + "> **批注于 /notes/a.md**：原文「原文2」 → 批注「批注2」\n";
        assertEquals(expected, out);
    }

    @Test
    public void exportMarkdownFormatWithoutSource() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(new AnnotationEntry("a1", "批注1", "原文1", 0));

        String out = AnnotationHelper.buildExportText(
                entries, AnnotationHelper.ExportFormat.MARKDOWN);

        assertEquals("> **批注**：原文「原文1」 → 批注「批注1」\n", out);
    }

    // ── Locatability（编辑后批注重定位：失效判定）──────────────────────────────
    // 素材：TEXT 中 "aaa" 出现于 0、8 两处，"bbb" 于 4，"ccc" 于 12。

    private static final String TEXT = "aaa bbb aaa ccc";

    private static AnnotationEntry entry(String id, String orig, int occurrence) {
        return new AnnotationEntry(id, "注释", orig, occurrence);
    }

    @Test
    public void isFindableExactHit() {
        assertTrue(AnnotationHelper.isFindable(entry("a1", "aaa", 0), TEXT));
        assertTrue(AnnotationHelper.isFindable(entry("a2", "bbb", 0), TEXT));
    }

    @Test
    public void isFindableOccurrenceIndexOutOfRange() {
        // "aaa" 仅出现 2 次（index 0/1），request occurrence 2 定位不到
        assertFalse(AnnotationHelper.isFindable(entry("a3", "aaa", 2), TEXT));
    }

    @Test
    public void isFindableNotFound() {
        assertFalse(AnnotationHelper.isFindable(entry("a4", "zzz", 0), TEXT));
    }

    @Test
    public void isFindableEmptyOriginal() {
        assertFalse(AnnotationHelper.isFindable(entry("a5", "", 0), TEXT));
    }

    @Test
    public void isFindableNullEntryOrNullText() {
        assertFalse(AnnotationHelper.isFindable(null, TEXT));
        assertFalse(AnnotationHelper.isFindable(entry("a1", "aaa", 0), null));
        assertFalse(AnnotationHelper.isFindable(entry("a1", "aaa", 0), ""));
    }

    @Test
    public void isFindableWholeText() {
        // 原文等于整个文本（恰好落在末尾）→ 可定位
        assertTrue(AnnotationHelper.isFindable(entry("a6", TEXT, 0), TEXT));
    }

    @Test
    public void countNonFindableSome() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(entry("a1", "aaa", 0));
        entries.add(entry("a3", "aaa", 2));   // 失效
        entries.add(entry("a2", "bbb", 0));
        assertEquals(1, AnnotationHelper.countNonFindable(entries, TEXT));
    }

    @Test
    public void countNonFindableNone() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(entry("a1", "aaa", 0));
        entries.add(entry("a2", "bbb", 0));
        assertEquals(0, AnnotationHelper.countNonFindable(entries, TEXT));
    }

    @Test
    public void countNonFindableNullInputs() {
        assertEquals(0, AnnotationHelper.countNonFindable(null, TEXT));
        assertEquals(0, AnnotationHelper.countNonFindable(
                new java.util.ArrayList<>(), TEXT));
        assertEquals(0, AnnotationHelper.countNonFindable(
                new java.util.ArrayList<>(), null));
    }

    @Test
    public void nonFindableIdsCollectsOnlyFailed() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(entry("a1", "aaa", 0));
        entries.add(entry("a3", "aaa", 2));   // 失效
        entries.add(entry("a2", "bbb", 0));
        java.util.Set<String> failed = AnnotationHelper.nonFindableIds(entries, TEXT);
        assertEquals(1, failed.size());
        assertTrue(failed.contains("a3"));
        assertFalse(failed.contains("a1"));
        assertFalse(failed.contains("a2"));
    }

    @Test
    public void nonFindableIdsEmptyWhenAllFindable() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(entry("a1", "aaa", 0));
        entries.add(entry("a2", "bbb", 0));
        assertTrue(AnnotationHelper.nonFindableIds(entries, TEXT).isEmpty());
        assertTrue(AnnotationHelper.nonFindableIds(null, TEXT).isEmpty());
    }

    @Test
    public void drawerStatusesSameOrderMarksFailed() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(entry("a1", "aaa", 0));
        entries.add(entry("a3", "aaa", 2));   // 失效
        entries.add(entry("a2", "bbb", 0));
        entries.add(entry("a4", "ccc", 0));

        List<AnnotationHelper.AnnotationStatus> statuses =
                AnnotationHelper.drawerStatuses(entries, TEXT);

        assertEquals(4, statuses.size());
        assertEquals(AnnotationHelper.AnnotationStatus.OK, statuses.get(0));
        assertEquals(AnnotationHelper.AnnotationStatus.FAILED, statuses.get(1));
        assertEquals(AnnotationHelper.AnnotationStatus.OK, statuses.get(2));
        assertEquals(AnnotationHelper.AnnotationStatus.OK, statuses.get(3));
    }

    @Test
    public void drawerStatusesEmptyOriginalAsFailed() {
        List<AnnotationEntry> entries = new java.util.ArrayList<>();
        entries.add(entry("a5", "", 0));

        List<AnnotationHelper.AnnotationStatus> statuses =
                AnnotationHelper.drawerStatuses(entries, TEXT);

        assertEquals(1, statuses.size());
        assertEquals(AnnotationHelper.AnnotationStatus.FAILED, statuses.get(0));
    }

    @Test
    public void drawerStatusesEmptyAndNullInputs() {
        assertTrue(AnnotationHelper.drawerStatuses(null, TEXT).isEmpty());
        assertTrue(AnnotationHelper.drawerStatuses(
                new java.util.ArrayList<>(), TEXT).isEmpty());
    }
}
