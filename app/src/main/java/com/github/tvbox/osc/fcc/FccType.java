package com.github.tvbox.osc.fcc;

/**
 * FCC (Fast Channel Change) protocol variants.
 *
 * <p>Telecom/ZTE/Fiberhome uses RTCP FMT 2/3/4/5 on a single socket.
 * Huawei uses RTCP FMT 5/6/8/9 with a media/signal port pair and optional NAT
 * traversal (FMT 12). The reference implementation is rtp2httpd's fcc module.
 */
public enum FccType {
    TELECOM,
    HUAWEI;

    /**
     * Parse the {@code fcc-type} query parameter. rtp2httpd defaults to the
     * telecom protocol when the parameter is absent, so do the same.
     */
    public static FccType fromParam(String value) {
        if (value == null) return TELECOM;
        String v = value.trim().toLowerCase();
        if (v.equals("huawei")) return HUAWEI;
        if (v.equals("telecom") || v.equals("zteg") || v.equals("zte") || v.equals("fiberhome")) {
            return TELECOM;
        }
        return TELECOM;
    }
}
