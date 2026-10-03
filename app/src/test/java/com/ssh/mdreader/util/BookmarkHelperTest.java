package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JVM unit tests for {@link BookmarkHelper}.
 * Pure string/list logic — no Android dependencies.
 */
public class BookmarkHelperTest {

    // ── normalizePath ─────────────────────────────────────────────────────

    @Test
    public void normalizeTrimsAndConvertsBackslash() {
        assertEquals("/home/user",
                BookmarkHelper.normalizePath("  /home\\user  "));
    }

    @Test
    public void normalizeCollapsesDuplicateSlashesAndTrailingSlash() {
        assertEquals("/a/b",
                BookmarkHelper.normalizePath("/a//b/"));
    }

    @Test
    public void normalizeKeepsRootSlash() {
        assertEquals("/", BookmarkHelper.normalizePath("///"));
        assertEquals("/", BookmarkHelper.normalizePath("/"));
    }

    @Test
    public void normalizeEmptyOrNullReturnsEmpty() {
        assertEquals("", BookmarkHelper.normalizePath(null));
        assertEquals("", BookmarkHelper.normalizePath(""));
        assertEquals("", BookmarkHelper.normalizePath("   "));
    }

    // ── addBookmark ───────────────────────────────────────────────────────

    @Test
    public void addToEmptyList() {
        List<String> result = BookmarkHelper.addBookmark(new ArrayList<>(), "/a");
        assertEquals(Arrays.asList("/a"), result);
    }

    @Test
    public void addNewPrependsAndKeepsOrder() {
        List<String> result = BookmarkHelper.addBookmark(
                Arrays.asList("/b"), "/a");
        assertEquals(Arrays.asList("/a", "/b"), result);
    }

    @Test
    public void addExistingMovesToFrontWithoutDuplicate() {
        List<String> result = BookmarkHelper.addBookmark(
                Arrays.asList("/a", "/b"), "/b");
        assertEquals(Arrays.asList("/b", "/a"), result);
    }

    @Test
    public void addNormalizesBeforeStore() {
        List<String> result = BookmarkHelper.addBookmark(
                null, " /home\\读书 笔记/ ");
        assertEquals(Arrays.asList("/home/读书 笔记"), result);
    }

    @Test
    public void addRespectsMaxLimitDropsOldest() {
        List<String> input = new ArrayList<>();
        for (int i = 1; i <= BookmarkHelper.MAX_BOOKMARKS; i++) {
            input.add("/dir" + i);
        }
        List<String> result = BookmarkHelper.addBookmark(input, "/new");
        assertEquals(BookmarkHelper.MAX_BOOKMARKS, result.size());
        assertEquals("/new", result.get(0));
        // 最旧的 /dir30 被丢弃，/dir1 保留
        assertEquals("/dir1", result.get(1));
        assertEquals("/dir29", result.get(BookmarkHelper.MAX_BOOKMARKS - 1));
        assertFalse(result.contains("/dir30"));
    }

    @Test
    public void addIgnoresEmptyPath() {
        List<String> input = Arrays.asList("/a");
        List<String> result = BookmarkHelper.addBookmark(input, "   ");
        assertEquals(Arrays.asList("/a"), result);
    }

    // ── removeBookmark ────────────────────────────────────────────────────

    @Test
    public void removeExistingDeletesAllMatches() {
        List<String> result = BookmarkHelper.removeBookmark(
                Arrays.asList("/a", "/b", "/a/"), "/a");
        assertEquals(Arrays.asList("/b"), result);
    }

    @Test
    public void removeIsCaseSensitive() {
        // Unix 路径大小写敏感：/A 不应匹配 /a
        List<String> result = BookmarkHelper.removeBookmark(
                Arrays.asList("/a", "/b"), "/A");
        assertEquals(Arrays.asList("/a", "/b"), result);
    }

    @Test
    public void removeMissingReturnsEquivalentCopy() {
        List<String> result = BookmarkHelper.removeBookmark(
                Arrays.asList("/a", "/b"), "/z");
        assertEquals(Arrays.asList("/a", "/b"), result);
    }

    @Test
    public void removeOnNullReturnsEmpty() {
        List<String> result = BookmarkHelper.removeBookmark(null, "/a");
        assertTrue(result.isEmpty());
    }

    // ── isBookmarked ──────────────────────────────────────────────────────

    @Test
    public void isBookmarkedMatchesNormalized() {
        List<String> bookmarks = Arrays.asList("/home/docs");
        assertTrue(BookmarkHelper.isBookmarked(bookmarks, "/home/docs/"));
        assertTrue(BookmarkHelper.isBookmarked(bookmarks, " /home\\docs "));
        assertFalse(BookmarkHelper.isBookmarked(bookmarks, "/home/other"));
        assertFalse(BookmarkHelper.isBookmarked(bookmarks, null));
        assertFalse(BookmarkHelper.isBookmarked(new ArrayList<>(), "/home/docs"));
    }

    // ── displayName ───────────────────────────────────────────────────────

    @Test
    public void displayNameLastSegmentRootAndPlain() {
        assertEquals("docs", BookmarkHelper.displayName("/home/user/docs"));
        assertEquals("/", BookmarkHelper.displayName("/"));
        assertEquals("plain", BookmarkHelper.displayName("plain"));
        assertEquals("", BookmarkHelper.displayName(null));
    }
}
