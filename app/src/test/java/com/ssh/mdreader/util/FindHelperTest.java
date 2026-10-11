package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.util.FindHelper.Match;

import org.junit.Test;

import java.util.List;

/**
 * {@link FindHelper} 纯函数层单测：字面匹配扫描（文档序/非重叠/跨行/大小写模式/
 * 中文/边界防御）与循环推进语义。
 *
 * <p>匹配语义对齐 Java {@code String.indexOf} 字面扫描：{@code "aa"} in {@code "aaa"}
 * = 1 处 {@code [0,2)}（非重叠）；{@code ignoreCase=true} 走 regionMatches 逐字符
 * 不区分大小写且<b>不改变匹配长度</b>。换行符按普通字符参与比较。</p>
 */
public class FindHelperTest {

    // ── scanAll: 边界 ─────────────────────────────────────────────────────────

    @Test
    public void scan_nullOrEmpty_inputs_returnsEmpty() {
        assertTrue(FindHelper.scanAll(null, "x", true).isEmpty());
        assertTrue(FindHelper.scanAll("abc", null, true).isEmpty());
        assertTrue(FindHelper.scanAll("abc", "", true).isEmpty());
        assertTrue(FindHelper.scanAll("", "x", true).isEmpty());
        assertTrue(FindHelper.scanAll("abc", "abcd", true).isEmpty());   // query 长于文本
    }

    @Test
    public void scan_noMatch_returnsEmpty() {
        assertTrue(FindHelper.scanAll("hello world", "xyz", true).isEmpty());
    }

    // ── scanAll: 基本匹配与位置 ───────────────────────────────────────────────

    @Test
    public void scan_singleMatch_positions() {
        List<Match> ms = FindHelper.scanAll("hello world", "world", true);
        assertEquals(1, ms.size());
        assertEquals(6, ms.get(0).start);
        assertEquals(11, ms.get(0).end);
    }

    @Test
    public void scan_multipleMatches_documentOrder() {
        List<Match> ms = FindHelper.scanAll("ababab", "ab", true);
        assertEquals(3, ms.size());
        assertEquals(0, ms.get(0).start);
        assertEquals(2, ms.get(1).start);
        assertEquals(4, ms.get(2).start);
        assertEquals(2, ms.get(0).end);
        assertEquals(4, ms.get(1).end);
        assertEquals(6, ms.get(2).end);
    }

    @Test
    public void scan_nonOverlapping_skipsOverlap() {
        // indexOf 语义：上一匹配 [0,2) 结束后从 2 起搜 → "aaa" 中 "aa" 仅 1 处
        List<Match> ms = FindHelper.scanAll("aaa", "aa", true);
        assertEquals(1, ms.size());
        assertEquals(0, ms.get(0).start);
        assertEquals(2, ms.get(0).end);
    }

    @Test
    public void scan_chineseText() {
        List<Match> ms = FindHelper.scanAll("文档内文本查找功能", "文本查找", true);
        assertEquals(1, ms.size());
        assertEquals(3, ms.get(0).start);
        assertEquals(7, ms.get(0).end);
    }

    @Test
    public void scan_crossLine() {
        List<Match> ms = FindHelper.scanAll("第一行\n目标 之后的文本", "行\n目标", true);
        assertEquals(1, ms.size());
        assertEquals(2, ms.get(0).start);   // "行\n目标" 起点在"行"（索引 2）
        assertEquals(6, ms.get(0).end);     // 2 + 4 = 6
    }

    @Test
    public void scan_queryWithTrailingSpace_keptLiteral() {
        // 不 trim：查询词前后空白按字面参与匹配
        List<Match> ms = FindHelper.scanAll("a b c", "a ", true);
        assertEquals(1, ms.size());
        assertEquals(0, ms.get(0).start);
        assertEquals(2, ms.get(0).end);
    }

    // ── scanAll: 大小写模式 ──────────────────────────────────────────────────

    @Test
    public void scan_ignoreCase_matchesMixedCase() {
        List<Match> ms = FindHelper.scanAll("HeLLo world hElLo", "hello", true);
        assertEquals(2, ms.size());
        assertEquals(0, ms.get(0).start);
        assertEquals(12, ms.get(1).start);
    }

    @Test
    public void scan_caseSensitive_distinguishes() {
        List<Match> ms = FindHelper.scanAll("Hello hello", "Hello", false);
        assertEquals(1, ms.size());
        assertEquals(0, ms.get(0).start);
        assertEquals(5, ms.get(0).end);
    }

    @Test
    public void scan_ignoreCase_keepsMatchLength() {
        // 大小写不敏感不得改变匹配长度（toLowerCase 折叠隐患用例：德文 ß→SS 不存在，
        // 用长度稳定断言覆盖 regionMatches 路径——查询含大写转小写映射的字符）
        List<Match> ms = FindHelper.scanAll("aBc", "AbC", true);
        assertEquals(1, ms.size());
        assertEquals(0, ms.get(0).start);
        assertEquals(3, ms.get(0).end);
    }

    // ── advance: 循环推进 ────────────────────────────────────────────────────

    @Test
    public void advance_empty_returnsMinusOne() {
        assertEquals(-1, FindHelper.advance(java.util.Collections.emptyList(), 0, 1));
        assertEquals(-1, FindHelper.advance(java.util.Collections.emptyList(), 0, -1));
        assertEquals(-1, FindHelper.advance(null, 0, 1));
    }

    @Test
    public void advance_wrapsAround() {
        List<Match> ms = FindHelper.scanAll("a a a", "a", true);   // 3 处
        assertEquals(1, FindHelper.advance(ms, 0, 1));
        assertEquals(2, FindHelper.advance(ms, 1, 1));
        assertEquals(0, FindHelper.advance(ms, 2, 1));   // 末处向后循环回 0
        assertEquals(2, FindHelper.advance(ms, 0, -1));  // 首处向前循环到末
        assertEquals(1, FindHelper.advance(ms, 2, -1));
    }

    @Test
    public void advance_singleMatch_staysAtZero() {
        List<Match> ms = FindHelper.scanAll("only", "only", true);
        assertEquals(1, ms.size());
        assertEquals(0, FindHelper.advance(ms, 0, 1));
        assertEquals(0, FindHelper.advance(ms, 0, -1));
    }

    @Test
    public void advance_negativeOrOvershootCurrent_normalized() {
        List<Match> ms = FindHelper.scanAll("a b c", "a", true);
        // current 越界也循环（防御：不应让 UI 崩溃）
        int next = FindHelper.advance(ms, -5, 1);
        assertTrue(next >= 0 && next < ms.size());
        next = FindHelper.advance(ms, 999, -1);
        assertTrue(next >= 0 && next < ms.size());
    }

    // ── scanAll: 整词匹配（wholeWord，对齐 markor 正则 \b 边界的字面实现）──────

    @Test
    public void scan_wholeWord_standaloneMatches() {
        List<Match> ms = FindHelper.scanAll("say hello world", "hello", true, true);
        assertEquals(1, ms.size());
        assertEquals(4, ms.get(0).start);
        assertEquals(9, ms.get(0).end);
    }

    @Test
    public void scan_wholeWord_rejectsInsideWord() {
        assertTrue(FindHelper.scanAll("catalog", "cat", true, true).isEmpty());
        assertTrue(FindHelper.scanAll("cats", "cat", true, true).isEmpty());
        assertTrue(FindHelper.scanAll("xcat", "cat", true, true).isEmpty());
    }

    @Test
    public void scan_wholeWord_digitAndUnderscoreAreWordChars() {
        // 数字/下划线属于词字符（与 Java 正则默认 \w 一致）
        assertTrue(FindHelper.scanAll("cat_dog", "cat", true, true).isEmpty());
        assertTrue(FindHelper.scanAll("cat2", "cat", true, true).isEmpty());
        assertEquals(1, FindHelper.scanAll("cat dog", "cat", true, true).size());
    }

    @Test
    public void scan_wholeWord_cjkIsNonWordSeparator() {
        // 中文等非 ASCII 字符=分隔符：「测试」在中文相邻处命中（markor \b 会 0 命中，
        // 本项目按 CJK 天然分隔处理，更符合中文用户预期）
        assertEquals(1, FindHelper.scanAll("中文测试文本", "测试", true, true).size());
        // 与 ASCII 字母相邻则视为嵌入单词（与 catalog 同规则）
        assertTrue(FindHelper.scanAll("abc测试def", "测试", true, true).isEmpty());
    }

    @Test
    public void scan_wholeWord_rejectedAdvancesByOne_keepsLaterMatches() {
        // "catcatalog cat"：前两处 "cat"（0 与 3）被拒后须从 i+1 续扫，末处独立 cat 命中
        List<Match> ms = FindHelper.scanAll("catcatalog cat", "cat", true, true);
        assertEquals(1, ms.size());
        assertEquals(11, ms.get(0).start);
        assertEquals(14, ms.get(0).end);
    }

    @Test
    public void scan_wholeWord_overlappingAllRejected() {
        // "aaa" 中 "aa"：两处候选均被边界拒绝 → 空（整词模式须逐位检查重叠候选）
        assertTrue(FindHelper.scanAll("aaa", "aa", true, true).isEmpty());
    }

    @Test
    public void scan_wholeWord_combinesWithIgnoreCase() {
        // 整词 + 大小写组合：两个独立单词且大小写不敏感
        List<Match> ms = FindHelper.scanAll("Cat dog CAT", "cat", true, true);
        assertEquals(2, ms.size());
        assertEquals(0, ms.get(0).start);
        assertEquals(8, ms.get(1).start);
    }

    @Test
    public void scan_wholeWord_caseSensitive_combined() {
        // 大小写敏感 + 整词：「Hello」在 "Hello hello" 中仅命中开头一处（第二处小写不匹配）
        List<Match> ms = FindHelper.scanAll("Hello hello", "Hello", false, true);
        assertEquals(1, ms.size());
        assertEquals(0, ms.get(0).start);
        // 大小写不敏感 + 整词：两处独立 hello 均命中
        ms = FindHelper.scanAll("Hello hello", "Hello", true, true);
        assertEquals(2, ms.size());
        assertEquals(6, ms.get(1).start);
    }

    @Test
    public void scan_wholeWord_punctuationSeparates() {
        // 标点/空白 = 非词字符：- 与 . 均构成边界
        List<Match> ms = FindHelper.scanAll("cat.cat,cat", "cat", true, true);
        assertEquals(3, ms.size());
        ms = FindHelper.scanAll("cat-cat", "cat", true, true);
        assertEquals(2, ms.size());
    }

    @Test
    public void scan_wholeWord_textStartEndAreBoundaries() {
        assertEquals(1, FindHelper.scanAll("cat", "cat", true, true).size());
        assertEquals(1, FindHelper.scanAll("cat ", "cat", true, true).size());
        assertEquals(1, FindHelper.scanAll(" cat", "cat", true, true).size());
    }

    @Test
    public void scan_wholeWord_emptyOrTooLongQuery() {
        assertTrue(FindHelper.scanAll("abc", "", true, true).isEmpty());
        assertTrue(FindHelper.scanAll("cat", "catalog", true, true).isEmpty());
        assertTrue(FindHelper.scanAll(null, "x", true, true).isEmpty());
    }

    @Test
    public void scan_threeArgs_delegatesToWholeWordOff() {
        // 3 参重载=4 参 wholeWord=false：既有调用方（ViewerFindBar 等）行为不变
        List<Match> ms3 = FindHelper.scanAll("catalog cat", "cat", true);
        assertEquals(2, ms3.size());   // 字面包含：catalog 内也命中
        List<Match> ms4 = FindHelper.scanAll("catalog cat", "cat", true, false);
        assertEquals(ms3.size(), ms4.size());
    }
}
