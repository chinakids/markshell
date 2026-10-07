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
                || lower.endsWith(".yaml") || lower.endsWith(".yml");
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
                // 无扩展名静态说明/构建文件特判（README/Dockerfile/Makefile 等，git 仓库高频）
                || isCommonPlainName();
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
