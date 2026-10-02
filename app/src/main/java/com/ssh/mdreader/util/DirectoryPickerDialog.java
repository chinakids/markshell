package com.ssh.mdreader.util;

import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.ssh.SshManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 目录选择对话框：用于把文件/目录「移动」或「复制」到其他目录。
 * 展示当前目标目录路径、子目录列表（异步加载，仅列目录）与「上级」按钮，
 * 底部确认按钮回调所选目标目录；调用方负责执行 renameFile/copyFile。
 * 确认前做同位置/自指校验（文案按移动/复制区分）。回调均在主线程（Dialog 自身生命周期内）。
 */
public class DirectoryPickerDialog extends Dialog {

    /** 用户在对话框中确认了目标目录（主线程回调）。 */
    public interface OnDirectoryPickedListener {
        void onPicked(String targetDir);
    }

    private final SshManager sshManager = SshManager.getInstance();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final String srcPath;
    private final String srcName;
    private final boolean srcIsDir;
    private final boolean copyMode;
    private final OnDirectoryPickedListener listener;

    private String currentDir;
    private TextView tvPath;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private DirectoryPickerDialog(@NonNull Context context,
                                  @NonNull String srcPath,
                                  @NonNull String srcName,
                                  boolean srcIsDir,
                                  boolean copyMode,
                                  @NonNull OnDirectoryPickedListener listener) {
        super(context, R.style.BrandDialog);
        this.srcPath = srcPath;
        this.srcName = srcName;
        this.srcIsDir = srcIsDir;
        this.copyMode = copyMode;
        this.listener = listener;
        this.currentDir = UiUtils.getParentPath(srcPath);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_directory_picker, null);
        setContentView(view);
        DialogHelper.applyDialogSize(this);

        ((TextView) view.findViewById(R.id.dialog_picker_title))
                .setText((copyMode ? "复制「" : "移动「") + srcName + "」到");
        tvPath = view.findViewById(R.id.dialog_picker_path);
        tvEmpty = view.findViewById(R.id.dialog_picker_empty);
        progressBar = view.findViewById(R.id.dialog_picker_progress);

        RecyclerView recycler = view.findViewById(R.id.dialog_picker_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);

        view.findViewById(R.id.dialog_picker_btn_parent)
                .setOnClickListener(v -> {
                    currentDir = UiUtils.getParentPath(currentDir);
                    loadDirs();
                });
        view.findViewById(R.id.dialog_picker_btn_negative)
                .setOnClickListener(v -> dismiss());
        view.findViewById(R.id.dialog_picker_btn_move)
                .setOnClickListener(v -> onConfirmPicked());
        ((TextView) view.findViewById(R.id.dialog_picker_btn_move))
                .setText(copyMode ? "复制到此" : "移动到此");

        setCancelable(true);
    }

    /** 弹出「移动」模式的对话框；初始目标目录 = 源所在目录（用户可直接「上级」或点选子目录）。返回实例供调用方管理生命周期。 */
    public static DirectoryPickerDialog show(@NonNull Context context,
                                             @NonNull String srcPath,
                                             @NonNull String srcName,
                                             boolean srcIsDir,
                                             @NonNull OnDirectoryPickedListener listener) {
        return show(context, srcPath, srcName, srcIsDir, false, listener);
    }

    /** 弹出「移动/复制」模式的对话框；{@code copyMode=true} 时为复制（标题/按钮/校验文案区分）。 */
    public static DirectoryPickerDialog show(@NonNull Context context,
                                             @NonNull String srcPath,
                                             @NonNull String srcName,
                                             boolean srcIsDir,
                                             boolean copyMode,
                                             @NonNull OnDirectoryPickedListener listener) {
        DirectoryPickerDialog dialog =
                new DirectoryPickerDialog(context, srcPath, srcName, srcIsDir, copyMode, listener);
        dialog.loadDirs();
        dialog.show();
        return dialog;
    }

    private void onConfirmPicked() {
        String sameLocationMsg = copyMode ? "目标与原位置相同" : "位置未改变";
        String intoItselfMsg = copyMode ? "不能复制到自身内部" : "不能移动到自身内部";
        if (SshManager.isMoveSameLocation(srcPath, currentDir)) {
            UiUtils.showToast(getContext(), sameLocationMsg);
            return;
        }
        if (srcIsDir && SshManager.isMoveIntoItself(srcPath, currentDir)) {
            UiUtils.showToast(getContext(), intoItselfMsg);
            return;
        }
        listener.onPicked(currentDir);
        dismiss();
    }

    /** 异步列出 currentDir 的子目录（经 SshManager 单线程 executor，回调在后台线程）。 */
    private void loadDirs() {
        tvPath.setText(currentDir);
        progressBar.setVisibility(View.VISIBLE);
        adapter.clear();
        sshManager.listFiles(currentDir, new SshManager.FileListCallback() {
            @Override
            public void onSuccess(List<RemoteFile> files) {
                mainHandler.post(() -> {
                    if (!isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    List<RemoteFile> dirs = new ArrayList<>();
                    for (RemoteFile f : files) {
                        if (f.isDirectory() && !f.getPath().equals(srcPath)) {
                            dirs.add(f);
                        }
                    }
                    adapter.setDirs(dirs);
                    tvEmpty.setVisibility(dirs.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    if (!isShowing()) return;
                    progressBar.setVisibility(View.GONE);
                    UiUtils.showToast(getContext(), "加载目录失败: " + message);
                });
            }
        });
    }

    private final DirAdapter adapter = new DirAdapter(
            dir -> {
                currentDir = dir.getPath();
                loadDirs();
            });

    /** 子目录列表（仅目录、文件夹图标、点击进入）。 */
    private static class DirAdapter extends RecyclerView.Adapter<DirAdapter.VH> {

        interface OnDirClick {
            void onDirClick(RemoteFile dir);
        }

        private final List<RemoteFile> dirs = new ArrayList<>();
        private final OnDirClick click;

        DirAdapter(OnDirClick click) {
            this.click = click;
        }

        void clear() {
            dirs.clear();
            notifyDataSetChanged();
        }

        void setDirs(List<RemoteFile> list) {
            dirs.clear();
            dirs.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.dialog_list_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            RemoteFile dir = dirs.get(position);
            holder.text.setText(dir.getName());
            holder.icon.setImageResource(R.drawable.ic_folder);
            holder.itemView.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                click.onDirClick(dirs.get(pos));
            });
        }

        @Override
        public int getItemCount() {
            return dirs.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView text;
            final ImageView icon;

            VH(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.list_item_text);
                icon = itemView.findViewById(R.id.list_item_icon);
            }
        }
    }
}
