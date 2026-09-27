package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
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
}
