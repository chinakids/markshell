package com.ssh.mdreader.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.ssh.SshManager;

import java.util.ArrayList;
import java.util.List;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.image.ImagesPlugin;

/**
 * FileBrowserActivity 的两栏预览（layout-w600dp 右侧 pane）渲染编排层：markdown/csv/
 * 代码/图片/文本的懒加载（SshManager 回调）、生命周期守卫与视图程序化构建都在这里，
 * UI 侧仅通过 {@link Host} 最小回调面提供预览容器与线程调度，使 Activity 只保留浏览编排。
 *
 * 行为契约（重构维护，2026-10-02）：文案、回调顺序与拆分前逐字一致；
 * 所有文案改动必须视为行为变更。
 */
public class PreviewPaneHelper {

    /** 宿主（FileBrowserActivity）向 helper 暴露的最小回调面。 */
    public interface Host {
        /** Activity 仍存活（!isFinishing() && !isDestroyed()）。 */
        boolean isAlive();

        /** 右栏预览容器（layout-w600dp），普通纵向布局返回 null。 */
        FrameLayout previewPane();

        /** 等价原 Activity.runOnUiThread（保持同线程即时执行语义）。 */
        void runOnUiThread(Runnable r);

        /** 预览 pane 工具栏「打开」被点击（#37：预览是只读子集，全量能力经此进入对应查看器）。 */
        void onPreviewOpenRequested(RemoteFile file);
    }

    /** 与拆分前一致的 logcat tag（原 FileBrowserActivity）。 */
    private static final String TAG = "FileBrowserActivity";

    private final Context context;
    private final Host host;
    private final SshManager sshManager = SshManager.getInstance();

    private Markwon previewMarkwon;

    /** 当前预览文件的远端绝对路径（#9 内嵌图片相对路径解析基座）。 */
    private String previewBasePath;

    public PreviewPaneHelper(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    private Markwon getPreviewMarkwon() {
        if (previewMarkwon == null) {
            previewMarkwon = Markwon.builder(context)
                    // #9：注册 markdown-sftp 图片 handler（远端路径图片加载）
                    .usePlugin(ImagesPlugin.create(images -> images.addSchemeHandler(
                            SftpImageSchemeHandler.getInstance(context))))
                    .usePlugin(TablePlugin.create(context))
                    .usePlugin(TaskListPlugin.create(context))
                    .usePlugin(SftpImageSpanPlugin.create(() -> previewBasePath))
                    .build();
        }
        return previewMarkwon;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    /**
     * Loads a preview of {@code file} into the right pane.  Builds the pane
     * view programmatically: a title bar (file name) plus a content area that
     * hosts a type-specific renderer.
     */
    public void loadPreviewInPane(RemoteFile file) {
        FrameLayout pane = host.previewPane();
        if (pane == null) return;
        if (!host.isAlive()) return;

        previewBasePath = file.getPath();
        pane.removeAllViews();

        boolean supported = file.isMarkdown() || file.isCsv() || file.isCodeFile()
                || file.isImageFile() || file.isTextFile();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0D0D0D);
        pane.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Use a Toolbar to match the left pane's ?attr/actionBarSize exactly
        com.google.android.material.appbar.MaterialToolbar previewBar =
                new com.google.android.material.appbar.MaterialToolbar(context);
        previewBar.setTitle(file.getName());
        previewBar.setTitleTextColor(0xFFE0E0E0);
        previewBar.setSubtitleTextColor(0xFFA0A0A0);
        previewBar.setBackgroundColor(0xFF1E1E1E);
        // Use wrap_content + minHeight so subtitle (file path) is not clipped on short screens
        TypedValue tv = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true);
        int actionBarHeight = TypedValue.complexToDimensionPixelSize(tv.data, context.getResources().getDisplayMetrics());
        previewBar.setMinimumHeight(actionBarHeight);
        root.addView(previewBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(context);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        if (!supported) {
            showPreviewMessage(body, context.getString(R.string.preview_unsupported));
            return;
        }

        // #37：预览 pane「打开」出口——预览为只读子集，全量查看器能力（批注/编辑/大纲/
        // 查找/复制路径/缩放保存等）只能经此进入；不支持类型 no-op（按钮不铺出）。
        if (OpenFileHelper.detectViewerKind(file.getPath()) != null) {
            previewBar.inflateMenu(R.menu.menu_preview_pane);
            previewBar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_preview_open) {
                    host.onPreviewOpenRequested(file);
                    return true;
                }
                return false;
            });
        }

        ProgressBar pb = new ProgressBar(context);
        body.addView(pb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        if (file.isImageFile()) {
            sshManager.readFileBytes(file.getPath(), new SshManager.FileBytesCallback() {
                @Override
                public void onSuccess(byte[] bytes) {
                    host.runOnUiThread(() -> {
                        if (!host.isAlive()) return;
                        pb.setVisibility(View.GONE);
                        showImagePreview(body, bytes);
                    });
                }
                @Override
                public void onError(String message) {
                    host.runOnUiThread(() -> {
                        if (!host.isAlive()) return;
                        pb.setVisibility(View.GONE);
                        showPreviewMessage(body, "加载失败: " + message);
                    });
                }
            });
        } else {
            sshManager.readFile(file.getPath(), new SshManager.FileContentCallback() {
                @Override
                public void onSuccess(String content) {
                    if (file.isCodeFile()) {
                        // highlight off the main thread, then apply
                        new Thread(() -> {
                            SpannableStringBuilder highlighted;
                            try {
                                highlighted = CodeHighlighter.highlight(
                                        content, file.getName());
                            } catch (Throwable t) {
                                Log.w(TAG, "代码高亮失败（回退纯文本）: " + file.getName(), t);
                                highlighted = new SpannableStringBuilder(content);
                            }
                            SpannableStringBuilder result = highlighted;
                            host.runOnUiThread(() -> {
                                if (!host.isAlive()) return;
                                pb.setVisibility(View.GONE);
                                showCodePreview(body, content, result);
                            });
                        }, "preview-highlight").start();
                    } else {
                        host.runOnUiThread(() -> {
                            if (!host.isAlive()) return;
                            pb.setVisibility(View.GONE);
                            if (file.isMarkdown()) {
                                showMarkdownPreview(body, content);
                            } else if (file.isCsv()) {
                                showCsvPreview(body, content);
                            } else {
                                showTextPreview(body, content);
                            }
                        });
                    }
                }
                @Override
                public void onError(String message) {
                    host.runOnUiThread(() -> {
                        if (!host.isAlive()) return;
                        pb.setVisibility(View.GONE);
                        showPreviewMessage(body, "加载失败: " + message);
                    });
                }
            });
        }
    }

    private void showPreviewMessage(FrameLayout body, String message) {
        TextView tv = new TextView(context);
        tv.setText(message);
        tv.setTextColor(0xFF666666);
        tv.setTextSize(16f);
        tv.setGravity(Gravity.CENTER);
        body.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
    }

    private void showMarkdownPreview(FrameLayout body, String content) {
        ScrollView sv = new ScrollView(context);
        TextView tv = new TextView(context);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        tv.setTextColor(0xFFE0E0E0);
        tv.setLineSpacing(0, 1.5f);
        sv.addView(tv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getPreviewMarkwon().setMarkdown(tv, content);
    }

    private void showTextPreview(FrameLayout body, String content) {
        ScrollView sv = new ScrollView(context);
        TextView tv = new TextView(context);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        tv.setTextColor(0xFFE0E0E0);
        tv.setLineSpacing(0, 1.4f);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        tv.setText(content);
        sv.addView(tv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /**
     * Code preview: line-number column + highlighted code, fixed 14sp,
     * horizontally and vertically scrollable (same structure as the
     * full-screen CodeViewerActivity, without pinch zoom).
     */
    private void showCodePreview(FrameLayout body, String rawContent,
                                  SpannableStringBuilder highlighted) {
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setFillViewport(true);
        ScrollView sv = new ScrollView(context);
        sv.setFillViewport(true);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView lineNums = new TextView(context);
        lineNums.setTypeface(Typeface.MONOSPACE);
        lineNums.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        lineNums.setTextColor(0xFF666666);
        lineNums.setBackgroundColor(0xFF1E1E1E);
        lineNums.setPadding(dp(8), dp(8), dp(8), dp(8));
        lineNums.setGravity(Gravity.END);
        lineNums.setMinWidth(dp(48));
        String[] lines = rawContent.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= lines.length; i++) sb.append(i).append('\n');
        lineNums.setText(sb);
        row.addView(lineNums, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView code = new TextView(context);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        code.setTextColor(0xFFE0E0E0);
        code.setLineSpacing(0, 1.4f);
        code.setTextIsSelectable(true);
        code.setPadding(dp(8), dp(8), dp(8), dp(8));
        code.setText(highlighted);
        row.addView(code, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sv.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        hsv.addView(sv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(hsv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showImagePreview(FrameLayout body, byte[] bytes) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);
        int imgW = opts.outWidth, imgH = opts.outHeight;

        if (imgW <= 0 || imgH <= 0) {
            showPreviewMessage(body, "无法解码图片");
            return;
        }

        int maxDim = 2048, sample = 1;
        while (imgW / sample > maxDim || imgH / sample > maxDim) sample *= 2;

        BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
        decodeOpts.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOpts);

        if (bitmap == null) {
            showPreviewMessage(body, "图片解码失败");
            return;
        }

        // EXIF 方向修正（#42）：与图片查看器/Markdown 内嵌图同一语义——无方向标签
        // 的图零开销，带标签的竖拍照片按角度旋转，失败静默回退原图。
        bitmap = ImageExifHelper.rotateBitmap(bitmap,
                ImageExifHelper.orientationDegrees(bytes));

        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setImageBitmap(bitmap);
        body.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void showCsvPreview(FrameLayout body, String content) {
        ScrollView sv = new ScrollView(context);
        TextView tv = new TextView(context);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tv.setTextColor(0xFFE0E0E0);
        tv.setPadding(dp(16), dp(16), dp(16), dp(16));
        tv.setText(formatCsvPreview(content));
        sv.addView(tv, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** Formats CSV content as a monospace aligned table for preview. */
    static String formatCsvPreview(String content) {
        List<List<String>> rows = new ArrayList<>();
        for (String line : content.split("\n")) {
            if (line.trim().isEmpty()) continue;
            rows.add(parseCsvLine(line));
        }
        if (rows.isEmpty()) return "";

        int cols = 0;
        for (List<String> r : rows) cols = Math.max(cols, r.size());

        int[] widths = new int[cols];
        for (List<String> r : rows) {
            for (int i = 0; i < r.size(); i++) {
                widths[i] = Math.max(widths[i], r.get(i).length());
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> r = rows.get(rowIndex);
            for (int i = 0; i < r.size(); i++) {
                String cell = r.get(i);
                sb.append(cell);
                if (i < r.size() - 1) {
                    int pad = widths[i] - cell.length() + 2;
                    for (int p = 0; p < pad; p++) sb.append(' ');
                }
            }
            sb.append('\n');
            // separator line under the header row
            if (rowIndex == 0) {
                for (int i = 0; i < cols; i++) {
                    for (int p = 0; p < widths[i] + 2; p++) sb.append('-');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /** Parses one CSV line: quoted cells, escaped quotes (""), comma outside quotes. */
    static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields;
    }
}
