package com.ssh.mdreader.util;

/**
 * 本地文件上传到远端的纯函数层（零 Android 依赖，JVM 可测）。
 *
 * <p><b>背景（能力发现循环）</b>：迭代64 实现了「下载到本地」（远端→本地，SAF
 * {@code CreateDocument} 另存为），而全仓 upload/上传 0 命中——上传（本地→远端）缺失，
 * 双向往来只有下载半边半残；「把手机里的文件（截图/配置/证书/logcat 导出）放到服务器」
 * 用户流无法绕过。竞品 Termius 上传键族 29 键实证（上传文件/上传文件夹/SFTP 传输状态
 * 全套）=商业 SSH/SFTP 客户端标配。本类=与 {@link DownloadHelper} 对称的纯函数层。</p>
 *
 * <p><b>语义单一源</b>（同类逻辑不复制，教训㉗a 同型）：</p>
 * <ul>
 *   <li>{@link #resolveDisplayName(String, String)}：SAF 返回 uri 的本地显示名解析——
 *       优先 {@code OpenableColumns.DISPLAY_NAME}，缺失回退 {@code lastPathSegment}，
 *       再缺失→{@link #DEFAULT_FILE_NAME}；两分支的「取末级名」一律委托
 *       {@link DownloadHelper#suggestFileName}（反斜杠归一/折叠/末级提取不重复实现），
 *       含 {@code /} 的异常显示名只取末级=防路径注入。</li>
 *   <li>{@link #buildTargetPath(String, String)}：当前目录 + 显示名 → 远端目标路径；
 *       目录规范化委托 {@link BookmarkHelper#normalizePath}、拼接委托
 *       {@link NewFileHelper#joinPath}（根 {@code "/"} 与尾斜杠处理单一语义源）。</li>
 * </ul>
 */
public final class UploadHelper {

    /** 本地显示名与路径段均缺失时的兜底远端文件名。 */
    public static final String DEFAULT_FILE_NAME = "uploaded_file";

    private UploadHelper() {
    }

    /**
     * 解析 SAF 返回 uri 应使用的远端文件名：优先 displayName（非空），
     * 缺失回退 lastPathSegment（非空），两者皆缺失（或提取后为空）→ {@link #DEFAULT_FILE_NAME}。
     */
    public static String resolveDisplayName(String displayName, String lastPathSegment) {
        if (displayName != null && !displayName.trim().isEmpty()) {
            return DownloadHelper.suggestFileName(displayName);
        }
        if (lastPathSegment != null && !lastPathSegment.trim().isEmpty()) {
            return DownloadHelper.suggestFileName(lastPathSegment);
        }
        return DEFAULT_FILE_NAME;
    }

    /** 单参便捷版：仅按 displayName 解析（缺失→{@link #DEFAULT_FILE_NAME}）。 */
    public static String resolveDisplayName(String displayName) {
        return resolveDisplayName(displayName, null);
    }

    /**
     * 构建上传目标远端路径：当前目录（规范化） + 解析后的文件名（joinPath 语义：
     * 根目录不产生双斜杠、目录去尾斜杠、名称 trim）。currentDir 为 null/空 → 仅文件名
     * （相对路径，交由调用方保证 currentPath 语义）。
     */
    public static String buildTargetPath(String currentDir, String displayName) {
        String dir = currentDir == null ? "" : BookmarkHelper.normalizePath(currentDir);
        return NewFileHelper.joinPath(dir, resolveDisplayName(displayName));
    }
}
