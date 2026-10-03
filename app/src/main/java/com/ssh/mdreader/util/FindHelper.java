package com.ssh.mdreader.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 文档内文本查找纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（路线图 #8，2026-10-03）</b>：长文档阅读定位关键词是阅读器标配能力。
 * 本层在<b>渲染后文本</b>（MarkdownReaderActivity 的 {@code tvContent} 当前内容，即用户
 * 实际看到/可选择的文本）上做字面匹配扫描——与源 Markdown 相比，渲染文本剥离了
 * {@code **}、{@code `} 等行内语法记号，用户按所见文本查找天然命中；且匹配区间直接
 * 是渲染文本字符偏移，可 100% 复用 {@link AnnotationOverlayHelper#jumpToCharOffset}
 * 跳转落点（关抽屉 + 临时高亮 + 平滑滚动），无需零宽标记注入。</p>
 *
 * <p><b>匹配语义</b>（文档化，与 Java {@code String.indexOf} 一致的字面扫描）：</p>
 * <ul>
 *   <li><b>非重叠</b>：每个匹配起点 ≥ 上一个匹配终点（{@code "aa"} in {@code "aaa"} →
 *       1 处 {@code [0,2)}，重叠的 {@code [1,3)} 不报）。</li>
 *   <li><b>跨行</b>：渲染文本含 {@code \n}，查询串含换行也可命中（逐字符字面比较，
 *       regionMatches 语义，不做规范化/折叠）。</li>
 *   <li><b>大小写</b>：{@code ignoreCase=true} 时用 {@code String.regionMatches(true,...)}
 *       逐字符不区分大小写（Unicode 码点级映射由 regionMatches 承担，且<b>不改变
 *       匹配长度</b>——不做 {@code toLowerCase} 整体折叠，避免个别字符映射后长度变化
 *       导致偏移错位）。默认调用方传 {@code true}（中文无大小写，英文查找更友好）。</li>
 *   <li>空白/全角半角<b>不</b>做任何归一化——所见即所搜。</li>
 * </ul>
 */
public final class FindHelper {

    private FindHelper() {
    }

    /** 单处匹配在查找文本中的区间（0-based 字符偏移，{@code [start, end)}）。 */
    public static final class Match {
        /** 匹配起点（含）。 */
        public final int start;
        /** 匹配终点（不含）。 */
        public final int end;

        Match(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /**
     * 全量扫描 {@code haystack} 中 {@code query} 的<b>全部</b>字面匹配（文档序，
     * 非重叠）。任一参数为 null、query 为空、haystack 为空 → 空列表（从不返回 null）。
     *
     * @param haystack   查找文本（渲染后文本）
     * @param query      查询词（空串/空白串只按字面匹配，不 trim）
     * @param ignoreCase true=逐字符不区分大小写
     */
    public static List<Match> scanAll(CharSequence haystack, String query, boolean ignoreCase) {
        if (haystack == null || query == null || query.isEmpty()) return Collections.emptyList();
        int n = haystack.length();
        int qLen = query.length();
        if (n == 0 || qLen > n) return Collections.emptyList();
        List<Match> result = new ArrayList<>();
        int from = 0;
        while (from + qLen <= n) {
            int i = indexOfHaystack(haystack, query, from, ignoreCase);
            if (i < 0) break;
            result.add(new Match(i, i + qLen));
            from = i + qLen;   // 非重叠推进
        }
        return result;
    }

    /**
     * 循环推进当前匹配下标：{@code direction=1} 下一处、{@code direction=-1} 上一处，
     * 首尾<b>循环</b>（查找语义：看不到边界，绕回）。空列表 → -1。
     */
    public static int advance(List<Match> matches, int current, int direction) {
        if (matches == null || matches.isEmpty() || direction == 0) return -1;
        int size = matches.size();
        int idx = current < 0 ? 0 : current;
        int next = (int) (((long) idx + direction) % size);
        if (next < 0) next += size;
        return next;
    }

    /** 从 from 起首个匹配起点（字面；ignoreCase=false 走 indexOf，true 走 regionMatches 扫描）。 */
    private static int indexOfHaystack(CharSequence haystack, String query,
                                       int from, boolean ignoreCase) {
        int n = haystack.length();
        int qLen = query.length();
        if (!ignoreCase && haystack instanceof String) {
            return ((String) haystack).indexOf(query, from);
        }
        for (int i = from; i + qLen <= n; i++) {
            if (regionMatches(haystack, i, query, 0, qLen, ignoreCase)) return i;
        }
        return -1;
    }

    /** 等价 String.regionMatches 的逐字符比较（对 String 直接委托，保性能）。 */
    private static boolean regionMatches(CharSequence a, int aOff, String b, int bOff,
                                         int len, boolean ignoreCase) {
        if (a instanceof String && b instanceof String) {
            return ((String) a).regionMatches(ignoreCase, aOff, b, bOff, len);
        }
        for (int k = 0; k < len; k++) {
            char ca = a.charAt(aOff + k);
            char cb = b.charAt(bOff + k);
            if (ca != cb) {
                if (!ignoreCase) return false;
                char ua = Character.toUpperCase(ca);
                char ub = Character.toUpperCase(cb);
                if (ua != ub) {
                    if (Character.toLowerCase(ua) != Character.toLowerCase(ub)) return false;
                }
            }
        }
        return true;
    }
}
