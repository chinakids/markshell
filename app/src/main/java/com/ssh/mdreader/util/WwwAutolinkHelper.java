package com.ssh.mdreader.util;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * GFM {@code www.} 裸域自动链接匹配纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（迭代110，2026-10-10）</b>：commonmark-ext-autolink 0.13.0 的
 * {@code AutolinkPostProcessor} 构造器仅启用 {@code EnumSet.of(URL, EMAIL)}（本地
 * javap 字节码实证，底层 nibor autolink 0.10.0 库本身支持 {@code WWW} 类型，是库
 * 未启用）——GFM 规范要求 {@code www.} 裸域自动链化（自动补 {@code http} scheme），
 * 阅读器出现「打开 www.example.com 查看」时无链接。本层按 GFM spec
 * 「Autolinks (extension)」实现完整 www 规则，由 {@link WwwAutolinkPostProcessor}
 * 在 commonmark 解析后置处理中消费。</p>
 *
 * <p><b>匹配规则</b>（GFM spec 权威原文，2026-10-10 经 github/cmark-gfm
 * test/spec.txt 提取）：</p>
 * <ul>
 *   <li><b>前边界</b>：{@code www.} 只能出现在行首、空白后、或定界符
 *       {@code * _ ~ (} 之后（本层检查字面量内前一字符；节点级边界由调用方负责）。</li>
 *   <li><b>valid domain</b>：{@code www.} 后跟由 ASCII 字母数字、{@code _}、{@code -}
 *       组成的段，段间以 {@code .} 分隔；<b>至少一个句点</b>；<b>最后两段不得含
 *       {@code _}</b>。</li>
 *   <li><b>路径</b>：domain 后跟零或多个非空白非 {@code <} 字符；<b>额外排除
 *       项目定位标记字符 {@code U+200B}/{@code U+200C}</b>（任务清单/大纲零宽标记，
 *       非用户内容，防污染链接目标——GFM 原文无此项，工程化偏差）。</li>
 *   <li><b>扩展路径验证</b>：尾随标点 {@code ? ! . , : * _ ~} 不计入（可多个）；
 *       以 {@code )} 结尾时括号配平（右括号多于左括号则剔除多余尾随右括号）；
 *       以 {@code ;} 结尾且紧邻为 {@code &[A-Za-z0-9]+;}（entity 引用形态）时该
 *       entity 整体不计入；{@code <} 立即结束（路径收集时已按此截断）。</li>
 *   <li><b>目标</b>：{@code http://} + 匹配文本（GFM 规定 scheme 自动插入）。</li>
 * </ul>
 *
 * <p><b>不匹配</b>：{@code http://www.x.com}（www 前为 {@code /}，非边界）；{@code
 * foowww.x.com}（粘连）；{@code www.a.b_}（最后两段含下划线——两段时两段都是最后
 * 两段）；{@code www.example.com} 后的成对 {@code &amp;} 实体；行内/围栏代码（Code/
 * FencedCodeBlock 节点非 Text，由 {@link WwwAutolinkPostProcessor} 天然跳过）。</p>
 */
public final class WwwAutolinkHelper {

    private WwwAutolinkHelper() {
    }

    /** GFM 规定的链接前置定界符（外加行首/空白）。 */
    private static final String BOUNDARY_DELIMS = "*_~(";

    /** GFM 扩展路径验证的尾随标点。 */
    private static final String TRAILING_PUNCT = "?!.,:*_~";

    /** 任务清单行定位标记（U+200B，见 {@link TaskCheckboxHelper}）。 */
    private static final char MARKER_TASKS = '\u200B';
    /** 大纲标题定位标记（U+200C，见 {@link TocHelper}）。 */
    private static final char MARKER_HEADING = '\u200C';

    /** 单处 www 自动链接：在源文本中的区间与补全 scheme 的目标。 */
    public static final class WwwLink {
        /** 匹配起点（含，字符偏移）。 */
        public final int begin;
        /** 匹配终点（不含，字符偏移）。 */
        public final int end;
        /** 链接目标（{@code http://} + 原文）。 */
        @NonNull
        public final String target;

        WwwLink(int begin, int end, @NonNull String target) {
            this.begin = begin;
            this.end = end;
            this.target = target;
        }
    }

    /**
     * 在 {@code text} 中查找全部 GFM www 自动链接区间。
     *
     * @param text 待扫描文本（可为空串，自动返回空列表）
     * @return 按出现顺序升序、互不重叠的匹配；无匹配返回空列表
     */
    @NonNull
    public static List<WwwLink> findLinks(@NonNull String text) {
        List<WwwLink> out = new ArrayList<>();
        int len = text.length();
        int i = 0;
        while (i < len) {
            int idx = text.indexOf("www.", i);
            if (idx < 0) {
                break;
            }
            if (idx > 0 && !isBoundary(text.charAt(idx - 1))) {
                // 前边界不符：跳过该位置继续（注意保留下一处 www. 的可能性）
                i = idx + 1;
                continue;
            }
            WwwLink link = matchAt(text, idx);
            if (link != null) {
                out.add(link);
                i = link.end;
            } else {
                i = idx + 1;
            }
        }
        return out;
    }

    /** GFM 前边界判定：行首（由调用点处理）、空白、定界符 {@code * _ ~ (}，
     * 或项目零宽定位标记（U+200B/U+200C——它们是无意义注入字符，不代表文本粘连，
     * 若算作「非边界」会误伤标题/任务行内的链接，如 {@code # &lt;U+200C&gt;www.x.com}）。 */
    private static boolean isBoundary(char c) {
        return Character.isWhitespace(c) || BOUNDARY_DELIMS.indexOf(c) >= 0
                || c == MARKER_TASKS || c == MARKER_HEADING;
    }

    /** ASCII 字母数字（GFM 的 alphanumeric 指 ASCII，避免中文/全角误入域名段）。 */
    private static boolean isAsciiAlnum(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** GFM domain 段字符：ASCII 字母数字、{@code _}、{@code -}。 */
    private static boolean isSegChar(char c) {
        return isAsciiAlnum(c) || c == '_' || c == '-';
    }

    /** 路径字符：非空白、非 {@code <}、非项目零宽标记。 */
    private static boolean isPathChar(char c) {
        return !Character.isWhitespace(c) && c != '<' && c != MARKER_TASKS && c != MARKER_HEADING;
    }

    /** 在 {@code idx}（指向 {@code w}）处尝试完整匹配，失败返回 {@code null}。 */
    private static WwwLink matchAt(@NonNull String text, int idx) {
        int len = text.length();
        int pos = idx + 4; // 跳过 "www."
        // ── 解析 domain 段（每段后须紧跟 "." 才能继续，至少两段） ──
        List<int[]> segs = new ArrayList<>();
        int next = pos;
        while (next < len && isSegChar(text.charAt(next))) {
            next++;
        }
        if (next == pos) {
            return null; // "www." 后无任何段字符
        }
        segs.add(new int[]{pos, next});
        while (next < len && text.charAt(next) == '.') {
            int segStart = next + 1;
            int p = segStart;
            while (p < len && isSegChar(text.charAt(p))) {
                p++;
            }
            if (p == segStart) {
                break; // "." 后无段字符：domain 到此为止
            }
            segs.add(new int[]{segStart, p});
            next = p;
        }
        if (segs.size() < 2) {
            return null; // 至少一个句点（两段）
        }
        // 最后两段不得含下划线
        int n = segs.size();
        if (containsUnderscore(text, segs.get(n - 1)) || containsUnderscore(text, segs.get(n - 2))) {
            return null;
        }
        int domainEnd = segs.get(n - 1)[1];

        // ── 路径：domain 后零或多个路径字符（< 与空白在 isPathChar 即终止） ──
        int j = domainEnd;
        while (j < len && isPathChar(text.charAt(j))) {
            j++;
        }

        // ── 扩展路径验证 1：尾随标点 ──
        while (j > domainEnd && TRAILING_PUNCT.indexOf(text.charAt(j - 1)) >= 0) {
            j--;
        }
        // ── 扩展路径验证 2：以 ) 结尾时括号配平 ──
        if (j > domainEnd && text.charAt(j - 1) == ')') {
            int open = 0;
            int close = 0;
            for (int k = idx; k < j; k++) {
                char c = text.charAt(k);
                if (c == '(') {
                    open++;
                } else if (c == ')') {
                    close++;
                }
            }
            while (j > domainEnd && text.charAt(j - 1) == ')' && close > open) {
                j--;
                close--;
            }
        }
        // ── 扩展路径验证 3：以 ; 结尾的 entity 引用形态 ──
        if (j > domainEnd && text.charAt(j - 1) == ';') {
            int amp = -1;
            for (int k = j - 2; k >= idx; k--) {
                if (text.charAt(k) == '&') {
                    amp = k;
                    break;
                }
            }
            if (amp >= 0 && isAsciiAlnumRun(text, amp + 1, j - 1)) {
                j = amp;
            }
        }

        if (j <= idx + 4) {
            return null; // 防御：剩余不足完整 "www." 前缀
        }
        return new WwwLink(idx, j, "http://" + text.substring(idx, j));
    }

    private static boolean containsUnderscore(@NonNull String text, int[] seg) {
        for (int k = seg[0]; k < seg[1]; k++) {
            if (text.charAt(k) == '_') {
                return true;
            }
        }
        return false;
    }

    /** 检查 {@code [from, to)} 区间是否为非空 ASCII 字母数字串。 */
    private static boolean isAsciiAlnumRun(@NonNull String text, int from, int to) {
        if (to <= from) {
            return false;
        }
        for (int k = from; k < to; k++) {
            if (!isAsciiAlnum(text.charAt(k))) {
                return false;
            }
        }
        return true;
    }
}
