package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * JVM unit tests for {@link LastBrowseHelper}.
 * Pure start-path resolution logic — no Android dependencies.
 */
public class LastBrowseHelperTest {

    // ── resolveStartPath ──────────────────────────────────────────────────

    @Test
    public void explicitPathWins() {
        assertEquals("/home/user/docs",
                LastBrowseHelper.resolveStartPath("/home/user/docs", "/data/last", "/home/user"));
    }

    @Test
    public void emptyExplicitFallsBackToLast() {
        assertEquals("/data/last",
                LastBrowseHelper.resolveStartPath("", "/data/last", "/home/user"));
    }

    @Test
    public void nullExplicitFallsBackToLast() {
        assertEquals("/data/last",
                LastBrowseHelper.resolveStartPath(null, "/data/last", "/home/user"));
    }

    @Test
    public void whitespaceExplicitTreatedAsAbsent() {
        assertEquals("/data/last",
                LastBrowseHelper.resolveStartPath("   ", "/data/last", "/home/user"));
    }

    @Test
    public void noExplicitOrLastFallsBackToDefault() {
        assertEquals("/home/user",
                LastBrowseHelper.resolveStartPath(null, null, "/home/user"));
    }

    @Test
    public void allAbsentReturnsEmpty() {
        assertEquals("", LastBrowseHelper.resolveStartPath(null, null, null));
    }

    @Test
    public void resultIsNormalized() {
        // 去结尾斜杠 + 折叠连续斜杠
        assertEquals("/data/last",
                LastBrowseHelper.resolveStartPath(null, "/data/last/", "/home/user"));
        assertEquals("/data/a/b",
                LastBrowseHelper.resolveStartPath(null, "/data//a/b///", "/home/user"));
    }

    @Test
    public void rootSlashIsPreserved() {
        assertEquals("/", LastBrowseHelper.resolveStartPath(null, "/", "/home/user"));
    }

    // ── normalizeForSave ──────────────────────────────────────────────────

    @Test
    public void normalizeForSaveNormalizes() {
        assertEquals("/data/last",
                LastBrowseHelper.normalizeForSave(" /data/last// "));
    }

    @Test
    public void normalizeForSaveInvalidReturnsEmpty() {
        assertEquals("", LastBrowseHelper.normalizeForSave(null));
        assertEquals("", LastBrowseHelper.normalizeForSave("   "));
    }
}
