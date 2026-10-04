package com.ssh.mdreader.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;

/**
 * JPEG EXIF Orientation 解析（纯 Java + 最小 Android 旋转胶水，JVM 可测）。
 *
 * <p><b>背景（走查实锤）</b>：{@link android.graphics.BitmapFactory} 与 Markwon 图片管线
 * 解码时均<b>忽略</b> EXIF Orientation 标签——手机相机/DSLR 竖拍照片（常见 Orientation
 * 6/8）打开后横置/倒置，且应用内无任何旋转手势/按钮可修正（无法绕过）。本类从 JPEG
 * 字节流解析 APP1（Exif）段落的 Orientation（0x0112）标签并映射为显示旋转角度。
 *
 * <p><b>输出约定</b>：{@link #orientationDegrees} 恒返回 0/90/180/270；任何失败（非
 * JPEG/无 EXIF/段落损坏/值越界/空输入）返回 0 = 零旋转原样解码（安全回退，行为与
 * 现状一致）。
 *
 * <p><b>镜像近似</b>：镜像+旋转组合的 orientation（2/4/5/7，极罕见设备）按纯旋转
 * 近似（本项目不做镜像翻转），映射 2→0、4→180、5→90、7→270（如实标注）。
 */
public final class ImageExifHelper {

    private ImageExifHelper() {}

    /** 直接由 JPEG 字节解析 EXIF Orientation 并映射为显示旋转角度（0/90/180/270）。 */
    public static int orientationDegrees(byte[] jpeg) {
        return rotationDegrees(parseExifOrientation(jpeg));
    }

    /**
     * EXIF Orientation 值（1-8）→ 显示旋转角度（EXIF 规范变换表；0/越界返回 0）。
     * 1=正、2=水平镜像、3=180°、4=垂直镜像、5=镜像+90°、6=90°、7=镜像+270°、8=270°。
     */
    public static int rotationDegrees(int orientation) {
        switch (orientation) {
            case 3: return 180;
            case 4: return 180;
            case 5: return 90;
            case 6: return 90;
            case 7: return 270;
            case 8: return 270;
            default: return 0; // 1/2/未知/越界 → 零旋转
        }
    }

    /**
     * 解析 JPEG 字节流中的 EXIF Orientation 原始值（1-8）；非 JPEG/无 EXIF/
     * 结构异常/值越界/空输入 → 0。
     */
    public static int parseExifOrientation(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4) return 0;
        if ((jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) return 0; // SOI
        int idx = 2;
        while (idx + 1 < jpeg.length) {
            int b = jpeg[idx] & 0xFF;
            if (b != 0xFF) break; // 结构破坏
            int marker = jpeg[idx + 1] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) break; // EOI/SOS：EXIF 必在扫描数据前
            if (marker == 0x01) { idx += 2; continue; }  // TEM 无长度
            if (idx + 4 > jpeg.length) break;
            int segLen = ((jpeg[idx + 2] & 0xFF) << 8) | (jpeg[idx + 3] & 0xFF);
            if (segLen < 2) break;
            if (marker == 0xE1) { // APP1
                int payloadStart = idx + 4;
                int payloadLen = segLen - 2;
                if (payloadStart + 6 <= jpeg.length
                        && payloadStart + payloadLen <= jpeg.length
                        && jpeg[payloadStart] == 'E' && jpeg[payloadStart + 1] == 'x'
                        && jpeg[payloadStart + 2] == 'i' && jpeg[payloadStart + 3] == 'f'
                        && jpeg[payloadStart + 4] == 0 && jpeg[payloadStart + 5] == 0) {
                    int ori = parseTiffOrientation(jpeg, payloadStart + 6,
                            payloadStart + payloadLen);
                    if (ori != 0) return ori;
                }
            }
            idx += 2 + segLen;
        }
        return 0;
    }

    /**
     * 解析 TIFF（EXIF APP1 载荷）内 IFD0 的 Orientation（0x0112）标签值。
     * 支持小端（II）与大端（MM），逐项 12 字节扫描，全边界校验；失败/缺失返回 0。
     */
    private static int parseTiffOrientation(byte[] jpeg, int start, int end) {
        if (end - start < 8) return 0;
        int b0 = jpeg[start] & 0xFF, b1 = jpeg[start + 1] & 0xFF;
        boolean little;
        if (b0 == 0x49 && b1 == 0x49) {      // "II"
            little = true;
        } else if (b0 == 0x4D && b1 == 0x4D) { // "MM"
            little = false;
        } else {
            return 0;
        }
        if (u16(jpeg, start + 2, little) != 0x2A) return 0; // TIFF 魔数
        long ifdOff = u32(jpeg, start + 4, little);
        if (ifdOff < 0 || ifdOff + 2 > end - start) return 0;
        int ifd = start + (int) ifdOff;
        int count = u16(jpeg, ifd, little);
        if (count <= 0 || count > 4096) return 0; // 防御
        int p = ifd + 2;
        for (int i = 0; i < count; i++) {
            if (p + 12 > end) break;
            int tag = u16(jpeg, p, little);
            if (tag == 0x0112) {
                int type = u16(jpeg, p + 2, little);
                long cnt = u32(jpeg, p + 4, little);
                if (type != 0x0003 || cnt < 1) return 0; // SHORT 型
                int val = u16(jpeg, p + 8, little);       // 值字段前 2 字节
                return (val >= 1 && val <= 8) ? val : 0;
            }
            p += 12;
        }
        return 0;
    }

    /** 按角度旋转位图（EXIF 修正用）；旋转失败/同等角度返回原图（安全回退）。 */
    public static Bitmap rotateBitmap(Bitmap src, int degrees) {
        if (src == null || degrees % 360 == 0) return src;
        try {
            Matrix m = new Matrix();
            m.postRotate(degrees);
            Bitmap out = Bitmap.createBitmap(src, 0, 0,
                    src.getWidth(), src.getHeight(), m, true);
            return out != null && out != src ? out : src;
        } catch (Throwable t) {
            return src; // OOM 等 → 保持原图（与现状一致，不崩）
        }
    }

    private static int u16(byte[] b, int off, boolean little) {
        int v0 = b[off] & 0xFF, v1 = b[off + 1] & 0xFF;
        return little ? (v0 | (v1 << 8)) : ((v0 << 8) | v1);
    }

    private static long u32(byte[] b, int off, boolean little) {
        long v0 = b[off] & 0xFF, v1 = b[off + 1] & 0xFF,
                v2 = b[off + 2] & 0xFF, v3 = b[off + 3] & 0xFF;
        return little ? (v0 | (v1 << 8) | (v2 << 16) | (v3 << 24))
                : ((v0 << 24) | (v1 << 16) | (v2 << 8) | v3);
    }
}
