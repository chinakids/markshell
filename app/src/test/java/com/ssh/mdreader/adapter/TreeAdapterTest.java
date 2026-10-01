package com.ssh.mdreader.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.util.FileSortUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TreeAdapter 纯逻辑单测：copyNode / removeFromList / collectExpandedDirs /
 * applyExistingState 树内操作。不实例化 TreeAdapter（避免 RecyclerView 依赖），
 * 只测包级静态方法。
 */
public class TreeAdapterTest {

    private RemoteFile dir(String name, String path, boolean expanded, boolean loaded) {
        RemoteFile d = new RemoteFile(name, path, true, 0, 493, 100);
        d.setExpanded(expanded);
        d.setChildrenLoaded(loaded);
        return d;
    }

    private RemoteFile file(String name, String path) {
        return new RemoteFile(name, path, false, 123, 420, 200);
    }

    @Test
    public void copyNode_rewritesDescendantPathsRecursively() {
        RemoteFile sub = dir("sub", "/a/b/c/sub", true, true);
        sub.getChildren().add(file("y.md", "/a/b/c/sub/y.md"));
        RemoteFile c = dir("c", "/a/b/c", true, true);
        c.getChildren().add(file("x.txt", "/a/b/c/x.txt"));
        c.getChildren().add(sub);

        RemoteFile renamed = TreeAdapter.copyNode(c, "c2", "/a/b/c2");

        assertEquals("c2", renamed.getName());
        assertEquals("/a/b/c2", renamed.getPath());
        assertEquals(2, renamed.getChildren().size());
        // 顶层子文件路径改写
        RemoteFile x = renamed.getChildren().get(0);
        assertEquals("/a/b/c2/x.txt", x.getPath());
        assertEquals("x.txt", x.getName());
        // 嵌套目录及更深层路径改写
        RemoteFile sub2 = renamed.getChildren().get(1);
        assertEquals("/a/b/c2/sub", sub2.getPath());
        assertEquals("/a/b/c2/sub/y.md", sub2.getChildren().get(0).getPath());
    }

    @Test
    public void copyNode_preservesStateFlags() {
        RemoteFile c = dir("c", "/a/b/c", true, true);
        c.setDepth(1);
        RemoteFile childX = file("x.txt", "/a/b/c/x.txt");
        childX.setDepth(2); // 真实流程 setChildren 会设 parent.depth+1
        c.getChildren().add(childX);

        RemoteFile renamed = TreeAdapter.copyNode(c, "c2", "/a/b/c2");

        assertTrue(renamed.isExpanded());
        assertTrue(renamed.isChildrenLoaded());
        assertEquals(1, renamed.getDepth());
        RemoteFile child = renamed.getChildren().get(0);
        assertFalse(child.isDirectory());
        assertEquals(123, child.getSize());
        assertEquals(420, child.getPermissions());
        assertEquals(2, child.getDepth());
    }

    @Test
    public void copyNode_leafFileKeepsSimpleShape() {
        RemoteFile f = file("a.txt", "/tmp/a.txt");

        RemoteFile renamed = TreeAdapter.copyNode(f, "b.txt", "/tmp/b.txt");

        assertEquals("/tmp/b.txt", renamed.getPath());
        assertTrue(renamed.getChildren().isEmpty());
        assertFalse(renamed.isDirectory());
    }

    @Test
    public void removeFromList_removesRootNodeByPath() {
        List<RemoteFile> nodes = new ArrayList<>();
        nodes.add(dir("a", "/data/a", false, false));
        nodes.add(file("b.md", "/data/b.md"));

        boolean removed = TreeAdapter.removeFromList(nodes, "/data/a");

        assertTrue(removed);
        assertEquals(1, nodes.size());
        assertEquals("/data/b.md", nodes.get(0).getPath());
    }

    @Test
    public void removeFromList_descendsIntoLoadedSubtree() {
        RemoteFile sub = dir("sub", "/data/a/sub", true, true);
        sub.getChildren().add(file("f.txt", "/data/a/sub/f.txt"));
        RemoteFile a = dir("a", "/data/a", true, true);
        a.getChildren().add(sub);
        List<RemoteFile> nodes = new ArrayList<>();
        nodes.add(a);

        boolean removed = TreeAdapter.removeFromList(nodes, "/data/a/sub/f.txt");

        assertTrue(removed);
        assertEquals(0, a.getChildren().get(0).getChildren().size());
    }

    @Test
    public void removeFromList_notFoundKeepsListUntouched() {
        List<RemoteFile> nodes = new ArrayList<>();
        nodes.add(dir("a", "/data/a", false, true));
        nodes.get(0).getChildren().add(file("x.txt", "/data/a/x.txt"));

        boolean removed = TreeAdapter.removeFromList(nodes, "/data/nope");

        assertFalse(removed);
        assertEquals(1, nodes.size());
        assertEquals(1, nodes.get(0).getChildren().size());
    }

    @Test
    public void collectExpandedDirs_collectsOnlyExpandedAndRecurses() {
        RemoteFile inner = dir("inner", "/a/b/inner", true, true);
        RemoteFile expanded = dir("b", "/a/b", true, true);
        expanded.getChildren().add(inner);
        RemoteFile other = dir("c", "/a/c", false, true); // 未展开，不收集
        List<RemoteFile> nodes = new ArrayList<>();
        nodes.add(expanded);
        nodes.add(other);

        Map<String, RemoteFile> map = new HashMap<>();
        TreeAdapter.collectExpandedDirs(nodes, map);

        assertTrue(map.containsKey("/a/b"));
        assertTrue(map.containsKey("/a/b/inner"));
        assertFalse(map.containsKey("/a/c"));
        assertEquals(inner, map.get("/a/b/inner"));
    }

    @Test
    public void applyExistingState_preservesExpandedDirWithOldChildren() {
        RemoteFile oldChild = file("old.txt", "/data/a/old.txt");
        RemoteFile oldA = dir("a", "/data/a", true, true);
        oldA.getChildren().add(oldChild);
        Map<String, RemoteFile> oldMap = new HashMap<>();
        oldMap.put("/data/a", oldA);

        RemoteFile newA = dir("a", "/data/a", false, false);
        List<RemoteFile> files = new ArrayList<>();
        files.add(newA);
        TreeAdapter.applyExistingState(files, oldMap);

        assertTrue(newA.isExpanded());
        assertTrue(newA.isChildrenLoaded());
        assertEquals(1, newA.getChildren().size());
        assertEquals("/data/a/old.txt", newA.getChildren().get(0).getPath());
        assertEquals(1, newA.getChildren().get(0).getDepth());
        assertEquals(0, newA.getDepth());
        assertFalse(newA.getChildren().get(0).isDirectory());
    }

    @Test
    public void applyExistingState_resetsUnmatchedDirectories() {
        Map<String, RemoteFile> oldMap = new HashMap<>();
        RemoteFile fresh = dir("new", "/data/new", false, false);
        fresh.getChildren().add(file("stale.txt", "/data/new/stale.txt"));

        List<RemoteFile> files = new ArrayList<>();
        files.add(fresh);
        TreeAdapter.applyExistingState(files, oldMap);

        assertFalse(fresh.isExpanded());
        assertFalse(fresh.isChildrenLoaded());
        assertTrue(fresh.getChildren().isEmpty());
    }

    @Test
    public void applyExistingState_matchesByPathAcrossInstances() {
        RemoteFile oldA = dir("a", "/data/a", true, true);
        oldA.getChildren().add(file("old.txt", "/data/a/old.txt"));
        Map<String, RemoteFile> oldMap = new HashMap<>();
        oldMap.put("/data/a", oldA);

        RemoteFile newA = dir("a", "/data/a", true, true); // 不同实例，同路径
        List<RemoteFile> files = new ArrayList<>();
        files.add(newA);
        TreeAdapter.applyExistingState(files, oldMap);

        assertEquals(1, newA.getChildren().size());
        assertEquals("old.txt", newA.getChildren().get(0).getName());
    }

    private RemoteFile fileM(String name, String path, long mtime) {
        return new RemoteFile(name, path, false, 123, 420, mtime);
    }

    @Test
    public void sortLoadedChildren_resortsByMtimeModeDirsFirst() {
        RemoteFile a = dir("a", "/data/a", true, true);
        a.getChildren().add(fileM("z.txt", "/data/a/z.txt", 100));
        a.getChildren().add(fileM("a.txt", "/data/a/a.txt", 500));
        a.getChildren().add(dir("sub", "/data/a/sub", false, false)); // 目录恒在前
        List<RemoteFile> nodes = new ArrayList<>();
        nodes.add(a);

        TreeAdapter.sortLoadedChildren(nodes, FileSortUtils.SORT_MTIME);

        List<RemoteFile> kids = a.getChildren();
        assertEquals("sub", kids.get(0).getName());
        assertEquals("a.txt", kids.get(1).getName());
        assertEquals("z.txt", kids.get(2).getName());
    }

    @Test
    public void sortLoadedChildren_recursesIntoNestedLoadedDirs() {
        RemoteFile sub = dir("sub", "/data/a/sub", true, true);
        sub.getChildren().add(fileM("x.txt", "/data/a/sub/x.txt", 1000));
        sub.getChildren().add(fileM("y.txt", "/data/a/sub/y.txt", 50));
        RemoteFile a = dir("a", "/data/a", true, true);
        a.getChildren().add(sub);
        List<RemoteFile> nodes = new ArrayList<>();
        nodes.add(a);

        TreeAdapter.sortLoadedChildren(nodes, FileSortUtils.SORT_MTIME);

        assertEquals("x.txt", sub.getChildren().get(0).getName());
        assertEquals("y.txt", sub.getChildren().get(1).getName());
    }

    @Test
    public void sortLoadedChildren_leavesUnloadedDirsAndRootOrderAlone() {
        RemoteFile unloaded = dir("u", "/data/u", false, false);
        unloaded.getChildren().add(fileM("old.txt", "/data/u/old.txt", 999));
        RemoteFile fresh = dir("fresh", "/data/fresh", false, false);
        List<RemoteFile> nodes = new ArrayList<>();
        nodes.add(fresh);
        nodes.add(unloaded);

        TreeAdapter.sortLoadedChildren(nodes, FileSortUtils.SORT_SIZE);

        // 根节点列表由调用方排序，函数只重排"已加载"子树，不动根序
        assertEquals("fresh", nodes.get(0).getName());
        assertEquals("u", nodes.get(1).getName());
        // 未加载目录的旧子序保留（其 children 本应在加载时排序）
        assertEquals(1, unloaded.getChildren().size());
    }
}
