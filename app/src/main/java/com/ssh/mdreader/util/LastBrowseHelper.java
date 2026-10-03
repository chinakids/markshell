package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 「继续上次位置」纯函数层（无 Android 依赖，便于 JVM 单测）。
 *
 * <p>负责启动目录解析策略：调用方显式指定的路径（连接配置里的主目录/remotePath）优先；
 * 否则恢复该服务器上次浏览的目录；均无则回退默认目录（home）。路径比较与返回前一律经
 * {@link BookmarkHelper#normalizePath} 规范化（与书签/最近打开同语义：去空白、
 * {@code \\} 转 {@code /}、折叠连续斜杠、去结尾斜杠，根目录 {@code "/"} 保留）。</p>
 *
 * <p>所有方法均为纯函数，不修改入参；返回结果为规范化后的新字符串。</p>
 */
public final class LastBrowseHelper {

    private LastBrowseHelper() {}

    /**
     * 解析启动浏览目录（优先级从高到低）：
     * <ol>
     *   <li>{@code explicitPath}：调用方显式指定（连接配置主目录/remotePath），首位非空即生效；</li>
     *   <li>{@code lastPath}：上次浏览目录（按服务器隔离存储）；</li>
     *   <li>{@code defaultPath}：兜底（远端 home）。</li>
     * </ol>
     * 入参均先规范化；空白/规范化后为空视为「未提供」并落到下一优先级。
     *
     * @return 规范化后的启动目录；若三层均无效返回空串（调用方自行兜底）。
     */
    @NonNull
    public static String resolveStartPath(@Nullable String explicitPath,
                                          @Nullable String lastPath,
                                          @Nullable String defaultPath) {
        String explicit = BookmarkHelper.normalizePath(explicitPath);
        if (!explicit.isEmpty()) return explicit;
        String last = BookmarkHelper.normalizePath(lastPath);
        if (!last.isEmpty()) return last;
        return BookmarkHelper.normalizePath(defaultPath);
    }

    /**
     * 待持久化的上次浏览目录：规范化；无效（null/规范化后为空）返回空串——
     * 调用方对空串应「清除记录」而非写入（目录已不存在时防止脏记录）。
     */
    @NonNull
    public static String normalizeForSave(@Nullable String path) {
        return BookmarkHelper.normalizePath(path);
    }
}
