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

    private Bitmap currentBitmap;
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

        try {
            String displayName = fileName != null ? fileName : "image_" + System.currentTimeMillis();
            if (!displayName.contains(".")) displayName += ".png";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
                        UiUtils.showToast(this, "已保存到相册");
                    }
                }
            } else {
                // Android 9 and below : direct file write
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File imageFile = new File(picturesDir, displayName);
                try (FileOutputStream fos = new FileOutputStream(imageFile)) {
                    currentBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    UiUtils.showToast(this, "已保存到相册");
                }

                // Notify media scanner
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, imageFile.getAbsolutePath());
                getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            }
        } catch (Exception e) {
            UiUtils.showToast(this, "保存失败: " + e.getMessage());
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
