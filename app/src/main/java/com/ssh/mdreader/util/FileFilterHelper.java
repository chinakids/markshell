package com.ssh.mdreader.util;

/**
 * 文件列表名称过滤纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（第三轮能力发现）</b>：FileBrowser 只有 显示隐藏/排序/书签 三个入口，
 * 竞品文件管理器（markor filter、MiXplorer/Material Files 搜索）均为标配；目录文件
 * 一多（几十上百）靠人眼找名字不可用。本层提供名称子串匹配（大小写不敏感），
 * scope=当前已加载列表（根目录+已展开子目录），SFTP 递归搜索为路线图后续候选。</p>
 *
 * <p><b>语义（文档化）</b>：</p>
 * <ul>
 *   <li>{@code query} 为 null/空 → 不过滤（全部通过）。</li>
 *   <li>匹配=文件<b>名称</b>（含扩展名）包含查询串（子串语义，非前缀），
 *       大小写不敏感（{@link String#regionMatches} 逐字符，与 FindHelper 同源实现）。</li>
 *   <li>查询串<b>不 trim</b>（与查找栏行为一致：所见即所筛；首尾空格按字面参与匹配）。</li>
 *   <li>空/null 查询=不过滤 → 恒通过（含 null 名称；null 名称仅在<b>有生效查询</b>时
 *       不匹配，属防御分支）。</li>
 * </ul>
 *
 * <p><b>约定</b>：目录加载是惰性展开的（{@code setChildren} 后才可见），因此过滤
 * 不触发任何 SFTP 遍历——只作用于内存中已有的树；递归搜索另行立项。</p>
 */
public final class FileFilterHelper {

    private FileFilterHelper() {
    }

    /** 规范化查询串：null → 空串（其余原样，不 trim）。 */
    public static String normalize(String query) {
        return query == null ? "" : query;
    }

    /** 是否有生效中的过滤：query 非空即生效。 */
    public static boolean active(String query) {
        return query != null && !query.isEmpty();
    }

    /**
     * 名称是否命中过滤（子串、大小写不敏感）。空/null 查询恒为 true（不过滤）；
     * null 名称恒为 false（防御）。查询串按字符处理，不区分语言折叠边界
     * （与 FindHelper 同一实现口径）。
     */
    public static boolean matchesName(String name, String query) {
        if (!active(query)) return true;
        if (name == null) return false;
        return indexOfIgnoreCase(name, query) >= 0;
    }

    /** 大小写不敏感子串查找（regionMatches 逐字符，零分配）。找不到返回 -1。 */
    static int indexOfIgnoreCase(String text, String needle) {
        int limit = text.length() - needle.length();
        for (int i = 0; i <= limit; i++) {
            if (text.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }
}
