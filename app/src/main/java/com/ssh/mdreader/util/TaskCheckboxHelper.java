package com.ssh.mdreader.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务清单 checkbox 交互纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（路线图 #5，2026-10-03 javap 实证）</b>：markwon ext-tasklist 4.6.2 的
 * {@code TaskListPlugin} 只提供渲染——{@code TaskListSpan} 是 {@code LeadingMarginSpan}，
 * 无任何点击/事件 API（javap 实证：TaskListPlugin.create 仅注册 parser/spansFactory/visitor；
 * TaskListSpan 仅 isDone/setDone/绘制），且其依赖 com.atlassian.commonmark:commonmark:0.13.0
 * 的 Node 无源码位置 API（getSourceSpans 为后续版本）→ 「点击任务项 → 源文本行号」必须自建映射。</p>
 *
 * <p><b>行定位算法（本卡设计决策，零新依赖）</b>：<b>零宽标记直通渲染</b>——</p>
 * <ol>
 *   <li>{@link #injectMarkers} 在渲染前把源文本逐任务行在 {@code [ ]}/{@code [x]} 标记后的
 *       首个空白之后插入 {@link #MARKER}（U+200B ZERO WIDTH SPACE，视觉零宽）×（该任务行在扫描序
 *       中的序号+1）。Markwon 把它当作普通字面字符保留在任务项文本最前面（
 *       {@code TaskListPostProcessor} 字节码实证：正则 {@code ^\[([xX\s])\]\s+(.*)} 匹配后把
 *       group2 原样迁入新的 Text 节点，零宽字符无拆分/无丢弃）。</li>
 *   <li>渲染后点击命中处（Activity 侧复用批注点击的 {@code layout.getOffsetForHorizontal} 机制）
 *       由 {@code TaskListSpan} 定位任务项文本区间 → {@link #decodeMarkerIndex} 从区间起点读出
 *       连续 MARKER 个数-1 = 扫描序序号 → 与 {@code injectMarkers} 同源的 {@link TaskLine} 映射回
 *       源文本行/字符位置 → {@link #flipTaskState} 只改那一个字符 → writeFile 写回。</li>
 * </ol>
 * <p>该机制与 Markwon 渲染错位（换行/标题/TOC 同源的渲染-源码偏移问题）<b>完全解耦</b>：标记
 * 直通渲染，命中与渲染内容/行距/换行无关；同样可平移给路线图 #6 大纲 TOC（标题行注入同类标记
 * →按标记渲染偏移跳转）。</p>
 *
 * <p><b>谓词对齐（与 {@code TaskListPostProcessor.REGEX_TASK_LIST_ITEM =
 * ^\[([xX\s])\]\s+(.*)} 逐项对齐）</b>：</p>
 * <ul>
 *   <li>括号内单字符：空格/{@code x}/{@code X}/tab（{@code \s} 类，与 Markwon 一致）；</li>
 *   <li>{@code ]} 后必须至少一个空白（空格/tab）——{@code - [ ]}（后无空白）不是 Markwon 任务项，
 *       本层同样不识别（对齐而非放宽）；</li>
 *   <li>list marker {@code -}/{@code *}/{@code +} 均可（Markwon 不限 marker 字符）+ 任意缩进 +
 *       {@code >} 引用前缀（blockquote 内任务项 Markwon 同样渲染）；</li>
 *   <li>围栏代码块内的任务行<b>不</b>注入标记（Markwon 将其渲染为代码字面而非任务项，注入仅污染
 *       复制文本、无任何功能作用；4 空格缩进代码不区分——嵌套列表可同形，注入无害）。</li>
 * </ul>
 * <p><b>误判安全性</b>：即使谓词与 Markwon 存在差异，多余标记只停留于非任务项文本中（不可点，
 * 因点击只对 {@code TaskListSpan} 覆盖且有标记的项生效）；解码索引与注入同源同一扫描表，
 * 不会「点 A 翻 B」。</p>
 */
public final class TaskCheckboxHelper {

    private TaskCheckboxHelper() {
    }

    /** 行定位标记字符（U+200B ZERO WIDTH SPACE，视觉零宽、Markwon 字面直通）。 */
    public static final char MARKER = '\u200B';

    /** 任务行谓词：前缀（缩进+blockquote）| marker | 空白 | [状态] | 空白+内容。 */
    private static final Pattern TASK_LINE = Pattern.compile(
            "^(\\s*(?:>\\s*)*)([-*+])([ \\t]+)\\[([ xX\\t])\\]([ \\t]+)(.*)$");

    /**
     * 单个任务行的源文本坐标（0-based；所有字符偏移均指<b>源文本</b>绝对位置）。
     */
    public static final class TaskLine {
        /** 0-based 源行号。 */
        public final int lineIndex;
        /** 状态字符（' '/'x'/'X'）在源文本中的绝对字符偏移（'[' 之后那个字符）。 */
        public final int stateCharIndex;
        /** 标记注入位置：']' 后首个空白段之后（即 Markwon group2 的起点）的源文本绝对字符偏移。 */
        public final int insertCharIndex;
        /** 是否已完成（状态字符为 'x'/'X'）。 */
        public final boolean checked;

        TaskLine(int lineIndex, int stateCharIndex, int insertCharIndex, boolean checked) {
            this.lineIndex = lineIndex;
            this.stateCharIndex = stateCharIndex;
            this.insertCharIndex = insertCharIndex;
            this.checked = checked;
        }
    }

    /** 注入标记后的渲染输入与其任务行元数据（{@link #injectMarkers} 产出）。 */
    public static final class MarkedSource {
        /** 注入标记后的源文本（喂给 Markwon 渲染；与源文本仅相差零宽标记）。 */
        public final String text;
        /** 与 {@code text} 同序（=源文本扫描序）的任务行坐标。 */
        public final List<TaskLine> lines;

        MarkedSource(String text, List<TaskLine> lines) {
            this.text = text;
            this.lines = lines;
        }
    }

    /** 全源扫描任务行（跳过围栏代码块；CRLF 兼容——谓词正则的 {@code .}/{@code $} 不吞 \\r）。 */
    public static List<TaskLine> scan(String source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<TaskLine> result = new ArrayList<>();
        int lineStart = 0;
        int lineIndex = 0;
        char fenceChar = 0;     // 0=不在围栏；否则为 '`' 或 '~'
        int fenceLen = 0;

        while (lineStart <= source.length()) {
            int lineEnd = source.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = source.length();
            String body = source.substring(lineStart, lineEnd);
            // CRLF 兼容：剥掉行尾单个 \r（行内容/偏移仍基于 lineStart，不受影响）；
            // Java 正则 `.`/$ 默认不吞 \r，不剥会导致 matches() 整体失败
            if (body.endsWith("\r")) body = body.substring(0, body.length() - 1);

            String trimmed = body.trim();
            if (fenceChar != 0) {
                // 围栏内：仅识别关闭围栏（同字符且长度 >= 开启长度）
                int run = fenceRun(trimmed, fenceChar);
                if (run >= fenceLen && run > 0) {
                    fenceChar = 0;
                    fenceLen = 0;
                }
            } else if (isFenceStart(trimmed)) {
                fenceChar = trimmed.charAt(0);
                fenceLen = fenceRun(trimmed, fenceChar);
            } else {
                Matcher m = TASK_LINE.matcher(body);
                if (m.matches()) {
                    boolean checked = "x".equalsIgnoreCase(m.group(4));
                    result.add(new TaskLine(
                            lineIndex,
                            lineStart + m.start(4),
                            lineStart + m.end(5),
                            checked));
                }
            }

            if (lineEnd >= source.length()) break;
            lineStart = lineEnd + 1;
            lineIndex++;
        }
        return result;
    }

    /** 在源文本每个任务行的标记注入位置插入 MARKER×（扫描序+1），返回渲染输入与同序坐标。 */
    public static MarkedSource injectMarkers(String source) {
        if (source == null || source.isEmpty()) {
            return new MarkedSource(source == null ? "" : source, Collections.emptyList());
        }
        List<TaskLine> lines = scan(source);
        if (lines.isEmpty()) return new MarkedSource(source, lines);
        StringBuilder sb = new StringBuilder(source);
        // 从后往前插入：后插入不影响先前已计算的位置
        for (int i = lines.size() - 1; i >= 0; i--) {
            int pos = lines.get(i).insertCharIndex;
            if (pos < 0 || pos > sb.length()) continue;   // 防御
            for (int k = 0; k <= i; k++) sb.insert(pos, MARKER);
        }
        return new MarkedSource(sb.toString(), lines);
    }

    /**
     * 从 {@code from}（含）起解码连续 {@link #MARKER} 个数编码的任务行扫描序序号；无标记 → -1。
     * 只统计从 {@code from} 开始的连续段，后续正文中的标记（理论上不存在）不参与。
     */
    public static int decodeMarkerIndex(CharSequence seq, int from) {
        if (seq == null || from < 0 || from >= seq.length()) return -1;
        int count = 0;
        while (from + count < seq.length() && seq.charAt(from + count) == MARKER) count++;
        return count > 0 ? count - 1 : -1;
    }

    /**
     * 翻转任务状态：只改动 {@code line.stateCharIndex} 那一个字符（checked→'x'，未勾→' '），
     * 其余逐字符保留（含换行风格/缩进/后续文本）。越界防御：不改动并返回原文本。
     */
    public static String flipTaskState(String source, TaskLine line, boolean checked) {
        if (source == null || line == null
                || line.stateCharIndex < 0 || line.stateCharIndex >= source.length()) {
            return source;
        }
        char target = checked ? 'x' : ' ';
        char old = source.charAt(line.stateCharIndex);
        if (old == target) return source;
        return source.substring(0, line.stateCharIndex)
                + target
                + source.substring(line.stateCharIndex + 1);
    }

    /** 行首（trim 后）是否围栏开启行。 */
    private static boolean isFenceStart(String trimmed) {
        if (trimmed.isEmpty()) return false;
        char c = trimmed.charAt(0);
        return (c == '`' || c == '~') && fenceRun(trimmed, c) >= 3;
    }

    /** 行首同字符连续个数（0=不是围栏字符开头）。 */
    private static int fenceRun(String s, char c) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == c) n++;
        return n;
    }
}
