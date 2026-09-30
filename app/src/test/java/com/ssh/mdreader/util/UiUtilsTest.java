package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * UiUtils 纯逻辑单测：errorMessage 兜底文案与 getParentPath 路径计算。
 * 不依赖 android.widget（Toast/Snackbar 调用不覆盖；returnDefaultValues 兜底）。
 */
public class UiUtilsTest {

    @Test
    public void errorMessage_nullReturnsGenericFallback() {
        assertEquals("未知错误", UiUtils.errorMessage(null));
    }

    @Test
    public void errorMessage_messagePresentReturnsIt() {
        Throwable t = new IllegalStateException("服务器连接超时");
        assertEquals("服务器连接超时", UiUtils.errorMessage(t));
    }

    @Test
    public void errorMessage_blankMessageFallsBackToClassName() {
        Throwable t = new IllegalStateException("");
        assertEquals("IllegalStateException", UiUtils.errorMessage(t));
    }

    @Test
    public void errorMessage_anonymousClassWithBlankNameFallsBackToGeneric() {
        // 匿名类 getSimpleName() 为空字符串 → 只可能走到最终兜底
        Throwable t = new Throwable("") {};
        assertEquals("未知错误", UiUtils.errorMessage(t));
    }

    @Test
    public void getParentPath_rootStaysRoot() {
        assertEquals("/", UiUtils.getParentPath("/"));
    }

    @Test
    public void getParentPath_nullIsTreatedAsRoot() {
        assertEquals("/", UiUtils.getParentPath(null));
    }

    @Test
    public void getParentPath_topLevelFileReturnsSlash() {
        assertEquals("/", UiUtils.getParentPath("/a.txt"));
    }

    @Test
    public void getParentPath_nestedPathDropsLastSegment() {
        assertEquals("/a/b", UiUtils.getParentPath("/a/b/c.txt"));
    }

    @Test
    public void getParentPath_trailingSlashIsHarmless() {
        assertEquals("/a", UiUtils.getParentPath("/a/b/"));
    }

    @Test
    public void errorMessage_resultIsNeverNull() {
        assertNotNull(UiUtils.errorMessage(new RuntimeException()));
    }
}
