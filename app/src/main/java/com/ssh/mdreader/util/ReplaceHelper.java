package com.ssh.mdreader.util;

import java.util.List;

/**
 * 编辑模式文本替换纯函数层（JVM 可测，无 Android 依赖；路线图 #11）。
 *
 * <p>复用 {@link FindHelper#scanAll} 在<b>同一原文</b>上先全量扫描匹配区间，再按文档序
 * 逐段拼接新串——匹配索引始终基于原文计算，拼接过程不修改原文，因此不存在
 * 「替换后偏移失效」问题（等价于「从后往前替换」的思路，实现更直接）。</p>
 *
 * <p><b>字面量语义</b>：replacement 中的 {@code $}、反斜杠等一律按字面处理，不做
 * 正则/转义展开。单次操作只替换「原文扫描出的匹配」——替换文本里新出现的查询串
 * 不会在同一次操作中被再次替换（无递归、无死循环）。</p>
 *
 * <p>匹配语义（大小写/非重叠/跨行）与 {@link FindHelper} 完全一致：「替什么」由
 * FindHelper 决定，本层只负责「怎么替」。</p>
 */
public final class ReplaceHelper {

    private ReplaceHelper() {
    }

    /**
     * 把 {@code text} 中<b>全部</b>非重叠匹配替换为 {@code replacement}（原文序一次扫描）。
     *
     * @param text        被替换文本（null → 返回 null）
     * @param query       查询词（null/空 → 视为无匹配，返回原文本）
     * @param replacement 替换文本（null 按空串处理）
     * @param ignoreCase  true=逐字符不区分大小写（与 FindHelper.scanAll 语义一致）
     * @return 替换后的新串；无匹配时返回 {@code text.toString()}（内容相同的新引用）
     */
    public static String replaceAll(CharSequence text, String query, String replacement,
                                    boolean ignoreCase) {
        if (text == null) return null;
        List<FindHelper.Match> matches = FindHelper.scanAll(text, query, ignoreCase);
        if (matches.isEmpty()) return text.toString();
        String repl = replacement != null ? replacement : "";
        StringBuilder sb = new StringBuilder(
                text.length() + (repl.length() - query.length()) * matches.size());
        int prev = 0;
        for (FindHelper.Match m : matches) {
            sb.append(text, prev, m.start);
            sb.append(repl);
            prev = m.end;
        }
        sb.append(text, prev, text.length());
        return sb.toString();
    }

    /**
     * 只替换第 {@code occurrenceIndex} 处（0-based 文档序）匹配，其余匹配保持原样。
     * 越界（负数/超总量）或无匹配 → 返回原文本（约定「不存在即不替换」）。
     */
    public static String replaceOccurrence(CharSequence text, String query, String replacement,
                                           boolean ignoreCase, int occurrenceIndex) {
        if (text == null) return null;
        List<FindHelper.Match> matches = FindHelper.scanAll(text, query, ignoreCase);
        if (occurrenceIndex < 0 || occurrenceIndex >= matches.size()) return text.toString();
        String repl = replacement != null ? replacement : "";
        StringBuilder sb = new StringBuilder(text.length() + (repl.length() - query.length()));
        FindHelper.Match m = matches.get(occurrenceIndex);
        sb.append(text, 0, m.start);
        sb.append(repl);
        sb.append(text, m.end, text.length());
        return sb.toString();
    }
}
