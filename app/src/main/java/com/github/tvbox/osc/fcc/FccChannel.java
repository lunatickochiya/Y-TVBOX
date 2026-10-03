package com.github.tvbox.osc.fcc;

/**
 * Parsed description of a live channel URL that carries FCC information.
 *
 * <p>Supported inputs include:
 * <ul>
 *   <li>{@code rtp://239.1.1.1:5002?fcc=10.0.0.1:8027}</li>
 *   <li>{@code udp://@239.1.1.1:5002?fcc=10.0.0.1:15970&fcc-type=huawei}</li>
 *   <li>{@code http://192.168.1.1:5140/rtp/239.1.1.1:5002?fcc=10.0.0.1:8027}
 *       (rtp2httpd style proxy URL; the app can bypass the proxy and speak FCC
 *       itself)</li>
 * </ul>
 */
public final class FccChannel {
    /** Original live URL, used as fallback and for header matching. */
    public final String sourceUrl;
    public final String multicastIp;
    public final int multicastPort;
    public final String fccIp;
    public final int fccPort;
    public final FccType type;
    /** True when the source URL is an HTTP multicast proxy URL. */
    public final boolean proxied;

    FccChannel(String sourceUrl, String multicastIp, int multicastPort,
               String fccIp, int fccPort, FccType type, boolean proxied) {
        this.sourceUrl = sourceUrl;
        this.multicastIp = multicastIp;
        this.multicastPort = multicastPort;
        this.fccIp = fccIp;
        this.fccPort = fccPort;
        this.type = type;
        this.proxied = proxied;
    }

    @Override
    public String toString() {
        return "FccChannel{mcast=" + multicastIp + ":" + multicastPort
                + ", fcc=" + fccIp + ":" + fccPort
                + ", type=" + type + ", proxied=" + proxied + "}";
    }
}
