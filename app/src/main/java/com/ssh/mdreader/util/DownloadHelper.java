package com.ssh.mdreader.util;

/**
 * 远端文件下载/保存到本地的纯函数层（零 Android 依赖，JVM 可测）。
 *
 * 语义单一源：
 * <ul>
 *   <li>{@link #suggestFileName(String)}：SAF「另存为」的默认文件名，取自远端路径末级名；
 *       用户可在系统对话框中任意改名。</li>
 *   <li>{@link #formatBytes(long)}：字节数人类可读（B/KB/MB），与 {@code RemoteFile.getFormattedSize()}
 *       同一语义源（该方法委托本函数）。</li>
 *   <li>{@link #progressPercent(long, long)}：进度百分比（total 未知/非法 → -1，表示进度不可知）；
 *       供下载进度展示使用。</li>
 * </ul>
 */
public final class DownloadHelper {

    /** 路径无法提取名称时的兜底文件名（SAF 不允许空默认名）。 */
    public static final String DEFAULT_FILE_NAME = "remote_file";

    private DownloadHelper() {
    }

    /**
     * 从远端路径提取 SAF 默认文件名：末级名（去尾斜杠；根目录/空白/空 → {@link #DEFAULT_FILE_NAME}）；
     * 反斜杠按 {@code /} 处理（远端路径习惯正斜杠，防御性归一）。返回名不做任何改写
     * （中文/空格/隐藏文件原样保留）。
     */
    public static String suggestFileName(String path) {
        if (path == null || path.trim().isEmpty()) {
            return DEFAULT_FILE_NAME;
        }
        String norm = path.trim().replace('\\', '/');
        while (norm.length() > 1 && norm.endsWith("/")) {
            norm = norm.substring(0, norm.length() - 1);
        }
        int slash = norm.lastIndexOf('/');
        String name = slash >= 0 ? norm.substring(slash + 1) : norm;
        if (name.isEmpty()) {
            return DEFAULT_FILE_NAME;
        }
        return name;
    }

    /**
     * 字节数人类可读：&lt;1024 → "N B"；&lt;1MB → "%.1f KB"；其余 → "%.1f MB"。
     * 与 {@code RemoteFile.getFormattedSize()} 完全同语义（该方法委托本函数）。
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * 下载进度百分比：total/bytes 非法（total &lt;= 0、copied &lt; 0）→ -1（进度不可知）；
     * 结果钳制到 [0, 100]（copied 超 total 时不超 100）。长整型乘法防超大文件溢出。
     */
    public static int progressPercent(long copied, long total) {
        if (total <= 0 || copied < 0) {
            return -1;
        }
        long pct = copied * 100 / total;
        if (pct > 100) {
            return 100;
        }
        return (int) pct;
    }
}
