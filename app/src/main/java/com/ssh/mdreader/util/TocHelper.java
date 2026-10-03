package com.ssh.mdreader.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 大纲（TOC）导航纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（路线图 #6，2026-10-03）</b>：Markwon 渲染后字符偏移与源文本不一致，
 * 「点击 TOC 条目 → 滚动到标题」不能按源文本行号直接跳转。本层沿用路线图 #5
 * {@link TaskCheckboxHelper} 的<b>零宽标记直通渲染</b>行定位机制并平移——标题正文前
 * 注入 {@link #HEADING_MARKER}（U+200C ZERO WIDTH NON-JOINER）×（扫描序+1），Markwon
 * 底层 commonmark 解析器把它当作普通字符直通到标题文本节点（本轮 commonmark 0.13.0
 * 实测：ATX/setext 标题文本均原样保留，{@code # ​X} 与 {@code ​Title\n===} 均解析为
 * Heading 且 Text 含 U+200C；{@code #​X}（无空格）不是标题；4 空格缩进是代码块），
 * 渲染后用 Markwon 的 {@code io.noties.markwon.core.spans.HeadingSpan}（javap 实证
 * 4.6.2 存在、public 构造器、{@code getLevel()}）限定区间解码序号，与 {@link #scan}
 * 同源映射回标题。</p>
 *
 * <p><b>与任务清单标记共存</b>：{@link TaskCheckboxHelper#MARKER}=U+200B（任务）、
 * 本层 {@link #HEADING_MARKER}=U+200C（标题）——两字符均非 Unicode 空白且互不相同，
 * 同一渲染文本中的标记段<b>按标记字符天然分离</b>，无需共享序号，解码无歧义
 * （任务只读 {@code TaskListSpan} 内的 U+200B 段，标题只读 {@code HeadingSpan} 内的
 * U+200C 段）。{@link #injectMarkers} 一次性合并两类注入点从后往前插入——位置互不重叠：
 * 标题行（{@code #} 前缀或 setext 正文行）与任务行（行首 {@code [-*+]} list marker）
 * 谓词不相交。</p>
 *
 * <p><b>标题谓词（与 commonmark 0.13.0 实测对齐）</b>：</p>
 * <ul>
 *   <li>ATX：可选引用前缀 {@code [ \t]*>[ \t]*}（可多层，如 {@code > > # x}）之后
 *       缩进 ≤3 <b>空格</b>（4+ 空格=缩进代码块，非标题；行首 tab 不视为缩进字符——
 *       commonmark 中 tab=4 列，{@code \t# x} 是代码块，本层同样不识别）+ {@code #{1,6}}
 *       +（空白或行尾）；{@code #foo}（无空格）不是 ATX（但可成为下面的 setext 正文——
 *       commonmark 实测 {@code #foo\n---} = level2 标题「#foo」）；{@code \#} 转义行
 *       不识别；7 个 {@code #} 不识别。</li>
 *   <li>setext：正文行=普通文本行（剥引用前缀后 ≤3 空格缩进、非空、非围栏、非合法
 *       ATX、非列表 marker 形态 {@code [-*+][ \t]}/{@code \d+[.)][ \t]}、非 hr/underline
 *       形态 {@code [-=]+} 行），且<b>下一行</b>为下划线行（同样剥前缀+≤3 空格缩进后
 *       整行为 {@code =+} → level1 或 {@code -{2,}} → level2，允许行尾空白）。</li>
 *   <li>围栏代码块（``` / ~~~，自动机同 {@link TaskCheckboxHelper#scan}）内一律跳过。</li>
 *   <li>行内格式记号（{@code **}、{@code [}、{@code `} 等）<b>不</b>剥离——TOC 按
 *       源文本原样展示（成本收益取舍：去记号需全文内联解析，收益低）。</li>
 * </ul>
 * <p><b>已知近似（保守，文档化）</b>：list 容器内 setext（{@code - Item\n  ---}，
 * 正文行以 list marker 开头被排除）不识别——commonmark 实测会渲染为列表内 level2
 * 标题，但容器缩进无法以绝对缩进模拟，接受「TOC 不列罕见 list 内 setext 标题」；
 * 该近似只影响 TOC 条目多寡，不影响跳转正确性（跳转按注入标记解码，与标题谓词无关）。</p>
 */
public final class TocHelper {

    private TocHelper() {
    }

    /** 标题行定位标记字符（U+200C ZERO WIDTH NON-JOINER，视觉零宽、commonmark 直通）。 */
    public static final char HEADING_MARKER = '\u200C';

    /** 引用前缀 + ≤3 空格缩进 + ATX 标记 + 空白/行尾（s 分支=空标题，正文为 null）。 */
    private static final Pattern ATX = Pattern.compile(
            "^((?:[ \\t]*>[ \\t]*)*)( {0,3})(#{1,6})(?:[ \\t]+(.*)|[ \\t]*)$");

    /** 下划线行（setext）：引用前缀 + ≤3 空格缩进 + 整行仅 = + 行尾空白。 */
    private static final Pattern UNDERLINE_EQ = Pattern.compile(
            "^((?:[ \\t]*>[ \\t]*)*) {0,3}=+[ \\t]*$");
    private static final Pattern UNDERLINE_DASH = Pattern.compile(
            "^((?:[ \\t]*>[ \\t]*)*) {0,3}-{2,}[ \\t]*$");

    /** 列表 marker 形态（setext 正文行排除）。 */
    private static final Pattern LIST_MARKER = Pattern.compile(
            "^[-*+][ \\t]+|^\\d+[.)][ \\t]+|^[-*+]$");

    /**
     * 单个标题的源文本坐标（0-based；字符偏移均指<b>源文本</b>绝对位置）。
     */
    public static final class Heading {
        /** 1..6。 */
        public final int level;
        /** 标题文本（去首尾空白；空标题 {@code #} 为 ""）。 */
        public final String text;
        /** 0-based 源行号。 */
        public final int sourceLineIndex;
        /** 标题正文起点在源文本中的绝对字符偏移（= HEADING_MARKER 注入位置）。 */
        public final int markerInsertIndex;
        /** 注入标记前需补一个空格（仅「空标题且行尾无空白」；保证 {@code #} 后空白使 ATX 成立）。 */
        public final boolean markerNeedsSpace;

        Heading(int level, String text, int sourceLineIndex, int markerInsertIndex,
                boolean markerNeedsSpace) {
            this.level = level;
            this.text = text;
            this.sourceLineIndex = sourceLineIndex;
            this.markerInsertIndex = markerInsertIndex;
            this.markerNeedsSpace = markerNeedsSpace;
        }
    }

    /** 注入标记后的渲染输入与标题元数据（{@link #injectMarkers} 产出）。 */
    public static final class MarkedSource {
        /** 注入标记后的源文本（喂给 Markwon；与源文本仅相差零宽标记）。 */
        public final String text;
        /** 与扫描同序（=源文本扫描序）的标题坐标。 */
        public final List<Heading> headings;
        /** 任务行坐标（{@link TaskCheckboxHelper#scan} 产出；与 {@code text} 同源）。 */
        public final List<TaskCheckboxHelper.TaskLine> taskLines;

        MarkedSource(String text, List<Heading> headings,
                     List<TaskCheckboxHelper.TaskLine> taskLines) {
            this.text = text;
            this.headings = headings;
            this.taskLines = taskLines;
        }
    }

    /** 大纲展示条目：标题 + 缩进级（level-1，0..5）。 */
    public static final class TocEntry {
        public final Heading heading;
        /** 展示缩进级（0 起；= level-1）。 */
        public final int indent;

        TocEntry(Heading heading, int indent) {
            this.heading = heading;
            this.indent = indent;
        }
    }

    /** setext 正文候选（局部辅助：下一行为下划线时构成 setext 标题）。 */
    private static final class SetextCandidate {
        final String text;
        final int insertIndex;
        final int lineIndex;

        SetextCandidate(String text, int insertIndex, int lineIndex) {
            this.text = text;
            this.insertIndex = insertIndex;
            this.lineIndex = lineIndex;
        }
    }

    /** 待注入的标记段。 */
    private static final class Insertion {
        final int pos;
        final char ch;
        final int count;
        /** 位置在行尾且行尾无空白时必须补一个空格才能使 ATX 成立（空标题）。 */
        final boolean padSpace;

        Insertion(int pos, char ch, int count, boolean padSpace) {
            this.pos = pos;
            this.ch = ch;
            this.count = count;
            this.padSpace = padSpace;
        }
    }

    /**
     * 全源扫描标题（跳过围栏代码块；CRLF 兼容；引用前缀+缩进规则见类注释）。
     * 返回空列表而非 null。
     */
    public static List<Heading> scan(String source) {
        if (source == null || source.isEmpty()) return Collections.emptyList();
        List<Heading> result = new ArrayList<>();
        int lineStart = 0;
        int lineIndex = 0;
        char fenceChar = 0;
        int fenceLen = 0;
        String pendingText = null;
        int pendingInsert = -1;
        int pendingLine = -1;

        while (lineStart <= source.length()) {
            int lineEnd = source.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = source.length();
            String body = source.substring(lineStart, lineEnd);
            if (body.endsWith("\r")) body = body.substring(0, body.length() - 1);

            String trimmed = body.trim();
            if (fenceChar != 0) {
                int run = fenceRun(trimmed, fenceChar);
                if (run >= fenceLen && run > 0) {
                    fenceChar = 0;
                    fenceLen = 0;
                }
                pendingText = null;   // 围栏内无正文-下划线配对
            } else if (isFenceStart(trimmed)) {
                fenceChar = trimmed.charAt(0);
                fenceLen = fenceRun(trimmed, fenceChar);
                pendingText = null;
            } else {
                Matcher m = ATX.matcher(body);
                if (m.matches()) {
                    fillAtx(result, body, m, lineIndex, lineStart);
                    pendingText = null;
                } else {
                    int ul = underlineLevel(body);
                    if (ul != 0) {
                        if (pendingText != null) {
                            result.add(new Heading(ul, pendingText,
                                    pendingLine, pendingInsert, false));
                        }
                        pendingText = null;
                    } else {
                        SetextCandidate cand = setextCandidate(body, lineIndex, lineStart);
                        if (cand != null) {
                            pendingText = cand.text;
                            pendingInsert = cand.insertIndex;
                            pendingLine = cand.lineIndex;
                        } else {
                            pendingText = null;
                        }
                    }
                }
            }

            if (lineEnd >= source.length()) break;
            lineStart = lineEnd + 1;
            lineIndex++;
        }
        return result;
    }

    /** ATX 匹配行 → 标题（level/文本/注入位置）。 */
    private static void fillAtx(List<Heading> result, String body, Matcher m,
                                int lineIndex, int lineStart) {
        int prefixLen = m.group(1).length();
        int indentLen = m.group(2).length();
        int hashLen = m.group(3).length();
        String g4 = m.group(4);
        String text = g4 != null ? g4.trim() : "";
        int insert;
        boolean needsSpace = false;   // 空标题且行尾无空白：注入前补空格使 ATX 成立
        if (g4 != null) {
            insert = lineStart + m.start(4);            // 正文首字符（标记注入点）
        } else {
            insert = lineStart + body.length();          // 空标题：行尾
            int n = body.length();
            if (n == 0 || (body.charAt(n - 1) != ' ' && body.charAt(n - 1) != '\t')) {
                needsSpace = true;
            }
        }
        result.add(new Heading(hashLen, text, lineIndex, insert, needsSpace));
    }

    /** 下划线级别：1（{@code =+}）或 2（{@code -{2,}}）；不构成下划线=0。 */
    private static int underlineLevel(String body) {
        if (UNDERLINE_EQ.matcher(body).matches()) return 1;
        if (UNDERLINE_DASH.matcher(body).matches()) return 2;
        return 0;
    }

    /** 本行是否可暂时作为「下一行是下划线则构成 setext」的正文候选；不是返回 null。 */
    private static SetextCandidate setextCandidate(String body, int lineIndex, int lineStart) {
        int q = quotePrefixLen(body);
        int indent = indentSpaces(body, q);
        if (indent > 3) return null;                     // 4+ 空格=缩进代码块，非段落行
        int contentStart = q + indent;
        int len = body.length();
        if (contentStart >= len) return null;            // 前缀后无内容（空白行）

        String content = body.substring(contentStart);
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return null;
        if (LIST_MARKER.matcher(content).find()) return null;   // list marker / hr 形态
        // 首字符为 '='（underline 混淆）→ 保守排除（= 开头文本行极罕见）
        if (trimmed.charAt(0) == '=') return null;
        // 注：以 '#' 开头但非合法 ATX（如 #foo）可作为 setext 正文（commonmark 实测对齐）
        return new SetextCandidate(trimmed, lineStart + contentStart, lineIndex);
    }

    /** 引用前缀长度（(?:[ \t]*>[ \t]*)* 连续匹配的字符数）。 */
    private static int quotePrefixLen(String body) {
        int i = 0;
        int n = body.length();
        while (i < n) {
            int j = i;
            while (j < n && (body.charAt(j) == ' ' || body.charAt(j) == '\t')) j++;
            if (j < n && body.charAt(j) == '>') {
                j++;
                while (j < n && (body.charAt(j) == ' ' || body.charAt(j) == '\t')) j++;
                i = j;
            } else {
                break;
            }
        }
        return i;
    }

    /** 从 from 起连续空格数（缩进；仅空格，tab 不算——commonmark tab=4 列=代码块）。 */
    private static int indentSpaces(String body, int from) {
        int i = from;
        while (i < body.length() && body.charAt(i) == ' ') i++;
        return Math.min(i - from, 4);   // 调用方按 <=3 判断；返回实际个数（最多4）
    }

    /**
     * 在源文本标题与任务行一次性注入标记，返回渲染输入与同源坐标。
     * 标题标记=U+200C×（扫描序+1）；任务标记=U+200B×（扫描序+1，委托
     * {@link TaskCheckboxHelper#scan}，公共 API 零改动）。
     */
    public static MarkedSource injectMarkers(String source) {
        if (source == null || source.isEmpty()) {
            return new MarkedSource(source == null ? "" : source,
                    Collections.emptyList(), Collections.emptyList());
        }
        List<Heading> headings = scan(source);
        List<TaskCheckboxHelper.TaskLine> taskLines = TaskCheckboxHelper.scan(source);
        if (headings.isEmpty() && taskLines.isEmpty()) {
            return new MarkedSource(source, headings, taskLines);
        }
        List<Insertion> ins = new ArrayList<>(headings.size() + taskLines.size());
        for (int i = 0; i < headings.size(); i++) {
            Heading h = headings.get(i);
            ins.add(new Insertion(h.markerInsertIndex, HEADING_MARKER, i + 1,
                    h.markerNeedsSpace));
        }
        for (int k = 0; k < taskLines.size(); k++) {
            TaskCheckboxHelper.TaskLine t = taskLines.get(k);
            ins.add(new Insertion(t.insertCharIndex, TaskCheckboxHelper.MARKER, k + 1, false));
        }
        StringBuilder sb = new StringBuilder(source);
        // 从后往前插入（按 pos 降序；位置互不重叠，仅防御性排序）
        Collections.sort(ins, (a, b) -> Integer.compare(b.pos, a.pos));
        for (Insertion in : ins) {
            if (in.pos < 0 || in.pos > sb.length()) continue;
            StringBuilder tok = new StringBuilder();
            if (in.padSpace) tok.append(' ');
            for (int k = 0; k < in.count; k++) tok.append(in.ch);
            sb.insert(in.pos, tok);
        }
        return new MarkedSource(sb.toString(), headings, taskLines);
    }

    /** 构建大纲展示条目（文档序=扫描序；缩进级=level-1）。 */
    public static List<TocEntry> buildTree(List<Heading> headings) {
        if (headings == null || headings.isEmpty()) return Collections.emptyList();
        List<TocEntry> out = new ArrayList<>(headings.size());
        for (Heading h : headings) {
            out.add(new TocEntry(h, Math.max(0, h.level - 1)));
        }
        return out;
    }

    /**
     * 在 {@code [from, to)} 区间内解码标题标记序号：找到该区间内<b>首个</b>
     * 连续 {@link #HEADING_MARKER} 段，计数-1=标题扫描序；区间内无标记 → -1。
     * 从区间起点向后扫描（容忍 span 起点与标记段起点之间的微小偏移）。
     */
    public static int decodeHeadingIndex(CharSequence seq, int from, int to) {
        if (seq == null || from < 0 || from >= seq.length()) return -1;
        if (to > seq.length()) to = seq.length();
        if (from >= to) return -1;
        int i = from;
        while (i < to && seq.charAt(i) != HEADING_MARKER) i++;
        if (i >= to) return -1;
        int count = 0;
        while (i + count < to && seq.charAt(i + count) == HEADING_MARKER) count++;
        return count > 0 ? count - 1 : -1;
    }

    // ── 基础工具 ────────────────────────────────────────────────────────────

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
