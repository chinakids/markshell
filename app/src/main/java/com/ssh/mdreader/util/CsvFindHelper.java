package com.ssh.mdreader.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CSV 表格查找纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（路线图 #21，2026-10-04）</b>：Text/Code/CSV 三个查看器无文档内查找
 * （Markdown 阅读器与编辑模式已有 {@link FindHelper}）。CSV 查看器以 TableLayout
 * 渲染单元格，无单一线性文本可扫描，故本层定义<b>单元格匹配</b>语义：查询串以
 * {@link FindHelper#scanAll} 同款字面/大小写语义判定每个单元格是否包含命中（单一
 * 语义源——不做第二套匹配实现），命中导航的最小单位=单元格。</p>
 *
 * <p><b>匹配语义</b>（文档化）：</p>
 * <ul>
 *   <li><b>单元格级</b>：每格只报一条命中（格内出现多次不重复计数）——导航最小单位
 *       是「哪一格」而非格内字符区间；「a,b」这类跨单元格串<b>不</b>做扁平拼接匹配
 *       （避免单元格边界误报，与表格查看「所见=格内容」直觉一致；含逗号的合法字段
 *       本身就在单个引号单元格内，仍可命中）。</li>
 *   <li><b>顺序</b>：行序（row 升序）为主、列序（cell 升序）为次——扫描即文档序。</li>
 *   <li><b>大小写</b>：{@code ignoreCase=true} 时与 FindHelper 完全一致（regionMatches
 *       逐字符，不改变匹配长度）。中文无大小写。</li>
 *   <li>空白/全角半角<b>不</b>做任何归一化；cell 为 null/空串、query 为空串 → 不命中。</li>
 * </ul>
 */
public final class CsvFindHelper {

    private CsvFindHelper() {
    }

    /** 单处单元格命中：{@code row}/{@code cell} 为 0-based 下标（行含表头行）。 */
    public static final class CellMatch {
        /** 行下标（含表头行 = 0）。 */
        public final int row;
        /** 列下标。 */
        public final int cell;

        CellMatch(int row, int cell) {
            this.row = row;
            this.cell = cell;
        }
    }

    /**
     * 全量扫描表格（单元格文本矩阵）中 {@code query} 的全部单元格命中（文档序）。
     * 任一参数为 null、query 为空、rows 为空 → 空列表（从不返回 null）。
     *
     * @param rows       每行各单元格文本（可从 renderCsv 解析结果直接传入）
     * @param query      查询词（空串/空白串只按字面匹配，不 trim）
     * @param ignoreCase true=逐字符不区分大小写
     */
    public static List<CellMatch> matchCells(List<List<String>> rows, String query,
                                             boolean ignoreCase) {
        if (rows == null || query == null || query.isEmpty()) {
            return Collections.emptyList();
        }
        List<CellMatch> result = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            if (row == null) continue;
            for (int c = 0; c < row.size(); c++) {
                String cell = row.get(c);
                if (cell == null || cell.isEmpty()) continue;
                if (!FindHelper.scanAll(cell, query, ignoreCase).isEmpty()) {
                    result.add(new CellMatch(r, c));
                }
            }
        }
        return result;
    }

    /**
     * 循环推进当前命中下标：{@code direction=1} 下一处、{@code direction=-1} 上一处，
     * 首尾<b>循环</b>（同 FindHelper.advance 语义）。空列表或 direction=0 → -1。
     */
    public static int advance(List<CellMatch> matches, int current, int direction) {
        if (matches == null || matches.isEmpty() || direction == 0) return -1;
        int size = matches.size();
        int idx = current < 0 ? 0 : current;
        int next = (int) (((long) idx + direction) % size);
        if (next < 0) next += size;
        return next;
    }
}
