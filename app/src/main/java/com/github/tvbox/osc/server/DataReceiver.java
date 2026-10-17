package com.github.tvbox.osc.server;

/**
 * 手机网页(/action)推送过来的数据回调(y-live 只保留直播源与 EPG 源).
 */
public interface DataReceiver {

    void onLiveReceived(String url);

    void onEpgReceived(String url);
}
