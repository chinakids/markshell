package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * JVM unit tests for {@link ClipPathHelper}.
 * Pure text logic — no Android dependencies.
 */
public class ClipPathHelperTest {

    // ── clipText ──────────────────────────────────────────────────────────

    @Test
    public void clipTextReturnsPathAsIs() {
        assertEquals("/var/log/nginx/error.log", ClipPathHelper.clipText("/var/log/nginx/error.log"));
    }

    @Test
    public void clipTextKeepsTrailingSlashForDirectoryPath() {
        assertEquals("/data/docs/", ClipPathHelper.clipText("/data/docs/"));
    }

    @Test
    public void clipTextNullReturnsNull() {
        assertNull(ClipPathHelper.clipText(null));
    }

    @Test
    public void clipTextEmptyReturnsNull() {
        assertNull(ClipPathHelper.clipText(""));
    }

    @Test
    public void clipTextKeepsWhitespaceAsIs() {
        // 语义决策：剪贴板内容=完整远端路径原样（不 trim 不改写）——空白串仍是合法内容
        assertEquals("   ", ClipPathHelper.clipText("   "));
    }

    // ── toastText ─────────────────────────────────────────────────────────

    @Test
    public void toastTextPrefixedWithCopyNotice() {
        assertEquals("已复制路径: /etc/hosts", ClipPathHelper.toastText("/etc/hosts"));
    }

    @Test
    public void toastTextNullReturnsNull() {
        assertNull(ClipPathHelper.toastText(null));
        assertNull(ClipPathHelper.toastText(""));
    }
}
