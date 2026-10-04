package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.SearchResult;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** {@link RecursiveSearchHelper} 纯函数层单测（JVM，无 Android 依赖）。 */
public class RecursiveSearchHelperTest {

    // ── resolveMaxDepth：0=无限（markor 同型）、负数防御 ─────────────────────────

    @Test
    public void resolveMaxDepth_positivePassthrough() {
        assertEquals(3, RecursiveSearchHelper.resolveMaxDepth(3));
        assertEquals(10, RecursiveSearchHelper.resolveMaxDepth(10));
    }

    @Test
    public void resolveMaxDepth_zeroMeansUnlimited() {
        assertEquals(Integer.MAX_VALUE, RecursiveSearchHelper.resolveMaxDepth(0));
    }

    @Test
    public void resolveMaxDepth_negativeFallsBackToDefault() {
        assertEquals(RecursiveSearchHelper.DEFAULT_MAX_DEPTH,
                RecursiveSearchHelper.resolveMaxDepth(-1));
        assertEquals(RecursiveSearchHelper.DEFAULT_MAX_DEPTH,
                RecursiveSearchHelper.resolveMaxDepth(-999));
    }

    // ── shouldDescend：markor 精确同型（depth < max 才列内容） ───────────────────

    @Test
    public void shouldDescend_belowLimitTrue_atLimitFalse() {
        assertTrue(RecursiveSearchHelper.shouldDescend(0, 3));
        assertTrue(RecursiveSearchHelper.shouldDescend(1, 3));
        assertTrue(RecursiveSearchHelper.shouldDescend(2, 3));
        assertFalse(RecursiveSearchHelper.shouldDescend(3, 3));
        assertFalse(RecursiveSearchHelper.shouldDescend(4, 3));
    }

    @Test
    public void shouldDescend_maxOneOnlyScansRootEntries() {
        // maxDepth=1 = 仅搜索根目录本身的条目，不进入任何子目录
        assertTrue(RecursiveSearchHelper.shouldDescend(0, 1));
        assertFalse(RecursiveSearchHelper.shouldDescend(1, 1));
    }

    @Test
    public void shouldDescend_unlimitedAlwaysTrue() {
        assertTrue(RecursiveSearchHelper.shouldDescend(0, Integer.MAX_VALUE));
        assertTrue(RecursiveSearchHelper.shouldDescend(500, Integer.MAX_VALUE));
    }

    // ── isIgnoredDir：忽略集语义 ────────────────────────────────────────────────

    @Test
    public void isIgnoredDir_knownNoiseDirsTrue() {
        assertTrue(RecursiveSearchHelper.isIgnoredDir(".git"));
        assertTrue(RecursiveSearchHelper.isIgnoredDir(".svn"));
        assertTrue(RecursiveSearchHelper.isIgnoredDir(".hg"));
        assertTrue(RecursiveSearchHelper.isIgnoredDir("node_modules"));
        assertTrue(RecursiveSearchHelper.isIgnoredDir("__pycache__"));
        assertTrue(RecursiveSearchHelper.isIgnoredDir(".tmp"));
    }

    @Test
    public void isIgnoredDir_regularDirsFalse() {
        assertFalse(RecursiveSearchHelper.isIgnoredDir("src"));
        assertFalse(RecursiveSearchHelper.isIgnoredDir("docs"));
        assertFalse(RecursiveSearchHelper.isIgnoredDir("build"));
    }

    @Test
    public void isIgnoredDir_nullEmptyAndLookalikeFalse() {
        assertFalse(RecursiveSearchHelper.isIgnoredDir(null));
        assertFalse(RecursiveSearchHelper.isIgnoredDir(""));
        // 前缀/文件同形不误伤：.gitignore 是文件且非目录
        assertFalse(RecursiveSearchHelper.isIgnoredDir(".gitignore"));
        assertFalse(RecursiveSearchHelper.isIgnoredDir("node_modules_backup"));
        // 大小写敏感（.GIT 不忽略——远端大小写敏感文件系统语义）
        assertFalse(RecursiveSearchHelper.isIgnoredDir(".GIT"));
    }

    // ── relativePath：展示路径计算 ──────────────────────────────────────────────

    @Test
    public void relativePath_rootSlashStripsLeadingSlash() {
        assertEquals("etc", RecursiveSearchHelper.relativePath("/", "/etc"));
        assertEquals("a/b/c.txt", RecursiveSearchHelper.relativePath("/", "/a/b/c.txt"));
    }

    @Test
    public void relativePath_normalRootStripsPrefix() {
        assertEquals("c.txt", RecursiveSearchHelper.relativePath("/home/u", "/home/u/c.txt"));
        assertEquals("sub/notes.md",
                RecursiveSearchHelper.relativePath("/home/u", "/home/u/sub/notes.md"));
    }

    @Test
    public void relativePath_rootWithTrailingSlashNoDoubleStrip() {
        assertEquals("a.txt", RecursiveSearchHelper.relativePath("/home/u/", "/home/u/a.txt"));
    }

    @Test
    public void relativePath_fullEqualsRootGivesEmpty() {
        assertEquals("", RecursiveSearchHelper.relativePath("/home/u", "/home/u"));
    }

    @Test
    public void relativePath_notUnderRootGivesNull() {
        assertNull(RecursiveSearchHelper.relativePath("/home/u", "/home/u2/x.txt"));
        assertNull(RecursiveSearchHelper.relativePath("/a", "/b/c"));
    }

    @Test
    public void relativePath_nullInputsGivesNull() {
        assertNull(RecursiveSearchHelper.relativePath(null, "/a"));
        assertNull(RecursiveSearchHelper.relativePath("/a", null));
    }

    // ── sortResults：目录恒前 + relPath 大小写不敏感升序 ────────────────────────

    private static SearchResult file(String rel) {
        return new SearchResult("f", "/r/" + rel, rel, false, 100);
    }

    private static SearchResult dir(String rel) {
        return new SearchResult("d", "/r/" + rel, rel, true, 0);
    }

    @Test
    public void sortResults_directoriesBeforeFiles() {
        List<SearchResult> in = Arrays.asList(file("z.txt"), dir("a_dir"), file("a.txt"));
        List<SearchResult> out = RecursiveSearchHelper.sortResults(in);
        assertEquals(3, out.size());
        assertTrue(out.get(0).isDirectory());
        assertFalse(out.get(1).isDirectory());
        assertFalse(out.get(2).isDirectory());
    }

    @Test
    public void sortResults_caseInsensitiveRelPath() {
        List<SearchResult> in = Arrays.asList(file("z.md"), file("README.md"), file("a.md"));
        List<SearchResult> out = RecursiveSearchHelper.sortResults(in);
        assertEquals("a.md", out.get(0).getRelativePath());
        assertEquals("README.md", out.get(1).getRelativePath());
        assertEquals("z.md", out.get(2).getRelativePath());
    }

    @Test
    public void sortResults_deepPathOrdersByFullRelPath() {
        List<SearchResult> in = Arrays.asList(
                file("sub/a.md"), file("x.md"), file("subdir/b.md"), file("sub/c.md"));
        List<SearchResult> out = RecursiveSearchHelper.sortResults(in);
        assertEquals("sub/a.md", out.get(0).getRelativePath());
        assertEquals("sub/c.md", out.get(1).getRelativePath());
        assertEquals("subdir/b.md", out.get(2).getRelativePath());
        assertEquals("x.md", out.get(3).getRelativePath());
    }

    @Test
    public void sortResults_doesNotMutateInput() {
        List<SearchResult> in = new ArrayList<>(Arrays.asList(file("z"), file("a")));
        List<SearchResult> out = RecursiveSearchHelper.sortResults(in);
        assertNotSame(in, out);
        assertEquals("z", in.get(0).getRelativePath());
        assertEquals("a", in.get(1).getRelativePath());
    }

    @Test
    public void sortResults_emptyAndSingleAndNull() {
        assertTrue(RecursiveSearchHelper.sortResults(new ArrayList<>()).isEmpty());
        assertTrue(RecursiveSearchHelper.sortResults(null).isEmpty());
        List<SearchResult> single = RecursiveSearchHelper.sortResults(
                Arrays.asList(file("only.md")));
        assertEquals(1, single.size());
    }

    @Test
    public void sortResults_nullRelPathSortedFirstDefensive() {
        SearchResult nullRel = new SearchResult("n", "/r/n", null, false, 0);
        List<SearchResult> out = RecursiveSearchHelper.sortResults(
                Arrays.asList(file("b.md"), nullRel));
        assertSame(nullRel, out.get(0));
    }

    // ── deliverResults（第二十四轮 #35 取消交付语义；markor onPostExecute 同型） ──

    @Test
    public void deliverResults_cancelledDiscardsPartialResults() {
        // 取消后不交付部分结果（markor !isCancelled() 才回调）：无论已匹配多少
        List<SearchResult> in = Arrays.asList(file("a.md"), file("b.md"));
        assertNull(RecursiveSearchHelper.deliverResults(true, in));
        assertNull(RecursiveSearchHelper.deliverResults(true, new ArrayList<>()));
    }

    @Test
    public void deliverResults_cancelledWithNullInputStillNull() {
        assertNull(RecursiveSearchHelper.deliverResults(true, null));
    }

    @Test
    public void deliverResults_notCancelledSortsAndReturns() {
        List<SearchResult> in = Arrays.asList(file("z.md"), dir("a_dir"), file("a.md"));
        List<SearchResult> out = RecursiveSearchHelper.deliverResults(false, in);
        assertTrue(out.get(0).isDirectory());
        assertEquals("a.md", out.get(1).getRelativePath());
    }

    @Test
    public void deliverResults_notCancelledEmptyAndNullDefensive() {
        assertTrue(RecursiveSearchHelper.deliverResults(false, new ArrayList<>()).isEmpty());
        assertTrue(RecursiveSearchHelper.deliverResults(false, null).isEmpty());
    }

    @Test
    public void deliverResults_notCancelledDoesNotMutateInput() {
        List<SearchResult> in = new ArrayList<>(Arrays.asList(file("z"), file("a")));
        List<SearchResult> out = RecursiveSearchHelper.deliverResults(false, in);
        assertNotSame(in, out);
        assertEquals("z", in.get(0).getRelativePath());
        // 成功路径=原 sortResults 语义：目录恒前+大小写不敏感升序（单点不漂移）
        List<SearchResult> mixed = Arrays.asList(file("B.md"), file("a.md"), dir("m"));
        List<SearchResult> sorted = RecursiveSearchHelper.deliverResults(false, mixed);
        assertTrue(sorted.get(0).isDirectory());
        assertEquals("a.md", sorted.get(1).getRelativePath());
        assertEquals("B.md", sorted.get(2).getRelativePath());
    }

    @Test
    public void deliverResults_singleElementPassthrough() {
        List<SearchResult> single = RecursiveSearchHelper.deliverResults(
                false, Arrays.asList(file("only.md")));
        assertEquals(1, single.size());
        assertEquals("only.md", single.get(0).getRelativePath());
    }
}
