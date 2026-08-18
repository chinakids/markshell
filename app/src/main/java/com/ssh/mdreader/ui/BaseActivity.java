package com.ssh.mdreader.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.ssh.mdreader.R;

public abstract class BaseActivity extends AppCompatActivity {

    protected MaterialToolbar toolbar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

    protected void showLoading(boolean loading) {
        // Override in subclass
    }
}
