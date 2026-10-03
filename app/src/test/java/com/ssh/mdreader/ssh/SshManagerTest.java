package com.ssh.mdreader.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.LsTestFactory;
import com.jcraft.jsch.SftpException;
import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.model.SshConfig;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 断链判定逻辑单测：只读操作失败重试前先区分「链路已断」与
 * 「文件不存在/权限不足」等业务错误，避免把业务错误误判为断线。
 */
public class SshManagerTest {

    @Test
    public void ioException_isConnectionGone() {
        assertTrue(SshManager.isConnectionGone(new IOException("Premature EOF")));
        assertTrue(SshManager.isConnectionGone(new SocketException("Connection reset")));
    }

    @Test
    public void sftpChannelClosed_isConnectionGone() throws Exception {
        assertTrue(SshManager.isConnectionGone(new SftpException(4, "Channel is closed")));
        assertTrue(SshManager.isConnectionGone(new SftpException(6, "Connection is closed by foreign host")));
        assertTrue(SshManager.isConnectionGone(new SftpException(4, "connection lost")));
        assertTrue(SshManager.isConnectionGone(new SftpException(4, null)));
    }

    @Test
    public void businessError_isNotConnectionGone() throws Exception {
        assertFalse(SshManager.isConnectionGone(new SftpException(2, "No such file")));
        assertFalse(SshManager.isConnectionGone(new SftpException(3, "Permission denied")));
        assertFalse(SshManager.isConnectionGone(new RuntimeException("unexpected")));
    }

    @Test
    public void buildRenamePath_joinsSameDirectory() {
        assertEquals("/a/b/d.txt", SshManager.buildRenamePath("/a/b/c.txt", "d.txt"));
        assertEquals("/tmp/g.log", SshManager.buildRenamePath("/tmp/f.log", "g.log"));
        // 根目录下文件：保留前导斜杠
        assertEquals("/x", SshManager.buildRenamePath("/file", "x"));
    }

    @Test
    public void buildRenamePath_preservesNewNameAsIs() {
        // 新名自身含路径（移动场景由 UI 拦截，底层按完整新名处理）
        assertEquals("/a/b/sub/d.txt", SshManager.buildRenamePath("/a/b/c.txt", "sub/d.txt"));
        // 无斜杠的异常路径：直接返回新名
        assertEquals("d.txt", SshManager.buildRenamePath("c.txt", "d.txt"));
    }

    @Test
    public void buildChildPath_joinsParentAndName() {
        assertEquals("/a/b/name.txt", SshManager.buildChildPath("/a/b", "name.txt"));
        assertEquals("/a/b/name.txt", SshManager.buildChildPath("/a/b/", "name.txt"));
        // 根目录：不产生双斜杠
        assertEquals("/name.txt", SshManager.buildChildPath("/", "name.txt"));
        assertEquals("sub/x", SshManager.buildChildPath("sub", "x"));
    }

    @Test
    public void sanitizeHeartbeat_rejectsOutOfRange() {
        assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, SshManager.sanitizeHeartbeat(0));
        assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, SshManager.sanitizeHeartbeat(999));
        assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, SshManager.sanitizeHeartbeat(-100));
        assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, SshManager.sanitizeHeartbeat(60_001));
    }

    @Test
    public void sanitizeHeartbeat_keepsValidValues() {
        assertEquals(1_000, SshManager.sanitizeHeartbeat(1_000));
        assertEquals(10_000, SshManager.sanitizeHeartbeat(10_000));
        assertEquals(60_000, SshManager.sanitizeHeartbeat(60_000));
    }

    @Test
    public void parseOctalMode_validModes() {
        assertEquals(0x1ED, SshManager.parseOctalMode("755"));   // 0755
        assertEquals(0x1A4, SshManager.parseOctalMode("644"));   // 0644
        assertEquals(0x3FF, SshManager.parseOctalMode("1777"));  // 01777（setuid 场景）
        assertEquals(0x1A4, SshManager.parseOctalMode("0644"));  // 前导 0 合法
        assertEquals(0, SshManager.parseOctalMode("000"));
    }

    @Test
    public void parseOctalMode_rejectsInvalid() {
        assertEquals(-1, SshManager.parseOctalMode("888"));   // 非八进制位
        assertEquals(-1, SshManager.parseOctalMode("75"));    // 位数不足
        assertEquals(-1, SshManager.parseOctalMode("1"));
        assertEquals(-1, SshManager.parseOctalMode("0"));     // 唯一 0 位不足（须写 000）
        assertEquals(-1, SshManager.parseOctalMode(""));      // 空输入
        assertEquals(-1, SshManager.parseOctalMode(null));
        assertEquals(-1, SshManager.parseOctalMode("755 "));  // 多余空格
        assertEquals(-1, SshManager.parseOctalMode("07775")); // 位数超限
        assertEquals(-1, SshManager.parseOctalMode("abc"));
    }

    @Test
    public void setHeartbeatInterval_sanitizesAndStores() {
        SshManager mgr = SshManager.getInstance();
        int original = mgr.getHeartbeatIntervalMs();
        try {
            mgr.setHeartbeatIntervalMs(30_000);
            assertEquals(30_000, mgr.getHeartbeatIntervalMs());
            // 非法值（0）应回退默认，不污染运行态
            mgr.setHeartbeatIntervalMs(0);
            assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, mgr.getHeartbeatIntervalMs());
        } finally {
            mgr.setHeartbeatIntervalMs(original);
        }
    }

    @Test
    public void buildMovePath_joinsTargetDirAndName() {
        assertEquals("/a/b/note.md", SshManager.buildMovePath("/a/c/note.md", "/a/b"));
        assertEquals("/a/b/note.md", SshManager.buildMovePath("/a/c/note.md", "/a/b/"));
        // 根目录目标：只保留一个前导斜杠
        assertEquals("/note.md", SshManager.buildMovePath("/a/c/note.md", "/"));
        // 根目录下的源（无父目录前缀）
        assertEquals("/b/note.md", SshManager.buildMovePath("/note.md", "/b"));
    }

    @Test
    public void buildCopyPath_joinsTargetDirAndName() {
        // 与 buildMovePath 同语义（委托复用）：源名不变拼到目标目录
        assertEquals("/a/b/note.md", SshManager.buildCopyPath("/a/c/note.md", "/a/b"));
        assertEquals("/a/b/note.md", SshManager.buildCopyPath("/a/c/note.md", "/a/b/"));
        // 根目录目标：只保留一个前导斜杠
        assertEquals("/note.md", SshManager.buildCopyPath("/a/c/note.md", "/"));
        // 根目录下的源（无父目录前缀）
        assertEquals("/b/note.md", SshManager.buildCopyPath("/note.md", "/b"));
    }

    @Test
    public void isMoveSameLocation_targetEqualsSourceParent() {
        assertTrue(SshManager.isMoveSameLocation("/a/b/note.md", "/a/b"));
        assertTrue(SshManager.isMoveSameLocation("/a/b/note.md", "/a/b/")); // 结尾斜杠等价
        assertTrue(SshManager.isMoveSameLocation("/note.md", "/"));         // 根目录下文件
        assertFalse(SshManager.isMoveSameLocation("/a/b/note.md", "/a"));
        assertFalse(SshManager.isMoveSameLocation("/a/b/note.md", null));
    }

    @Test
    public void isMoveIntoItself_selfOrDescendant() {
        assertTrue(SshManager.isMoveIntoItself("/a/b", "/a/b"));
        assertTrue(SshManager.isMoveIntoItself("/a/b", "/a/b/data"));
        assertTrue(SshManager.isMoveIntoItself("/a/b", "/a/b/data/sub"));
        assertTrue(SshManager.isMoveIntoItself("/a/b", "/a/b/"));       // 结尾斜杠等价
        assertFalse(SshManager.isMoveIntoItself("/a/b", "/a"));         // 上级合法
        assertFalse(SshManager.isMoveIntoItself("/a/b", "/a/bc"));      // 前缀相似≠子孙
        assertFalse(SshManager.isMoveIntoItself("/a/b", null));
    }

    @Test
    public void isSpecialEntry_dotEntries() {
        assertTrue(SshManager.isSpecialEntry("."));
        assertTrue(SshManager.isSpecialEntry(".."));
    }

    @Test
    public void isSpecialEntry_normalNames() {
        assertFalse(SshManager.isSpecialEntry("a"));
        assertFalse(SshManager.isSpecialEntry(".hidden"));   // 点开头≠特殊条目
        assertFalse(SshManager.isSpecialEntry("..hidden"));
        assertFalse(SshManager.isSpecialEntry("a."));
        assertFalse(SshManager.isSpecialEntry(""));
        assertFalse(SshManager.isSpecialEntry(null));
    }

    @Test
    public void copyNodeRecursivePaths_keepSrcDstInParallel() {
        // 目录复制递归时源与目标按同名子项并行前进（copyNodeSync 的路径拼装）
        String src = "/a/b";
        String dst = "/x/b";
        String child = "sub/f.txt";
        assertEquals("/a/b/sub/f.txt", SshManager.buildChildPath(src, child));
        assertEquals("/x/b/sub/f.txt", SshManager.buildChildPath(dst, child));
        // 根目录目标：不产生双斜杠
        assertEquals("/b/f.txt", SshManager.buildChildPath("/b", "f.txt"));
    }

    @Test
    public void sortByDepthShortestFirst_parentsBeforeDescendantsAndDedup() {
        List<String> input = Arrays.asList("/a/b/c/x.txt", "/a", "/a/b", "/a/b/c/x.txt", "/m.md");
        List<String> sorted = SshManager.sortByDepthShortestFirst(input);

        assertEquals(4, sorted.size()); // 去重后 4 项
        assertEquals("/a", sorted.get(0));       // 祖先在前
        assertEquals("/a/b", sorted.get(1));
        assertEquals("/m.md", sorted.get(2));    // 长度 5，先于更深的子项
        assertEquals("/a/b/c/x.txt", sorted.get(3));
    }

    @Test
    public void sortByDepthShortestFirst_stableShorterFirst() {
        // 等长路径保持相对顺序稳定（LinkedHashSet）
        List<String> input = Arrays.asList("/b/b.txt", "/a/a.txt");
        List<String> sorted = SshManager.sortByDepthShortestFirst(input);
        assertEquals(Arrays.asList("/b/b.txt", "/a/a.txt"), sorted);
    }

    @Test
    public void validateBatchMove_okWhenAllValid() {
        List<String> srcs = Arrays.asList("/a/x.txt", "/b/y.txt");
        Set<String> dirs = new HashSet<>();
        assertNull(SshManager.validateBatchMove(srcs, dirs, "/dest"));
    }

    @Test
    public void validateBatchMove_rejectsSameLocation() {
        List<String> srcs = Arrays.asList("/a/x.txt");
        assertNotNull(SshManager.validateBatchMove(srcs, new HashSet<>(), "/a"));
    }

    @Test
    public void validateBatchMove_rejectsMoveIntoItself() {
        List<String> srcs = Arrays.asList("/a/b");
        Set<String> dirs = new HashSet<>(Arrays.asList("/a/b"));
        assertNotNull(SshManager.validateBatchMove(srcs, dirs, "/a/b/data"));
        // 目录源未在 dirSrcPaths 中时不做自指判断（与单源语义一致，由调用方保证）
        assertNull(SshManager.validateBatchMove(srcs, new HashSet<>(), "/a/b/data"));
    }

    @Test
    public void validateBatchMove_rejectsTargetNameClash() {
        // 两个不同父目录下的同名文件移到同一目标 → 冲突
        List<String> srcs = Arrays.asList("/a/x.txt", "/b/x.txt");
        assertNotNull(SshManager.validateBatchMove(srcs, new HashSet<>(), "/dest"));
    }

    @Test
    public void validateBatchMove_rejectsEmpty() {
        assertNotNull(SshManager.validateBatchMove(new ArrayList<>(), new HashSet<>(), "/dest"));
        assertNotNull(SshManager.validateBatchMove(null, new HashSet<>(), "/dest"));
    }

    // ---- listFiles 解析：toRemoteFile（单条 LsEntry → RemoteFile 纯函数，无需真实 SFTP 连接） ----

    @Test
    public void toRemoteFile_directoryEntry_mapsAttrsAndPath() {
        ChannelSftp.LsEntry entry = LsTestFactory.entry("conf", 0x41ED, 4096, 1_700_000_000);
        RemoteFile f = SshManager.toRemoteFile("/home/u", entry);
        assertEquals("conf", f.getName());
        assertEquals("/home/u/conf", f.getPath());
        assertTrue(f.isDirectory());
        assertEquals(4096, f.getSize());
        assertEquals(0x41ED, f.getPermissions());
        assertEquals(1_700_000_000L, f.getMtime());
    }

    @Test
    public void toRemoteFile_fileEntry_isNotDirectory() {
        ChannelSftp.LsEntry entry = LsTestFactory.entry("notes.md", 0x81A4, 12345, 1_700_000_001);
        RemoteFile f = SshManager.toRemoteFile("/home/u/docs", entry);
        assertEquals("notes.md", f.getName());
        assertEquals("/home/u/docs/notes.md", f.getPath());
        assertFalse(f.isDirectory());
        assertEquals(12345, f.getSize());
    }

    @Test
    public void toRemoteFile_rootParent_noDoubleSlash() {
        ChannelSftp.LsEntry entry = LsTestFactory.entry("etc", 0x41ED, 4096, 1_700_000_000);
        RemoteFile f = SshManager.toRemoteFile("/", entry);
        assertEquals("/etc", f.getPath());
    }

    @Test
    public void toRemoteFile_trailingSlashParent_joinsOnce() {
        ChannelSftp.LsEntry entry = LsTestFactory.entry("a.txt", 0x81A4, 7, 1_700_000_000);
        RemoteFile f = SshManager.toRemoteFile("/home/u/", entry);
        assertEquals("/home/u/a.txt", f.getPath());
    }

    @Test
    public void toRemoteFile_symlink_isTreatedAsFile() {
        // S_IFLNK(0xA000)|0777：类型位非目录 → 按普通文件处理（与 listFiles 既有行为一致）
        ChannelSftp.LsEntry entry = LsTestFactory.entry("link.md", 0xA1FF, 0, 1_700_000_000);
        RemoteFile f = SshManager.toRemoteFile("/home/u", entry);
        assertFalse(f.isDirectory());
        assertEquals("link.md", f.getName());
    }

    @Test
    public void toRemoteFile_plainPermsPreserved() {
        ChannelSftp.LsEntry entry = LsTestFactory.entry("x.txt", 0x1A4, 42, 1_700_000_000);
        RemoteFile f = SshManager.toRemoteFile("/home/u", entry);
        assertEquals(0x1A4, f.getPermissions());
        assertEquals(42, f.getSize());
        assertEquals(1_700_000_000L, f.getMtime());
    }

    // ── 连接复用资格判定（reuseEligible = SshConnectionHelper 谓词 + 心跳一致性） ──

    private static SshConfig cfg(String host, String pass) {
        return new SshConfig("alias", host, 22, "u", pass, "/");
    }

    @Test
    public void reuseEligible_sameTargetSameCredsSameHeartbeat() {
        assertTrue(SshManager.reuseEligible(cfg("h", "p"), cfg("h", "p"), true, 5000, 5000));
    }

    @Test
    public void reuseEligible_notAliveOrDifferentTarget() {
        assertFalse(SshManager.reuseEligible(cfg("h", "p"), cfg("h", "p"), false, 5000, 5000));
        assertFalse(SshManager.reuseEligible(cfg("h", "p"), cfg("h2", "p"), true, 5000, 5000));
    }

    @Test
    public void reuseEligible_heartbeatChangedForcesReconnect() {
        // 用户改了心跳偏好：「下次 connect 生效」语义 → 即使同目标存活也必须重建
        assertFalse(SshManager.reuseEligible(cfg("h", "p"), cfg("h", "p"), true, 5000, 10000));
        // 心跳相同则不受影响
        assertTrue(SshManager.reuseEligible(cfg("h", "p"), cfg("h", "p"), true, 10000, 10000));
    }

    @Test
    public void reuseEligible_credentialChangeForcesReconnect() {
        assertFalse(SshManager.reuseEligible(cfg("h", "old"), cfg("h", "new"), true, 5000, 5000));
    }
}
