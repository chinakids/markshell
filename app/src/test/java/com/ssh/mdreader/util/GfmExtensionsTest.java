package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;

import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * GFM 补齐解析语义测试（2026-10-07 第 51 轮）：验证 {@link GfmAutolinkPlugin} 与
 * {@code StrikethroughPlugin} 的 {@code configureParser} 注册后，commonmark 解析器
 * 产出符合预期的 AST——裸 URL/邮箱自动成 {@code Link}（复用既有 Link 渲染/点击通路）、
 * {@code ~~text~~} 成 {@code Strikethrough} 节点。
 *
 * <p>行为基线=JVM 探针实证（commonmark-ext-autolink 0.13.0）：带 scheme 的 URL 与
 * 邮箱才链接；{@code www.} 裸域不链接（GFM 规范偏差，如实落档）；行内代码/围栏代码
 * 不链接；已有链接内不重复链接；数字/版本串不误链。
 */
public class GfmExtensionsTest {

    private Parser parser;

    @Before
    public void setUp() {
        // 与 MarkdownReaderActivity/PreviewPaneHelper 的插件注册等价（纯解析器侧）
        Parser.Builder builder = Parser.builder();
        StrikethroughPlugin.create().configureParser(builder);
        GfmAutolinkPlugin.create().configureParser(builder);
        parser = builder.build();
    }

    private static List<Text> textNodes(Node root) {
        List<Text> texts = new ArrayList<>();
        collect(root, Text.class, texts);
        return texts;
    }

    private static List<Link> linkNodes(Node root) {
        List<Link> links = new ArrayList<>();
        collect(root, Link.class, links);
        return links;
    }

    private static <T extends Node> void collect(Node node, Class<T> clazz, List<T> out) {
        if (clazz.isInstance(node)) {
            out.add(clazz.cast(node));
        }
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
            collect(c, clazz, out);
        }
    }

    @Test
    public void strikethrough_basic() {
        Node root = parser.parse("~~删除的~~ 保留的");
        List<Strikethrough> sts = new ArrayList<>();
        collect(root, Strikethrough.class, sts);
        assertEquals("应恰好一个 Strikethrough 节点", 1, sts.size());
        List<Text> inner = textNodes(sts.get(0));
        assertEquals(1, inner.size());
        assertEquals("删除的", inner.get(0).getLiteral());
        // 保留的 无声息地留在段落 Text 中
        List<Text> all = textNodes(root);
        assertTrue(all.stream().anyMatch(t -> " 保留的".equals(t.getLiteral())));
    }

    @Test
    public void strikethrough_nested_emphasis() {
        Node root = parser.parse("~~a *b* c~~");
        List<Strikethrough> sts = new ArrayList<>();
        collect(root, Strikethrough.class, sts);
        assertEquals(1, sts.size());
        assertEquals("Strikethrough 内应含 Emphasis（嵌套不破坏）", 1, countOf(sts.get(0), org.commonmark.node.Emphasis.class));
    }

    @Test
    public void autolink_http_url() {
        Node root = parser.parse("看 https://example.com/path 说明");
        List<Link> links = linkNodes(root);
        assertEquals(1, links.size());
        assertEquals("https://example.com/path", links.get(0).getDestination());
        assertEquals("https://example.com/path", ((Text) links.get(0).getFirstChild()).getLiteral());
        // 前后文本保留在同段落，成对 Link 外的 Text 不丢
        List<Text> texts = textNodes(root);
        assertTrue(texts.stream().anyMatch(t -> "看 ".equals(t.getLiteral())));
        assertTrue(texts.stream().anyMatch(t -> " 说明".equals(t.getLiteral())));
    }

    @Test
    public void autolink_email_mailto() {
        Node root = parser.parse("联系 kk@example.com");
        List<Link> links = linkNodes(root);
        assertEquals(1, links.size());
        assertEquals("mailto:kk@example.com", links.get(0).getDestination());
    }

    @Test
    public void autolink_www_bare_not_linked() {
        // GFM 规范偏差如实落档：commonmark-ext-autolink 0.13.0 不链接 www. 裸域
        Node root = parser.parse("打开 www.example.com 浏览");
        assertEquals(0, linkNodes(root).size());
    }

    @Test
    public void autolink_skips_inline_code() {
        Node root = parser.parse("行内 `http://code.com` 不链");
        assertEquals(0, linkNodes(root).size());
        assertEquals(1, countOf(root, Code.class));
    }

    @Test
    public void autolink_skips_fenced_code() {
        Node root = parser.parse("```\nhttp://inblock.com\n```");
        assertEquals(0, linkNodes(root).size());
        assertEquals(1, countOf(root, FencedCodeBlock.class));
    }

    @Test
    public void autolink_inside_strikethrough_two_way() {
        // 删除线内 URL：Strikethrough(delimiter 解析期) 先成型，autolink(后置) 在
        // Text 内生成 Link=删除线+可点链接共存
        Node root = parser.parse("~~http://x.com~~");
        List<Strikethrough> sts = new ArrayList<>();
        collect(root, Strikethrough.class, sts);
        assertEquals(1, sts.size());
        List<Link> links = linkNodes(sts.get(0));
        assertEquals("删除线内的 URL 应成 Link", 1, links.size());
        assertEquals("http://x.com", links.get(0).getDestination());
    }

    @Test
    public void autolink_multiple_urls() {
        Node root = parser.parse("a http://a.com 和 http://b.com");
        List<Link> links = linkNodes(root);
        assertEquals(2, links.size());
        assertEquals("http://a.com", links.get(0).getDestination());
        assertEquals("http://b.com", links.get(1).getDestination());
    }

    @Test
    public void autolink_no_false_positive_on_numbers() {
        Node root = parser.parse("config: host=1.2.3.4 port=22 版本 123.456.789");
        assertEquals("数字/版本串不误链", 0, linkNodes(root).size());
        assertNotNull(root);
    }

    private static int countOf(Node node, Class<? extends Node> clazz) {
        int n = 0;
        if (clazz.isInstance(node)) {
            n++;
        }
        for (Node c = node.getFirstChild(); c != null; c = c.getNext()) {
            n += countOf(c, clazz);
        }
        return n;
    }
}
