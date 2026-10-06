package com.github.tvbox.osc.webx;

import android.content.Context;

/**
 * 普通版本(不含 X5 内核)的空实现.
 * 通过 app/build.gradle 的 sourceSets 挂到 normal/python 版本,
 * x5 版本由 app/src/x5 下的同名类提供真正的 TBS 实现.
 */
public final class X5Support {

    private X5Support() {
    }

    public static boolean isSupported() {
        return false;
    }

    public static void init(Context context, InitCallback callback) {
        if (callback != null) {
            callback.onResult(false);
        }
    }

    public static boolean canLoadX5(Context context) {
        return false;
    }

    public static int getVersion(Context context) {
        return 0;
    }

    public static boolean hasLocalCore(Context context) {
        return false;
    }

    public static boolean installLocalCore(Context context) {
        return false;
    }

    public static WebViewHolder createWebView(Context context, WebViewHolder.Host host) {
        return null;
    }

    public interface InitCallback {
        void onResult(boolean x5Ready);
    }
}
