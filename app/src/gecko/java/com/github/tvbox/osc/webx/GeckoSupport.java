package com.github.tvbox.osc.webx;

import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * GeckoView(Mozilla)嗅探内核实现, 只存在于 gecko 编译版本(app/src/gecko).
 *
 * 请求嗅探通过内置 WebExtension(assets/extensions/sniffer) 的 webRequest 完成:
 * 扩展把每个请求的 URL/请求头上报给 App, 由 App 的嗅探逻辑决定是否播放;
 * 点击选择器(evaluateJavascript) 通过扩展的 native 端口在页面里执行 JS.
 */
public final class GeckoSupport {

    private static final String TAG = "YTVBoxGecko";
    private static final String EXTENSION_LOCATION = "resource://android/assets/extensions/sniffer/";
    private static final String EXTENSION_ID = "sniffer@ytvbox.local";
    private static final String NATIVE_APP = "browser";

    private static GeckoRuntime sRuntime;

    private GeckoSupport() {
    }

    public static boolean isSupported() {
        return true;
    }

    public static String getVersionName() {
        return "GeckoView 144";
    }

    public static synchronized GeckoRuntime runtime(Context context) {
        if (sRuntime == null) {
            sRuntime = GeckoRuntime.create(context.getApplicationContext());
        }
        return sRuntime;
    }

    /** 初始化: 注册内置嗅探扩展(只需一次, 重复调用会复用已安装的扩展) */
    public static void init(Context context, final InitCallback callback) {
        try {
            runtime(context).getWebExtensionController()
                    .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                    .accept(extension -> {
                        Log.i(TAG, "sniffer extension ready: " + extension);
                        if (callback != null) {
                            callback.onResult(true);
                        }
                    }, e -> {
                        Log.e(TAG, "sniffer extension install failed", e);
                        if (callback != null) {
                            callback.onResult(false);
                        }
                    });
        } catch (Throwable e) {
            e.printStackTrace();
            if (callback != null) {
                callback.onResult(false);
            }
        }
    }

    public static WebViewHolder createWebView(Context context, WebViewHolder.Host host) {
        try {
            return new Holder(context, host);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public interface InitCallback {
        void onResult(boolean ready);
    }

    private static class Holder implements WebViewHolder {

        private final GeckoView view;
        private final GeckoSession session;
        private final WebViewHolder.Host host;
        private WebExtension.Port port;
        private String pendingJs;
        private String currentUrl = "";

        Holder(Context context, WebViewHolder.Host host) {
            this.host = host;
            this.session = new GeckoSession();
            this.view = new GeckoView(context);
            session.open(runtime(context));
            session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
                @Override
                public void onPageStop(GeckoSession s, boolean success) {
                    if (host != null && currentUrl != null && !currentUrl.isEmpty() && !"about:blank".equals(currentUrl)) {
                        host.onPageFinished(currentUrl);
                    }
                }
            });
            session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
                @Override
                public void onLocationChange(GeckoSession s, String url,
                                             List<GeckoSession.PermissionDelegate.ContentPermission> perms) {
                    currentUrl = url;
                }

                @Override
                public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession s, GeckoSession.NavigationDelegate.LoadRequest request) {
                    sniff(request);
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                }

                @Override
                public GeckoResult<AllowOrDeny> onSubframeLoadRequest(GeckoSession s, GeckoSession.NavigationDelegate.LoadRequest request) {
                    sniff(request);
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW);
                }

                @Override
                public GeckoResult<GeckoSession> onNewSession(GeckoSession s, String uri) {
                    // 嗅探用 WebView 不弹新窗口
                    return GeckoResult.fromValue(null);
                }
            });
            session.setPromptDelegate(new GeckoSession.PromptDelegate() {
                @Override
                public GeckoResult<PromptResponse> onAlertPrompt(GeckoSession s, AlertPrompt prompt) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }

                @Override
                public GeckoResult<PromptResponse> onButtonPrompt(GeckoSession s, ButtonPrompt prompt) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }

                @Override
                public GeckoResult<PromptResponse> onTextPrompt(GeckoSession s, TextPrompt prompt) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }

                @Override
                public GeckoResult<PromptResponse> onAuthPrompt(GeckoSession s, AuthPrompt prompt) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }

                @Override
                public GeckoResult<PromptResponse> onChoicePrompt(GeckoSession s, ChoicePrompt prompt) {
                    return GeckoResult.fromValue(prompt.dismiss());
                }
            });
            view.setSession(session);
            view.setFocusable(false);
            view.setFocusableInTouchMode(false);
            view.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            // 安装/复用内置嗅探扩展, 并挂上消息代理(请求上报 + eval 端口)
            runtime(context).getWebExtensionController()
                    .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
                    .accept(extension -> {
                        if (extension != null) {
                            extension.setMessageDelegate(messageDelegate, NATIVE_APP);
                        }
                    }, e -> Log.e(TAG, "sniffer extension install failed", e));
        }

        private void sniff(GeckoSession.NavigationDelegate.LoadRequest request) {
            if (host == null || request == null || request.uri == null || request.uri.isEmpty()) return;
            try {
                host.onInterceptRequest(request.uri, new HashMap<String, String>());
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        private final WebExtension.MessageDelegate messageDelegate = new WebExtension.MessageDelegate() {
            @Override
            public GeckoResult<Object> onMessage(final @NonNull String nativeApp, final @NonNull Object message,
                                                 final @NonNull WebExtension.MessageSender sender) {
                try {
                    if (message instanceof JSONObject) {
                        JSONObject json = (JSONObject) message;
                        if ("request".equals(json.optString("type"))) {
                            String url = json.optString("url");
                            if (url != null && !url.isEmpty() && host != null) {
                                host.onInterceptRequest(url, parseHeaders(json.optJSONObject("headers")));
                            }
                        }
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            public void onConnect(final @NonNull WebExtension.Port p) {
                port = p;
                p.setDelegate(new WebExtension.PortDelegate() {
                    @Override
                    public void onPortMessage(final @NonNull Object message, final @NonNull WebExtension.Port p2) {
                    }

                    @Override
                    public void onDisconnect(final @NonNull WebExtension.Port p2) {
                        if (port == p2) {
                            port = null;
                        }
                    }
                });
                if (pendingJs != null) {
                    String js = pendingJs;
                    pendingJs = null;
                    evaluateJavascript(js);
                }
            }
        };

        private HashMap<String, String> parseHeaders(JSONObject json) {
            HashMap<String, String> headers = new HashMap<>();
            if (json != null) {
                for (Iterator<String> it = json.keys(); it.hasNext(); ) {
                    String k = it.next();
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        headers.put(k, " " + json.optString(k));
                    }
                }
            }
            return headers;
        }

        @Override
        public View getView() {
            return view;
        }

        @Override
        public void loadUrl(String url, Map<String, String> headers) {
            // GeckoSession.loadUri 不支持自定义请求头, 仅设置 UA
            session.loadUri(url);
        }

        @Override
        public void stopLoading() {
            try {
                session.stop();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Override
        public void destroy() {
            try {
                session.stop();
                view.releaseSession();
                session.close();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Override
        public void evaluateJavascript(String js) {
            if (js == null || js.isEmpty()) return;
            try {
                if (port != null) {
                    JSONObject msg = new JSONObject();
                    msg.put("type", "eval");
                    msg.put("code", js);
                    port.postMessage(msg);
                } else {
                    pendingJs = js;
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Override
        public void setUserAgentString(String userAgent) {
            try {
                if (userAgent != null) {
                    session.getSettings().setUserAgentOverride(userAgent);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        @Override
        public void setBlockNetworkImage(boolean block) {
            // GeckoView 无对应开关
        }
    }
}
