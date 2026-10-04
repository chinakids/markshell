package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** ShareHelper 纯函数单测（第三十九轮 #51 查看器内容分享判定层）。 */
public class ShareHelperTest {

    // ── isShareableText ───────────────────────────────────────────────────────

    @Test
    public void nullTextNotShareable() {
        assertFalse(ShareHelper.isShareableText(null));
    }

    @Test
    public void emptyTextNotShareable() {
        assertFalse(ShareHelper.isShareableText(""));
    }

    @Test
    public void whitespaceOnlyNotShareable() {
        assertFalse(ShareHelper.isShareableText("   \n\t  "));
    }

    @Test
    public void normalTextShareable() {
        assertTrue(ShareHelper.isShareableText("hello"));
    }

    @Test
    public void textWithSurroundingWhitespaceShareable() {
        assertTrue(ShareHelper.isShareableText("  hello  "));
    }

    // ── mimeForImage ──────────────────────────────────────────────────────────

    @Test
    public void pngBytesMapToPngMime() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        assertEquals("image/png", ShareHelper.mimeForImage(png));
    }

    @Test
    public void jpegBytesMapToJpegMime() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        assertEquals("image/jpeg", ShareHelper.mimeForImage(jpeg));
    }

    @Test
    public void unknownBytesFallbackToOctetStream() {
        byte[] junk = {'x', 'y', 'z', 'w'};
        assertEquals("application/octet-stream", ShareHelper.mimeForImage(junk));
    }

    @Test
    public void nullBytesFallbackToOctetStream() {
        assertEquals("application/octet-stream", ShareHelper.mimeForImage(null));
    }

    // ── suggestImageFileName ──────────────────────────────────────────────────

    @Test
    public void keepsExistingExtension() {
        assertEquals("photo.jpg", ShareHelper.suggestImageFileName("photo.jpg", "image/jpeg"));
    }

    @Test
    public void appendsMissingExtensionByMime() {
        assertEquals("photo.png", ShareHelper.suggestImageFileName("photo", "image/png"));
    }

    @Test
    public void defaultNameForEmptyDisplayName() {
        String name = ShareHelper.suggestImageFileName("", "image/png");
        assertTrue(name.startsWith("image_"));
        assertTrue(name.endsWith(".png"));
    }

    // ── shouldStreamText ──────────────────────────────────────────────────────

    @Test
    public void smallTextDirectShare() {
        assertFalse(ShareHelper.shouldStreamText("small text"));
    }

    @Test
    public void nullTextDirectShare() {
        assertFalse(ShareHelper.shouldStreamText(null));
    }

    @Test
    public void boundaryExactlyAtLimitDirectShare() {
        // 恰好 512KB（ASCII 每字符 1 字节）= 不降级（严格大于才降级）
        StringBuilder sb = new StringBuilder(ShareHelper.MAX_EXTRA_TEXT_BYTES);
        for (int i = 0; i < ShareHelper.MAX_EXTRA_TEXT_BYTES; i++) sb.append('a');
        assertFalse(ShareHelper.shouldStreamText(sb.toString()));
    }

    @Test
    public void overLimitUsesStreamShare() {
        StringBuilder sb = new StringBuilder(ShareHelper.MAX_EXTRA_TEXT_BYTES + 1);
        for (int i = 0; i < ShareHelper.MAX_EXTRA_TEXT_BYTES + 1; i++) sb.append('a');
        assertTrue(ShareHelper.shouldStreamText(sb.toString()));
    }

    @Test
    public void multibyteTextCountsUtf8Bytes() {
        // 512KB/3 个字节每字符 = 524288/3 ≈ 174762，'山'(U+5C71)=3 字节；
        // 200000 字符（60 万字节）→ 降级
        StringBuilder sb = new StringBuilder(200000);
        for (int i = 0; i < 200000; i++) sb.append('山');
        assertTrue(ShareHelper.shouldStreamText(sb.toString()));
    }

    // ── fileNameFromPath ──────────────────────────────────────────────────────

    @Test
    public void basenameFromAbsolutePath() {
        assertEquals("app.log", ShareHelper.fileNameFromPath("/var/log/app.log"));
    }

    @Test
    public void basenameFromRelativePath() {
        assertEquals("conf.yml", ShareHelper.fileNameFromPath("etc/conf.yml"));
    }

    @Test
    public void basenameFromTrailingSlashStripped() {
        assertEquals("dir", ShareHelper.fileNameFromPath("/home/user/dir/"));
    }

    @Test
    public void backslashSeparatorNormalized() {
        assertEquals("run.sh", ShareHelper.fileNameFromPath("C:\\srv\\run.sh"));
    }

    @Test
    public void nullOrEmptyPathDefaultsToShareTxt() {
        assertEquals("share.txt", ShareHelper.fileNameFromPath(null));
        assertEquals("share.txt", ShareHelper.fileNameFromPath(""));
    }
}
