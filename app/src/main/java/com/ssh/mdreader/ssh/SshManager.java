package com.ssh.mdreader.ssh;

import android.util.Log;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.model.SshConfig;
import com.ssh.mdreader.util.FileSortUtils;
import com.ssh.mdreader.util.UiUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton owner of the SSH/SFTP connection.
 *
 * <p>JSch's {@link ChannelSftp} is NOT thread-safe: concurrent commands on the
 * same channel can corrupt its internal stream. All SFTP operations here are
 * therefore serialized on a single worker thread (see {@link #sftpExecutor}),
 * which also bounds thread creation (one worker instead of a thread per op).</p>
 *
 * <p>Callbacks are delivered on the worker thread, never on the main thread.</p>
 */
public class SshManager {

    private static final String TAG = "SshManager";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** Socket read timeout: bounds blocking channel I/O (e.g. liveness pwd()) on zombie links. */
    private static final int IO_TIMEOUT_MS = 10_000;
    /** Keepalive heartbeat interval to the SSH server (JSch setServerAliveInterval). */
    static final int DEFAULT_HEARTBEAT_MS = 5_000;
    /** 合法心跳间隔范围（毫秒）；越界值回退默认，见 {@link #sanitizeHeartbeat(int)}。 */
    static final int HEARTBEAT_MIN_MS = 1_000;
    static final int HEARTBEAT_MAX_MS = 60_000;
    /** Unanswered heartbeats before JSch declares the link dead (JSch default is 1; 3 tolerates transient hiccups). */
    private static final int HEARTBEAT_COUNT_MAX = 3;

    private static volatile SshManager instance;

    /** Single worker serializing every session/channel operation. */
    private final ExecutorService sftpExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sftp-worker");
        t.setDaemon(true);
        return t;
    });

    private Session session;
    private ChannelSftp sftpChannel;
    private SshConfig config;
    private String homeDirectory = "/";
    private ConnectionListener listener;
    /** Set when the user explicitly disconnects; suppresses auto-reconnect (cleared by connect()). */
    private volatile boolean userDisconnected;
    /** 心跳间隔（毫秒），由 UI 层从偏好注入；对已建立连接不生效，下次 connect/自动重连生效。 */
    private volatile int heartbeatIntervalMs = DEFAULT_HEARTBEAT_MS;

    public interface ConnectionListener {
        void onConnected();
        void onError(String message);
        void onDisconnected();
    }

    public interface FileListCallback {
        void onSuccess(List<RemoteFile> files);
        void onError(String message);
    }

    public interface FileContentCallback {
        void onSuccess(String content);
        void onError(String message);
    }

    private SshManager() {}

    public static SshManager getInstance() {
        if (instance == null) {
            synchronized (SshManager.class) {
                if (instance == null) {
                    instance = new SshManager();
                }
            }
        }
        return instance;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;
    }

    /** 设置心跳间隔（毫秒）。非法值（<1000 或 >60000）回退默认 5000。 */
    public void setHeartbeatIntervalMs(int ms) {
        this.heartbeatIntervalMs = sanitizeHeartbeat(ms);
    }

    public int getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    /** 心跳间隔合法性过滤：越界值回退默认 5000。包级可见以便单测。 */
    static int sanitizeHeartbeat(int ms) {
        if (ms < HEARTBEAT_MIN_MS || ms > HEARTBEAT_MAX_MS) return DEFAULT_HEARTBEAT_MS;
        return ms;
    }

    public void connect(SshConfig config, ConnectionListener listener) {
        this.config = config;
        this.listener = listener;
        userDisconnected = false;
        // Capture per-request listener so callbacks always reach the Activity
        // that initiated THIS connect, even if another one registers later.
        ConnectionListener cb = listener;

        sftpExecutor.execute(() -> {
            try {
                openChannelSync();

                try {
                    homeDirectory = sftpChannel.getHome();
                } catch (Exception e) {
                    Log.w(TAG, "获取远端 home 目录失败，回退到 /", e);
                    homeDirectory = "/";
                }

                if (cb != null) cb.onConnected();
            } catch (Exception e) {
                Log.w(TAG, "连接失败: " + config.getHost() + ":" + config.getPort(), e);
                cleanupSync();
                if (cb != null) cb.onError(UiUtils.errorMessage(e));
            }
        });
    }

    /** 用当前 config 建连（session + sftp channel），失败时同步清理。仅限 worker 线程调用。 */
    private void openChannelSync() throws Exception {
        cleanupSync();
        JSch jsch = new JSch();
        session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
        session.setPassword(config.getPassword());

        Properties props = new Properties();
        props.put("StrictHostKeyChecking", "no");
        session.setConfig(props);
        session.setServerAliveInterval(heartbeatIntervalMs);
        session.setServerAliveCountMax(HEARTBEAT_COUNT_MAX);
        // Bound socket reads so channel I/O on a silently-broken link
        // fails fast instead of blocking indefinitely.
        session.setTimeout(IO_TIMEOUT_MS);
        session.connect(CONNECT_TIMEOUT_MS);

        sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect(CONNECT_TIMEOUT_MS);
    }

    /**
     * 返回当前可用的 SFTP 通道；若连接已死（JSch 心跳判死或对端断开），
     * 自动用最近一次 config 重连一次再返回。用户显式断开后不自动重连。
     * 仅限 worker 线程调用。
     */
    private ChannelSftp obtainChannel() throws Exception {
        if (session != null && session.isConnected()
                && sftpChannel != null && sftpChannel.isConnected()) {
            return sftpChannel;
        }
        if (userDisconnected) {
            throw new IllegalStateException("已断开连接");
        }
        if (config == null) {
            throw new IllegalStateException("未配置连接");
        }
        Log.w(TAG, "连接已断开，自动重连: " + config.getHost() + ":" + config.getPort());
        openChannelSync();
        return sftpChannel;
    }

    private interface SftpOp {
        void run(ChannelSftp channel) throws Exception;
    }

    /**
     * 一次 SFTP 操作的统一执行骨架（worker 线程上调用）：先校验/重连通道，
     * 然后执行；{@code retryable}（只读类操作）在疑似断线的异常下重连后重试一次。
     * 写/删类操作传 {@code false}，避免 append 在部分写入后被重试造成重复数据。
     */
    private void runOp(String name, boolean retryable, SftpOp op, java.util.function.Consumer<String> onError) {
        try {
            op.run(obtainChannel());
        } catch (Exception first) {
            if (retryable && isConnectionGone(first)) {
                Log.w(TAG, name + " 疑似断线，重连后重试一次", first);
                try {
                    op.run(obtainChannel());
                    return;
                } catch (Exception second) {
                    Log.w(TAG, name + " 重试仍失败", second);
                    onError.accept(UiUtils.errorMessage(second));
                    return;
                }
            }
            Log.w(TAG, name + " 失败", first);
            onError.accept(UiUtils.errorMessage(first));
        }
    }

    /** 判断异常是否表明 SSH 链路已断（与「文件不存在/权限不足」等业务错误区分）。包级可见以便单测。 */
    static boolean isConnectionGone(Exception e) {
        if (e instanceof java.io.IOException || e instanceof java.net.SocketException) return true;
        if (e instanceof SftpException) {
            String m = e.getMessage();
            if (m == null) return true;
            m = m.toLowerCase();
            return m.contains("closed") || m.contains("connection")
                    || m.contains("eof") || m.contains("timeout") || m.contains("broken");
        }
        return false;
    }

    /**
     * Synchronously force-closes any existing session and SFTP channel.
     * Callbacks are not fired. MUST be called on the {@code sftpExecutor}
     * worker only, so that no in-flight operation touches the channel while
     * it is being torn down.
     */
    private void cleanupSync() {
        try {
            ChannelSftp ch = sftpChannel;
            if (ch != null) {
                try { ch.disconnect(); } catch (Exception e) { Log.w(TAG, "断开 SFTP channel 失败", e); }
                sftpChannel = null;
            }
            Session s = session;
            if (s != null) {
                try { s.disconnect(); } catch (Exception e) { Log.w(TAG, "断开 Session 失败", e); }
                session = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "清理连接状态失败", e);
        }
    }

    public void disconnect() {
        userDisconnected = true;
        sftpExecutor.execute(() -> {
            try {
                if (sftpChannel != null && sftpChannel.isConnected()) {
                    sftpChannel.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception e) {
                Log.w(TAG, "断开连接失败", e);
            } finally {
                sftpChannel = null;
                session = null;
                // Do NOT clear config — preserve it for reconnection
                ConnectionListener cb = listener;
                if (cb != null) cb.onDisconnected();
            }
        });
    }

    public boolean isConnected() {
        return session != null && session.isConnected()
                && sftpChannel != null && sftpChannel.isConnected();
    }

    /**
     * Reliable liveness check.  Sends a lightweight SFTP {@code pwd()}
     * command to verify the connection is actually responsive — not just
     * that the socket object exists.  JSch's {@code isConnected()} may
     * return {@code true} even after the TCP link has been silently broken
     * (e.g. by a network switch), leading to zombie connections.
     */
    public boolean isConnectionAlive() {
        if (session == null || !session.isConnected()) return false;
        if (sftpChannel == null || !sftpChannel.isConnected()) return false;
        try {
            sftpChannel.pwd();
            return true;
        } catch (Exception e) {
            Log.d(TAG, "连接存活检查失败", e);
            return false;
        }
    }

    public interface ConnectionAliveCallback {
        void onResult(boolean alive);
    }

    /**
     * Asynchronous liveness check.  {@link #isConnectionAlive()} performs a
     * blocking SFTP round-trip (up to the socket read timeout on a zombie
     * link), so it must never be invoked from the main thread.  This queues
     * the check on the SFTP worker (serialized with other channel ops) and
     * delivers the result via {@code callback} (still on that worker thread).
     */
    public void checkConnectionAlive(ConnectionAliveCallback callback) {
        sftpExecutor.execute(() -> callback.onResult(isConnectionAlive()));
    }

    public String getHomeDirectory() {
        return homeDirectory;
    }

    public SshConfig getConfig() {
        return config;
    }

    public void listFiles(String path, FileListCallback callback) {
        sftpExecutor.execute(() -> runOp("列出目录", true, channel -> {
            Vector<ChannelSftp.LsEntry> entries = channel.ls(path);
            List<RemoteFile> files = new ArrayList<>();

            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (name.equals(".") || name.equals("..")) continue;

                SftpATTRS attrs = entry.getAttrs();
                String fullPath = buildChildPath(path, name);

                files.add(new RemoteFile(
                        name,
                        fullPath,
                        attrs.isDir(),
                        attrs.getSize(),
                        attrs.getPermissions(),
                        attrs.getMTime()
                ));
            }

            // SFTP 返回顺序不定，API 兜底固定为名称序；UI 层可按偏好再排。
            List<RemoteFile> sorted = FileSortUtils.sort(files, FileSortUtils.SORT_NAME);
            callback.onSuccess(sorted);
        }, callback::onError));
    }

    public void readFile(String path, FileContentCallback callback) {
        sftpExecutor.execute(() -> runOp("读取文件", true, channel -> {
            InputStream is = channel.get(path);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            is.close();
            callback.onSuccess(baos.toString(StandardCharsets.UTF_8.name()));
        }, callback::onError));
    }

    public interface FileBytesCallback {
        void onSuccess(byte[] bytes);
        void onError(String message);
    }

    public void readFileBytes(String path, FileBytesCallback callback) {
        sftpExecutor.execute(() -> runOp("读取字节", true, channel -> {
            InputStream is = channel.get(path);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            is.close();
            callback.onSuccess(baos.toByteArray());
        }, callback::onError));
    }

    public interface WriteFileCallback {
        void onSuccess();
        void onError(String message);
    }

    public void writeFile(String path, String content, boolean append, WriteFileCallback callback) {
        sftpExecutor.execute(() -> runOp("写文件", false, channel -> {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
            int mode = append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE;
            channel.put(bais, path, mode);
            callback.onSuccess();
        }, callback::onError));
    }

    public interface DeleteFileCallback {
        void onSuccess();
        void onError(String message);
    }

    public void deleteFile(String path, DeleteFileCallback callback) {
        sftpExecutor.execute(() -> runOp("删除文件", false, channel -> {
            channel.rm(path);
            callback.onSuccess();
        }, callback::onError));
    }

    /**
     * 递归删除目录（含全部内容）。JSch 的 {@code rm} 只删文件、{@code rmdir} 只删空目录，
     * 故非空目录先递归清空子项再 rmdir。与 deleteFile 同为写操作不重试；
     * 与服务器交互在单个 sftpExecutor 任务内完成，中途失败会报错（可能残留部分子项，
     * 与所有递归删除工具一致，无原子回滚，确认文案已注明不可恢复）。
     */
    public void deleteDirectory(String path, DeleteFileCallback callback) {
        sftpExecutor.execute(() -> runOp("删除目录", false, channel -> {
            deleteNodeSync(channel, path);
            callback.onSuccess();
        }, callback::onError));
    }

    /** 递归删除单个节点（仅限 worker 线程调用）：文件→rm；空目录→rmdir；非空目录→清空后 rmdir。 */
    private void deleteNodeSync(ChannelSftp channel, String path) throws Exception {
        if (channel.stat(path).isDir()) {
            try {
                channel.rmdir(path); // 空目录直接成功
                return;
            } catch (SftpException ignored) {
                // 非空目录（或服务器对非空目录返回 SSH_FX_FAILURE）：走递归清空
            }
            Vector<ChannelSftp.LsEntry> entries = channel.ls(path);
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (name.equals(".") || name.equals("..")) continue;
                deleteNodeSync(channel, buildChildPath(path, name));
            }
            channel.rmdir(path);
        } else {
            channel.rm(path);
        }
    }

    public interface RenameFileCallback {
        void onSuccess();
        void onError(String message);
    }

    public void renameFile(String oldPath, String newPath, RenameFileCallback callback) {
        sftpExecutor.execute(() -> runOp("重命名", false, channel -> {
            channel.rename(oldPath, newPath);
            callback.onSuccess();
        }, callback::onError));
    }

    public interface ExistsCallback {
        void onResult(boolean exists);
        void onError(String message);
    }

    /**
     * 检查远端路径是否存在：stat 探测，{@link ChannelSftp#SSH_FX_NO_SUCH_FILE} 视为不存在（false），
     * 其余异常经 onError 上报。只读类操作，疑似断线时重连重试一次（与 listFiles/readFile 同口径）。
     */
    public void fileExists(String path, ExistsCallback callback) {
        sftpExecutor.execute(() -> runOp("检查路径", true, channel -> {
            try {
                channel.stat(path);
                callback.onResult(true);
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                    callback.onResult(false);
                } else {
                    throw e;
                }
            }
        }, callback::onError));
    }

    public interface CopyFileCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * 复制文件：get（远端读）→ InputStream 流式 put（远端写，OVERWRITE）。
     * SFTP 无服务器端 copy 原语，复制 = 两次远端传输；写操作不重试（runOp retryable=false），
     * 与 writeFile/renameFile 同口径，避免部分写入后被重试造成重复数据。
     * 目标已存在时由调用方先经 {@link #fileExists} 提示覆盖/跳过，本方法只负责写（总是覆盖）。
     */
    public void copyFile(String srcPath, String dstPath, CopyFileCallback callback) {
        sftpExecutor.execute(() -> runOp("复制文件", false, channel -> {
            try (InputStream is = channel.get(srcPath)) {
                channel.put(is, dstPath, ChannelSftp.OVERWRITE);
            }
            callback.onSuccess();
        }, callback::onError));
    }

    public interface ChmodCallback {
        void onSuccess();
        void onError(String message);
    }

    /**
     * 修改文件/目录权限（JSch chmod 走 SSH_FXP_SETSTAT，OpenSSH sftp-server 协议标配）。
     * 只读之外的变更操作不重试（与 renameFile 同口径）。
     */
    public void chmodFile(String path, int mode, ChmodCallback callback) {
        sftpExecutor.execute(() -> runOp("修改权限", false, channel -> {
            channel.chmod(mode, path);
            callback.onSuccess();
        }, callback::onError));
    }

    /**
     * 把用户输入解析为八进制权限值（纯函数，便于单测；UI 层亦用于校验）。
     * 仅接受 3~4 位八进制数（如 644 / 755 / 1777）；非法输入返回 -1。
     */
    public static int parseOctalMode(String input) {
        if (input == null || !input.matches("[0-7]{3,4}")) return -1;
        try {
            return Integer.parseInt(input, 8);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 由原路径与「同目录下的新文件名」构造目标路径（纯函数，便于单测）。
     * 新名不允许包含 '/'（含路径的移动需求见 {@code renameFile} 完整路径调用，
     * UI 层仅负责拦截）。
     */
    public static String buildRenamePath(String oldPath, String newName) {
        int idx = oldPath.lastIndexOf('/');
        if (idx < 0) return newName;
        return oldPath.substring(0, idx + 1) + newName;
    }

    /**
     * 拼接父目录与子项名为完整路径（纯函数，便于单测）：父目录以 '/' 结尾时直接拼接，
     * 否则补一个 '/'；根目录传入 "/" 亦正确（结果 "/name"）。
     */
    public static String buildChildPath(String parentPath, String name) {
        return parentPath.endsWith("/") ? parentPath + name : parentPath + "/" + name;
    }

    /**
     * 构造「移动到目标目录」的完整目标路径（纯函数，便于单测）：
     * 源文件名保持不变，拼接在 targetDir 之后；规范掉多余的结尾斜杠（根目录除外）。
     */
    public static String buildMovePath(String srcPath, String targetDir) {
        int idx = srcPath.lastIndexOf('/');
        String name = idx < 0 ? srcPath : srcPath.substring(idx + 1);
        if ("/".equals(targetDir)) return "/" + name;
        String base = targetDir.endsWith("/")
                ? targetDir.substring(0, targetDir.length() - 1) : targetDir;
        return base + "/" + name;
    }

    /**
     * 构造「复制到目标目录」的完整目标路径（纯函数，便于单测）。
     * 与 {@link #buildMovePath} 语义一致（源文件名不变拼到 targetDir），故直接委托，避免两份逻辑漂移。
     */
    public static String buildCopyPath(String srcPath, String targetDir) {
        return buildMovePath(srcPath, targetDir);
    }

    /** 目录部分（不含末级名称），根目录返回 "/"；无斜杠时返回 ""。 */
    private static String parentOf(String path) {
        String p = path;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        int idx = p.lastIndexOf('/');
        if (idx < 0) return "";
        if (idx == 0) return "/";
        return p.substring(0, idx);
    }

    /**
     * 目标目录是否与源同位置（即目标目录 = 源所在目录，移动等于没动）。
     * 纯函数，便于单测；UI 层据此拦截并提示。
     */
    public static boolean isMoveSameLocation(String srcPath, String targetDir) {
        if (targetDir == null) return false;
        String target = targetDir.length() > 1 && targetDir.endsWith("/")
                ? targetDir.substring(0, targetDir.length() - 1) : targetDir;
        if (target.isEmpty()) return false;
        return target.equals(parentOf(srcPath));
    }

    /**
     * 目录源是否要移动到自身或其子孙目录中（仅对目录源有意义；文件源调用方不应触发）。
     * 纯函数，便于单测；UI 层据此拦截并提示。
     */
    public static boolean isMoveIntoItself(String srcPath, String targetDir) {
        if (targetDir == null) return false;
        String src = srcPath.endsWith("/") && !srcPath.equals("/")
                ? srcPath.substring(0, srcPath.length() - 1) : srcPath;
        String target = targetDir.endsWith("/") && !targetDir.equals("/")
                ? targetDir.substring(0, targetDir.length() - 1) : targetDir;
        return target.equals(src) || target.startsWith(src + "/");
    }
}
