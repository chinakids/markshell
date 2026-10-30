package com.ssh.mdreader.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.ShareHelper;
import com.ssh.mdreader.util.UiUtils;

/**
 * HTML 渲染查看器（本地/远端 .html/.htm 文件 → WebView 渲染）。
 *
 * <p>与代码查看器（{@link CodeViewerActivity}，看源码+高亮）的分工：
 * 同一份 .html 默认走本查看器渲染，菜单可切到源码视图。</p>
 *
 * <p>加载方式：SFTP 读出文本 → {@code loadDataWithBaseURL(null, html, "text/html",
 * "UTF-8", null)}。**已知限制**：HTML 里引用的相对资源（同目录的 .css/.js/图片）
 * 无法自动加载——那些文件也在远端 SFTP，WebView 不认识 sftp://；绝对地址（http/https）
 * 与内联样式/脚本正常。后续可做「拦截 WebResourceRequest + SFTP 取资源」增强。</p>
 */
public class HtmlViewerActivity extends BaseActivity {

    private static final int MENU_COPY_PATH_ID = 0xA5001;
    private static final int MENU_SHARE_ID = 0xA5002;
    private static final int MENU_RELOAD_ID = 0xA5003;
    private static final int MENU_SOURCE_ID = 0xA5004;

    private WebView webView;
    private View loadingOverlay;
    private String currentFilePath;
    private String rawHtml;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_viewer);

        setupToolbar(R.string.title_html_viewer, true);

        webView = findViewById(R.id.web_html);
        loadingOverlay = findViewById(R.id.loading_overlay);

        WebSettings settings = webView.getSettings();
        // 本地 HTML 常带脚本（图表/折叠菜单等）；仅渲染本地内容，不启用跨域文件访问。
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        // 站内跳转留在本 WebView；外链交给系统浏览器，避免 WebView 里点出去回不来。
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("http://") || url.startsWith("https://")
                        || url.startsWith("mailto:") || url.startsWith("tel:")) {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                                request.getUrl()));
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            }
        });

        currentFilePath = getIntent().getStringExtra("file_path");
        String fileName = getIntent().getStringExtra("file_name");
        if (getSupportActionBar() != null && fileName != null) {
            getSupportActionBar().setSubtitle(fileName);
        }
        if (currentFilePath == null) { finish(); return; }

        if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
        loadHtml(currentFilePath);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (currentFilePath == null) return super.onCreateOptionsMenu(menu);
        menu.add(Menu.NONE, MENU_COPY_PATH_ID, Menu.NONE, getString(R.string.menu_copy_path))
                .setIcon(R.drawable.ic_content_copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_SHARE_ID, Menu.NONE, getString(R.string.menu_share))
                .setIcon(R.drawable.ic_share)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        menu.add(Menu.NONE, MENU_SOURCE_ID, Menu.NONE, getString(R.string.menu_view_source))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, MENU_RELOAD_ID, Menu.NONE, getString(R.string.menu_reload))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_COPY_PATH_ID) {
            UiUtils.copyRemotePath(this, currentFilePath);
            return true;
        }
        if (item.getItemId() == MENU_SHARE_ID) {
            String text = rawHtml != null ? rawHtml : "";
            UiUtils.shareText(this, text, ShareHelper.fileNameFromPath(currentFilePath));
            return true;
        }
        if (item.getItemId() == MENU_RELOAD_ID) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
            loadHtml(currentFilePath);
            return true;
        }
        if (item.getItemId() == MENU_SOURCE_ID) {
            // 切到源码视图：复用代码查看器的分发路径（同一 path 走 CodeViewer）。
            android.content.Intent intent = new android.content.Intent(this, CodeViewerActivity.class);
            intent.putExtra("file_path", currentFilePath);
            intent.putExtra("file_name", ShareHelper.fileNameFromPath(currentFilePath));
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadHtml(String filePath) {
        SshManager.getInstance().readFile(filePath, new SshManager.FileContentCallback() {
            @Override
            public void onSuccess(String content) {
                runOnUiThread(() -> {
                    rawHtml = content;
                    // baseUrl=null：不假设任何目录上下文（相对资源本就取不到，见类注释）。
                    // mimeType 显式 text/html + UTF-8，避免中文乱码。
                    webView.loadDataWithBaseURL(null, content, "text/html", "UTF-8", null);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
                    webView.loadDataWithBaseURL(null,
                            "<html><body style=\"background:#1E1E1E;color:#A0A0A0;"
                                    + "font-family:sans-serif;padding:24px\">"
                                    + getString(R.string.msg_load_failed, error) + "</body></html>",
                            "text/html", "UTF-8", null);
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        // WebView 内有历史（站内锚点/内联跳转）先回退，否则退出查看器。
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
