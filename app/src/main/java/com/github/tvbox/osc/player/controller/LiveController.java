package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ProgressBar;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 直播控制器
 */

public class LiveController extends BaseController {
    protected ProgressBar mLoading;
    private int minFlingDistance = 100;             //最小识别距离
    private int minFlingVelocity = 10;              //最小识别速度

    public LiveController(@NotNull Context context) {
        super(context);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.player_live_control_view;
    }

    @Override
    protected void initView() {
        super.initView();
        mLoading = findViewById(R.id.loading);
    }

    public interface LiveControlListener {
        boolean singleTap(MotionEvent e);

        void longPress();

        void playStateChanged(int playState);

        void changeSource(int direction);

        /** 触屏左半屏上下滑动切台: 1=下一个频道, -1=上一个频道 */
        void changeChannel(int direction);
    }

    private LiveController.LiveControlListener listener = null;

    public void setListener(LiveController.LiveControlListener listener) {
        this.listener = listener;
    }

    // 触屏左半屏上下滑动切台: 累计滑动距离, 超过阈值换台(带冷却, 手势结束后自动清零)
    private float channelSlideDelta = 0f;
    private long channelSlideLastTime = 0L;
    private long channelSwitchLastTime = 0L;

    /**
     * 直播界面左半屏上下滑动改为切换频道(亮度调整已移到直播设置).
     * deltaY &gt; 0 表示手指上滑 → 下一个频道.
     */
    @Override
    protected void slideToChangeBrightness(float deltaY) {
        long now = System.currentTimeMillis();
        if (now - channelSlideLastTime > 300) {
            channelSlideDelta = 0f;
        }
        channelSlideLastTime = now;
        channelSlideDelta += deltaY;
        int threshold = Math.max(getMeasuredHeight() / 8, 60);
        if (Math.abs(channelSlideDelta) >= threshold && now - channelSwitchLastTime >= 400) {
            if (listener != null) {
                listener.changeChannel(channelSlideDelta > 0 ? 1 : -1);
            }
            channelSlideDelta = 0f;
            channelSwitchLastTime = now;
        }
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (listener.singleTap(e))
            return true;
        return super.onSingleTapConfirmed(e);
    }

    @Override
    public void onLongPress(MotionEvent e) {
        listener.longPress();
        super.onLongPress(e);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        listener.playStateChanged(playState);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1.getX() - e2.getX() > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
            listener.changeSource(-1);          //左滑
        } else if (e2.getX() - e1.getX() > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
            listener.changeSource(1);           //右滑
        } else if (e1.getY() - e2.getY() > minFlingDistance && Math.abs(velocityY) > minFlingVelocity) {
        } else if (e2.getY() - e1.getY() > minFlingDistance && Math.abs(velocityY) > minFlingVelocity) {
        }
        return false;
    }
}
