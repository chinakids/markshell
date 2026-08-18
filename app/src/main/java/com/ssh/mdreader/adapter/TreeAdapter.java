package com.ssh.mdreader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.RemoteFile;

import java.util.ArrayList;
import java.util.List;

public class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.ViewHolder> {

    public interface OnFileActionListener {
        void onDirectoryExpand(RemoteFile dir, ExpandCallback callback);
        void onFileClick(RemoteFile file);
        void onFileLongClick(RemoteFile file);
        void onDirectoryLongClick(RemoteFile dir);
    }

    public interface ExpandCallback {
        void onLoaded(List<RemoteFile> children);
        void onError(String message);
    }

    private final List<RemoteFile> flatList = new ArrayList<>();
    private final List<RemoteFile> rootFiles = new ArrayList<>();
    private OnFileActionListener listener;
    private boolean showHidden = false;

    public void setOnFileActionListener(OnFileActionListener listener) {
        this.listener = listener;
    }

    /**
     * Removes a file (by path) from the internal tree and refreshes the list.
     * Works for files inside expanded subdirectories too.
     */
    public void removeFile(String path) {
        if (removeFromList(rootFiles, path)) {
            rebuildFlatList();
        }
    }

    private boolean removeFromList(List<RemoteFile> nodes, String path) {
        for (int i = 0; i < nodes.size(); i++) {
            RemoteFile node = nodes.get(i);
            if (node.getPath().equals(path)) {
                nodes.remove(i);
                return true;
            }
            if (node.isDirectory() && node.isChildrenLoaded()) {
                if (removeFromList(node.getChildren(), path)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setShowHidden(boolean showHidden) {
        this.showHidden = showHidden;
        rebuildFlatList();
    }

    public boolean isShowHidden() {
        return showHidden;
    }

    /**
     * Replaces root files while preserving expanded state and loaded children
     * of directories that still exist (matched by path).
     */
    public void setFiles(List<RemoteFile> files) {
        // Build a map of old expanded dirs by path
        java.util.Map<String, RemoteFile> oldMap = new java.util.HashMap<>();
        collectExpandedDirs(rootFiles, oldMap);

        rootFiles.clear();
        for (RemoteFile f : files) {
            f.setDepth(0);
            RemoteFile old = oldMap.get(f.getPath());
            if (old != null && f.isDirectory()) {
                // Preserve expanded state and children
                f.setExpanded(old.isExpanded());
                f.setChildrenLoaded(old.isChildrenLoaded());
                f.getChildren().clear();
                for (RemoteFile child : old.getChildren()) {
                    child.setDepth(1);
                    f.getChildren().add(child);
                }
            } else {
                f.setExpanded(false);
                f.setChildrenLoaded(false);
                f.getChildren().clear();
            }
        }
        rootFiles.addAll(files);
        rebuildFlatList();
    }

    /** Recursively collects all expanded directories by path. */
    private void collectExpandedDirs(List<RemoteFile> nodes, java.util.Map<String, RemoteFile> map) {
        for (RemoteFile node : nodes) {
            if (node.isDirectory() && node.isExpanded()) {
                map.put(node.getPath(), node);
                collectExpandedDirs(node.getChildren(), map);
            }
        }
    }

    private void rebuildFlatList() {
        flatList.clear();
        for (RemoteFile root : rootFiles) {
            flattenNode(root);
        }
        notifyDataSetChanged();
    }

    private void flattenNode(RemoteFile node) {
        if (!showHidden && isHidden(node)) return;
        flatList.add(node);
        if (node.isDirectory() && node.isExpanded()) {
            for (RemoteFile child : node.getChildren()) {
                flattenNode(child);
            }
        }
    }

    private boolean isHidden(RemoteFile file) {
        return file.getName().startsWith(".");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RemoteFile file = flatList.get(position);

        int indentPx = (int) (file.getDepth() * 24 * holder.itemView.getResources()
                .getDisplayMetrics().density);
        holder.itemRoot.setPadding(
                indentPx + (int) (16 * holder.itemView.getResources().getDisplayMetrics().density),
                (int) (12 * holder.itemView.getResources().getDisplayMetrics().density),
                (int) (16 * holder.itemView.getResources().getDisplayMetrics().density),
                (int) (12 * holder.itemView.getResources().getDisplayMetrics().density)
        );

        holder.tvName.setText(file.getName());

        if (file.isDirectory()) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
            holder.tvInfo.setText("文件夹");
            holder.ivExpand.setVisibility(View.VISIBLE);

            if (file.isExpanded()) {
                holder.ivExpand.setImageResource(R.drawable.ic_expand_more);
            } else {
                holder.ivExpand.setImageResource(R.drawable.ic_chevron_right);
            }

            holder.itemView.setOnClickListener(v -> toggleDirectory(file, holder));
            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onDirectoryLongClick(file);
                return true;
            });
        } else {
            if (file.isMarkdown()) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_md);
            } else if (file.isCsv()) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_csv);
            } else if (file.isCodeFile()) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_code);
            } else if (file.isImageFile()) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_image);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_file_unsupported);
            }
            holder.tvInfo.setText(file.getFormattedSize());
            holder.ivExpand.setVisibility(View.INVISIBLE);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onFileClick(file);
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) listener.onFileLongClick(file);
                return true;
            });
        }
    }

    private void toggleDirectory(RemoteFile dir, ViewHolder holder) {
        if (dir.isExpanded()) {
            dir.setExpanded(false);
            rebuildFlatList();
            return;
        }

        if (dir.isChildrenLoaded()) {
            dir.setExpanded(true);
            rebuildFlatList();
            return;
        }

        holder.tvInfo.setText("加载中…");

        if (listener != null) {
            listener.onDirectoryExpand(dir, new ExpandCallback() {
                @Override
                public void onLoaded(List<RemoteFile> children) {
                    dir.setChildren(children);
                    dir.setExpanded(true);
                    rebuildFlatList();
                }

                @Override
                public void onError(String message) {
                    holder.tvInfo.setText("加载失败，点击重试");
                    holder.itemView.setOnClickListener(v -> toggleDirectory(dir, holder));
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return flatList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout itemRoot;
        final ImageView ivExpand;
        final ImageView ivIcon;
        final TextView tvName;
        final TextView tvInfo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemRoot = itemView.findViewById(R.id.item_root);
            ivExpand = itemView.findViewById(R.id.iv_expand);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            tvName = itemView.findViewById(R.id.tv_name);
            tvInfo = itemView.findViewById(R.id.tv_info);
        }
    }
}
