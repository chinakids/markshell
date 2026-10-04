package com.ssh.mdreader.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.Layout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.snackbar.Snackbar;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class UiUtils {

    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public static void showSnackbar(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT).show();
    }

    public static void showSnackbarLong(View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
    }

    /**
     * 常驻 Snackbar + 动作按钮（markor FileSearchEngine.bindSnackBar 同型：
     * {@code Snackbar LENGTH_INDEFINITE + setAction(cancel)}——后台长操作的非模态
     * 可中止提示）。返回实例供调用方在操作完成/取消时 dismiss。
     */
    public static Snackbar showSnackbarIndefiniteWithAction(View view, String message,
                                                            String action, Runnable onAction) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(action, v -> onAction.run());
        snackbar.show();
        return snackbar;
    }

    /**
     * Returns a non-null, human-readable error message for {@code t}:
     * its message if present, otherwise the class simple name, otherwise a
     * generic fallback. Prevents callers from surfacing "失败: null" to the
     * UI or logcat when an exception carries no message.
     */
    public static String errorMessage(Throwable t) {
        if (t == null) return "未知错误";
        String m = t.getMessage();
        if (m != null && !m.isEmpty()) return m;
        String name = t.getClass().getSimpleName();
        return name.isEmpty() ? "未知错误" : name;
    }

    public static String getParentPath(String path) {
        if (path == null || path.equals("/")) return "/";
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash <= 0) return "/";
        return trimmed.substring(0, lastSlash);
    }

    /**
     * 平滑滚动到 {@code textView} 指定字符偏移所在行（垂直居中）。
     * 布局未就绪（post 时 layout==null）时为空操作；偏移越界由
     * {@link Layout#getLineForOffset} 钳制到最近行。行顶上方留 16dp 视觉缓冲。
     *
     * <p><b>来源</b>：抽取自 AnnotationOverlayHelper.scrollToOffset（批注/大纲导航已
     * 生产验证的算法）——查看器查找（ViewerFindBar）与本类共用，消除滚动实现复制
     * （两处实现=两套 bug 面）。</p>
     */
    public static void scrollToOffsetCenter(ScrollView scrollView, TextView textView,
                                            int charOffset) {
        textView.post(() -> {
            Layout layout = textView.getLayout();
            if (layout == null) return;
            int line = layout.getLineForOffset(charOffset);
            int lineTop = layout.getLineTop(line);
            int paddingTop = (int) (16 * textView.getResources().getDisplayMetrics().density);
            int scrollY = Math.max(0, lineTop + paddingTop - scrollView.getHeight() / 2);
            scrollView.smoothScrollTo(0, scrollY);
        });
    }

    /** 复制远程路径到系统剪贴板并 toast。路径由 ClipPathHelper 纯函数层派生文案。 */
    public static void copyRemotePath(Context context, String path) {
        String clip = ClipPathHelper.clipText(path);
        if (clip == null) return;
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("远程路径", clip));
        }
        String msg = ClipPathHelper.toastText(path);
        if (msg != null) {
            showToast(context, msg);
        }
    }

    // ── 内容分享（#51）───────────────────────────────────────────────────────

    /** FileProvider authority（manifest 中 ${applicationId}.fileprovider 对位）。 */
    private static final String FILE_PROVIDER_AUTHORITY = "com.ssh.mdreader.fileprovider";

    /**
     * 分享文本内容（markor {@code GsContextUtils.shareText} 同型：ACTION_SEND +
     * EXTRA_TEXT + text/plain + chooser）。文本超 {@link ShareHelper#MAX_EXTRA_TEXT_BYTES}
     * 时自动降级为文件流分享（EXTRA_STREAM + FileProvider，markor shareFile 同型），
     * 避免 Binder TransactionTooLargeException；两种通道对接收方语义一致=同一文本。
     * 空/空白文本提示后不动作。chooserTitle 默认「分享文本」。
     */
    public static void shareText(Context context, String text, String fileName) {
        shareText(context, text, fileName, "分享文本");
    }

    /** {@link #shareText(Context, String, String)} + 自定义 chooser 标题（批注导出等场景）。 */
    public static void shareText(Context context, String text, String fileName,
                                 String chooserTitle) {
        if (!ShareHelper.isShareableText(text)) {
            showToast(context, "暂无可分享内容");
            return;
        }
        if (ShareHelper.shouldStreamText(text)) {
            String name = fileName != null && !fileName.isEmpty() ? fileName : "分享文本.txt";
            shareStreamBytes(context, text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    name, ShareHelper.MIME_TEXT_PLAIN, chooserTitle);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(ShareHelper.MIME_TEXT_PLAIN);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(intent, chooserTitle));
    }

    /**
     * 分享原始字节（图片：原图直存零重编码，保 EXIF/格式；文本超大降级同口）。
     * 写入 cache/shared/ 后经 FileProvider 以 content:// + FLAG_GRANT_READ_URI_PERMISSION
     * 交给接收方（markor shareStream 同型），写入失败 toast 不崩溃。
     */
    public static void shareBytes(Context context, byte[] bytes, String fileName,
                                  String mime, String chooserTitle) {
        if (bytes == null || bytes.length == 0) {
            showToast(context, "暂无内容可分享");
            return;
        }
        shareStreamBytes(context, bytes, fileName, mime, chooserTitle);
    }

    private static void shareStreamBytes(Context context, byte[] bytes, String fileName,
                                         String mime, String chooserTitle) {
        File dir = new File(context.getCacheDir(), "shared");
        if (!dir.exists() && !dir.mkdirs()) {
            showToast(context, "分享失败：缓存目录不可用");
            return;
        }
        File file = new File(dir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        } catch (IOException e) {
            showToast(context, "分享失败：" + errorMessage(e));
            return;
        }
        Uri uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_TEXT, fileName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, chooserTitle));
    }
}
