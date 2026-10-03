package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** DownloadHelper 纯函数单测：SAF 默认文件名 / 字节格式化 / 进度百分比语义。 */
public class DownloadHelperTest {

    // ── suggestFileName ────────────────────────────────────────────────

    @Test
    public void nullPath_usesFallback() {
        assertEquals("remote_file", DownloadHelper.suggestFileName(null));
    }

    @Test
    public void blankPath_usesFallback() {
        assertEquals("remote_file", DownloadHelper.suggestFileName("   "));
    }

    @Test
    public void simpleFileName_preserved() {
        assertEquals("app.log", DownloadHelper.suggestFileName("/var/log/app.log"));
    }

    @Test
    public void deepPath_takesLastName() {
        assertEquals("app.conf", DownloadHelper.suggestFileName("/etc/nginx/sites-enabled/app.conf"));
    }

    @Test
    public void trailingSlash_stripped() {
        assertEquals("data", DownloadHelper.suggestFileName("/home/user/data/"));
    }

    @Test
    public void rootPath_usesFallback() {
        assertEquals("remote_file", DownloadHelper.suggestFileName("/"));
    }

    @Test
    public void backslash_normalizedLikeSlash() {
        assertEquals("win.txt", DownloadHelper.suggestFileName("C:\\docs\\win.txt"));
    }

    @Test
    public void chineseAndSpaces_preserved() {
        assertEquals("生产 报表 2026.csv", DownloadHelper.suggestFileName("/data/生产 报表 2026.csv"));
    }

    @Test
    public void hiddenFile_preserved() {
        assertEquals(".env", DownloadHelper.suggestFileName("/srv/app/.env"));
    }

    // ── formatBytes ────────────────────────────────────────────────────

    @Test
    public void bytesUnder1K_rawBytes() {
        assertEquals("512 B", DownloadHelper.formatBytes(512));
    }

    @Test
    public void zeroBytes_showsZeroB() {
        assertEquals("0 B", DownloadHelper.formatBytes(0));
    }

    @Test
    public void boundary1023_isBytes() {
        assertEquals("1023 B", DownloadHelper.formatBytes(1023));
    }

    @Test
    public void exactly1K_isKB() {
        assertEquals("1.0 KB", DownloadHelper.formatBytes(1024));
    }

    @Test
    public void fractionalKB() {
        assertEquals("1.5 KB", DownloadHelper.formatBytes(1536));
    }

    @Test
    public void exactly1MB_isMB() {
        assertEquals("1.0 MB", DownloadHelper.formatBytes(1024 * 1024));
    }

    @Test
    public void fractionalMB() {
        assertEquals("2.5 MB", DownloadHelper.formatBytes(2L * 1024 * 1024 + 512 * 1024));
    }

    // ── progressPercent ────────────────────────────────────────────────

    @Test
    public void unknownTotal_returnsMinusOne() {
        assertEquals(-1, DownloadHelper.progressPercent(0, 0));
        assertEquals(-1, DownloadHelper.progressPercent(100, -1));
    }

    @Test
    public void zeroCopied_isZero() {
        assertEquals(0, DownloadHelper.progressPercent(0, 1000));
    }

    @Test
    public void fiftyPercent() {
        assertEquals(50, DownloadHelper.progressPercent(500, 1000));
    }

    @Test
    public void fullProgress_isHundred() {
        assertEquals(100, DownloadHelper.progressPercent(1000, 1000));
    }

    @Test
    public void overTotal_clampedToHundred() {
        assertEquals(100, DownloadHelper.progressPercent(2000, 1000));
    }

    @Test
    public void negativeCopied_unknown() {
        assertEquals(-1, DownloadHelper.progressPercent(-5, 1000));
    }
}
