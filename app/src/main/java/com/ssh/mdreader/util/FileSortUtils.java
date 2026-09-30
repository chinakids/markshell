package com.ssh.mdreader.util;

import com.ssh.mdreader.model.RemoteFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 文件列表排序策略（纯函数，可单测）：目录恒排在文件前；
 * 各组内按所选模式排序：名称（忽略大小写升序）/ 修改时间（新在前）/ 大小（大在前）。
 * 排序不改动入参列表，返回新列表。
 */
public final class FileSortUtils {

    public static final int SORT_NAME = 0;
    public static final int SORT_MTIME = 1;
    public static final int SORT_SIZE = 2;

    private FileSortUtils() {
    }

    public static List<RemoteFile> sort(List<RemoteFile> files, int mode) {
        List<RemoteFile> sorted = new ArrayList<>(files);
        Collections.sort(sorted, comparator(mode));
        return sorted;
    }

    public static Comparator<RemoteFile> comparator(int mode) {
        switch (mode) {
            case SORT_MTIME:
                return (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    int c = Long.compare(b.getMtime(), a.getMtime());
                    return c != 0 ? c : a.getName().compareToIgnoreCase(b.getName());
                };
            case SORT_SIZE:
                return (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    int c = Long.compare(b.getSize(), a.getSize());
                    return c != 0 ? c : a.getName().compareToIgnoreCase(b.getName());
                };
            case SORT_NAME:
            default:
                return (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) {
                        return a.isDirectory() ? -1 : 1;
                    }
                    return a.getName().compareToIgnoreCase(b.getName());
                };
        }
    }
}
