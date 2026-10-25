package com.github.catvod.net;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Compatibility subset of catvod's modern Net class. csp_* spiders compiled
 * for the newer host API can use these methods without starting their own Go
 * proxy. The full feature set (WebSocket/media cache) is intentionally not
 * needed for search/playback.
 */
public final class Net implements AutoCloseable {

    public interface Cookies {
        String getCookie(String url);

        boolean setCookie(String url, String value);
    }

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final Cookies cookies;

    public Net() {
        this(null);
    }

    public Net(Cookies cookies) {
        this.cookies = cookies;
    }

    public Net(Cookies cookies, String siteKey) {
        this.cookies = cookies;
    }

    public Net session() {
        return this;
    }

    public String get(String url) {
        return get(url, Collections.<String, String>emptyMap());
    }

    public String get(String url, Map<String, String> headers) {
        return execute("GET", url, null, headers, 15000);
    }

    public String get(String url, int timeout) {
        return execute("GET", url, null, Collections.<String, String>emptyMap(), timeout);
    }

    public String post(String url, String json) {
        return post(url, json, Collections.<String, String>emptyMap());
    }

    public String post(String url, String json, Map<String, String> headers) {
        return execute("POST", url, json, headers, 15000);
    }

    public String json(String url, String options) {
        return get(url);
    }

    public String jsonText(String url, String options) {
        return get(url);
    }

    public String req(String url, String json) {
        return post(url, json);
    }

    public Result request(String url, NetOptions options) {
        NetOptions value = options == null ? new NetOptions() : options;
        try {
            String body = execute(value.getMethod(), url, value.getBody(), value.getHeader(), value.getTimeout());
            return new Result(200, Collections.<String, String>emptyMap(), body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8), null);
        } catch (Throwable e) {
            return new Result(0, Collections.<String, String>emptyMap(), new byte[0], e.toString());
        }
    }

    private String execute(String method, String url, String body, Map<String, String> headers, int timeout) {
        try {
            OkHttpClient client = OkHttp.client().newBuilder()
                    .connectTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build();
            Request.Builder builder = new Request.Builder().url(url);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) builder.header(entry.getKey(), entry.getValue());
                }
            }
            if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
                RequestBody requestBody = RequestBody.create(JSON, body == null ? "" : body);
                builder.method(method.toUpperCase(), requestBody);
            } else {
                builder.get();
            }
            try (Response response = client.newCall(builder.build()).execute()) {
                if (response.body() == null) return "";
                return response.body().string();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String getCookie(String url) {
        return cookies == null ? null : cookies.getCookie(url);
    }

    public boolean setCookie(String url, String value) {
        return cookies != null && cookies.setCookie(url, value);
    }

    @Override
    public void close() {
    }

    public static final class Result {
        public final int code;
        public final Map<String, String> headers;
        public final byte[] body;
        public final String error;

        public Result(int code, Map<String, String> headers, byte[] body, String error) {
            this.code = code;
            this.headers = headers;
            this.body = body;
            this.error = error;
        }

        public String text() {
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        }

        public static Result failure(Throwable error) {
            return new Result(0, new HashMap<String, String>(), new byte[0], error == null ? "" : error.toString());
        }
    }
}
