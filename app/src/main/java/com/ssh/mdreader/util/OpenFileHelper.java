package com.ssh.mdreader.util;

import android.content.Context;
import android.content.Intent;

import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.ui.CodeViewerActivity;
import com.ssh.mdreader.ui.CsvReaderActivity;
import com.ssh.mdreader.ui.ImageViewerActivity;
import com.ssh.mdreader.ui.MarkdownReaderActivity;
import com.ssh.mdreader.ui.TextViewerActivity;

/**
 * 远端文件路径 → 查看器 Activity 的分发单点（此前只有 FileBrowserActivity 一份
 * 内联逻辑；Markdown 链接点击（路线图第二轮 #1）复用同一分发，避免双份维护）。
 *
 * <p>与 {@link RemoteFile#isMarkdown() 等} 扩展名判定严格同源；不支持的扩展名返回 null
 * （调用方自行 toast，文案一致）。</p>
 *
 * <p>能力发现循环第廿六轮（#37）：抽取 {@link #detectViewerKind(String)} 纯函数作为
 * 查看器类型判定的单一语义源（两栏预览 pane「打开」出口按 kind 决定按钮可见性，
 * 与 {@link #buildViewerIntent} 逐项同源，不重复实现扩展名表）。</p>
 */
public final class OpenFileHelper {

    /** 查看器类型（与 RemoteFile 扩展名判定同源）；不支持的类型为 null。 */
    public enum ViewerKind { MARKDOWN, CSV, CODE, IMAGE, TEXT }

    private OpenFileHelper() {
    }

    /**
     * 由远端绝对路径判定查看器类型（纯函数，JVM 可测）。
     * 类型分发与 {@link RemoteFile#isMarkdown()} 等严格同源（探测同参构造）；
     * 不支持/空路径返回 null。
     */
    public static ViewerKind detectViewerKind(String remotePath) {
        if (remotePath == null || remotePath.isEmpty()) return null;
        int slash = remotePath.lastIndexOf('/');
        String name = slash >= 0 ? remotePath.substring(slash + 1) : remotePath;
        if (name.isEmpty()) return null;
        RemoteFile probe = new RemoteFile(name, remotePath, false, 0, 0, 0);
        if (probe.isMarkdown()) return ViewerKind.MARKDOWN;
        if (probe.isCsv()) return ViewerKind.CSV;
        if (probe.isCodeFile()) return ViewerKind.CODE;
        if (probe.isImageFile()) return ViewerKind.IMAGE;
        if (probe.isTextFile()) return ViewerKind.TEXT;
        return null;
    }

    /**
     * 由远端绝对路径构建查看器 Intent（extra：file_path/file_name，与 FileBrowserActivity
     * 既往约定一致）。不支持的类型返回 null。
     */
    public static Intent buildViewerIntent(Context context, String remotePath) {
        ViewerKind kind = detectViewerKind(remotePath);
        if (kind == null) return null;
        int slash = remotePath.lastIndexOf('/');
        String name = slash >= 0 ? remotePath.substring(slash + 1) : remotePath;
        Intent intent = new Intent();
        switch (kind) {
            case MARKDOWN:
                intent.setClass(context, MarkdownReaderActivity.class);
                break;
            case CSV:
                intent.setClass(context, CsvReaderActivity.class);
                break;
            case CODE:
                intent.setClass(context, CodeViewerActivity.class);
                break;
            case IMAGE:
                intent.setClass(context, ImageViewerActivity.class);
                break;
            case TEXT:
                intent.setClass(context, TextViewerActivity.class);
                break;
        }
        intent.putExtra("file_path", remotePath);
        intent.putExtra("file_name", name);
        return intent;
    }
}
