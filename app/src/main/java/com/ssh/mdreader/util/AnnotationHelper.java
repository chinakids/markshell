package com.ssh.mdreader.util;

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
            if (entry != null) list.add(entry);
        }
        return list;
    }

    private static boolean isHeaderRow(@NonNull String line) {
        String norm = line.toLowerCase().replaceAll("[\"\\s]", "");
        return norm.startsWith("id,") || norm.equals("id");
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
}
