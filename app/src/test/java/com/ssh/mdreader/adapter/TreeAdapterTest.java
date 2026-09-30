package com.ssh.mdreader.adapter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.RemoteFile;

import org.junit.Test;

/**
 * TreeAdapter.copyNode 纯逻辑单测：目录改名后子树路径递归改写、展开态保留。
 * 不实例化 TreeAdapter（避免 RecyclerView 依赖），只测包级静态方法。
 */
public class TreeAdapterTest {

    private RemoteFile dir(String name, String path, boolean expanded, boolean loaded) {
        RemoteFile d = new RemoteFile(name, path, true, 0, 493);
        d.setExpanded(expanded);
        d.setChildrenLoaded(loaded);
        return d;
    }

    private RemoteFile file(String name, String path) {
        return new RemoteFile(name, path, false, 123, 420);
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
}
