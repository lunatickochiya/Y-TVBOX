package com.github.tvbox.osc.webx;

import android.view.View;

import java.util.Map;

/**
 * 嗅探 WebView 引擎的抽象封装(系统 WebView 之外的可选内核: X5 / Gecko).
 *
 * 实现类位于各内核的编译版本源集(app/src/x5, app/src/gecko), 普通版本由空实现源集
 * (app/src/std, app/src/geckostub)提供, 主代码不直接引用 TBS / GeckoView 的类.
 */
public interface WebViewHolder {

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
