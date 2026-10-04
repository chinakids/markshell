package com.ssh.mdreader.model;

/**
 * 递归搜索结果条目（轻量 POJO，SshManager 回调载体）。
 *
 * <p>{@code path} 为远端绝对路径（打开/跳转用）；{@code relativePath} 为相对搜索
 * 根目录的展示路径（结果列表显示用）；{@code directory} 区分目录/文件（点击行为
 * 分派：目录=跳转，文件=打开）；{@code size} 为文件字节大小（搜索经 ls 结果
 * 已取到 attrs 数据，一并展出/传导：目录无有效大小恒为 0，文件结果打开时供
 * 大文件护栏（#33）使用——避免 path-only 入口因 size 缺失而绕过护栏）。</p>
 */
public class SearchResult {
    private final String name;
    private final String path;
    private final String relativePath;
    private final boolean directory;
    private final long size;

    public SearchResult(String name, String path, String relativePath, boolean directory, long size) {
        this.name = name;
        this.path = path;
        this.relativePath = relativePath;
        this.directory = directory;
        this.size = size;
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

    /** 文件字节大小；目录恒 0（SFTP 目录 attrs 大小无业务语义）。 */
    public long getSize() {
        return size;
    }
}
