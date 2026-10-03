package com.ssh.mdreader.util;

/**
 * Markdown 内嵌图片 destination 改写纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（路线图 #9，2026-10-03）</b>：Markwon 的 {@code ImagesPlugin.create()}
 * 默认只注册 {@code data:}/{@code http(s):} 两个 scheme handler，图片 destination
 * 若是相对/绝对<b>远端路径</b>（如 {@code ![x](docs/img.png)}、{@code ![x](/img.png)}）
 * 在加载时会因「无 scheme handler」不显示（源码路径：AsyncDrawableLoaderImpl 的
 * {@code No scheme-handler is found} 异常，默认 errorHandler 仅打日志）——这是
 * SSH 阅读器浏览带配图 Markdown 文档时的核心体验缺口。</p>
 *
 * <p><b>方案</b>：在 Markwon 的 {@code Image} span factory 处把这类 destination
 * 改写为自定义 scheme {@code markdown-sftp:} + 百分号编码的绝对远端路径；
 * 图片加载器（{@link SftpImageSchemeHandler}）按该 scheme 从 SFTP 同步读取并解码，
 * 复用既有 {@link SshManager#readFileBytes}（候选见 {@link SftpImageCache} 缓存）。
 * 链接语法 {@code [x](p)} 不受影响——本层只作用于 {@code Image} 节点。</p>
 *
 * <p><b>语义</b>（文档化）：</p>
 * <ul>
 *   <li>destination 为空/null、或<b>含任何标准 scheme</b>（{@code http/https/data/file}
 *       等）→ 返回 {@code null}（保持原样，交由 Markwon 内置 handler 处理；未知 scheme
 *       仍然不显示，与本层无关）。</li>
 *   <li>无 scheme 的相对/绝对路径 → 先用 {@link LinkTargetHelper#resolveRemotePath}
 *       规范化为绝对远端路径（相对 base=当前文档所在目录），再做百分号编码：仅保留
 *       {@code A-Za-z0-9_.-/} 原样，其余字符（空格、中文、{@code #}、{@code ?} 等）以
 *       UTF-8 {@code %XX} 编码——保证最终 destination 是可解析的 URI 且编码无歧义。</li>
 * </ul>
 */
public final class ImageTargetHelper {

    /** 自定义图片 scheme（Handler 侧 {@link SftpImageSchemeHandler#SCHEME} 同源）。 */
    public static final String SCHEME = "markdown-sftp";

    /** scheme + 分隔冒号 = destination 前缀。 */
    public static final String SCHEME_PREFIX = SCHEME + ":";

    private ImageTargetHelper() {
    }

    /**
     * 计算 {@code Image} 节点 destination 的改写结果：{@code null}=保持原样，
     * 否则返回 {@code markdown-sftp:} + 编码后的绝对远端路径。
     *
     * @param rawDest         原始 destination（commonmark 解析后字符串）
     * @param currentFilePath 当前 Markdown 文件的远端绝对路径（相对路径解析基座；
     *                        null 时按链接自身的绝对性规范化）
     */
    public static String buildSftpDestination(String rawDest, String currentFilePath) {
        if (rawDest == null || rawDest.isEmpty()) return null;
        if (hasScheme(rawDest)) return null;
        String remote = LinkTargetHelper.resolveRemotePath(currentFilePath, rawDest);
        return SCHEME_PREFIX + encodePath(remote);
    }

    /**
     * 剥离 {@code markdown-sftp:} 前缀，返回编码后的远端路径；
     * 无前缀时原样返回（防御性，正常不会发生）。
     */
    public static String stripSftpScheme(String destination) {
        if (destination != null && destination.startsWith(SCHEME_PREFIX)) {
            return destination.substring(SCHEME_PREFIX.length());
        }
        return destination;
    }

    /** 是否已含 scheme（RFC URI scheme 语法：字母开头，随后字母/数字/+-.，直到 ':'）。 */
    static boolean hasScheme(String s) {
        int i = 0;
        char c = s.charAt(0);
        if (!isAlpha(c)) return false;
        i++;
        while (i < s.length()) {
            c = s.charAt(i);
            if (c == ':') return true;
            if (!isSchemeChar(c)) return false;
            i++;
        }
        return false;
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isSchemeChar(char c) {
        return isAlpha(c) || (c >= '0' && c <= '9') || c == '+' || c == '-' || c == '.';
    }

    /** 百分号编码（UTF-8）：保留 {@code A-Za-z0-9_.-/}，其余字节 {@code %XX}；
     * 已存在的合法 {@code %XX} 序列原样保留（避免对已编码 destination 双重编码）。 */
    static String encodePath(String path) {
        StringBuilder out = null;
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '%' && i + 2 < path.length()
                    && hexVal(path.charAt(i + 1)) >= 0 && hexVal(path.charAt(i + 2)) >= 0) {
                if (out != null) out.append(c).append(path.charAt(i + 1)).append(path.charAt(i + 2));
                i += 2;
                continue;
            }
            if (isUnreserved(c) || c == '/') {
                if (out != null) out.append(c);
                continue;
            }
            if (out == null) out = new StringBuilder(path.length() + 16).append(path, 0, i);
            byte[] bytes = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            for (byte b : bytes) {
                out.append('%');
                out.append(HEX[(b >> 4) & 0xF]);
                out.append(HEX[b & 0xF]);
            }
        }
        return out == null ? path : out.toString();
    }

    private static int hexVal(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private static boolean isUnreserved(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.'
                || c == '~';
    }
}
