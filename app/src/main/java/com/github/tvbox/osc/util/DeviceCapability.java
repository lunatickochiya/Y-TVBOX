package com.github.tvbox.osc.util;

import android.app.ActivityManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备能力探测: 内存档位 / TV 判定 / 硬件解码器能力.
 * 低内存档位用于收缩缓存、图片加载和线程池, 降低内存占用.
 */
public class DeviceCapability {
    private static final String TAG = "DeviceCapability";

    public static final int MEMORY_LOW = 0;
    public static final int MEMORY_MEDIUM = 1;
    public static final int MEMORY_HIGH = 2;

    private static volatile DeviceCapability sInstance;
    private final boolean mIsTV;
    private final int mMemoryClass;
    private final boolean mHasHevcHwDecoder;
    private final boolean mSupportsTunneledPlayback;
    private final int mSocVendor;

    /** SoC 厂商 */
    public static final int SOC_OTHER = 0;
    public static final int SOC_MTK = 1;
    public static final int SOC_AMLOGIC = 2;
    public static final int SOC_ALLWINNER = 3;
    public static final int SOC_ROCKCHIP = 4;

    private DeviceCapability(Context context) {
        Context appCtx = context.getApplicationContext();
        mIsTV = detectTV(appCtx);
        mMemoryClass = detectMemoryClass(appCtx);
        mHasHevcHwDecoder = hasHardwareDecoder("video/hevc");
        mSupportsTunneledPlayback = detectTunneledPlayback("video/hevc");
        mSocVendor = detectSocVendor();
        Log.i(TAG, "memoryClass=" + mMemoryClass + " soc=" + socVendorName(mSocVendor) + " tv=" + mIsTV);
    }

    public static DeviceCapability get(Context context) {
        if (sInstance == null) {
            synchronized (DeviceCapability.class) {
                if (sInstance == null) {
                    sInstance = new DeviceCapability(context);
                }
            }
        }
        return sInstance;
    }

    private static boolean detectTV(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true;
        }
        if (context.getPackageManager().hasSystemFeature("android.software.leanback")) {
            return true;
        }
        String characteristics = Build.UNKNOWN;
        try {
            characteristics = (String) Build.class.getField("CHARACTERISTICS").get(null);
        } catch (Exception ignored) {
        }
        return characteristics != null && characteristics.contains("tv");
    }

    public static boolean hasHardwareDecoder(String mimeType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (codecInfo.isEncoder()) continue;
            if (!isHardwareCodec(codecInfo)) continue;
            for (String type : codecInfo.getSupportedTypes()) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isHardwareCodec(MediaCodecInfo codecInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return codecInfo.isHardwareAccelerated();
        }
        String name = codecInfo.getName().toLowerCase();
        return !name.startsWith("omx.google.") && !name.startsWith("c2.android.")
                && !name.contains("sw") && !name.contains("ffmpeg");
    }

    private static boolean detectTunneledPlayback(String mimeType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (codecInfo.isEncoder()) continue;
            if (!isHardwareCodec(codecInfo)) continue;
            for (String type : codecInfo.getSupportedTypes()) {
                if (!type.equalsIgnoreCase(mimeType)) continue;
                try {
                    MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(type);
                    if (caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback)) {
                        Log.d(TAG, "Tunneled playback supported by: " + codecInfo.getName());
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    /**
     * 电视盒子 SoC 厂商识别: 先看硬解解码器名字(最可靠), 再看 Build 字段.
     * 用于 IJK 硬解排序等按厂商区分的处理.
     */
    private static int detectSocVendor() {
        try {
            MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
                if (codecInfo.isEncoder()) continue;
                String name = codecInfo.getName().toLowerCase();
                if (name.startsWith("c2.mtk.") || name.startsWith("omx.mtk.")) return SOC_MTK;
                if (name.startsWith("c2.amlogic.") || name.startsWith("omx.amlogic.")) return SOC_AMLOGIC;
                if (name.startsWith("c2.allwinner.") || name.startsWith("omx.allwinner.") || name.contains("cedar")) {
                    return SOC_ALLWINNER;
                }
                if (name.startsWith("c2.rk.") || name.startsWith("omx.rk.")
                        || name.startsWith("c2.rockchip.") || name.startsWith("omx.rockchip.")) {
                    return SOC_ROCKCHIP;
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        String hardware = (Build.HARDWARE + " " + Build.BOARD + " " + Build.DEVICE + " " + Build.PRODUCT).toLowerCase();
        if (hardware.contains("amlogic") || hardware.contains("meson") || hardware.contains("gxbb")
                || hardware.contains("gxl") || hardware.contains("g12") || hardware.contains("sm1")
                || hardware.contains("sc2")) {
            return SOC_AMLOGIC;
        }
        if (hardware.contains("allwinner") || hardware.contains("sun50iw") || hardware.contains("sun8iw")
                || hardware.contains("sun7i") || hardware.contains("sun6i") || hardware.contains("exdroid")
                || hardware.contains("cedar")) {
            return SOC_ALLWINNER;
        }
        if (hardware.contains("rockchip") || hardware.contains("rk30") || hardware.contains("rk32")
                || hardware.contains("rk33") || hardware.contains("rk35") || hardware.contains("rk3588")) {
            return SOC_ROCKCHIP;
        }
        if (hardware.contains("mediatek") || hardware.matches(".*\\bmt[0-9]{4}.*")) {
            return SOC_MTK;
        }
        return SOC_OTHER;
    }

    public static String socVendorName(int vendor) {
        switch (vendor) {
            case SOC_MTK:
                return "MediaTek";
            case SOC_AMLOGIC:
                return "Amlogic";
            case SOC_ALLWINNER:
                return "Allwinner";
            case SOC_ROCKCHIP:
                return "Rockchip";
            default:
                return "Other";
        }
    }

    private static int detectMemoryClass(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return MEMORY_MEDIUM;
        int memoryClass = am.getMemoryClass();
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(memInfo);
        long totalMB = memInfo.totalMem / (1024 * 1024);
        if (memoryClass <= 128 || totalMB <= 1536) {
            return MEMORY_LOW;
        } else if (memoryClass <= 256 || totalMB <= 3072) {
            return MEMORY_MEDIUM;
        }
        return MEMORY_HIGH;
    }

    public boolean isTV() {
        return mIsTV;
    }

    public int getMemoryClass() {
        return mMemoryClass;
    }

    public boolean hasHevcHwDecoder() {
        return mHasHevcHwDecoder;
    }

    public boolean supportsTunneledPlayback() {
        return mSupportsTunneledPlayback;
    }

    public boolean isMtkTv() {
        return mSocVendor == SOC_MTK;
    }

    /** SoC 厂商: SOC_MTK / SOC_AMLOGIC / SOC_ALLWINNER / SOC_OTHER */
    public int getSocVendor() {
        return mSocVendor;
    }

    public boolean isAmlogic() {
        return mSocVendor == SOC_AMLOGIC;
    }

    public boolean isAllwinner() {
        return mSocVendor == SOC_ALLWINNER;
    }

    public boolean isRockchip() {
        return mSocVendor == SOC_ROCKCHIP;
    }

    public boolean shouldUseSurfaceView() {
        return mIsTV && mSupportsTunneledPlayback;
    }

    /** 视频缓存大小: 低内存 64M / 中 128M / 高 512M */
    public long getRecommendedCacheSize() {
        switch (mMemoryClass) {
            case MEMORY_LOW:
                return 64 * 1024 * 1024L;
            case MEMORY_MEDIUM:
                return 128 * 1024 * 1024L;
            default:
                return 512 * 1024 * 1024L;
        }
    }

    public static List<String> listHardwareDecoders() {
        List<String> result = new ArrayList<>();
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (codecInfo.isEncoder()) continue;
            if (!isHardwareCodec(codecInfo)) continue;
            StringBuilder sb = new StringBuilder(codecInfo.getName()).append(" [");
            String[] types = codecInfo.getSupportedTypes();
            for (int i = 0; i < types.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(types[i]);
            }
            sb.append("]");
            result.add(sb.toString());
        }
        return result;
    }
}
