package com.github.tvbox.osc.util;
import org.json.JSONObject;
import xyz.doikki.videoplayer.player.VideoView;
public final class PlayerHelper {
    public static void updateCfg(VideoView view, JSONObject config) {
        view.setScreenScaleType(config.optInt("sc", 0));
    }
}
