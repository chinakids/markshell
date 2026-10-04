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

    // ── Text（第卅五轮 #47：运维高频配置/脚本/说明文件补齐） ───────────────

    @Test
    public void textTxt() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/logs/info.txt");
    }

    @Test
    public void textLogUpper() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/LOG/daily.LOG");
    }

    @Test
    public void textYaml() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/docker/conf.yaml");
    }

    @Test
    public void textYml() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/app/docker-compose.yml");
    }

    @Test
    public void textConf() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/nginx/nginx.conf");
    }

    @Test
    public void textProperties() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/app/application.properties");
    }

    @Test
    public void textIni() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/php.ini");
    }

    @Test
    public void textToml() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/app/pyproject.toml");
    }

    @Test
    public void textEnv() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/app/.env");
    }

    @Test
    public void textShellScript() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/opt/deploy.sh");
    }

    @Test
    public void textSql() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/db/backup.sql");
    }

    @Test
    public void textReadmeNoExtension() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/a/README");
    }

    @Test
    public void textDockerfile() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/app/Dockerfile");
    }

    @Test
    public void textMakefile() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/src/Makefile");
    }

    @Test
    public void textLicense() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/LICENSE");
    }

    // ── Code C/C++（第卅五轮 #47：clike 语法正确高亮） ──────────────────────

    @Test
    public void codeCFile() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/src/main.c");
    }

    @Test
    public void codeHeader() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/include/util.h");
    }

    @Test
    public void codeCpp() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/src/main.cpp");
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
    public void unsupportedDocx() {
        assertNull(OpenFileHelper.detectViewerKind("/a/doc.docx"));
    }

    @Test
    public void unsupportedUnknownBinary() {
        // 无扩展名非已知说明文件（sshd_config 等尚未覆盖=后继候选，如实标注）
        assertNull(OpenFileHelper.detectViewerKind("/etc/ssh/sshd_config"));
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
