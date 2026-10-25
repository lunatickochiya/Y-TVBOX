package com.github.tvbox.osc.player.mpv;

/** Test double: AVAILABLE can be toggled to simulate standard and MPV APKs. */
public final class MpvSupport {

    public static final int DECODER_HARD = 0;
    public static final int DECODER_SOFT = 1;
    public static final String DECODE_HARD_NAME = "硬解";
    public static final String DECODE_SOFT_NAME = "软解";

    public static boolean AVAILABLE = true;

    private MpvSupport() {
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static int decodeModeOf(String name) {
        return DECODE_SOFT_NAME.equals(name) ? DECODER_SOFT : DECODER_HARD;
    }

    public static String decodeNameOf(int mode) {
        return mode == DECODER_SOFT ? DECODE_SOFT_NAME : DECODE_HARD_NAME;
    }
}
