package com.ssh.mdreader.model;

/**
 * 递归搜索结果条目（轻量 POJO，SshManager 回调载体）。
 *
 * <p>{@code path} 为远端绝对路径（打开/跳转用）；{@code relativePath} 为相对搜索
 * 根目录的展示路径（结果列表显示用）；{@code directory} 区分目录/文件（点击行为
 * 分派：目录=跳转，文件=打开）。</p>
 */
public class SearchResult {
    private final String name;
    private final String path;
    private final String relativePath;
    private final boolean directory;

    public SearchResult(String name, String path, String relativePath, boolean directory) {
        this.name = name;
        this.path = path;
        this.relativePath = relativePath;
        this.directory = directory;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public boolean isDirectory() {
        return directory;
    }
}
