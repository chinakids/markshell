package com.ssh.mdreader.ssh;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.model.SshConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    /** Socket read timeout: bounds blocking channel I/O (e.g. liveness pwd()) on zombie links. */
    private static final int IO_TIMEOUT_MS = 10_000;

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

    public void connect(SshConfig config, ConnectionListener listener) {
        this.config = config;
        this.listener = listener;
        // Capture per-request listener so callbacks always reach the Activity
        // that initiated THIS connect, even if another one registers later.
        ConnectionListener cb = listener;

        sftpExecutor.execute(() -> {
            cleanupSync();

            try {
                JSch jsch = new JSch();
                session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
                session.setPassword(config.getPassword());

                Properties props = new Properties();
                props.put("StrictHostKeyChecking", "no");
                session.setConfig(props);
                session.setServerAliveInterval(5000);
                // Bound socket reads so channel I/O on a silently-broken link
                // fails fast instead of blocking indefinitely.
                session.setTimeout(IO_TIMEOUT_MS);
                session.connect(CONNECT_TIMEOUT_MS);

                sftpChannel = (ChannelSftp) session.openChannel("sftp");
                sftpChannel.connect(CONNECT_TIMEOUT_MS);

                try {
                    homeDirectory = sftpChannel.getHome();
                } catch (Exception e) {
                    homeDirectory = "/";
                }

                if (cb != null) cb.onConnected();
            } catch (Exception e) {
                cleanupSync();
                if (cb != null) cb.onError(e.getMessage());
            }
        });
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
                try { ch.disconnect(); } catch (Exception ignored) {}
                sftpChannel = null;
            }
            Session s = session;
            if (s != null) {
                try { s.disconnect(); } catch (Exception ignored) {}
                session = null;
            }
        } catch (Exception ignored) {}
    }

    public void disconnect() {
        sftpExecutor.execute(() -> {
            try {
                if (sftpChannel != null && sftpChannel.isConnected()) {
                    sftpChannel.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            } catch (Exception ignored) {
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
        sftpExecutor.execute(() -> {
            try {
                ChannelSftp channel = sftpChannel;
                if (channel == null || !channel.isConnected()) {
                    callback.onError("未连接到服务器");
                    return;
                }

                Vector<ChannelSftp.LsEntry> entries = channel.ls(path);
                List<RemoteFile> files = new ArrayList<>();

                for (ChannelSftp.LsEntry entry : entries) {
                    String name = entry.getFilename();
                    if (name.equals(".") || name.equals("..")) continue;

                    SftpATTRS attrs = entry.getAttrs();
                    String fullPath = path.endsWith("/") ? path + name : path + "/" + name;

                    files.add(new RemoteFile(
                            name,
                            fullPath,
                            attrs.isDir(),
                            attrs.getSize(),
                            attrs.getPermissions()
                    ));
                }

                Collections.sort(files, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                });

                callback.onSuccess(files);
            } catch (SftpException e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public void readFile(String path, FileContentCallback callback) {
        sftpExecutor.execute(() -> {
            try {
                ChannelSftp channel = sftpChannel;
                if (channel == null || !channel.isConnected()) {
                    callback.onError("未连接到服务器");
                    return;
                }

                InputStream is = channel.get(path);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                is.close();
                callback.onSuccess(baos.toString(StandardCharsets.UTF_8.name()));
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public interface FileBytesCallback {
        void onSuccess(byte[] bytes);
        void onError(String message);
    }

    public void readFileBytes(String path, FileBytesCallback callback) {
        sftpExecutor.execute(() -> {
            try {
                ChannelSftp channel = sftpChannel;
                if (channel == null || !channel.isConnected()) {
                    callback.onError("未连接到服务器");
                    return;
                }

                InputStream is = channel.get(path);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                is.close();
                callback.onSuccess(baos.toByteArray());
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public interface WriteFileCallback {
        void onSuccess();
        void onError(String message);
    }

    public void writeFile(String path, String content, boolean append, WriteFileCallback callback) {
        sftpExecutor.execute(() -> {
            try {
                ChannelSftp channel = sftpChannel;
                if (channel == null || !channel.isConnected()) {
                    callback.onError("未连接到服务器");
                    return;
                }
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(bytes);
                int mode = append ? ChannelSftp.APPEND : ChannelSftp.OVERWRITE;
                channel.put(bais, path, mode);
                callback.onSuccess();
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    public interface DeleteFileCallback {
        void onSuccess();
        void onError(String message);
    }

    public void deleteFile(String path, DeleteFileCallback callback) {
        sftpExecutor.execute(() -> {
            try {
                ChannelSftp channel = sftpChannel;
                if (channel == null || !channel.isConnected()) {
                    callback.onError("未连接到服务器");
                    return;
                }
                channel.rm(path);
                callback.onSuccess();
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }
}
