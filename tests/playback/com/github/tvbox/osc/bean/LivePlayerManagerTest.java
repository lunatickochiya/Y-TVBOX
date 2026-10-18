package com.github.tvbox.osc.bean;

import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import org.json.JSONObject;
import xyz.doikki.videoplayer.player.VideoView;

public final class LivePlayerManagerTest {
    public static void main(String[] args) {
        temporaryFallbackDoesNotChangeSavedDecoderWhenScaleChanges();
        explicitDecoderChoiceReplacesTemporaryFallback();
        defaultChannelScaleDoesNotSaveFallback();
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
