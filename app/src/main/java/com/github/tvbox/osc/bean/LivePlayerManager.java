package com.github.tvbox.osc.bean;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.player.EXOmPlayer;
import com.github.tvbox.osc.player.mpv.MpvSupport;
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
    // User configuration stays separate from a temporary decoder fallback.
    private JSONObject savedPlayerConfig;

    public void init(VideoView videoView) {
        try {
            int playerType = Hawk.get(HawkConfig.LIVE_PLAYER_TYPE, -1);
            if (playerType == -1) playerType = Hawk.get(HawkConfig.PLAY_TYPE, 0);
            // 直播支持 4 种播放器: 0=系统, 1=IJK, 2=Exo, 3=MPV; 其它值(点播的阿里/MX 等)统一按 Exo 处理,
            // 否则「播放解码」会显示成"系统"、而且切台后会换成不支持直播的播放器(无声)
            if (playerType < 0 || playerType > 3) {
                playerType = 2;
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, playerType);
            }
            if (playerType == 3 && !MpvSupport.isAvailable()) {
                // 标准版没有 libmpv: 保存过 MPV 配置也回退 Exo
                playerType = 2;
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, playerType);
            }
            defaultPlayerConfig.put("pl", playerType);
            defaultPlayerConfig.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "软解码"));
            // Exo 解码: 0=硬解, 1=软解; 没设置过时默认硬解(保持原来的硬解优先行为, 纯直播版在 App 里默认软解)
            defaultPlayerConfig.put("exo", EXOmPlayer.decodeNameOf(
                    Hawk.get(HawkConfig.LIVE_EXO_DECODE, EXOmPlayer.DECODER_HARD)));
            // MPV 解码: 0=硬解, 1=软解
            defaultPlayerConfig.put("mpv", MpvSupport.decodeNameOf(
                    Hawk.get(HawkConfig.LIVE_MPV_DECODE, MpvSupport.DECODER_HARD)));
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            defaultPlayerConfig.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            android.util.Log.w("YLive", "LivePlayerManager 默认配置 " + defaultPlayerConfig);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(VideoView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        currentPlayerConfig = copyConfig(defaultPlayerConfig);
        savedPlayerConfig = copyConfig(defaultPlayerConfig);
    }

    public void getLiveChannelPlayer(VideoView videoView, String channelName) {
        JSONObject playerConfig = Hawk.get(channelName, null);
        if (playerConfig == null) {
            // 频道没有单独记忆: 沿用上一个频道的解码方式, 不要跳回全局默认的 exo软解.
            // 用 savedPlayerConfig 而不是 currentPlayerConfig: 软解失败时的临时硬解回退
            // 只对当前频道有效, 不能带到下一个频道.
            JSONObject inherited = copyConfig(defaultPlayerConfig);
            copyDecoder(savedPlayerConfig, inherited);
            int inheritedType = livePlayerTypeOf(inherited);
            if (inheritedType != livePlayerTypeOf(defaultPlayerConfig)) {
                // 把沿用的解码方式记成默认, 下次启动/再切台仍然是同一个解码.
                rememberPlayerTypeAsDefault(inheritedType);
            }
            savedPlayerConfig = copyConfig(inherited);
            if (!currentPlayerConfig.toString().equals(inherited.toString())) {
                PlayerHelper.updateCfg(videoView, inherited);
            }
            currentPlayerConfig = inherited;
            return;
        }
        savedPlayerConfig = copyConfig(playerConfig);
        if (playerConfig.toString().equals(currentPlayerConfig.toString()))
            return;

        try {
            if (playerConfig.getInt("pl") == currentPlayerConfig.getInt("pl")
                    && playerConfig.getInt("pr") == currentPlayerConfig.getInt("pr")
                    && playerConfig.getString("ijk").equals(currentPlayerConfig.getString("ijk"))
                    && Objects.equals(playerConfig.optString("exo"), currentPlayerConfig.optString("exo"))
                    && Objects.equals(playerConfig.optString("mpv"), currentPlayerConfig.optString("mpv"))) {
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
     * 0=系统, 1=ijk硬解, 2=ijk软解, 3=exo软解, 4=exo硬解, 5=mpv硬解, 6=mpv软解
     */
    public int getLivePlayerType() {
        int playerTypeIndex = 0;
        try {
            int playerType = currentPlayerConfig.getInt("pl");
            String ijkCodec = currentPlayerConfig.getString("ijk");
            String exoDecode = currentPlayerConfig.has("exo")
                    ? currentPlayerConfig.getString("exo")
                    : EXOmPlayer.decodeNameOf(Hawk.get(HawkConfig.LIVE_EXO_DECODE, EXOmPlayer.DECODER_HARD));
            String mpvDecode = currentPlayerConfig.has("mpv")
                    ? currentPlayerConfig.getString("mpv")
                    : MpvSupport.decodeNameOf(Hawk.get(HawkConfig.LIVE_MPV_DECODE, MpvSupport.DECODER_HARD));
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
                case 3:
                    if (!MpvSupport.isAvailable()) {
                        playerTypeIndex = 4; // 标准版回退 Exo 硬解
                    } else {
                        playerTypeIndex = MpvSupport.decodeModeOf(mpvDecode) == MpvSupport.DECODER_SOFT ? 6 : 5;
                    }
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
        JSONObject playerConfig = copyConfig(currentPlayerConfig);
        applyPlayerType(playerConfig, playerType);
        // 手动选择的解码方式同时作为全局默认: 切到没有单独记忆的频道时继续使用,
        // 否则会跳回 App 初始的 exo软解.
        rememberPlayerTypeAsDefault(playerType);
        PlayerHelper.updateCfg(videoView, playerConfig);
        savedPlayerConfig = copyConfig(playerConfig);
        // 显式选择始终记到该频道: 即使当前等于全局默认也要写入,
        // 这样以后在别的频道改解码, 不会把这里的频道记忆一起改掉.
        Hawk.put(channelName, savedPlayerConfig);
        currentPlayerConfig = playerConfig;
    }

    /**
     * 临时切换解码(不写入该频道的记忆配置), 用于"软解失败自动回退硬解":
     * 菜单显示会跟着变, 切走再切回来会恢复用户自己的设置.
     */
    public void changeLivePlayerTypeTemp(VideoView videoView, int playerType) {
        JSONObject playerConfig = new JSONObject();
        try {
            playerConfig.put("pl", currentPlayerConfig.getInt("pl"));
            playerConfig.put("ijk", currentPlayerConfig.getString("ijk"));
            playerConfig.put("exo", currentPlayerConfig.optString("exo", EXOmPlayer.DECODE_HARD_NAME));
            playerConfig.put("mpv", currentPlayerConfig.optString("mpv", MpvSupport.DECODE_HARD_NAME));
            playerConfig.put("pr", currentPlayerConfig.getInt("pr"));
            playerConfig.put("sc", currentPlayerConfig.getInt("sc"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        applyPlayerType(playerConfig, playerType);
        PlayerHelper.updateCfg(videoView, playerConfig);
        currentPlayerConfig = playerConfig;
    }

    /** 把「播放解码」菜单下标写进播放配置(0系统/1ijk硬解/2ijk软解/3exo软解/4exo硬解/5mpv硬解/6mpv软解) */
    private void applyPlayerType(JSONObject playerConfig, int playerType) {
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
                case 5:
                    playerConfig.put("pl", 3);
                    playerConfig.put("mpv", MpvSupport.DECODE_HARD_NAME);
                    break;
                case 6:
                    playerConfig.put("pl", 3);
                    playerConfig.put("mpv", MpvSupport.DECODE_SOFT_NAME);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /** 播放配置对应的「播放解码」菜单下标(0系统/1ijk硬解/2ijk软解/3exo软解/4exo硬解/5mpv硬解/6mpv软解) */
    private int livePlayerTypeOf(JSONObject playerConfig) {
        if (playerConfig == null) return 0;
        int playerType = playerConfig.optInt("pl", 0);
        if (playerType == 1) {
            return "硬解码".equals(playerConfig.optString("ijk")) ? 1 : 2;
        }
        if (playerType == 2) {
            String exoDecode = playerConfig.has("exo")
                    ? playerConfig.optString("exo")
                    : EXOmPlayer.decodeNameOf(Hawk.get(HawkConfig.LIVE_EXO_DECODE, EXOmPlayer.DECODER_HARD));
            return EXOmPlayer.decodeModeOf(exoDecode) == EXOmPlayer.DECODER_SOFT ? 3 : 4;
        }
        if (playerType == 3) {
            if (!MpvSupport.isAvailable()) return 4;
            String mpvDecode = playerConfig.has("mpv")
                    ? playerConfig.optString("mpv")
                    : MpvSupport.decodeNameOf(Hawk.get(HawkConfig.LIVE_MPV_DECODE, MpvSupport.DECODER_HARD));
            return MpvSupport.decodeModeOf(mpvDecode) == MpvSupport.DECODER_SOFT ? 6 : 5;
        }
        return 0;
    }

    /**
     * 用户手动选择的解码方式同时记成全局默认, 切到没有单独记忆的频道时沿用.
     * 只改解码相关字段, 不动 defaultPlayerConfig 里的画面比例.
     */
    private void rememberPlayerTypeAsDefault(int playerType) {
        switch (playerType) {
            case 0:
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 0);
                break;
            case 1:
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 1);
                Hawk.put(HawkConfig.IJK_CODEC, "硬解码");
                break;
            case 2:
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 1);
                Hawk.put(HawkConfig.IJK_CODEC, "软解码");
                break;
            case 3:
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 2);
                Hawk.put(HawkConfig.LIVE_EXO_DECODE, EXOmPlayer.DECODER_SOFT);
                break;
            case 4:
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 2);
                Hawk.put(HawkConfig.LIVE_EXO_DECODE, EXOmPlayer.DECODER_HARD);
                break;
            case 5:
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 3);
                Hawk.put(HawkConfig.LIVE_MPV_DECODE, MpvSupport.DECODER_HARD);
                break;
            case 6:
                Hawk.put(HawkConfig.LIVE_PLAYER_TYPE, 3);
                Hawk.put(HawkConfig.LIVE_MPV_DECODE, MpvSupport.DECODER_SOFT);
                break;
        }
        applyPlayerType(defaultPlayerConfig, playerType);
        android.util.Log.w("YLive", "默认解码更新: " + defaultPlayerConfig);
    }

    private void copyDecoder(JSONObject source, JSONObject target) {
        if (source == null) return;
        try {
            if (source.has("pl")) target.put("pl", source.getInt("pl"));
            if (source.has("ijk")) target.put("ijk", source.getString("ijk"));
            if (source.has("exo")) target.put("exo", source.getString("exo"));
            if (source.has("mpv")) target.put("mpv", source.getString("mpv"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void changeLivePlayerScale(@NonNull VideoView videoView, int playerScale, String channelName){
        videoView.setScreenScaleType(playerScale);
        JSONObject playerConfig = copyConfig(savedPlayerConfig);
        try {
            playerConfig.put("sc", playerScale);
            currentPlayerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        savedPlayerConfig = playerConfig;
        saveChannelConfig(channelName, playerConfig);
    }

    private void saveChannelConfig(String channelName, JSONObject playerConfig) {
        if (playerConfig.toString().equals(defaultPlayerConfig.toString())) Hawk.delete(channelName);
        else Hawk.put(channelName, playerConfig);
    }

    private JSONObject copyConfig(JSONObject playerConfig) {
        try {
            return new JSONObject(playerConfig.toString());
        } catch (JSONException e) {
            throw new IllegalStateException("Invalid live player configuration", e);
        }
    }
}
