package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

public class AboutDialog extends BaseDialog {

    public AboutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_about);
        TextView tvVersionInfo = findViewById(R.id.tvVersionInfo);
        if (tvVersionInfo != null) {
            tvVersionInfo.setText("版本: " + BuildConfig.VERSION_NAME
                    + "\n编译时间: " + formatBuildTime(BuildConfig.BUILD_TIME));
        }
    }

    /** 20261018_1016 -> 2026-10-18 10:16 */
    public static String formatBuildTime(String raw) {
        try {
            if (raw != null && raw.length() >= 13) {
                return raw.substring(0, 4) + "-" + raw.substring(4, 6) + "-" + raw.substring(6, 8)
                        + " " + raw.substring(9, 11) + ":" + raw.substring(11, 13);
            }
        } catch (Throwable ignored) {
        }
        return raw == null ? "" : raw;
    }
}
