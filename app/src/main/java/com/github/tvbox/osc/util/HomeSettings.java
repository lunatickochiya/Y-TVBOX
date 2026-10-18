package com.github.tvbox.osc.util;

/** Home reload decisions and startup handoffs, independent of Android lifecycle classes. */
public final class HomeSettings {
    public enum Reload { NONE, LIVE, CACHE, NETWORK }

    public static final class Snapshot {
        private final String api;
        private final String source;
        private final int recommendation;
        private final int theme;
        private final int locale;
        private final String live;

        public Snapshot(String api, String source, int recommendation, int theme, int locale) {
            this(api, source, recommendation, theme, locale, "");
        }

        public Snapshot(String api, String source, int recommendation, int theme, int locale, String live) {
            this.api = normalize(api);
            this.source = normalize(source);
            this.recommendation = recommendation;
            this.theme = theme;
            this.locale = locale;
            this.live = normalize(live);
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private Snapshot applied;
    private final boolean allowDefaultLive;
    private boolean active = true;
    private boolean resumed;
    private boolean homeStarted;
    private boolean defaultLiveHandled;

    public HomeSettings(Snapshot initial, boolean allowDefaultLive) {
        applied = initial;
        this.allowDefaultLive = allowDefaultLive;
    }

    /** Advance the baseline before requesting recreation, so the change cannot be replayed. */
    public Reload consume(Snapshot current) {
        Snapshot previous = applied;
        applied = current;
        if (!previous.api.equals(current.api)) return Reload.NETWORK;
        if (!previous.source.equals(current.source)
                || previous.recommendation != current.recommendation
                || previous.theme != current.theme
                || previous.locale != current.locale) return Reload.CACHE;
        if (!previous.live.equals(current.live)) return Reload.LIVE;
        return Reload.NONE;
    }

    /** Config loading may choose a fallback source; that is not a user settings change. */
    public void acceptLoadedHomeSource(String source) {
        acceptLoadedConfig(source, applied.live);
    }

    public void acceptLoadedConfig(String source, String live) {
        applied = new Snapshot(applied.api, source, applied.recommendation, applied.theme, applied.locale, live);
    }

    public void setResumed(boolean resumed) {
        this.resumed = active && resumed;
    }

    public boolean isResumed() {
        return resumed;
    }

    public boolean isActive() {
        return active;
    }

    /** Config/jar may finish while paused; only request the content when Home can show it. */
    public boolean startHomeIfReady(boolean ready) {
        if (!active || !resumed || !ready || homeStarted) return false;
        homeStarted = true;
        return true;
    }

    public boolean launchDefaultLive(boolean enabled) {
        if (!active || !resumed || !homeStarted || defaultLiveHandled) return false;
        defaultLiveHandled = true;
        return allowDefaultLive && enabled;
    }

    public void close() {
        active = false;
        resumed = false;
    }
}
