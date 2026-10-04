package com.ssh.mdreader.util;

/**
 * 关于/版本信息纯函数层（零 android.* 依赖，JVM 可测）。
 *
 * <p>走查（能力发现循环第四十二轮）：设置域剩余候选=关于/版本页——strings.xml
 * {@code action_about}「关于」为死字符串（全仓 0 引用）且 build.gradle 的
 * versionName/versionCode（1.0.0/1）无处展示=已取数据未展出（判据⑤）；竞品 markor
 * strings.xml {@code about}「About」（键名存在性）+ MoreInfoFragment.java:156/161
 * 源码实证（{@code "Version v%s (%d)"} = getAppVersionName + VERSION_CODE + Package
 * 信息）=关于/版本展示为设置域标配（行为语义）。本功能属配置类（教训㊼：配置类功能
 * 不按交互类触发制观察），按候选排序（关于/版本页 1/1=1.0 为最高剩余非触发制）本轮实施。
 *
 * <p>本类只做版本行格式化（含防御归一）；UI 层（MainActivity）经 PackageManager
 * 取真实 versionName/versionCode，Android 依赖隔离在 UI 层（与 ThemeHelper 同范式）。
 */
public final class AboutHelper {

    private AboutHelper() {
    }

    /**
     * 版本行：markor MoreInfoFragment {@code "Version v%s (%d)"} 同型。
     *
     * <p>versionName 为 null/空白时回退 {@code "?"}（PackageManager 官方 API 允许
     * 无版本名，防御展示「v? (1)」而非崩溃/空值）；versionCode 原样透传（int 契约）。
     */
    public static String versionLine(String versionName, int versionCode) {
        String name = versionName == null ? "?" : versionName.trim();
        if (name.isEmpty()) name = "?";
        return "v" + name + " (" + versionCode + ")";
    }
}
