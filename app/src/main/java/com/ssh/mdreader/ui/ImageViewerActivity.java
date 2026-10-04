package com.ssh.mdreader.ui;

import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.ssh.SshManager;
import com.ssh.mdreader.util.DialogHelper;
import com.ssh.mdreader.util.ImageExifHelper;
import com.ssh.mdreader.util.ImageSaveHelper;
import com.ssh.mdreader.util.ShareHelper;
import com.ssh.mdreader.util.UiUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class ImageViewerActivity extends BaseActivity {

    private ImageView ivImage;
    private View loadingOverlay;
    private TextView tvError;
    private FrameLayout frameContainer;

    private final Matrix matrix = new Matrix();
    private float minScale = 1f;
    private float maxScale = 5f;
    private float currentScale = 1f;
    private ScaleGestureDetector scaleDetector;

    private static final int NONE = 0, DRAG = 1, ZOOM = 2;
    private int mode = NONE;
    private final PointF lastTouch = new PointF();

    private static final int MENU_COPY_PATH_ID = 0xA4001;
    private static final int MENU_SAVE_IMAGE_ID = 0xA4002;
    private static final int MENU_SHARE_ID = 0xA4003;

    private Bitmap currentBitmap;
    private byte[] originalBytes;
    private String fileName;
    private ValueAnimator currentAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        fileName = getIntent().getStringExtra("file_name");
        setupToolbar(fileName != null ? fileName : "图片查看器", true);

        ivImage = findViewById(R.id.iv_image);
        loadingOverlay = findViewById(R.id.loading_overlay);
        tvError = findViewById(R.id.tv_error);
        frameContainer = findViewById(R.id.frame_image_container);

        scaleDetector = new ScaleGestureDetector(this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float scaleFactor = detector.getScaleFactor();
                        float newScale = currentScale * scaleFactor;
                        newScale = Math.max(minScale, Math.min(maxScale, newScale));

                        float deltaScale = newScale / currentScale;
                        matrix.postScale(deltaScale, deltaScale,
                                detector.getFocusX(), detector.getFocusY());
                        currentScale = newScale;
                        ivImage.setImageMatrix(matrix);
                        return true;
                    }
                });

        frameContainer.setOnTouchListener(this::onTouch);

        // Long-press to save
        ivImage.setOnLongClickListener(v -> {
            if (currentBitmap == null) return false;
            showSaveDialog();
            return true;
        });

        loadImage();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 与 Text/Code/CSV 查看器组能力一致（走查 #31：图片查看器复制路径入口缺失）。
        menu.add(Menu.NONE, MENU_COPY_PATH_ID, Menu.NONE, "复制路径")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        // 保存入口发现性（第十五轮观察：长按无可见按钮）：溢出菜单常驻入口，与长按同一语义。
        menu.add(Menu.NONE, MENU_SAVE_IMAGE_ID, Menu.NONE, "保存图片")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        // #51 分享图片：原字节直发（零重编码，与「保存图片」同一数据通道）。
        menu.add(Menu.NONE, MENU_SHARE_ID, Menu.NONE, "分享图片")
                .setIcon(R.drawable.ic_share)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == MENU_COPY_PATH_ID) {
            String path = getIntent() != null ? getIntent().getStringExtra("file_path") : null;
            if (path == null || path.isEmpty()) {
                UiUtils.showToast(this, "路径不可用");
            } else {
                UiUtils.copyRemotePath(this, path);
            }
            return true;
        }
        if (item.getItemId() == MENU_SAVE_IMAGE_ID) {
            if (currentBitmap == null) {
                UiUtils.showToast(this, "图片尚未加载完成");
            } else {
                showSaveDialog();
            }
            return true;
        }
        if (item.getItemId() == MENU_SHARE_ID) {
            if (originalBytes == null || originalBytes.length == 0) {
                UiUtils.showToast(this, "图片尚未加载完成");
                return true;
            }
            String mime = ShareHelper.mimeForImage(originalBytes);
            String displayName = ShareHelper.suggestImageFileName(
                    fileName != null ? fileName : "image", mime);
            UiUtils.shareBytes(this, originalBytes, displayName, mime, "分享图片");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSaveDialog() {
        DialogHelper.showConfirmDialog(this,
                "保存图片",
                "将图片保存到相册？",
                "保存", "取消",
                d -> saveImageToGallery(),
                d -> {});
    }

    private void saveImageToGallery() {
        if (currentBitmap == null) return;

        // #44 原图直存：保存 readFileBytes 原始字节（零重编码）→ 保原分辨率/EXIF 元数据/
        // 原格式与体积，且文件名-内容-MIME 三方一致；旧实现为降采样位图+PNG 重编码+
        // displayName 保留 .jpg+MIME 硬编码 image/png（内容被改、三方不一致）。
        // MIME 按 magic bytes 判定；raw=false 仅在 MIME 无法识别时回退位图压缩（理论不可达，
        // 查看器仅接受位图集，防御性兜底保持旧行为）。
        String mime = ImageSaveHelper.sniffMime(originalBytes);
        String displayName = ImageSaveHelper.ensureDisplayName(
                fileName != null ? fileName : "image_" + System.currentTimeMillis(), mime);
        boolean raw = mime != null && originalBytes != null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
                values.put(MediaStore.Images.Media.MIME_TYPE, raw ? mime : "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (raw) {
                            os.write(originalBytes, 0, originalBytes.length);
                        } else {
                            currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                        }
                        UiUtils.showToast(this, "已保存到相册");
                    }
                }
            } else {
                // Android 9 and below : direct file write
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File imageFile = new File(picturesDir, displayName);
                try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                    if (raw) {
                        fos.write(originalBytes, 0, originalBytes.length);
                    } else {
                        currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    }
                    UiUtils.showToast(this, "已保存到相册");
                }

                // Notify media scanner
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, imageFile.getAbsolutePath());
                getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            }
        } catch (Exception e) {
            UiUtils.showToast(this, "保存失败: " + UiUtils.errorMessage(e));
        }
    }

    private boolean onTouch(View v, MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouch.set(event.getX(), event.getY());
                mode = DRAG;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mode = ZOOM;
                break;

            case MotionEvent.ACTION_MOVE:
                if (mode == DRAG && !scaleDetector.isInProgress()) {
                    float dx = event.getX() - lastTouch.x;
                    float dy = event.getY() - lastTouch.y;
                    matrix.postTranslate(dx, dy);
                    ivImage.setImageMatrix(matrix);
                    lastTouch.set(event.getX(), event.getY());
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = NONE;
                animateToBounds();
                break;
        }
        return true;
    }

    private void animateToBounds() {
        if (ivImage.getDrawable() == null) return;

        float[] values = new float[9];
        matrix.getValues(values);

        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];
        float scaleX = values[Matrix.MSCALE_X];

        float imgW = ivImage.getDrawable().getIntrinsicWidth();
        float imgH = ivImage.getDrawable().getIntrinsicHeight();
        float viewW = ivImage.getWidth();
        float viewH = ivImage.getHeight();

        float scaledW = imgW * scaleX;
        float scaledH = imgH * scaleX;

        // Calculate bounds
        float minTransX = viewW - scaledW;
        float maxTransX = 0f;
        float minTransY = viewH - scaledH;
        float maxTransY = 0f;

        // If image is smaller than view, center it
        if (scaledW <= viewW) {
            minTransX = (viewW - scaledW) / 2f;
            maxTransX = minTransX;
        }
        if (scaledH <= viewH) {
            minTransY = (viewH - scaledH) / 2f;
            maxTransY = minTransY;
        }

        float targetScale = currentScale;
        if (currentScale < minScale) targetScale = minScale;
        if (currentScale > maxScale) targetScale = maxScale;

        float targetTransX = Math.max(minTransX, Math.min(maxTransX, transX));
        float targetTransY = Math.max(minTransY, Math.min(maxTransY, transY));

        // Only animate if out of bounds
        if (Math.abs(transX - targetTransX) < 1f && Math.abs(transY - targetTransY) < 1f && Math.abs(currentScale - targetScale) < 0.01f) {
            return;
        }

        if (currentAnimator != null && currentAnimator.isRunning()) {
            currentAnimator.cancel();
        }

        final float fromScale = currentScale;
        final float fromTransX = transX;
        final float fromTransY = transY;
        final float toScale = targetScale;
        final float toTransX = targetTransX;
        final float toTransY = targetTransY;

        currentAnimator = ValueAnimator.ofFloat(0f, 1f);
        currentAnimator.setDuration(300);
        currentAnimator.setInterpolator(new DecelerateInterpolator());
        currentAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();

            float newScale = fromScale + (toScale - fromScale) * fraction;
            float newTransX = fromTransX + (toTransX - fromTransX) * fraction;
            float newTransY = fromTransY + (toTransY - fromTransY) * fraction;

            matrix.reset();
            matrix.postScale(newScale, newScale);
            matrix.postTranslate(newTransX, newTransY);

            ivImage.setImageMatrix(matrix);
            currentScale = newScale;
        });
        currentAnimator.start();
    }

    private void loadImage() {
        String filePath = getIntent().getStringExtra("file_path");
        if (filePath == null) { finish(); return; }

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }
        tvError.setVisibility(View.GONE);

        SshManager.getInstance().readFileBytes(filePath,
                new SshManager.FileBytesCallback() {
                    @Override
                    public void onSuccess(byte[] bytes) {
                        // #44 原图直存：保留原始字节引用（保存时直接写入，零重编码、
                        // 保 EXIF/分辨率/格式），仅显示走降采样解码。
                        originalBytes = bytes;
                        runOnUiThread(() -> decodeAndDisplay(bytes));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            if (loadingOverlay != null) {
                                loadingOverlay.setVisibility(View.GONE);
                            }
                            tvError.setVisibility(View.VISIBLE);
                            tvError.setText("加载失败: " + message);
                        });
                    }
                });
    }

    private void decodeAndDisplay(byte[] bytes) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

        int imgW = opts.outWidth;
        int imgH = opts.outHeight;
        if (imgW <= 0 || imgH <= 0) {
            if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.GONE);
            }
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("无法解码图片");
            return;
        }

        int maxDim = 2048;
        int sampleSize = 1;
        while ((imgW / sampleSize) > maxDim || (imgH / sampleSize) > maxDim) {
            sampleSize *= 2;
        }

        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sampleSize;
        currentBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

        if (currentBitmap == null) {
            if (loadingOverlay != null) {
                loadingOverlay.setVisibility(View.GONE);
            }
            tvError.setVisibility(View.VISIBLE);
            tvError.setText("图片解码失败");
            return;
        }

        // EXIF 方向修正（#42）：相机/DSLR 竖拍照片带 Orientation 标签，BitmapFactory
        // 解码时忽略 → 横置显示；按标签旋转位图，失败静默回退原图。
        int exifDegrees = ImageExifHelper.orientationDegrees(bytes);
        if (exifDegrees != 0 && currentBitmap != null) {
            currentBitmap = ImageExifHelper.rotateBitmap(currentBitmap, exifDegrees);
        }

        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.GONE);
        }
        ivImage.setImageBitmap(currentBitmap);
        ivImage.post(this::fitImageToView);
    }

    private void fitImageToView() {
        if (ivImage.getDrawable() == null) return;

        float viewW = ivImage.getWidth();
        float viewH = ivImage.getHeight();
        float imgW = ivImage.getDrawable().getIntrinsicWidth();
        float imgH = ivImage.getDrawable().getIntrinsicHeight();

        if (viewW <= 0 || viewH <= 0 || imgW <= 0 || imgH <= 0) return;

        float scaleX = viewW / imgW;
        float scaleY = viewH / imgH;
        float scale = Math.min(scaleX, scaleY);

        matrix.reset();
        matrix.postScale(scale, scale);

        float scaledW = imgW * scale;
        float scaledH = imgH * scale;
        float dx = (viewW - scaledW) / 2f;
        float dy = (viewH - scaledH) / 2f;
        matrix.postTranslate(dx, dy);

        ivImage.setImageMatrix(matrix);
        currentScale = 1f;
        minScale = 0.5f;
        maxScale = 5f;
    }
}
