package com.ssh.mdreader.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LinkTargetHelperTest {

    // ── classify ───────────────────────────────────────────────────────────

    @Test
    public void externalUrl_http() {
        LinkTargetHelper.Resolved r = LinkTargetHelper.classify("https://example.com/a?b=c", "/root/doc.md");
        assertEquals(LinkTargetHelper.Kind.EXTERNAL_URL, r.kind);
        assertEquals("https://example.com/a?b=c", r.target);
    }

    @Test
    public void externalUrl_mailtoAndFtp() {
        assertEquals(LinkTargetHelper.Kind.EXTERNAL_URL,
                LinkTargetHelper.classify("mailto:x@y.z", null).kind);
        assertEquals(LinkTargetHelper.Kind.EXTERNAL_URL,
                LinkTargetHelper.classify("ftp://host/file", null).kind);
    }

    @Test
    public void externalUrl_keepsPercentAndPlus() {
        LinkTargetHelper.Resolved r = LinkTargetHelper.classify("https://a/b%20c+d", null);
        assertEquals("https://a/b%20c+d", r.target);
    }

    @Test
    public void scheme_mixedCaseAndDigits() {
        assertEquals(LinkTargetHelper.Kind.EXTERNAL_URL,
                LinkTargetHelper.classify("HTTPS://x", null).kind);
        assertEquals(LinkTargetHelper.Kind.EXTERNAL_URL,
                LinkTargetHelper.classify("ssh2://x", null).kind);
    }

    @Test
    public void scheme_notDetectedWithoutColon() {
        // 路径中冒号前不是 scheme（含 '/'）→ 远程文件
        LinkTargetHelper.Resolved r = LinkTargetHelper.classify("a/b:c", null);
        assertEquals(LinkTargetHelper.Kind.REMOTE_FILE, r.kind);
        assertEquals("a/b:c", r.target);
    }

    @Test
    public void anchor_basic() {
        LinkTargetHelper.Resolved r = LinkTargetHelper.classify("# 安装 指南", null);
        assertEquals(LinkTargetHelper.Kind.PAGE_ANCHOR, r.kind);
        assertEquals("安装 指南", r.target);
    }

    @Test
    public void anchor_percentDecoded() {
        LinkTargetHelper.Resolved r = LinkTargetHelper.classify("#%E5%AE%89%E8%A3%85", null);
        assertEquals("安装", r.target);
    }

    @Test
    public void anchor_emptyOrJustHash_invalid() {
        assertEquals(LinkTargetHelper.Kind.INVALID, LinkTargetHelper.classify("#", null).kind);
        assertEquals(LinkTargetHelper.Kind.INVALID, LinkTargetHelper.classify("  #  ", null).kind);
    }

    @Test
    public void invalid_nullEmptyWhitespace() {
        assertEquals(LinkTargetHelper.Kind.INVALID, LinkTargetHelper.classify(null, null).kind);
        assertEquals(LinkTargetHelper.Kind.INVALID, LinkTargetHelper.classify("", null).kind);
        assertEquals(LinkTargetHelper.Kind.INVALID, LinkTargetHelper.classify("   ", null).kind);
    }

    @Test
    public void remote_relativeResolvedAgainstCurrentFile() {
        LinkTargetHelper.Resolved r = LinkTargetHelper.classify("docs/guide.md", "/home/u/readme.md");
        assertEquals(LinkTargetHelper.Kind.REMOTE_FILE, r.kind);
        assertEquals("/home/u/docs/guide.md", r.target);
    }

    @Test
    public void remote_absoluteKeptNormalized() {
        LinkTargetHelper.Resolved r = LinkTargetHelper.classify("/var/log/a.log", "/home/u/x.md");
        assertEquals("/var/log/a.log", r.target);
    }

    @Test
    public void remote_parentTraversalBoundedAtRoot() {
        assertEquals("/a.txt",
                LinkTargetHelper.classify("../../../../a.txt", "/home/u/docs/x.md").target);
    }

    @Test
    public void remote_percentDecoded() {
        LinkTargetHelper.Resolved r = LinkTargetHelper.classify("my%20file.txt", "/home/u/x.md");
        assertEquals("/home/u/my file.txt", r.target);
    }

    @Test
    public void remote_plusLiteralKept() {
        assertEquals("/home/u/a+b.txt",
                LinkTargetHelper.classify("a+b.txt", "/home/u/x.md").target);
    }

    // ── resolveRemotePath / normalize ──────────────────────────────────────

    @Test
    public void normalize_dotDotsAndRoot() {
        assertEquals("/a/b", LinkTargetHelper.normalize("/a/./b"));
        assertEquals("/a/b", LinkTargetHelper.normalize("/a/x/../b"));
        assertEquals("/", LinkTargetHelper.normalize("/a/.."));
        assertEquals("a/b", LinkTargetHelper.normalize("a/./b"));
        assertEquals("b", LinkTargetHelper.normalize("a/../b"));
        assertEquals("..", LinkTargetHelper.normalize(".."));
    }

    @Test
    public void resolveRemotePath_noCurrentFileKeepsRelative() {
        assertEquals("docs/x.md", LinkTargetHelper.resolveRemotePath(null, "docs/x.md"));
    }

    @Test
    public void resolveRemotePath_rootCurrentFile() {
        // 根下文件的基目录=“/”；相对链接解析在其上
        assertEquals("/x.md", LinkTargetHelper.resolveRemotePath("/readme.md", "x.md"));
    }

    // ── findAnchorHeading / slugify ────────────────────────────────────────

    private static TocHelper.Heading h(int level, String text) {
        return new TocHelper.Heading(level, text, 0, 0, false);
    }

    @Test
    public void findAnchor_exactIgnoreCase() {
        List<TocHelper.Heading> hs = new ArrayList<>(Arrays.asList(h(1, "安装"), h(2, "Usage")));
        assertEquals(1, LinkTargetHelper.findAnchorHeading(hs, " usage "));
    }

    @Test
    public void findAnchor_slugFuzzy() {
        List<TocHelper.Heading> hs = new ArrayList<>(Arrays.asList(h(1, "快速 开始!")));
        assertEquals(0, LinkTargetHelper.findAnchorHeading(hs, "快速-开始"));
    }

    @Test
    public void findAnchor_missing() {
        assertEquals(-1, LinkTargetHelper.findAnchorHeading(
                new ArrayList<>(Arrays.asList(h(1, "A"))), "B"));
        assertEquals(-1, LinkTargetHelper.findAnchorHeading(null, "A"));
        assertEquals(-1, LinkTargetHelper.findAnchorHeading(
                new ArrayList<>(Arrays.asList(h(1, "A"))), ""));
    }

    @Test
    public void slugify_examples() {
        assertEquals("quick-start", LinkTargetHelper.slugify("Quick Start!"));
        assertEquals("a-b", LinkTargetHelper.slugify("a -- b"));
        assertEquals("", LinkTargetHelper.slugify("---"));
        assertEquals("abc", LinkTargetHelper.slugify("ABC"));
    }
}
