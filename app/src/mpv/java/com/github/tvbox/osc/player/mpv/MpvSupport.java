package com.github.tvbox.osc.player.mpv;

import android.content.Context;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * MPV 版的编译期入口。标准版(std)使用 src/std 下的同名空实现，
 * 因此 libmpv 不会被打进标准 APK。
 */
public final class MpvSupport {

    public static final int DECODER_HARD = 0;
    public static final int DECODER_SOFT = 1;
    public static final String DECODE_HARD_NAME = "硬解";
    public static final String DECODE_SOFT_NAME = "软解";

    private MpvSupport() {
    }

    public static boolean isAvailable() {
        return true;
    }

    public static AbstractPlayer createPlayer(Context context, boolean hardwareDecode) {
        return new MPVPlayer(context, hardwareDecode);
    }

    public static int decodeModeOf(String name) {
        return DECODE_SOFT_NAME.equals(name) ? DECODER_SOFT : DECODER_HARD;
    }

    public static String decodeNameOf(int mode) {
        return mode == DECODER_SOFT ? DECODE_SOFT_NAME : DECODE_HARD_NAME;
    }
}
