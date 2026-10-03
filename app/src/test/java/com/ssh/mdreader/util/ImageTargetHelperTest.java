package com.ssh.mdreader.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ImageTargetHelperTest {

    // ── buildSftpDestination：无 scheme 路径 → markdown-sftp: 改写 ────────────

    @Test
    public void relativePath_rewritesWithBaseDir() {
        assertEquals("markdown-sftp:/docs/img.png",
                ImageTargetHelper.buildSftpDestination("img.png", "/docs/readme.md"));
    }

    @Test
    public void dotSlash_rewritesAfterNormalize() {
        assertEquals("markdown-sftp:/docs/assets/a.png",
                ImageTargetHelper.buildSftpDestination("./assets/a.png", "/docs/readme.md"));
    }

    @Test
    public void parentPath_clampsAtRoot() {
        assertEquals("markdown-sftp:/img.png",
                ImageTargetHelper.buildSftpDestination("../../img.png", "/docs/sub/readme.md"));
    }

    @Test
    public void absolutePath_usesAsIs() {
        assertEquals("markdown-sftp:/images/logo.png",
                ImageTargetHelper.buildSftpDestination("/images/logo.png", "/docs/readme.md"));
    }

    @Test
    public void noCurrentFile_stillRewrites() {
        assertEquals("markdown-sftp:a/b.png",
                ImageTargetHelper.buildSftpDestination("a/b.png", null));
    }

    // ── buildSftpDestination：带 scheme → 保持原样（null） ────────────────────

    @Test
    public void httpKeepsNull() {
        assertNull(ImageTargetHelper.buildSftpDestination("https://example.com/x.png", "/d.md"));
        assertNull(ImageTargetHelper.buildSftpDestination("http://example.com/x.png", "/d.md"));
    }

    @Test
    public void dataUriKeepsNull() {
        assertNull(ImageTargetHelper.buildSftpDestination("data:image/png;base64,AAAA", "/d.md"));
    }

    @Test
    public void fileSchemeKeepsNull() {
        // file:// 无 handler 时不显示（与本层无关，不抢占）
        assertNull(ImageTargetHelper.buildSftpDestination("file:///sdcard/a.png", "/d.md"));
    }

    @Test
    public void emptyOrNullKeepsNull() {
        assertNull(ImageTargetHelper.buildSftpDestination("", "/d.md"));
        assertNull(ImageTargetHelper.buildSftpDestination(null, "/d.md"));
    }

    @Test
    public void mixedCaseSchemeKeepsNull() {
        assertNull(ImageTargetHelper.buildSftpDestination("HTTP://x/a.png", "/d.md"));
        assertNull(ImageTargetHelper.buildSftpDestination("ssh2://x", "/d.md"));
    }

    // ── 编码 ──────────────────────────────────────────────────────────────────

    @Test
    public void chineseAndSpace_encodedUtf8() {
        assertEquals("markdown-sftp:/docs/%E5%9B%BE%20abc.png",
                ImageTargetHelper.buildSftpDestination("图 abc.png", "/docs/readme.md"));
    }

    @Test
    public void hashAndQuestion_encoded() {
        assertEquals("markdown-sftp:/docs/a%23b%3Fc.png",
                ImageTargetHelper.buildSftpDestination("a#b?c.png", "/docs/readme.md"));
    }

    @Test
    public void existingPercentSequence_keptOnce() {
        // 已是编码形式的 destination 不双重编码
        assertEquals("markdown-sftp:/docs/a%20b.png",
                ImageTargetHelper.buildSftpDestination("a%20b.png", "/docs/readme.md"));
    }

    @Test
    public void lonePercent_encoded() {
        assertEquals("markdown-sftp:/docs/100%25_foo.png",
                ImageTargetHelper.buildSftpDestination("100%_foo.png", "/docs/readme.md"));
    }

    // ── stripSftpScheme ──────────────────────────────────────────────────────

    @Test
    public void stripScheme_removesPrefix() {
        assertEquals("/docs/img.png",
                ImageTargetHelper.stripSftpScheme("markdown-sftp:/docs/img.png"));
    }

    @Test
    public void stripScheme_passthrough() {
        assertEquals("http://x/a.png",
                ImageTargetHelper.stripSftpScheme("http://x/a.png"));
        assertNull(ImageTargetHelper.stripSftpScheme(null));
    }

    // ── hasScheme 边界 ───────────────────────────────────────────────────────

    @Test
    public void hasScheme_falseWhenColonAfterSlash() {
        // 路径含冒号但 scheme 非法（冒号前无 scheme 语法）→ 视为无 scheme 路径
        assertTrue(!ImageTargetHelper.hasScheme("a/b:c"));
    }

    @Test
    public void hasScheme_trueForStandardAndCustom() {
        assertTrue(ImageTargetHelper.hasScheme("https://x"));
        assertTrue(ImageTargetHelper.hasScheme("data:image"));
        assertTrue(ImageTargetHelper.hasScheme("markdown-sftp:"));
    }
}
