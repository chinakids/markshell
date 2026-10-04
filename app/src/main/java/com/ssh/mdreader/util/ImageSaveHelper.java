package com.ssh.mdreader.util;

/**
 * 图片保存到相册的格式判定层（第卅二轮 #44）。
 *
 * <p>背景：图片查看器保存路径此前为「maxDim=2048 降采样位图 + 强制 PNG 重编码 +
 * displayName 保留原扩展名（如 .jpg）+ MIME 硬编码 image/png」——文件名/内容/MIME
 * 三方不一致，且原图分辨率、EXIF 元数据、原格式体积全部丢失（唯一导出通道=数据损坏）。
 * 本轮改为原始字节直存（零重编码），本类承载全部纯函数判定（JVM 可测），
 * MediaStore/文件写入为 {@code ImageViewerActivity} 胶水。</p>
 *
 * <p>识别集=项目图片查看器支持集（{@code RemoteFile#isImageFile()}：
 * png/jpg/jpeg/gif/webp/bmp），magic bytes 保守判定，无法识别返回 null（调用方回退）。</p>
 */
public final class ImageSaveHelper {

    private ImageSaveHelper() {
    }

    /**
     * magic bytes → MIME（大小写无关字节级前缀）；无法识别/数据不足返回 null。
     */
    public static String sniffMime(byte[] data) {
        if (data == null || data.length < 3) return null;
        // JPEG: FF D8 FF
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 'P' 'N' 'G' 0D 0A 1A 0A
        if (data.length >= 8 && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return "image/png";
        }
        // GIF: 'G' 'I' 'F' '8' (87a/89a/87a variant)
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8') {
            return "image/gif";
        }
        // WebP: "RIFF"...."WEBP"
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        // BMP: 'B' 'M'
        if (data[0] == 'B' && data[1] == 'M') return "image/bmp";
        return null;
    }

    /** MIME → 扩展名（支持集内）；未知/空返回 null。 */
    public static String extensionForMime(String mime) {
        if (mime == null) return null;
        switch (mime) {
            case "image/jpeg": return ".jpg";
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "image/bmp": return ".bmp";
            default: return null;
        }
    }

    /**
     * 保存显示名修正：
     * <ul>
     *   <li>有扩展名（含 {@code .jpg} 这类纯扩展名名）→ 原样保留——原字节直存后内容与
     *       扩展名天然一致，不再出现「.jpg 内容为 PNG」；</li>
     *   <li>无扩展名 → 按 MIME 补正确扩展名（如远端文件名只叫 {@code photo}）；</li>
     *   <li>MIME 未知不补；空/空名 → {@code image_<时间戳>} 默认名（同历史行为）。</li>
     * </ul>
     */
    public static String ensureDisplayName(String displayName, String mime) {
        String ext = extensionForMime(mime);
        if (displayName == null || displayName.isEmpty()) {
            return "image_" + System.currentTimeMillis() + (ext != null ? ext : ".img");
        }
        if (hasExtension(displayName)) return displayName;
        return ext != null ? displayName + ext : displayName;
    }

    private static boolean hasExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 && dot < name.length() - 1;
    }
}
