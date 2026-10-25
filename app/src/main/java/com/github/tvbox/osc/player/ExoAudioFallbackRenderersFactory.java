package com.github.tvbox.osc.player;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;

import java.util.ArrayList;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;

/**
 * 点播默认 Exo 渲染器: 视频仍用系统 MediaCodec, 音频在系统解码器不支持
 * AC3/EAC3/DTS/TrueHD 等格式时用 nextlib 的 FFmpeg 音频解码兜底。
 *
 * 直播使用 {@link LiveExoRenderersFactory}, 视频/音频都会追加 FFmpeg。
 */
public final class ExoAudioFallbackRenderersFactory extends DefaultRenderersFactory {

    private static final String TAG = "ExoAudioFallback";

    public ExoAudioFallbackRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected void buildAudioRenderers(Context context, int extensionRendererMode,
                                       MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback,
                                       AudioSink audioSink, Handler eventHandler,
                                       AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        super.buildAudioRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
                audioSink, eventHandler, eventListener, out);
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
            return;
        }
        try {
            out.add(new FfmpegAudioRenderer(eventHandler, eventListener, audioSink));
        } catch (Throwable e) {
            Log.w(TAG, "FFmpeg 音频软解渲染器加载失败, 仅使用系统音频: " + e);
        }
    }
}
