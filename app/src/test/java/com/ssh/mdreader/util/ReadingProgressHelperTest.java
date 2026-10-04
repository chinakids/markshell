package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JVM unit tests for {@link ReadingProgressHelper}.
 * Pure key/clamp/upsert logic — no Android dependencies.
 */
public class ReadingProgressHelperTest {

    // ── key ────────────────────────────────────────────────────────────────

    @Test
    public void keyIsStableAndDeterministic() {
        String k1 = ReadingProgressHelper.key("192.168.0.1", 22, "root", "/var/log/app.log");
        String k2 = ReadingProgressHelper.key("192.168.0.1", 22, "root", "/var/log/app.log");
        assertEquals(k1, k2);
        assertEquals("192.168.0.1:22@root|/var/log/app.log", k1);
    }

    @Test
    public void keyDistinguishesServerAndPath() {
        String base = "h", path = "/a";
        assertNotEquals(
                ReadingProgressHelper.key("h1", 22, "u", path),
                ReadingProgressHelper.key("h2", 22, "u", path));
        assertNotEquals(
                ReadingProgressHelper.key(base, 2222, "u", path),
                ReadingProgressHelper.key(base, 2223, "u", path));
        assertNotEquals(
                ReadingProgressHelper.key(base, 22, "u1", path),
                ReadingProgressHelper.key(base, 22, "u2", path));
        assertNotEquals(
                ReadingProgressHelper.key(base, 22, "u", "/a"),
                ReadingProgressHelper.key(base, 22, "u", "/b"));
    }

    @Test
    public void keyNormalizesPathLikeBookmarks() {
        assertEquals(
                ReadingProgressHelper.key("h", 22, "u", "/a/b"),
                ReadingProgressHelper.key("h", 22, "u", " /a\\b// "));
    }

    @Test
    public void keyToleratesNulls() {
        assertEquals(":22@|", ReadingProgressHelper.key(null, 22, null, null));
        // 根路径 "/" 被 normalizePath 保留（单斜杠合法路径，与空串路径区分）
        assertEquals(":22@|/", ReadingProgressHelper.key(null, 22, null, "/"));
    }

    // ── get ────────────────────────────────────────────────────────────────

    @Test
    public void getAbsentOrNullReturnsZero() {
        assertEquals(0, ReadingProgressHelper.get(null, "k"));
        assertEquals(0, ReadingProgressHelper.get(new LinkedHashMap<>(), "k"));
    }

    @Test
    public void getReturnsStoredValue() {
        Map<String, Integer> m = ReadingProgressHelper.upsert(null, "k", 42);
        assertEquals(42, ReadingProgressHelper.get(m, "k"));
    }

    // ── upsert ─────────────────────────────────────────────────────────────

    @Test
    public void upsertInsertsNewEntry() {
        Map<String, Integer> m = ReadingProgressHelper.upsert(null, "k", 10);
        assertEquals(1, m.size());
        assertEquals(Integer.valueOf(10), m.get("k"));
    }

    @Test
    public void upsertIsPureDoesNotMutateInput() {
        Map<String, Integer> input = new LinkedHashMap<>();
        input.put("a", 1);
        Map<String, Integer> result = ReadingProgressHelper.upsert(input, "b", 2);
        assertEquals(Integer.valueOf(1), input.get("a"));
        assertFalse(input.containsKey("b"));
        assertEquals(2, result.size());
    }

    @Test
    public void upsertUpdateRefreshesValueAndMovesToEnd() {
        Map<String, Integer> m = ReadingProgressHelper.upsert(null, "a", 1);
        m = ReadingProgressHelper.upsert(m, "b", 2);
        m = ReadingProgressHelper.upsert(m, "a", 3);
        assertEquals(2, m.size());
        assertEquals(Integer.valueOf(3), m.get("a"));
        String last = m.keySet().iterator().next(); // 队首
        assertEquals("b", last);                    // a 被重插到队尾，b 成为最旧
    }

    @Test
    public void upsertZeroRemovesEntry() {
        Map<String, Integer> m = ReadingProgressHelper.upsert(null, "a", 1);
        m = ReadingProgressHelper.upsert(m, "a", 0);
        assertEquals(0, m.size());
        assertEquals(0, ReadingProgressHelper.get(m, "a"));
    }

    @Test
    public void upsertNegativeRemovesEntry() {
        Map<String, Integer> m = ReadingProgressHelper.upsert(null, "a", 1);
        m = ReadingProgressHelper.upsert(m, "a", -5);
        assertEquals(0, m.size());
    }

    @Test
    public void upsertCapsAndDropsOldest() {
        Map<String, Integer> m = null;
        for (int i = 1; i <= ReadingProgressHelper.MAX_ENTRIES + 1; i++) {
            m = ReadingProgressHelper.upsert(m, "k" + i, i);
        }
        assertEquals(ReadingProgressHelper.MAX_ENTRIES, m.size());
        assertFalse(m.containsKey("k1"));        // 最先插入的已被丢弃
        assertTrue(m.containsKey("k" + (ReadingProgressHelper.MAX_ENTRIES + 1)));
    }

    @Test
    public void upsertUpdateDoesNotAddDuplicate() {
        Map<String, Integer> m = ReadingProgressHelper.upsert(null, "a", 1);
        m = ReadingProgressHelper.upsert(m, "a", 2);
        m = ReadingProgressHelper.upsert(m, "a", 3);
        assertEquals(1, m.size());
        assertEquals(Integer.valueOf(3), m.get("a"));
    }

    // ── clamp ──────────────────────────────────────────────────────────────

    @Test
    public void clampsNegativeToZero() {
        assertEquals(0, ReadingProgressHelper.clamp(-1, 100));
        assertEquals(0, ReadingProgressHelper.clamp(-999, 0));
    }

    @Test
    public void clampsAboveMax() {
        assertEquals(100, ReadingProgressHelper.clamp(200, 100));
        assertEquals(0, ReadingProgressHelper.clamp(5, 0));
    }

    @Test
    public void clampKeepsInRange() {
        assertEquals(42, ReadingProgressHelper.clamp(42, 100));
        assertEquals(100, ReadingProgressHelper.clamp(100, 100));
    }

    @Test
    public void clampHandlesNegativeMax() {
        assertEquals(0, ReadingProgressHelper.clamp(10, -1));
        assertEquals(0, ReadingProgressHelper.clamp(-10, -1));
    }

    // ── isRestorable ───────────────────────────────────────────────────────

    @Test
    public void restorableOnlyForPositive() {
        assertTrue(ReadingProgressHelper.isRestorable(1));
        assertFalse(ReadingProgressHelper.isRestorable(0));
        assertFalse(ReadingProgressHelper.isRestorable(-3));
    }

    @Test
    public void getReturnsZeroAfterZeroUpsert() {
        Map<String, Integer> m = ReadingProgressHelper.upsert(null, "k", 1);
        m = ReadingProgressHelper.upsert(m, "k", 0);
        assertEquals(0, ReadingProgressHelper.get(m, "k"));
    }
}
