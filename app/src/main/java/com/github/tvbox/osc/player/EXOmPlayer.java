package com.github.tvbox.osc.player;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.MappingTrackSelector;
import com.github.tvbox.osc.util.StringUtils;
import xyz.doikki.videoplayer.exo.ExoMediaPlayer;

public class EXOmPlayer extends ExoMediaPlayer {

    /** 硬解: MediaCodec 优先, FFmpeg 软解兜底(硬解不支持的编码/音频自动转软解) */
    public static final int DECODER_HARD = 0;
    /** 软解: FFmpeg 软解优先, MediaCodec 兜底 */
    public static final int DECODER_SOFT = 1;

    /** 解码方式名称(直播设置里存的字符串) */
    public static final String DECODE_SOFT_NAME = "软解";
    public static final String DECODE_HARD_NAME = "硬解";

    private int decoderMode = -1;

    private String audioId = "";
    private String subtitleId = "";

    public EXOmPlayer(Context context) {
        super(context);
    }

    /**
     * 带解码方式的构造(直播播放器用): 同时应用直播缓冲区策略.
     *
     * @param decoderMode {@link #DECODER_HARD} / {@link #DECODER_SOFT}
     */
    public EXOmPlayer(Context context, int decoderMode) {
        super(context);
        setDecoderMode(decoderMode);
    }

    /** "软解" -> {@link #DECODER_SOFT}, 其余(含 null) -> {@link #DECODER_HARD} */
    public static int decodeModeOf(String name) {
        return DECODE_SOFT_NAME.equals(name) ? DECODER_SOFT : DECODER_HARD;
    }

    public static String decodeNameOf(int mode) {
        return mode == DECODER_SOFT ? DECODE_SOFT_NAME : DECODE_HARD_NAME;
    }

    /**
     * 设置解码方式: 两种方式都用 {@link LiveExoRenderersFactory}
     * (系统硬解 + FFmpeg 软解), 区别只在软解优先级;
     * 同时把直播缓冲区调小(有上限, 起播更快, 内存更省).
     * 需在 {@link #initPlayer()} 之前调用.
     */
    public void setDecoderMode(int decoderMode) {
        this.decoderMode = decoderMode;
        setRenderersFactory(new LiveExoRenderersFactory(mAppContext));
        setExtensionRendererMode(decoderMode == DECODER_SOFT
                ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        setLoadControl(createLiveLoadControl());
        android.util.Log.w("YLive", "EXOmPlayer 解码方式=" + decodeNameOf(decoderMode)
                + " 视频软解优先级=" + (decoderMode == DECODER_SOFT ? "优先" : "兜底") + " 音频=系统优先");
    }

    public int getDecoderMode() {
        return decoderMode;
    }

    /** 直播缓冲区: 2s~20s / 24MB 上限, 1s 即可起播, 不回看(省内存) */
    private static LoadControl createLiveLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(2000, 20000, 1000, 2000)
                .setTargetBufferBytes(24 * 1024 * 1024)
                .setBackBuffer(0, false)
                .build();
    }

    @SuppressLint("UnsafeOptInUsageError")
    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo != null) {
            getExoSelectedTrack();
            for (int groupArrayIndex = 0; groupArrayIndex < trackInfo.getRendererCount(); groupArrayIndex++) {
                TrackGroupArray groupArray = trackInfo.getTrackGroups(groupArrayIndex);
                for (int groupIndex = 0; groupIndex < groupArray.length; groupIndex++) {
                    TrackGroup group = groupArray.get(groupIndex);
                    for (int formatIndex = 0; formatIndex < group.length; formatIndex++) {
                        Format format = group.getFormat(formatIndex);
                        if (MimeTypes.isAudio(format.sampleMimeType)) {
                            String trackName = (data.getAudio().size() + 1) + "：" + trackNameProvider.getTrackName(format) + "[" + format.codecs + "]";
                            TrackInfoBean t = new TrackInfoBean();
                            t.name = trackName;
                            t.language = "";
                            t.trackId = formatIndex;
                            t.selected = !StringUtils.isEmpty(audioId) && audioId.equals(format.id);
                            t.trackGroupId = groupIndex;
                            t.renderId = groupArrayIndex;
                            data.addAudio(t);
                        } else if (MimeTypes.isText(format.sampleMimeType)) {
                            String trackName = (data.getSubtitle().size() + 1) + "：" + trackNameProvider.getTrackName(format);
                            TrackInfoBean t = new TrackInfoBean();
                            t.name = trackName;
                            t.language = "";
                            t.trackId = formatIndex;
                            t.selected = !StringUtils.isEmpty(subtitleId) && subtitleId.equals(format.id);
                            t.trackGroupId = groupIndex;
                            t.renderId = groupArrayIndex;
                            data.addSubtitle(t);
                        }
                    }
                }
            }
        }
        return data;
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void getExoSelectedTrack() {
        audioId = "";
        subtitleId = "";        
        for (Tracks.Group group : mMediaPlayer.getCurrentTracks().getGroups()) {
            if (!group.isSelected()) continue;
            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                if (!group.isTrackSelected(trackIndex)) continue;
                Format format = group.getTrackFormat(trackIndex);
                if (MimeTypes.isAudio(format.sampleMimeType)) {
                    audioId = format.id;
                }
                if (MimeTypes.isText(format.sampleMimeType)) {
                    subtitleId = format.id;
                }                
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void selectExoTrack(@Nullable TrackInfoBean videoTrackBean) {
        MappingTrackSelector.MappedTrackInfo trackInfo = getTrackSelector().getCurrentMappedTrackInfo();
        if (trackInfo != null) {
            if (videoTrackBean == null) {
                for (int renderIndex = 0; renderIndex < trackInfo.getRendererCount(); renderIndex++) {
                    if (trackInfo.getRendererType(renderIndex) == C.TRACK_TYPE_TEXT) {
                        DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().getParameters().buildUpon();
                        parametersBuilder.setRendererDisabled(renderIndex, true);
                        getTrackSelector().setParameters(parametersBuilder);
                        break;
                    }
                }
            } else {
                TrackGroupArray trackGroupArray = trackInfo.getTrackGroups(videoTrackBean.renderId);
                @SuppressLint("UnsafeOptInUsageError") DefaultTrackSelector.SelectionOverride override = new DefaultTrackSelector.SelectionOverride(videoTrackBean.trackGroupId, videoTrackBean.trackId);
                DefaultTrackSelector.Parameters.Builder parametersBuilder = getTrackSelector().buildUponParameters();
                parametersBuilder.setRendererDisabled(videoTrackBean.renderId, false);
                parametersBuilder.setSelectionOverride(videoTrackBean.renderId, trackGroupArray, override);
                getTrackSelector().setParameters(parametersBuilder);
            }
        }

    }

    public void setOnTimedTextListener(Player.Listener listener) {
        mMediaPlayer.addListener(listener);
    }

}
