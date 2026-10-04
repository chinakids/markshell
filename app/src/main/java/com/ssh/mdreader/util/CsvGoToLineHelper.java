package com.ssh.mdreader.util;

/**
 * CSV 查看器「转到行号」纯函数层（能力发现循环第四十三轮，走查命中 #55：
 * Text/Code 查看器均有「转到行号…」（GoToLineHelper+菜单+跳转），CSV 无=
 * 同组能力不一致）。
 *
 * <p><b>语义（与 {@link GoToLineHelper} 文本模型对拍）</b>：行号 1-based；输入
 * &lt;1 → 第 1 行（Text/Code 经 parseLineNumber 归一为 1 后同型）；越界（&gt; 总行数）
 * → 最后一行（对应文本模型「越界 → 文本末尾」的 markor 同义——CSV 无字符流，
 * 行的末尾即末行）；空表无数据行 → -1（调用方提示不跳转）。含表头行：第 1 行=表头
 * （与 renderCsv 的 rowIdx==0 表头判定同源，行号按显示行序计）。</p>
 *
 * <p>本类零 Android 依赖（纯 int 计算），JVM 可测。</p>
 */
public final class CsvGoToLineHelper {

    private CsvGoToLineHelper() {
    }

    /**
     * 用户行号 → 数据行索引（0-based，可用于 {@code rowViews}）。
     *
     * @param lineNumber 用户输入行号（1-based）。
     * @param totalRows 当前表格总行数（含表头；{@code rowViews.size()}）。
     * @return {@code totalRows <= 0} → -1；{@code lineNumber < 1} → 0；
     *         {@code lineNumber > totalRows} → {@code totalRows - 1}（末行）；否则 {@code lineNumber - 1}。
     */
    public static int rowIndexFor(int lineNumber, int totalRows) {
        if (totalRows <= 0) {
            return -1;
        }
        if (lineNumber < 1) {
            return 0;
        }
        if (lineNumber > totalRows) {
            return totalRows - 1;
        }
        return lineNumber - 1;
    }
}
