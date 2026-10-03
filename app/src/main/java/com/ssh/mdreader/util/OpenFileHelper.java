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
 */
public final class OpenFileHelper {

    private OpenFileHelper() {
    }

    /**
     * 由远端绝对路径构建查看器 Intent（extra：file_path/file_name，与 FileBrowserActivity
     * 既往约定一致）。不支持的类型返回 null。
     */
    public static Intent buildViewerIntent(Context context, String remotePath) {
        if (remotePath == null || remotePath.isEmpty()) return null;
        int slash = remotePath.lastIndexOf('/');
        String name = slash >= 0 ? remotePath.substring(slash + 1) : remotePath;
        if (name.isEmpty()) return null;
        RemoteFile probe = new RemoteFile(name, remotePath, false, 0, 0, 0);
        Intent intent = new Intent();
        if (probe.isMarkdown()) {
            intent.setClass(context, MarkdownReaderActivity.class);
        } else if (probe.isCsv()) {
            intent.setClass(context, CsvReaderActivity.class);
        } else if (probe.isCodeFile()) {
            intent.setClass(context, CodeViewerActivity.class);
        } else if (probe.isImageFile()) {
            intent.setClass(context, ImageViewerActivity.class);
        } else if (probe.isTextFile()) {
            intent.setClass(context, TextViewerActivity.class);
        } else {
            return null;
        }
        intent.putExtra("file_path", remotePath);
        intent.putExtra("file_name", name);
        return intent;
    }
}
