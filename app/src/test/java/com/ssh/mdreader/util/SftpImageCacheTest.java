package com.ssh.mdreader.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class SftpImageCacheTest {

    @Test
    public void putAndGet_hit() {
        SftpImageCache cache = new SftpImageCache(1000, 10);
        byte[] data = new byte[]{1, 2, 3};
        assertTrue(cache.put("/a.png", data));
        assertSame(data, cache.get("/a.png"));
        assertEquals(1, cache.size());
        assertEquals(3, cache.totalBytes());
    }

    @Test
    public void get_missReturnsNull() {
        SftpImageCache cache = new SftpImageCache(1000, 10);
        cache.put("/a.png", new byte[]{1});
        assertNull(cache.get("/b.png"));
    }

    @Test
    public void put_nullOrEmptyIgnored() {
        SftpImageCache cache = new SftpImageCache(100, 10);
        assertFalse(cache.put("/a.png", null));
        assertFalse(cache.put("/a.png", new byte[0]));
        assertEquals(0, cache.size());
    }

    @Test
    public void singleImageOverMax_notCached() {
        SftpImageCache cache = new SftpImageCache(100, 10);
        byte[] big = new byte[SftpImageCache.MAX_SINGLE_BYTES + 1];
        assertFalse(cache.put("/big.png", big));
        assertEquals(0, cache.size());
        assertEquals(0, cache.totalBytes());
    }

    @Test
    public void lruEvictsLeastRecentlyUsed() {
        // entries 上限 2：放入 a、b、c 后最久未用的 a 被逐出
        SftpImageCache cache = new SftpImageCache(1000, 2);
        cache.put("/a", new byte[]{1});
        cache.put("/b", new byte[]{1});
        cache.put("/c", new byte[]{1});
        assertEquals(2, cache.size());
        assertNull(cache.get("/a"));
        assertEquals(1, cache.get("/b").length);
        assertEquals(1, cache.get("/c").length);
    }

    @Test
    public void getRefreshesAccessOrder() {
        SftpImageCache cache = new SftpImageCache(1000, 2);
        cache.put("/a", new byte[]{1});
        cache.put("/b", new byte[]{1});
        // 访问 a 使其成为最近使用
        cache.get("/a");
        cache.put("/c", new byte[]{1});
        // 逐出的应是 b（最久未用）
        assertNull(cache.get("/b"));
        assertTrue(cache.get("/a") != null);
        assertTrue(cache.get("/c") != null);
    }

    @Test
    public void totalBytesBudget_evictsUntilFit() {
        SftpImageCache cache = new SftpImageCache(10, 100);
        cache.put("/a", new byte[6]);
        cache.put("/b", new byte[6]);   // 总 12 > 10 → 逐出 a
        assertEquals(1, cache.size());
        assertNull(cache.get("/a"));
        assertEquals(6, cache.totalBytes());
    }

    @Test
    public void putReplacingExisting_refreshesEntry() {
        SftpImageCache cache = new SftpImageCache(100, 10);
        cache.put("/a", new byte[3]);
        cache.put("/a", new byte[5]);
        assertEquals(1, cache.size());
        assertEquals(5, cache.totalBytes());
        assertEquals(5, cache.get("/a").length);
    }

    @Test
    public void clear_resetsAll() {
        SftpImageCache cache = new SftpImageCache(100, 10);
        cache.put("/a", new byte[3]);
        cache.put("/b", new byte[4]);
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.totalBytes());
    }
}
