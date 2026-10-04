package com.ssh.mdreader.util;

/**
 * 内容分享纯函数判定层（第三十九轮 #51 查看器/阅读器「分享」）。
 *
 * <p>背景：全仓 ACTION_SEND 仅有批注导出 1 处（{@code AnnotationOverlayHelper.shareExport}），
 * 文本/代码/CSV/图片/Markdown 五查看器均无分享入口——运维场景「把服务器上的日志/配置/截图
 * 发给同事」只能复制路径（对方无 SFTP 访问权）或保存图片到相册再手动分享（两步绕行）。
 * 竞品=markor {@code DocumentEditAndViewFragment} 菜单源码实证：submenu_share（isText 时可见）
 * 含 action_share_text（{@code GsContextUtils.shareText} EXTRA_TEXT 直发全文）/
 * action_share_file（FileProvider EXTRA_STREAM）等全集。</p>
 *
 * <p>本类仅承载 JVM 可测的判定与命名纯函数；Intent 构造与缓存写入为
 * {@code UiUtils.shareText/shareBytes} 胶水（Android 平台调用不可 JVM 单测）。</p>
 */
public final class ShareHelper {

    private ShareHelper() {
    }

    /** 文本分享 MIME（markor {@code MIME_TEXT_PLAIN} 对齐）。 */
    public static final String MIME_TEXT_PLAIN = "text/plain";

    /** 未知图片字节的兜底 MIME：接收方可保存，但不能宣称为具体图片类型。 */
    public static final String MIME_OCTET_STREAM = "application/octet-stream";

    /**
     * EXTRA_TEXT 直发上限（字节）：Android Binder 事务缓冲约 1MB，超过会抛
     * {@code TransactionTooLargeException} 导致崩溃；512KB 保守余量（markor
     * {@code shareText} 无此护栏=直接 EXTRA_TEXT——本地便签场景文件通常小；
     * 本项目查看对象为服务器日志/转储，可能远超）。超过后降级为文件流分享
     * （markor {@code shareFile} FileProvider 同型），语义等价：接收方得到同一文本。
     */
    public static final int MAX_EXTRA_TEXT_BYTES = 512 * 1024;

    /**
     * 文本是否必须走文件流：UTF-8 字节数严格大于 {@link #MAX_EXTRA_TEXT_BYTES}。
     * 等值/低于=EXTRA_TEXT 直发。null 视同直发（调用方已空守卫）。
     */
    public static boolean shouldStreamText(String text) {
        if (text == null) return false;
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return bytes.length > MAX_EXTRA_TEXT_BYTES;
    }

    /**
     * 文本是否可分享：非 null 且含非空白字符。
     *
     * <p>空白/空串分享会在接收方生成空消息=无效交付，调用方据此提示
     * 「暂无可分享内容」而非发出空 Intent。</p>
     */
    public static boolean isShareableText(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) return true;
        }
        return false;
    }

    /**
     * 图片字节 → 分享 MIME：委托 {@link ImageSaveHelper#sniffMime}（单一语义源=保存与分享
     * 同一判断），无法识别返回 {@link #MIME_OCTET_STREAM} 兜底（与保存路径的 null 回退不同：
     * 保存可放弃，分享不可半途而废，必须给接收方一个可操作的类型）。
     */
    public static String mimeForImage(byte[] bytes) {
        String mime = ImageSaveHelper.sniffMime(bytes);
        return mime != null ? mime : MIME_OCTET_STREAM;
    }

    /**
     * 分享图片的缓存文件名建议：委托 {@link ImageSaveHelper#ensureDisplayName}
     * （有扩展名保留、无扩展名补 MIME 扩展名、空名默认 image_&lt;ts&gt;）——
     * 与「保存图片」同一命名语义，接收方所见文件名与内容一致。
     */
    public static String suggestImageFileName(String displayName, String mime) {
        return ImageSaveHelper.ensureDisplayName(displayName, mime);
    }

    /**
     * 由远端路径提取显示文件名（斜杠/反斜杠兼容，尾部斜杠剥离）；
     * 空/纯路径返回 {@code share.txt} 默认名。分享用它作为 EXTRA_TEXT/缓存文件名，
     * 与 {@code DownloadHelper.suggestFileName} 同一语义（但那属于下载域，不复用防耦合）。
     */
    public static String fileNameFromPath(String path) {
        if (path == null || path.trim().isEmpty()) return "share.txt";
        String norm = path.trim().replace('\\', '/');
        while (norm.length() > 1 && norm.endsWith("/")) {
            norm = norm.substring(0, norm.length() - 1);
        }
        int slash = norm.lastIndexOf('/');
        String name = slash >= 0 ? norm.substring(slash + 1) : norm;
        if (name.isEmpty()) return "share.txt";
        return name;
    }
}
