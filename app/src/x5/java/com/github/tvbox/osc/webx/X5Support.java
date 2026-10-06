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
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * X5(TBS)内核实现, 只存在于 x5 编译版本(app/src/x5).
 *
 * 内核为内置方案: APK 里打包了对应 ABI 的官方内核文件(assets/tbs/tbs_core_*.tbs),
 * 安装时复制到应用私有目录并调用 {@link QbSdk#installLocalTbsCore} 本地安装,
 * 不从网络下载; 安装完成后需重启应用生效.
 */
public final class X5Support {

    private static final String TAG = "YTVBoxX5";
    /** 内置内核目录(assets/tbs/tbs_core_<版本>_...tbs) */
    private static final String CORE_ASSET_DIR = "tbs";
    /** 每个进程只尝试安装一次内置内核 */
    private static boolean sLocalCoreTried = false;
    private static boolean sLocalCoreInstalled = false;

    private X5Support() {
    }

    public static boolean isSupported() {
        return true;
    }

    /** 已安装的内核版本, 0 表示未安装 */
    public static int getVersion(Context context) {
        try {
            return QbSdk.getTbsVersion(context.getApplicationContext());
        } catch (Throwable e) {
            return 0;
        }
    }

    /** X5 内核是否可用(已安装并加载) */
    public static boolean canLoadX5(Context context) {
        try {
            return QbSdk.canLoadX5(context.getApplicationContext());
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 初始化 X5: 内核已安装时加载, 否则不做任何事(由设置界面触发本地安装),
     * 不使用 TBS 的联网下载.
     */
    public static void init(Context context, final InitCallback callback) {
        Context app = context.getApplicationContext();
        if (getVersion(app) > 0) {
            initEnv(app, callback);
            return;
        }
        if (callback != null) {
            callback.onResult(false);
        }
    }

    private static void initEnv(Context app, final InitCallback callback) {
        try {
            QbSdk.initX5Environment(app, new QbSdk.PreInitCallback() {
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

    /** 是否内置了内核文件(assets/tbs/*.tbs) */
    public static boolean hasLocalCore(Context context) {
        try {
            String[] files = context.getAssets().list(CORE_ASSET_DIR);
            if (files != null) {
                for (String f : files) {
                    if (f.endsWith(".tbs")) return true;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 安装内置的 X5 内核(assets/tbs/tbs_core_<版本>_...tbs):
     * 复制到私有目录后调用 QbSdk.installLocalTbsCore, 重启应用生效.
     *
     * @return 本次是否触发了安装
     */
    public static boolean installLocalCore(Context context) {
        if (sLocalCoreTried) return sLocalCoreInstalled;
        sLocalCoreTried = true;
        Context app = context.getApplicationContext();
        try {
            if (getVersion(app) > 0) return false;
            String[] files = app.getAssets().list(CORE_ASSET_DIR);
            if (files == null || files.length == 0) return false;
            String coreName = null;
            for (String f : files) {
                if (f.endsWith(".tbs")) {
                    coreName = f;
                    break;
                }
            }
            if (coreName == null) return false;
            int version = parseCoreVersion(coreName);
            if (version <= 0) return false;
            File dir = new File(app.getFilesDir(), CORE_ASSET_DIR);
            if (!dir.exists() && !dir.mkdirs()) return false;
            File coreFile = new File(dir, coreName);
            if (!coreFile.exists() || coreFile.length() == 0) {
                copyAsset(app, CORE_ASSET_DIR + "/" + coreName, coreFile);
            }
            if (!coreFile.exists() || coreFile.length() == 0) return false;
            QbSdk.reset(app); // 清除旧的 TBS 状态(每个进程最多调用一次)
            QbSdk.installLocalTbsCore(app, version, coreFile.getAbsolutePath());
            sLocalCoreInstalled = true;
            Log.i(TAG, "install local X5 core version=" + version + " path=" + coreFile);
            return true;
        } catch (Throwable e) {
            e.printStackTrace();
            return false;
        }
    }

    /** tbs_core_046515_... -> 46515 */
    private static int parseCoreVersion(String fileName) {
        try {
            String prefix = "tbs_core_";
            int start = fileName.indexOf(prefix);
            if (start < 0) return 0;
            start += prefix.length();
            int end = fileName.indexOf('_', start);
            String versionStr = end > start ? fileName.substring(start, end) : fileName.substring(start);
            return Integer.parseInt(versionStr);
        } catch (Throwable e) {
            return 0;
        }
    }

    private static void copyAsset(Context context, String assetName, File dest) throws Exception {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = context.getAssets().open(assetName);
            out = new FileOutputStream(dest);
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            out.flush();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
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
