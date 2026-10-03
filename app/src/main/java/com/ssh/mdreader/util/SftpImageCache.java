package com.ssh.mdreader.util;

import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * 已加载图片字节缓存（LRU，纯 Java 可 JVM 测试，无 Android 依赖）。
 *
 * <p><b>背景（路线图 #9）</b>：Markdown 内嵌图片经 {@link SftpImageSchemeHandler}
 * 从 SFTP 读取——同一文档反复打开、滚动回视口重新 attach 都会再次触发加载，
 * 若不缓存则每次重跑 SFTP 往返（网络是主要成本）。本层缓存<b>原始字节</b>
 * （解码动作留给 handler 每次执行，Bitmap 生命周期不与缓存绑定，避免 Drawable
 * 复用/泄露；解码在后台线程，成本远低于网络往返）。</p>
 *
 * <p><b>线程安全</b>（第三轮走查修正）：Markwon loader 使用
 * {@code Executors.newCachedThreadPool()}——同文档多图<b>并发</b>加载，且阅读器与
 * 两栏预览各有一个 loader（共享本缓存），本类所有公共方法因此<b>全量 synchronized</b>。
 * 规格：单次 {@code get} 失败后的并发重复加载允许发生（无 in-flight 去重，属性能优化
 * 而非正确性）；任何时刻不得抛 {@code ConcurrentModificationException} 且
 * {@code totalBytes} 记账必须与实际条目一致。</p>
 *
 * <p><b>容量策略</b>（文档化）：</p>
 * <ul>
 *   <li>单图超过 {@link #MAX_SINGLE_BYTES}（4MB）→ <b>不入缓存</b>（超大图应
 *       一次性显示，不为它逐出其它小图）。</li>
 *   <li>总字节预算 {@link #DEFAULT_MAX_TOTAL_BYTES}（12MB）与条目上限
 *       {@link #DEFAULT_MAX_ENTRIES}（30）双双生效：put 超预算时按访问序
 *       （LRU）逐出直到满足。</li>
 *   <li>{@code get} 命中自动刷新访问序；{@code null}/{@code empty} 字节不入缓存。</li>
 * </ul>
 */
public final class SftpImageCache {

    /** 单图上限：4MB。 */
    public static final int MAX_SINGLE_BYTES = 4 * 1024 * 1024;

    /** 默认总字节预算：12MB。 */
    public static final int DEFAULT_MAX_TOTAL_BYTES = 12 * 1024 * 1024;

    /** 默认条目上限：30。 */
    public static final int DEFAULT_MAX_ENTRIES = 30;

    private final int maxTotalBytes;
    private final int maxEntries;

    /** access-order=true：{@code get} 命中移到末尾（最近使用）；迭代序=最早→最新。 */
    private final LinkedHashMap<String, byte[]> entries = new LinkedHashMap<>(16, 0.75f, true);
    private int totalBytes;

    public SftpImageCache() {
        this(DEFAULT_MAX_TOTAL_BYTES, DEFAULT_MAX_ENTRIES);
    }

    public SftpImageCache(int maxTotalBytes, int maxEntries) {
        this.maxTotalBytes = maxTotalBytes;
        this.maxEntries = maxEntries;
    }

    /** 命中返回字节并刷新访问序；未命中返回 null。线程安全。 */
    public synchronized byte[] get(String path) {
        return entries.get(path);
    }

    /**
     * 存入（单图超限/空字节/不允许时直接忽略）。超预算时逐出最久未用条目，
     * 直至总字节与条目数都在限内。返回是否实际缓存。线程安全。
     */
    public synchronized boolean put(String path, byte[] bytes) {
        if (path == null || bytes == null || bytes.length == 0) return false;
        if (bytes.length > MAX_SINGLE_BYTES) return false;
        byte[] prev = entries.remove(path);
        if (prev != null) totalBytes -= prev.length;
        entries.put(path, bytes);
        totalBytes += bytes.length;
        evictIfNeeded();
        return true;
    }

    private void evictIfNeeded() {
        Iterator<byte[]> it = entries.values().iterator();
        while ((totalBytes > maxTotalBytes || entries.size() > maxEntries) && it.hasNext()) {
            byte[] victim = it.next();
            it.remove();
            totalBytes -= victim.length;
        }
    }

    /** 当前缓存条目数。线程安全。 */
    public synchronized int size() {
        return entries.size();
    }

    /** 当前缓存总字节数。线程安全。 */
    public synchronized int totalBytes() {
        return totalBytes;
    }

    /** 清空。线程安全。 */
    public synchronized void clear() {
        entries.clear();
        totalBytes = 0;
    }
}
