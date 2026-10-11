package com.ssh.mdreader.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * MarkdownLinkHelper 单测（迭代113：编辑模式插入链接/图片纯函数层，markor
 * insert_link/insert_image 对齐——MarkdownSyntaxHighlighter.LINK 正则与 Link.extract 语义实证）。
 *
 * <p>覆盖：已有链接识别（行内搜索/图片/嵌套括号/多链接命中最左覆盖/边界与行隔离/百分号解码）；
 * 插入（光标处未命中插入/命中整链接替换/选区替换成链接/图片模板/空 URL no-op/标题与地址清洗/
 * 空格编码 %20/管道符保留取舍/往返稳定/越界防御）。
 */
public class MarkdownLinkHelperTest {

    // ── extract：识别 ───────────────────────────────────────────────────────

    @Test
    public void extract_basicLink() {
        String text = "前置 [标题](https://a.cn) 后置";
        int s = text.indexOf('标');
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, s);
        assertTrue(li.isValid());
        assertEquals("标题", li.title);
        assertEquals("https://a.cn", li.url);
        assertFalse(li.isImage);
        assertEquals(text.indexOf('['), li.start);
        assertEquals(text.indexOf(')') + 1, li.end);
    }

    @Test
    public void extract_posInUrl() {
        String text = "[t](http://x/y.md)";
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, text.indexOf("//x") + 1);
        assertTrue(li.isValid());
        assertEquals("t", li.title);
        assertEquals("http://x/y.md", li.url);
    }

    @Test
    public void extract_imageLink() {
        String text = "![截图](img/1.png)";
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, 3);
        assertTrue(li.isValid());
        assertTrue(li.isImage);
        assertEquals("截图", li.title);
        assertEquals("img/1.png", li.url);
    }

    @Test
    public void extract_plainText_miss() {
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract("普通文本没有链接", 2);
        assertFalse(li.isValid());
    }

    @Test
    public void extract_incompleteSyntax_miss() {
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract("看 [文](这里没闭合", 3);
        assertFalse(li.isValid());
    }

    @Test
    public void extract_nestedParensInUrl() {
        String text = "x [t](a(b)c) y";
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, text.indexOf('t'));
        assertTrue(li.isValid());
        assertEquals("a(b)c", li.url);
        assertEquals(text.indexOf("[t]"), li.start);
        assertEquals(text.lastIndexOf(')') + 1, li.end);
    }

    @Test
    public void extract_secondLinkOnSameLine() {
        String text = "[one](a) 和 [two](b)";
        int pos = text.indexOf("two");
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, pos);
        assertTrue(li.isValid());
        assertEquals("two", li.title);
        assertEquals("b", li.url);
        assertEquals(text.lastIndexOf('['), li.start);
    }

    @Test
    public void extract_lineScoped() {
        String text = "[one](a)\n[two](b)";
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, text.indexOf('\n') + 3);
        assertTrue(li.isValid());
        assertEquals("two", li.title);
    }

    @Test
    public void extract_posAtEndBoundary_hits() {
        // markor 语义 end >= pos：光标紧贴链接末尾）之后仍视为在链接上
        String text = "[t](u)后";
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, text.length() - 1);
        assertTrue(li.isValid());
    }

    @Test
    public void extract_percentDecodedUrl() {
        String text = "[t](a%20b%20c.md)";
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, 1);
        assertTrue(li.isValid());
        assertEquals("a b c.md", li.url);
    }

    @Test
    public void extract_defensiveInputs() {
        assertFalse(MarkdownLinkHelper.extract(null, 0).isValid());
        assertFalse(MarkdownLinkHelper.extract("abc", -1).isValid());
        assertFalse(MarkdownLinkHelper.extract("abc", 99).isValid());
    }

    // ── insertLink：光标插入 / 链接替换 / 选区替换 ──────────────────────────

    @Test
    public void insert_noSelection_noLink_cursor() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("abc", 1, 1, "t", "u", false);
        assertEquals("a[t](u)bc", r.text);
        assertEquals(7, r.selStart);
        assertEquals(7, r.selEnd);
    }

    @Test
    public void insert_cursorInsideLink_replacesWholeLink() {
        String text = "预 [旧](x) 后";
        int s = text.indexOf('旧');
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink(text, s, s, "新", "y", false);
        assertEquals("预 [新](y) 后", r.text);
        assertEquals(8, r.selStart);
        assertEquals(8, r.selEnd);
    }

    @Test
    public void insert_cursorAtLinkEnd_replacesWholeLink() {
        String text = "前[旧](x)后";
        int pos = text.indexOf(')');
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink(text, pos, pos, "t", "u", false);
        assertEquals("前[t](u)后", r.text);
    }

    @Test
    public void insert_selection_replacedByLink() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("abcde", 1, 4, "sel", "u", false);
        assertEquals("a[sel](u)e", r.text);
        assertEquals(9, r.selStart);
        assertEquals(9, r.selEnd);
    }

    @Test
    public void insert_imageTemplate() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("x", 1, 1, "alt", "img.png", true);
        assertEquals("x![alt](img.png)", r.text);
        assertEquals(1 + 15, r.selStart);
    }

    @Test
    public void insert_emptyUrl_noop() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("abc", 1, 1, "t", "  ", false);
        assertEquals("abc", r.text);
        assertEquals(1, r.selStart);
        assertEquals(1, r.selEnd);
    }

    @Test
    public void insert_nullUrl_noop() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("abc", 1, 1, "t", null, false);
        assertEquals("abc", r.text);
    }

    @Test
    public void insert_titleAndUrlTrimmed() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("", 0, 0, " 标题 ", " u ", false);
        assertEquals("[标题](u)", r.text);
    }

    @Test
    public void insert_urlSpaceEncodedAsPercent20() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("", 0, 0, "t", "http://x/y z", false);
        assertEquals("[t](http://x/y%20z)", r.text);
    }

    @Test
    public void insert_titlePipeCharPreserved() {
        // 取舍（与 markor |→/ 不同）：CommonMark 中管道符在链接文本合法，保留字面
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("", 0, 0, "a|b", "u", false);
        assertEquals("[a|b](u)", r.text);
    }

    @Test
    public void insert_roundTripExtractThenInsert() {
        String text = "见 [说明](docs/a%20b.md) 后";
        int s = text.indexOf('说');
        MarkdownLinkHelper.LinkInfo li = MarkdownLinkHelper.extract(text, s);
        assertTrue(li.isValid());
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink(text, s, s, li.title, li.url, li.isImage);
        assertEquals(text, r.text);
        assertEquals(li.end, r.selStart);
    }

    @Test
    public void insert_invertedSelection_normalized() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("abcde", 4, 1, "t", "u", false);
        assertEquals("a[t](u)e", r.text);
    }

    @Test
    public void insert_atTextEnd() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("end", 3, 3, "t", "u", false);
        assertEquals("end[t](u)", r.text);
        assertEquals(9, r.selStart);
    }

    @Test
    public void insert_nullText() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink(null, 0, 0, "t", "u", false);
        assertEquals(null, r.text);
    }

    @Test
    public void insert_outOfRangeSelection_clamped() {
        MarkdownLinkHelper.Result r = MarkdownLinkHelper.insertLink("ab", 99, 99, "t", "u", false);
        assertEquals("ab[t](u)", r.text);
    }
}
