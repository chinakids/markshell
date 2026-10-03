package com.ssh.mdreader.adapter;

import android.graphics.drawable.Drawable;
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
import com.ssh.mdreader.util.FileFilterHelper;
import com.ssh.mdreader.util.FileSortUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.ViewHolder> {

    public interface OnFileActionListener {
        void onDirectoryExpand(RemoteFile dir, ExpandCallback callback);
        void onFileClick(RemoteFile file);
        void onFileLongClick(RemoteFile file);
        void onDirectoryLongClick(RemoteFile dir);
    }

    /** 多选状态变化回调（选中项增删/模式进出）——主线程调用，UI 据此刷新标题与操作栏。 */
    public interface OnSelectionListener {
        void onSelectionChanged();
    }

    public interface ExpandCallback {
        void onLoaded(List<RemoteFile> children);
        void onError(String message);
    }

    private final List<RemoteFile> flatList = new ArrayList<>();
    private final List<RemoteFile> rootFiles = new ArrayList<>();
    private OnFileActionListener listener;
    private OnSelectionListener selectionListener;
    private boolean showHidden = false;

    /** 名称过滤查询串（null/空=不过滤）——覆盖根列表与已展开子树。 */
    private String filterQuery = "";

    /** 多选模式开关与已选路径集合（path 为键，节点重建后仍可匹配）。 */
    private boolean selectionMode = false;
    private final Set<String> selectedPaths = new LinkedHashSet<>();

    public void setOnFileActionListener(OnFileActionListener listener) {
        this.listener = listener;
    }

    public void setOnSelectionListener(OnSelectionListener listener) {
        this.selectionListener = listener;
    }

    // ── 多选模式 ────────────────────────────────────────────────────────────

    public boolean isSelectionMode() {
        return selectionMode;
    }

    /** 进入多选模式并选中 {@code file}（首个选中项）。 */
    public void enterSelectionMode(RemoteFile file) {
        selectionMode = true;
        selectedPaths.clear();
        selectedPaths.add(file.getPath());
        rebuildFlatList();
        notifySelectionChanged();
    }

    /** 切换 {@code file} 的选中状态；最后一个选中项被取消时自动退出多选模式。 */
    public void toggleSelection(RemoteFile file) {
        if (!selectionMode) return;
        if (selectedPaths.contains(file.getPath())) {
            selectedPaths.remove(file.getPath());
            if (selectedPaths.isEmpty()) {
                selectionMode = false;
            }
        } else {
            selectedPaths.add(file.getPath());
        }
        rebuildFlatList();
        notifySelectionChanged();
    }

    /** 多选模式下选中当前可见列表的全部条目（含已展开子树）。 */
    public void selectAllVisible() {
        if (!selectionMode) return;
        selectedPaths.clear();
        for (RemoteFile f : flatList) {
            selectedPaths.add(f.getPath());
        }
        rebuildFlatList();
        notifySelectionChanged();
    }

    /** 退出多选模式并清空所有选中。 */
    public void exitSelectionMode() {
        if (!selectionMode && selectedPaths.isEmpty()) return;
        selectionMode = false;
        selectedPaths.clear();
        rebuildFlatList();
        notifySelectionChanged();
    }

    public int getSelectedCount() {
        return selectedPaths.size();
    }

    public List<String> getSelectedPaths() {
        return new ArrayList<>(selectedPaths);
    }

    /** 返回当前选中的节点列表（按选中顺序，可能含已隐藏之外的任意层节点）。 */
    public List<RemoteFile> getSelectedFiles() {
        List<RemoteFile> result = new ArrayList<>();
        for (RemoteFile f : flatList) {
            if (selectedPaths.contains(f.getPath())) {
                result.add(f);
            }
        }
        return result;
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged();
        }
    }

    /**
     * Removes several files (by path) from the internal tree at once and
     * refreshes the list once. Works for files inside expanded subdirectories too.
     */
    public void removeFiles(List<String> paths) {
        boolean changed = false;
        for (String path : paths) {
            if (removeFromList(rootFiles, path)) {
                changed = true;
            }
        }
        if (changed) {
            rebuildFlatList();
        }
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

    /**
     * Renames a node (by old path) in the internal tree and refreshes the list.
     * Directory nodes keep their expanded state, already-loaded children and
     * have all descendant paths rewritten to the new prefix.
     */
    public void renameFile(String oldPath, String newName) {
        if (renameInList(rootFiles, oldPath, newName)) {
            rebuildFlatList();
        }
    }

    private boolean renameInList(List<RemoteFile> nodes, String oldPath, String newName) {
        for (int i = 0; i < nodes.size(); i++) {
            RemoteFile node = nodes.get(i);
            if (node.getPath().equals(oldPath)) {
                int slash = oldPath.lastIndexOf('/');
                String newPath = slash < 0 ? newName : oldPath.substring(0, slash + 1) + newName;
                nodes.set(i, copyNode(node, newName, newPath));
                return true;
            }
            if (node.isDirectory() && node.isChildrenLoaded()) {
                if (renameInList(node.getChildren(), oldPath, newName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Builds a deep copy of {@code node} named {@code newName} at {@code newPath},
     * preserving depth/expanded/childrenLoaded and rewriting every descendant
     * path to the new prefix (目录改名后子树仍可导航). Package-private for unit tests.
     */
    static RemoteFile copyNode(RemoteFile node, String newName, String newPath) {
        RemoteFile copy = new RemoteFile(newName, newPath, node.isDirectory(),
                node.getSize(), node.getPermissions(), node.getMtime());
        copy.setDepth(node.getDepth());
        copy.setExpanded(node.isExpanded());
        copy.setChildrenLoaded(node.isChildrenLoaded());
        String oldDir = node.getPath() + "/";
        String newDir = newPath + "/";
        for (RemoteFile child : node.getChildren()) {
            if (child.getPath().startsWith(oldDir)) {
                String childPath = newDir + child.getPath().substring(oldDir.length());
                copy.getChildren().add(copyNode(child, child.getName(), childPath));
            } else {
                copy.getChildren().add(child);
            }
        }
        return copy;
    }

    /** Recursively removes {@code path} from {@code nodes}, descending into loaded
     *  directories. Package-private (pure logic, no instance state) for unit tests. */
    static boolean removeFromList(List<RemoteFile> nodes, String path) {
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

    // ── 名称过滤（第三轮能力发现） ─────────────────────────────────────────────

    /**
     * 设置名称过滤查询串（null/空=清除过滤）。仅作用于内存中已加载的树
     * （根列表+已展开子目录），不触发 SFTP。生效中若正处于多选模式则自动退出
     * 多选——避免「已选文件被过滤隐藏后批量操作漏项」的语义陷阱
     * （{@link #getSelectedFiles()} 只返回当前可见节点）。
     */
    public void setFilterQuery(String query) {
        this.filterQuery = FileFilterHelper.normalize(query);
        if (isFilterActive() && selectionMode) {
            selectionMode = false;
            selectedPaths.clear();
            notifySelectionChanged();
        }
        rebuildFlatList();
    }

    /** 是否有生效中的过滤。 */
    public boolean isFilterActive() {
        return FileFilterHelper.active(filterQuery);
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
     * of directories that still exist (matched by path). Convenience variant
     * defaulting to name order; prefer {@link #setFiles(List, int)} when the
     * caller knows the active sort mode.
     */
    public void setFiles(List<RemoteFile> files) {
        setFiles(files, FileSortUtils.SORT_NAME);
    }

    /**
     * Replaces root files (supplied already sorted by {@code sortMode}) while
     * preserving expanded state and loaded children of directories that still
     * exist (matched by path); preserved subtrees are re-sorted with the same
     * mode so expanded directories stay consistent with the main list.
     */
    public void setFiles(List<RemoteFile> files, int sortMode) {
        // Build a map of old expanded dirs by path
        java.util.Map<String, RemoteFile> oldMap = new java.util.HashMap<>();
        collectExpandedDirs(rootFiles, oldMap);

        rootFiles.clear();
        applyExistingState(files, oldMap);
        rootFiles.addAll(files);
        sortLoadedChildren(rootFiles, sortMode);
        rebuildFlatList();
    }

    /** Recursively re-sorts the children of every loaded directory using
     *  {@code sortMode} (directories first, then per-mode ordering), so subtrees
     *  preserved by {@code setFiles} follow the active sort order.
     *  Package-private (pure logic, no instance state) for unit tests. */
    static void sortLoadedChildren(List<RemoteFile> nodes, int sortMode) {
        for (RemoteFile f : nodes) {
            if (f.isDirectory() && f.isChildrenLoaded()) {
                Collections.sort(f.getChildren(), FileSortUtils.comparator(sortMode));
                sortLoadedChildren(f.getChildren(), sortMode);
            }
        }
    }

    /**
     * Applies preserved expansion state from {@code oldMap} (keyed by path) onto each
     * new file: directories matched by path keep expanded/childrenLoaded and inherit
     * the old children (depth reset to 1); unmatched ones start collapsed.
     * Package-private (pure logic, no instance state) for unit tests.
     */
    static void applyExistingState(List<RemoteFile> files,
                                   java.util.Map<String, RemoteFile> oldMap) {
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
    }

    /** Recursively collects all expanded directories by path. Package-private
     *  (pure logic, no instance state) for unit tests. */
    static void collectExpandedDirs(List<RemoteFile> nodes,
                                    java.util.Map<String, RemoteFile> map) {
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
        notifyDataChanged();
    }

    /**
     * 转发 {@link #notifyDataSetChanged()}。包级可见以便单测子类覆写为 no-op：
     * JVM 单测用的是 mockable android.jar，RecyclerView.Adapter 内部观察者字段为 null，
     * 直接通知会 NPE；多选状态机测试不关心视图绘制，仅需验证状态。
     */
    void notifyDataChanged() {
        notifyDataSetChanged();
    }

    private void flattenNode(RemoteFile node) {
        if (!showHidden && isHidden(node)) return;
        if (isFilterActive() && !FileFilterHelper.matchesName(node.getName(), filterQuery)) return;
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
        boolean selected = selectionMode && selectedPaths.contains(file.getPath());

        int indentPx = (int) (file.getDepth() * 24 * holder.itemView.getResources()
                .getDisplayMetrics().density);
        holder.itemRoot.setPadding(
                indentPx + (int) (16 * holder.itemView.getResources().getDisplayMetrics().density),
                (int) (12 * holder.itemView.getResources().getDisplayMetrics().density),
                (int) (16 * holder.itemView.getResources().getDisplayMetrics().density),
                (int) (12 * holder.itemView.getResources().getDisplayMetrics().density)
        );

        holder.tvName.setText(file.getName());

        // 选中态视觉：整行高亮（主题 primary 约 15% 透明度）+ 勾选图标
        if (selected) {
            int base = holder.itemView.getContext().getColor(R.color.md_theme_primary);
            holder.itemView.setBackgroundColor((base & 0x00FFFFFF) | 0x26000000);
        } else {
            holder.itemView.setBackground(holder.defaultBackground);
        }
        holder.ivCheck.setVisibility(selectionMode
                ? (selected ? View.VISIBLE : View.INVISIBLE) : View.INVISIBLE);

        if (file.isDirectory()) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
            holder.tvInfo.setText("文件夹");
            // 多选模式下点击=选中而非展开，隐藏展开箭头避免误导
            holder.ivExpand.setVisibility(selectionMode ? View.INVISIBLE : View.VISIBLE);

            if (file.isExpanded()) {
                holder.ivExpand.setImageResource(R.drawable.ic_expand_more);
            } else {
                holder.ivExpand.setImageResource(R.drawable.ic_chevron_right);
            }

            holder.itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    toggleSelection(file);
                } else {
                    toggleDirectory(file, holder);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (selectionMode) return true; // 多选模式下不弹长按菜单
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
            } else if (file.isTextFile()) {
                holder.ivIcon.setImageResource(R.drawable.ic_file_txt);
            } else {
                holder.ivIcon.setImageResource(R.drawable.ic_file_unsupported);
            }
            holder.tvInfo.setText(file.getFormattedSize());
            holder.ivExpand.setVisibility(View.INVISIBLE);

            holder.itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    toggleSelection(file);
                } else if (listener != null) {
                    listener.onFileClick(file);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (selectionMode) return true;
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

    /** 当前可见列表第 {@code position} 项（含过滤/隐藏语义）；越界返回 null。 */
    public RemoteFile getFile(int position) {
        if (position < 0 || position >= flatList.size()) return null;
        return flatList.get(position);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout itemRoot;
        final ImageView ivExpand;
        final ImageView ivIcon;
        final ImageView ivCheck;
        final TextView tvName;
        final TextView tvInfo;
        /** 行默认背景（selectableItemBackground），取消选中时恢复。 */
        final Drawable defaultBackground;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            itemRoot = itemView.findViewById(R.id.item_root);
            ivExpand = itemView.findViewById(R.id.iv_expand);
            ivIcon = itemView.findViewById(R.id.iv_icon);
            ivCheck = itemView.findViewById(R.id.iv_check);
            tvName = itemView.findViewById(R.id.tv_name);
            tvInfo = itemView.findViewById(R.id.tv_info);
            defaultBackground = itemView.getBackground();
        }
    }
}
