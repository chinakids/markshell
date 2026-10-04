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
    private static final int P_UNCHECKED = 5;   // [-*+] [ ]
    private static final int P_CHECKED = 6;     // [-*+] [x] / [X]
    private static final int P_NONE = 0;

    /** 识别 rest（去前导空白后的行内容）开头的 markdown 行前缀。返回 {type, level(仅 ATX), markerEnd}。 */
    private static int[] detectPrefix(String rest) {
        if (rest.isEmpty()) return new int[]{P_NONE, 0, 0};
        // 任务清单：[-*+] [ ] / [-*+] [xX]（必须在无序列表之前判定，markor PREFIX_PATTERNS 同序）
        char c0 = rest.charAt(0);
        // 结构（6 字符前缀）：0=marker 1=' ' 2='[' 3=box 4=']' 5=' '（markor [\sxX] 字符集同序）
        if ((c0 == '-' || c0 == '*' || c0 == '+') && rest.length() >= 6
                && rest.charAt(1) == ' ' && rest.charAt(2) == '[' && rest.charAt(3) != ']'
                && rest.charAt(4) == ']' && rest.charAt(5) == ' ') {
            char box = rest.charAt(3);
            if (box == ' ') return new int[]{P_UNCHECKED, 0, 6};
            if (box == 'x' || box == 'X') return new int[]{P_CHECKED, 0, 6};
        }
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
        return toggleLinePrefix(text, start, end, P_ATX, level, marker.toString(), "");
    }

    /** 无序列表切换：已是无序列表则移除，其它前缀替换为「- 」，无前缀则插入（markor 同款）。 */
    public static Result toggleUnorderedList(String text, int start, int end) {
        return toggleLinePrefix(text, start, end, P_UNORDERED, 0, "- ", "");
    }

    /** 引用切换：已是引用（&gt; ）则移除，其它前缀替换为「&gt; 」，无前缀则插入（markor toggleQuote 同款）。 */
    public static Result toggleQuote(String text, int start, int end) {
        return toggleLinePrefix(text, start, end, P_QUOTE, 0, "> ", "");
    }

    /** 有序列表切换：已是有序列表则移除，其它前缀替换为「1. 」，无前缀则插入；随后对受影响列表段自动重编号（markor replaceWithOrderedListPrefixOrRemovePrefix + runRenumberOrderedListIfRequired）。 */
    public static Result toggleOrderedList(String text, int start, int end) {
        Result r = toggleLinePrefix(text, start, end, P_ORDERED, 0, "1. ", "");
        if (r.text == null || r.text.equals(text)) return r;
        return renumberOrderedList(r.text, r.selStart, r.selEnd);
    }

    // ── 回车自动续行（迭代82：markor AutoTextFormatter.autoIndent 行为语义对位）────

    /**
     * 回车自动续行（markor AutoTextFormatter.autoIndent 源码语义）：输入换行时（由 UI 层按
     * markor isNewLine 条件判定）计算<b>应追加在输入之后的续行内容</b>。
     *
     * <ul>
     *   <li>解析光标所在行（markor ListLine 同型：行首缩进原文保留、行内容前缀识别复用 {@link #detectPrefix}）；</li>
     *   <li>行是有序列表且行有内容且光标位于前缀之后：追加=缩进 + 下一个编号 + 分隔符 + 空格
     *       （编号=当前数字+1，markor getNextOrderedValue(value,false) 数字分支；字母列表分支当前
     *       前缀正则不可达=本项目仅数字，迭代81 同注）；</li>
     *   <li>行是任务清单（勾选/未勾选同族）：追加=缩进 + 原 marker + &quot;[ ] &quot;（新项=未勾选，
     *       markor newItemPrefix 同型：PREFIX_CHECKBOX_LIST 左右组拼装）；</li>
     *   <li>行是无序列表：追加=缩进 + 原 marker + 空格；</li>
     *   <li>其它行（普通/引用/标题等）：追加=仅行前导缩进（markor 引用不续 &gt;、标题不续 #、代码块
     *       缩进保留，colloquially「智能缩进」）；</li>
     *   <li>返回空串=无需追加（调用方原样返回输入）。</li>
     * </ul>
     *
     * <p><b>与 markor 的差异（如实标注）</b>：markor 续项条件为
     * {@code lineEnd != groupEnd && dend >= groupEnd}，其中行尾空白也算「有内容」；本项目判定
     * 「行有内容」基于 detectPrefix 去尾空白后的前缀（行=前缀+尾随空格如 {@code "1.  "} 视为无内容
     * 不续，行为更保守）。行=恰好只有前缀（{@code "1. "}）两者一致=不续。</p>
     */
    public static String newlineContinuation(String text, int cursor) {
        if (text == null) return "";
        int n = text.length();
        if (n == 0) return "";
        int pos = clamp(cursor, 0, n);
        int lineStart = lineStartOf(text, pos);
        int lineEnd = text.indexOf('\n', lineStart);
        if (lineEnd < 0) lineEnd = n;
        String line = text.substring(lineStart, lineEnd);
        // 行首缩进原文保留（markor line.substring(0, indentEnd) 同型；空白=空格+制表，制表按 1 字符保留）
        int wsEnd = 0;
        while (wsEnd < line.length() && (line.charAt(wsEnd) == ' ' || line.charAt(wsEnd) == '\t')) wsEnd++;
        String indent = line.substring(0, wsEnd);
        int[] p = detectPrefix(stripTrailingWs(line.substring(wsEnd)));
        int type = p[0];
        int prefixEnd = wsEnd + p[2];   // 前缀结束（绝对坐标；detectPrefix markerEnd 对去除前导空白后的内容）
        // markor autoIndent 分支门：列表续项需要「行有内容且光标在前缀之后」
        //（lineEnd != groupEnd && dend >= groupEnd——本项目用原行长度与光标位置对位）
        boolean canContinue = prefixEnd < line.length() && pos >= prefixEnd;
        if (canContinue) {
            if (type == P_ORDERED) {
                // 数字串=[wsEnd, prefixEnd-2)（digits + divider + space）；分隔符在 prefixEnd-2
                String value = line.substring(wsEnd, prefixEnd - 2);
                char delimiter = line.charAt(prefixEnd - 2);
                return indent + nextOrderedValue(value, false) + delimiter + " ";
            }
            if (type == P_UNCHECKED || type == P_CHECKED) {
                return indent + line.charAt(wsEnd) + " [ ] ";   // 新项=未勾选（markor newItemPrefix 同）
            }
            if (type == P_UNORDERED) {
                return indent + line.charAt(wsEnd) + " ";
            }
        }
        return indent;
    }

    // ── 有序列表自动重编号（迭代81：markor AutoTextFormatter.renumberOrderedList 对齐）────

    /** 列表层级容差：缩进差 <=2 视作同级，>2 视作子列表（markor indentSlack 同值）。 */
    private static final int INDENT_SLACK = 2;

    /**
     * 有序列表自动重编号（markor AutoTextFormatter.renumberOrderedList 语义对位）：
     * <ul>
     *   <li>从选区起始行向上求所属列表顶（getOrderedListStart + getLevelStart 同型：逐级父级、同级向左扩展）；</li>
     *   <li>自顶向下遍历：同级连续有序项按 1..N 递增；更深缩进（差&gt;2）=子列表独立从 1；更浅=弹栈回父层；</li>
     *   <li>同级不同分隔符（. 与 )）=新列表从 1 重编号；空行不打断；列表外普通行弹空栈=整体放弃（markor 同）；</li>
     *   <li>首行不是有序行时 no-op（markor {@code !firstLine.isOrderedList} 同）；</li>
     *   <li>编号数字长度变化时选区位移换算（编辑整体位于端点之前才位移，markor shifts 同型）。</li>
     * </ul>
     */
    public static Result renumberOrderedList(String text, int selStart, int selEnd) {
        if (text == null) return new Result(null, selStart, selEnd);
        int n = text.length();
        int s = clamp(selStart, 0, n);
        int e = clamp(selEnd, 0, n);
        if (e < s) { int t = s; s = e; e = t; }

        java.util.List<OrderedLine> lines = parseOrderedLines(text);
        if (lines.isEmpty()) return new Result(text, s, e);
        int startIdx = lineIndexOf(lines, s);
        if (startIdx < 0) return new Result(text, s, e);

        int top = orderedListStart(lines, startIdx);
        OrderedLine first = lines.get(top);
        if (!first.isOrdered) return new Result(text, s, e);

        java.util.List<int[]> edits = new java.util.ArrayList<>();   // {numStart, numEnd}（原文本坐标）
        java.util.List<String> reps = new java.util.ArrayList<>();
        java.util.List<OrderedLine> stack = new java.util.ArrayList<>();
        stack.add(first);
        int delta0 = 0, delta1 = 0;

        for (int idx = top; idx < lines.size(); idx++) {
            OrderedLine line = lines.get(idx);
            // 遍历条件：自列表顶向下（markor firstLine.isParentLevelOf||isMatchingList 同型）
            if (!(first.isParentLevelOf(line) || first.isMatchingList(line))) break;
            OrderedLine peek = stack.get(stack.size() - 1);
            if (line.isOrdered) {
                if (line.isChildLevelOf(peek)) {
                    stack.add(line);
                } else if (line.isParentLevelOf(peek)) {
                    while (stack.get(stack.size() - 1).isChildLevelOf(line)) {
                        stack.remove(stack.size() - 1);
                    }
                }
                peek = stack.get(stack.size() - 1);
                if (line != peek && !peek.isMatchingList(line)) {
                    stack.remove(stack.size() - 1);
                    stack.add(line);
                }
            } else if (!line.isEmpty) {
                while (!stack.isEmpty() && !stack.get(stack.size() - 1).isParentLevelOf(line)) {
                    stack.remove(stack.size() - 1);
                }
                if (stack.isEmpty()) return new Result(text, s, e);   // markor EmptyStackException=整体放弃重编号（编辑不应用）
            }

            if (line.isOrdered) {
                peek = stack.get(stack.size() - 1);
                String newValue = nextOrderedValue(peek.value, line == peek);
                if (!newValue.equals(line.value)) {
                    int lenDiff = newValue.length() - line.value.length();
                    if (line.numEnd < s) delta0 += lenDiff;
                    if (line.numEnd < e) delta1 += lenDiff;
                    edits.add(new int[]{line.numStart, line.numEnd});
                    reps.add(newValue);
                    line.value = newValue;
                }
                stack.remove(stack.size() - 1);
                stack.add(line);
            }
        }

        if (edits.isEmpty()) return new Result(text, s, e);
        StringBuilder sb = new StringBuilder(text);
        for (int i = edits.size() - 1; i >= 0; i--) {
            int[] ed = edits.get(i);
            sb.replace(ed[0], ed[1], reps.get(i));
        }
        return new Result(sb.toString(), clamp(s + delta0, 0, sb.length()), clamp(e + delta1, 0, sb.length()));
    }

    /** markor getNextOrderedValue 数字分支（检测仅识别数字前缀；字母列表分支当前正则不可达）。 */
    private static String nextOrderedValue(String current, boolean restart) {
        if (restart) return "1";
        if (current == null || current.isEmpty()) return "1";
        int v = 0;
        try { v = Integer.parseInt(current); } catch (NumberFormatException ignored) { /* 非数字=0，markor tryParseInt 同型 */ }
        return String.valueOf(v + 1);
    }

    /** 行模型（markor ListLine/OrderedListLine 语义对位）。 */
    private static final class OrderedLine {
        final int lineStart, lineEnd;    // 行范围 [lineStart, lineEnd)，不含换行符
        final boolean isEmpty;           // 行内容全空白
        final int indent;                // 空格*1 + 制表*4（markor countChars 同型）
        final boolean isTopLevel;
        final boolean isOrdered;
        final char delimiter;            // '.' / ')'
        final int numStart, numEnd;      // 编号数字串范围（原文本坐标）
        String value;                    // 当前编号值（重编号中可更新）

        OrderedLine(int lineStart, int lineEnd, boolean isEmpty, int indent,
                    boolean isOrdered, char delimiter, int numStart, int numEnd, String value) {
            this.lineStart = lineStart;
            this.lineEnd = lineEnd;
            this.isEmpty = isEmpty;
            this.indent = indent;
            this.isTopLevel = indent <= INDENT_SLACK;
            this.isOrdered = isOrdered;
            this.delimiter = delimiter;
            this.numStart = numStart;
            this.numEnd = numEnd;
            this.value = value;
        }

        /** 本行是 line 的父级层（line 为空行=任何层子级；line 缩进更深>slack 也是子级）——markor isParentLevelOf 同型。 */
        boolean isParentLevelOf(OrderedLine line) {
            return line.isEmpty || (!isEmpty && (line.indent - indent) > INDENT_SLACK);
        }

        /** 本行是 line 的子级层（本行为空=任何层子级；本行缩进更深>slack）——markor isChildLevelOf 同型。 */
        boolean isChildLevelOf(OrderedLine line) {
            return isEmpty || (!line.isEmpty && (indent - line.indent) > INDENT_SLACK);
        }

        /** 同级同分隔符的有序列表（markor isMatchingList 同型）。 */
        boolean isMatchingList(OrderedLine line) {
            return isOrdered && line.isOrdered && delimiter == line.delimiter
                    && Math.abs(indent - line.indent) <= INDENT_SLACK;
        }
    }

    /** 按行解析全文（含空行）；行界=LF，行范围不含换行符。 */
    private static java.util.List<OrderedLine> parseOrderedLines(String text) {
        java.util.List<OrderedLine> out = new java.util.ArrayList<>();
        int n = text.length();
        int pos = 0;
        while (pos <= n) {
            int lineEnd = text.indexOf('\n', pos);
            if (lineEnd < 0) lineEnd = n;
            int wsEnd = pos;
            while (wsEnd < lineEnd && (text.charAt(wsEnd) == ' ' || text.charAt(wsEnd) == '\t')) wsEnd++;
            boolean isEmpty = wsEnd >= lineEnd;
            int indent = 0;
            for (int k = pos; k < wsEnd; k++) indent += (text.charAt(k) == '\t') ? 4 : 1;
            boolean isOrdered = false;
            char delimiter = 0;
            int numStart = -1, numEnd = -1;
            String value = "";
            if (!isEmpty) {
                int[] p = detectPrefix(stripTrailingWs(text.substring(wsEnd, lineEnd)));
                if (p[0] == P_ORDERED) {
                    isOrdered = true;
                    numStart = wsEnd;
                    int i = wsEnd;
                    while (i < lineEnd && Character.isDigit(text.charAt(i))) i++;
                    numEnd = i;
                    delimiter = (i < lineEnd) ? text.charAt(i) : 0;
                    value = text.substring(numStart, numEnd);
                }
            }
            out.add(new OrderedLine(pos, lineEnd, isEmpty, indent, isOrdered, delimiter, numStart, numEnd, value));
            if (lineEnd == n) break;
            pos = lineEnd + 1;
        }
        return out;
    }

    /** 行索引：pos 所在行（markor getLineStart 同型；lines 非空）。 */
    private static int lineIndexOf(java.util.List<OrderedLine> lines, int pos) {
        if (pos <= 0) return 0;
        int j = 0;
        while (j + 1 < lines.size() && lines.get(j + 1).lineStart <= pos) j++;
        return j;
    }

    /** markor OrderedListLine.getParent：向上找第一个「更浅（差&gt;slack）或空行承担父级」的行；首行/顶层非空行无父。 */
    private static int parentIdx(java.util.List<OrderedLine> lines, int idx) {
        OrderedLine self = lines.get(idx);
        if (self.lineStart <= 0) return -1;
        if (!(self.isEmpty || !self.isTopLevel)) return -1;
        int position = self.lineStart - 1;
        while (true) {
            int j = lineIndexOf(lines, position);
            OrderedLine cand = lines.get(j);
            int nextP = cand.lineStart - 1;
            if (cand.isParentLevelOf(self) || nextP <= 0) return j;
            position = nextP;
        }
    }

    /** markor getLevelStart：自 idx 向上扩展，同类同分隔符连续有序行的最顶。 */
    private static int levelStart(java.util.List<OrderedLine> lines, int idx) {
        OrderedLine self = lines.get(idx);
        int listStart = idx;
        if (self.lineStart <= INDENT_SLACK) return idx;
        int lineIdx = idx;
        while (true) {
            int prev = lineIndexOf(lines, lines.get(lineIdx).lineStart - 1);
            OrderedLine prevLine = lines.get(prev);
            boolean matching = self.isMatchingList(prevLine);
            if (matching) listStart = prev;
            if (prevLine.lineStart <= INDENT_SLACK || (!matching && !self.isParentLevelOf(prevLine))) break;
            lineIdx = prev;
        }
        return listStart;
    }

    /** markor getOrderedListStart：向上逐级父级取最顶有序行；顶行有序/空行再向左取同级起点。 */
    private static int orderedListStart(java.util.List<OrderedLine> lines, int idx) {
        int listStart = idx;
        int line = idx;
        while (true) {
            int p = parentIdx(lines, line);
            if (p < 0) break;
            if (lines.get(p).isOrdered) listStart = p;
            line = p;
        }
        OrderedLine ls = lines.get(listStart);
        if (ls.isOrdered || ls.isEmpty) {
            listStart = levelStart(lines, listStart);
        }
        return listStart;
    }


    /**
     * 任务清单切换（markor toggleToCheckedOrUncheckedListPrefix 同款）：目标=未勾选「- [ ] 」；
     * 已是未勾选→改为「- [x] 」，已是勾选→改回「- [ ] 」，其它前缀/无前缀→设为「- [ ] 」。
     * 行前导缩进保留；不提供「移除任务前缀」路径（与 markor 一致：按钮只翻转状态）。
     */
    public static Result toggleTaskList(String text, int start, int end) {
        return toggleLinePrefix(text, start, end, P_UNCHECKED, 0, "- [ ] ", "- [x] ");
    }

    /**
     * 行级前缀切换核心：作用于选区覆盖的所有行（空选区=光标所在行）。
     * 每行独立判定（detectPrefix）：
     * <ul>
     *   <li>已是目标前缀：应用 alternative（默认空串=移除；任务清单场景=替换为勾选态）。</li>
     *   <li>是其它已知前缀（标题/列表/引用/有序/任务）：替换为目标前缀。</li>
     *   <li>无前缀：在前导空白之后插入目标前缀。</li>
     * </ul>
     * 前导空白（行首缩进）与行尾换行保留；选区随编辑位移换算。
     */
    private static Result toggleLinePrefix(String text, int start, int end, int targetType, int targetLevel, String targetMarker, String altMarker) {
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
                    rep = altMarker;                 // 已是目标 → 应用替代（默认移除/任务=勾选态）
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
