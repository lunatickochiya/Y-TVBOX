package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.Epginfo;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

import okhttp3.ResponseBody;

/**
 * Fetches an XMLTV EPG file once, caches the parsed result and answers
 * per-channel queries for {@code LivePlayActivity.getEpg()}.
 *
 * <p>The router-side {@code 2wan-route-iptv} advertises
 * {@code http://<router>/iptv_epg.xml.gz}; the DIYP JSON format the app used
 * before cannot read it, so XMLTV URLs (.xml / .xml.gz) are routed here.
 */
public final class XmltvEpgUtil {

    public interface Callback {
        void onResult(List<Epginfo> list);
    }

    private static final long CACHE_TTL_MS = 30 * 60 * 1000L;
    private static final long FAILURE_BACKOFF_MS = 60 * 1000L;

    private static final class Waiter {
        final String channelName;
        final String tagName;
        final Date date;
        final Callback callback;

        Waiter(String channelName, String tagName, Date date, Callback callback) {
            this.channelName = channelName;
            this.tagName = tagName;
            this.date = date;
            this.callback = callback;
        }
    }

    private static XmltvEpgParser.Parsed cache;
    private static String cacheUrl;
    private static long cacheTime;
    private static String loadingUrl;
    private static String failureUrl;
    private static long failureTime;
    private static Waiter deferred;
    private static final List<Waiter> waiters = new ArrayList<>();

    private XmltvEpgUtil() {
    }

    /** True when the configured EPG address is an XMLTV file. */
    public static boolean isXmltvUrl(String url) {
        if (url == null) return false;
        String value = url.trim().toLowerCase(Locale.US);
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        return value.endsWith(".xml") || value.endsWith(".xml.gz")
                || value.endsWith(".xmltv") || value.endsWith(".xmltv.gz");
    }

    /**
     * Query the EPG of one channel. The callback runs on the caller thread for
     * cached data and on the main thread after a network fetch (OkGo).
     */
    public static void query(String url, String channelName, String tagName, Date date, Callback callback) {
        if (url == null || url.isEmpty()) {
            callback.onResult(Collections.<Epginfo>emptyList());
            return;
        }
        long now = System.currentTimeMillis();
        if (cache != null && url.equals(cacheUrl) && now - cacheTime < CACHE_TTL_MS) {
            callback.onResult(XmltvEpgParser.query(cache, channelName, tagName, date));
            return;
        }
        if (url.equals(failureUrl) && now - failureTime < FAILURE_BACKOFF_MS) {
            callback.onResult(Collections.<Epginfo>emptyList());
            return;
        }
        Waiter waiter = new Waiter(channelName, tagName, date, callback);
        if (url.equals(loadingUrl)) {
            synchronized (waiters) {
                waiters.add(waiter);
            }
            return;
        }
        if (loadingUrl != null) {
            synchronized (waiters) {
                deferred = waiter;
            }
            return;
        }
        loadingUrl = url;
        synchronized (waiters) {
            waiters.add(waiter);
        }
        fetch(url);
    }

    private static void fetch(final String url) {
        // convertResponse runs on OkGo's worker thread, so the 1MB+ XMLTV is
        // decoded and parsed off the main thread; onSuccess only caches it.
        OkGo.<XmltvEpgParser.Parsed>get(url).execute(new AbsCallback<XmltvEpgParser.Parsed>() {
            @Override
            public XmltvEpgParser.Parsed convertResponse(okhttp3.Response response) throws Throwable {
                ResponseBody body = response.body();
                if (body == null) return null;
                String xml = decode(body.bytes());
                return xml == null ? null : XmltvEpgParser.parse(xml);
            }

            @Override
            public void onSuccess(Response<XmltvEpgParser.Parsed> response) {
                finish(url, response.body());
            }

            @Override
            public void onError(Response<XmltvEpgParser.Parsed> response) {
                finish(url, null);
            }
        });
    }

    private static void finish(String url, XmltvEpgParser.Parsed parsed) {
        if (parsed != null) {
            cache = parsed;
            cacheUrl = url;
            cacheTime = System.currentTimeMillis();
            failureUrl = null;
        } else {
            failureUrl = url;
            failureTime = System.currentTimeMillis();
        }
        if (url.equals(loadingUrl)) loadingUrl = null;

        List<Waiter> ready = new ArrayList<>();
        Waiter next = null;
        synchronized (waiters) {
            Iterator<Waiter> iterator = waiters.iterator();
            while (iterator.hasNext()) {
                Waiter waiter = iterator.next();
                ready.add(waiter);
                iterator.remove();
            }
            if (deferred != null) {
                next = deferred;
                deferred = null;
            }
        }
        for (Waiter waiter : ready) {
            waiter.callback.onResult(parsed != null
                    ? XmltvEpgParser.query(parsed, waiter.channelName, waiter.tagName, waiter.date)
                    : Collections.<Epginfo>emptyList());
        }
        if (next != null) {
            query(url, next.channelName, next.tagName, next.date, next.callback);
        }
    }

    private static String decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        if (data.length > 2 && (data[0] & 0xFF) == 0x1F && (data[1] & 0xFF) == 0x8B) {
            try {
                GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));
                ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(8192, data.length * 4));
                byte[] buffer = new byte[16384];
                int read;
                while ((read = gzip.read(buffer)) > 0) {
                    output.write(buffer, 0, read);
                }
                gzip.close();
                return new String(output.toByteArray(), "UTF-8");
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
