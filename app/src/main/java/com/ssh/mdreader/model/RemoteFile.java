package com.ssh.mdreader.model;

import com.ssh.mdreader.util.DownloadHelper;

import java.util.ArrayList;
import java.util.List;

public class RemoteFile {
    private final String name;
    private final String path;
    private final boolean directory;
    private final long size;
    private final int permissions;
    /** 最后修改时间（Unix 秒，来自 SFTP attrs.getMTime()；未知为 0）。 */
    private final long mtime;

    private int depth;
    private boolean expanded;
    private boolean childrenLoaded;
    private final List<RemoteFile> children = new ArrayList<>();

    public RemoteFile(String name, String path, boolean directory, long size, int permissions, long mtime) {
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.size = size;
        this.permissions = permissions;
        this.mtime = mtime;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public boolean isDirectory() { return directory; }
    public long getSize() { return size; }
    public int getPermissions() { return permissions; }
    public long getMtime() { return mtime; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public boolean isChildrenLoaded() { return childrenLoaded; }
    public void setChildrenLoaded(boolean childrenLoaded) { this.childrenLoaded = childrenLoaded; }

    public List<RemoteFile> getChildren() { return children; }
    public void setChildren(List<RemoteFile> newChildren) {
        children.clear();
        for (RemoteFile child : newChildren) {
            child.setDepth(this.depth + 1);
            children.add(child);
        }
        childrenLoaded = true;
    }

    public boolean isMarkdown() {
        if (directory) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown");
    }

    public boolean isCsv() {
        if (directory) return false;
        return name.toLowerCase().endsWith(".csv");
    }

    public boolean isCodeFile() {
        if (directory) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".json")
                || lower.endsWith(".py")
                || lower.endsWith(".js") || lower.endsWith(".mjs")
                || lower.endsWith(".ts")
                || lower.endsWith(".jsx") || lower.endsWith(".tsx")
                || lower.endsWith(".html") || lower.endsWith(".htm")
                || lower.endsWith(".css")
                || lower.endsWith(".java")
                || lower.endsWith(".xml") || lower.endsWith(".svg")
                // C/C++ 族（能力发现循环第卅五轮 #47：Prism4j clike 语法正确高亮）
                || lower.endsWith(".c") || lower.endsWith(".h")
                || lower.endsWith(".cpp") || lower.endsWith(".cc")
                || lower.endsWith(".cxx") || lower.endsWith(".hh")
                || lower.endsWith(".hpp")
                // Shell 脚本（第 52 轮：bash grammar 落地后由纯文本查看器升级到代码
                // 查看器，获语法高亮+行号+查找；CodeHighlighter.resolveLanguage 同步）
                || lower.endsWith(".sh") || lower.endsWith(".bash") || lower.endsWith(".zsh")
                // 有 Prism grammar 的代码族（第 53 轮：PrismBundle 已含 go/kotlin/sql/yaml
                // 而扩展名路由未接=与本轮 bash 同型缺口；升级后获语法高亮+行号+查找）
                || lower.endsWith(".sql")
                || lower.endsWith(".go")
                || lower.endsWith(".kt")
                || lower.endsWith(".yaml") || lower.endsWith(".yml")
                // 无扩展名 dot 前缀 shell 启动文件（第卌六轮：与 .sh/.bash/.zsh 同类
                // 即 shell 脚本——按迭代96 同判据（bash 高亮已备）路由到代码查看器）
                || isShellDotName()
                // 无扩展名系统级 shell 启动文件（第 47 轮：/etc/profile、/etc/bashrc、
                // /etc/csh.login 等=shell 脚本，与 dot 同判据路由代码查看器获 bash 高亮）
                || isShellPlainName();
    }

    /** 无扩展名但实为 shell 脚本的点前缀启动文件（大小写不敏感白名单）。 */
    private boolean isShellDotName() {
        String n = name.toLowerCase();
        return n.equals(".bashrc")
                || n.equals(".bash_profile") || n.equals(".bash_login")
                || n.equals(".bash_logout") || n.equals(".bash_aliases")
                || n.equals(".zshrc") || n.equals(".zprofile")
                || n.equals(".zshenv") || n.equals(".zlogin") || n.equals(".zlogout")
                || n.equals(".profile") || n.equals(".envrc")
                || n.equals(".kshrc") || n.equals(".cshrc") || n.equals(".tcshrc");
    }

    /** 无扩展名但实为 shell 脚本的系统级启动文件（/etc 下常见，大小写不敏感白名单）。 */
    private boolean isShellPlainName() {
        String n = name.toLowerCase();
        return n.equals("profile")
                || n.equals("bashrc")
                || n.equals("csh.login") || n.equals("csh.cshrc") || n.equals("csh.logout")
                || n.equals("tcshrc")
                || n.equals("zshenv") || n.equals("zprofile")
                || n.equals("zshrc") || n.equals("zlogin") || n.equals("zlogout");
    }

    public boolean isImageFile() {
        if (directory) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    public boolean isTextFile() {
        if (directory) return false;
        String lower = name.toLowerCase();
        // 运维高频文本/配置/脚本扩展名（第卅五轮 #47：nagios/nginx/.env/docker-compose
        // 等点击不再「暂不支持此文件类型」；对标 markor PlaintextTextConverter
        // EXT_TEXT+EXT_CODE_HL 按文本打开的惯例，无对应高亮语法则走纯文本查看器）
        return lower.endsWith(".txt")
                || lower.endsWith(".log")
                || lower.endsWith(".text")
                || lower.endsWith(".conf") || lower.endsWith(".config")
                || lower.endsWith(".properties") || lower.endsWith(".ini")
                || lower.endsWith(".cfg") || lower.endsWith(".toml")
                || lower.endsWith(".env")
                // .sh/.bash/.zsh 已移至 isCodeFile（第 52 轮），此处不再包含
                // .sql/.go/.kt/.yaml/.yml 已移至 isCodeFile（第 53 轮，Prism grammar 已备）
                || lower.endsWith(".pl") || lower.endsWith(".pm") || lower.endsWith(".rb")
                || lower.endsWith(".php")
                || lower.endsWith(".rs") || lower.endsWith(".swift") || lower.endsWith(".lua")
                || lower.endsWith(".srt") || lower.endsWith(".lrc")
                || lower.endsWith(".m3u") || lower.endsWith(".m3u8")
                // 无扩展名 dot 前缀配置/清单文件特判（.gitignore/.npmrc 等，运维正餐；
                // shell 启动类 .bashrc 等已在 isCodeFile 分流，此处不重复）
                || isCommonDotName()
                // 无扩展名运维系统文本文件特判（sshd_config/known_hosts/mime.types 等，
                // /etc 与 ~/.ssh 高频；与 dot 轮同判据白名单开放）
                || isCommonServiceName()
                // ~/.ssh 目录内无扩展名文件特判（config/authorized_keys/私钥等；按父目录
                // 白名单=仅 .ssh 下命中，「config」等通用名不做全局误判）
                || isDotSshFile()
                // .pub 公钥文件特判（~/.ssh/id_*.pub 与 /etc/ssh/ssh_host_*_key.pub；
                // 单行文本公钥（type base64 comment），按父目录白名单=仅 .ssh/ssh 下命中）
                || isSshPubFile()
                // 无扩展名静态说明/构建文件特判（README/Dockerfile/Makefile 等，git 仓库高频）
                || isCommonPlainName();
    }

    /** 无扩展名但可确定是文本配置的点前缀文件（大小写不敏感白名单）。 */
    private boolean isCommonDotName() {
        String n = name.toLowerCase();
        // .env 系列变体（.env.local/.env.production/.env.example 等，以 .env. 开头）
        if (n.startsWith(".env.")) return true;
        return n.equals(".gitignore") || n.equals(".gitattributes")
                || n.equals(".gitconfig") || n.equals(".gitmodules")
                || n.equals(".npmrc") || n.equals(".yarnrc") || n.equals(".bowerrc")
                || n.equals(".editorconfig") || n.equals(".dockerignore")
                || n.equals(".htaccess") || n.equals(".htpasswd")
                || n.equals(".vimrc") || n.equals(".inputrc") || n.equals(".screenrc")
                || n.equals(".hgignore") || n.equals(".flaskenv")
                || n.equals(".eslintrc") || n.equals(".prettierrc") || n.equals(".babelrc");
    }

    /** 无扩展名但可确定是文本的运维系统文件（/etc 与 ~/.ssh 高频，大小写不敏感白名单）。 */
    private boolean isCommonServiceName() {
        String n = name.toLowerCase();
        return n.equals("sshd_config") || n.equals("ssh_config")
                || n.equals("known_hosts") || n.equals("ssh_known_hosts")
                || n.equals("hosts") || n.equals("hostname")
                || n.equals("passwd") || n.equals("group")
                || n.equals("fstab") || n.equals("exports")
                || n.equals("crontab") || n.equals("aliases")
                || n.equals("mime.types")
                || n.equals("services") || n.equals("protocols")
                || n.equals("motd") || n.equals("issue") || n.equals("netrc");
    }

    /**
     * 无扩展名但按父目录白名单可确定的 ~/.ssh 文件（大小写不敏感；仅当父目录为
     * .ssh 才命中——「config」等通用名不全局误判）。名单依 OpenSSH 官方 man
     * （ssh(1)/sshd(8)/ssh-keygen(1) FILES 节）：config、authorized_keys(/2)、
     * authorized_principals、known_hosts2、environment 与私钥 id_* 族。
     */
    private boolean isDotSshFile() {
        String n = name.toLowerCase();
        boolean nameOk = n.equals("config")
                || n.equals("authorized_keys") || n.equals("authorized_keys2")
                || n.equals("authorized_principals")
                || n.equals("known_hosts2")
                || n.equals("environment")
                || n.equals("id_rsa") || n.equals("id_dsa") || n.equals("id_ecdsa")
                || n.equals("id_ed25519")
                || n.equals("id_ecdsa_sk") || n.equals("id_ed25519_sk")
                || n.equals("id_mldsa44_ed25519");
        return nameOk && isInDotSshDir();
    }

    /** path 的父目录是否为 .ssh（OpenSSH 用户级约定名；支持绝对/相对主目录）。 */
    private boolean isInDotSshDir() {
        return isParentDir(".ssh");
    }

    /** path 的父目录是否在 ssh 配置目录白名单内（.ssh 用户级 / ssh 系统级=/etc/ssh）。 */
    private boolean isInSshDir() {
        return isParentDir(".ssh") || isParentDir("ssh");
    }

    /** path 的父目录（去尾斜杠后）是否恰为 dir 或以 dir/ 结尾，小写精确匹配。 */
    private boolean isParentDir(String dir) {
        if (path == null) return false;
        int slash = path.lastIndexOf('/');
        if (slash < 0) return false;
        String parent = path.substring(0, slash);
        while (parent.endsWith("/")) {
            parent = parent.substring(0, parent.length() - 1);
        }
        return parent.equals(dir) || parent.endsWith("/" + dir);
    }

    /**
     * 按父目录白名单可确定的 OpenSSH 公钥文件（大小写不敏感；仅当父目录为 .ssh
     * 或 ssh（/etc/ssh）才命中——其它目录的 .pub 不全局开放，避免误判）。
     * 依据 OpenSSH 官方 man FILES 节：ssh-keygen(1) 的 ~/.ssh/id_*.pub（用户认证公钥）
     * 与 sshd(8) 的 /etc/ssh/ssh_host_*_key.pub（主机公钥，world-readable）均为
     * 「keytype base64 [comment]」单行文本。
     */
    private boolean isSshPubFile() {
        return name.toLowerCase().endsWith(".pub") && isInSshDir();
    }

    /** 无扩展名但可确定是文本内容的常见文件名（大小写不敏感特判）。 */
    private boolean isCommonPlainName() {
        String n = name.toLowerCase();
        return n.equals("readme")
                || n.equals("dockerfile")
                || n.equals("makefile")
                || n.equals("license")
                || n.equals("changelog")
                || n.equals("authors");
    }

    public boolean isViewable() {
        return isMarkdown() || isCsv() || isCodeFile() || isImageFile() || isTextFile();
    }

    public String getFormattedSize() {
        if (directory) return "";
        // 委托 DownloadHelper.formatBytes：与「下载到本地」完成提示同一语义源（B/KB/MB）。
        return DownloadHelper.formatBytes(size);
    }
}
