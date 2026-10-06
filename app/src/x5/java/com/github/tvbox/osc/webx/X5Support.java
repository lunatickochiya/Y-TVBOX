package com.github.tvbox.osc.webx;

import android.content.Context;
import android.util.Log;
import android.view.View;

import com.tencent.smtt.export.external.interfaces.ConsoleMessage;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.export.external.interfaces.SslError;
import com.tencent.smtt.export.external.interfaces.SslErrorHandler;
import com.tencent.smtt.export.external.interfaces.WebResourceRequest;
import com.tencent.smtt.export.external.interfaces.WebResourceResponse;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsDownloader;
import com.tencent.smtt.sdk.TbsListener;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * X5(TBS)内核实现, 只存在于 x5 编译版本(app/src/x5).
 *
 * 内核未安装时 {@link #init} 会让 TBS 在后台自动下载,
 * 下载完成后 {@link #canLoadX5} 返回 true, 即可创建 X5 WebView.
 */
public final class X5Support {

    private static final String TAG = "YTVBoxX5";

    private X5Support() {
    }

    public static boolean isSupported() {
        return true;
    }

    /** 初始化 X5 内核, 未安装时 TBS 会在后台自动下载 */
    public static void init(Context context, final InitCallback callback) {
        try {
            QbSdk.initX5Environment(context.getApplicationContext(), new QbSdk.PreInitCallback() {
                @Override
                public void onCoreInitFinished() {
                }

                @Override
                public void onViewInitFinished(boolean isX5Core) {
                    Log.i(TAG, "X5 init finished, x5Core=" + isX5Core);
                    if (callback != null) {
                        callback.onResult(isX5Core);
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) {
                callback.onResult(false);
            }
        }
    }

    /** X5 内核是否可用 */
    public static boolean canLoadX5(Context context) {
        try {
            return QbSdk.canLoadX5(context.getApplicationContext());
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    /** X5 内核版本号, 0 表示未安装 */
    public static int getVersion(Context context) {
        try {
            return QbSdk.getTbsVersion(context.getApplicationContext());
        } catch (Throwable e) {
            return 0;
        }
    }

    /** 主动触发内核下载 */
    public static void startDownload(Context context) {
        try {
            TbsDownloader.startDownload(context.getApplicationContext());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 重置内核(删除后重新下载) */
    public static void reset(Context context) {
        try {
            QbSdk.reset(context.getApplicationContext());
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /** 下载/安装进度监听 */
    public static void setDownloadListener(final DownloadListener listener) {
        try {
            QbSdk.setTbsListener(new TbsListener() {
                @Override
                public void onDownloadFinish(int code) {
                    if (listener != null) {
                        listener.onDownloadFinished(code);
                    }
                }

                @Override
                public void onInstallFinish(int code) {
                    if (listener != null) {
                        listener.onInstallFinished(code);
                    }
                }

                @Override
                public void onDownloadProgress(int progress) {
                    if (listener != null) {
                        listener.onDownloadProgress(progress);
                    }
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static X5WebViewHolder createWebView(Context context, X5WebViewHolder.Host host) {
        try {
            return new Holder(context, host);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public interface InitCallback {
        void onResult(boolean x5Ready);
    }

    public interface DownloadListener {
        void onDownloadProgress(int progress);

        void onDownloadFinished(int code);

        void onInstallFinished(int code);
    }

    private static class Holder implements X5WebViewHolder {

        private final WebView webView;
        private final X5WebViewHolder.Host host;

        Holder(Context context, X5WebViewHolder.Host host) {
            this.host = host;
            this.webView = new WebView(context);
            initSettings();
            initClient();
        }

        @SuppressWarnings("deprecation")
        private void initSettings() {
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
            settings.setAllowContentAccess(true);
            settings.setAllowFileAccess(true);
            settings.setAllowUniversalAccessFromFileURLs(true);
            settings.setAllowFileAccessFromFileURLs(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setJavaScriptCanOpenWindowsAutomatically(true);
            settings.setSupportMultipleWindows(false);
            settings.setBuiltInZoomControls(true);
            settings.setSupportZoom(false);
            settings.setDefaultTextEncodingName("utf-8");
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            settings.setBlockNetworkImage(true);
            try {
                settings.setMediaPlaybackRequiresUserGesture(false);
            } catch (Throwable ignored) {
            }
            try {
                // MIXED_CONTENT_ALWAYS_ALLOW = 0, X5 的 WebSettings 没有公开常量
                settings.setMixedContentMode(0);
            } catch (Throwable ignored) {
            }
            webView.setBackgroundColor(0xFF000000);
            webView.setFocusable(false);
            webView.setFocusableInTouchMode(false);
            webView.clearFocus();
            webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        }

        private void initClient() {
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                    return false;
                }

                @Override
                public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                    return true;
                }

                @Override
                public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                    return true;
                }

                @Override
                public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                    return true;
                }
            });
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                    handler.proceed();
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return false;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return false;
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    if (host != null) {
                        host.onPageFinished(url);
                    }
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    return intercept(request.getUrl().toString(), request.getRequestHeaders());
                }

                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                    return intercept(url, null);
                }
            });
        }

        private WebResourceResponse intercept(String url, Map<String, String> requestHeaders) {
            HashMap<String, String> headers = new HashMap<>();
            if (requestHeaders != null) {
                for (String k : requestHeaders.keySet()) {
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        headers.put(k, " " + requestHeaders.get(k));
                    }
                }
            }
            int decision = host == null ? 0 : host.onInterceptRequest(url, headers);
            if (decision == 2) {
                return new WebResourceResponse("image/x-icon", "UTF-8", null);
            }
            if (decision == 1) {
                return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
            }
            return null;
        }

        @Override
        public View getView() {
            return webView;
        }

        @Override
        public void loadUrl(String url, Map<String, String> headers) {
            webView.stopLoading();
            if (headers != null && !headers.isEmpty()) {
                webView.loadUrl(url, headers);
            } else {
                webView.loadUrl(url);
            }
        }

        @Override
        public void stopLoading() {
            webView.stopLoading();
        }

        @Override
        public void destroy() {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.removeAllViews();
                webView.destroy();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Override
        public void evaluateJavascript(String js) {
            try {
                webView.evaluateJavascript(js, null);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Override
        public void setUserAgentString(String userAgent) {
            if (userAgent != null) {
                webView.getSettings().setUserAgentString(userAgent);
            }
        }

        @Override
        public void setBlockNetworkImage(boolean block) {
            webView.getSettings().setBlockNetworkImage(block);
        }
    }
}
