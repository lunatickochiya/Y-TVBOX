package com.github.tvbox.osc.player;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import java.util.ArrayList;
import java.util.List;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;

/**
 * 直播用 Exo 渲染器工厂: 在系统硬解(MediaCodec)之外追加 FFmpeg 软解渲染器,
 * 让 Exo 能软解 H.264 / H.265 / MPEG2 / VP8 / VP9.
 *
 * FFmpeg 渲染器加在 (extensions 模式决定顺序):
 * - EXTENSION_RENDERER_MODE_PREFER: 加在最前 → 软解优先(软解)
 * - EXTENSION_RENDERER_MODE_ON:     加在最后 → 硬解优先, 软解兜底(硬解)
 * - EXTENSION_RENDERER_MODE_OFF:    不加
 *
 * 软解比 nextlib 默认更省内存: 线程数上限 4, 输入/输出缓冲各 2 帧
 * (默认 = CPU 核数 + 4/4 缓冲, 4K 下仅缓冲就差 40MB 以上).
 * 音频的 FFmpeg 软解(AC3/EAC3/DTS 等)由 DefaultRenderersFactory 自动追加 media3 扩展.
 */
public class LiveExoRenderersFactory extends DefaultRenderersFactory {

    private static final String TAG = "LiveExoRenderers";

    /** 软解线程数上限: 线程越多越吃内存, 盒子 4 线程足够 */
    private static final int MAX_SOFT_DECODE_THREADS = 4;
    /** 软解输入缓冲帧数(默认 4) */
    private static final int SOFT_DECODE_INPUT_BUFFERS = 2;
    /** 软解输出缓冲帧数(默认 4, 4K 每帧约 12MB) */
    private static final int SOFT_DECODE_OUTPUT_BUFFERS = 2;
    /** 连续丢帧上报间隔(避免软解跟不上时频繁打扰播放器) */
    private static final int MAX_DROPPED_FRAMES_TO_NOTIFY = 100;

    public LiveExoRenderersFactory(Context context) {
        super(context);
    }

    @Override
    protected void buildAudioRenderers(Context context, int extensionRendererMode,
                                       MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback,
                                       AudioSink audioSink, Handler eventHandler,
                                       AudioRendererEventListener eventListener, ArrayList<Renderer> out) {
        // 乐视/MStar 的此解码器在配置 MPEG 音频时会触发 ACodec 原生断言(SIGABRT),
        // 进程直接退出, enableDecoderFallback 无法捕获; 必须在创建 MediaCodec 之前排除.
        MediaCodecSelector audioCodecSelector = (mimeType, requiresSecureDecoder, requiresTunnelingDecoder) -> {
            List<MediaCodecInfo> decoders = mediaCodecSelector.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder);
            ArrayList<MediaCodecInfo> supported = new ArrayList<>();
            for (MediaCodecInfo decoder : decoders) {
                if (!"OMX.google.mstmp3.decoder".equalsIgnoreCase(decoder.name)) {
                    supported.add(decoder);
                }
            }
            return supported;
        };
        // 仍按系统音频优先、FFmpeg 兜底选择, 与视频软/硬解模式无关.
        super.buildAudioRenderers(context, EXTENSION_RENDERER_MODE_ON, audioCodecSelector, enableDecoderFallback,
                audioSink, eventHandler, eventListener, out);
        // 系统音频解码器不支持 AC3/EAC3/DTS/TrueHD 等格式时, 用 nextlib 内置的 FFmpeg 音频解码兜底.
        try {
            out.add(new FfmpegAudioRenderer(eventHandler, eventListener, audioSink));
        } catch (Throwable e) {
            Log.w(TAG, "FFmpeg 音频软解渲染器加载失败, 仅使用系统音频: " + e);
        }
    }

    @Override
    protected void buildVideoRenderers(Context context, int extensionRendererMode,
                                       MediaCodecSelector mediaCodecSelector, boolean enableDecoderFallback,
                                       Handler eventHandler, VideoRendererEventListener eventListener,
                                       long allowedVideoJoiningTimeMs, ArrayList<Renderer> out) {
        super.buildVideoRenderers(context, extensionRendererMode, mediaCodecSelector, enableDecoderFallback,
                eventHandler, eventListener, allowedVideoJoiningTimeMs, out);
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) {
            return;
        }
        int index = out.size();
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            index--;
        }
        try {
            int threads = Math.min(Runtime.getRuntime().availableProcessors(), MAX_SOFT_DECODE_THREADS);
            FfmpegVideoRenderer renderer = new FfmpegVideoRenderer(allowedVideoJoiningTimeMs, eventHandler,
                    eventListener, MAX_DROPPED_FRAMES_TO_NOTIFY, Math.max(threads, 1),
                    SOFT_DECODE_INPUT_BUFFERS, SOFT_DECODE_OUTPUT_BUFFERS);
            out.add(index, renderer);
        } catch (Throwable e) {
            // 软解渲染器不可用(缺库/机型不支持)时退回纯硬解, 不影响播放
            Log.w(TAG, "FFmpeg 软解渲染器加载失败, 回退硬解: " + e);
        }
    }
}
