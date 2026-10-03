package com.github.tvbox.osc.fcc;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

/**
 * App-level FCC orchestrator. {@link #prepare(String)} returns a local relay
 * URL when the channel supports native FCC; otherwise callers keep the
 * original URL. Only one session can be active at a time.
 */
public final class FccController {

    private static final String TAG = "FccController";
    private static final long RELAY_QUEUE_BYTES = 16 * 1024 * 1024L;
    private static final int RELAY_SOCKET_TIMEOUT_MS = 5000;

    public interface Listener {
        /** Native FCC failed before any data; callers should play {@code sourceUrl}. */
        void onFccFatal(String sourceUrl, String reason);
    }

    private static volatile FccController instance;

    private FccSession session;
    private FccRelayServer relay;
    private ByteQueue queue;
    private WifiManager.MulticastLock multicastLock;
    private volatile String sourceUrl;
    private volatile Listener listener;

    private FccController() {
    }

    public static FccController get() {
        if (instance == null) {
            synchronized (FccController.class) {
                if (instance == null) instance = new FccController();
            }
        }
        return instance;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isActive() {
        return session != null;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    /**
     * Start a native FCC session for the given live URL.
     *
     * @return a local {@code http://127.0.0.1:port/live.ts} URL, or {@code null}
     *         when the URL has no FCC info or native FCC is disabled
     */
    public synchronized String prepare(String url) {
        stop();
        if (!Hawk.get(HawkConfig.FCC_ENABLE, true)) return null;
        if (url == null || url.isEmpty()) return null;

        FccChannel channel;
        try {
            channel = FccUrlParser.parse(url);
        } catch (Throwable t) {
            Log.w(TAG, "FCC URL parse failed: " + t);
            return null;
        }
        if (channel == null) return null;
        if (channel.proxied) {
            // HTTP multicast proxy URLs (rtp2httpd/udpxy) are already served by
            // a proxy that handles FCC server-side. Native FCC over a NAT'd LAN
            // cannot receive the operator's unicast burst (it arrives from a
            // different source port than the request and is not an established
            // conntrack flow), so leave these to the upstream proxy.
            Log.i(TAG, "FCC: proxied URL, keep upstream proxy handling");
            return null;
        }

        try {
            queue = new ByteQueue(RELAY_QUEUE_BYTES);
            relay = new FccRelayServer(queue);
            relay.start(RELAY_SOCKET_TIMEOUT_MS, true);
            int port = relay.getListeningPort();
            if (port <= 0) throw new IllegalStateException("relay did not start");
            sourceUrl = url;
            acquireMulticastLock();
            session = new FccSession(channel, queue, logger, sessionListener);
            session.start();
            String localUrl = "http://127.0.0.1:" + port + FccRelayServer.RELAY_PATH;
            Log.i(TAG, "FCC session started for " + channel + " -> " + localUrl);
            return localUrl;
        } catch (Throwable t) {
            Log.e(TAG, "FCC prepare failed: " + t);
            stop();
            return null;
        }
    }

    /** Stop the active session, if any. Safe to call repeatedly. */
    public synchronized void stop() {
        FccSession current = session;
        session = null;
        if (current != null) {
            try {
                current.stop();
            } catch (Throwable ignored) {
            }
        }
        ByteQueue currentQueue = queue;
        queue = null;
        if (currentQueue != null) {
            currentQueue.close();
        }
        FccRelayServer currentRelay = relay;
        relay = null;
        if (currentRelay != null) {
            try {
                currentRelay.stop();
            } catch (Throwable ignored) {
            }
        }
        releaseMulticastLock();
        sourceUrl = null;
    }

    private void acquireMulticastLock() {
        try {
            WifiManager wifiManager = (WifiManager) App.getInstance()
                    .getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager == null) return;
            multicastLock = wifiManager.createMulticastLock("ytvbox-fcc");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Throwable t) {
            Log.w(TAG, "multicast lock failed: " + t);
        }
    }

    private void releaseMulticastLock() {
        WifiManager.MulticastLock lock = multicastLock;
        multicastLock = null;
        if (lock != null) {
            try {
                if (lock.isHeld()) lock.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private final FccLogger logger = new FccLogger() {
        @Override
        public void d(String message) {
            Log.d(TAG, message);
        }

        @Override
        public void w(String message) {
            Log.w(TAG, message);
        }

        @Override
        public void e(String message) {
            Log.e(TAG, message);
        }
    };

    private final FccSessionListener sessionListener = new FccSessionListener() {
        @Override
        public void onFccActive() {
            Log.i(TAG, "FCC unicast burst active");
        }

        @Override
        public void onFccFallback(String reason) {
            Log.w(TAG, "FCC fallback to multicast: " + reason);
        }

        @Override
        public void onFatal(String reason) {
            String url = sourceUrl;
            Listener current = listener;
            Log.e(TAG, "FCC fatal: " + reason);
            if (current != null) current.onFccFatal(url, reason);
        }
    };
}
