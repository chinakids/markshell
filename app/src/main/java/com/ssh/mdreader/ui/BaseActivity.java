package com.ssh.mdreader.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.appbar.MaterialToolbar;
import com.ssh.mdreader.R;
import com.ssh.mdreader.util.PreferenceManager;
import com.ssh.mdreader.util.ThemeHelper;

import java.util.Calendar;

public abstract class BaseActivity extends AppCompatActivity {

    protected MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        applyStoredTheme();
        super.onCreate(savedInstanceState);
    }

    /**
     * 应用持久化外观主题（AppCompat DayNight 六态，走查 #54/#57）：在 super.onCreate 之前调用，
     * 保证本次 Activity 创建即按存储模式取资源；切换后 {@code setDefaultNightMode} 触发配置变化、
     * 已存活 Activity 自动重建，本方法幂等（重复调用同值零副作用）。
     */
    private void applyStoredTheme() {
        String mode = new PreferenceManager(getApplicationContext()).getThemeMode();
        AppCompatDelegate.setDefaultNightMode(resolveAppCompatNightMode(mode));
    }

    /**
     * 主题值 → {@link AppCompatDelegate} 夜间模式常量的唯一映射点（单一语义源）：
     * 所有消费方（本基类启动应用 + MainActivity 切换回调）不得各自再写一份映射，防漂移
     * （#56 连接表单校验同类教训）。抽象意图（nightIntentFor）在 ThemeHelper 纯函数层，
     * 本方法仅做抽象意图→AppCompat 常量的一层翻译。
     */
    @SuppressWarnings("deprecation") // ModeNight AUTO 已弃用但为本方 auto 语义（markor 同款 noinspection）
    protected static int resolveAppCompatNightMode(String theme) {
        int intent = ThemeHelper.nightIntentFor(theme,
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        if (intent == ThemeHelper.NIGHT_NO) return AppCompatDelegate.MODE_NIGHT_NO;
        if (intent == ThemeHelper.NIGHT_YES) return AppCompatDelegate.MODE_NIGHT_YES;
        if (intent == ThemeHelper.NIGHT_AUTO_TIME) return AppCompatDelegate.MODE_NIGHT_AUTO;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
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
