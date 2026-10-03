package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录书签纯函数层（无 Android 依赖，便于 JVM 单测）。
 *
 * <p>书签路径均为远端绝对路径；所有入参在比较/存储前经 {@link #normalizePath} 规范化：
 * 去首尾空白、{@code \\} 转 {@code /}、折叠连续斜杠、去结尾斜杠（根目录 {@code "/"} 保留）。
 * 空串视为无效路径，不参与收藏。</p>
 *
 * <p>约定：列表按「新收藏在前」排序；超出 {@link #MAX_BOOKMARKS} 时丢弃最旧（末尾）条目。
 * 所有方法均为纯函数——不修改入参列表，返回新列表。</p>
 */
public final class BookmarkHelper {

    /** 单台服务器书签上限：超出后丢弃最旧条目。 */
    public static final int MAX_BOOKMARKS = 30;

    private BookmarkHelper() {}

    // ── 路径规范化 ───────────────────────────────────────────────────────────

    /**
     * 规范化远端目录路径：去首尾空白、{@code \\} 转 {@code /}、折叠连续斜杠、
     * 去结尾斜杠（根目录 {@code "/"} 保留）。空/null 返回空串。
     */
    @NonNull
    public static String normalizePath(@Nullable String path) {
        if (path == null) return "";
        String p = path.trim().replace('\\', '/');
        StringBuilder sb = new StringBuilder(p.length());
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (c == '/' && sb.length() > 0 && sb.charAt(sb.length() - 1) == '/') {
                continue;
            }
            sb.append(c);
        }
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    // ── 增删查 ───────────────────────────────────────────────────────────────

    /** {@code path} 是否已在书签列表中（按规范化后比较）。 */
    public static boolean isBookmarked(@Nullable List<String> bookmarks,
                                       @Nullable String path) {
        String target = normalizePath(path);
        if (target.isEmpty() || bookmarks == null) return false;
        for (String b : bookmarks) {
            if (target.equals(normalizePath(b))) return true;
        }
        return false;
    }

    /**
     * 添加书签：规范化后置顶；已存在则去重并移到最前；超出上限丢弃最旧条目。
     * 不修改入参列表。
     */
    @NonNull
    public static List<String> addBookmark(@Nullable List<String> bookmarks,
                                           @Nullable String path) {
        String target = normalizePath(path);
        List<String> result = new ArrayList<>();
        if (target.isEmpty()) {
            if (bookmarks != null) result.addAll(bookmarks);
            return result;
        }
        if (bookmarks != null) {
            for (String b : bookmarks) {
                String n = normalizePath(b);
                if (!n.isEmpty() && !n.equals(target)) {
                    result.add(n);
                }
            }
        }
        result.add(0, target);
        while (result.size() > MAX_BOOKMARKS) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    /** 移除书签（移除全部规范化匹配项）；不存在的路径返回与原列表等价的拷贝。 */
    @NonNull
    public static List<String> removeBookmark(@Nullable List<String> bookmarks,
                                              @Nullable String path) {
        String target = normalizePath(path);
        List<String> result = new ArrayList<>();
        if (bookmarks == null) return result;
        for (String b : bookmarks) {
            String n = normalizePath(b);
            if (!n.isEmpty() && !n.equals(target)) {
                result.add(n);
            }
        }
        return result;
    }

    // ── 展示辅助 ─────────────────────────────────────────────────────────────

    /** 书签展示名：路径最后一段；根目录返回 {@code "/"}；空返回空串。 */
    @NonNull
    public static String displayName(@Nullable String path) {
        String p = normalizePath(path);
        if (p.isEmpty()) return "";
        if ("/".equals(p)) return "/";
        int idx = p.lastIndexOf('/');
        return idx < 0 ? p : p.substring(idx + 1);
    }
}
