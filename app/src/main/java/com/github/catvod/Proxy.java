package com.github.catvod;

/**
 * Compatibility surface used by modern csp_* spiders.  The app's own HTTP
 * server (RemoteServer) serves /proxy, so spiders can hand network work back
 * to the app instead of starting their own Go proxy.
 */
public final class Proxy {

    private static volatile int port = -1;

    private Proxy() {
    }

    public static void set(int value) {
        port = value;
    }

    public static int getPort() {
        return port;
    }

    public static String getUrl(boolean local) {
        return "http://" + (local ? "127.0.0.1" : "127.0.0.1") + ":" + getPort() + "/proxy";
    }
}
