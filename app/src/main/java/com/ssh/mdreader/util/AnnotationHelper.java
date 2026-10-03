package com.ssh.mdreader.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ssh.mdreader.model.AnnotationEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for the annotation system.
 *
 * <p>The new scheme stores annotations in a CSV file only.  The Markdown source
 * file is <em>never modified</em>.  At render time each annotation is located
 * by finding the {@code occurrenceIndex}-th occurrence of {@code originalText}
 * in the rendered TextView output.</p>
 *
 * <p>CSV format (four columns):<br>
 * {@code "id","批注内容","原文片段","出现序号"}</p>
 */
public class AnnotationHelper {

    private static final String TAG = "AnnotationHelper";

    /** CSV header written as the first line of every annotation file. */
    public static final String CSV_HEADER = "\"id\",\"批注内容\",\"原文片段\",\"出现序号\"";

    private AnnotationHelper() {}

    // ── File path ─────────────────────────────────────────────────────────────

    /** Derives the annotation CSV path from the original Markdown file path. */
    @NonNull
    public static String buildAnnotationFilePath(@NonNull String mdFilePath) {
        int lastSlash = Math.max(mdFilePath.lastIndexOf('/'), mdFilePath.lastIndexOf('\\'));
        String dir      = lastSlash >= 0 ? mdFilePath.substring(0, lastSlash + 1) : "";
        String fileName = lastSlash >= 0 ? mdFilePath.substring(lastSlash + 1)    : mdFilePath;
        int dotIdx = fileName.lastIndexOf('.');
        String baseName = dotIdx >= 0 ? fileName.substring(0, dotIdx) : fileName;
        return dir + baseName + "_批注.csv";
    }

    // ── Occurrence helpers ────────────────────────────────────────────────────

    /**
     * Counts how many times {@code needle} appears in {@code text} strictly
     * before index {@code beforeIndex}.
     *
     * @param text        the text to search in
     * @param needle      the substring to count
     * @param beforeIndex exclusive upper bound in {@code text}
     * @return number of non-overlapping occurrences before {@code beforeIndex}
     */
    public static int countOccurrencesBefore(@NonNull String text,
                                              @NonNull String needle,
                                              int beforeIndex) {
        if (needle.isEmpty() || beforeIndex <= 0) return 0;
        int count = 0;
        int from  = 0;
        int limit = Math.min(beforeIndex, text.length());
        while (from < limit) {
            int idx = text.indexOf(needle, from);
            if (idx < 0 || idx >= limit) break;
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    /**
     * Finds the start index of the {@code n}-th (0-based) occurrence of
     * {@code needle} in {@code text}.
     *
     * @return the start index, or {@code -1} if fewer than {@code n+1}
     *         occurrences exist
     */
    public static int findNthOccurrence(@NonNull String text,
                                         @NonNull String needle,
                                         int n) {
        if (needle.isEmpty() || n < 0) return -1;
        int from  = 0;
        int count = 0;
        while (from <= text.length()) {
            int idx = text.indexOf(needle, from);
            if (idx < 0) return -1;
            if (count == n) return idx;
            count++;
            from = idx + needle.length();
        }
        return -1;
    }

    // ── Annotation file serialisation / parsing ───────────────────────────────

    /**
     * Parses the annotation CSV content into entries.
     * Skips the header row and malformed lines.
     * Compatible with legacy three-column files (occurrenceIndex defaults to 0).
     */
    @NonNull
    public static List<AnnotationEntry> parseAnnotationFile(@NonNull String content) {
        List<AnnotationEntry> list = new ArrayList<>();
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) continue;
            if (i == 0 && isHeaderRow(trimmed)) continue;
            AnnotationEntry entry = AnnotationEntry.parse(trimmed);
            if (entry != null) {
                list.add(entry);
            } else if (!isLegacyFormatLine(trimmed)) {
                // 既不是遗留格式也不是空行 → 数据损坏，记日志便于诊断
                Log.w(TAG, "跳过无法解析的批注行（第 " + (i + 1) + " 行）: " + trimmed);
            }
        }
        return list;
    }

    private static boolean isHeaderRow(@NonNull String line) {
        String norm = line.toLowerCase().replaceAll("[\"\\s]", "");
        return norm.startsWith("id,") || norm.equals("id");
    }

    /** True for legacy line/col format rows (e.g. "L3:5-…") skipped by design. */
    private static boolean isLegacyFormatLine(@NonNull String line) {
        return line.startsWith("L") && line.contains(":");
    }

    /** Serialises the annotations list to CSV with a header on the first line. */
    @NonNull
    public static String formatAnnotationFile(@NonNull List<AnnotationEntry> annotations) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER).append("\n");
        for (AnnotationEntry a : annotations) {
            sb.append(a.format()).append("\n");
        }
        return sb.toString();
    }

    // ── ID generation ─────────────────────────────────────────────────────────

    /** Generates a short unique ID as a base-36 timestamp string. */
    @NonNull
    public static String generateId() {
        return Long.toString(System.currentTimeMillis(), 36);
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /** 批注导出格式（纯函数 {@link #buildExportText}，无 Android 依赖）。 */
    public enum ExportFormat { TEXT, HTML, MARKDOWN }

    /**
     * 由批注 CSV 路径反推原 Markdown 文件路径（仅用于导出时展示来源；不校验存在性）。
     * 与 {@link #buildAnnotationFilePath} 互逆。
     */
    @NonNull
    public static String buildMarkdownFilePath(@NonNull String annotationCsvPath) {
        final String suffix = "_批注.csv";
        if (annotationCsvPath.endsWith(suffix)) {
            return annotationCsvPath.substring(0, annotationCsvPath.length() - suffix.length()) + ".md";
        }
        return annotationCsvPath;
    }

    /** 导出批注为文本；来源名省略。 */
    @NonNull
    public static String buildExportText(@NonNull List<AnnotationEntry> annotations,
                                         @NonNull ExportFormat format) {
        return buildExportText(annotations, null, format);
    }

    /**
     * 导出批注为指定格式的纯文本内容（分享/复制用）。
     *
     * <ul>
     *   <li>{@link ExportFormat#TEXT}：纯文本报告，每条含「原文（第 N 次出现）」与批注；</li>
     *   <li>{@link ExportFormat#HTML}：独立 {@code <blockquote>} 批注块（内容做 HTML 转义）；</li>
     *   <li>{@link ExportFormat#MARKDOWN}：{@code > **批注于 <path>**：原文"…" → 批注"…"} 附录行。</li>
     * </ul>
     *
     * <p>不修改源文档；位置以 {@link AnnotationEntry#occurrenceIndex} 的出现序号标注。
     * 条目文本中的换行会被规范化为字面 {@code \n}，避免破坏导出格式。</p>
     */
    @NonNull
    public static String buildExportText(@NonNull List<AnnotationEntry> annotations,
                                         @Nullable String sourceName,
                                         @NonNull ExportFormat format) {
        switch (format) {
            case TEXT:     return buildTextExport(annotations, sourceName);
            case HTML:     return buildHtmlExport(annotations, sourceName);
            case MARKDOWN: return buildMarkdownExport(annotations, sourceName);
        }
        throw new IllegalArgumentException("未知导出格式: " + format);
    }

    private static String buildTextExport(@NonNull List<AnnotationEntry> annotations,
                                          @Nullable String sourceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("批注导出（共 ").append(annotations.size()).append(" 条）\n");
        if (sourceName != null && !sourceName.isEmpty()) {
            sb.append("来源：").append(sourceName).append('\n');
        }
        sb.append("================================\n");
        int index = 1;
        for (AnnotationEntry a : annotations) {
            sb.append('\n').append('[').append(index).append("] 原文（第 ")
                    .append(a.occurrenceIndex + 1).append(" 次出现）：「")
                    .append(normalizeForExport(a.originalText)).append("」\n")
                    .append("    批注：「").append(normalizeForExport(a.text)).append("」\n");
            index++;
        }
        return sb.toString();
    }

    private static String buildHtmlExport(@NonNull List<AnnotationEntry> annotations,
                                          @Nullable String sourceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- 批注导出（共 ").append(annotations.size()).append(" 条）");
        if (sourceName != null && !sourceName.isEmpty()) {
            sb.append("；来源：").append(escapeHtml(sourceName));
        }
        sb.append(" -->\n");
        int index = 1;
        for (AnnotationEntry a : annotations) {
            sb.append("<blockquote>\n")
                    .append("<p><strong>批注 ").append(index).append("</strong>：")
                    .append(escapeHtml(normalizeForExport(a.text))).append("</p>\n")
                    .append("<p>原文（第 ").append(a.occurrenceIndex + 1).append(" 次出现）：")
                    .append(escapeHtml(normalizeForExport(a.originalText))).append("</p>\n")
                    .append("</blockquote>\n");
            index++;
        }
        return sb.toString();
    }

    private static String buildMarkdownExport(@NonNull List<AnnotationEntry> annotations,
                                              @Nullable String sourceName) {
        String where = (sourceName != null && !sourceName.isEmpty())
                ? "批注于 " + sourceName : "批注";
        StringBuilder sb = new StringBuilder();
        for (AnnotationEntry a : annotations) {
            sb.append("> **").append(where).append("**：原文「")
                    .append(normalizeForExport(a.originalText)).append("」 → 批注「")
                    .append(normalizeForExport(a.text)).append("」\n");
        }
        return sb.toString();
    }

    /** 条目文本规范化：统一换行为字面 {@code \n}（两字符），防止破坏导出结构。 */
    @NonNull
    private static String normalizeForExport(@Nullable String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }

    /** HTML 转义 &lt;&gt;&amp; 与引号，供 HTML 导出使用。 */
    @NonNull
    private static String escapeHtml(@NonNull String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
