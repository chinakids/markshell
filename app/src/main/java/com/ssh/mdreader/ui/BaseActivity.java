package com.ssh.mdreader.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.ssh.mdreader.R;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.ThemeHelper;

public abstract class BaseActivity extends AppCompatActivity {

    protected MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyStoredTheme();
        super.onCreate(savedInstanceState);
    }

    /**
     * 应用持久化外观主题（AppCompat DayNight 三态，走查 #54）：在 super.onCreate 之前调用，
     * 保证本次 Activity 创建即按存储模式取资源；切换后 {@code setDefaultNightMode} 触发配置变化、
     * 已存活 Activity 自动重建，本方法幂等（重复调用同值零副作用）。
     */
    private void applyStoredTheme() {
        String mode = new PreferenceManager(getApplicationContext()).getThemeMode();
        int semantic = ThemeHelper.modeFor(mode);
        int night = semantic == ThemeHelper.MODE_LIGHT
                ? AppCompatDelegate.MODE_NIGHT_NO
                : semantic == ThemeHelper.MODE_DARK
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(night);
    }

    protected void setupToolbar(int titleResId, boolean showBack) {
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setTitle(titleResId);
            if (showBack) {
                toolbar.setNavigationOnClickListener(v -> onBackPressed());
            }
        }
    }

    protected void setupToolbar(String title, boolean showBack) {
        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setTitle(title);
            if (showBack) {
                toolbar.setNavigationOnClickListener(v -> onBackPressed());
            }
        }
    }

    /** True when the current window is wide enough (foldable unfolded / tablet) for two-pane UIs. */
    protected boolean isLargeScreen() {
        return getResources().getConfiguration().screenWidthDp >= 600;
    }

    protected void showLoading(boolean loading) {
        // Override in subclass
    }
}
