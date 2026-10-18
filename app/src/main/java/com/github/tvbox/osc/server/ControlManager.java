package com.github.tvbox.osc.server;

import android.content.Context;

import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.api.ApiConfig;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

/**
 * 本地 HTTP 服务管理(y-live 只用于手机扫码配置直播源/EPG 源).
 */
public class ControlManager {
    private static volatile ControlManager instance;
    private RemoteServer mServer = null;
    public static Context mContext;

    private ControlManager() {

    }

    public static ControlManager get() {
        if (instance == null) {
            synchronized (ControlManager.class) {
                if (instance == null) {
                    instance = new ControlManager();
                }
            }
        }
        return instance;
    }

    public static void init(Context context) {
        mContext = context;
    }

    public String getAddress(boolean local) {
        if (mServer == null || !mServer.isStarting()) return "";
        return local ? mServer.getLoadAddress() : mServer.getServerAddress();
    }

    public void startServer() {
        if (mServer != null && mServer.isStarting()) {
            return;
        }
        do {
            mServer = new RemoteServer(RemoteServer.serverPort, mContext);
            mServer.setDataReceiver(new DataReceiver() {
                @Override
                public void onLiveReceived(String url) {
                    if (!Hawk.put(HawkConfig.LIVE_URL, url)) throw new IllegalStateException("Cannot save live URL");
                    ApiConfig.putLiveHistory(url);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_LIVE_URL_CHANGE, url));
                }

                @Override
                public void onEpgReceived(String url) {
                    if (!Hawk.put(HawkConfig.EPG_URL, url)) throw new IllegalStateException("Cannot save EPG URL");
                    if (!url.isEmpty()) ApiConfig.putEPGHistory(url);
                    EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_EPG_URL_CHANGE, url));
                }
            });
            try {
                mServer.start();
                IjkMediaPlayer.setDotPort(Hawk.get(HawkConfig.DOH_URL, 0) > 0, RemoteServer.serverPort);
                break;
            } catch (IOException ex) {
                RemoteServer.serverPort++;
                mServer.stop();
            }
        } while (RemoteServer.serverPort < 9999);
        if (mServer != null && !mServer.isStarting()) mServer = null;
    }

    public void stopServer() {
        if (mServer != null && mServer.isStarting()) {
            mServer.stop();
        }
        mServer = null;
    }
}
