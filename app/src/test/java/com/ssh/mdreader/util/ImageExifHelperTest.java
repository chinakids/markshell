package com.ssh.mdreader.util;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.ByteArrayOutputStream;

/**
 * {@link ImageExifHelper} 解析层 JVM 单测：合成最小 JPEG 字节流（SOI + APPn 段 + EOI）
 * 验证 APP1/Exif/TIFF/IFD0 Orientation（0x0112）解析的全部边界与字节序路径。
 * rotateBitmap 为 Android 胶水（returnDefaultValues 下仅覆盖 null 安全）。
 */
public class ImageExifHelperTest {

    // ── 合成工具（严格按 JPEG 段格式字节级构造）─────────────────────────────

    /** SOI + segments + EOI。 */
    private static byte[] jpeg(byte[]... segments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF); out.write(0xD8);
        for (byte[] s : segments) out.write(s, 0, s.length);
        out.write(0xFF); out.write(0xD9);
        return out.toByteArray();
    }

    /** FF marker + 2 字节长度 + payload。 */
    private static byte[] segment(int marker, byte[] payload) {
        byte[] s = new byte[payload.length + 4];
        s[0] = (byte) 0xFF; s[1] = (byte) marker;
        int len = payload.length + 2;
        s[2] = (byte) (len >> 8); s[3] = (byte) (len & 0xFF);
        System.arraycopy(payload, 0, s, 4, payload.length);
        return s;
    }

    /** APP1 Exif 段：payload = "Exif\0\0" + TIFF（IFD0 单条 Orientation）。 */
    private static byte[] exifApp1(int orientation, boolean bigEndian) {
        byte[] tiff = tiff(orientation, bigEndian);
        byte[] payload = new byte[6 + tiff.length];
        payload[0] = 'E'; payload[1] = 'x'; payload[2] = 'i'; payload[3] = 'f';
        payload[4] = 0; payload[5] = 0;
        System.arraycopy(tiff, 0, payload, 6, tiff.length);
        return segment(0xE1, payload);
    }

    /** 最小 TIFF：II/MM 头 + IFD0 一条（可选 orientation；tag 0 表示缺省）。 */
    private static byte[] tiff(int orientation, boolean bigEndian) {
        byte[] t = new byte[22];
        int p = 0;
        if (bigEndian) {
            t[p++] = 'M'; t[p++] = 'M';
            t[p++] = 0; t[p++] = 0x2A;
            t[p++] = 0; t[p++] = 0; t[p++] = 0; t[p++] = 8;   // IFD offset = 8
            t[p++] = 0; t[p++] = 1;                          // count = 1
            t[p++] = 0x01; t[p++] = 0x12;                    // Orientation tag
            t[p++] = 0; t[p++] = 3;                          // SHORT
            t[p++] = 0; t[p++] = 0; t[p++] = 0; t[p++] = 1;  // count = 1
            t[p++] = 0; t[p++] = (byte) orientation;         // value (前 2 字节, 大端)
            t[p++] = 0; t[p++] = 0;
        } else {
            t[p++] = 'I'; t[p++] = 'I';
            t[p++] = 0x2A; t[p++] = 0;
            t[p++] = 8; t[p++] = 0; t[p++] = 0; t[p++] = 0;
            t[p++] = 1; t[p++] = 0;
            t[p++] = 0x12; t[p++] = 0x01;
            t[p++] = 3; t[p++] = 0;
            t[p++] = 1; t[p++] = 0; t[p++] = 0; t[p++] = 0;
            t[p++] = (byte) orientation; t[p++] = 0; t[p++] = 0; t[p++] = 0;
        }
        return t;
    }

    /** APP0 JFIF 段（无 EXIF 的合法 JPEG 段）。 */
    private static byte[] jfifApp0() {
        return segment(0xE0, new byte[] {'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1, 0, 1, 0, 0});
    }

    // ── 主路径 ────────────────────────────────────────────────────────────

    @Test
    public void orientation6littleEndian_returns90() {
        assertEquals(90, ImageExifHelper.orientationDegrees(jpeg(exifApp1(6, false))));
        assertEquals(6, ImageExifHelper.parseExifOrientation(jpeg(exifApp1(6, false))));
    }

    @Test
    public void orientation8bigEndian_returns270() {
        assertEquals(270, ImageExifHelper.orientationDegrees(jpeg(exifApp1(8, true))));
    }

    @Test
    public void orientation3_returns180() {
        assertEquals(180, ImageExifHelper.orientationDegrees(jpeg(exifApp1(3, false))));
    }

    @Test
    public void orientation1_returns0() {
        assertEquals(0, ImageExifHelper.orientationDegrees(jpeg(exifApp1(1, false))));
    }

    @Test
    public void mirrorOrientations_approximateByRotation() {
        assertEquals(0, ImageExifHelper.rotationDegrees(2));   // 镜像 → 0
        assertEquals(180, ImageExifHelper.rotationDegrees(4)); // 镜像 → 180
        assertEquals(90, ImageExifHelper.rotationDegrees(5));
        assertEquals(270, ImageExifHelper.rotationDegrees(7));
    }

    @Test
    public void unknownOrientation_returns0() {
        assertEquals(0, ImageExifHelper.rotationDegrees(0));
        assertEquals(0, ImageExifHelper.rotationDegrees(9));
        assertEquals(0, ImageExifHelper.rotationDegrees(-1));
    }

    // ── 防御路径 ─────────────────────────────────────────────────────────

    @Test
    public void jpegWithoutExif_returns0() {
        assertEquals(0, ImageExifHelper.orientationDegrees(jpeg(jfifApp0())));
    }

    @Test
    public void nonJpegSignature_returns0() {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        assertEquals(0, ImageExifHelper.orientationDegrees(png));
    }

    @Test
    public void nullAndEmpty_returns0() {
        assertEquals(0, ImageExifHelper.orientationDegrees(null));
        assertEquals(0, ImageExifHelper.orientationDegrees(new byte[0]));
        assertEquals(0, ImageExifHelper.orientationDegrees(new byte[] {(byte) 0xFF, (byte) 0xD8}));
    }

    @Test
    public void truncatedAfterSoi_returns0() {
        byte[] j = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1};
        assertEquals(0, ImageExifHelper.orientationDegrees(j)); // 段长截断
        byte[] j2 = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x00, 0x05};
        assertEquals(0, ImageExifHelper.orientationDegrees(j2)); // 超界 payload
    }

    @Test
    public void orientationValueOutOfRange_returns0() {
        assertEquals(0, ImageExifHelper.orientationDegrees(jpeg(exifApp1(9, false))));
    }

    @Test
    public void exifAfterOtherSegments_stillParsed() {
        // APP0(JFIF) + APP1(Exif) + EOI —— 依规范 EXIF 可在任意应用段后
        assertEquals(90, ImageExifHelper.orientationDegrees(
                jpeg(jfifApp0(), exifApp1(6, false))));
    }

    @Test
    public void missingOrientationTag_returns0() {
        byte[] tiffOtherTag = new byte[22];
        // 手工构造 tag=0x0132(DateTime) 的一条 IFD
        tiffOtherTag[0] = 'I'; tiffOtherTag[1] = 'I';
        tiffOtherTag[2] = 0x2A; tiffOtherTag[3] = 0;
        tiffOtherTag[4] = 8; tiffOtherTag[5] = 0; tiffOtherTag[6] = 0; tiffOtherTag[7] = 0;
        tiffOtherTag[8] = 1; tiffOtherTag[9] = 0;
        tiffOtherTag[10] = 0x32; tiffOtherTag[11] = 0x01; // 0x0132
        tiffOtherTag[12] = 2; tiffOtherTag[13] = 0;       // ASCII
        tiffOtherTag[14] = 1; tiffOtherTag[15] = 0; tiffOtherTag[16] = 0; tiffOtherTag[17] = 0;
        tiffOtherTag[18] = '0'; tiffOtherTag[19] = '0'; tiffOtherTag[20] = 0; tiffOtherTag[21] = 0;
        byte[] payload = new byte[6 + tiffOtherTag.length];
        payload[0] = 'E'; payload[1] = 'x'; payload[2] = 'i'; payload[3] = 'f';
        payload[4] = 0; payload[5] = 0;
        System.arraycopy(tiffOtherTag, 0, payload, 6, tiffOtherTag.length);
        assertEquals(0, ImageExifHelper.orientationDegrees(jpeg(segment(0xE1, payload))));
    }

    @Test
    public void wrongTiffMagic_returns0() {
        byte[] t = tiff(6, false);
        t[2] = 0x00; t[3] = 0x2A; // 字节序伪装错误
        byte[] payload = new byte[6 + t.length];
        payload[0] = 'E'; payload[1] = 'x'; payload[2] = 'i'; payload[3] = 'f';
        payload[4] = 0; payload[5] = 0;
        System.arraycopy(t, 0, payload, 6, t.length);
        assertEquals(0, ImageExifHelper.orientationDegrees(jpeg(segment(0xE1, payload))));
    }

    @Test
    public void app1WithoutExifSignature_skipped() {
        // APP1 但载荷非 Exif（如 XMP）→ 跳过继续扫描，后续无 EXIF → 0
        byte[] xmp = segment(0xE1, "<?xpacket?>".getBytes());
        assertEquals(0, ImageExifHelper.orientationDegrees(jpeg(xmp, jfifApp0())));
    }

    @Test
    public void exifAfterSos_notParsed() {
        // SOS（扫描数据开始）后的 APP1 无效：解析器见 SOS 即止
        byte[] sos = segment(0xDA, new byte[] {0x01, 0x01, 0x00, 0x00});
        assertEquals(0, ImageExifHelper.orientationDegrees(jpeg(sos, exifApp1(6, false))));
    }

    @Test
    public void malformedSegmentLength_returns0() {
        byte[] bad = new byte[] {
                (byte) 0xFF, (byte) 0xD8, // SOI
                (byte) 0xFF, (byte) 0xE1, 0x00, 0x01, // segLen=1（<2）
                (byte) 0xFF, (byte) 0xD9
        };
        assertEquals(0, ImageExifHelper.orientationDegrees(bad));
    }

    @Test
    public void rotateBitmap_nullSafe() {
        assertNull(ImageExifHelper.rotateBitmap(null, 90));
        assertNull(ImageExifHelper.rotateBitmap(null, 0));
    }

    @Test
    public void emptyApp1_returns0() {
        // 空 APP1（len=2 无载荷）不应越界
        assertEquals(0, ImageExifHelper.orientationDegrees(
                jpeg(new byte[] {(byte) 0xFF, (byte) 0xE1, 0x00, 0x02})));
    }
}
