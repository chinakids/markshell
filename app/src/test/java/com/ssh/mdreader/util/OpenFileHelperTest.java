package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * OpenFileHelper 查看器类型判定纯函数单测（能力发现循环第廿六轮 #37：
 * detectViewerKind 抽取为判定单一语义源；用例锁定与 RemoteFile 扩展名表严格同源）。
 */
public class OpenFileHelperTest {

    private void assertKind(OpenFileHelper.ViewerKind expected, String path) {
        assertEquals(expected, OpenFileHelper.detectViewerKind(path));
    }

    // ── Markdown ──────────────────────────────────────────────────────────

    @Test
    public void markdownMd() {
        assertKind(OpenFileHelper.ViewerKind.MARKDOWN, "/docs/notes.md");
    }

    @Test
    public void markdownMarkdown() {
        assertKind(OpenFileHelper.ViewerKind.MARKDOWN, "/a/b/README.markdown");
    }

    @Test
    public void markdownMdown() {
        assertKind(OpenFileHelper.ViewerKind.MARKDOWN, "/a/x.mdown");
    }

    @Test
    public void markdownUppercase() {
        assertKind(OpenFileHelper.ViewerKind.MARKDOWN, "/NOTES.MD");
    }

    // ── CSV ───────────────────────────────────────────────────────────────

    @Test
    public void csvPlain() {
        assertKind(OpenFileHelper.ViewerKind.CSV, "/data/table.csv");
    }

    // ── Code ──────────────────────────────────────────────────────────────

    @Test
    public void codeJson() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/conf/settings.json");
    }

    @Test
    public void codeJava() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/src/Main.java");
    }

    @Test
    public void codeTsx() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/src/App.tsx");
    }

    @Test
    public void codeHtmlHtm() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/web/index.htm");
    }

    @Test
    public void codeSvg() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/img/logo.svg");
    }

    // ── Image ─────────────────────────────────────────────────────────────

    @Test
    public void imagePng() {
        assertKind(OpenFileHelper.ViewerKind.IMAGE, "/img/a.png");
    }

    @Test
    public void imageJpeg() {
        assertKind(OpenFileHelper.ViewerKind.IMAGE, "/img/pic.JPEG");
    }

    @Test
    public void imageWebp() {
        assertKind(OpenFileHelper.ViewerKind.IMAGE, "/img/x.webp");
    }

    // ── Text ──────────────────────────────────────────────────────────────

    @Test
    public void textTxt() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/logs/info.txt");
    }

    @Test
    public void textLogUpper() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/LOG/daily.LOG");
    }

    // ── Unsupported ───────────────────────────────────────────────────────

    @Test
    public void unsupportedZip() {
        assertNull(OpenFileHelper.detectViewerKind("/a/archive.zip"));
    }

    @Test
    public void unsupportedPdf() {
        assertNull(OpenFileHelper.detectViewerKind("/a/doc.pdf"));
    }

    @Test
    public void unsupportedYaml() {
        assertNull(OpenFileHelper.detectViewerKind("/a/conf.yaml"));
    }

    @Test
    public void noExtension() {
        assertNull(OpenFileHelper.detectViewerKind("/a/README"));
    }

    @Test
    public void trailingDot() {
        assertNull(OpenFileHelper.detectViewerKind("/a/file."));
    }

    // ── Edge ──────────────────────────────────────────────────────────────

    @Test
    public void nullPath() {
        assertNull(OpenFileHelper.detectViewerKind(null));
    }

    @Test
    public void emptyPath() {
        assertNull(OpenFileHelper.detectViewerKind(""));
    }

    @Test
    public void rootFile() {
        assertKind(OpenFileHelper.ViewerKind.MARKDOWN, "/readme.md");
    }

    @Test
    public void trailingSlashHasNoName() {
        assertNull(OpenFileHelper.detectViewerKind("/docs/"));
    }

    @Test
    public void nameTakenFromLastSegmentOnly() {
        assertKind(OpenFileHelper.ViewerKind.MARKDOWN, "/a/b/notes.md");
    }
}
