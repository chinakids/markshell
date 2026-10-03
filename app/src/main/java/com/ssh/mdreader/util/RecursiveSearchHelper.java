package com.ssh.mdreader.util;

import com.ssh.mdreader.model.SearchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文件浏览递归搜索纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（第十六轮能力发现 #12）</b>：FileBrowser 只有当前目录过滤
 * （{@link FileFilterHelper}），深层定位需逐层进入；竞品 markor
 * {@code recursive_search_in_location} + {@code max_search_depth}（FileSearchEngine.java
 * 416 行源码实证：迭代 Actor 队列遍历、深度限制、忽略目录、符号链接跳过、relPath 排序）。
 * 本层提供遍历规划/深度限制/忽略谓词/相对路径/结果排序；匹配谓词=委托
 * {@link FileFilterHelper#matchesName}（单一语义源，不重复实现第二套子串匹配）。
 * SFTP 实际遍历在 {@code SshManager.searchFiles}（worker 线程 BFS，performance
 * 真机域见文档）。</p>
 *
 * <p><b>深度语义（与 markor 精确同型）</b>：目录深度 0 基（root=0）。一个目录
 * 深度为 {@code d} 时仅当 {@code d < maxDepth} 才列出其内容（markor：
 * {@code if (depth < maxSearchDepth) handleDirectory(...)}）；{@code maxDepth=0}
 * 表示无限制（markor {@code getSearchMaxDepth()}：0 → Integer.MAX_VALUE）；
 * 负数防御为 {@link #DEFAULT_MAX_DEPTH}。因此 {@code maxDepth=1} = 只搜索指定
 * 目录本身的条目（不进入任何子目录）。</p>
 *
 * <p><b>忽略目录</b>：默认忽略版本控制/依赖/临时噪音目录（markor 默认
 * {@code .git / .tmp / *Thumb*} 同型，本实现为确定性字面集合，不含正则）。
 * 忽略仅阻止<b>下钻</b>，不阻止名称匹配——即被忽略目录里不会有结果，
 * 但名为「.git」的目录本身不会作为目录结果出现（其内容不被扫描）。</p>
 *
 * <p><b>符号链接</b>：由 {@code SshManager} 遍历侧跳过（{@code SftpATTRS.isLink()}，
 * markor {@code GsFileUtils.isSymbolicLink} 同型），防权限死循环——纯函数层不感知。</p>
 */
public final class RecursiveSearchHelper {

    /** 默认最大深度（用户未显式选择时的取值）。 */
    public static final int DEFAULT_MAX_DEPTH = 3;

    /** 默认忽略目录（字面精确匹配，大小写敏感）。 */
    static final String[] IGNORED_DIRS = {
            ".git", ".svn", ".hg", "node_modules", "__pycache__", ".tmp"
    };

    private RecursiveSearchHelper() {
    }

    /**
     * 解析用户请求深度：{@code >0} 原样；{@code 0} = 无限制（{@link Integer#MAX_VALUE}，
     * markor 0=infinite 同语义）；{@code <0} 防御为 {@link #DEFAULT_MAX_DEPTH}。
     */
    public static int resolveMaxDepth(int requested) {
        if (requested == 0) return Integer.MAX_VALUE;
        if (requested < 0) return DEFAULT_MAX_DEPTH;
        return requested;
    }

    /**
     * 目录深度 {@code dirDepth} 为 {@code d}（root=0）时是否应列出其内容。
     * 仅当 {@code d < maxDepth}；{@code maxDepth=Integer.MAX_VALUE} 恒 true（有限深度内）。
     */
    public static boolean shouldDescend(int dirDepth, int maxDepth) {
        return dirDepth < maxDepth;
    }

    /**
     * 目录名是否属于默认忽略集（阻止下钻）。null/空 → false（不忽略）。
     * 字面精确匹配（不 trim，大小写敏感）。
     */
    public static boolean isIgnoredDir(String name) {
        if (name == null || name.isEmpty()) return false;
        for (String ignored : IGNORED_DIRS) {
            if (ignored.equals(name)) return true;
        }
        return false;
    }

    /**
     * 计算相对路径（根目录 → 条目的展示路径）。语义：
     * <ul>
     *   <li>{@code fullPath} 等于 {@code rootPath} → {@code ""}（防御分支）。</li>
     *   <li>root 为 {@code "/"}：去一个前导斜杠（{@code "/etc"→"etc"}）。</li>
     *   <li>常规 root：要求 {@code fullPath} 以 {@code root+"/"}（或 root 自带尾斜杠
     *       则直接 root）为前缀，去掉前缀；否则返回 null（防御，调用方以
     *       {@code buildChildPath} 保证前缀成立）。</li>
     * </ul>
     */
    public static String relativePath(String rootPath, String fullPath) {
        if (rootPath == null || fullPath == null) return null;
        if (fullPath.equals(rootPath)) return "";
        if ("/".equals(rootPath)) {
            return fullPath.startsWith("/") ? fullPath.substring(1) : fullPath;
        }
        String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
        if (!fullPath.startsWith(prefix)) return null;
        return fullPath.substring(prefix.length());
    }

    /**
     * 结果排序：目录恒前（与文件列表视觉一致）；组内按相对路径大小写不敏感升序
     * （markor {@code keySort(_result, relPath.toLowerCase())} 同型）。返回<b>新</b>
     * 列表，不修改入参（项目纯函数风格）；入参 null → 空列表。
     */
    public static List<SearchResult> sortResults(List<SearchResult> results) {
        List<SearchResult> sorted = new ArrayList<>();
        if (results != null) sorted.addAll(results);
        Collections.sort(sorted, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            // null 相对路径防御（构造保证非空，防御分支排在未命名之前）
            String pa = a.getRelativePath() == null ? "" : a.getRelativePath();
            String pb = b.getRelativePath() == null ? "" : b.getRelativePath();
            return pa.compareToIgnoreCase(pb);
        });
        return sorted;
    }
}
