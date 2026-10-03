package com.github.tvbox.osc.fcc;

import java.net.InetAddress;

/**
 * FCC control packet builders and parsers. Byte layout is a direct port of
 * rtp2httpd's {@code fcc_telecom.c} and {@code fcc_huawei.c}.
 *
 * <p>All multi-byte fields are network byte order (big endian), matching the
 * C implementation which memcpy's network-order values around.
 */
public final class FccPackets {

    public static final int PK_LEN_REQ_TELECOM = 40;
    public static final int PK_LEN_TERM_TELECOM = 16;
    public static final int PK_LEN_REQ_HUAWEI = 32;
    public static final int PK_LEN_NAT_HUAWEI = 8;
    public static final int PK_LEN_TERM_HUAWEI = 16;

    public static final int FMT_TELECOM_REQ = 2;
    public static final int FMT_TELECOM_RESP = 3;
    public static final int FMT_TELECOM_SYN = 4;
    public static final int FMT_TELECOM_TERM = 5;
    public static final int FMT_HUAWEI_REQ = 5;
    public static final int FMT_HUAWEI_RESP = 6;
    public static final int FMT_HUAWEI_SYN = 8;
    public static final int FMT_HUAWEI_TERM = 9;

    private FccPackets() {
    }

    public static boolean isRtcp(byte[] data, int len) {
        if (data == null || len < 8) return false;
        int version = (data[0] >> 6) & 0x03;
        if (version != 2) return false;
        int payloadType = data[1] & 0xFF;
        if (payloadType < 200 || payloadType > 211) return false;
        int lengthWords = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        int packetLen = (lengthWords + 1) * 4;
        return packetLen > 0 && packetLen <= len;
    }

    /** Telecom FCC request, RTCP FMT 2. */
    public static byte[] buildTelecomRequest(InetAddress multicast, int multicastPort, int clientPort) {
        byte[] pk = new byte[PK_LEN_REQ_TELECOM];
        pk[0] = (byte) (0x80 | FMT_TELECOM_REQ);
        pk[1] = (byte) 205;
        putU16(pk, 2, pk.length / 4 - 1);
        byte[] mcast = multicast.getAddress();
        System.arraycopy(mcast, 0, pk, 8, 4);
        putU16(pk, 16, clientPort);
        putU16(pk, 18, multicastPort);
        System.arraycopy(mcast, 0, pk, 20, 4);
        return pk;
    }

    /** Telecom FCC termination, RTCP FMT 5. */
    public static byte[] buildTelecomTermination(InetAddress multicast, int seqn) {
        byte[] pk = new byte[PK_LEN_TERM_TELECOM];
        pk[0] = (byte) (0x80 | FMT_TELECOM_TERM);
        pk[1] = (byte) 205;
        putU16(pk, 2, pk.length / 4 - 1);
        System.arraycopy(multicast.getAddress(), 0, pk, 8, 4);
        pk[12] = (byte) (seqn != 0 ? 0 : 1);
        putU16(pk, 14, seqn & 0xFFFF);
        return pk;
    }

    /** Huawei FCC request, RTCP FMT 5. */
    public static byte[] buildHuaweiRequest(InetAddress multicast, InetAddress localIp, int clientPort) {
        byte[] pk = new byte[PK_LEN_REQ_HUAWEI];
        pk[0] = (byte) (0x80 | FMT_HUAWEI_REQ);
        pk[1] = (byte) 205;
        putU16(pk, 2, 7);
        System.arraycopy(multicast.getAddress(), 0, pk, 8, 4);
        System.arraycopy(localIp.getAddress(), 0, pk, 20, 4);
        putU16(pk, 24, clientPort);
        putU16(pk, 26, 0x8000);
        putU32(pk, 28, 0x20000000L);
        return pk;
    }

    /** Huawei FCC NAT traversal packet, FMT 12. */
    public static byte[] buildHuaweiNat(long sessionId) {
        byte[] pk = new byte[PK_LEN_NAT_HUAWEI];
        pk[0] = 0x00;
        pk[1] = 0x03;
        pk[2] = 0x00;
        pk[3] = 0x00;
        putU32(pk, 4, sessionId & 0xFFFFFFFFL);
        return pk;
    }

    /** Huawei FCC termination, RTCP FMT 9. */
    public static byte[] buildHuaweiTermination(InetAddress multicast, int seqn) {
        byte[] pk = new byte[PK_LEN_TERM_HUAWEI];
        pk[0] = (byte) (0x80 | FMT_HUAWEI_TERM);
        pk[1] = (byte) 205;
        putU16(pk, 2, 3);
        System.arraycopy(multicast.getAddress(), 0, pk, 8, 4);
        if (seqn > 0) {
            pk[12] = 0x01;
            pk[13] = 0x00;
            putU16(pk, 14, seqn & 0xFFFF);
        } else {
            pk[12] = 0x02;
            pk[13] = 0x00;
        }
        return pk;
    }

    /**
     * Parse a control packet. Returns {@code null} when it is not a control
     * packet the session knows how to handle.
     */
    public static FccControl parse(FccType type, byte[] data, int len) {
        if (data == null || len < 12) return null;
        if (!isRtcp(data, len)) return null;
        int fmt = data[0] & 0x1F;
        if (type == FccType.HUAWEI) {
            return parseHuawei(data, len, fmt);
        }
        return parseTelecom(data, len, fmt);
    }

    private static FccControl parseTelecom(byte[] buf, int len, int fmt) {
        if (fmt == FMT_TELECOM_SYN) {
            FccControl c = new FccControl(FccControl.KIND_SYNC, fmt);
            return c;
        }
        if (fmt != FMT_TELECOM_RESP) return null;
        if ((buf[1] & 0xFF) != 205) return null;
        if (len < 36) return null;

        FccControl c = new FccControl(FccControl.KIND_TELECOM_RESPONSE, fmt);
        c.resultCode = buf[12] & 0xFF;
        c.actionType = buf[13] & 0xFF;
        c.signalPort = getU16(buf, 14);
        c.mediaPort = getU16(buf, 16);
        long ip = getU32(buf, 20);
        c.newIp = ip != 0 ? u32ToIp(ip) : null;
        c.validTime = getU32(buf, 24);
        c.speed = getU32(buf, 28);
        c.speedAfterSync = getU32(buf, 32);
        return c;
    }

    private static FccControl parseHuawei(byte[] buf, int len, int fmt) {
        if (fmt == FMT_HUAWEI_SYN) {
            return new FccControl(FccControl.KIND_SYNC, fmt);
        }
        if (fmt != FMT_HUAWEI_RESP) return null;
        if ((buf[1] & 0xFF) != 205) return null;
        if (len < 16) return null;

        FccControl c = new FccControl(FccControl.KIND_HUAWEI_RESPONSE, fmt);
        c.resultCode = buf[12] & 0xFF;
        c.actionType = getU16(buf, 14);
        if (c.actionType == 2 && len >= 24) {
            c.firstSeq = getU16(buf, 16);
            c.bitrateKbps = getU16(buf, 20);
            if (len >= 36) {
                int natFlag = buf[24] & 0xFF;
                c.natFlag = (natFlag << 2) >> 7; // extract bit 5
                c.natServerPort = getU16(buf, 26);
                c.sessionId = getU32(buf, 28);
                long ip = getU32(buf, 32);
                c.natServerIp = ip != 0 ? u32ToIp(ip) : null;
            }
        } else if (c.actionType == 3 && len >= 36) {
            c.natServerPort = getU16(buf, 26);
            long ip = getU32(buf, 32);
            c.natServerIp = ip != 0 ? u32ToIp(ip) : null;
        }
        return c;
    }

    public static String u32ToIp(long value) {
        return ((value >> 24) & 0xFF) + "." + ((value >> 16) & 0xFF) + "."
                + ((value >> 8) & 0xFF) + "." + (value & 0xFF);
    }

    static void putU16(byte[] dst, int off, int value) {
        dst[off] = (byte) ((value >> 8) & 0xFF);
        dst[off + 1] = (byte) (value & 0xFF);
    }

    static void putU32(byte[] dst, int off, long value) {
        dst[off] = (byte) ((value >> 24) & 0xFF);
        dst[off + 1] = (byte) ((value >> 16) & 0xFF);
        dst[off + 2] = (byte) ((value >> 8) & 0xFF);
        dst[off + 3] = (byte) (value & 0xFF);
    }

    static int getU16(byte[] src, int off) {
        return ((src[off] & 0xFF) << 8) | (src[off + 1] & 0xFF);
    }

    static long getU32(byte[] src, int off) {
        return ((long) (src[off] & 0xFF) << 24)
                | ((long) (src[off + 1] & 0xFF) << 16)
                | ((long) (src[off + 2] & 0xFF) << 8)
                | (src[off + 3] & 0xFF);
    }
}
