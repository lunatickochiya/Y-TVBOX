package com.github.catvod;

import android.app.Activity;
import android.content.Context;

import java.lang.ref.WeakReference;

/** Minimal host runtime for modern csp_* spiders. */
public final class Runtime {

    private static WeakReference<Context> context;
    private static WeakReference<Activity> activity;

    private Runtime() {
    }

    public static void set(Context value) {
        context = new WeakReference<>(value);
    }

    public static Context context() {
        return context == null ? null : context.get();
    }

    public static void setActivity(Activity value) {
        activity = new WeakReference<>(value);
    }

    public static Activity activity() {
        Activity value = activity == null ? null : activity.get();
        if (value == null || value.isFinishing()) return null;
        return value;
    }

    public static void toast(String text) {
        // Optional cosmetic callback used by some spiders; the app's UI layer
        // already reports errors. No-op is safe here.
    }
}
