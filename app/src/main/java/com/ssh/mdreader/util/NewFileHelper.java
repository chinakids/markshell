package com.ssh.mdreader.util;

/**
 * 新建文件/目录的名称校验与路径拼接纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（能力发现循环）</b>：FileBrowser 已覆盖 浏览/排序/显隐/筛选/书签/重命名/
 * 移动/复制/删除/权限/多选，唯独缺少「新建」——CRUD 的 C。竞品（markor template、
 * MaterialFiles、各类 SSH 文件管理器）均为标配；配合既有「远程在线编辑」（迭代28），
 * 「新建文件 → 编辑保存 → 批注」即形成完整笔记流。</p>
 *
 * <p><b>语义（文档化）</b>：</p>
 * <ul>
 *   <li>{@link #validateName}：名称输入须盘内合法——非空、非 "."/".."、不含路径分隔符
 *       {@code /}（POSIX：其他字符包括空格/中文/反斜杠均合法，不做过度约束）；
 *       首尾空白按「输入容错」处理（DialogHelper 已 trim，此处防御性再判）。</li>
 *   <li>{@link #joinPath}：以当前目录拼出子路径；目录为根 {@code "/"} 或空串时正确处理
 *       （不产生双斜杠），名称按 trim 后拼接。</li>
 * </ul>
 *
 * <p><b>创建语义</b>：新建=「不存在才创建」。文件用 {@code SshManager.createFile}
 * （先 stat 存在性再写，避免覆盖已有内容）、目录用 {@code SshManager.createDirectory}
 * （mkdir 前同样先判存在）——与编辑保存的 OVERWRITE 写口径严格区分，防止
 * 「新建同名文件静默覆盖已有文件」的数据丢失。</p>
 */
public final class NewFileHelper {

    private NewFileHelper() {
    }

    /** 名称是否合法（游戏规则见类注释）。合法返回 null；否则返回给用户看的中文错误文案。 */
    public static String validateName(String name) {
        if (name == null || name.trim().isEmpty()) return "名称不能为空";
        String n = name.trim();
        if (n.equals(".") || n.equals("..")) return "名称不能是 “.” 或 “..”";
        if (n.contains("/")) return "名称不能包含 “/”";
        return null;
    }

    /** 拼接子路径：dir（去除尾部斜杠；根 "/" 与空串特殊处理）+ "/" + name.trim()。 */
    public static String joinPath(String dir, String name) {
        String d = dir == null ? "" : dir;
        if (d.isEmpty()) return name.trim();
        if (d.equals("/")) return "/" + name.trim();
        while (d.endsWith("/")) {
            d = d.substring(0, d.length() - 1);
        }
        return d + "/" + name.trim();
    }
}
