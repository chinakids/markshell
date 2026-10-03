package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** {@link FileFilterHelper} 纯函数层单测（JVM，无 Android 依赖）。 */
public class FileFilterHelperTest {

    // ── normalize / active ────────────────────────────────────────────────────

    @Test
    public void normalize_nullBecomesEmpty() {
        assertEquals("", FileFilterHelper.normalize(null));
    }

    @Test
    public void normalize_keepsAsIs_noTrim() {
        assertEquals(" README ", FileFilterHelper.normalize(" README "));
    }

    @Test
    public void active_emptyOrNullFalse() {
        assertFalse(FileFilterHelper.active(null));
        assertFalse(FileFilterHelper.active(""));
    }

    @Test
    public void active_whitespaceIsActive() {
        assertTrue(FileFilterHelper.active(" "));
    }

    // ── matchesName 基本语义 ──────────────────────────────────────────────────

    @Test
    public void matches_nullQueryPassAll() {
        assertTrue(FileFilterHelper.matchesName("anything.md", null));
        assertTrue(FileFilterHelper.matchesName("anything.md", ""));
    }

    @Test
    public void matches_nullNameOnlyRejectedWithActiveQuery() {
        // 空间/空查询=不过滤恒通过（null 名称也通过）；有生效查询时 null 名称不匹配
        assertTrue(FileFilterHelper.matchesName(null, ""));
        assertTrue(FileFilterHelper.matchesName(null, null));
        assertFalse(FileFilterHelper.matchesName(null, "x"));
    }

    @Test
    public void matches_substringHits() {
        assertTrue(FileFilterHelper.matchesName("README.md", "readme"));
        assertTrue(FileFilterHelper.matchesName("notes_today.md", "today"));
        assertTrue(FileFilterHelper.matchesName("a.md", "a"));
    }

    @Test
    public void matches_caseInsensitive() {
        assertTrue(FileFilterHelper.matchesName("ReadMe.MD", "README"));
        assertTrue(FileFilterHelper.matchesName("abc.md", "ABC"));
    }

    @Test
    public void matches_extensionIncluded() {
        assertTrue(FileFilterHelper.matchesName("chapter1.md", ".md"));
        assertTrue(FileFilterHelper.matchesName("data.csv", "CSV"));
    }

    @Test
    public void matches_noHit() {
        assertFalse(FileFilterHelper.matchesName("README.md", "log"));
        assertFalse(FileFilterHelper.matchesName("a.md", "z"));
    }

    @Test
    public void matches_needleLongerThanTextNoHit() {
        assertFalse(FileFilterHelper.matchesName("ab", "abc"));
    }

    @Test
    public void matches_fullEqualHits() {
        assertTrue(FileFilterHelper.matchesName("config.yml", "config.yml"));
    }

    @Test
    public void matches_emptyNeedleHits() {
        assertTrue(FileFilterHelper.matchesName("anything", ""));
    }

    @Test
    public void matches_chineseAndSpaces() {
        assertTrue(FileFilterHelper.matchesName("使用说明.md", "说明"));
        assertFalse(FileFilterHelper.matchesName("使用说明.md", "手册"));
        assertTrue(FileFilterHelper.matchesName("my notes.md", "NOTES"));
    }

    @Test
    public void matches_leadingTrailingSpaceLiteral() {
        // 查询串不 trim：首尾空格按字面参与匹配
        assertTrue(FileFilterHelper.matchesName("a b.md", "a "));
        assertFalse(FileFilterHelper.matchesName("ab.md", "a "));
    }

    // ── indexOfIgnoreCase 边界 ────────────────────────────────────────────────

    @Test
    public void indexOf_positions() {
        assertEquals(0, FileFilterHelper.indexOfIgnoreCase("abc", "a"));
        assertEquals(1, FileFilterHelper.indexOfIgnoreCase("abc", "B"));
        assertEquals(-1, FileFilterHelper.indexOfIgnoreCase("abc", "d"));
        assertEquals(2, FileFilterHelper.indexOfIgnoreCase("aaXaa", "x"));
    }

    @Test
    public void indexOf_overlappingOccurrences() {
        // 非重叠/重叠均只找第一个
        assertEquals(0, FileFilterHelper.indexOfIgnoreCase("aaaa", "aa"));
    }

    @Test
    public void indexOf_emptyQueryFound() {
        assertEquals(0, FileFilterHelper.indexOfIgnoreCase("abc", ""));
    }
}
