package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.util.TocHelper.Heading;
import com.ssh.mdreader.util.TocHelper.MarkedSource;
import com.ssh.mdreader.util.TocHelper.TocEntry;

import org.junit.Test;

import java.util.List;

/**
 * {@link TocHelper} 纯函数层单测：标题扫描（ATX/setext 谓词与 commonmark 0.13.0 实证对齐、
 * 围栏代码跳过、引用前缀、缩进规则、CRLF）、零宽标记注入（与任务标记共存）、大纲树构建、
 * 标题标记解码（区间内首个连续段）。
 *
 * <p>判据锚点：commonmark 0.13.0 本机实证——
 * {@code #​X}→Heading(Text=ZWNJ+X)、{@code ​Title\n===}→Heading level1、{@code #foo\n---}→
 * Heading level2 text=#foo、{@code # H\n---}→Heading+ThematicBreak（非 setext）、
 * {@code - item\n---}→List+ThematicBreak、{@code    # x}→IndentedCodeBlock、
 * {@code #}→Heading（空标题）、{@code ##   Text   }→Text=Text。</p>
 */
public class TocHelperTest {

    // ── scan: ATX ────────────────────────────────────────────────────────────

    @Test
    public void scan_nullOrEmpty_returnsEmpty() {
        assertTrue(TocHelper.scan(null).isEmpty());
        assertTrue(TocHelper.scan("").isEmpty());
        assertTrue(TocHelper.scan("plain text\nno headings\n").isEmpty());
    }

    @Test
    public void scan_atx_levelsAndText() {
        List<Heading> hs = TocHelper.scan("# A\n## B\n### C\n#### D\n##### E\n###### F");
        assertEquals(6, hs.size());
        for (int i = 0; i < 6; i++) {
            assertEquals(i + 1, hs.get(i).level);
            assertEquals(String.valueOf((char) ('A' + i)), hs.get(i).text);
        }
    }

    @Test
    public void scan_atx_insertIsTextStart() {
        List<Heading> hs = TocHelper.scan("# A\n## B\n");
        // "# A" 正文 'A' 在源偏移 2；"## B" 正文 'B' 在偏移 6+1=7
        assertEquals(2, hs.get(0).markerInsertIndex);
        assertEquals(7, hs.get(1).markerInsertIndex);
        assertEquals(0, hs.get(0).sourceLineIndex);
        assertEquals(1, hs.get(1).sourceLineIndex);
    }

    @Test
    public void scan_atx_requiresWhitespaceAfterHash() {
        // # 后无空白 → 非 ATX（consistent with commonmark）
        assertTrue(TocHelper.scan("#noSpace").isEmpty());
        // \# 转义 → 非标题
        assertTrue(TocHelper.scan("\\# not").isEmpty());
        // 7 个 # → 非标题
        assertTrue(TocHelper.scan("####### x").isEmpty());
    }

    @Test
    public void scan_atx_indentRules() {
        // 3 空格缩进合法
        List<Heading> hs = TocHelper.scan("   ### Three");
        assertEquals(1, hs.size());
        assertEquals(3, hs.get(0).level);
        assertEquals("Three", hs.get(0).text);
        // 4 空格缩进=缩进代码块 → 非标题
        assertTrue(TocHelper.scan("    # Four").isEmpty());
    }

    @Test
    public void scan_atx_trailingWhitespaceStripped() {
        List<Heading> hs = TocHelper.scan("##   Text   ");
        assertEquals(1, hs.size());
        assertEquals("Text", hs.get(0).text);
    }

    @Test
    public void scan_atx_emptyHeading() {
        List<Heading> hs = TocHelper.scan("#");
        assertEquals(1, hs.size());
        assertEquals("", hs.get(0).text);
        assertEquals(1, hs.get(0).markerInsertIndex);   // 行尾
        // "# "（# + 尾随空格）同样是空标题
        hs = TocHelper.scan("# \n");
        assertEquals(1, hs.size());
        assertEquals("", hs.get(0).text);
        assertEquals(2, hs.get(0).markerInsertIndex);
    }

    @Test
    public void scan_atx_blockquote() {
        List<Heading> hs = TocHelper.scan("> # Quoted\n> > ## Deep");
        assertEquals(2, hs.size());
        assertEquals(1, hs.get(0).level);
        assertEquals("Quoted", hs.get(0).text);
        assertEquals(4, hs.get(0).markerInsertIndex);   // "> # Quoted" 中 Q 在偏移 4
        assertEquals(2, hs.get(1).level);
        assertEquals("Deep", hs.get(1).text);
    }

    // ── scan: setext ─────────────────────────────────────────────────────────

    @Test
    public void scan_setext_eqAndDash() {
        List<Heading> hs = TocHelper.scan("Title\n===");
        assertEquals(1, hs.size());
        assertEquals(1, hs.get(0).level);
        assertEquals("Title", hs.get(0).text);
        assertEquals(0, hs.get(0).markerInsertIndex);

        hs = TocHelper.scan("Title\n---");
        assertEquals(1, hs.size());
        assertEquals(2, hs.get(0).level);
    }

    @Test
    public void scan_setext_underlineMustFollowTextLine() {
        // ATX 标题行后 --- 是 ThematicBreak，不是 setext
        List<Heading> hs = TocHelper.scan("# H\n---");
        assertEquals(1, hs.size());
        assertEquals(1, hs.get(0).level);
        // 列表 marker 行后 --- 是 hr
        assertTrue(TocHelper.scan("- item\n---").isEmpty());
        // 数字列表 marker 行后 --- 是 hr
        assertTrue(TocHelper.scan("1. item\n---").isEmpty());
        // underline 行不能带多余内容
        assertTrue(TocHelper.scan("T\n=== x").isEmpty());
    }

    @Test
    public void scan_setext_hashNoSpaceIsTextLine() {
        // commonmark 实证：#foo\n--- → level2 标题「#foo」
        List<Heading> hs = TocHelper.scan("#foo\n---");
        assertEquals(1, hs.size());
        assertEquals(2, hs.get(0).level);
        assertEquals("#foo", hs.get(0).text);
    }

    @Test
    public void scan_setext_mixedWithAtx() {
        // text\n===\n## after → h1 + h2
        List<Heading> hs = TocHelper.scan("text\n===\n## after");
        assertEquals(2, hs.size());
        assertEquals(1, hs.get(0).level);
        assertEquals(2, hs.get(1).level);
    }

    @Test
    public void scan_setext_blockquote() {
        List<Heading> hs = TocHelper.scan("> T\n> ===");
        assertEquals(1, hs.size());
        assertEquals(1, hs.get(0).level);
        assertEquals("T", hs.get(0).text);
        assertEquals(2, hs.get(0).markerInsertIndex);
    }

    @Test
    public void scan_setext_listInside_notSupported() {
        // 保守近似（文档化）：list 容器内 setext 不识别（commonmark 会渲染为列表内标题）
        assertTrue(TocHelper.scan("- Item\n  ---").isEmpty());
    }

    @Test
    public void scan_setext_4SpaceIndentIsCode() {
        assertTrue(TocHelper.scan("    Title\n    ===").isEmpty());
    }

    // ── scan: 围栏与 CRLF ────────────────────────────────────────────────────

    @Test
    public void scan_fencedCodeSkipped() {
        List<Heading> hs = TocHelper.scan("```\n# fake\n---\n```\n# real");
        assertEquals(1, hs.size());
        assertEquals("real", hs.get(0).text);
        // ~~~ 围栏同样跳过
        hs = TocHelper.scan("~~~\n## fake2\n~~~\n## real2");
        assertEquals(1, hs.size());
        assertEquals("real2", hs.get(0).text);
    }

    @Test
    public void scan_crlf() {
        List<Heading> hs = TocHelper.scan("Title\r\n===\r\n# N\r\n");
        assertEquals(2, hs.size());
        assertEquals(1, hs.get(0).level);
        assertEquals("N", hs.get(1).text);
    }

    // ── injectMarkers ────────────────────────────────────────────────────────

    @Test
    public void inject_headingsOnly() {
        MarkedSource ms = TocHelper.injectMarkers("# A\n# B\n");
        assertEquals("# \u200CA\n# \u200C\u200CB\n", ms.text);
        assertEquals(2, ms.headings.size());
        assertTrue(ms.taskLines.isEmpty());
    }

    @Test
    public void inject_emptyHeadingPadsSpace() {
        // "#"（空标题，行尾无空白）：注入 " \u200C" 保证 ATX 成立（# 后需空白）
        MarkedSource ms = TocHelper.injectMarkers("#");
        assertEquals("# \u200C", ms.text);
        // "# " 已有尾随空白：直接注入标记
        ms = TocHelper.injectMarkers("# ");
        assertEquals("# \u200C", ms.text);
    }

    @Test
    public void inject_headingsAndTasksCoexist() {
        MarkedSource ms = TocHelper.injectMarkers("# A\n- [ ] t\n# B\n");
        assertEquals(2, ms.headings.size());
        assertEquals(1, ms.taskLines.size());
        // 两类标记字符分别存在且互不混淆
        assertTrue(ms.text.indexOf(TocHelper.HEADING_MARKER) >= 0);
        assertTrue(ms.text.indexOf(TaskCheckboxHelper.MARKER) >= 0);
        // 标题正文前只有 U+200C（无 U+200B）
        int aIdx = ms.text.indexOf(HEADING_TEXT_A);
        assertEquals(TocHelper.HEADING_MARKER, ms.text.charAt(aIdx));
    }

    private static final String HEADING_TEXT_A = "\u200CA";

    @Test
    public void inject_nullOrEmpty() {
        MarkedSource ms = TocHelper.injectMarkers(null);
        assertEquals("", ms.text);
        assertTrue(ms.headings.isEmpty());
        ms = TocHelper.injectMarkers("");
        assertEquals("", ms.text);
        assertTrue(ms.taskLines.isEmpty());
    }

    @Test
    public void inject_noHeadings_noTasks_returnsOriginal() {
        MarkedSource ms = TocHelper.injectMarkers("plain\n");
        assertEquals("plain\n", ms.text);
    }

    // ── buildTree ────────────────────────────────────────────────────────────

    @Test
    public void buildTree_indentByLevel() {
        List<Heading> hs = TocHelper.scan("# A\n## B\n### C\n# D");
        List<TocEntry> tree = TocHelper.buildTree(hs);
        assertEquals(4, tree.size());
        assertEquals(0, tree.get(0).indent);
        assertEquals(1, tree.get(1).indent);
        assertEquals(2, tree.get(2).indent);
        assertEquals(0, tree.get(3).indent);
        assertTrue(TocHelper.buildTree(null).isEmpty());
        assertTrue(TocHelper.buildTree(hs.subList(0, 0)).isEmpty());
    }

    // ── decodeHeadingIndex ───────────────────────────────────────────────────

    @Test
    public void decode_firstSegmentInRange() {
        String s = "# \u200CA\n# \u200C\u200CB\n";
        assertEquals(0, TocHelper.decodeHeadingIndex(s, 0, s.length()));
        // 区间起点在标记之前一段距离（容忍 span 起点偏移）
        assertEquals(0, TocHelper.decodeHeadingIndex(s, 1, s.length()));
        assertEquals(1, TocHelper.decodeHeadingIndex(s, 7, s.length()));
    }

    @Test
    public void decode_noMarker_orInvalid() {
        assertEquals(-1, TocHelper.decodeHeadingIndex("abc", 0, 3));
        assertEquals(-1, TocHelper.decodeHeadingIndex(null, 0, 3));
        assertEquals(-1, TocHelper.decodeHeadingIndex("ab\u200Cc", 5, 3));   // from>=len
        assertEquals(-1, TocHelper.decodeHeadingIndex("ab\u200Cc", 2, 2));   // from>=to
        assertEquals(0, TocHelper.decodeHeadingIndex("ab\u200Cc", 0, 3));    // 区间内首个段
    }

    @Test
    public void decode_countsAsIndexOfMarker() {
        // 连续 3 个 U+200C → 扫描序 2
        String s = "\u200C\u200C\u200Cx";
        assertEquals(2, TocHelper.decodeHeadingIndex(s, 0, s.length()));
    }
}
