package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.RecentFileEntry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JVM unit tests for {@link RecentFilesHelper}.
 * Pure list logic — no Android dependencies.
 */
public class RecentFilesHelperTest {

    private static RecentFileEntry entry(String path, long ts) {
        return new RecentFileEntry(path, ts);
    }

    // ── record ─────────────────────────────────────────────────────────────

    @Test
    public void recordIntoEmptyList() {
        List<RecentFileEntry> result = RecentFilesHelper.record(
                new ArrayList<>(), "/a", 100L);
        assertEquals(1, result.size());
        assertEquals("/a", result.get(0).getPath());
        assertEquals(100L, result.get(0).getTs());
    }

    @Test
    public void recordNewPrependsAndKeepsOrder() {
        List<RecentFileEntry> result = RecentFilesHelper.record(
                Arrays.asList(entry("/b", 10L)), "/a", 20L);
        assertEquals(Arrays.asList("/a", "/b"), paths(result));
        assertEquals(20L, result.get(0).getTs());
    }

    @Test
    public void recordDuplicateMovesToFrontAndRefreshesTs() {
        List<RecentFileEntry> result = RecentFilesHelper.record(
                Arrays.asList(entry("/b", 10L), entry("/a", 11L), entry("/c", 12L)),
                "/a", 99L);
        assertEquals(Arrays.asList("/a", "/b", "/c"), paths(result));
        assertEquals(99L, result.get(0).getTs());
    }

    @Test
    public void recordNormalizesPathBeforeCompare() {
        // 入参 "/a/b/" 与既有 "/a/b" 规范化为同一条目
        List<RecentFileEntry> result = RecentFilesHelper.record(
                Arrays.asList(entry("/a/b", 10L)), "/a/b/", 20L);
        assertEquals(1, result.size());
        assertEquals("/a/b", result.get(0).getPath());
        assertEquals(20L, result.get(0).getTs());
    }

    @Test
    public void recordInvalidPathReturnsCopyWithoutAdding() {
        List<RecentFileEntry> before = Arrays.asList(entry("/a", 10L));
        List<RecentFileEntry> result = RecentFilesHelper.record(before, null, 20L);
        assertEquals(before, result);
        result = RecentFilesHelper.record(before, "   ", 20L);
        assertEquals(before, result);
    }

    @Test
    public void recordNullEntriesHandled() {
        List<RecentFileEntry> result = RecentFilesHelper.record(null, "/x", 1L);
        assertEquals(Arrays.asList("/x"), paths(result));
    }

    @Test
    public void recordTrimsToMaxRecentDroppingOldest() {
        List<RecentFileEntry> list = new ArrayList<>();
        // 塞满 MAX_RECENT 条，最旧为 /oldest
        for (int i = RecentFilesHelper.MAX_RECENT - 1; i >= 0; i--) {
            list.add(entry("/f" + i, (long) i));
        }
        List<RecentFileEntry> result = RecentFilesHelper.record(list, "/new", 999L);
        assertEquals(RecentFilesHelper.MAX_RECENT, result.size());
        assertEquals("/new", result.get(0).getPath());
        // 移除最旧 /f0 后，末尾为 /f1
        assertEquals("/f1", result.get(result.size() - 1).getPath());
        // 最旧条目 /f0 被丢弃
        for (RecentFileEntry e : result) {
            assertTrue(!e.getPath().equals("/f0"));
        }
    }

    // ── remove ─────────────────────────────────────────────────────────────

    @Test
    public void removeExistingPath() {
        List<RecentFileEntry> result = RecentFilesHelper.remove(
                Arrays.asList(entry("/a", 1L), entry("/b", 2L)), "/a");
        assertEquals(Arrays.asList("/b"), paths(result));
    }

    @Test
    public void removeNormalizesBeforeCompare() {
        List<RecentFileEntry> result = RecentFilesHelper.remove(
                Arrays.asList(entry("/a", 1L)), "/A/".replace("A", "a") + "/");
        assertTrue(result.isEmpty());
    }

    @Test
    public void removeMissingReturnsEquivalentCopy() {
        List<RecentFileEntry> before = Arrays.asList(entry("/a", 1L));
        List<RecentFileEntry> result = RecentFilesHelper.remove(before, "/zzz");
        assertEquals(before, result);
    }

    @Test
    public void removeNullEntriesReturnsEmpty() {
        assertTrue(RecentFilesHelper.remove(null, "/a").isEmpty());
    }

    // ── 帮助 ────────────────────────────────────────────────────────────────

    private static List<String> paths(List<RecentFileEntry> entries) {
        List<String> out = new ArrayList<>();
        for (RecentFileEntry e : entries) {
            out.add(e.getPath());
        }
        return out;
    }
}
