package com.ssh.mdreader.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.Log;

import com.ssh.mdreader.ssh.SshManager;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.noties.markwon.image.ImageItem;
import io.noties.markwon.image.SchemeHandler;

/**
 * Markwon 图片 {@link SchemeHandler}：处理 {@code markdown-sftp:} 前缀的图片
 * destination，从 SSH 会话按绝对远端路径取字节、解码为 {@link BitmapDrawable}。
 *
 * <p><b>线程模型</b>（实证自 Markwon 4.6.2 源码）：loader 在<b>后台线程</b>
 * （executorService）同步调用 {@link #handle}，结果经 main-looper {@code Handler}
 * post 回 {@code AsyncDrawable.setResult}。因此本 handler 可以：
 * ① 在自身线程上<b>阻塞等待</b> SFTP 读取（经 {@link SshManager#readFileBytes}
 * 走既有<b>单 worker 串行</b>队列，再用 {@link CountDownLatch} 同步取结果——
 * 不破坏 SshManager 的连接串行模型，也不会死锁：等待线程不属于 SFTP worker）；</p>
 *
 * <p>② 在后台线程解码（{@link BitmapFactory}，非 UI 线程）。</p>
 *
 * <p><b>失败语义</b>：任何失败（无会话/读取失败/超时/解码失败）抛
 * {@link IOException}，由 Markwon loader 的 errorHandler 兜底（默认仅 logcat，
 * 图片位置不显示——与缺失文件行为一致，不崩不弹层）。</p>
 *
 * <p><b>缓存</b>：成功读取的字节缓存于 {@link SftpImageCache}（静态共享，
 * 阅读器/预览 pane 一致），避免重复 SFTP 往返。</p>
 */
public final class SftpImageSchemeHandler extends SchemeHandler {

    private static final String TAG = "MarkShellImg";

    /** 与 {@link ImageTargetHelper#SCHEME} 同源。 */
    public static final String SCHEME = ImageTargetHelper.SCHEME;

    /** 单次读取等待上限（默认 15s，覆盖慢链路；与连接心跳同一数量级）。 */
    private static final long DEFAULT_TIMEOUT_MS = 15_000L;

    private final Context appContext;
    private final SftpImageCache cache;
    private final long timeoutMs;

    private static final SftpImageCache SHARED_CACHE = new SftpImageCache();

    /** 进程级共享 handler（cache 随之共享；context 取 applicationContext 供 resources）。 */
    private static volatile SftpImageSchemeHandler instance;

    /** 取进程级单例；{@code context} 仅首次使用（其 applicationContext）。 */
    public static SftpImageSchemeHandler getInstance(Context context) {
        if (instance == null) {
            synchronized (SftpImageSchemeHandler.class) {
                if (instance == null) {
                    instance = new SftpImageSchemeHandler(context, SHARED_CACHE, DEFAULT_TIMEOUT_MS);
                }
            }
        }
        return instance;
    }

    /** 测试/注入构造。 */
    SftpImageSchemeHandler(Context context, SftpImageCache cache, long timeoutMs) {
        this.appContext = context != null ? context.getApplicationContext() : null;
        this.cache = cache;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public Collection<String> supportedSchemes() {
        return Collections.singletonList(SCHEME);
    }

    @Override
    public ImageItem handle(String destination, Uri uri) {
        String encodedPath = ImageTargetHelper.stripSftpScheme(destination);
        String remotePath = LinkTargetHelper.percentDecode(encodedPath);
        byte[] bytes = cache.get(remotePath);
        if (bytes == null) {
            try {
                bytes = loadSync(remotePath);
            } catch (IOException e) {
                Log.w(TAG, "图片加载失败: " + remotePath, e);
                throw new IllegalStateException(e);
            }
            cache.put(remotePath, bytes);
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) {
            throw new IllegalStateException("无法解码图片: " + remotePath);
        }
        // EXIF 方向修正（#42）：BitmapFactory 忽略 Orientation 标签，竖拍照片会横置；
        // 与图片查看器/预览 pane 同一语义源 ImageExifHelper，失败静默回退。
        bitmap = ImageExifHelper.rotateBitmap(bitmap,
                ImageExifHelper.orientationDegrees(bytes));
        return ImageItem.withResult(new BitmapDrawable(appContext.getResources(), bitmap));
    }

    /** 同步等待 SshManager 的单 worker 队列取出字节；超时/失败抛 IOException。 */
    private byte[] loadSync(String path) throws IOException {
        final CountDownLatch latch = new CountDownLatch(1);
        final byte[][] box = new byte[1][];
        final String[] err = new String[1];
        SshManager.getInstance().readFileBytes(path, new SshManager.FileBytesCallback() {
            @Override
            public void onSuccess(byte[] bytes) {
                box[0] = bytes;
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                err[0] = message;
                latch.countDown();
            }
        });
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("读取图片超时: " + path);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("读取图片被中断: " + path, e);
        }
        if (err[0] != null) throw new IOException("读取图片失败: " + err[0]);
        if (box[0] == null || box[0].length == 0) throw new IOException("图片内容为空: " + path);
        return box[0];
    }
}
