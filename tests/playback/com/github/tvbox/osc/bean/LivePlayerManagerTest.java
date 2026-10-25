package com.github.tvbox.osc.bean;

import com.github.tvbox.osc.player.mpv.MpvSupport;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import org.json.JSONObject;
import xyz.doikki.videoplayer.player.VideoView;

public final class LivePlayerManagerTest {
    public static void main(String[] args) {
        temporaryFallbackDoesNotChangeSavedDecoderWhenScaleChanges();
        explicitDecoderChoiceReplacesTemporaryFallback();
        defaultChannelScaleDoesNotSaveFallback();
        decoderChoiceSurvivesChannelSwitch();
        temporaryFallbackDoesNotLeakToNextChannel();
        legacyChannelDecoderCarriesOverToUnconfiguredChannel();
        perChannelChoiceIsNotOverwrittenByGlobalChange();
        mpvDecoderChoiceIsRemembered();
        mpvChoiceFallsBackToExoWhenLibraryIsMissing();
        System.out.println("LivePlayerManagerTest passed");
    }

    // Persisting the effective config would silently turn the temporary hard fallback permanent.
    private static void temporaryFallbackDoesNotChangeSavedDecoderWhenScaleChanges() {
        Hawk.clear();
        LivePlayerManager manager = new LivePlayerManager();
        VideoView view = new VideoView();
        manager.init(view);
        manager.changeLivePlayerType(view, 3, "dance");
        manager.changeLivePlayerTypeTemp(view, 4);
        manager.changeLivePlayerScale(view, 2, "dance");
        check(manager.getLivePlayerType() == 4, "active fallback must remain until leaving channel");
        JSONObject saved = Hawk.get("dance", null);
        check("软解".equals(saved.optString("exo")), "scale change must preserve saved soft decoder");
        check(saved.optInt("sc") == 2, "scale change must be saved");
        manager.getDefaultLiveChannelPlayer(view);
        manager.getLiveChannelPlayer(view, "dance");
        check(manager.getLivePlayerType() == 3, "returning to channel must restore user's decoder");
        check(manager.getLivePlayerScale() == 2, "returning to channel must keep new scale");
    }

    private static void explicitDecoderChoiceReplacesTemporaryFallback() {
        Hawk.clear();
        LivePlayerManager manager = new LivePlayerManager();
        VideoView view = new VideoView();
        manager.init(view);
        manager.changeLivePlayerType(view, 3, "dance");
        manager.changeLivePlayerTypeTemp(view, 4);
        manager.changeLivePlayerType(view, 1, "dance");
        manager.changeLivePlayerScale(view, 1, "dance");
        manager.getDefaultLiveChannelPlayer(view);
        manager.getLiveChannelPlayer(view, "dance");
        check(manager.getLivePlayerType() == 1, "explicit choice must replace old saved decoder");
    }

    private static void defaultChannelScaleDoesNotSaveFallback() {
        Hawk.clear();
        Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 2);
        Hawk.put(HawkConfig.LIVE_EXO_DECODE, 1);
        LivePlayerManager manager = new LivePlayerManager();
        VideoView view = new VideoView();
        manager.init(view);
        manager.changeLivePlayerTypeTemp(view, 4);
        manager.changeLivePlayerScale(view, 1, "default");
        manager.getDefaultLiveChannelPlayer(view);
        manager.getLiveChannelPlayer(view, "default");
        check(manager.getLivePlayerType() == 3, "default soft decoder must survive fallback plus scale change");
        check(manager.getLivePlayerScale() == 1, "default channel must save its new scale");
    }

    // The reported bug: picking exo hard and zapping to a channel without its own memory
    // silently restarted playback with the global exo soft default.
    private static void decoderChoiceSurvivesChannelSwitch() {
        Hawk.clear();
        Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 2);
        Hawk.put(HawkConfig.LIVE_EXO_DECODE, 1);
        LivePlayerManager manager = new LivePlayerManager();
        VideoView view = new VideoView();
        manager.init(view);
        manager.changeLivePlayerType(view, 4, "hard");
        manager.getLiveChannelPlayer(view, "next");
        check(manager.getLivePlayerType() == 4, "channel switch must keep the selected exo hard decoder");

        LivePlayerManager restarted = new LivePlayerManager();
        restarted.init(view);
        restarted.getLiveChannelPlayer(view, "next");
        check(restarted.getLivePlayerType() == 4, "explicit decoder choice must survive an app restart");
    }

    // The automatic soft -> hard fallback is temporary; it must not become the next channel's decoder.
    private static void temporaryFallbackDoesNotLeakToNextChannel() {
        Hawk.clear();
        Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 2);
        Hawk.put(HawkConfig.LIVE_EXO_DECODE, 1);
        LivePlayerManager manager = new LivePlayerManager();
        VideoView view = new VideoView();
        manager.init(view);
        manager.changeLivePlayerType(view, 3, "soft");
        manager.changeLivePlayerTypeTemp(view, 4);
        manager.getLiveChannelPlayer(view, "next");
        check(manager.getLivePlayerType() == 3, "temporary hard fallback must not leak to the next channel");
    }

    // Channels remembered by an older build keep their own decoder; a new channel inherits it
    // instead of jumping back to the global default.
    private static void legacyChannelDecoderCarriesOverToUnconfiguredChannel() {
        Hawk.clear();
        Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 2);
        Hawk.put(HawkConfig.LIVE_EXO_DECODE, 1);
        LivePlayerManager manager = new LivePlayerManager();
        VideoView view = new VideoView();
        manager.init(view);
        JSONObject legacy = new JSONObject();
        try {
            legacy.put("pl", 2);
            legacy.put("ijk", "软解码");
            legacy.put("exo", "硬解");
            legacy.put("pr", 0);
            legacy.put("sc", 0);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Hawk.put("legacy", legacy);
        manager.getLiveChannelPlayer(view, "legacy");
        check(manager.getLivePlayerType() == 4, "legacy channel must still use its saved hard decoder");
        manager.getLiveChannelPlayer(view, "next");
        check(manager.getLivePlayerType() == 4, "unconfigured channel must inherit the last used decoder");

        LivePlayerManager restarted = new LivePlayerManager();
        restarted.init(view);
        restarted.getLiveChannelPlayer(view, "next");
        check(restarted.getLivePlayerType() == 4, "inherited decoder must survive an app restart");
    }

    // A channel where the user explicitly picked a decoder keeps it even after the global default changes.
    private static void perChannelChoiceIsNotOverwrittenByGlobalChange() {
        Hawk.clear();
        Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 2);
        Hawk.put(HawkConfig.LIVE_EXO_DECODE, 1);
        LivePlayerManager manager = new LivePlayerManager();
        VideoView view = new VideoView();
        manager.init(view);
        manager.changeLivePlayerType(view, 4, "hard");
        manager.getLiveChannelPlayer(view, "soft");
        manager.changeLivePlayerType(view, 3, "soft");
        manager.getLiveChannelPlayer(view, "hard");
        check(manager.getLivePlayerType() == 4, "channel memory must keep its explicit hard decoder");
        manager.getLiveChannelPlayer(view, "soft");
        check(manager.getLivePlayerType() == 3, "channel memory must keep its explicit soft decoder");
    }

    // MPV has two menu entries: 5=hard, 6=soft. It must behave like Exo for
    // global defaults, per-channel memory and temporary fallback.
    private static void mpvDecoderChoiceIsRemembered() {
        MpvSupport.AVAILABLE = true;
        Hawk.clear();
        LivePlayerManager manager = new LivePlayerManager();
        VideoView view = new VideoView();
        manager.init(view);
        manager.changeLivePlayerType(view, 6, "mpv-soft");
        check(manager.getLivePlayerType() == 6, "MPV soft decoder must round-trip");
        check(Hawk.get(HawkConfig.LIVE_PLAYER_TYPE, -1) == 20, "MPV must be saved as player type 20");
        check(Hawk.get(HawkConfig.LIVE_MPV_DECODE, -1) == MpvSupport.DECODER_SOFT,
                "MPV soft mode must be saved");
        manager.getLiveChannelPlayer(view, "next");
        check(manager.getLivePlayerType() == 6, "MPV decoder must survive channel switch");
        manager.changeLivePlayerType(view, 5, "mpv-hard");
        check(manager.getLivePlayerType() == 5, "MPV hard decoder must round-trip");
        JSONObject saved = Hawk.get("mpv-hard", null);
        check(saved != null && saved.optInt("pl") == 20, "MPV channel config must use pl=20");
        check("硬解".equals(saved.optString("mpv")), "MPV channel config must save hard mode");
    }

    // A standard APK (no libmpv) upgraded over an MPV install must not try to
    // instantiate a missing player.
    private static void mpvChoiceFallsBackToExoWhenLibraryIsMissing() {
        MpvSupport.AVAILABLE = false;
        try {
            Hawk.clear();
            Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 20);
            Hawk.put(HawkConfig.LIVE_MPV_DECODE, MpvSupport.DECODER_SOFT);
            LivePlayerManager manager = new LivePlayerManager();
            VideoView view = new VideoView();
            manager.init(view);
            check(manager.getLivePlayerType() == 4, "missing MPV library must fall back to exo hard");
            check(Hawk.get(HawkConfig.LIVE_PLAYER_TYPE, -1) == 2,
                    "missing MPV library must be rewritten to exo");
        } finally {
            MpvSupport.AVAILABLE = true;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
