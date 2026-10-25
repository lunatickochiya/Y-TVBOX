package com.github.catvod.net;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

/** Minimal option object compatible with modern csp_* spiders. */
public class NetOptions {

    private static final Gson GSON = new Gson();

    private String method;
    private String body;
    private JsonElement headers;
    private Integer timeout;
    private Integer redirect;

    public static NetOptions from(String json) {
        try {
            NetOptions value = json == null || json.isEmpty() ? null : GSON.fromJson(json, NetOptions.class);
            return value == null ? new NetOptions() : value;
        } catch (Throwable e) {
            return new NetOptions();
        }
    }

    public String getMethod() {
        return method == null || method.isEmpty() ? "get" : method;
    }

    public String getBody() {
        return body;
    }

    public int getTimeout() {
        return timeout == null || timeout <= 0 ? 15000 : timeout;
    }

    public boolean isRedirect() {
        return redirect == null || redirect != 0;
    }

    public Map<String, String> getHeader() {
        Map<String, String> result = new HashMap<>();
        if (headers == null) return result;
        try {
            JsonElement value = headers;
            if (value.isJsonPrimitive()) value = JsonParser.parseString(value.getAsString());
            JsonObject object = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        } catch (Throwable ignored) {
        }
        return result;
    }
}
