package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** {@link GoToPathHelper} 纯函数层单测（JVM，无 Android 依赖）。 */
public class GoToPathHelperTest {

    // ── resolve：空输入 → null ────────────────────────────────────────────────

    @Test
    public void resolve_nullInputReturnsNull() {
        assertNull(GoToPathHelper.resolve("/a/b", null));
        assertNull(GoToPathHelper.resolve(null, null));
    }

    @Test
    public void resolve_blankInputReturnsNull() {
        assertNull(GoToPathHelper.resolve("/a/b", ""));
        assertNull(GoToPathHelper.resolve("/a/b", "   "));
    }

    // ── resolve：绝对路径原样规范化 ───────────────────────────────────────────

    @Test
    public void resolve_absoluteSimple() {
        assertEquals("/var/log", GoToPathHelper.resolve("/a/b", "/var/log"));
        assertEquals("/", GoToPathHelper.resolve("/a", "/"));
    }

    @Test
    public void resolve_absoluteTrailingSlashStripped() {
        assertEquals("/var/log", GoToPathHelper.resolve("/a", "/var/log/"));
    }

    @Test
    public void resolve_absoluteDuplicateSlashesCollapsed() {
        assertEquals("/var/log", GoToPathHelper.resolve("/a", "//var//log/"));
    }

    @Test
    public void resolve_absoluteBackslashesConverted() {
        assertEquals("/var/log", GoToPathHelper.resolve("/a", "\\var\\log"));
    }

    @Test
    public void resolve_absoluteDotSegmentSkipped() {
        assertEquals("/var/log", GoToPathHelper.resolve("/a", "/var/./log"));
    }

    @Test
    public void resolve_absoluteDotDotResolved() {
        assertEquals("/var/nginx", GoToPathHelper.resolve("/a", "/var/log/../nginx"));
    }

    @Test
    public void resolve_absoluteDotDotOverRootStaysRoot() {
        assertEquals("/x", GoToPathHelper.resolve("/a", "/../x"));
        assertEquals("/", GoToPathHelper.resolve("/a", "/.."));
    }

    @Test
    public void resolve_api_ignorePathWithSpacesTrimmed() {
        // 仅去首尾空白，路径内含空格不切断
        assertEquals("/var/log", GoToPathHelper.resolve("/a", "  /var/log  "));
    }

    // ── resolve：相对路径按当前目录拼接 ───────────────────────────────────────

    @Test
    public void resolve_relativeSimple() {
        assertEquals("/a/b/logs", GoToPathHelper.resolve("/a/b", "logs"));
    }

    @Test
    public void resolve_relativeWithDotSlash() {
        assertEquals("/a/b/logs", GoToPathHelper.resolve("/a/b", "./logs"));
    }

    @Test
    public void resolve_relativeDotDotClimbs() {
        assertEquals("/a", GoToPathHelper.resolve("/a/b", ".."));
        assertEquals("/a/x", GoToPathHelper.resolve("/a/b/c", "../../x"));
    }

    @Test
    public void resolve_relativeDotDotAtRootStaysRoot() {
        assertEquals("/", GoToPathHelper.resolve("/", ".."));
    }

    @Test
    public void resolve_relativeDotKeepsCurrent() {
        assertEquals("/a/b", GoToPathHelper.resolve("/a/b", "."));
    }

    @Test
    public void resolve_relativeTrailingSlashStripped() {
        assertEquals("/a/b/x", GoToPathHelper.resolve("/a/b", "x/"));
    }

    @Test
    public void resolve_relativeMixedDotSeq() {
        assertEquals("/a/b/d", GoToPathHelper.resolve("/a/b", "./c/../d"));
    }

    // ── resolve：当前目录防御 ────────────────────────────────────────────────

    @Test
    public void resolve_currentPathNullTreatedAsRoot() {
        assertEquals("/x", GoToPathHelper.resolve(null, "x"));
    }

    @Test
    public void resolve_currentPathRoot() {
        assertEquals("/x", GoToPathHelper.resolve("/", "x"));
        assertEquals("/x", GoToPathHelper.resolve("/", "/x"));
        assertNull(GoToPathHelper.resolve("/", ""));
    }

    @Test
    public void resolve_currentPathTrailingSlashDefensive() {
        assertEquals("/a/b/x", GoToPathHelper.resolve("/a/b/", "x"));
    }

    // ── fileName：末级名称 ────────────────────────────────────────────────────

    @Test
    public void fileName_basic() {
        assertEquals("c.txt", GoToPathHelper.fileName("/a/b/c.txt"));
        assertEquals("a", GoToPathHelper.fileName("/a"));
        assertEquals("a", GoToPathHelper.fileName("/a/"));
        assertEquals("a", GoToPathHelper.fileName("a"));
    }

    @Test
    public void fileName_rootOrEmpty() {
        assertEquals("", GoToPathHelper.fileName("/"));
        assertEquals("", GoToPathHelper.fileName(null));
        assertEquals("", GoToPathHelper.fileName(""));
    }
}
