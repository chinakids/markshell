package com.ssh.mdreader.util;

/**
 * 外观主题纯函数层（零 android.* 依赖，JVM 可测）。
 *
 * <p>走查 #54：#57——主题状态取 markor {@code pref_arrkeys__app_themes} 六态全集
 * system/auto/autocompat/light/dark/dark-black（第四十六轮 gh api 源码实抽 arrays.xml）；
 * 行为语义经 {@code GsContextUtils.applyDayNightTheme}（line 2838-2852）源码实证：
 * light→MODE_NIGHT_NO、dark 与 dark-black→MODE_NIGHT_YES（{@code pref.contains("dark")} 同判）、
 * system→MODE_NIGHT_FOLLOW_SYSTEM、auto→MODE_NIGHT_AUTO（deprecated，按 22:00-06:00 判）、
 * autocompat→{@code isCurrentHourOfDayBetween(9,17)}（ISO 09:00-17:00 判浅色，含端点）。
 * dark-black 在 markor 无独立资源（values-night 无 styles 覆盖、dark__background=dark_grey
 * #212121 与 dark 共用）=两者行为等价，如实标注；本类忠实复现该语义。
 *
 * <p>本类只做主-值和合法性归一化、时间判定与「夜间意图」解析；UI 层（{@code BaseActivity}）
 * 负责把抽象夜间意图翻译成 {@code androidx.appcompat.app.AppCompatDelegate} 常量并调用，
 * Android 依赖被隔离在 UI 层（单一语义源={@code BaseActivity.resolveAppCompatNightMode}）。
 */
public final class ThemeHelper {

    /** 跟随系统（默认）。 */
    public static final String THEME_SYSTEM = "system";
    /** 自动（AppCompat AUTO=按 22:00-06:00 判深色）。 */
    public static final String THEME_AUTO = "auto";
    /** 自动兼容（09:00-17:00 含端点=浅色，其余=深色；markor autocompat 同语义）。 */
    public static final String THEME_AUTOCOMPAT = "autocompat";
    /** 强制浅色。 */
    public static final String THEME_LIGHT = "light";
    /** 强制深色。 */
    public static final String THEME_DARK = "dark";
    /** 强制深色·纯黑（markor dark-black：行为与 dark 等价=均 MODE_NIGHT_YES，如实标注）。 */
    public static final String THEME_DARK_BLACK = "dark-black";

    /** 默认主题。 */
    public static final String DEFAULT_THEME = THEME_SYSTEM;

    /** 语义模式：跟随系统。 */
    public static final int MODE_SYSTEM = 0;
    /** 语义模式：浅色。 */
    public static final int MODE_LIGHT = 1;
    /** 语义模式：深色。 */
    public static final int MODE_DARK = 2;
    /** 语义模式：自动（22:00-06:00 深色）。 */
    public static final int MODE_AUTO = 3;
    /** 语义模式：自动兼容（09:00-17:00 浅色）。 */
    public static final int MODE_AUTOCOMPAT = 4;
    /** 语义模式：深色·纯黑（行为同 MODE_DARK，markor 同）。 */
    public static final int MODE_DARK_BLACK = 5;

    /** 夜间意图：跟随系统。 */
    public static final int NIGHT_FOLLOW_SYSTEM = 0;
    /** 夜间意图：强制浅色。 */
    public static final int NIGHT_NO = 1;
    /** 夜间意图：强制深色。 */
    public static final int NIGHT_YES = 2;
    /** 夜间意图：按时间自动（22:00-06:00 深色，AppCompat MODE_NIGHT_AUTO 语义）。 */
    public static final int NIGHT_AUTO_TIME = 3;

    /** autocompat 白天判定：起始小时（含）。 */
    public static final int DAYLIGHT_START_HOUR = 9;
    /** autocompat 白天判定：结束小时（含）。 */
    public static final int DAYLIGHT_END_HOUR = 17;

    private ThemeHelper() {
    }

    /** 合法主题集合（markor 六态全集）。 */
    public static boolean isTheme(String theme) {
        return THEME_SYSTEM.equals(theme) || THEME_AUTO.equals(theme)
                || THEME_AUTOCOMPAT.equals(theme) || THEME_LIGHT.equals(theme)
                || THEME_DARK.equals(theme) || THEME_DARK_BLACK.equals(theme);
    }

    /**
     * 归一化主题值：null/空白/大小写不符/非法一律回退 {@link #DEFAULT_THEME}。
     * 读写两侧均经此归一，杜绝脏值进入持久化（旧三态脏值「auto/dark-black 非法」随本轮
     * 转正合法，先前归一为 system 的旧持久值无迁移负担=新会话按新语义读取）。
     */
    public static String normalize(String raw) {
        if (raw == null) return DEFAULT_THEME;
        String trimmed = raw.trim().toLowerCase();
        if (isTheme(trimmed)) return trimmed;
        return DEFAULT_THEME;
    }

    /** 主题值 → 语义模式；非法输入按跟随系统处理。 */
    public static int modeFor(String theme) {
        String normalized = normalize(theme);
        if (THEME_LIGHT.equals(normalized)) return MODE_LIGHT;
        if (THEME_DARK.equals(normalized)) return MODE_DARK;
        if (THEME_DARK_BLACK.equals(normalized)) return MODE_DARK_BLACK;
        if (THEME_AUTO.equals(normalized)) return MODE_AUTO;
        if (THEME_AUTOCOMPAT.equals(normalized)) return MODE_AUTOCOMPAT;
        return MODE_SYSTEM;
    }

    /** 语义模式 → 主题值；非法输入按跟随系统处理。 */
    public static String themeFor(int mode) {
        if (mode == MODE_LIGHT) return THEME_LIGHT;
        if (mode == MODE_DARK) return THEME_DARK;
        if (mode == MODE_DARK_BLACK) return THEME_DARK_BLACK;
        if (mode == MODE_AUTO) return THEME_AUTO;
        if (mode == MODE_AUTOCOMPAT) return THEME_AUTOCOMPAT;
        return THEME_SYSTEM;
    }

    /**
     * autocompat 白天判定：09:00-17:00（含端点）为白天=浅色，否则深色。
     * 语义与 markor {@code isCurrentHourOfDayBetween(9, 17)}（{@code h>=begin && h<=end}，
     * begin/end 越界防御归 0）对齐；本方法入参为小时（Calendar HOUR_OF_DAY 恒 0-23），
     * 超界小时防御性判非白天（markor 的 begin/end 防御在此无入参对应，如实标注差异）。
     */
    public static boolean isDaylightHour(int hour) {
        return hour >= DAYLIGHT_START_HOUR && hour <= DAYLIGHT_END_HOUR;
    }

    /**
     * 主题值 + 当前小时 → 夜间意图（抽象层；AppCompat 常量映射在 UI 层单一语义源）。
     * <ul>
     * <li>system → {@link #NIGHT_FOLLOW_SYSTEM}</li>
     * <li>light → {@link #NIGHT_NO}</li>
     * <li>dark / dark-black → {@link #NIGHT_YES}（markor contains("dark") 同判=black 等价 dark）</li>
     * <li>auto → {@link #NIGHT_AUTO_TIME}（AppCompat MODE_NIGHT_AUTO 语义）</li>
     * <li>autocompat → {@link #isDaylightHour(hourOfDay)} 判 {@link #NIGHT_NO}/{@link #NIGHT_YES}</li>
     * </ul>
     * 非法输入→跟随系统。
     */
    public static int nightIntentFor(String theme, int hourOfDay) {
        String normalized = normalize(theme);
        if (THEME_LIGHT.equals(normalized)) return NIGHT_NO;
        if (THEME_DARK.equals(normalized) || THEME_DARK_BLACK.equals(normalized)) return NIGHT_YES;
        if (THEME_AUTO.equals(normalized)) return NIGHT_AUTO_TIME;
        if (THEME_AUTOCOMPAT.equals(normalized)) return isDaylightHour(hourOfDay) ? NIGHT_NO : NIGHT_YES;
        return NIGHT_FOLLOW_SYSTEM;
    }
}
