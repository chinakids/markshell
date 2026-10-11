package com.ssh.mdreader.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 链接/图片插入纯函数层（JVM 可测，无 Android 依赖），对齐 markor
 * {@code insert_link/insert_image} 语义（AttachLinkOrFileDialog + Link.extract 实证 2026-10-11）。
 *
 * <p><b>对齐的 markor 语义</b>：</p>
 * <ul>
 *   <li>模板：链接 {@code [title](url)}、图片 {@code ![title](url)}（markor getLinkFormat/getImageFormat）。</li>
 *   <li>已有链接识别：markor {@code MarkdownSyntaxHighlighter.LINK} 同源正则
 *       {@code (?m)(!)?\[([^]]*)]\(([^()]*(?:\([^()]*\)[^()]*)*)\)}，在光标所在行搜索；
 *       命中「光标位于链接语法内」→ 预填标题/地址并<b>整体替换</b>该链接（markor Link.extract 语义：
 *       {@code start <= pos && end >= pos}，end 为 exclusive 边界）。</li>
 *   <li>有选区：替换整个选区（markor dialog 语义：选区文本预填标题，确认后 replace 选区）。</li>
 *   <li>URL 为空：不插入（markor {@code GsTextUtils.isNullOrEmpty(path) return}）。</li>
 *   <li>地址编码：仅空格→{@code %20}（markor formatLink 同款；不做整体 URL 编码）。</li>
 *   <li>预填解码：percentDecode 复用 {@link LinkTargetHelper} 语义（{@code +} 字面、非法 {@code %} 保留；
 *       markor 用 decodeUrl，本项目统一用已有百分号解码，往返稳定：解码的 %20 重插时再编码）。</li>
 * </ul>
 *
 * <p><b>与 markor 的取舍（本轮记录）</b>：标题不做 {@code |→/} 替换（CommonMark 中 {@code |} 在链接文本
 * 字面合法，替换有损用户文本；markor 为 GitHub 表格兼容而做）；不做本地文件浏览/相册（本项目为
 * SSH 远程阅读器，目标地址由用户直接输入绝对 URL 或相对路径）。</p>
 */
public final class MarkdownLinkHelper {

    private MarkdownLinkHelper() {
    }

    /** markor MarkdownSyntaxHighlighter.LINK 同源正则（图片=前导 !，url 支持一层嵌套括号）。 */
    static final Pattern LINK = Pattern.compile("(?m)(!)?\\[([^]]*)]\\(([^()]*(?:\\([^()]*\\)[^()]*)*)\\)");

    /** 链接识别结果（extract 产出）。 */
    public static final class LinkInfo {
        public final String title;
        public final String url;
        public final boolean isImage;
        public final int start;
        public final int end;

        LinkInfo(String title, String url, boolean isImage, int start, int end) {
            this.title = title;
            this.url = url;
            this.isImage = isImage;
            this.start = start;
            this.end = end;
        }

        public boolean isValid() {
            return !url.isEmpty() && start >= 0 && end >= 0;
        }
    }

    /** 插入结果：新文本 + 新光标位置（项目 Result 惯例）。 */
    public static final class Result {
        public final String text;
        public final int selStart;
        public final int selEnd;

        public Result(String text, int selStart, int selEnd) {
            this.text = text;
            this.selStart = selStart;
            this.selEnd = selEnd;
        }
    }

    /**
     * 提取 {@code pos} 处所在的链接（markor Link.extract 语义：所在行内第一个
     * 覆盖 {@code pos} 的链接匹配；图片链接 {@code isImage=true}）。未命中返回 {@code isValid()==false}。
     */
    public static LinkInfo extract(String text, int pos) {
        if (text == null || pos < 0 || pos > text.length()) return new LinkInfo("", "", false, -1, -1);
        // pos 所在行 [lineStart, lineEnd)（行尾换行不含；复刻 markor 行内搜索）
        int lineStart = 0;
        int nl = pos > 0 ? text.lastIndexOf('\n', pos - 1) : -1;
        if (nl >= 0) lineStart = nl + 1;
        int lineEnd = text.indexOf('\n', pos);
        if (lineEnd < 0) lineEnd = text.length();

        Matcher m = LINK.matcher(text.subSequence(lineStart, lineEnd));
        while (m.find()) {
            int start = m.start(0) + lineStart;
            int end = m.end(0) + lineStart;
            if (start <= pos && end >= pos) {
                String url = LinkTargetHelper.percentDecode(m.group(3)).trim();
                return new LinkInfo(m.group(2), url, m.group(1) != null, start, end);
            }
        }
        return new LinkInfo("", "", false, -1, -1);
    }

    /**
     * 插入链接/图片（markor insert_link/insert_image 语义）：
     * <ul>
     *   <li>{@code url} 空（null/空串/纯空白）→ no-op，返回原文本与原选区；</li>
     *   <li>选区非空（{@code start != end}）→ 替换选区 {@code [start,end)} 为模板；</li>
     *   <li>选区为空 → 光标处命中链接（{@link #extract}）→ 替换整链接；否则光标处插入。</li>
     * </ul>
     * 返回选区 = 插入内容结尾（单点光标）。
     */
    public static Result insertLink(String text, int start, int end, String title, String url, boolean isImage) {
        if (text == null) return new Result(null, start, end);
        if (url == null || url.trim().isEmpty()) return new Result(text, start, end);
        int s = clamp(Math.min(start, end), 0, text.length());
        int e = clamp(Math.max(start, end), 0, text.length());

        int insStart = s;
        int insEnd = e;
        if (s == e) {
            LinkInfo li = extract(text, s);
            if (li.isValid()) {
                insStart = li.start;
                insEnd = li.end;
            }
        }
        String t = title == null ? "" : title.trim();
        String linkText = (isImage ? "![" : "[") + t + "](" + url.trim().replace(" ", "%20") + ")";
        String nt = text.substring(0, insStart) + linkText + text.substring(insEnd);
        int cursor = insStart + linkText.length();
        return new Result(nt, cursor, cursor);
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        return v > hi ? hi : v;
    }
}
