package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ThemeHelper 纯函数单测：合法性 / 归一化（大小写、空白、脏值回退默认）/ 双向映射 /
 * 时段判定（autocompat 09:00-17:00）/ 夜间意图解析（六态，走查 #57）。
 * 语义与 markor GsContextUtils.applyDayNightTheme + isCurrentHourOfDayBetween 源码实证对齐
 * （六态全集=pref_arrkeys__app_themes system/auto/autocompat/light/dark/dark-black；
 * light→NO、dark/dark-black→YES（contains("dark")）、system→FOLLOW_SYSTEM、
 * auto→AUTO_TIME、autocompat→（9&lt;=h&lt;=17）?NO:YES）。
 */
public class ThemeHelperTest {

    // ── 常量契约 ────────────────────────────────────────────────────────────

    @Test
    public void constants_consistent() {
        assertEquals("system", ThemeHelper.THEME_SYSTEM);
        assertEquals("auto", ThemeHelper.THEME_AUTO);
        assertEquals("autocompat", ThemeHelper.THEME_AUTOCOMPAT);
        assertEquals("light", ThemeHelper.THEME_LIGHT);
        assertEquals("dark", ThemeHelper.THEME_DARK);
        assertEquals("dark-black", ThemeHelper.THEME_DARK_BLACK);
        assertEquals(ThemeHelper.THEME_SYSTEM, ThemeHelper.DEFAULT_THEME);

        assertEquals(0, ThemeHelper.MODE_SYSTEM);
        assertEquals(1, ThemeHelper.MODE_LIGHT);
        assertEquals(2, ThemeHelper.MODE_DARK);
        assertEquals(3, ThemeHelper.MODE_AUTO);
        assertEquals(4, ThemeHelper.MODE_AUTOCOMPAT);
        assertEquals(5, ThemeHelper.MODE_DARK_BLACK);

        assertEquals(0, ThemeHelper.NIGHT_FOLLOW_SYSTEM);
        assertEquals(1, ThemeHelper.NIGHT_NO);
        assertEquals(2, ThemeHelper.NIGHT_YES);
        assertEquals(3, ThemeHelper.NIGHT_AUTO_TIME);

        assertEquals(9, ThemeHelper.DAYLIGHT_START_HOUR);
        assertEquals(17, ThemeHelper.DAYLIGHT_END_HOUR);
    }

    // ── 合法性 ──────────────────────────────────────────────────────────────

    @Test
    public void valid_values_accepted() {
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_SYSTEM));
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_AUTO));
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_AUTOCOMPAT));
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_LIGHT));
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_DARK));
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_DARK_BLACK));
        assertFalse(ThemeHelper.isTheme("bogus"));
        assertFalse(ThemeHelper.isTheme(""));
        assertFalse(ThemeHelper.isTheme(null));
    }

    // ── 归一化（脏值一律回退跟随系统）──────────────────────────────────────

    @Test
    public void normalize_canonical_values_unchanged() {
        assertEquals("system", ThemeHelper.normalize("system"));
        assertEquals("auto", ThemeHelper.normalize("auto"));
        assertEquals("autocompat", ThemeHelper.normalize("autocompat"));
        assertEquals("light", ThemeHelper.normalize("light"));
        assertEquals("dark", ThemeHelper.normalize("dark"));
        assertEquals("dark-black", ThemeHelper.normalize("dark-black"));
    }

    @Test
    public void normalize_case_and_whitespace_insensitive() {
        assertEquals("light", ThemeHelper.normalize("  Light "));
        assertEquals("dark", ThemeHelper.normalize("DARK"));
        assertEquals("system", ThemeHelper.normalize("System"));
        assertEquals("auto", ThemeHelper.normalize("AUTO"));
        assertEquals("autocompat", ThemeHelper.normalize("Autocompat"));
        assertEquals("dark-black", ThemeHelper.normalize(" Dark-Black "));
    }

    @Test
    public void normalize_null_empty_invalid_defaults_to_system() {
        assertEquals("system", ThemeHelper.normalize(null));
        assertEquals("system", ThemeHelper.normalize(""));
        assertEquals("system", ThemeHelper.normalize("bogus"));
        assertEquals("system", ThemeHelper.normalize("darkblack"));
        assertEquals("system", ThemeHelper.normalize("  "));
    }

    // ── 双向映射（非法输入按跟随系统处理）──────────────────────────────────

    @Test
    public void modeFor_maps_all_states() {
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor("system"));
        assertEquals(ThemeHelper.MODE_AUTO, ThemeHelper.modeFor("auto"));
        assertEquals(ThemeHelper.MODE_AUTOCOMPAT, ThemeHelper.modeFor("autocompat"));
        assertEquals(ThemeHelper.MODE_LIGHT, ThemeHelper.modeFor("light"));
        assertEquals(ThemeHelper.MODE_DARK, ThemeHelper.modeFor("dark"));
        assertEquals(ThemeHelper.MODE_DARK_BLACK, ThemeHelper.modeFor("dark-black"));
    }

    @Test
    public void modeFor_invalid_defaults_to_system() {
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor(null));
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor("bogus"));
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor(""));
    }

    @Test
    public void themeFor_maps_all_states_and_roundtrip() {
        assertEquals("system", ThemeHelper.themeFor(ThemeHelper.MODE_SYSTEM));
        assertEquals("auto", ThemeHelper.themeFor(ThemeHelper.MODE_AUTO));
        assertEquals("autocompat", ThemeHelper.themeFor(ThemeHelper.MODE_AUTOCOMPAT));
        assertEquals("light", ThemeHelper.themeFor(ThemeHelper.MODE_LIGHT));
        assertEquals("dark", ThemeHelper.themeFor(ThemeHelper.MODE_DARK));
        assertEquals("dark-black", ThemeHelper.themeFor(ThemeHelper.MODE_DARK_BLACK));
        for (int mode : new int[]{ThemeHelper.MODE_SYSTEM, ThemeHelper.MODE_AUTO,
                ThemeHelper.MODE_AUTOCOMPAT, ThemeHelper.MODE_LIGHT, ThemeHelper.MODE_DARK,
                ThemeHelper.MODE_DARK_BLACK}) {
            assertEquals(mode, ThemeHelper.modeFor(ThemeHelper.themeFor(mode)));
        }
    }

    @Test
    public void themeFor_invalid_defaults_to_system() {
        assertEquals("system", ThemeHelper.themeFor(-1));
        assertEquals("system", ThemeHelper.themeFor(99));
    }

    // ── 时段判定（autocompat 09:00-17:00 含端点）─────────────────────────────

    @Test
    public void isDaylightHour_boundaries_inclusive() {
        assertFalse(ThemeHelper.isDaylightHour(0));
        assertFalse(ThemeHelper.isDaylightHour(8));
        assertTrue(ThemeHelper.isDaylightHour(9));
        assertTrue(ThemeHelper.isDaylightHour(12));
        assertTrue(ThemeHelper.isDaylightHour(17));
        assertFalse(ThemeHelper.isDaylightHour(18));
        assertFalse(ThemeHelper.isDaylightHour(23));
    }

    @Test
    public void isDaylightHour_out_of_range_defensive_false() {
        assertFalse(ThemeHelper.isDaylightHour(-1));
        assertFalse(ThemeHelper.isDaylightHour(24));
    }

    // ── 夜间意图解析（UI 层映射 AppCompat 常量的抽象语义，markor 行为对齐）──

    @Test
    public void nightIntentFor_deterministic_modes() {
        assertEquals(ThemeHelper.NIGHT_FOLLOW_SYSTEM, ThemeHelper.nightIntentFor("system", 10));
        assertEquals(ThemeHelper.NIGHT_NO, ThemeHelper.nightIntentFor("light", 10));
        assertEquals(ThemeHelper.NIGHT_YES, ThemeHelper.nightIntentFor("dark", 10));
        // dark-black 与 dark 等价（markor contains("dark") 同判，无独立资源）：
        assertEquals(ThemeHelper.NIGHT_YES, ThemeHelper.nightIntentFor("dark-black", 10));
        assertEquals(ThemeHelper.NIGHT_AUTO_TIME, ThemeHelper.nightIntentFor("auto", 10));
    }

    @Test
    public void nightIntentFor_autocompat_hour_dependent() {
        assertEquals(ThemeHelper.NIGHT_NO, ThemeHelper.nightIntentFor("autocompat", 9));
        assertEquals(ThemeHelper.NIGHT_NO, ThemeHelper.nightIntentFor("autocompat", 17));
        assertEquals(ThemeHelper.NIGHT_YES, ThemeHelper.nightIntentFor("autocompat", 8));
        assertEquals(ThemeHelper.NIGHT_YES, ThemeHelper.nightIntentFor("autocompat", 18));
        assertEquals(ThemeHelper.NIGHT_YES, ThemeHelper.nightIntentFor("autocompat", 0));
    }

    @Test
    public void nightIntentFor_invalid_defaults_to_follow_system() {
        assertEquals(ThemeHelper.NIGHT_FOLLOW_SYSTEM, ThemeHelper.nightIntentFor(null, 10));
        assertEquals(ThemeHelper.NIGHT_FOLLOW_SYSTEM, ThemeHelper.nightIntentFor("bogus", 10));
        assertEquals(ThemeHelper.NIGHT_FOLLOW_SYSTEM, ThemeHelper.nightIntentFor("", 10));
    }
}
