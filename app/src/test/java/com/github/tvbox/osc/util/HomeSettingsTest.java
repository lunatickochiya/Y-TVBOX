package com.github.tvbox.osc.util;

/**
 * Standalone regression checks; no Android runtime or Gradle required.
 *
 * javac -d /tmp/home-settings-test app/src/main/java/com/github/tvbox/osc/util/HomeSettings.java \
 *     app/src/test/java/com/github/tvbox/osc/util/HomeSettingsTest.java
 * java -cp /tmp/home-settings-test com.github.tvbox.osc.util.HomeSettingsTest
 */
public final class HomeSettingsTest {
    private static int checks;

    public static void main(String[] args) {
        unchangedSettingsDoNotReload();
        changedSettingsAreConsumedOnce();
        apiChangeRequiresNetwork();
        homeChangesUseCache();
        appearanceChangesUseCache();
        restoredSettingsDoNotReload();
        stringsAreNormalized();
        loadedSourceDoesNotLookLikeAUserChange();
        loadedSourceDoesNotHideOtherChanges();
        pausedStartupWaitsForResume();
        defaultLiveLaunchesOnce();
        settingsReloadNeverLaunchesDefaultLive();
        closedHomeRejectsLateWork();
        liveChangesDoNotReloadVod();
        System.out.println("HomeSettingsTest: " + checks + " checks passed");
    }

    private static HomeSettings.Snapshot snapshot(String api, String source, int rec, int theme, int locale) {
        return new HomeSettings.Snapshot(api, source, rec, theme, locale);
    }

    private static HomeSettings fresh() {
        return new HomeSettings(snapshot("https://example.test/config", "first", 0, 0, 0), true);
    }

    private static void unchangedSettingsDoNotReload() {
        HomeSettings settings = fresh();
        // Player, DNS and live-only values are deliberately outside the Home snapshot.
        equal(HomeSettings.Reload.NONE, settings.consume(snapshot("https://example.test/config", "first", 0, 0, 0)));
    }

    private static void changedSettingsAreConsumedOnce() {
        HomeSettings settings = fresh();
        HomeSettings.Snapshot changed = snapshot("https://example.test/config", "second", 1, 0, 0);
        equal(HomeSettings.Reload.CACHE, settings.consume(changed));
        equal(HomeSettings.Reload.NONE, settings.consume(changed));
        settings.setResumed(false);
        settings.setResumed(true);
        equal(HomeSettings.Reload.NONE, settings.consume(changed));
    }

    private static void apiChangeRequiresNetwork() {
        HomeSettings settings = fresh();
        HomeSettings.Snapshot changed = snapshot("https://example.test/new", "second", 1, 2, 1);
        equal(HomeSettings.Reload.NETWORK, settings.consume(changed));
        equal(HomeSettings.Reload.NONE, settings.consume(changed));
    }

    private static void homeChangesUseCache() {
        equal(HomeSettings.Reload.CACHE, fresh().consume(snapshot("https://example.test/config", "second", 0, 0, 0)));
        equal(HomeSettings.Reload.CACHE, fresh().consume(snapshot("https://example.test/config", "first", 2, 0, 0)));
    }

    private static void appearanceChangesUseCache() {
        equal(HomeSettings.Reload.CACHE, fresh().consume(snapshot("https://example.test/config", "first", 0, 3, 0)));
        equal(HomeSettings.Reload.CACHE, fresh().consume(snapshot("https://example.test/config", "first", 0, 0, 1)));
    }

    private static void restoredSettingsDoNotReload() {
        // A user can select another value and restore it before leaving Settings.
        equal(HomeSettings.Reload.NONE, fresh().consume(snapshot("https://example.test/config", "first", 0, 0, 0)));
    }

    private static void stringsAreNormalized() {
        HomeSettings settings = new HomeSettings(snapshot(null, null, 0, 0, 0), true);
        equal(HomeSettings.Reload.NONE, settings.consume(snapshot("", "", 0, 0, 0)));
        equal(HomeSettings.Reload.NONE, fresh().consume(snapshot(" https://example.test/config ", " first ", 0, 0, 0)));
    }

    private static void loadedSourceDoesNotLookLikeAUserChange() {
        HomeSettings settings = new HomeSettings(snapshot("https://example.test/config", "", 0, 0, 0), true);
        settings.acceptLoadedHomeSource("first");
        equal(HomeSettings.Reload.NONE, settings.consume(snapshot("https://example.test/config", "first", 0, 0, 0)));
        equal(HomeSettings.Reload.CACHE, settings.consume(snapshot("https://example.test/config", "second", 0, 0, 0)));
    }

    private static void loadedSourceDoesNotHideOtherChanges() {
        HomeSettings settings = fresh();
        settings.acceptLoadedHomeSource("fallback");
        equal(HomeSettings.Reload.NETWORK, settings.consume(snapshot("https://example.test/new", "fallback", 0, 0, 0)));
        settings = fresh();
        settings.acceptLoadedHomeSource("fallback");
        equal(HomeSettings.Reload.CACHE, settings.consume(snapshot("https://example.test/config", "fallback", 2, 0, 0)));
    }

    private static void pausedStartupWaitsForResume() {
        HomeSettings settings = fresh();
        equal(false, settings.startHomeIfReady(true));
        equal(true, settings.isActive()); // Pausing must not drop config/jar completion.
        settings.setResumed(true);
        equal(false, settings.startHomeIfReady(false));
        equal(true, settings.startHomeIfReady(true));
        equal(false, settings.startHomeIfReady(true));
        settings.setResumed(false);
        settings.setResumed(true);
        equal(false, settings.startHomeIfReady(true));
    }

    private static void defaultLiveLaunchesOnce() {
        HomeSettings settings = fresh();
        equal(false, settings.launchDefaultLive(true));
        settings.setResumed(true);
        settings.startHomeIfReady(true);
        equal(true, settings.launchDefaultLive(true));
        equal(false, settings.launchDefaultLive(true));
        settings.setResumed(false);
        settings.setResumed(true);
        equal(false, settings.launchDefaultLive(true));

        settings = fresh();
        settings.setResumed(true);
        settings.startHomeIfReady(true);
        equal(false, settings.launchDefaultLive(false));
        equal(false, settings.launchDefaultLive(true)); // Enabling it later is not another startup.
    }

    private static void settingsReloadNeverLaunchesDefaultLive() {
        HomeSettings settings = new HomeSettings(snapshot("", "", 0, 0, 0), false);
        settings.setResumed(true);
        equal(true, settings.startHomeIfReady(true));
        equal(false, settings.launchDefaultLive(true));
    }

    private static void closedHomeRejectsLateWork() {
        HomeSettings settings = fresh();
        settings.setResumed(true);
        settings.close();
        equal(false, settings.isActive());
        equal(false, settings.startHomeIfReady(true));
        equal(false, settings.launchDefaultLive(true));
        settings.setResumed(true);
        equal(false, settings.isResumed());
    }

    private static void liveChangesDoNotReloadVod() {
        HomeSettings settings = fresh();
        HomeSettings.Snapshot custom = new HomeSettings.Snapshot("https://example.test/config", "first", 0, 0, 0,
                "https://example.test/live.m3u");
        equal(HomeSettings.Reload.LIVE, settings.consume(custom));
        equal(HomeSettings.Reload.NONE, settings.consume(custom));
        equal(HomeSettings.Reload.LIVE, settings.consume(snapshot("https://example.test/config", "first", 0, 0, 0)));
    }

    private static void equal(Object expected, Object actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("check " + checks + ": expected " + expected + ", got " + actual);
        }
    }
}
