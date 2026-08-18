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

public class SshManager {

    private static final int CONNECT_TIMEOUT_MS = 10_000;

    private static volatile SshManager instance;

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

        new Thread(() -> {
            cleanupSync();

            try {
                JSch jsch = new JSch();
                session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
                session.setPassword(config.getPassword());

                Properties props = new Properties();
                props.put("StrictHostKeyChecking", "no");
                session.setConfig(props);
                session.setServerAliveInterval(5000);
                session.connect(CONNECT_TIMEOUT_MS);

                sftpChannel = (ChannelSftp) session.openChannel("sftp");
                sftpChannel.connect(CONNECT_TIMEOUT_MS);

                try {
                    homeDirectory = sftpChannel.getHome();
                } catch (Exception e) {
                    homeDirectory = "/";
                }

                if (listener != null) listener.onConnected();
            } catch (Exception e) {
                cleanupSync();
                if (listener != null) listener.onError(e.getMessage());
            }
        }, "ssh-connect").start();
    }

    /**
     * Synchronously force-closes any existing session and SFTP channel.
     * Safe to call from any thread. No callbacks are fired.
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
        new Thread(() -> {
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
                if (listener != null) listener.onDisconnected();
            }
        }, "ssh-disconnect").start();
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

    public String getHomeDirectory() {
        return homeDirectory;
    }

    public SshConfig getConfig() {
        return config;
    }

    public void listFiles(String path, FileListCallback callback) {
        new Thread(() -> {
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
        }, "ssh-ls").start();
    }

    public void readFile(String path, FileContentCallback callback) {
        new Thread(() -> {
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
        }, "ssh-read").start();
    }

    public interface FileBytesCallback {
        void onSuccess(byte[] bytes);
        void onError(String message);
    }

    public void readFileBytes(String path, FileBytesCallback callback) {
        new Thread(() -> {
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
        }, "ssh-read-bytes").start();
    }

    public interface WriteFileCallback {
        void onSuccess();
        void onError(String message);
    }

    public void writeFile(String path, String content, boolean append, WriteFileCallback callback) {
        new Thread(() -> {
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
        }, "ssh-write").start();
    }

    public interface DeleteFileCallback {
        void onSuccess();
        void onError(String message);
    }

    public void deleteFile(String path, DeleteFileCallback callback) {
        new Thread(() -> {
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
        }, "ssh-delete").start();
    }
}
