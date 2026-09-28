package com.ssh.mdreader.util;

import android.content.Context;
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
}
