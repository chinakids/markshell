package com.ssh.mdreader.util;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * {@link ImageSaveHelper} 格式判定层 JVM 单测：magic bytes → MIME 全格式覆盖与
 * 边界（短数组/未知/损坏）、MIME→扩展名映射、显示名修正（有/无扩展名/空名/未知 MIME）。
 */
public class ImageSaveHelperTest {

    // ── sniffMime ────────────────────────────────────────────────────────────

    @Test
    public void sniffJpeg() {
        assertEquals("image/jpeg", ImageSaveHelper.sniffMime(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0}));
        assertEquals("image/jpeg", ImageSaveHelper.sniffMime(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}));
    }

    @Test
    public void sniffPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
        assertEquals("image/png", ImageSaveHelper.sniffMime(png));
    }

    @Test
    public void sniffGif() {
        assertEquals("image/gif", ImageSaveHelper.sniffMime("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("image/gif", ImageSaveHelper.sniffMime("GIF87a".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    public void sniffWebp() {
        byte[] webp = new byte[12];
        webp[0] = 'R'; webp[1] = 'I'; webp[2] = 'F'; webp[3] = 'F';
        webp[8] = 'W'; webp[9] = 'E'; webp[10] = 'B'; webp[11] = 'P';
        assertEquals("image/webp", ImageSaveHelper.sniffMime(webp));
    }

    @Test
    public void sniffBmp() {
        assertEquals("image/bmp", ImageSaveHelper.sniffMime("BM\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1)));
    }

    @Test
    public void sniffUnknownOrTruncated() {
        assertNull(ImageSaveHelper.sniffMime(null));
        assertNull(ImageSaveHelper.sniffMime(new byte[0]));
        assertNull(ImageSaveHelper.sniffMime(new byte[]{0x00, (byte) 0xFF}));
        assertNull(ImageSaveHelper.sniffMime("hello world".getBytes(StandardCharsets.US_ASCII)));
        // RIFF 头存在但无 WEBP 四字节 = 非 WebP，不误判
        byte[] riff = new byte[12];
        riff[0] = 'R'; riff[1] = 'I'; riff[2] = 'F'; riff[3] = 'F';
        riff[8] = 'W'; riff[9] = 'A'; riff[10] = 'V'; riff[11] = 'E';
        assertNull(ImageSaveHelper.sniffMime(riff));
        // PNG 缺完整 8 字节签名尾：不误判
        byte[] shortPng = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A};
        assertNull(ImageSaveHelper.sniffMime(shortPng));
    }

    // ── extensionForMime ────────────────────────────────────────────────────

    @Test
    public void extensionMapping() {
        assertEquals(".jpg", ImageSaveHelper.extensionForMime("image/jpeg"));
        assertEquals(".png", ImageSaveHelper.extensionForMime("image/png"));
        assertEquals(".gif", ImageSaveHelper.extensionForMime("image/gif"));
        assertEquals(".webp", ImageSaveHelper.extensionForMime("image/webp"));
        assertEquals(".bmp", ImageSaveHelper.extensionForMime("image/bmp"));
        assertNull(ImageSaveHelper.extensionForMime("image/heic"));
        assertNull(ImageSaveHelper.extensionForMime(null));
    }

    // ── ensureDisplayName ───────────────────────────────────────────────────

    @Test
    public void keepsExistingExtension() {
        assertEquals("photo.png", ImageSaveHelper.ensureDisplayName("photo.png", "image/jpeg"));
        assertEquals("a.b.jpg", ImageSaveHelper.ensureDisplayName("a.b.jpg", "image/jpeg"));
        // 纯扩展名名也视为已有扩展名，原样保留（.jpg 风格）
        assertEquals(".jpg", ImageSaveHelper.ensureDisplayName(".jpg", "image/jpeg"));
    }

    @Test
    public void appendsExtensionWhenMissing() {
        assertEquals("photo.jpg", ImageSaveHelper.ensureDisplayName("photo", "image/jpeg"));
        assertEquals("截图.webp", ImageSaveHelper.ensureDisplayName("截图", "image/webp"));
    }

    @Test
    public void unknownMimeDoesNotAppend() {
        assertEquals("photo", ImageSaveHelper.ensureDisplayName("photo", null));
        assertEquals("photo", ImageSaveHelper.ensureDisplayName("photo", "image/heic"));
    }

    @Test
    public void emptyNameDefaultsToTimestamp() {
        String d = ImageSaveHelper.ensureDisplayName("", "image/jpeg");
        assertTrue("默认名应以 image_ 开头", d.startsWith("image_"));
        assertTrue("默认名应带 .jpg 扩展名", d.endsWith(".jpg"));
        String d2 = ImageSaveHelper.ensureDisplayName(null, null);
        assertTrue("未知 MIME 默认名应带 .img", d2.endsWith(".img"));
    }
}
