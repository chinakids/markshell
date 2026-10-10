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

    // ── Text ~/.ssh 无扩展名文件（迭代103：按父目录白名单，config/authorized_keys/私钥族） ──

    @Test
    public void textDotSshConfig() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/config");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/CONFIG");
    }

    @Test
    public void textDotSshAuthKeys() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/authorized_keys");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/authorized_keys2");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/authorized_principals");
    }

    @Test
    public void textDotSshKnownHosts2Environment() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/root/.ssh/known_hosts2");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/root/.ssh/environment");
    }

    @Test
    public void textDotSshPrivateKeys() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_rsa");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_dsa");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_ecdsa");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_ed25519");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_ecdsa_sk");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_ed25519_sk");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_mldsa44_ed25519");
    }

    @Test
    public void textDotSshParentScopedOnly() {
        // 白名单按父目录开放：同名文件在非 .ssh 目录=仍不支持（不做全局误判）
        assertNull(OpenFileHelper.detectViewerKind("/srv/app/config"));
        assertNull(OpenFileHelper.detectViewerKind("/home/u/authorized_keys"));
        assertNull(OpenFileHelper.detectViewerKind("/etc/id_rsa"));
        // 相对主目录形态（主目录直接设为 .ssh 时）
        assertKind(OpenFileHelper.ViewerKind.TEXT, ".ssh/config");
        // 根下 .ssh 形态
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/.ssh/authorized_keys");
    }

    // ── Text .pub 公钥文件（迭代104：~/.ssh 与 /etc/ssh 下按父目录白名单） ──

    @Test
    public void textSshPubUserKeys() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_ed25519.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_rsa.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_dsa.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_ecdsa.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_ecdsa_sk.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_ed25519_sk.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/id_mldsa44_ed25519.pub");
    }

    @Test
    public void textSshPubHostKeys() {
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/ssh/ssh_host_rsa_key.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/ssh/ssh_host_ecdsa_key.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/ssh/ssh_host_ed25519_key.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/ssh/ssh_host_mldsa44_ed25519_key.pub");
    }

    @Test
    public void textSshPubCaseInsensitive() {
        // 文件名扩展名大小写不敏感；目录名按 Linux 标准小写约定（.ssh/ssh）精确匹配
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/.ssh/ID_ED25519.PUB");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/ssh/SSH_HOST_RSA_KEY.PUB");
    }

    @Test
    public void textSshPubParentScopedOnly() {
        // 白名单按父目录开放：非 .ssh/ssh 目录的同名 .pub=仍不支持（不全局开放）
        assertNull(OpenFileHelper.detectViewerKind("/srv/keys/id_rsa.pub"));
        assertNull(OpenFileHelper.detectViewerKind("/home/u/keys.pub"));
        assertNull(OpenFileHelper.detectViewerKind("/opt/ssh-custom/id_ed25519.pub"));
        // 相对主目录形态（主目录直接设为 .ssh 或 /etc 时）
        assertKind(OpenFileHelper.ViewerKind.TEXT, ".ssh/id_ed25519.pub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "ssh/ssh_host_ed25519_key.pub");
        // 根下 .ssh 形态
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/.ssh/id_rsa.pub");
    }

    // ── Text 运维配置文件补全（迭代105：sudoers/sources.list/.repo/systemd/udev 等） ──

    @Test
    public void textOpsExtListSourcesRepoRules() {
        // sources.list(5)：/etc/apt/sources.list 与 sources.list.d 文件名须为
        // .list（one-line）或 .sources（deb822）；yum.conf(5)：/etc/yum.repos.d 下
        // *.repo；udev(7)：规则文件必须 .rules 扩展名——均明文走纯文本查看器
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/apt/sources.list");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/apt/sources.list.d/debian.list");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/apt/sources.list.d/debian.sources");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/yum.repos.d/centos.repo");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/udev/rules.d/99-custom.rules");
        // 非标准目录同名扩展名=按扩展名开放（.list 为明文惯例，无二进制误判风险）
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/home/u/project/playlist.list");
    }

    @Test
    public void textOpsSystemdUnits() {
        // systemd.unit(5)：unit type suffix 必须为 .service/.socket/.device/.mount/
        // .automount/.swap/.target/.path/.timer/.slice/.scope 之一——INI 明文配置
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/systemd/system/nginx.service");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/lib/systemd/system/cron.service");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/systemd/system/mytimer.timer");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/systemd/system/foo.socket");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/systemd/system/auto.mount");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/systemd/system/multi-user.target");
    }

    @Test
    public void textOpsServiceNameWhitelist() {
        // sudoers(5) 策略由 /etc/sudoers 驱动；shadow(5) 加密口令文本；gshadow(5)
        // 组影子文本；login.defs(5) 站点级配置——均明文，白名单开放
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/sudoers");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/shadow");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/gshadow");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/login.defs");
    }

    @Test
    public void textOpsSudoersDotDir() {
        // sudoers(5)：@includedir 读入目录全部文件为 sudoers 规则（仅跳过含
        // '.'/'~' 名的临时文件）——片段文件名任意且无扩展名要求，按父目录白名单开放
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/sudoers.d/README");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/sudoers.d/openssh-server");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/sudoers.d/zz-custom");
        // 根级形态（isParentDir endsWith 分支，与 /.ssh 同判据）
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/sudoers.d/backup.run");
        // 非白名单目录的同名文件不误开（全局名与目录白名单都不命中）；
        // default 目录中未开放名的文件仍不支持（/etc/default/grub 已于第 63 轮开放）
        assertNull(OpenFileHelper.detectViewerKind("/etc/default/ssh"));
    }

    @Test
    public void textOpsCaseInsensitive() {
        // 扩展名大小写不敏感（目录名按 Linux 约定小写精确，与 .pub 轮同判据）
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/APT/SOURCES.LIST");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/systemd/system/Nginx.Service");
    }

    // ── Text 运维文件补全走查续（迭代107：pam.d/hosts.allow|deny/default-grub/locale.gen） ──

    @Test
    public void textOpsHostsAccessLocaleGen() {
        // hosts_access(5)（tcpd）：/etc/hosts.allow 命中则授权、否则 /etc/hosts.deny
        // 命中则拒绝、否则授权——内容为「daemon: client」规则行明文；locale.gen(5)：
        // /etc/locale.gen 列出 locale-gen 要生成的 locale（每行「locale charset」）
        // ——文件名含点但无扩展名语义，按精确名白名单开放
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/hosts.allow");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/hosts.deny");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/locale.gen");
    }

    @Test
    public void textOpsPamDir() {
        // pam.conf(5)：/etc/pam.d/ 目录中每个文件=一个服务的 PAM 配置（文件名=服务名
        // 小写），规则行明文——按父目录白名单开放（与 sudoers.d 同判据，名任意）
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/pam.d/common-auth");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/pam.d/sshd");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/pam.d/login");
        // 根级形态（isParentDir endsWith 分支，与 /.ssh 同判据）
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/pam.d/custom");
    }

    @Test
    public void textOpsDefaultGrub() {
        // GNU GRUB Manual Simple configuration：/etc/default/grub 被 grub-mkconfig 的
        // shell 脚本 source（normally 为 KEY=value 键值序列）——按键值配置语义走纯文本；
        // 父目录 default+文件名 grub 双条件白名单：非 default 目录「grub」不误判、
        // default 目录其它未开放名不开放
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/default/grub");
        assertKind(OpenFileHelper.ViewerKind.TEXT, "/etc/default/GRUB");
        assertNull(OpenFileHelper.detectViewerKind("/srv/app/grub"));
        assertNull(OpenFileHelper.detectViewerKind("/opt/default/list"));
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
