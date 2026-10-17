package com.github.tvbox.osc.bean;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.player.EXOmPlayer;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

import xyz.doikki.videoplayer.player.VideoView;

public class LivePlayerManager {
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;

    public void init(VideoView videoView) {
        try {
            int playerType = Hawk.get(HawkConfig.LIVE_PLAYER_TYPE, -1);
            if (playerType == -1) playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
            // 直播只支持 3 种播放器: 0=系统, 1=IJK, 2=Exo; 其它值(点播的阿里/MX 等)统一按 Exo 处理,
            // 否则「播放解码」会显示成"系统"、而且切台后会换成不支持直播的播放器(无声)
            if (playerType < 0 || playerType > 2) {
                playerType = 2;
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, playerType);
            }
            defaultPlayerConfig.put("pl", playerType);
            defaultPlayerConfig.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "软解码"));
            // Exo 解码: 0=硬解, 1=软解; 没设置过时默认硬解(保持原来的硬解优先行为, 纯直播版在 App 里默认软解)
            defaultPlayerConfig.put("exo", EXOmPlayer.decodeNameOf(
                    Hawk.get(HawkConfig.LIVE_EXO_DECODE, EXOmPlayer.DECODER_HARD)));
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            android.util.Log.i("YLive", "LivePlayerManager 默认配置 " + defaultPlayerConfig);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(VideoView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getLiveChannelPlayer(VideoView videoView, String channelName) {
        JSONObject playerConfig = Hawk.get(channelName, null);
        if (playerConfig == null) {
            if (!currentPlayerConfig.toString().equals(defaultPlayerConfig.toString()))
                getDefaultLiveChannelPlayer(videoView);
            return;
        }
        if (playerConfig.toString().equals(currentPlayerConfig.toString()))
            return;

        try {
            if (playerConfig.getInt("pl") == currentPlayerConfig.getInt("pl")
                    && playerConfig.getInt("pr") == currentPlayerConfig.getInt("pr")
                    && playerConfig.getString("ijk").equals(currentPlayerConfig.getString("ijk"))
                    && Objects.equals(playerConfig.optString("exo"), currentPlayerConfig.optString("exo"))) {
                videoView.setScreenScaleType(playerConfig.getInt("sc"));
            } else {
                PlayerHelper.updateCfg(videoView, playerConfig);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    /**
     * 当前直播解码选项在「播放解码」菜单里的位置:
     * 0=系统, 1=ijk硬解, 2=ijk软解, 3=exo软解, 4=exo硬解
     */
    public int getLivePlayerType() {
        int playerTypeIndex = 0;
        try {
            int playerType = currentPlayerConfig.getInt("pl");
            String ijkCodec = currentPlayerConfig.getString("ijk");
            String exoDecode = currentPlayerConfig.has("exo")
                    ? currentPlayerConfig.getString("exo")
                    : EXOmPlayer.decodeNameOf(Hawk.get(HawkConfig.LIVE_EXO_DECODE, EXOmPlayer.DECODER_HARD));
            switch (playerType) {
                case 0:
                    playerTypeIndex = 0;
                    break;
                case 1:
                    if (ijkCodec.equals("硬解码"))
                        playerTypeIndex = 1;
                    else
                        playerTypeIndex = 2;
                    break;
                case 2:
                    playerTypeIndex = EXOmPlayer.decodeModeOf(exoDecode) == EXOmPlayer.DECODER_SOFT ? 3 : 4;
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return playerTypeIndex;
    }

    public int getLivePlayerScale() {
        try {
            return currentPlayerConfig.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName) {
        JSONObject playerConfig = currentPlayerConfig;
        try {
            switch (playerType) {
                case 0:
                    playerConfig.put("pl", 0);
                    playerConfig.put("ijk", "软解码");
                    break;
                case 1:
                    playerConfig.put("pl", 1);
                    playerConfig.put("ijk", "硬解码");
                    break;
                case 2:
                    playerConfig.put("pl", 1);
                    playerConfig.put("ijk", "软解码");
                    break;
                case 3:
                    playerConfig.put("pl", 2);
                    playerConfig.put("exo", EXOmPlayer.DECODE_SOFT_NAME);
                    break;
                case 4:
                    playerConfig.put("pl", 2);
                    playerConfig.put("exo", EXOmPlayer.DECODE_HARD_NAME);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    public void changeLivePlayerScale(@NonNull VideoView videoView, int playerScale, String channelName){
        videoView.setScreenScaleType(playerScale);

        JSONObject playerConfig = currentPlayerConfig;
        try {
            playerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }
}
