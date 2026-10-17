package com.github.tvbox.osc.event;

/**
 * 全局刷新事件(y-live 只保留直播相关)
 */
public class RefreshEvent {
    /** 直播界面刷新(切台/换源等) */
    public static final int TYPE_LIVEPLAY_UPDATE = 11;
    /** 手机网页推送的直播源变化 */
    public static final int TYPE_LIVE_URL_CHANGE = 13;
    /** 手机网页推送的 EPG 源变化 */
    public static final int TYPE_EPG_URL_CHANGE = 14;

    /**
     * 事件类型
     */
    public int type;

    /**
     * 附带的数据
     */
    public Object obj;

    public RefreshEvent(int type) {
        this.type = type;
    }

    public RefreshEvent(int type, Object obj) {
        this.type = type;
        this.obj = obj;
    }
}
