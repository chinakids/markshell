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
        return scanAll(haystack, query, ignoreCase, false);
    }

    /**
     * 全量扫描 + 可选<b>整词过滤</b>（匹配语义与 3 参重载完全一致，仅追加词边界条件）。
     *
     * <p><b>整词语义</b>（文档化，对齐 markor {@code TextSearchHandler} 的
     * {@code "\b"+query+"\b"} 正则词边界）：词字符 = ASCII 字母/数字/下划线
     * （{@code [A-Za-z0-9_]}，与 Java 正则默认 {@code \w} 一致）；匹配区间两端
     * 都必须位于文本边界或紧邻非词字符——即「cat」不命中 {@code catalog}/{@code cats}/
     * {@code cat_dog}，但命中 {@code cat-dog} 或句首/句尾的独立 {@code cat}。
     *
     * <p>中文等非 ASCII 字符视为<b>非词字符</b>（分隔符）：「测试」在
     * {@code 中文测试文本} 中命中（直觉语义）；但在 {@code abc测试def} 中不命中
     * （与 ASCII 字母相邻，视为嵌入单词）。注：markor 的 {@code \b} 对两侧均为
     * 非词字符时（如中文相邻处）不构成边界、中文查询会 0 命中；本项目按「CJK
     * 天然分隔」处理，更符合中文用户预期，ASCII 侧语义与 markor 一致。</p>
     *
     * <p>被过滤的候选匹配不占用「非重叠」推进位：从 {@code i+1} 继续扫描（可能
     * 与另一字面匹配重叠，如 {@code "aaa"} 中 {@code "aa"} 整词应 0 命中需逐位
     * 检查）。</p>
     *
     * @param wholeWord true=仅保留两端均为词边界的匹配
     */
    public static List<Match> scanAll(CharSequence haystack, String query, boolean ignoreCase,
                                      boolean wholeWord) {
        if (haystack == null || query == null || query.isEmpty()) return Collections.emptyList();
        int n = haystack.length();
        int qLen = query.length();
        if (n == 0 || qLen > n) return Collections.emptyList();
        List<Match> result = new ArrayList<>();
        int from = 0;
        while (from + qLen <= n) {
            int i = indexOfHaystack(haystack, query, from, ignoreCase);
            if (i < 0) break;
            if (wholeWord && !isWholeWord(haystack, i, i + qLen)) {
                from = i + 1;   // 整词过滤拒绝：仅前进 1 字符，不跳过潜在重叠匹配
                continue;
            }
            result.add(new Match(i, i + qLen));
            from = i + qLen;   // 非重叠推进
        }
        return result;
    }

    /** 整词边界判定：匹配两端均在文本边界或紧邻非词字符（词字符=ASCII 字母/数字/下划线）。 */
    private static boolean isWholeWord(CharSequence s, int start, int end) {
        boolean leftOk = start <= 0 || !isAsciiWordChar(s.charAt(start - 1));
        boolean rightOk = end >= s.length() || !isAsciiWordChar(s.charAt(end));
        return leftOk && rightOk;
    }

    private static boolean isAsciiWordChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9') || c == '_';
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
