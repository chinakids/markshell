package com.ssh.mdreader.util;

/**
 * 查看器「转到行号」纯函数层（能力发现循环 #18 位阶：查看器行号列只能看不能跳，
 * 本轮补齐跳转；行为语义对标 markor {@code showGoToLineDialog} 实现源码实证）。
 *
 * <p><b>语义（markor 对拍）</b>：行号 1-based；输入 &lt;1 → 1；越界行号 → 文本末尾
 * （markor {@code getIndexFromLineOffset} 循环结束返回 {@code length()} 同型，不做 clamp
 * 到末行）；空文本 → 0。行数语义与 {@link LineNumberHelper#countLines} 同源（逻辑行，
 * CRLF 按 LF 计）。</p>
 *
 * <p>本类零 Android 依赖（String 上操作），JVM 可测。</p>
 */
public final class GoToLineHelper {

    private GoToLineHelper() {
    }

    /**
     * 解析用户输入的行号。
     *
     * @return null/空白/非法数字（含 int 溢出）→ -1；数字 &lt;1 → 1（markor 同型）；否则原值。
     */
    public static int parseLineNumber(String input) {
        if (input == null) {
            return -1;
        }
        String t = input.trim();
        if (t.isEmpty()) {
            return -1;
        }
        int n;
        try {
            n = Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return -1;
        }
        return Math.max(n, 1);
    }

    /**
     * 第 {@code lineNumber} 行（1-based，逻辑行）行首字符偏移。
     *
     * <p>越界（行号 &gt; 总行数）→ {@code text.length()}（markor 同型）；{@code lineNumber <= 1}
     * → 0；null 文本 → 0。</p>
     */
    public static int lineStartOffset(CharSequence text, int lineNumber) {
        if (text == null) {
            return 0;
        }
        if (lineNumber <= 1) {
            return 0;
        }
        int target = lineNumber - 1;   // 需跳过 target 个换行
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
                if (count == target) {
                    return i + 1;
                }
            }
        }
        return text.length();          // 行数不足：越界 → 末尾
    }

    /**
     * 第 {@code lineNumber} 行高亮区间 {@code [start, end)}：行首到行尾（不含换行符；
     * 空行 end==start）。
     *
     * <p>越界行 → {@code [length, length]}（空区间，调用方仅滚动不设高亮）。</p>
     */
    public static int[] lineHighlightRange(CharSequence text, int lineNumber) {
        if (text == null) {
            return new int[]{0, 0};
        }
        int start = lineStartOffset(text, lineNumber);
        int end = lineStartOffset(text, lineNumber + 1);
        // 去尾部换行符；若 end 已到末尾（最后一行/越界）则行尾=length
        while (end > start && text.charAt(end - 1) == '\n') {
            end--;
        }
        if (end < start) {
            end = start;
        }
        return new int[]{start, end};
    }
}
