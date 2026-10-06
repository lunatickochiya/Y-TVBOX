package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;

/**
 * 直播界面亮度调节对话框.
 * 原左半屏上下滑动调亮度已改为切换频道, 亮度调整移到直播设置里.
 */
public class LiveBrightnessDialog extends BaseDialog {

    public interface OnBrightnessChangeListener {
        /** @param brightness 0.01~1.0 */
        void onBrightnessChanged(float brightness);
    }

    private static final int MIN_PROGRESS = 10;

    public LiveBrightnessDialog(@NonNull Context context, float current, final OnBrightnessChangeListener listener) {
        super(context);
        setContentView(R.layout.dialog_live_brightness);
        SeekBar seekBar = findViewById(R.id.seekBrightness);
        final TextView value = findViewById(R.id.tvBrightnessValue);
        int progress = (int) (current * 100f);
        if (progress < MIN_PROGRESS || progress > 100) {
            progress = 50;
        }
        seekBar.setMax(100);
        seekBar.setProgress(progress);
        value.setText("亮度 " + progress + "%");
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int p, boolean fromUser) {
                if (p < MIN_PROGRESS) {
                    p = MIN_PROGRESS;
                }
                value.setText("亮度 " + p + "%");
                if (listener != null) {
                    listener.onBrightnessChanged(p / 100f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
    }
}
