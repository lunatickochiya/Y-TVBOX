package com.github.tvbox.osc.fcc;

/**
 * Decoded FCC control (RTCP-like) packet received from the FCC server.
 *
 * <p>Field layout follows rtp2httpd's {@code fcc_telecom.c} / {@code fcc_huawei.c}.
 * Unused fields are zero / {@code null} depending on {@link #kind}.
 */
public final class FccControl {
    /** Telecom FMT 3 server response. */
    public static final int KIND_TELECOM_RESPONSE = 1;
    /** FMT 4 (telecom) / FMT 8 (huawei) sync notification. */
    public static final int KIND_SYNC = 2;
    /** Huawei FMT 6 server response. */
    public static final int KIND_HUAWEI_RESPONSE = 3;
    public static final int KIND_UNKNOWN = 0;

    public final int kind;
    public final int fmt;

    // Telecom response (FMT 3). resultCode == 0 means success.
    public int resultCode;
    /** 1 = no unicast needed, 2 = unicast follows, 3 = redirect. */
    public int actionType;
    public int signalPort;
    public int mediaPort;
    public String newIp;
    public long validTime;
    public long speed;
    public long speedAfterSync;

    // Huawei response (FMT 6). resultCode == 1 means success.
    public int firstSeq;
    public int bitrateKbps;
    public int natFlag;
    public int natServerPort;
    public String natServerIp;
    public long sessionId;

    FccControl(int kind, int fmt) {
        this.kind = kind;
        this.fmt = fmt;
    }
}
