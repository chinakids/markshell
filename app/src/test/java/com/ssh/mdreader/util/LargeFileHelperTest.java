package com.ssh.mdreader.util;

import com.ssh.mdreader.R;

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

    // ── confirmMessageResId：返回文案资源 ID（「返回资源 ID」方案，纯函数零 Context） ──

    @Test
    public void confirmMessage_resId_matchesResource() {
        // 文案本体已迁入 strings.xml（格式串 %1$s=可读大小），helper 仅返回资源 ID，
        // 由 UI 层 getString(id, DownloadHelper.formatBytes(size)) 格式化。
        assertEquals(R.string.msg_confirm_large_file, LargeFileHelper.confirmMessageResId());
    }
}
