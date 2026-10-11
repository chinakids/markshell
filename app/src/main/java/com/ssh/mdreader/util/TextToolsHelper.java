package com.ssh.mdreader.util;

/**
 * 编辑模式文本工具纯函数层（能力发现第七十轮：markor TextActions 对齐，markor=gsantner/markor）。
 *
 * <p>对标 markor 源码行为实证：
 * <ul>
 *   <li><b>大小写</b>=TextViewUtils.replaceSelection + TextCasingUtils（toggleCase/switchCase/
 *       capitalizeWords/capitalizeSentences）：仅<b>非空选区</b>生效；逐字符变换，变换后选区落在
 *       变换结果文本上（与 EditText.replace 后 span 选区随动语义一致）。
 *       capitalizeWords=词首大写<b>其余小写</b>；capitalizeSentences=仅句首（.?! 后）大写
 *       <b>其余原样</b>（markor 源码此二处语义不同，如实对齐）。</li>
 *   <li><b>行级操作</b>=ActionButtonBase.duplicateLineSelection / deleteLine /
 *       moveLineSelectionBy1：作用于<b>选区覆盖行块</b>（空选区=光标行）；行块范围
 *       =getLineStart(sel0)..getLineEnd(sel1)（两边均<b>不含行尾换行</b>，markor TextViewUtils 同型）；
 *       复制=块尾插入 "\n"+块（向下复制，选区跟随到复制块，列不变）；删除=块+其后换行整体删除
 *       （末行无换行则删到末尾），光标落块首；移动=与相邻行块交换（无相邻行 no-op），
 *       选区跟随行块整体移动，列保持。</li>
 * </ul>
 *
 * <p>全部为纯函数（无 Android 依赖），JVM 单测覆盖；返回 {@link Result}（新文本+新选区），
 * UI 层拿到后 setText + setSelection 即可。文本无变化（空选区/无相邻行等 no-op）时返回
 * 原文本与入参选区，调用方可据 {@code result.text.equals(text)} 判断并跳过写回。
 */
public final class TextToolsHelper {

    private TextToolsHelper() {}

    /** 一次文本工具操作的结果：新文本与新选区。 */
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

    // ── 大小写（markor TextCasingUtils + replaceSelection 语义）───────────────

    /** 全大写（markor toggleCase 的大写分支；逐字符 String 级变换）。空选区 no-op。 */
    public static Result toUpperCase(String text, int start, int end) {
        return transformCase(text, start, end, 0);
    }

    /** 全小写（markor toggleCase 的小写分支）。空选区 no-op。 */
    public static Result toLowerCase(String text, int start, int end) {
        return transformCase(text, start, end, 1);
    }

    /** 逐词首字母大写、其余小写（markor capitalizeWords：非空白后首字符大写，其它字符 toLowerCase）。 */
    public static Result capitalizeWords(String text, int start, int end) {
        return transformCase(text, start, end, 2);
    }

    /** 仅句首（. ! ? 之后跳过的空白后首个字符）大写、其余原样（markor capitalizeSentences 同型）。 */
    public static Result capitalizeSentences(String text, int start, int end) {
        return transformCase(text, start, end, 3);
    }

    /** 逐字符大小写互换（markor switchCase：大写→小写、小写→大写、其它原样）。 */
    public static Result switchCase(String text, int start, int end) {
        return transformCase(text, start, end, 4);
    }

    /** 大小写变换核心：仅非空选区；变换后选区=变换结果全选（markor replace 后选区随动语义）。 */
    private static Result transformCase(String text, int start, int end, int mode) {
        if (text == null) return new Result(null, start, end);
        int n = text.length();
        int s = clamp(start, 0, n);
        int e = clamp(end, 0, n);
        if (e < s) { int t = s; s = e; e = t; }
        if (s == e) return new Result(text, s, e);            // markor getSelectedText 为空=不替换

        String selected = text.substring(s, e);
        String transformed;
        if (mode == 0) {
            transformed = selected.toUpperCase(java.util.Locale.ROOT);
        } else if (mode == 1) {
            transformed = selected.toLowerCase(java.util.Locale.ROOT);
        } else if (mode == 2) {
            transformed = capitalizeWordsCore(selected);
        } else if (mode == 3) {
            transformed = capitalizeSentencesCore(selected);
        } else {
            transformed = switchCaseCore(selected);
        }
        if (transformed.equals(selected)) return new Result(text, s, e);   // 无变化不写回

        String newText = text.substring(0, s) + transformed + text.substring(e);
        // 选区=变换结果（替换区间随文本长度变化——toUpperCase 在 'ß' 等字符上会增长度）
        return new Result(newText, s, s + transformed.length());
    }

    /** markor capitalizeWords：词首（非空白前）大写，其余字符小写。 */
    private static String capitalizeWordsCore(String selected) {
        StringBuilder sb = new StringBuilder(selected.length());
        boolean capNext = true;
        for (int i = 0; i < selected.length(); i++) {
            char c = selected.charAt(i);
            if (Character.isWhitespace(c)) {
                capNext = true;
                sb.append(c);
            } else if (capNext) {
                sb.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    /** markor capitalizeSentences：句首大写，其余原样（含句间空白与句中大小写）。 */
    private static String capitalizeSentencesCore(String selected) {
        StringBuilder sb = new StringBuilder(selected.length());
        boolean capNext = true;
        for (int i = 0; i < selected.length(); i++) {
            char c = selected.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                capNext = true;
                sb.append(c);
            } else if (Character.isWhitespace(c)) {
                sb.append(c);                                 // 空白不重置句首标记（markor 同）
            } else if (capNext) {
                sb.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** markor switchCase：逐字符互换。 */
    private static String switchCaseCore(String selected) {
        StringBuilder sb = new StringBuilder(selected.length());
        for (int i = 0; i < selected.length(); i++) {
            char c = selected.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(Character.toLowerCase(c));
            } else if (Character.isLowerCase(c)) {
                sb.append(Character.toUpperCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ── 行级操作（markor ActionButtonBase 语义）─────────────────────────────

    /** 复制选区覆盖行块（向下复制；markor duplicateLineSelection：块尾插 "\n"+块，选区跟随到复制块）。 */
    public static Result duplicateLines(String text, int start, int end) {
        if (text == null) return new Result(null, start, end);
        int n = text.length();
        int s = clamp(start, 0, n);
        int e = clamp(end, 0, n);
        if (e < s) { int t = s; s = e; e = t; }

        int lineS = lineStart(text, s);
        int lineE = lineEnd(text, e);
        String block = text.substring(lineS, lineE);

        StringBuilder newText = new StringBuilder(n + block.length() + 1);
        newText.append(text, 0, lineE).append('\n').append(block).append(text, lineE, n);

        int lineCount = countNewlines(text, lineS, lineE) + 1;
        int newS = lineE + 1 + (s - lineS);
        int newE = lineE + 1 + (e - lineS);
        int len = newText.length();
        return new Result(newText.toString(), clamp(newS, 0, len), clamp(newE, 0, len));
    }

    /** 删除选区覆盖行块（markor deleteLine 扩展：块+其后换行整体删除；末行无换行删到末尾）。 */
    public static Result deleteLines(String text, int start, int end) {
        if (text == null) return new Result(null, start, end);
        int n = text.length();
        int s = clamp(start, 0, n);
        int e = clamp(end, 0, n);
        if (e < s) { int t = s; s = e; e = t; }

        int lineS = lineStart(text, s);
        int lineE = lineEnd(text, e);
        int delEnd = lineE;
        if (delEnd < n && text.charAt(delEnd) == '\n') delEnd++;

        String newText = text.substring(0, lineS) + text.substring(delEnd);
        int len = newText.length();
        return new Result(newText, clamp(lineS, 0, len), clamp(lineS, 0, len));
    }

    /** 上移选区覆盖行块一行（markor moveLineSelectionBy1(up)：与上一行块交换；首行 no-op）。 */
    public static Result moveLinesUp(String text, int start, int end) {
        if (text == null) return new Result(null, start, end);
        int n = text.length();
        int s = clamp(start, 0, n);
        int e = clamp(end, 0, n);
        if (e < s) { int t = s; s = e; e = t; }

        int lineS = lineStart(text, s);
        int lineE = lineEnd(text, e);
        if (lineS <= 0) return new Result(text, s, e);        // markor：isUp && lineSel[0] > 0 才动

        int prevS = lineStart(text, lineS - 1);
        int prevE = lineEnd(text, lineS - 1);
        String block = text.substring(lineS, lineE);
        String prevBlock = text.substring(prevS, prevE);

        StringBuilder newText = new StringBuilder(n);
        newText.append(text, 0, prevS).append(block).append('\n').append(prevBlock).append(text, lineE, n);

        int newS = prevS + (s - lineS);
        int newE = prevS + (e - lineS);
        int len = newText.length();
        return new Result(newText.toString(), clamp(newS, 0, len), clamp(newE, 0, len));
    }

    /** 下移选区覆盖行块一行（markor moveLineSelectionBy1(down)：与下一行块交换；末行 no-op）。 */
    public static Result moveLinesDown(String text, int start, int end) {
        if (text == null) return new Result(null, start, end);
        int n = text.length();
        int s = clamp(start, 0, n);
        int e = clamp(end, 0, n);
        if (e < s) { int t = s; s = e; e = t; }

        int lineS = lineStart(text, s);
        int lineE = lineEnd(text, e);
        if (lineE >= n) return new Result(text, s, e);        // markor：lineSel[1] < length 才动

        int nextS = lineE + 1;                                 // lineE 处必为 '\n'（lineEnd 停靠语义）
        int nextE = lineEnd(text, nextS);
        String block = text.substring(lineS, lineE);
        String nextBlock = text.substring(nextS, nextE);

        StringBuilder newText = new StringBuilder(n);
        newText.append(text, 0, lineS).append(nextBlock).append('\n').append(block).append(text, nextE, n);

        // 块在新文本中的起点=lineS + nextBlock长度 + 1；选区按「距块首偏移」跟随（等长/不等长均正确）
        int blockStartNew = lineS + (nextE - nextS) + 1;
        int newS = blockStartNew + (s - lineS);
        int newE = blockStartNew + (e - lineS);
        int len = newText.length();
        return new Result(newText.toString(), clamp(newS, 0, len), clamp(newE, 0, len));
    }

    // ── 行工具（markor TextViewUtils.getLineStart/getLineEnd 同型）────────────

    /** 行首：从 pos 向前到 '\n'（不含）或文本头；pos 无效时原样返回（markor 同）。 */
    private static int lineStart(String s, int pos) {
        int i = pos;
        if (i >= 0 && i <= s.length()) {
            for (; i > 0; i--) {
                if (s.charAt(i - 1) == '\n') break;
            }
        }
        return i;
    }

    /** 行尾：从 pos 向后到 '\n'（不含）或文本尾；pos 无效时原样返回（markor 同型边界）。 */
    private static int lineEnd(String s, int pos) {
        int i = pos;
        if (i >= 0 && i <= s.length()) {
            for (; i < s.length(); i++) {
                if (s.charAt(i) == '\n') break;
            }
        }
        return i;
    }

    /** [from,to) 区间内 '\n' 计数。 */
    private static int countNewlines(String s, int from, int to) {
        int c = 0;
        for (int i = from; i < to; i++) {
            if (s.charAt(i) == '\n') c++;
        }
        return c;
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
