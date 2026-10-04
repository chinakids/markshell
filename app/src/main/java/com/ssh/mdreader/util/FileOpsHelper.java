package com.ssh.mdreader.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;

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
                                            "已移动到 " + targetDir);
                                    host.onFileRemoved(file.getPath());
                                });
                            }

                            @Override
                            public void onError(String message) {
                                MAIN.post(() ->
                                        UiUtils.showToast(context,
                                                "移动失败: " + message));
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
                                        "检查目标失败: " + message);
                            });
                        }
                    });
                });
    }

    private void confirmCopyOverwrite(RemoteFile file, String dst) {
        DialogHelper.showConfirmDialog(context,
                "目标已存在",
                "目标位置已存在「" + file.getName() + "」，是否覆盖？",
                "覆盖", "跳过",
                d -> performCopy(file, dst),
                d -> UiUtils.showToast(context, "已跳过复制"));
    }

    private void performCopy(RemoteFile file, String dst) {
        ssh.copyFile(file.getPath(), dst, new SshManager.CopyFileCallback() {
            @Override
            public void onSuccess() {
                MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    UiUtils.showToast(context, "已复制到 " + dst);
                    host.onFilesChanged();
                });
            }

            @Override
            public void onError(String message) {
                MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    UiUtils.showToast(context, "复制失败: " + message);
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
                                        "检查目标失败: " + message);
                            });
                        }
                    });
                });
    }

    /** 目录级「覆盖」= 合并：目标目录已存在时确认继续合并（同名文件逐项覆盖），文案与文件版区分。 */
    private void confirmCopyDirectoryOverwrite(RemoteFile dir, String dst) {
        DialogHelper.showConfirmDialog(context,
                "目标目录已存在",
                "目标位置已存在目录「" + dir.getName() + "」，继续将合并两目录内容，同名文件将被覆盖。是否继续？",
                "继续合并", "取消",
                d -> performCopyDirectory(dir, dst),
                d -> UiUtils.showToast(context, "已取消复制"));
    }

    private void performCopyDirectory(RemoteFile dir, String dst) {
        ssh.copyDirectory(dir.getPath(), dst, new SshManager.CopyFileCallback() {
            @Override
            public void onSuccess() {
                MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    UiUtils.showToast(context, "已复制到 " + dst);
                    host.onFilesChanged();
                });
            }

            @Override
            public void onError(String message) {
                MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    UiUtils.showToast(context, "复制失败: " + message);
                });
            }
        });
    }

    /** 重命名：输入框校验（非空/未改变/不含 /）后 JSch rename，成功由列表侧更新名称。 */
    public void renameFile(RemoteFile file) {
        DialogHelper.showInputDialog(context,
                "重命名 " + file.getName(),
                "输入新名称（不含 /）",
                "重命名", "取消",
                input -> {
                    if (input.isEmpty()) {
                        UiUtils.showToast(context, "名称不能为空");
                        return;
                    }
                    if (input.equals(file.getName())) {
                        UiUtils.showToast(context, "名称未改变");
                        return;
                    }
                    if (input.contains("/")) {
                        UiUtils.showToast(context, "名称不能包含 /");
                        return;
                    }
                    String newPath = SshManager.buildRenamePath(file.getPath(), input);
                    ssh.renameFile(file.getPath(), newPath, new SshManager.RenameFileCallback() {
                        @Override
                        public void onSuccess() {
                            MAIN.post(() -> {
                                UiUtils.showToast(context, "已重命名为 " + input);
                                host.onFileRenamed(file.getPath(), input);
                            });
                        }

                        @Override
                        public void onError(String message) {
                            MAIN.post(() ->
                                    UiUtils.showToast(context, "重命名失败: " + message));
                        }
                    });
                });
    }

    /** 删除文件的危险确认。 */
    public void confirmDeleteFile(RemoteFile file) {
        DialogHelper.showDangerConfirmDialog(context,
                "删除文件",
                "确定要删除 \"" + file.getName() + "\" 吗？此操作不可恢复。",
                "删除", "取消",
                d -> deleteRemoteFile(file),
                d -> {
                });
    }

    private void deleteRemoteFile(RemoteFile file) {
        ssh.deleteFile(file.getPath(), new SshManager.DeleteFileCallback() {
            @Override
            public void onSuccess() {
                MAIN.post(() -> {
                    UiUtils.showToast(context, "已删除 " + file.getName());
                    host.onFileRemoved(file.getPath());
                });
            }

            @Override
            public void onError(String message) {
                MAIN.post(() ->
                        UiUtils.showToast(context, "删除失败: " + message));
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
                "权限 " + file.getName(),
                "当前 " + current + "，输入 3~4 位八进制（如 644、755、1777）",
                "确定", "取消",
                InputType.TYPE_CLASS_NUMBER,
                current,
                input -> {
                    int mode = SshManager.parseOctalMode(input);
                    if (mode < 0) {
                        UiUtils.showToast(context, "权限格式：3 或 4 位八进制（如 755）");
                        return;
                    }
                    ssh.chmodFile(file.getPath(), mode, new SshManager.ChmodCallback() {
                        @Override
                        public void onSuccess() {
                            MAIN.post(() -> {
                                UiUtils.showToast(context, "已设置权限 " + input);
                                host.onFilesChanged();
                            });
                        }

                        @Override
                        public void onError(String message) {
                            MAIN.post(() ->
                                    UiUtils.showToast(context, "设置权限失败: " + message));
                        }
                    });
                });
    }

    /** 批量删除：危险确认（含目录递归），全部成功后从列表移除；部分失败则刷新真实状态。 */
    public void deleteSelectedFiles(List<RemoteFile> files) {
        if (files.isEmpty()) return;
        List<String> paths = pathsOf(files);
        DialogHelper.showDangerConfirmDialog(context,
                "删除所选 " + files.size() + " 项",
                "确定要删除选中的 " + files.size() + " 项（目录含全部内容）吗？此操作不可恢复。",
                "删除", "取消",
                d -> ssh.batchDelete(paths, (ok, fail, first) -> MAIN.post(() -> {
                    if (!host.isAlive()) return;
                    if (fail == 0) {
                        UiUtils.showToast(context, "已删除 " + ok + " 项");
                        host.onFilesRemoved(paths);
                        host.onSelectionExited();
                    } else {
                        UiUtils.showToast(context,
                                ok > 0 ? ("部分失败：成功 " + ok + " 项，失败 " + fail + " 项：" + first)
                                       : ("删除失败：" + first));
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
                "批量设置权限（" + files.size() + " 项）",
                "输入 3~4 位八进制（如 755）",
                "确定", "取消",
                InputType.TYPE_CLASS_NUMBER,
                null,
                input -> {
                    int mode = SshManager.parseOctalMode(input);
                    if (mode < 0) {
                        UiUtils.showToast(context, "权限格式：3 或 4 位八进制（如 755）");
                        return;
                    }
                    ssh.batchChmod(paths, mode, (ok, fail, first) -> MAIN.post(() -> {
                        if (!host.isAlive()) return;
                        if (fail == 0) {
                            UiUtils.showToast(context,
                                    "已设置 " + ok + " 项权限为 " + input);
                        } else {
                            UiUtils.showToast(context,
                                    ok > 0 ? ("部分失败：成功 " + ok + " 项，失败 " + fail + " 项：" + first)
                                           : ("设置权限失败：" + first));
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
                                "已移动 " + ok + " 项到 " + targetDir);
                    } else {
                        UiUtils.showToast(context,
                                ok > 0 ? ("部分失败：成功 " + ok + " 项，失败 " + fail + " 项：" + first)
                                       : ("移动失败：" + first));
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
                            UiUtils.showToast(context, "检查目标失败: " + message);
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
                "目标目录已存在 " + conflictCount + " 个同名项",
                new String[]{"全部覆盖（" + conflictCount + " 项）",
                        "跳过同名（复制其余 " + rest + " 项）", "取消"},
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
                UiUtils.showToast(context, "所选项目在目标目录均已存在，未复制");
                host.onSelectionExited();
            }
            return;
        }
        ssh.batchCopy(toCopy, targetDir, (ok, fail, first) -> MAIN.post(() -> {
            if (!host.isAlive()) return;
            String suffix = skippedCount > 0 ? "（跳过 " + skippedCount + " 个同名项）" : "";
            if (fail == 0) {
                UiUtils.showToast(context, "已复制 " + ok + " 项到 " + targetDir + suffix);
            } else {
                UiUtils.showToast(context,
                        ok > 0 ? ("部分失败：成功 " + ok + " 项，失败 " + fail + " 项：" + first)
                               : ("复制失败：" + first));
            }
            host.onSelectionExited();
        }));
    }

    /** 删除目录的危险确认（含全部内容）。 */
    public void confirmDeleteDirectory(RemoteFile dir) {
        DialogHelper.showDangerConfirmDialog(context,
                "删除目录",
                "确定要删除目录 \"" + dir.getName() + "\"（含全部内容）吗？此操作不可恢复。",
                "删除", "取消",
                d -> deleteRemoteDirectory(dir),
                d -> {
                });
    }

    private void deleteRemoteDirectory(RemoteFile dir) {
        ssh.deleteDirectory(dir.getPath(), new SshManager.DeleteFileCallback() {
            @Override
            public void onSuccess() {
                MAIN.post(() -> {
                    UiUtils.showToast(context, "已删除 " + dir.getName());
                    host.onFileRemoved(dir.getPath());
                });
            }

            @Override
            public void onError(String message) {
                MAIN.post(() ->
                        UiUtils.showToast(context, "删除失败: " + message));
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
