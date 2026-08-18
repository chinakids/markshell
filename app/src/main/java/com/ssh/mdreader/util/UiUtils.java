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

    public static String getParentPath(String path) {
        if (path == null || path.equals("/")) return "/";
        String trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int lastSlash = trimmed.lastIndexOf('/');
        if (lastSlash <= 0) return "/";
        return trimmed.substring(0, lastSlash);
    }
}
