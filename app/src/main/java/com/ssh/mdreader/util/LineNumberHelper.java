package com.ssh.mdreader.util;

/**
 * 行号列纯函数层（能力发现循环 #18：纯文本查看器行号 + 代码查看器行号共用）。
 *
 * <p>行数语义=CodeViewer 既有 {@code content.split("\n", -1).length} 的等价实现：
 * 空串=1 行（存在一个空行）、尾随换行符=末尾多一个空行、CRLF 按 LF 计（\\r 留在行尾不影响行号）。
 * markor LineNumbersView 计数同型（maxNumber = 1 + 换行符个数）。
 * </p>
 */
public final class LineNumberHelper {

    private LineNumberHelper() {
    }

    /** 逻辑行数：null→0；空串→1；其余=1+换行符个数（split("\n",-1) 语义）。 */
    public static int countLines(CharSequence text) {
        if (text == null) {
            return 0;
        }
        if (text.length() == 0) {
            return 1;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /** 生成 {@code "1\n2\n…N\n"} 行号序列（CodeViewer 列式行号既有语义）；count<=0 → 空串。 */
    public static String numberSequence(int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(count * 4);
        for (int i = 1; i <= count; i++) {
            sb.append(i).append('\n');
        }
        return sb.toString();
    }

    /** 十进制位数（用于行号槽宽度计算）：n<=0 → 1（行号从 1 起，防御未知态）。 */
    public static int digits(int n) {
        if (n <= 0) {
            return 1;
        }
        int d = 0;
        while (n > 0) {
            n /= 10;
            d++;
        }
        return d;
    }
}
