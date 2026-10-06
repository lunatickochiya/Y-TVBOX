package com.github.tvbox.osc.webx;

import android.content.Context;

/**
 * 非 Gecko 版本的空实现.
 * gecko 版本由 app/src/gecko 下的同名类提供 GeckoView 实现.
 */
public final class GeckoSupport {

    private GeckoSupport() {
    }

    public static boolean isSupported() {
        return false;
    }

    public static void init(Context context, InitCallback callback) {
        if (callback != null) {
            callback.onResult(false);
        }
    }

    public static String getVersionName() {
        return "";
    }

    public static WebViewHolder createWebView(Context context, WebViewHolder.Host host) {
        return null;
    }

    public interface InitCallback {
        void onResult(boolean ready);
    }
}
