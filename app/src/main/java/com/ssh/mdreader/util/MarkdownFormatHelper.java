package com.ssh.mdreader.util;

/**
 * Markdown 编辑格式快捷插入纯函数层（路线图 #17 编辑格式工具栏）。
 *
 * <p>对标 markor（gsantner/markor）MarkdownActionButtons 源码行为实证：
 * <ul>
 *   <li><b>加粗/斜体</b>=runSurroundAction(&quot;**&quot;/&quot;_&quot;)：选中文字包裹切换（已包裹则去除），
 *       无选区=插入包裹符并把光标放中间，选中文字首尾空白留在包裹符外（trim 语义）。</li>
 *   <li><b>标题</b>=setOrUnsetHeadingWithLevel(level)：行级前缀切换——同级别移除、不同级别替换、无标题则插入；
 *       多行选区逐行独立切换。</li>
 *   <li><b>无序列表</b>=replaceWithUnorderedListPrefixOrRemovePrefix：行级切换——已是无序列表则移除，
 *       其它前缀（有序/标题/引用）替换为目标前缀，无前缀则插入。</li>
 * </ul>
 *
 * <p>全部为纯函数（无 Android 依赖），JVM 单测覆盖；返回 {@link Result}（新文本 + 新选区），
 * UI 层拿到后 setText + setSelection 即可。
 */
public final class MarkdownFormatHelper {

    private MarkdownFormatHelper() {}

    /** 一次格式操作的结果：新文本与新选区（选区已按编辑位移换算）。 */
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

    // ── 行级前缀识别 ─────────────────────────────────────────────────────────

    /** 前缀类别（检测优先级与 markor PREFIX_PATTERNS 一致：有序列表>标题>引用>无序列表）。 */
    private static final int P_ATX = 1;
    private static final int P_UNORDERED = 2;
    private static final int P_QUOTE = 3;
    private static final int P_ORDERED = 4;
    private static final int P_NONE = 0;

    /** 识别 rest（去前导空白后的行内容）开头的 markdown 行前缀。返回 {type, level(仅 ATX), markerEnd}。 */
    private static int[] detectPrefix(String rest) {
        if (rest.isEmpty()) return new int[]{P_NONE, 0, 0};
        // 有序列表：\d+[.)]\s
        int i = 0;
        while (i < rest.length() && Character.isDigit(rest.charAt(i))) i++;
        if (i > 0 && i < rest.length() && (rest.charAt(i) == '.' || rest.charAt(i) == ')')
                && i + 1 < rest.length() && rest.charAt(i + 1) == ' ') {
            return new int[]{P_ORDERED, 0, i + 2};
        }
        // ATX 标题：#{1,6}(空格|行尾)
        i = 0;
        while (i < rest.length() && i < 6 && rest.charAt(i) == '#') i++;
        if (i >= 1 && i <= 6 && (i == rest.length() || rest.charAt(i) == ' ')) {
            int level = i;
            int end = i;
            if (end < rest.length() && rest.charAt(end) == ' ') end++;   // 消费一个分隔空格
            return new int[]{P_ATX, level, end};
        }
        // 引用：> 后跟可选一个空格
        if (rest.charAt(0) == '>') {
            return new int[]{P_QUOTE, 0, (rest.length() > 1 && rest.charAt(1) == ' ') ? 2 : 1};
        }
        // 无序列表：[-*+]\s
        char c0 = rest.charAt(0);
        if ((c0 == '-' || c0 == '*' || c0 == '+') && rest.length() > 1 && rest.charAt(1) == ' ') {
            return new int[]{P_UNORDERED, 0, 2};
        }
        return new int[]{P_NONE, 0, 0};
    }

    // ── 选区包裹（加粗/斜体）─────────────────────────────────────────────────

    /**
     * 包裹/解包选区（markor runSurroundAction(open, close, trim=true) 语义）：
     * <ul>
     *   <li>空选区：在光标处插入 open+close，新选区=两者之间（可直接继续输入）。</li>
     *   <li>选区外紧邻 open…close：解包（去掉外层包裹，选区内容不变）。</li>
     *   <li>选区本身以 open 开头且以 close 结尾：解包（去掉内层包裹）。</li>
     *   <li>其余：包裹——选区首尾空白留在包裹符外（先取首个/末个非空白字符之间包裹，
     *       全空白选区直接整体包裹）。</li>
     * </ul>
     */
    public static Result wrapSelection(String text, int start, int end, String open, String close) {
        if (text == null) return new Result(null, start, end);
        String o = open == null ? "" : open;
        String c = close == null ? "" : close;
        int n = text.length();
        int s = clamp(start, 0, n);
        int e = clamp(end, 0, n);
        if (e < s) { int t = s; s = e; e = t; }
        if (o.isEmpty() && c.isEmpty()) return new Result(text, s, e);
        int ol = o.length(), cl = c.length();

        // 空选区：插入 open+close，光标置中
        if (s == e) {
            String nt = text.substring(0, s) + o + c + text.substring(s);
            int pos = s + ol;
            return new Result(nt, pos, pos);
        }

        // 选区外紧邻包裹 → 解包
        if (s >= ol && e + cl <= n
                && text.regionMatches(s - ol, o, 0, ol)
                && text.regionMatches(e, c, 0, cl)) {
            String nt = text.substring(0, s - ol) + text.substring(s, e) + text.substring(e + cl);
            return new Result(nt, s - ol, e - ol);
        }

        int selLen = e - s;
        // 选区本身以 open 开头且以 close 结尾 → 解包
        if (selLen >= ol + cl
                && text.regionMatches(s, o, 0, ol)
                && text.regionMatches(e - cl, c, 0, cl)) {
            String nt = text.substring(0, s) + text.substring(s + ol, e - cl) + text.substring(e);
            return new Result(nt, s, e - ol - cl);
        }

        // 包裹：trim 语义（首尾空白留在包裹符外）
        int first = s;
        while (first < e && Character.isWhitespace(text.charAt(first))) first++;
        int last = e - 1;
        while (last >= first && Character.isWhitespace(text.charAt(last))) last--;
        String nt;
        if (first > last) {
            // 全空白选区：整体包裹
            nt = text.substring(0, s) + o + text.substring(s, e) + c + text.substring(e);
        } else {
            nt = text.substring(0, s) + text.substring(s, first) + o
                    + text.substring(first, last + 1) + c
                    + text.substring(last + 1, e) + text.substring(e);
        }
        return new Result(nt, s + ol, e + ol);
    }

    // ── 行级前缀切换（标题/列表）────────────────────────────────────────────

    /** 标题切换：目标级别 level=1..6；多行选区逐行独立（markor setOrUnsetHeadingWithLevel 同款）。 */
    public static Result toggleHeading(String text, int start, int end, int level) {
        if (level < 1) level = 1;
        if (level > 6) level = 6;
        StringBuilder marker = new StringBuilder();
        for (int i = 0; i < level; i++) marker.append('#');
        marker.append(' ');
        return toggleLinePrefix(text, start, end, P_ATX, level, marker.toString());
    }

    /** 无序列表切换：已是无序列表则移除，其它前缀替换为「- 」，无前缀则插入（markor 同款）。 */
    public static Result toggleUnorderedList(String text, int start, int end) {
        return toggleLinePrefix(text, start, end, P_UNORDERED, 0, "- ");
    }

    /**
     * 行级前缀切换核心：作用于选区覆盖的所有行（空选区=光标所在行）。
     * 每行独立判定（detectPrefix）：
     * <ul>
     *   <li>已是目标前缀：移除（不符合级别则先替换成目标级别再视为已是）。</li>
     *   <li>是其它已知前缀（标题/列表/引用/有序）：替换为目标前缀。</li>
     *   <li>无前缀：在前导空白之后插入目标前缀。</li>
     * </ul>
     * 前导空白（行首缩进）与行尾换行保留；选区随编辑位移换算。
     */
    private static Result toggleLinePrefix(String text, int start, int end, int targetType, int targetLevel, String targetMarker) {
        if (text == null) return new Result(null, start, end);
        int n = text.length();
        int s = clamp(start, 0, n);
        int e = clamp(end, 0, n);
        if (e < s) { int t = s; s = e; e = t; }

        // 受影响行范围
        int firstLine = lineStartOf(text, s);
        int lastLine = (e > s) ? lineStartOf(text, e - 1) : firstLine;

        // 收集每行编辑（原文本偏移；行编辑互不重叠）
        java.util.List<int[]> edits = new java.util.ArrayList<>();   // {from, oldLen} + rep 存单独列表
        java.util.List<String> reps = new java.util.ArrayList<>();
        int line = firstLine;
        while (line <= n && line <= lastLine) {
            int lineEnd = text.indexOf('\n', line);
            if (lineEnd < 0) lineEnd = n;
            int contentEnd = lineEnd;   // 行内容含行内空白，不含换行符
            int wsEnd = line;
            while (wsEnd < contentEnd && (text.charAt(wsEnd) == ' ' || text.charAt(wsEnd) == '\t')) wsEnd++;

            int[] p = detectPrefix(stripTrailingWs(text.substring(wsEnd, contentEnd)));
            String rep;
            if (p[0] == targetType) {
                if (p[0] == P_ATX && p[1] != targetLevel) {
                    rep = targetMarker;              // 标题不同级别 → 替换
                } else {
                    rep = "";                        // 已是目标 → 移除
                }
            } else {
                rep = targetMarker;                  // 其它前缀/无前缀 → 替换/插入
            }

            // 无前缀时 oldLen=0=纯插入；否则移除旧前缀段（含其后的一个分隔空格）
            int oldLen = (p[0] == P_NONE) ? 0 : p[2];
            edits.add(new int[]{wsEnd, oldLen});
            reps.add(rep);
            if (lineEnd == n) break;
            line = lineEnd + 1;
        }

        if (edits.isEmpty()) return new Result(text, s, e);

        StringBuilder sb = new StringBuilder(text);
        int delta = 0;
        for (int i = 0; i < edits.size(); i++) {
            int[] ed = edits.get(i);
            String rep = reps.get(i);
            sb.replace(ed[0] + delta, ed[0] + delta + ed[1], rep);
            delta += rep.length() - ed[1];
        }
        int ns = shiftPoint(s, edits, reps);
        int ne = shiftPoint(e, edits, reps);
        return new Result(sb.toString(), ns, ne);
    }

    /**
     * 原文本位置 p 经一批（不相交、按 from 升序）编辑后的新位置。
     *
     * <p>正确性关键：与每个编辑比较时始终使用<b>原文本坐标</b>的 p，不能把已累计位移混入比较
     *（否则跨行多编辑时，落点会被后续「不该影响它」的编辑二次位移）。累计 delta 只用于最终计算。</p>
     */
    private static int shiftPoint(int p, java.util.List<int[]> edits, java.util.List<String> reps) {
        int delta = 0;
        for (int i = 0; i < edits.size(); i++) {
            int from = edits.get(i)[0];
            int oldLen = edits.get(i)[1];
            String rep = reps.get(i);
            if (p < from) break;                          // 后续编辑起点更大，均不影响
            if (p >= from + oldLen) {
                delta += rep.length() - oldLen;           // 该编辑整体位于 p 之前
            } else {
                return from + rep.length() + delta;       // p 落在被替换/删除区内：映射到替换结尾
            }
        }
        return p + delta;
    }

    // ── 工具 ────────────────────────────────────────────────────────────────

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /** 行起始（p 所在行第一个字符下标；行首本身）。 */
    private static int lineStartOf(String text, int p) {
        if (p <= 0) return 0;
        int idx = text.lastIndexOf('\n', p - 1);
        return idx + 1;
    }

    private static String stripTrailingWs(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
