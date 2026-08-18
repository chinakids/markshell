package com.ssh.mdreader.model;

import java.util.ArrayList;
import java.util.List;

public class RemoteFile {
    private final String name;
    private final String path;
    private final boolean directory;
    private final long size;
    private final int permissions;

    private int depth;
    private boolean expanded;
    private boolean childrenLoaded;
    private final List<RemoteFile> children = new ArrayList<>();

    public RemoteFile(String name, String path, boolean directory, long size, int permissions) {
        this.name = name;
        this.path = path;
        this.directory = directory;
        this.size = size;
        this.permissions = permissions;
    }

    public String getName() { return name; }
    public String getPath() { return path; }
    public boolean isDirectory() { return directory; }
    public long getSize() { return size; }
    public int getPermissions() { return permissions; }

    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }

    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }

    public boolean isChildrenLoaded() { return childrenLoaded; }
    public void setChildrenLoaded(boolean childrenLoaded) { this.childrenLoaded = childrenLoaded; }

    public List<RemoteFile> getChildren() { return children; }
    public void setChildren(List<RemoteFile> newChildren) {
        children.clear();
        for (RemoteFile child : newChildren) {
            child.setDepth(this.depth + 1);
            children.add(child);
        }
        childrenLoaded = true;
    }

    public boolean isMarkdown() {
        if (directory) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".mdown");
    }

    public boolean isCsv() {
        if (directory) return false;
        return name.toLowerCase().endsWith(".csv");
    }

    public boolean isCodeFile() {
        if (directory) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".json")
                || lower.endsWith(".py")
                || lower.endsWith(".js") || lower.endsWith(".mjs")
                || lower.endsWith(".ts")
                || lower.endsWith(".jsx") || lower.endsWith(".tsx")
                || lower.endsWith(".html") || lower.endsWith(".htm")
                || lower.endsWith(".css")
                || lower.endsWith(".java")
                || lower.endsWith(".xml") || lower.endsWith(".svg");
    }

    public boolean isImageFile() {
        if (directory) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }

    public boolean isViewable() {
        return isMarkdown() || isCsv() || isCodeFile() || isImageFile();
    }

    public String getFormattedSize() {
        if (directory) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
}
