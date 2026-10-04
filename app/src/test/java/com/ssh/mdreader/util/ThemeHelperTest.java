package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ThemeHelper 纯函数单测：合法性 / 归一化（大小写、空白、脏值回退默认）/ 双向映射。
 * 语义与 markor GsContextUtils.applyDayNightTheme 的 system/light/dark 子集对齐
 * （走查 #54，源码实证：light→MODE_NIGHT_NO、dark→MODE_NIGHT_YES、system→FOLLOW_SYSTEM）。
 */
public class ThemeHelperTest {

    // ── 常量契约 ────────────────────────────────────────────────────────────

    @Test
    public void constants_consistent() {
        assertEquals("system", ThemeHelper.THEME_SYSTEM);
        assertEquals("light", ThemeHelper.THEME_LIGHT);
        assertEquals("dark", ThemeHelper.THEME_DARK);
        assertEquals(ThemeHelper.THEME_SYSTEM, ThemeHelper.DEFAULT_THEME);
        assertEquals(0, ThemeHelper.MODE_SYSTEM);
        assertEquals(1, ThemeHelper.MODE_LIGHT);
        assertEquals(2, ThemeHelper.MODE_DARK);
    }

    // ── 合法性 ──────────────────────────────────────────────────────────────

    @Test
    public void valid_values_accepted() {
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_SYSTEM));
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_LIGHT));
        assertTrue(ThemeHelper.isTheme(ThemeHelper.THEME_DARK));
        assertFalse(ThemeHelper.isTheme("auto"));
        assertFalse(ThemeHelper.isTheme(""));
        assertFalse(ThemeHelper.isTheme(null));
    }

    // ── 归一化（脏值一律回退跟随系统）──────────────────────────────────────

    @Test
    public void normalize_canonical_values_unchanged() {
        assertEquals("system", ThemeHelper.normalize("system"));
        assertEquals("light", ThemeHelper.normalize("light"));
        assertEquals("dark", ThemeHelper.normalize("dark"));
    }

    @Test
    public void normalize_case_and_whitespace_insensitive() {
        assertEquals("light", ThemeHelper.normalize("  Light "));
        assertEquals("dark", ThemeHelper.normalize("DARK"));
        assertEquals("system", ThemeHelper.normalize("System"));
    }

    @Test
    public void normalize_null_empty_invalid_defaults_to_system() {
        assertEquals("system", ThemeHelper.normalize(null));
        assertEquals("system", ThemeHelper.normalize(""));
        assertEquals("system", ThemeHelper.normalize("auto"));
        assertEquals("system", ThemeHelper.normalize("dark-black"));
        assertEquals("system", ThemeHelper.normalize("  "));
    }

    // ── 双向映射（非法输入按跟随系统处理）──────────────────────────────────

    @Test
    public void modeFor_maps_three_states() {
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor("system"));
        assertEquals(ThemeHelper.MODE_LIGHT, ThemeHelper.modeFor("light"));
        assertEquals(ThemeHelper.MODE_DARK, ThemeHelper.modeFor("dark"));
    }

    @Test
    public void modeFor_invalid_defaults_to_system() {
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor(null));
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor("bogus"));
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor(""));
    }

    @Test
    public void themeFor_maps_three_states_and_roundtrip() {
        assertEquals("system", ThemeHelper.themeFor(ThemeHelper.MODE_SYSTEM));
        assertEquals("light", ThemeHelper.themeFor(ThemeHelper.MODE_LIGHT));
        assertEquals("dark", ThemeHelper.themeFor(ThemeHelper.MODE_DARK));
        assertEquals(ThemeHelper.MODE_SYSTEM, ThemeHelper.modeFor(ThemeHelper.themeFor(ThemeHelper.MODE_SYSTEM)));
        assertEquals(ThemeHelper.MODE_LIGHT, ThemeHelper.modeFor(ThemeHelper.themeFor(ThemeHelper.MODE_LIGHT)));
        assertEquals(ThemeHelper.MODE_DARK, ThemeHelper.modeFor(ThemeHelper.themeFor(ThemeHelper.MODE_DARK)));
    }

    @Test
    public void themeFor_invalid_defaults_to_system() {
        assertEquals("system", ThemeHelper.themeFor(-1));
        assertEquals("system", ThemeHelper.themeFor(99));
    }
}
