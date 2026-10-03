package com.ssh.mdreader.model;

/**
 * 最近打开文件（阅读历史）条目模型：远端绝对路径 + 上次打开时间戳（毫秒）。
 *
 * <p>归属=服务器（按连接键 host:port:user 隔离，与目录书签同范式，见
 * {@link com.ssh.mdreader.util.RecentFilesHelper}）。时间戳由调用方在记录时传入
 * {@code System.currentTimeMillis()}，仅作展示/排序保留，不参与去重判定。</p>
 */
public class RecentFileEntry {

    private String path;
    private long ts;

    public RecentFileEntry() {
    }

    public RecentFileEntry(String path, long ts) {
        this.path = path;
        this.ts = ts;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }
}
