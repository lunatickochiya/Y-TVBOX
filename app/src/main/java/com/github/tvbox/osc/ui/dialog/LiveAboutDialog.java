package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Build;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.BuildConfig;
import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * Y-Live「关于」: 应用名 / 版本 / 编译时间 / 包名 / 系统版本.
 */
public class LiveAboutDialog extends BaseDialog {

    public LiveAboutDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_live_about);
        setCanceledOnTouchOutside(true);
        TextView tvAppName = findViewById(R.id.tvAppName);
        TextView tvAboutInfo = findViewById(R.id.tvAboutInfo);
        tvAppName.setText("Y-Live");
        StringBuilder sb = new StringBuilder();
        sb.append("版本: ").append(BuildConfig.VERSION_NAME).append('\n');
        sb.append("编译时间: ").append(formatBuildTime(BuildConfig.BUILD_TIME)).append('\n');
        sb.append("包名: ").append(context.getPackageName()).append('\n');
        sb.append("系统: Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(')');
        tvAboutInfo.setText(sb.toString());
        findViewById(R.id.btnOk).setOnClickListener(v -> dismiss());
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
