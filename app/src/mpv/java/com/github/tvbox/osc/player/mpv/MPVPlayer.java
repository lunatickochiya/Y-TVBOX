package com.github.tvbox.osc.player.mpv;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;

import java.util.Map;

import is.xyz.mpv.MPVLib;
import is.xyz.mpv.MPVNode;
import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * 基于 libmpv 的播放器。视频默认走 mpv 的 gpu 渲染器，MPV 硬解使用
 * mediacodec-copy（MediaCodec 解码后拷贝到 GPU，兼容性比 mediacodec 直通好）。
 *
 * 直播场景只保留必需能力：起播、暂停、停止、Surface、音量、倍速、音轨信息。
 */
public final class MPVPlayer extends AbstractPlayer implements MPVLib.EventObserver {

    private static final String TAG = "MpvPlayer";

    private static final String HWDEC_HARD = "mediacodec-copy";
    private static final String HWDEC_SOFT = "no";
    private static final String HWDEC_CODECS = "h264,hevc,mpeg2video,mpeg4,vp8,vp9,av1";

    private final Context mAppContext;
    private final boolean hardwareDecode;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private boolean mCreated;
    private boolean mReleased;
    private boolean mPrepared;
    private boolean mRenderingStarted;
    private boolean mStopping;
    private boolean mPlayWhenReady = true;
    private boolean mLooping;

    private String mPath;
    private Map<String, String> mHeaders;
    private Surface mSurface;

    private long mDurationMs;
    private int mVideoWidth;
    private int mVideoHeight;
    private float mSpeed = 1f;

    public MPVPlayer(Context context, boolean hardwareDecode) {
        mAppContext = context.getApplicationContext();
        this.hardwareDecode = hardwareDecode;
    }

    @Override
    public void initPlayer() {
        if (mReleased) return;
        try {
            MPVLib.INSTANCE.create(mAppContext);
            mCreated = true;

            // 不读取用户 mpv.conf；直播参数全部由 App 控制
            option("config", "no");
            option("idle", "yes");
            option("terminal", "no");
            option("input-default-bindings", "no");
            option("input-vo-keyboard", "no");
            option("osd-level", "0");
            option("vo", "gpu");
            option("gpu-context", "android");
            option("opengl-es", "yes");
            option("ao", "audiotrack,opensles");
            option("audio-set-media-role", "yes");
            option("keep-open", "no");
            option("force-window", "no");
            option("hwdec", hardwareDecode ? HWDEC_HARD : HWDEC_SOFT);
            option("hwdec-codecs", HWDEC_CODECS);
            // 直播低延迟缓冲：按秒限制，最多 16MB；不回看
            option("cache", "yes");
            option("cache-secs", "2");
            option("demuxer-readahead-secs", "1");
            option("demuxer-max-bytes", String.valueOf(16 * 1024 * 1024));
            option("demuxer-max-back-bytes", "0");
            option("network-timeout", "15");
            option("stream-lavf-o", "reconnect=1,reconnect_streamed=1,reconnect_delay_max=5");
            option("vd-lavc-threads", "4");
            option("tls-verify", "no");

            MPVLib.INSTANCE.init();
            MPVLib.addObserver(this);
            MPVLib.INSTANCE.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
            MPVLib.INSTANCE.observeProperty("eof-reached", MPVLib.MpvFormat.MPV_FORMAT_FLAG);
            MPVLib.INSTANCE.observeProperty("video-params/w", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            MPVLib.INSTANCE.observeProperty("video-params/h", MPVLib.MpvFormat.MPV_FORMAT_INT64);
            MPVLib.INSTANCE.setPropertyDouble("volume", 100.0);
            Log.w(TAG, "MPV 初始化完成, 解码=" + (hardwareDecode ? "硬解" : "软解"));
        } catch (Throwable e) {
            Log.e(TAG, "MPV 初始化失败", e);
            notifyError(-1, "MPV 初始化失败: " + e);
        }
    }

    private void option(String name, String value) {
        MPVLib.INSTANCE.setOptionString(name, value);
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        mPath = path == null ? null : path.trim();
        mHeaders = headers;
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        // 直播不使用 raw/asset 播放
    }

    @Override
    public void start() {
        mPlayWhenReady = true;
        setPause(false);
    }

    @Override
    public void pause() {
        mPlayWhenReady = false;
        setPause(true);
    }

    private void setPause(boolean paused) {
        if (!mCreated || mReleased) return;
        try {
            MPVLib.INSTANCE.setPropertyBoolean("pause", paused);
        } catch (Throwable e) {
            Log.w(TAG, "设置暂停失败: " + e);
        }
    }

    @Override
    public void stop() {
        mStopping = true;
        mPrepared = false;
        mRenderingStarted = false;
        if (!mCreated || mReleased) return;
        try {
            MPVLib.INSTANCE.command("stop");
        } catch (Throwable e) {
            Log.w(TAG, "停止失败: " + e);
        }
    }

    @Override
    public void prepareAsync() {
        if (!mCreated || mReleased || mPath == null) {
            notifyError(-1, "MPV 未就绪或播放地址为空");
            return;
        }
        mStopping = false;
        mPrepared = false;
        mRenderingStarted = false;
        mDurationMs = 0;
        mVideoWidth = 0;
        mVideoHeight = 0;
        try {
            applyHeaders();
            // 从头开始，替换当前播放项
            MPVLib.INSTANCE.command("loadfile", mPath, "replace");
            setPause(!mPlayWhenReady);
        } catch (Throwable e) {
            Log.e(TAG, "loadfile 失败", e);
            notifyError(-1, "MPV loadfile 失败: " + e);
        }
    }

    private void applyHeaders() {
        String userAgent = null;
        StringBuilder fields = new StringBuilder();
        if (mHeaders != null) {
            for (Map.Entry<String, String> entry : mHeaders.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null) continue;
                if ("User-Agent".equalsIgnoreCase(key)) {
                    userAgent = value;
                } else {
                    if (fields.length() > 0) fields.append(',');
                    fields.append(key).append(": ").append(value);
                }
            }
        }
        if (userAgent != null) {
            option("user-agent", userAgent);
        } else {
            option("user-agent", "");
        }
        option("http-header-fields", fields.length() == 0 ? "" : fields.toString());
    }

    @Override
    public void reset() {
        mPrepared = false;
        mRenderingStarted = false;
        mDurationMs = 0;
        mVideoWidth = 0;
        mVideoHeight = 0;
        if (!mCreated || mReleased) return;
        try {
            MPVLib.INSTANCE.command("stop");
        } catch (Throwable e) {
            Log.w(TAG, "reset 失败: " + e);
        }
    }

    @Override
    public boolean isPlaying() {
        if (!mCreated || mReleased || !mPrepared) return false;
        try {
            Boolean paused = MPVLib.INSTANCE.getPropertyBoolean("pause");
            Boolean eof = MPVLib.INSTANCE.getPropertyBoolean("eof-reached");
            return (paused == null || !paused) && (eof == null || !eof);
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public void seekTo(long time) {
        if (!mCreated || mReleased) return;
        try {
            MPVLib.INSTANCE.command("seek", String.valueOf(time / 1000.0), "absolute");
        } catch (Throwable e) {
            Log.w(TAG, "seek 失败: " + e);
        }
    }

    @Override
    public void release() {
        if (mReleased) return;
        mReleased = true;
        MPVLib.removeObserver(this);
        if (mCreated) {
            try {
                MPVLib.INSTANCE.setPropertyString("vo", "null");
            } catch (Throwable ignored) {
            }
            try {
                MPVLib.INSTANCE.detachSurface();
            } catch (Throwable ignored) {
            }
            try {
                MPVLib.INSTANCE.destroy();
            } catch (Throwable e) {
                Log.w(TAG, "释放 MPV 失败: " + e);
            }
            mCreated = false;
        }
        mSurface = null;
        mPrepared = false;
        mRenderingStarted = false;
    }

    @Override
    public long getCurrentPosition() {
        Double pos = doubleProperty("time-pos");
        return pos == null ? 0 : (long) (pos * 1000.0);
    }

    @Override
    public long getDuration() {
        if (mDurationMs <= 0) {
            Double duration = doubleProperty("duration");
            if (duration != null && duration > 0) {
                mDurationMs = (long) (duration * 1000.0);
            }
        }
        return mDurationMs;
    }

    @Override
    public int getBufferedPercentage() {
        // 直播流没有可用的百分比，回看时长由上层按 duration/position 处理
        return 0;
    }

    @Override
    public void setSurface(Surface surface) {
        if (mSurface == surface) return;
        if (!mCreated || mReleased) {
            mSurface = surface;
            return;
        }
        try {
            if (mSurface != null) {
                MPVLib.INSTANCE.setPropertyString("vo", "null");
                MPVLib.INSTANCE.detachSurface();
            }
            mSurface = surface;
            if (surface != null && surface.isValid()) {
                MPVLib.INSTANCE.attachSurface(surface);
                MPVLib.INSTANCE.setPropertyString("vo", "gpu");
                MPVLib.INSTANCE.setPropertyString("force-window", "yes");
                Log.w(TAG, "MPV Surface 已附加");
            }
        } catch (Throwable e) {
            Log.w(TAG, "附加 Surface 失败: " + e);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null) {
            setSurface(null);
            return;
        }
        try {
            android.graphics.Rect frame = holder.getSurfaceFrame();
            if (frame != null && frame.width() > 0 && frame.height() > 0 && mCreated && !mReleased) {
                MPVLib.INSTANCE.setPropertyString("android-surface-size", frame.width() + "x" + frame.height());
            }
        } catch (Throwable ignored) {
        }
        setSurface(holder.getSurface());
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (!mCreated || mReleased) return;
        try {
            float volume = Math.max(0f, Math.min(1f, (leftVolume + rightVolume) / 2f));
            MPVLib.INSTANCE.setPropertyDouble("volume", volume * 100.0);
        } catch (Throwable e) {
            Log.w(TAG, "设置音量失败: " + e);
        }
    }

    @Override
    public void setLooping(boolean looping) {
        mLooping = looping;
        if (!mCreated || mReleased) return;
        try {
            MPVLib.INSTANCE.setPropertyString("loop-file", looping ? "inf" : "no");
        } catch (Throwable e) {
            Log.w(TAG, "设置循环失败: " + e);
        }
    }

    @Override
    public void setOptions() {
        // 所有 mpv 选项在 initPlayer() 中设置
    }

    @Override
    public void setSpeed(float speed) {
        mSpeed = speed;
        if (!mCreated || mReleased) return;
        try {
            MPVLib.INSTANCE.setPropertyDouble("speed", speed);
        } catch (Throwable e) {
            Log.w(TAG, "设置倍速失败: " + e);
        }
    }

    @Override
    public float getSpeed() {
        return mSpeed;
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }

    /** 供直播界面显示音轨/字幕轨道；不参与播放控制。 */
    public TrackInfo getTrackInfo() {
        TrackInfo data = new TrackInfo();
        if (!mCreated || mReleased) return data;
        try {
            int count = intProperty("track-list/count");
            for (int i = 0; i < count; i++) {
                String type = stringProperty("track-list/" + i + "/type");
                if (!"audio".equals(type) && !"sub".equals(type)) continue;
                int id = intProperty("track-list/" + i + "/id");
                String title = stringProperty("track-list/" + i + "/title");
                String lang = stringProperty("track-list/" + i + "/lang");
                boolean selected = booleanProperty("track-list/" + i + "/selected");
                TrackInfoBean bean = new TrackInfoBean();
                bean.trackId = id;
                bean.language = lang == null ? "" : lang;
                bean.selected = selected;
                bean.name = (title == null || title.isEmpty())
                        ? (lang == null || lang.isEmpty() ? ("轨道 " + id) : lang)
                        : title;
                if ("audio".equals(type)) data.addAudio(bean);
                else data.addSubtitle(bean);
            }
        } catch (Throwable e) {
            Log.w(TAG, "读取轨道失败: " + e);
        }
        return data;
    }

    // ---------------- MPVLib.EventObserver ----------------

    @Override
    public void eventProperty(String property) {
    }

    @Override
    public void eventProperty(String property, long value) {
        if ("video-params/w".equals(property) || "video-params/h".equals(property)) {
            mMainHandler.post(this::notifyVideoSize);
        }
    }

    @Override
    public void eventProperty(String property, boolean value) {
        if ("paused-for-cache".equals(property)) {
            mMainHandler.post(() -> {
                if (mReleased) return;
                notifyInfo(value ? MEDIA_INFO_BUFFERING_START : MEDIA_INFO_BUFFERING_END, 0);
            });
        }
    }

    @Override
    public void eventProperty(String property, String value) {
    }

    @Override
    public void eventProperty(String property, double value) {
    }

    @Override
    public void eventProperty(String property, MPVNode value) {
    }

    @Override
    public void event(int eventId, MPVNode data) {
        mMainHandler.post(() -> handleEvent(eventId, data));
    }

    private void handleEvent(int eventId, MPVNode data) {
        if (mReleased) return;
        switch (eventId) {
            case MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED:
                mPrepared = true;
                mStopping = false;
                mDurationMs = 0;
                notifyVideoSize();
                notifyPrepared();
                Log.w(TAG, "MPV 文件已加载: " + mPath);
                break;
            case MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG:
                notifyVideoSize();
                break;
            case MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART:
                notifyVideoSize();
                if (!mRenderingStarted) {
                    mRenderingStarted = true;
                    notifyInfo(MEDIA_INFO_RENDERING_START, 0);
                }
                break;
            case MPVLib.MpvEvent.MPV_EVENT_END_FILE:
                handleEndFile(data);
                break;
            default:
                break;
        }
    }

    private void handleEndFile(MPVNode data) {
        String reason = nodeString(data, "reason");
        String error = nodeString(data, "error");
        Log.w(TAG, "MPV END_FILE reason=" + reason + " error=" + error);
        if (mStopping) {
            mStopping = false;
            return;
        }
        if ("eof".equals(reason)) {
            notifyCompletion();
        } else if ("error".equals(reason)) {
            notifyError(-1, error == null || error.isEmpty() ? "MPV 播放失败" : error);
        }
    }

    private static String nodeString(MPVNode data, String key) {
        if (data == null) return null;
        try {
            MPVNode node = data.get(key);
            return node == null ? null : node.asString();
        } catch (Throwable e) {
            return null;
        }
    }

    private void notifyVideoSize() {
        if (!mCreated || mReleased) return;
        int w = intProperty("video-params/w");
        int h = intProperty("video-params/h");
        if (w <= 0 || h <= 0) return;
        if (w == mVideoWidth && h == mVideoHeight) return;
        mVideoWidth = w;
        mVideoHeight = h;
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(w, h);
        }
    }

    private void notifyPrepared() {
        if (mPlayerEventListener != null) mPlayerEventListener.onPrepared();
    }

    private void notifyInfo(int what, int extra) {
        if (mPlayerEventListener != null) mPlayerEventListener.onInfo(what, extra);
    }

    private void notifyCompletion() {
        if (mPlayerEventListener != null) mPlayerEventListener.onCompletion();
    }

    private void notifyError(int code, String message) {
        if (mPlayerEventListener != null) mPlayerEventListener.onError(code, message);
    }

    private int intProperty(String name) {
        try {
            Integer value = MPVLib.INSTANCE.getPropertyInt(name);
            return value == null ? 0 : value;
        } catch (Throwable e) {
            return 0;
        }
    }

    private Double doubleProperty(String name) {
        try {
            return MPVLib.INSTANCE.getPropertyDouble(name);
        } catch (Throwable e) {
            return null;
        }
    }

    private boolean booleanProperty(String name) {
        try {
            Boolean value = MPVLib.INSTANCE.getPropertyBoolean(name);
            return value != null && value;
        } catch (Throwable e) {
            return false;
        }
    }

    private String stringProperty(String name) {
        try {
            return MPVLib.INSTANCE.getPropertyString(name);
        } catch (Throwable e) {
            return null;
        }
    }
}
