package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.TimeZone;

/**
 * JVM unit tests for {@link FileMetaHelper}.
 * Pure mtime formatting logic — no Android dependencies.
 * Golden values verified with `date -u -r <epoch>` (epoch 1727913600 = 2024-10-03 00:00:00 UTC).
 */
public class FileMetaHelperTest {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final TimeZone CST = TimeZone.getTimeZone("Asia/Shanghai");

    // ── formatMtime(long, TimeZone) ─────────────────────────────────────────

    @Test
    public void zeroMtimeIsUnknown() {
        assertEquals(FileMetaHelper.UNKNOWN_TIME, FileMetaHelper.formatMtime(0L, UTC));
    }

    @Test
    public void negativeMtimeIsUnknown() {
        assertEquals(FileMetaHelper.UNKNOWN_TIME, FileMetaHelper.formatMtime(-1L, UTC));
    }

    @Test
    public void goldenEpochInUtc() {
        // 1727913600 = 2024-10-03 00:00:00 UTC（date -u -r 对拍）
        assertEquals("2024-10-03 00:00", FileMetaHelper.formatMtime(1727913600L, UTC));
    }

    @Test
    public void oneSecondBeforeGoldenEpoch() {
        assertEquals("2024-10-02 23:59", FileMetaHelper.formatMtime(1727913599L, UTC));
    }

    @Test
    public void timezoneOffsetApplied() {
        // UTC+8：黄金时刻应按本地时区平移
        assertEquals("2024-10-03 08:00", FileMetaHelper.formatMtime(1727913600L, CST));
    }

    @Test
    public void epochBoundaryMidnightRollsDate() {
        // 1727913599 = 2024-10-02 23:59:59 UTC；Shanghai = 2024-10-03 07:59
        assertEquals("2024-10-03 07:59", FileMetaHelper.formatMtime(1727913599L, CST));
    }

    @Test
    public void unknownMarkerIsNotTimestampShaped() {
        String marker = FileMetaHelper.UNKNOWN_TIME;
        assertEquals("—", marker);
    }
}
