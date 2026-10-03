package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ssh.mdreader.model.RecentFileEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 最近打开文件（阅读历史）纯函数层（无 Android 依赖，便于 JVM 单测）。
 *
 * <p>条目按「最近打开在前」排序：{@link #record} 置顶；同一路径重复打开=去重并移到最前并
 * 刷新时间戳；超出 {@link #MAX_RECENT} 时丢弃最旧（末尾）条目（产品决策落档：自清理，
 * 不做 TTL 过期）。路径在比较/存储前经 {@link BookmarkHelper#normalizePath} 规范化
 * （与书签同语义：去空白、{@code \\} 转 {@code /}、折叠连续斜杠、去结尾斜杠）。</p>
 *
 * <p>所有方法均为纯函数——不修改入参列表，返回新列表。时间戳由调用方注入
 * （避免纯函数依赖系统时钟，便于单测确定性）。</p>
 */
public final class RecentFilesHelper {

    /** 单台服务器历史上限：超出后丢弃最旧条目。 */
    public static final int MAX_RECENT = 50;

    private RecentFilesHelper() {}

    // ── 增删 ─────────────────────────────────────────────────────────────────

    /**
     * 记录一次打开：规范化后置顶、去重（同路径仅保留新的时间戳）、上限截断。
     * 无效路径（null/空/规范化后为空）返回与入参等价的拷贝（不写入）。
     */
    @NonNull
    public static List<RecentFileEntry> record(@Nullable List<RecentFileEntry> entries,
                                               @Nullable String path,
                                               long ts) {
        String target = BookmarkHelper.normalizePath(path);
        List<RecentFileEntry> result = new ArrayList<>();
        if (entries != null) {
            for (RecentFileEntry e : entries) {
                if (e == null) continue;
                String p = BookmarkHelper.normalizePath(e.getPath());
                if (!p.isEmpty() && !p.equals(target)) {
                    result.add(e);
                }
            }
        }
        if (!target.isEmpty()) {
            result.add(0, new RecentFileEntry(target, ts));
        }
        while (result.size() > MAX_RECENT) {
            result.remove(result.size() - 1);
        }
        return result;
    }

    /** 移除历史条目（移除全部规范化匹配项）；不存在的路径返回与入参等价的拷贝。 */
    @NonNull
    public static List<RecentFileEntry> remove(@Nullable List<RecentFileEntry> entries,
                                               @Nullable String path) {
        String target = BookmarkHelper.normalizePath(path);
        List<RecentFileEntry> result = new ArrayList<>();
        if (entries == null) return result;
        for (RecentFileEntry e : entries) {
            if (e == null) continue;
            String p = BookmarkHelper.normalizePath(e.getPath());
            if (!p.isEmpty() && !p.equals(target)) {
                result.add(e);
            }
        }
        return result;
    }
}
