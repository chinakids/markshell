package com.ssh.mdreader.util;

/**
 * 外观主题纯函数层（零 android.* 依赖，JVM 可测）。
 *
 * <p>走查 #54：主题设置——App 原仅 DayNight 跟随系统，无手动覆盖；markor
 * {@code pref_arrkeys__app_themes}（system/auto/autocompat/light/dark/dark-black，行为经
 * {@code GsContextUtils.applyDayNightTheme} 源码实证 = light→MODE_NIGHT_NO、dark→MODE_NIGHT_YES、
 * system→MODE_NIGHT_FOLLOW_SYSTEM）与 Material Files {@code settings_theme} 三态均为设置域标配。
 * 本项目取三态（跟随系统/浅色/深色），markor 的 auto/autocompat/black 为扩展项，留存观察。
 *
 * <p>本类只做主-值和合法性归一化；UI 层（{@code BaseActivity}）负责把语义值翻译成
 * {@code androidx.appcompat.app.AppCompatDelegate} 常量并调用，Android 依赖被隔离在 UI 层。
 */
public final class ThemeHelper {

    /** 跟随系统（默认）。 */
    public static final String THEME_SYSTEM = "system";
    /** 强制浅色。 */
    public static final String THEME_LIGHT = "light";
    /** 强制深色。 */
    public static final String THEME_DARK = "dark";

    /** 默认主题。 */
    public static final String DEFAULT_THEME = THEME_SYSTEM;

    /** 语义值：跟随系统。 */
    public static final int MODE_SYSTEM = 0;
    /** 语义值：浅色。 */
    public static final int MODE_LIGHT = 1;
    /** 语义值：深色。 */
    public static final int MODE_DARK = 2;

    private ThemeHelper() {
    }

    /** 合法主题集合。 */
    public static boolean isTheme(String theme) {
        return THEME_SYSTEM.equals(theme) || THEME_LIGHT.equals(theme) || THEME_DARK.equals(theme);
    }

    /**
     * 归一化主题值：null/空白/大小写不符/非法一律回退 {@link #DEFAULT_THEME}。
     * 读写两侧均经此归一，杜绝脏值进入持久化。
     */
    public static String normalize(String raw) {
        if (raw == null) return DEFAULT_THEME;
        String trimmed = raw.trim().toLowerCase();
        if (THEME_SYSTEM.equals(trimmed)) return THEME_SYSTEM;
        if (THEME_LIGHT.equals(trimmed)) return THEME_LIGHT;
        if (THEME_DARK.equals(trimmed)) return THEME_DARK;
        return DEFAULT_THEME;
    }

    /** 主题值 → 语义值；非法输入按跟随系统处理。 */
    public static int modeFor(String theme) {
        String normalized = normalize(theme);
        if (THEME_LIGHT.equals(normalized)) return MODE_LIGHT;
        if (THEME_DARK.equals(normalized)) return MODE_DARK;
        return MODE_SYSTEM;
    }

    /** 语义值 → 主题值；非法输入按跟随系统处理。 */
    public static String themeFor(int mode) {
        if (mode == MODE_LIGHT) return THEME_LIGHT;
        if (mode == MODE_DARK) return THEME_DARK;
        return THEME_SYSTEM;
    }
}
