package com.github.tvbox.osc.util;

/**
 * Playback defaults for the MediaCodec and SurfaceTexture implementations
 * shipped with Android 5.x devices.
 */
public final class LegacyPlaybackProfile {
    private static final int ANDROID_M = 23;

    private LegacyPlaybackProfile() {
    }

    public static boolean isLegacyAndroid(int sdkInt) {
        return sdkInt < ANDROID_M;
    }

    public static int defaultRenderType(int sdkInt) {
        return isLegacyAndroid(sdkInt) ? 1 : 0;
    }

    public static boolean enableExoTunneling(int sdkInt) {
        return !isLegacyAndroid(sdkInt);
    }

    public static boolean restoreIjkLoopFilter(int sdkInt) {
        return isLegacyAndroid(sdkInt);
    }
}
