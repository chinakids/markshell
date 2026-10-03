package com.ssh.mdreader.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Layout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import android.view.View;

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
}
