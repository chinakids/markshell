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

    // ── 多选模式（实例级：RecyclerView.Adapter 不注册观察者时 notify 为空操作，无 framework 依赖） ──

    @Test
    public void enterSelectionMode_selectsSingleAndActivates() {
        TreeAdapter adapter = noNotifyAdapter();
        RemoteFile f1 = file("a.md", "/data/a.md");
        RemoteFile f2 = file("b.md", "/data/b.md");
        List<RemoteFile> files = new ArrayList<>();
        files.add(f1);
        files.add(f2);
        adapter.setFiles(files);

        adapter.enterSelectionMode(f1);

        assertTrue(adapter.isSelectionMode());
        assertEquals(1, adapter.getSelectedCount());
        assertEquals("/data/a.md", adapter.getSelectedPaths().get(0));
        assertEquals(1, adapter.getSelectedFiles().size());
    }

    @Test
    public void toggleSelection_addsThenRemovesAndAutoExits() {
        TreeAdapter adapter = noNotifyAdapter();
        RemoteFile f1 = file("a.md", "/data/a.md");
        RemoteFile f2 = file("b.md", "/data/b.md");
        List<RemoteFile> files = new ArrayList<>();
        files.add(f1);
        files.add(f2);
        adapter.setFiles(files);
        adapter.enterSelectionMode(f1);

        adapter.toggleSelection(f2);
        assertEquals(2, adapter.getSelectedCount());

        adapter.toggleSelection(f2);
        assertEquals(1, adapter.getSelectedCount());
        assertTrue(adapter.isSelectionMode());

        // 取消最后一个选中项 → 自动退出多选模式
        adapter.toggleSelection(f1);
        assertFalse(adapter.isSelectionMode());
        assertEquals(0, adapter.getSelectedCount());
    }

    @Test
    public void selectAllVisible_coversExpandedTree() {
        TreeAdapter adapter = noNotifyAdapter();
        RemoteFile sub = dir("sub", "/data/a/sub", true, true);
        sub.getChildren().add(file("x.txt", "/data/a/sub/x.txt"));
        RemoteFile a = dir("a", "/data/a", true, true);
        a.getChildren().add(sub);
        List<RemoteFile> files = new ArrayList<>();
        files.add(a);
        files.add(file("top.md", "/data/top.md"));
        adapter.setFiles(files);

        // setFiles 会按新数据重置未匹配目录（清空 children）；模拟「曾加载过的已展开子树」重挂载
        a.getChildren().add(sub);
        a.setExpanded(true);

        adapter.enterSelectionMode(a);
        adapter.selectAllVisible();

        // 可见列表 = a、sub、x.txt、top.md（共 4）
        assertEquals(4, adapter.getSelectedCount());
        assertTrue(adapter.getSelectedPaths().contains("/data/a/sub/x.txt"));
    }

    @Test
    public void exitSelectionMode_clearsAll() {
        TreeAdapter adapter = noNotifyAdapter();
        RemoteFile f1 = file("a.md", "/data/a.md");
        List<RemoteFile> files = new ArrayList<>();
        files.add(f1);
        adapter.setFiles(files);
        adapter.enterSelectionMode(f1);

        adapter.exitSelectionMode();

        assertFalse(adapter.isSelectionMode());
        assertEquals(0, adapter.getSelectedCount());
    }

    // ── 名称过滤（第三轮能力发现） ────────────────────────────────────────────

    @Test
    public void setFilterQuery_filtersByNameCaseInsensitive() {
        TreeAdapter adapter = noNotifyAdapter();
        List<RemoteFile> files = new ArrayList<>();
        files.add(file("README.md", "/data/README.md"));
        files.add(file("notes.txt", "/data/notes.txt"));
        files.add(dir("assets", "/data/assets", false, false));
        adapter.setFiles(files);

        adapter.setFilterQuery("md");

        assertEquals(1, adapter.getItemCount());
        assertTrue(adapter.isFilterActive());
        assertEquals("README.md", adapter.getFile(0).getName());
    }

    @Test
    public void setFilterQuery_emptyClearsFilter() {
        TreeAdapter adapter = noNotifyAdapter();
        List<RemoteFile> files = new ArrayList<>();
        files.add(file("README.md", "/data/README.md"));
        files.add(file("notes.txt", "/data/notes.txt"));
        adapter.setFiles(files);
        adapter.setFilterQuery("md");
        assertEquals(1, adapter.getItemCount());

        adapter.setFilterQuery("");

        assertFalse(adapter.isFilterActive());
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void setFilterQuery_nullClearsFilter() {
        TreeAdapter adapter = noNotifyAdapter();
        List<RemoteFile> files = new ArrayList<>();
        files.add(file("README.md", "/data/README.md"));
        adapter.setFiles(files);
        adapter.setFilterQuery("readme");
        adapter.setFilterQuery(null);

        assertFalse(adapter.isFilterActive());
        assertEquals(1, adapter.getItemCount());
    }

    @Test
    public void setFilterQuery_activeExitsSelectionMode() {
        TreeAdapter adapter = noNotifyAdapter();
        RemoteFile f1 = file("a.md", "/data/a.md");
        List<RemoteFile> files = new ArrayList<>();
        files.add(f1);
        files.add(file("b.md", "/data/b.md"));
        adapter.setFiles(files);
        adapter.enterSelectionMode(f1);
        assertTrue(adapter.isSelectionMode());
        assertEquals(1, adapter.getSelectedCount());

        adapter.setFilterQuery("b");

        assertFalse(adapter.isSelectionMode());
        assertEquals(0, adapter.getSelectedCount());
        // 过滤后的可见项
        assertEquals(1, adapter.getItemCount());
        assertEquals("b.md", adapter.getFile(0).getName());
    }

    @Test
    public void setFilterQuery_noHitSelectionStillExits() {
        TreeAdapter adapter = noNotifyAdapter();
        RemoteFile f1 = file("a.md", "/data/a.md");
        adapter.setFiles(files(f1));
        adapter.enterSelectionMode(f1);

        adapter.setFilterQuery("zzz");

        assertFalse(adapter.isSelectionMode());
        assertEquals(0, adapter.getSelectedCount());
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void setFilterQuery_dirShownByOwnNameChildrenFiltered() {
        TreeAdapter adapter = noNotifyAdapter();
        RemoteFile sub = dir("docs", "/data/docs", true, true);
        sub.getChildren().add(file("y.md", "/data/docs/y.md"));
        sub.getChildren().add(file("z.txt", "/data/docs/z.txt"));
        List<RemoteFile> root = new ArrayList<>();
        root.add(sub);
        adapter.setFiles(root);
        // setFiles 会按新数据重置未匹配目录；模拟已加载子树重挂载
        sub.getChildren().clear();
        sub.getChildren().add(file("y.md", "/data/docs/y.md"));
        sub.getChildren().add(file("z.txt", "/data/docs/z.txt"));
        sub.setExpanded(true);

        adapter.setFilterQuery("docs");

        // 目录按自身名字匹配 → 保留；其子项按各自名字过滤 → 只留 y.md
        assertEquals(1, adapter.getItemCount());
        assertEquals("docs", adapter.getFile(0).getName());
        assertTrue(adapter.getSelectedPaths().isEmpty());
    }

    @Test
    public void setFilterQuery_dirNameNoMatchHidesSubtree() {
        TreeAdapter adapter = noNotifyAdapter();
        RemoteFile sub = dir("docs", "/data/docs", true, true);
        sub.getChildren().add(file("y.md", "/data/docs/y.md"));
        List<RemoteFile> root = new ArrayList<>();
        root.add(sub);
        adapter.setFiles(root);
        sub.getChildren().clear();
        sub.getChildren().add(file("y.md", "/data/docs/y.md"));
        sub.setExpanded(true);

        adapter.setFilterQuery("y");

        // 子文件命中但父目录名不命中 → 子树整体不可见（文档化语义：无 SFTP 遍历）
        assertEquals(0, adapter.getItemCount());
    }

    private static List<RemoteFile> files(RemoteFile... items) {
        List<RemoteFile> list = new ArrayList<>();
        for (RemoteFile r : items) {
            list.add(r);
        }
        return list;
    }

    /** 覆写 notifyDataChanged 为 no-op 的实例（见 TreeAdapter.notifyDataChanged 注释：
     *  mockable android.jar 下 RecyclerView.Adapter 空观察者字段为 null，直接通知会 NPE）。 */
    private TreeAdapter noNotifyAdapter() {
        return new TreeAdapter() {
            @Override
            void notifyDataChanged() {
                // no-op：校验多选状态机，不关心视图刷新
            }
        };
    }
}
