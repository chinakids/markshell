package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** ConnectionCopyHelper 纯函数单测：复制连接的新别名派生语义。 */
public class ConnectionCopyHelperTest {

    @Test
    public void nullAlias_returnsEmpty() {
        assertEquals("", ConnectionCopyHelper.duplicateName(null));
    }

    @Test
    public void emptyAlias_returnsEmpty() {
        assertEquals("", ConnectionCopyHelper.duplicateName(""));
    }

    @Test
    public void blankAlias_returnsEmpty() {
        assertEquals("", ConnectionCopyHelper.duplicateName("   "));
    }

    @Test
    public void chineseAlias_appendsSuffix() {
        assertEquals("生产服务器（副本）", ConnectionCopyHelper.duplicateName("生产服务器"));
    }

    @Test
    public void asciiAlias_appendsSuffix() {
        assertEquals("web-01（副本）", ConnectionCopyHelper.duplicateName("web-01"));
    }

    @Test
    public void aliasWithSurroundingWhitespace_isTrimmed() {
        assertEquals("server（副本）", ConnectionCopyHelper.duplicateName("  server  "));
    }

    @Test
    public void aliasWithInnerSpaces_preserved() {
        assertEquals("a b（副本）", ConnectionCopyHelper.duplicateName("a b"));
    }

    @Test
    public void alreadyDuplicatedAlias_appendsAgain() {
        // 重复复制（副本）继续追加：与 ConnectBot "x (copy) (copy)" 行为同型，用户可改。
        assertEquals("x（副本）（副本）", ConnectionCopyHelper.duplicateName("x（副本）"));
    }
}
