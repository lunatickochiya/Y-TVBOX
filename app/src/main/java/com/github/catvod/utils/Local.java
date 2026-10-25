package com.github.catvod.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.github.catvod.Runtime;

/** Per-site local storage used by modern csp_* spiders. */
public final class Local {

    private final String name;

    public Local(String siteKey) {
        if (siteKey == null || siteKey.isEmpty()) throw new IllegalArgumentException("Local requires a site key");
        name = "spider_local_" + siteKey;
    }

    public String get(String key) {
        return prefs().getString(key, null);
    }

    public void set(String key, String value) {
        prefs().edit().putString(key, value).apply();
    }

    public void delete(String key) {
        prefs().edit().remove(key).apply();
    }

    public void clear() {
        prefs().edit().clear().apply();
    }

    private SharedPreferences prefs() {
        Context context = Runtime.context();
        if (context == null) throw new IllegalStateException("Local requires an initialized app context");
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }
}
