package com.ssh.mdreader.util;

import java.util.List;

/**
 * Markdown 内链点击纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（路线图第二轮 #1，2026-10-03 javap 实证）</b>：Markwon core 4.6.2 已把链接
 * 渲染为 {@code io.noties.markwon.core.spans.LinkSpan}（{@code extends android.text.style.URLSpan}，
 * javap 实证：{@code getLink()/onClick(View)} 存在，{@code LinkSpanFactory} 经
 * {@code CoreProps.LINK_DESTINATION} 原样取链接目的地）——渲染链路完整，但 MarkShell
 * 从未接线点击：{@code MarkdownReaderActivity} 未注册 LinkMovementMethod（与批注/任务行
 * 自绘触摸拦截互斥），{@code AnnotationOverlayHelper#handleAnnotationClick} 只命中
 * AnnotationSpan/TaskListSpan，链接是纯死区。竞品（markor {@code open_link}、Haven/electerm）
 * 全部支持；对 SSH 阅读器，相对链接 {@code [说明](docs/guide.md)} 应打开<b>远端同目录文件</b>
 * 而非浏览器——这是本层存在的意义。</p>
 *
 * <p><b>点击优先级（本卡设计决策）</b>：批注 span（选中添加的批注）&gt; 链接 &gt; 任务行
 * checkbox。理由：链接文本自带下划线/颜色是明确可点击元素；任务行内点非链接区域（行首
 * 勾选框/普通文本）仍翻转 checkbox，行为无回退。批注优先与既往一致（选中文本主要交互）。</p>
 *
 * <p><b>解码规则</b>：Markdown 链接目的地可能含 URL 编码（{@code %20} 等），本层对
 * <b>远程文件/锚点</b>做百分号解码、对 <b>外部链接</b>不做（保持原文交给浏览器，避免
 * 误改 query）。{@code +} 一律保留字面（文件名常见加号；不按 application/x-www-form-urlencoded
 * 语义解码）。非法 {@code %} 序列原样保留。</p>
 */
public final class LinkTargetHelper {

    private LinkTargetHelper() {
    }

    /** 链接分类。 */
    public enum Kind {
        /** 外部 URL（带 scheme：http/https/mailto/ftp 等），交给系统浏览器/邮件客户端。 */
        EXTERNAL_URL,
        /** 远程文件路径（相对当前文件目录或绝对路径），打开对应远端文件。 */
        REMOTE_FILE,
        /** 页内锚点（{@code #xxx}），跳转匹配标题。 */
        PAGE_ANCHOR,
        /** 空/无效（纯空白、只有 # 等），不消费点击。 */
        INVALID
    }

    /** 分类结果。 */
    public static final class Resolved {
        public final Kind kind;
        /** 分类后目标：EXTERNAL_URL=原始链接；REMOTE_FILE=解析后的远端绝对路径；PAGE_ANCHOR=解码后的锚点名。 */
        public final String target;

        Resolved(Kind kind, String target) {
            this.kind = kind;
            this.target = target;
        }
    }

    /** scheme 判定：字母开头、后跟字母/数字/+/./-，后接 ':'。 */
    private static boolean hasScheme(String s) {
        int i = 0;
        if (i >= s.length() || !Character.isLetter(s.charAt(i))) return false;
        i++;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ':') return true;
            if (!(Character.isLetterOrDigit(c) || c == '+' || c == '.' || c == '-')) return false;
            i++;
        }
        return false;
    }

    /**
     * 分类链接。{@code currentFilePath}=当前正在阅读的远端文件绝对路径（用于相对链接
     * 解析基目录）；非空时 REMOTE_FILE 的 target 已解析为绝对路径。
     */
    public static Resolved classify(String link, String currentFilePath) {
        if (link == null) return new Resolved(Kind.INVALID, null);
        String s = link.trim();
        if (s.isEmpty() || s.equals("#")) return new Resolved(Kind.INVALID, null);

        if (s.startsWith("#")) {
            String anchor = percentDecode(s.substring(1)).trim();
            return anchor.isEmpty() ? new Resolved(Kind.INVALID, null)
                    : new Resolved(Kind.PAGE_ANCHOR, anchor);
        }
        if (hasScheme(s)) {
            return new Resolved(Kind.EXTERNAL_URL, s);
        }
        String path = percentDecode(s);
        return new Resolved(Kind.REMOTE_FILE,
                currentFilePath == null || currentFilePath.isEmpty() ? path
                        : resolveRemotePath(currentFilePath, path));
    }

    /**
     * 相对链接 → 远端绝对路径：以 {@code currentFilePath} 所在目录为基，规范化
     * {@code ./}/{@code ../}（根之上回退到根）；{@code link} 以 {@code /} 开头=直接使用
     * （仍规范化内部点段）。纯逻辑、无 IO。
     */
    public static String resolveRemotePath(String currentFilePath, String link) {
        if (link == null || link.isEmpty()) return link;
        if (currentFilePath == null || currentFilePath.isEmpty()) return normalize(link);
        // 基目录 = currentFilePath 的父目录（保留 / 分隔；形如 /a/b/readme.md → /a/b）
        int slash = currentFilePath.lastIndexOf('/');
        String base = slash >= 0 ? currentFilePath.substring(0, slash) : currentFilePath;
        if (base.isEmpty()) base = "/";
        if (link.startsWith("/")) return normalize(link);
        String combined = (base.endsWith("/") ? base : base + "/") + link;
        return normalize(combined);
    }

    /** 路径规范化：按 '/' 分段，'.' 去、'..' 上溯（根顶）、普通段保留；结果以 '/' 分隔。 */
    static String normalize(String path) {
        if (path == null || path.isEmpty()) return path;
        boolean absolute = path.startsWith("/");
        String[] parts = path.split("/");
        java.util.ArrayList<String> stack = new java.util.ArrayList<>();
        for (String p : parts) {
            if (p.isEmpty() || p.equals(".")) continue;
            if (p.equals("..")) {
                if (!stack.isEmpty() && !stack.get(stack.size() - 1).equals("..")) {
                    stack.remove(stack.size() - 1);
                } else if (!absolute) {
                    stack.add("..");
                }
                // 绝对路径下 '..' 在根顶：丢弃
            } else {
                stack.add(p);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (absolute) sb.append('/');
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) sb.append('/');
            sb.append(stack.get(i));
        }
        if (sb.length() == 0) return absolute ? "/" : ".";
        return sb.toString();
    }

    /**
     * 百分号解码（UTF-8）：{@code %XX}→字符，非法的 {@code %} 序列与 {@code +} 保留原样；
     * 无变化时返回原字符串（不新分配）。
     */
    static String percentDecode(String s) {
        if (s == null || s.indexOf('%') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        byte[] buf = new byte[s.length()];
        int n = 0, i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = hex(s.charAt(i + 1));
                int lo = hex(s.charAt(i + 2));
                if (hi >= 0 && lo >= 0) {
                    buf[n++] = (byte) ((hi << 4) | lo);
                    i += 3;
                    continue;
                }
            }
            if (n > 0) {
                out.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
                n = 0;
            }
            out.append(c);
            i++;
        }
        if (n > 0) {
            out.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private static int hex(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    /**
     * 锚点 → 标题扫描序：先精确匹配（忽略大小写与两端空白），再 slug 匹配
     * （双方 slugify：小写、非字母数字→'-'、连续 '-' 合并、两端去 '-'）。均无 → -1。
     */
    public static int findAnchorHeading(List<TocHelper.Heading> headings, String anchor) {
        if (headings == null || anchor == null || anchor.isEmpty()) return -1;
        String norm = anchor.trim();
        for (int i = 0; i < headings.size(); i++) {
            if (headings.get(i).text == null) continue;
            if (headings.get(i).text.trim().equalsIgnoreCase(norm)) return i;
        }
        String slug = slugify(norm);
        if (slug.isEmpty()) return -1;
        for (int i = 0; i < headings.size(); i++) {
            String t = headings.get(i).text;
            if (t == null) continue;
            String ts = slugify(t);
            if (!ts.isEmpty() && ts.equals(slug)) return i;
        }
        return -1;
    }

    /** slugify：小写；字母数字保留，其余→'-'；连续 '-' 合并；两端去 '-'。空输入返回 "". */
    static String slugify(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastDash = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            char lo = Character.toLowerCase(c);
            if (Character.isLetterOrDigit(lo)) {
                sb.append(lo);
                lastDash = false;
            } else if (!lastDash) {
                sb.append('-');
                lastDash = true;
            }
        }
        int start = 0, end = sb.length();
        while (start < end && sb.charAt(start) == '-') start++;
        while (end > start && sb.charAt(end - 1) == '-') end--;
        return sb.substring(start, end);
    }
}
