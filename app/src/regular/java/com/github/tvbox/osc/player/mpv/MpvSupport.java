package com.github.tvbox.osc.player.mpv;

import android.content.Context;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * 标准版(regular)没有 libmpv，保留同名空实现让 PlayerHelper 可以无分支编译。
 */
public final class MpvSupport {

    public static final int DECODER_HARD = 0;
    public static final int DECODER_SOFT = 1;
    public static final String DECODE_HARD_NAME = "硬解";
    public static final String DECODE_SOFT_NAME = "软解";

    private MpvSupport() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static AbstractPlayer createPlayer(Context context, boolean hardwareDecode) {
        return null;
    }

    public static int decodeModeOf(String name) {
        return DECODE_SOFT_NAME.equals(name) ? DECODER_SOFT : DECODER_HARD;
    }

    public static String decodeNameOf(int mode) {
        return mode == DECODER_SOFT ? DECODE_SOFT_NAME : DECODE_HARD_NAME;
    }
}
