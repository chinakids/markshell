package com.ssh.mdreader.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.RemoteFile;
import com.ssh.mdreader.ssh.SshManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * FileBrowserActivity 的文件/目录操作编排层：移动/复制/重命名/删除/权限（单项与批量）的
 * SshManager 调用与回调、确认/输入弹窗、目录选择器的组装都在这里，UI 侧仅通过
 * {@link Host} 最小回调面通知列表更新，使 Activity 只保留 UI 编排。
 *
 * 行为契约（重构维护，2026-10-02）：toast/弹窗文案、回调顺序与拆分前逐字一致；
 * 所有文案改动必须视为行为变更。
 */
public class FileOpsHelper {

    /** 宿主（FileBrowserActivity）向 helper 暴露的最小回调面。 */
    public interface Host {
        /** Activity 仍存活（!isFinishing() && !isDestroyed()）。 */
        boolean isAlive();

        /** 刷新文件列表（loadFiles）。 */
        void onFilesChanged();

        /** 从列表移除单个路径。 */
        void onFileRemoved(String path);

        /** 从列表移除多个路径。 */
        void onFilesRemoved(List<String> paths);

        /** 列表内重命名（path → newName）。 */
        void onFileRenamed(String path, String newName);

        /** 退出多选模式。 */
        void onSelectionExited();
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private final Context context;
    private final Host host;
    private final SshManager ssh = SshManager.getInstance();

    /** 最后一个打开的目录选择器，供 onDestroy 时关闭（对应原 Activity 的 dirPicker 生命周期）。 */
    private DirectoryPickerDialog activePicker;

    public FileOpsHelper(Context context, Host host) {
        this.context = context;
        this.host = host;
    }

    /** onDestroy 时关闭仍打开的目录选择器。 */
    public void dismissActivePicker() {
        if (activePicker != null) {
            activePicker.dismiss();
            activePicker = null;
        }
    }

    /** 移动文件/目录到其他目录：弹目录选择器，确认后 JSch rename（跨目录=目标前缀不同）。 */
    public void moveFile(RemoteFile file) {
        activePicker = DirectoryPickerDialog.show(context,
                file.getPath(), file.getName(), file.isDirectory(),
                targetDir -> ssh.renameFile(
                        file.getPath(),
                        SshManager.buildMovePath(file.getPath(), targetDir),
                        new SshManager.RenameFileCallback() {
                            @Override
                            public void onSuccess() {
                                MAIN.post(() -> {
                                    if (!host.isAlive()) return;
                                    UiUtils.showToast(context,
                                            context.getString(R.string.toast_moved, targetDir));
                                    host.onFileRemoved(file.getPath());
                                });
                            }

                            @Override
                            public void onError(String message) {
                                MAIN.post(() ->
                                        UiUtils.showToast(context,
                                                context.getString(R.string.toast_move_failed, message)));
                            }
                        }));
    }

    /** 复制文件到其他目录：弹目录选择器（复制模式），确认后先检查目标是否存在，存在则询问覆盖/跳过。 */
    public void copyFile(RemoteFile file) {
        activePicker = DirectoryPickerDialog.show(context,
                file.getPath(), file.getName(), file.isDirectory(), true,
                targetDir -> {
                    String dst = SshManager.buildCopyPath(file.getPath(), targetDir);
                    ssh.fileExists(dst, new SshManager.ExistsCallback() {
                        @Override
                        public void onResult(boolean exists) {
                            MAIN.post(() -> {
                                if (!host.isAlive()) return;
                                if (exists) {
                                    confirmCopyOverwrite(file, dst);
                                } else {
                                    performCopy(file, dst);
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            MAIN.post(() -> {
                                if (!host.isAlive()) return;
                                UiUtils.showToast(context,
                                        context.getString(R.string.toast_check_target_failed, message));
                            });
                        }
                    });
                });
    }

    private void confirmCopyOverwrite(RemoteFile file, String dst) {
        DialogHelper.showConfirmDialog(context,
                context.getString(R.string.dialog_target_exists),
                context.getString(R.string.msg_target_exists_overwrite, file.getName()),
                context.getString(R.string.btn_overwrite),
                context.getString(R.string.btn_skip),
                d -> performCopy(file, dst),
                d -> UiUtils.showToast(context, context.getString(R.string.toast_copy_skipped)));
    }

    private void performCopy(RemoteFile file, String dst) {
        ssh.copyFile(file.getPath(), dst, new SshManager.CopyFileCallback() {
            @Override
            public void onSuccess() {
                MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    UiUtils.showToast(context, context.getString(R.string.toast_copied_to, dst));
                    host.onFilesChanged();
                });
            }

            @Override
            public void onError(String message) {
                MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    UiUtils.showToast(context, context.getString(R.string.toast_copy_failed, message));
                });
            }
        });
    }

    /** 复制目录到其他目录：弹目录选择器（复制模式），确认后先检查目标目录是否存在，存在则提示合并语义。 */
    public void copyDirectory(RemoteFile dir) {
        activePicker = DirectoryPickerDialog.show(context,
                dir.getPath(), dir.getName(), true, true,
                targetDir -> {
                    String dst = SshManager.buildCopyPath(dir.getPath(), targetDir);
                    ssh.fileExists(dst, new SshManager.ExistsCallback() {
                        @Override
                        public void onResult(boolean exists) {
                            MAIN.post(() -> {
                                if (!host.isAlive()) return;
                                if (exists) {
                                    confirmCopyDirectoryOverwrite(dir, dst);
                                } else {
                                    performCopyDirectory(dir, dst);
                                }
                            });
                        }

                        @Override
                        public void onError(String message) {
                            MAIN.post(() -> {
                                if (!host.isAlive()) return;
                                UiUtils.showToast(context,
                                        context.getString(R.string.toast_check_target_failed, message));
                            });
                        }
                    });
                });
    }

    /** 目录级「覆盖」= 合并：目标目录已存在时确认继续合并（同名文件逐项覆盖），文案与文件版区分。 */
    private void confirmCopyDirectoryOverwrite(RemoteFile dir, String dst) {
        DialogHelper.showConfirmDialog(context,
                context.getString(R.string.dialog_target_dir_exists),
                context.getString(R.string.msg_target_dir_exists_merge, dir.getName()),
                context.getString(R.string.btn_merge),
                context.getString(R.string.btn_cancel),
                d -> performCopyDirectory(dir, dst),
                d -> UiUtils.showToast(context, context.getString(R.string.toast_copy_cancelled)));
    }

    private void performCopyDirectory(RemoteFile dir, String dst) {
        ssh.copyDirectory(dir.getPath(), dst, new SshManager.CopyFileCallback() {
            @Override
            public void onSuccess() {
                MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    UiUtils.showToast(context, context.getString(R.string.toast_copied_to, dst));
                    host.onFilesChanged();
                });
            }

            @Override
            public void onError(String message) {
                MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    UiUtils.showToast(context, context.getString(R.string.toast_copy_failed, message));
                });
            }
        });
    }

    /** 重命名：输入框校验（非空/未改变/不含 /）后 JSch rename，成功由列表侧更新名称。 */
    public void renameFile(RemoteFile file) {
        DialogHelper.showInputDialog(context,
                context.getString(R.string.dialog_rename_title, file.getName()),
                context.getString(R.string.hint_rename_name),
                context.getString(R.string.btn_rename),
                context.getString(R.string.btn_cancel),
                input -> {
                    if (input.isEmpty()) {
                        UiUtils.showToast(context, context.getString(R.string.toast_name_empty));
                        return;
                    }
                    if (input.equals(file.getName())) {
                        UiUtils.showToast(context, context.getString(R.string.toast_name_unchanged));
                        return;
                    }
                    if (input.contains("/")) {
                        UiUtils.showToast(context, context.getString(R.string.toast_name_contains_slash));
                        return;
                    }
                    String newPath = SshManager.buildRenamePath(file.getPath(), input);
                    ssh.renameFile(file.getPath(), newPath, new SshManager.RenameFileCallback() {
                        @Override
                        public void onSuccess() {
                            MAIN.post(() -> {
                                UiUtils.showToast(context, context.getString(R.string.toast_renamed_to, input));
                                host.onFileRenamed(file.getPath(), input);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            MAIN.post(() ->
                                    UiUtils.showToast(context,
                                            context.getString(R.string.toast_rename_failed, message)));
                        }
                    });
                });
    }

    /** 删除文件的危险确认。 */
    public void confirmDeleteFile(RemoteFile file) {
        DialogHelper.showDangerConfirmDialog(context,
                context.getString(R.string.dialog_delete_file),
                context.getString(R.string.msg_delete_file_confirm, file.getName()),
                context.getString(R.string.btn_delete),
                context.getString(R.string.btn_cancel),
                d -> deleteRemoteFile(file),
                d -> {
                });
    }

    private void deleteRemoteFile(RemoteFile file) {
        ssh.deleteFile(file.getPath(), new SshManager.DeleteFileCallback() {
            @Override
            public void onSuccess() {
                MAIN.post(() -> {
                    UiUtils.showToast(context, context.getString(R.string.toast_deleted_name, file.getName()));
                    host.onFileRemoved(file.getPath());
                });
            }

            @Override
            public void onError(String message) {
                MAIN.post(() ->
                        UiUtils.showToast(context,
                                context.getString(R.string.toast_delete_failed, message)));
            }
        });
    }

    /**
     * 修改文件/目录权限：预填当前八进制权限（&amp; 0777 去掉文件类型位），
     * 校验 3~4 位八进制后提交，成功刷新列表。
     */
    public void showChmodDialog(RemoteFile file) {
        String current = Integer.toOctalString(file.getPermissions() & 0777);
        DialogHelper.showInputDialog(context,
                context.getString(R.string.dialog_permissions_title, file.getName()),
                context.getString(R.string.msg_chmod_current, current),
                context.getString(R.string.btn_ok),
                context.getString(R.string.btn_cancel),
                InputType.TYPE_CLASS_NUMBER,
                current,
                input -> {
                    int mode = SshManager.parseOctalMode(input);
                    if (mode < 0) {
                        UiUtils.showToast(context, context.getString(R.string.toast_chmod_format));
                        return;
                    }
                    ssh.chmodFile(file.getPath(), mode, new SshManager.ChmodCallback() {
                        @Override
                        public void onSuccess() {
                            MAIN.post(() -> {
                                UiUtils.showToast(context, context.getString(R.string.toast_chmod_set, input));
                                host.onFilesChanged();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            MAIN.post(() ->
                                    UiUtils.showToast(context,
                                            context.getString(R.string.toast_chmod_failed, message)));
                        }
                    });
                });
    }

    /** 批量删除：危险确认（含目录递归），全部成功后从列表移除；部分失败则刷新真实状态。 */
    public void deleteSelectedFiles(List<RemoteFile> files) {
        if (files.isEmpty()) return;
        List<String> paths = pathsOf(files);
        DialogHelper.showDangerConfirmDialog(context,
                context.getString(R.string.dialog_delete_selected_title, files.size()),
                context.getString(R.string.msg_delete_selected_confirm, files.size()),
                context.getString(R.string.btn_delete),
                context.getString(R.string.btn_cancel),
                d -> ssh.batchDelete(paths, (ok, fail, first) -> MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    if (fail == 0) {
                        UiUtils.showToast(context, context.getString(R.string.toast_deleted_count, ok));
                        host.onFilesRemoved(paths);
                        host.onSelectionExited();
                    } else {
                        UiUtils.showToast(context,
                                ok > 0 ? context.getString(R.string.toast_partial_failure, ok, fail, first)
                                       : context.getString(R.string.toast_delete_failed_prefix, first));
                        host.onSelectionExited();
                        host.onFilesChanged();
                    }
                })),
                d -> {
                });
    }

    /** 批量权限：一个八进制值应用到所有选中项。 */
    public void chmodSelectedFiles(List<RemoteFile> files) {
        if (files.isEmpty()) return;
        List<String> paths = pathsOf(files);
        DialogHelper.showInputDialog(context,
                context.getString(R.string.dialog_chmod_batch_title, files.size()),
                context.getString(R.string.msg_chmod_input),
                context.getString(R.string.btn_ok),
                context.getString(R.string.btn_cancel),
                InputType.TYPE_CLASS_NUMBER,
                null,
                input -> {
                    int mode = SshManager.parseOctalMode(input);
                    if (mode < 0) {
                        UiUtils.showToast(context, context.getString(R.string.toast_chmod_format));
                        return;
                    }
                    ssh.batchChmod(paths, mode, (ok, fail, first) -> MAIN.post(() -> {
                        if (!host.isAlive()) return;
                        if (fail == 0) {
                            UiUtils.showToast(context,
                                    context.getString(R.string.toast_chmod_batch_set, ok, input));
                        } else {
                            UiUtils.showToast(context,
                                    ok > 0 ? context.getString(R.string.toast_partial_failure, ok, fail, first)
                                           : context.getString(R.string.toast_chmod_failed_prefix, first));
                        }
                        host.onSelectionExited();
                        host.onFilesChanged();
                    }));
                });
    }

    /** 批量移动：目录选择器（多源校验），确认后逐项 rename 到目标目录。 */
    public void moveSelectedFiles(List<RemoteFile> files) {
        if (files.isEmpty()) return;
        List<String> paths = pathsOf(files);
        Set<String> dirPaths = new HashSet<>();
        for (RemoteFile f : files) {
            if (f.isDirectory()) dirPaths.add(f.getPath());
        }
        activePicker = DirectoryPickerDialog.show(context, paths, dirPaths,
                targetDir -> ssh.batchMove(paths, targetDir, (ok, fail, first) -> MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    if (fail == 0) {
                        UiUtils.showToast(context,
                                context.getString(R.string.toast_move_count_to, ok, targetDir));
                    } else {
                        UiUtils.showToast(context,
                                ok > 0 ? context.getString(R.string.toast_partial_failure, ok, fail, first)
                                       : context.getString(R.string.toast_move_failed_prefix, first));
                    }
                    host.onSelectionExited();
                    host.onFilesChanged();
                })));
    }

    /** 批量复制：目录选择器（多源，复制语义校验），确认后探测目标同名冲突——无冲突直接复制；有冲突经列表对话框选择「全部覆盖 / 跳过同名 / 取消」（对标 Termius SFTP 多选复制 + markor copy_move_conflict 冲突策略）。 */
    public void copySelectedFiles(List<RemoteFile> files) {
        if (files.isEmpty()) return;
        List<String> paths = pathsOf(files);
        final List<String> names = new ArrayList<>(files.size());
        Set<String> dirPaths = new HashSet<>();
        for (RemoteFile f : files) {
            names.add(f.getName());
            if (f.isDirectory()) dirPaths.add(f.getPath());
        }
        activePicker = DirectoryPickerDialog.show(context, paths, dirPaths, true,
                targetDir -> ssh.checkTargetsExist(targetDir, names, new SshManager.TargetNamesCallback() {
                    @Override
                    public void onResult(List<String> existingNames) {
                        if (existingNames.isEmpty()) {
                            runBatchCopy(paths, targetDir, 0);
                        } else {
                            MAIN.post(() -> showCopyConflictChooser(paths, names, targetDir, existingNames));
                        }
                    }

                    @Override
                    public void onError(String message) {
                        MAIN.post(() -> {
                            if (!host.isAlive()) return;
                            UiUtils.showToast(context,
                                    context.getString(R.string.toast_check_target_failed, message));
                        });
                    }
                }));
    }

    /** 同名冲突三选：全部覆盖 / 跳过同名 / 取消（与单项复制「覆盖/跳过」语义同源，批量一次决策避免逐项弹窗）。 */
    private void showCopyConflictChooser(List<String> paths, List<String> names,
                                         String targetDir, List<String> existingNames) {
        if (!host.isAlive()) return;
        int conflictCount = existingNames.size();
        int rest = paths.size() - conflictCount;
        DialogHelper.showListDialog(context,
                context.getString(R.string.dialog_copy_conflict_title, conflictCount),
                new String[]{context.getString(R.string.item_copy_overwrite_all, conflictCount),
                        context.getString(R.string.item_copy_skip_same, rest),
                        context.getString(R.string.btn_cancel)},
                null,
                (dialog, which) -> {
                    if (which == 0) {
                        runBatchCopy(paths, targetDir, 0);
                    } else if (which == 1) {
                        List<String> toCopy = new ArrayList<>();
                        for (int i = 0; i < paths.size(); i++) {
                            if (!existingNames.contains(names.get(i))) toCopy.add(paths.get(i));
                        }
                        runBatchCopy(toCopy, targetDir, conflictCount);
                    }
                    // which == 2：取消，无操作
                });
    }

    /** 执行批量复制（worker 回调投递主线程）；{@code skippedCount>0} 时追加「跳过 N 个同名项」提示。 */
    private void runBatchCopy(List<String> toCopy, String targetDir, int skippedCount) {
        if (toCopy.isEmpty()) {
            if (host.isAlive()) {
                UiUtils.showToast(context, context.getString(R.string.toast_copy_all_exists));
                host.onSelectionExited();
            }
            return;
        }
        ssh.batchCopy(toCopy, targetDir, (ok, fail, first) -> MAIN.post(() -> {
            if (!host.isAlive()) return;
            String suffix = skippedCount > 0 ? context.getString(R.string.toast_copy_skipped_suffix, skippedCount) : "";
            if (fail == 0) {
                UiUtils.showToast(context, context.getString(R.string.toast_copy_count_to, ok, targetDir) + suffix);
            } else {
                UiUtils.showToast(context,
                        ok > 0 ? context.getString(R.string.toast_partial_failure, ok, fail, first)
                               : context.getString(R.string.toast_copy_failed_prefix, first));
            }
            host.onSelectionExited();
        }));
    }

    /** 删除目录的危险确认（含全部内容）。 */
    public void confirmDeleteDirectory(RemoteFile dir) {
        DialogHelper.showDangerConfirmDialog(context,
                context.getString(R.string.dialog_delete_directory),
                context.getString(R.string.msg_delete_directory_confirm, dir.getName()),
                context.getString(R.string.btn_delete),
                context.getString(R.string.btn_cancel),
                d -> deleteRemoteDirectory(dir),
                d -> {
                });
    }

    private void deleteRemoteDirectory(RemoteFile dir) {
        ssh.deleteDirectory(dir.getPath(), new SshManager.DeleteFileCallback() {
            @Override
            public void onSuccess() {
                MAIN.post(() -> {
                    UiUtils.showToast(context, context.getString(R.string.toast_deleted_name, dir.getName()));
                    host.onFileRemoved(dir.getPath());
                });
            }

            @Override
            public void onError(String message) {
                MAIN.post(() ->
                        UiUtils.showToast(context,
                                context.getString(R.string.toast_delete_failed, message)));
            }
        });
    }

    /** 选中节点的路径列表（按选中顺序）。 */
    private static List<String> pathsOf(List<RemoteFile> files) {
        List<String> paths = new ArrayList<>(files.size());
        for (RemoteFile f : files) {
            paths.add(f.getPath());
        }
        return paths;
    }
}
