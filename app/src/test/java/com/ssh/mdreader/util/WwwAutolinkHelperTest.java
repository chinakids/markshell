package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * GFM www 裸域自动链接匹配测试（迭代110）：规则黄金向量直接取自 GFM spec
 * 「Autolinks (extension)」官方 example（2026-10-10 经 github/cmark-gfm
 * test/spec.txt 提取），另含前边界/段规则/零宽标记共存/不误链用例。
 */
public class WwwAutolinkHelperTest {

    private static String targetOf(List<WwwAutolinkHelper.WwwLink> links, int index) {
        return links.get(index).target;
    }

    @Test
    public void bareDomain_schemeInserted() {
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks("www.commonmark.org");
        assertEquals(1, links.size());
        assertEquals("http://www.commonmark.org", targetOf(links, 0));
        assertEquals(0, links.get(0).begin);
        assertEquals("www.commonmark.org".length(), links.get(0).end);
    }

    @Test
    public void pathAfterDomain_isPartOfLink() {
        List<WwwAutolinkHelper.WwwLink> links =
                WwwAutolinkHelper.findLinks("Visit www.commonmark.org/help for more information.");
        assertEquals(1, links.size());
        assertEquals("http://www.commonmark.org/help", targetOf(links, 0));
        assertEquals("Visit ".length(), links.get(0).begin);
        assertTrue(links.get(0).end < "Visit www.commonmark.org/help for more information.".length());
    }

    @Test
    public void trailingPeriod_excluded() {
        // GFM 官方 example：Visit www.commonmark.org.
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks("Visit www.commonmark.org.");
        assertEquals(1, links.size());
        assertEquals("http://www.commonmark.org", targetOf(links, 0));
        assertEquals(links.get(0).end, "Visit www.commonmark.org".length());
    }

    @Test
    public void trailingPunctChain_excluded() {
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks("www.example.com!?!");
        assertEquals(1, links.size());
        assertEquals("http://www.example.com", targetOf(links, 0));
    }

    @Test
    public void unmatchedTrailingParens_excluded() {
        // GFM 官方 example：www.google.com/search?q=Markup+(business)))
        List<WwwAutolinkHelper.WwwLink> links =
                WwwAutolinkHelper.findLinks("www.google.com/search?q=Markup+(business)))");
        assertEquals(1, links.size());
        assertEquals("http://www.google.com/search?q=Markup+(business)", targetOf(links, 0));
    }

    @Test
    public void outerParens_preserved() {
        // GFM 官方 example：(www.google.com/search?q=Markup+(business))
        String text = "(www.google.com/search?q=Markup+(business))";
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks(text);
        assertEquals(1, links.size());
        assertEquals("http://www.google.com/search?q=Markup+(business)", targetOf(links, 0));
        // 外层左括号为边界符，右括号被配平规则排除
        assertEquals(1, links.get(0).begin);
        assertEquals(text.length() - 1, links.get(0).end);
    }

    @Test
    public void entityReference_excluded() {
        // GFM 官方 example：www.google.com/search?q=commonmark&hl;
        List<WwwAutolinkHelper.WwwLink> links =
                WwwAutolinkHelper.findLinks("www.google.com/search?q=commonmark&hl;");
        assertEquals(1, links.size());
        assertEquals("http://www.google.com/search?q=commonmark", targetOf(links, 0));
        assertEquals("www.google.com/search?q=commonmark".length(), links.get(0).end);
    }

    @Test
    public void semicolonWithoutAmp_notEntity() {
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks("www.example.com/a;b");
        assertEquals(1, links.size());
        assertEquals("http://www.example.com/a;b", targetOf(links, 0));
    }

    @Test
    public void lt_endsLink() {
        // GFM 官方 example：www.commonmark.org/he<lp
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks("www.commonmark.org/he<lp");
        assertEquals(1, links.size());
        assertEquals("http://www.commonmark.org/he", targetOf(links, 0));
    }

    @Test
    public void noMatch_httpSchemePrefixed() {
        assertTrue(WwwAutolinkHelper.findLinks("http://www.example.com").isEmpty());
    }

    @Test
    public void noMatch_gluedToWord() {
        assertTrue(WwwAutolinkHelper.findLinks("foowww.example.com").isEmpty());
    }

    @Test
    public void noMatch_singleSegment() {
        assertTrue(WwwAutolinkHelper.findLinks("www.example").isEmpty());
    }

    @Test
    public void noMatch_lastTwoSegmentsContainUnderscore() {
        assertTrue(WwwAutolinkHelper.findLinks("www.a_b.example").isEmpty());
    }

    @Test
    public void match_underscoreAllowedInEarlierSegment() {
        // GFM：下划线仅禁最后两段，更前段允许
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks("www.a_b.example.com");
        assertEquals(1, links.size());
        assertEquals("http://www.a_b.example.com", targetOf(links, 0));
    }

    @Test
    public void noMatch_uppercase() {
        assertTrue(WwwAutolinkHelper.findLinks("WWW.example.com").isEmpty());
    }

    @Test
    public void noMatch_numbers() {
        assertTrue(WwwAutolinkHelper.findLinks("host=1.2.3.4 port=22 版本 123.456.789").isEmpty());
    }

    @Test
    public void markerChars_excludedFromLink() {
        // 任务清单标记（U+200B）跟在 URL 后：链接不含零宽字符
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks("www.example.com\u200B");
        assertEquals(1, links.size());
        assertEquals("http://www.example.com", targetOf(links, 0));
        assertEquals("www.example.com".length(), links.get(0).end);
    }

    @Test
    public void markerBetweenNote_stillLinked() {
        // "- [ ]<U+200B> www.example.com"：标记在空白前，www 前边界=空格
        List<WwwAutolinkHelper.WwwLink> links =
                WwwAutolinkHelper.findLinks("- [ ]\u200B www.example.com");
        assertEquals(1, links.size());
        assertEquals("http://www.example.com", targetOf(links, 0));
    }

    @Test
    public void multipleLinks_inOrder() {
        List<WwwAutolinkHelper.WwwLink> links =
                WwwAutolinkHelper.findLinks("a www.a.com 和 www.b.com");
        assertEquals(2, links.size());
        assertEquals("http://www.a.com", targetOf(links, 0));
        assertEquals("http://www.b.com", targetOf(links, 1));
        assertEquals(links.get(0).end - links.get(0).begin, "www.a.com".length());
        assertTrue(links.get(0).end < links.get(1).begin);
    }

    @Test
    public void email_notHandled() {
        // 邮箱由官方 AutolinkExtension 处理，本层不产 www 匹配
        assertTrue(WwwAutolinkHelper.findLinks("foo@bar.baz").isEmpty());
    }

    @Test
    public void markerImmediatelyBefore_stillBoundary() {
        // 大纲注入形态：#<U+200C>www.example.com（标记在标题正文起点、紧贴 www）
        List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks("\u200Cwww.example.com");
        assertEquals(1, links.size());
        assertEquals("http://www.example.com", targetOf(links, 0));
    }

    @Test
    public void emptyAndNullSafe() {
        assertNotNull(WwwAutolinkHelper.findLinks(""));
        assertTrue(WwwAutolinkHelper.findLinks("").isEmpty());
    }
}
