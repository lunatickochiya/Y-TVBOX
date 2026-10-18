package com.github.tvbox.osc.util;

import com.orhanobut.hawk.Hawk;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class HawkConfig {
    // URL Configurations
    public static final String LIVE_URL = "live_url";
    public static final String LIVE_HISTORY = "live_history";
    public static final String EPG_URL = "epg_url";
    public static final String EPG_HISTORY = "epg_history";
    public static final String PROXY_SERVER = "proxy_server";
    // Settings
    public static final String DEBUG_OPEN = "debug_open";
    public static final String HOME_LOCALE = "language";                 // 0 中文 1 英文
    public static final String BOOT_START_LIVE = "boot_start_live";      //开机自启进直播的开关

    // Player Settings
    public static final String IJK_CODEC = "ijk_codec";
    public static final String PLAY_TYPE = "play_type";     //0 系统 1 ijk 2 exo
    public static final String PLAY_RENDER = "play_render"; //0 texture 2
    public static final String PLAY_SCALE = "play_scale";   //
    public static final String IJK_CACHE_PLAY = "ijk_cache_play";

    public static final String EXO_RENDERER = "exo_renderer";
    public static final String EXO_RENDERER_MODE = "exo_renderer_mode";


    // Other Settings
    public static final String DOH_URL = "doh_url";         // DNS
    public static final String THEME_SELECT = "theme_select";
    public static final String BACKGROUND_PLAY_TYPE = "background_play_type";

    // Live Settings
    public static final String LIVE_CHANNEL = "last_live_channel_name";
    public static final String LIVE_CHANNEL_GROUP = "last_live_channel_group_name";
    public static final String LIVE_CHANNEL_REVERSE = "live_channel_reverse";
    public static final String LIVE_CROSS_GROUP = "live_cross_group";
    public static final String LIVE_CONNECT_TIMEOUT = "live_connect_timeout";
    public static final String LIVE_SHOW_NET_SPEED = "live_show_net_speed";
    public static final String LIVE_SHOW_TIME = "live_show_time";
    public static final String LIVE_SKIP_PASSWORD = "live_skip_password";
    public static final String LIVE_PLAYER_TYPE = "live_player_type"; // 0 系统 1 ijk 2 exo
    public static final String LIVE_EXO_DECODE = "live_exo_decode"; // 直播 Exo 解码: 0=硬解(MediaCodec 优先), 1=软解(FFmpeg 优先)
    public static final String FCC_ENABLE = "live_fcc_enable"; // 原生 FCC 快速换台
    public static final String LIVE_BRIGHTNESS = "live_brightness"; // 直播界面亮度(0.01~1, 未设置为 -1)
    public static final String LIVE_HIDDEN = "live_hidden_channels"; // 隐藏的直播分组/频道(g|组名 / c|组名|频道名)

    public static boolean isDebug() {
        return Hawk.get(DEBUG_OPEN, false);
    }
}
