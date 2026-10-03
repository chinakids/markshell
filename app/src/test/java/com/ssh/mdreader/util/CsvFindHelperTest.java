package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.util.CsvFindHelper.CellMatch;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link CsvFindHelper} 纯函数层单测：CSV 表格单元格匹配（文档序/单元格级/大小写模式/
 * 引号字段/中文/边界防御）与循环推进语义。
 *
 * <p>匹配语义=单元格内子串（FindHelper 同款字面/ignoreCase），一格拉一条；「a,b」这类
 * 跨单元格拼接不匹配（表格所见=格内容）。</p>
 */
public class CsvFindHelperTest {

    private static List<List<String>> table(Object[]... rows) {
        List<List<String>> result = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            List<String> r = new java.util.ArrayList<>();
            for (Object o : row) r.add(String.valueOf(o));
            result.add(r);
        }
        return result;
    }

    // ── matchCells: 边界防御 ─────────────────────────────────────────────────

    @Test
    public void match_nullOrEmpty_inputs_returnsEmpty() {
        assertTrue(CsvFindHelper.matchCells(null, "x", true).isEmpty());
        assertTrue(CsvFindHelper.matchCells(table(new Object[]{"a"}), null, true).isEmpty());
        assertTrue(CsvFindHelper.matchCells(table(new Object[]{"a"}), "", true).isEmpty());
        assertTrue(CsvFindHelper.matchCells(Collections.emptyList(), "x", true).isEmpty());
    }

    @Test
    public void match_nullCell_nullRow_skippedWithoutError() {
        List<List<String>> rows = Arrays.asList(null, Arrays.asList("a", null));
        assertEquals(1, CsvFindHelper.matchCells(rows, "a", true).size());
        // 全 null/空串结构：无命中且不 NPE
        rows = Arrays.asList(null, Arrays.asList((String) null, "", null));
        assertTrue(CsvFindHelper.matchCells(rows, "a", true).isEmpty());
        // 行内 null/空串单元格直接跳过，其余正常参与
        rows = Arrays.asList(Arrays.asList("x", null, ""), Arrays.asList((String) null, "y"));
        List<CellMatch> ms = CsvFindHelper.matchCells(rows, "y", true);
        assertEquals(1, ms.size());
        assertEquals(1, ms.get(0).row);
        assertEquals(1, ms.get(0).cell);
    }

    // ── matchCells: 基本匹配与位置 ───────────────────────────────────────────

    @Test
    public void match_singleCell_found() {
        List<List<String>> rows = table(new Object[]{"name", "ip"},
                new Object[]{"web", "192.168.1.1"});
        List<CellMatch> ms = CsvFindHelper.matchCells(rows, "192.168", true);
        assertEquals(1, ms.size());
        assertEquals(1, ms.get(0).row);
        assertEquals(1, ms.get(0).cell);
    }

    @Test
    public void match_multipleCells_documentOrderRowThenColumn() {
        List<List<String>> rows = table(new Object[]{"a", "b", "a"},
                new Object[]{"c", "a", "d"});
        List<CellMatch> ms = CsvFindHelper.matchCells(rows, "a", true);
        assertEquals(3, ms.size());
        assertEquals(0, ms.get(0).row);
        assertEquals(0, ms.get(0).cell);
        assertEquals(0, ms.get(1).row);
        assertEquals(2, ms.get(1).cell);
        assertEquals(1, ms.get(2).row);
        assertEquals(1, ms.get(2).cell);
    }

    @Test
    public void match_headerRow_alsoSearchable() {
        List<List<String>> rows = table(new Object[]{"id", "cpu_usage"},
                new Object[]{"1", "0.4"});
        List<CellMatch> ms = CsvFindHelper.matchCells(rows, "cpu", true);
        assertEquals(1, ms.size());
        assertEquals(0, ms.get(0).row);
        assertEquals(1, ms.get(0).cell);
    }

    // ── matchCells: 大小写 ───────────────────────────────────────────────────

    @Test
    public void match_ignoreCase_hitsMixedCase() {
        List<List<String>> rows = table(new Object[]{"Name", "VALUE", "id"});
        assertEquals(1, CsvFindHelper.matchCells(rows, "value", true).size());
        assertEquals(1, CsvFindHelper.matchCells(rows, "VALUE", true).size());
        assertEquals(1, CsvFindHelper.matchCells(rows, "name", true).size());
        assertEquals(0, CsvFindHelper.matchCells(rows, "ID", false).size());
    }

    // ── matchCells: 单元格级（一格拉一条）与跨格不匹配 ──────────────────────────

    @Test
    public void match_repeatedInCell_reportedOnce() {
        List<List<String>> rows = table(new Object[]{"aaabbbaaa"});
        List<CellMatch> ms = CsvFindHelper.matchCells(rows, "aa", true);
        assertEquals(1, ms.size());   // 单元格级去重：导航最小单位=格
    }

    @Test
    public void match_crossCellConcatenation_notMatched() {
        List<List<String>> rows = table(new Object[]{"12", "34"});
        assertTrue(CsvFindHelper.matchCells(rows, "1234", true).isEmpty());
        // 引号包围的合法含逗号字段=单格内容，仍可命中
        rows = table(new Object[]{"\"12,34\"", "x"});
        List<CellMatch> ms = CsvFindHelper.matchCells(rows, "12,34", true);
        assertEquals(1, ms.size());
        assertEquals(0, ms.get(0).cell);
    }

    @Test
    public void match_blankQuery_neverMatches() {
        List<List<String>> rows = table(new Object[]{" ", "", "x"});
        assertTrue(CsvFindHelper.matchCells(rows, "", true).isEmpty());
        // 空白串按字面匹配（同 FindHelper：不 trim、不归一化）
        assertEquals(1, CsvFindHelper.matchCells(rows, " ", true).size());
        assertEquals(0, CsvFindHelper.matchCells(rows, " ", true).get(0).cell);
    }

    // ── matchCells: 中文/全角 ────────────────────────────────────────────────

    @Test
    public void match_chineseAndFullWidth_literal() {
        List<List<String>> rows = table(new Object[]{"主机", "192.168.1.1"},
                new Object[]{"负载", "80%"});
        List<CellMatch> ms = CsvFindHelper.matchCells(rows, "负载", true);
        assertEquals(1, ms.size());
        assertEquals(1, ms.get(0).row);
        assertEquals(0, ms.get(0).cell);
        // 全角/半角不归一化：全角逗号存一格
        rows = table(new Object[]{"备注", "风险，已处理"});
        assertEquals(1, CsvFindHelper.matchCells(rows, "风险，已", true).size());
        assertTrue(CsvFindHelper.matchCells(rows, "风险,已", true).isEmpty());
    }

    @Test
    public void match_noMatch_returnsEmpty() {
        List<List<String>> rows = table(new Object[]{"a", "b"}, new Object[]{"c", "d"});
        assertTrue(CsvFindHelper.matchCells(rows, "xyz", true).isEmpty());
        // query 长于单元格也不会误报
        assertTrue(CsvFindHelper.matchCells(rows, "abcdefgh", true).isEmpty());
    }

    // ── advance: 循环推进 ────────────────────────────────────────────────────

    @Test
    public void advance_wrapsAround() {
        List<CellMatch> ms = CsvFindHelper.matchCells(
                table(new Object[]{"a"}, new Object[]{"a"}, new Object[]{"a"}), "a", true);
        assertEquals(3, ms.size());
        assertEquals(1, CsvFindHelper.advance(ms, 0, 1));
        assertEquals(2, CsvFindHelper.advance(ms, 1, 1));
        assertEquals(0, CsvFindHelper.advance(ms, 2, 1));   // 末处回第一处
        assertEquals(2, CsvFindHelper.advance(ms, 0, -1));  // 首处回末处
        assertEquals(-1, CsvFindHelper.advance(Collections.emptyList(), 0, 1));
        assertEquals(-1, CsvFindHelper.advance(ms, 0, 0));
        // 与 FindHelper.advance 同语义：current<0 视为 0 再推进（方向 1 → 1，非 0）
        assertEquals(1, CsvFindHelper.advance(ms, -1, 1));
    }

    // ── matchCells: 长时间查询与空表格 ────────────────────────────────────────

    @Test
    public void match_emptyRowsTable_returnsEmpty() {
        assertTrue(CsvFindHelper.matchCells(table(), "x", true).isEmpty());
    }
}
