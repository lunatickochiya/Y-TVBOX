package com.github.tvbox.osc.fcc;

/**
 * RTP / MPEG-TS datagram parser. Port of rtp2httpd's {@code rtp_get_payload}
 * plus the stream layer's decision to drop stray non-RTP, non-TS datagrams.
 *
 * <p>FEC payload types (127/97) are reported but not used: the relay works
 * without FEC, matching a plain udpxy/rtp2httpd setup.
 */
public final class RtpPacket {
    private static final int FEC_PAYLOAD_TYPE_1 = 127;
    private static final int FEC_PAYLOAD_TYPE_2 = 97;

    public final boolean rtp;
    public final boolean fec;
    public final int seq;
    public final int payloadType;
    public final boolean marker;
    public final byte[] payload;
    public final int offset;
    public final int length;

    private RtpPacket(boolean rtp, boolean fec, int seq, int payloadType, boolean marker,
                      byte[] payload, int offset, int length) {
        this.rtp = rtp;
        this.fec = fec;
        this.seq = seq;
        this.payloadType = payloadType;
        this.marker = marker;
        this.payload = payload;
        this.offset = offset;
        this.length = length;
    }

    /**
     * @return parsed packet, or {@code null} when the datagram should be dropped
     */
    public static RtpPacket parse(byte[] data, int len) {
        if (data == null || len <= 0) return null;

        if (len >= 12 && (data[0] & 0xC0) == 0x80) {
            int payloadType = data[1] & 0x7F;
            int seq = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            if (payloadType == FEC_PAYLOAD_TYPE_1 || payloadType == FEC_PAYLOAD_TYPE_2) {
                return new RtpPacket(true, true, seq, payloadType, false, data, 0, len);
            }

            int flags = data[0] & 0xFF;
            int payloadStart = 12 + (flags & 0x0F) * 4;
            if (payloadStart > len) return null;

            if ((flags & 0x10) != 0) {
                if (payloadStart + 4 > len) return null;
                int extLen = ((data[payloadStart + 2] & 0xFF) << 8) | (data[payloadStart + 3] & 0xFF);
                if (extLen > (len - payloadStart - 4) / 4) return null;
                payloadStart += 4 + 4 * extLen;
            }

            int payloadLength = len - payloadStart;
            if ((flags & 0x20) != 0) {
                payloadLength -= data[len - 1] & 0xFF;
            }
            if (payloadLength <= 0 || payloadStart + payloadLength > len) return null;

            byte[] copy = new byte[payloadLength];
            System.arraycopy(data, payloadStart, copy, 0, payloadLength);
            boolean marker = (data[1] & 0x80) != 0;
            return new RtpPacket(true, false, seq, payloadType, marker, copy, 0, payloadLength);
        }

        // Bare MPEG-TS over UDP (RTSP negotiated plain MP2T / raw TS multicast).
        if ((data[0] & 0xFF) == 0x47) {
            byte[] copy = new byte[len];
            System.arraycopy(data, 0, copy, 0, len);
            return new RtpPacket(false, false, -1, -1, false, copy, 0, len);
        }

        return null;
    }
}
