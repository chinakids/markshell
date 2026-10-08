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
    public void codeYaml() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/docker/conf.yaml");
    }

    @Test
    public void codeYml() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/app/docker-compose.yml");
    }

    @Test
    public void codeGo() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/src/cmd/server.go");
    }

    @Test
    public void codeKt() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/src/Main.kt");
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
    public void codeShellSh() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/opt/deploy.sh");
    }

    @Test
    public void codeShellBash() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/opt/backup.bash");
    }

    @Test
    public void codeShellZsh() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/home/u/.config/init.zsh");
    }

    @Test
    public void codeSql() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/db/backup.sql");
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

    // ── Code dot 前缀 shell 启动文件（第卌六轮：.bashrc/.zshrc 等=shell 脚本，
    //    与 .sh/.bash/.zsh 同判据路由代码查看器获 bash 高亮） ────────────────

    @Test
    public void codeShellDotBashrc() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/home/u/.bashrc");
    }

    @Test
    public void codeShellDotBashProfile() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/home/u/.bash_profile");
    }

    @Test
    public void codeShellDotZshrc() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/home/u/.zshrc");
    }

    @Test
    public void codeShellDotProfile() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/home/u/.profile");
    }

    @Test
    public void codeShellDotEnvrc() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/repo/.envrc");
    }

    @Test
    public void codeShellDotUpperCase() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/home/u/.BASHRC");
    }

    // ── Text dot 前缀配置/清单文件（第卌六轮：.gitignore/.npmrc 等运维正餐） ──

    @Test
    public void textDotGitignore() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/repo/.gitignore");
    }

    @Test
    public void textDotGitconfig() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.gitconfig");
    }

    @Test
    public void textDotNpmrc() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/repo/.npmrc");
    }

    @Test
    public void textDotEnvLocal() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/app/.env.local");
    }

    @Test
    public void textDotEnvExample() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/app/.env.example");
    }

    @Test
    public void textDotEditorconfig() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/repo/.editorconfig");
    }

    @Test
    public void textDotDockerignore() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/repo/.dockerignore");
    }

    @Test
    public void textDotVimrc() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.vimrc");
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
        // 无扩展名非已知说明/运维文件（ds_store 等=仍不支持；sshd_config 已由
        // textService 系列覆盖，见下——白名单开放，非白名单保持不支持）
        assertNull(OpenFileHelper.detectViewerKind("/a/grub.cfg.bak.x"));
        assertNull(OpenFileHelper.detectViewerKind("/a/file_without_ext"));
    }

    // ── Text 无扩展名运维系统文件（第 47 轮：sshd_config/known_hosts 等白名单） ──

    @Test
    public void textServiceSshdConfig() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/ssh/sshd_config");
    }

    @Test
    public void textServiceSshConfig() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/ssh/ssh_config");
    }

    @Test
    public void textServiceKnownHosts() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/known_hosts");
    }

    @Test
    public void textServiceHosts() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/hosts");
    }

    @Test
    public void textServicePasswdGroup() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/passwd");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/group");
    }

    @Test
    public void textServiceFstabExports() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/fstab");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/exports");
    }

    @Test
    public void textServiceMimeTypes() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/mime.types");
    }

    @Test
    public void textServiceCrontabServices() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/crontab");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/services");
    }

    @Test
    public void textServiceUppercase() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/SSHD_CONFIG");
    }

    // ── Code 系统级无点 shell 启动文件（第 47 轮：/etc/profile 等白名单） ──

    @Test
    public void codeShellPlainProfile() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/etc/profile");
    }

    @Test
    public void codeShellPlainBashrc() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/etc/bashrc");
    }

    @Test
    public void codeShellPlainCshLogin() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/etc/csh.login");
    }

    @Test
    public void codeShellPlainZshrc() {
        assertKind(OpenFileHelper.ViewerKind.CODE, "/etc/zsh/zshrc");
    }

    @Test
    public void unsupportedDotUnknown() {
        // dot 前缀文件仅白名单开放（.bashrc/.gitignore 等）；非白名单仍不支持
        assertNull(OpenFileHelper.detectViewerKind("/.DS_Store"));
        assertNull(OpenFileHelper.detectViewerKind("/home/u/.unknownrc"));
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
