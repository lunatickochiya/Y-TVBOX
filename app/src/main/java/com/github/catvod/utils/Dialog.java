package com.github.catvod.utils;

import org.json.JSONObject;

/** Compatibility holder for modern csp_* spiders. */
public final class Dialog implements AutoCloseable {

    public JSONObject show(JSONObject options) throws Exception {
        return show(options, null);
    }

    public JSONObject show(JSONObject options, Callback onAction) throws Exception {
        return null;
    }

    @Override
    public void close() {
    }

    public interface Callback {
        JSONObject onAction(JSONObject event) throws Exception;
    }
}
