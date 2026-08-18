package com.ssh.mdreader.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.core.view.ViewCompat;
import androidx.customview.widget.ViewDragHelper;

import com.ssh.mdreader.R;

public class SwipeRevealLayout extends FrameLayout {

    private ViewDragHelper dragHelper;
    private View contentView;
    private View actionView;
    private int actionWidth;
    private float initialX;
    private float initialY;
    private boolean isOpen = false;
    private float touchSlop;
    private boolean dragging = false;

    public SwipeRevealLayout(Context context) {
        this(context, null);
    }

    public SwipeRevealLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SwipeRevealLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        dragHelper = ViewDragHelper.create(this, 1.0f, new ViewDragHelper.Callback() {
            @Override
            public boolean tryCaptureView(View child, int pointerId) {
                return child == contentView;
            }

            @Override
            public int clampViewPositionHorizontal(View child, int left, int dx) {
                return Math.max(-actionWidth, Math.min(0, left));
            }

            @Override
            public int getViewHorizontalDragRange(View child) {
                return actionWidth;
            }

            @Override
            public void onViewReleased(View releasedChild, float xvel, float xvel2) {
                super.onViewReleased(releasedChild, xvel, xvel2);
                if (xvel < -100) {
                    open();
                } else if (xvel > 100) {
                    close();
                } else {
                    if (contentView.getLeft() < -actionWidth / 2) {
                        open();
                    } else {
                        close();
                    }
                }
            }
        });
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (contentView == null) contentView = findViewById(R.id.item_content);
        if (actionView == null) {
            View child0 = getChildAt(0);
            if (child0 != null && child0.getId() != R.id.item_content) {
                actionView = child0;
            }
        }
        if (actionView != null) {
            actionWidth = actionView.getMeasuredWidth();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (contentView != null) {
            int contentLeft = isOpen ? -actionWidth : 0;
            contentView.layout(contentLeft, contentView.getTop(),
                    contentLeft + contentView.getWidth(), contentView.getBottom());
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = ev.getRawX();
                initialY = ev.getRawY();
                dragging = false;
                // 不拦截 DOWN，让子 View 收到点击
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getRawX() - initialX);
                float dy = Math.abs(ev.getRawY() - initialY);
                if (!dragging) {
                    if (dx > touchSlop && dx > dy * 1.5f) {
                        // 确认水平滑动，开始拦截
                        dragging = true;
                        // 发送一个 ACTION_CANCEL 给子 View
                        MotionEvent cancel = MotionEvent.obtain(ev);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.onInterceptTouchEvent(cancel);
                        cancel.recycle();
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                dragging = false;
                break;
        }
        // 拖拽中拦截，否则不拦截（让 click 正常传递）
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (dragging) {
            dragHelper.processTouchEvent(event);
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void computeScroll() {
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    public void open() {
        if (contentView == null || actionWidth == 0) return;
        isOpen = true;
        dragHelper.smoothSlideViewTo(contentView, -actionWidth, contentView.getTop());
        invalidate();
    }

    public void close() {
        if (contentView == null) return;
        isOpen = false;
        dragHelper.smoothSlideViewTo(contentView, 0, contentView.getTop());
        invalidate();
    }

    public boolean isOpen() {
        return isOpen;
    }
}
