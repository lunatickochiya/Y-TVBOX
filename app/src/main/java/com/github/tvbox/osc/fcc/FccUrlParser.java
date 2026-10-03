package com.github.tvbox.osc.fcc;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses live channel URLs into {@link FccChannel}. Returns {@code null} when
 * the URL has no usable FCC information, in which case the app keeps the
 * original playback path.
 */
public final class FccUrlParser {

    private static final int DEFAULT_FCC_PORT = 8027;

    private FccUrlParser() {
    }

    public static FccChannel parse(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return null;

        Map<String, String> query = parseQuery(trimmed);
        String fcc = query.get("fcc");
        if (fcc == null || fcc.isEmpty()) return null;

        FccType type = FccType.fromParam(query.get("fcc-type"));

        String[] multicast = parseMulticast(trimmed);
        if (multicast == null) return null;

        String[] server = parseServer(fcc);
        if (server == null) return null;

        boolean proxied = trimmed.toLowerCase(Locale.US).startsWith("http://")
                || trimmed.toLowerCase(Locale.US).startsWith("https://");

        return new FccChannel(trimmed, multicast[0], Integer.parseInt(multicast[1]),
                server[0], Integer.parseInt(server[1]), type, proxied);
    }

    /**
     * Extract the multicast address from an URL.
     *
     * @return {@code {ip, port}} or {@code null}
     */
    static String[] parseMulticast(String url) {
        String lower = url.toLowerCase(Locale.US);
        int schemeEnd = lower.indexOf("://");
        if (schemeEnd <= 0) return null;
        String scheme = lower.substring(0, schemeEnd);

        String target = null;
        if (scheme.equals("rtp") || scheme.equals("udp") || scheme.equals("igmp")
                || scheme.equals("mcast") || scheme.equals("multicast")) {
            // rtp://239.1.1.1:5002?fcc=...   rtp://@239.1.1.1:5002
            int start = schemeEnd + 3;
            int end = url.length();
            int slash = url.indexOf('/', start);
            if (slash >= 0) end = slash;
            int question = url.indexOf('?', start);
            if (question >= 0 && question < end) end = question;
            target = url.substring(start, end);
            if (target.startsWith("@")) target = target.substring(1);
            // Some lists use rtp://239.1.1.1:5002/ with trailing slash handled above.
            int lastColon = target.lastIndexOf(':');
            if (lastColon > 0) {
                String host = trimBrackets(target.substring(0, lastColon));
                String port = target.substring(lastColon + 1);
                if (isMulticastIp(host) && isPort(port)) return new String[]{host, port};
            }
            return null;
        }

        if (scheme.equals("http") || scheme.equals("https")) {
            // http://host:5140/rtp/239.1.1.1:5002?fcc=...  (rtp2httpd/udpxy style)
            int start = schemeEnd + 3;
            int pathStart = url.indexOf('/', start);
            if (pathStart < 0) return null;
            int queryStart = url.indexOf('?', pathStart);
            String path = queryStart >= 0 ? url.substring(pathStart, queryStart) : url.substring(pathStart);
            String[] segments = path.split("/");
            for (int i = 0; i + 1 < segments.length; i++) {
                String seg = segments[i];
                if (seg.equalsIgnoreCase("rtp") || seg.equalsIgnoreCase("udp")
                        || seg.equalsIgnoreCase("igmp") || seg.equalsIgnoreCase("mcast")) {
                    String candidate = segments[i + 1];
                    int lastColon = candidate.lastIndexOf(':');
                    if (lastColon > 0) {
                        String host = trimBrackets(candidate.substring(0, lastColon));
                        String port = candidate.substring(lastColon + 1);
                        if (isMulticastIp(host) && isPort(port)) return new String[]{host, port};
                    }
                }
            }
            return null;
        }

        return null;
    }

    /**
     * Parse the {@code fcc=ip:port} value.
     *
     * @return {@code {ip, port}} or {@code null}
     */
    static String[] parseServer(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        int colon = v.lastIndexOf(':');
        if (colon > 0 && colon < v.length() - 1) {
            String host = trimBrackets(v.substring(0, colon));
            String port = v.substring(colon + 1);
            if (isIpv4(host) && isPort(port)) return new String[]{host, port};
            return null;
        }
        if (isIpv4(v)) return new String[]{v, String.valueOf(DEFAULT_FCC_PORT)};
        // IPv6 and hostnames are not supported: let the original player handle it.
        return null;
    }

    /** Case-insensitive query parameter map with URL-decoding of the values. */
    static Map<String, String> parseQuery(String url) {
        Map<String, String> map = new LinkedHashMap<>();
        int question = url.indexOf('?');
        if (question < 0 || question == url.length() - 1) return map;
        String query = url.substring(question + 1);
        int hash = query.indexOf('#');
        if (hash >= 0) query = query.substring(0, hash);
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            map.put(urlDecode(key.toLowerCase(Locale.US)), urlDecode(value));
        }
        return map;
    }

    static boolean isMulticastIp(String ip) {
        if (!isIpv4(ip)) return false;
        int first = Integer.parseInt(ip.substring(0, ip.indexOf('.')));
        return first >= 224 && first <= 239;
    }

    static boolean isIpv4(String ip) {
        if (ip == null) return false;
        String[] parts = ip.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String p : parts) {
            if (p.isEmpty() || p.length() > 3) return false;
            for (int i = 0; i < p.length(); i++) {
                if (p.charAt(i) < '0' || p.charAt(i) > '9') return false;
            }
            int v = Integer.parseInt(p);
            if (v > 255) return false;
        }
        return true;
    }

    static boolean isPort(String port) {
        if (port == null || port.isEmpty() || port.length() > 5) return false;
        for (int i = 0; i < port.length(); i++) {
            if (port.charAt(i) < '0' || port.charAt(i) > '9') return false;
        }
        int p = Integer.parseInt(port);
        return p > 0 && p <= 65535;
    }

    private static String trimBrackets(String s) {
        if (s.startsWith("[") && s.endsWith("]")) return s.substring(1, s.length() - 1);
        return s;
    }

    private static String urlDecode(String s) {
        if (s.indexOf('%') < 0 && s.indexOf('+') < 0) return s;
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
