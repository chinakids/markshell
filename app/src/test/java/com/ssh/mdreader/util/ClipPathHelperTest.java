package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.ssh.mdreader.R;

import org.junit.Test;

/**
 * JVM unit tests for {@link ClipPathHelper}.
 * Pure text logic — no Android dependencies.
 *
 * <p>按「返回资源 ID」方案：{@link ClipPathHelper#toastText} 返回 {@code R.string.*} 资源 ID
 * （int；null/空路径返回 {@code 0} 表示不提示），断言资源 ID 常量而非中文字面量。
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
    public void toastTextReturnsCopyResourceId() {
        assertEquals(R.string.toast_copy_path_done, ClipPathHelper.toastText("/etc/hosts"));
    }

    @Test
    public void toastTextNullReturnsZero() {
        assertEquals(0, ClipPathHelper.toastText(null));
    }

    @Test
    public void toastTextEmptyReturnsZero() {
        assertEquals(0, ClipPathHelper.toastText(""));
    }
}
