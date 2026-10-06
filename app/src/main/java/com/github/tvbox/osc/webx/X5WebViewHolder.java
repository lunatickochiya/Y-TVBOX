package com.github.tvbox.osc.webx;

import android.view.View;

import java.util.Map;

/**
 * X5(TBS)内核 WebView 的抽象封装.
 *
 * 实现类只存在于 x5 编译版本(app/src/x5), 普通版本由 app/src/std 提供空实现,
 * 这样主代码不需要引用 TBS SDK 的类, 普通版本也不会打包 X5 内核.
 */
public interface X5WebViewHolder {

    interface Host {
        /**
         * 嗅探拦截决策.
         *
         * @return 0=放行, 1=拦截(返回空响应), 2=favicon 空响应
         */
        int onInterceptRequest(String url, Map<String, String> headers);

        /** 页面加载完成(主线程回调) */
        void onPageFinished(String url);
    }

    View getView();

    void loadUrl(String url, Map<String, String> headers);

    void stopLoading();

    void destroy();

    void evaluateJavascript(String js);

    void setUserAgentString(String userAgent);

    void setBlockNetworkImage(boolean block);
}
