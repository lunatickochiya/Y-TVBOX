package com.github.tvbox.osc.player;
public final class EXOmPlayer {
    public static final int DECODER_HARD = 0;
    public static final int DECODER_SOFT = 1;
    public static final String DECODE_HARD_NAME = "硬解";
    public static final String DECODE_SOFT_NAME = "软解";
    public static String decodeNameOf(int mode) { return mode == DECODER_SOFT ? DECODE_SOFT_NAME : DECODE_HARD_NAME; }
    public static int decodeModeOf(String name) { return DECODE_SOFT_NAME.equals(name) ? DECODER_SOFT : DECODER_HARD; }
}
