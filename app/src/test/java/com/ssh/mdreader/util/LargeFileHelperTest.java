package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** LargeFileHelper 纯函数单测：大文件判定边界语义 / 确认文案格式。 */
public class LargeFileHelperTest {

    // ── isLargeFile：阈值边界（严格大于 4MB 才提示） ────────────────────────

    @Test
    public void zero_unknownSize_notLarge() {
        assertFalse(LargeFileHelper.isLargeFile(0));
    }

    @Test
    public void negative_unknownSize_notLarge() {
        assertFalse(LargeFileHelper.isLargeFile(-1));
    }

    @Test
    public void tinyFile_notLarge() {
        assertFalse(LargeFileHelper.isLargeFile(1024));
    }

    @Test
    public void justBelowThreshold_notLarge() {
        assertFalse(LargeFileHelper.isLargeFile(LargeFileHelper.LARGE_FILE_THRESHOLD_BYTES - 1));
    }

    @Test
    public void exactlyThreshold_notLarge() {
        assertFalse(LargeFileHelper.isLargeFile(LargeFileHelper.LARGE_FILE_THRESHOLD_BYTES));
    }

    @Test
    public void oneByteAboveThreshold_large() {
        assertTrue(LargeFileHelper.isLargeFile(LargeFileHelper.LARGE_FILE_THRESHOLD_BYTES + 1));
    }

    @Test
    public void nginxLogSize_large() {
        // 12MB 轮转前日志：典型触发场景
        assertTrue(LargeFileHelper.isLargeFile(12L * 1024 * 1024));
    }

    @Test
    public void hugeBackup_large() {
        assertTrue(LargeFileHelper.isLargeFile(512L * 1024 * 1024));
    }

    // ── buildConfirmMessage：委托 DownloadHelper.formatBytes（单一语义源） ──

    @Test
    public void message_containsReadableSize() {
        String msg = LargeFileHelper.buildConfirmMessage(5L * 1024 * 1024);
        assertTrue(msg.contains("5.0 MB"));
    }

    @Test
    public void message_mentionsLoadingCost() {
        String msg = LargeFileHelper.buildConfirmMessage(20L * 1024 * 1024);
        assertTrue(msg.contains("卡顿"));
        assertTrue(msg.contains("仍要打开吗？"));
    }

    @Test
    public void message_startsWithHeader() {
        String msg = LargeFileHelper.buildConfirmMessage(20L * 1024 * 1024);
        assertTrue(msg.startsWith("文件较大（"));
    }

    @Test
    public void message_formatBytesDelegation_consistent() {
        long bytes = 7L * 1024 * 1024;
        assertEquals(DownloadHelper.formatBytes(bytes),
                LargeFileHelper.buildConfirmMessage(bytes)
                        .substring("文件较大（".length(),
                                "文件较大（".length() + DownloadHelper.formatBytes(bytes).length()));
    }
}
