package com.github.tvbox.osc.util;

import static okhttp3.ConnectionSpec.CLEARTEXT;
import static okhttp3.ConnectionSpec.COMPATIBLE_TLS;
import static okhttp3.ConnectionSpec.MODERN_TLS;
import static okhttp3.ConnectionSpec.RESTRICTED_TLS;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.base.App;

import com.lzy.okgo.OkGo;
import com.lzy.okgo.https.HttpsUtils;
import com.lzy.okgo.interceptor.HttpLoggingInterceptor;
import com.lzy.okgo.model.HttpHeaders;
import com.orhanobut.hawk.Hawk;

import java.io.File;
import java.util.List;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.Cache;
import okhttp3.ConnectionSpec;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.dnsoverhttps.DnsOverHttps;
import okhttp3.internal.Util;
import okhttp3.internal.Version;
import xyz.doikki.videoplayer.exo.ExoMediaSourceHelper;

public class OkGoHelper {
    public static final long DEFAULT_MILLISECONDS = 10000;      //默认的超时时间

    //https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/200
    public static HashMap<Integer, String > httpPhaseMap  = new HashMap<Integer, String>(){{
        put(200,"OK");
        put(301,"Moved Permanently");
        put(302,"Found");
        put(400,"Bad Request");
        put(401,"Unauthorized");
        put(403,"Forbidden");
        put(404,"Not Found");
        put(429,"Too Many Requests");
        put(500,"Internal Server Error");
        put(502,"Bad Gateway");
        put(503,"Service Unavailable");
        put(504,"Gateway Timeout");
    }};

    static void initExoOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkExoPlayer");

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }
        builder.addInterceptor(loggingInterceptor);
        builder.connectionSpecs(getConnectionSpec());
        builder.retryOnConnectionFailure(true);
        builder.followRedirects(true);
        builder.followSslRedirects(true);


        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        builder.dns(dnsOverHttps);

        ExoMediaSourceHelper.getInstance(App.getInstance()).setOkClient(builder.build());
    }

    public static DnsOverHttps dnsOverHttps = null;

    public static ArrayList<String> dnsHttpsList = new ArrayList<>();

    public static List<ConnectionSpec> getConnectionSpec() {
        return Util.immutableList(RESTRICTED_TLS, MODERN_TLS, COMPATIBLE_TLS, CLEARTEXT);
    }


    public static String getDohUrl(int type) {
        switch (type) {
            case 1: {
                return "https://doh.pub/dns-query";
            }
            case 2: {
                return "https://dns.alidns.com/dns-query";
            }
            case 3: {
                return "https://doh.360.cn/dns-query";
            }
            case 4: {
                // return "https://1.1.1.1/dns-query";   // takagen99 - removed Cloudflare
                return "https://dns.google/dns-query";
            }
            case 5: {
                return "https://dns.adguard.com/dns-query";
            }
            case 6: {
                return "https://dns.quad9.net/dns-query";
            }
            case 7: {
                return "https://cloudflare-dns.com/dns-query";
            }
            case 8: {
                return "https://doh.opendns.com";
            }
        }
        if (type == getCustomDohIndex()) {
            return Hawk.get(HawkConfig.DOH_CUSTOM_URL, "");
        }
        return "";
    }

    /** 「自定义」安全DNS在列表中的位置, 恒为最后一项。 */
    public static int getCustomDohIndex() {
        return dnsHttpsList.size() - 1;
    }

    static void initDnsOverHttps() {
        dnsHttpsList.add("关闭");
        dnsHttpsList.add("腾讯");
        dnsHttpsList.add("阿里");
        dnsHttpsList.add("360");
        dnsHttpsList.add("Google");
        dnsHttpsList.add("AdGuard");
        dnsHttpsList.add("Quad9");
        dnsHttpsList.add("Cloudflare");
        dnsHttpsList.add("OpenDNS");
        // 「自定义」必须始终在列表末尾, 选择它的序号会写入 HawkConfig.DOH_URL
        dnsHttpsList.add("自定义");
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkExoPlayer");
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }
        builder.addInterceptor(loggingInterceptor);
        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }
        builder.connectionSpecs(getConnectionSpec());
        builder.cache(new Cache(new File(App.getInstance().getCacheDir().getAbsolutePath(), "dohcache"), 10 * 1024 * 1024));
        OkHttpClient dohClient = builder.build();
        int sel = Hawk.get(HawkConfig.DOH_URL, 0);
        String dohUrl = getDohUrl(sel);
        dnsOverHttps = new DnsOverHttps.Builder().client(dohClient)
                .url(dohUrl.isEmpty() ? null : HttpUrl.get(dohUrl))
                .bootstrapDnsHosts(getDohIps(sel))
                .build();
    }

    /**
     * 内置各安全DNS服务的固定 IP, 让 DoH 引导解析不依赖系统 DNS。
     * 系统 DNS 不可用/极慢时 (如运营商 IPv6 DNS 失联, 每次解析要等超时回落),
     * DoH 仍能立即工作。返回 null 表示该服务没有可信固定 IP, 走系统 DNS 引导。
     */
    @Nullable
    public static List<InetAddress> getDohIps(int type) {
        if (type == getCustomDohIndex()) {
            // 自定义: 引导IP存 Hawk, 逗号/分号/空格分隔, 可不填(走系统DNS引导)
            String raw = Hawk.get(HawkConfig.DOH_CUSTOM_IPS, "");
            if (raw == null || raw.trim().isEmpty()) return null;
            try {
                List<InetAddress> hosts = new ArrayList<>();
                for (String ip : raw.split("[,;，；\\s]+")) {
                    if (!ip.trim().isEmpty()) hosts.add(InetAddress.getByName(ip.trim()));
                }
                return hosts.isEmpty() ? null : hosts;
            } catch (Throwable th) {
                th.printStackTrace();
                return null;
            }
        }
        String[] ips;
        switch (type) {
            case 1: // 腾讯 doh.pub
                ips = new String[]{"1.12.12.12", "120.53.53.53"};
                break;
            case 2: // 阿里 dns.alidns.com
                ips = new String[]{"223.5.5.5", "223.6.6.6"};
                break;
            case 4: // Google dns.google
                ips = new String[]{"8.8.8.8", "8.8.4.4"};
                break;
            case 5: // AdGuard dns.adguard.com
                ips = new String[]{"94.140.14.140", "94.140.14.141"};
                break;
            case 6: // Quad9 dns.quad9.net
                ips = new String[]{"9.9.9.9", "149.112.112.112"};
                break;
            case 7: // Cloudflare
                ips = new String[]{"1.1.1.1", "1.0.0.1"};
                break;
            case 8: // OpenDNS
                ips = new String[]{"208.67.222.222", "208.67.220.220"};
                break;
            default: // 关闭 / 360 无可信固定 IP
                return null;
        }
        try {
            List<InetAddress> hosts = new ArrayList<>();
            for (String ip : ips) hosts.add(InetAddress.getByName(ip));
            return hosts;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    static OkHttpClient defaultClient = null;
    static OkHttpClient noRedirectClient = null;

    public static OkHttpClient getDefaultClient() {
        return defaultClient;
    }

    public static OkHttpClient getNoRedirectClient() {
        return noRedirectClient;
    }

    public static void init() {
        initDnsOverHttps();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor("OkGo");

        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.BODY);
            loggingInterceptor.setColorLevel(Level.INFO);
        } else {
            loggingInterceptor.setPrintLevel(HttpLoggingInterceptor.Level.NONE);
            loggingInterceptor.setColorLevel(Level.OFF);
        }

        //builder.retryOnConnectionFailure(false);
        builder.connectionSpecs(getConnectionSpec());
        builder = builder.addInterceptor(loggingInterceptor)
                .readTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .writeTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .connectTimeout(DEFAULT_MILLISECONDS, TimeUnit.MILLISECONDS)
                .dns(dnsOverHttps);
        try {
            setOkHttpSsl(builder);
        } catch (Throwable th) {
            th.printStackTrace();
        }

        HttpHeaders.setUserAgent(Version.userAgent());
        OkHttpClient okHttpClient = builder.build();
        OkGo.getInstance().setOkHttpClient(okHttpClient);

        defaultClient = okHttpClient;
        builder.followRedirects(false);
        builder.followSslRedirects(false);
        noRedirectClient = builder.build();

        initExoOkHttpClient();        
    }

    private static synchronized void setOkHttpSsl(OkHttpClient.Builder builder) {
        try {
            final SSLSocketFactory sslSocketFactory = new SSLCompat();
            builder.sslSocketFactory(sslSocketFactory, SSLCompat.TM);
            builder.hostnameVerifier(HttpsUtils.UnSafeHostnameVerifier);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static class Tls12SocketFactory extends SSLSocketFactory {

        private static final String[] TLS_SUPPORT_VERSION = {"TLSv1.1", "TLSv1.2"};

        final SSLSocketFactory delegate;

        private Tls12SocketFactory(SSLSocketFactory base) {
            this.delegate = base;
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
            return patch(delegate.createSocket(s, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
            return patch(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress host, int port) throws IOException {
            return patch(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
            return patch(delegate.createSocket(address, port, localAddress, localPort));
        }

        private Socket patch(Socket s) {
            //代理SSLSocketFactory在创建一个Socket连接的时候，会设置Socket的可用的TLS版本。
            if (s instanceof SSLSocket) {
                ((SSLSocket) s).setEnabledProtocols(TLS_SUPPORT_VERSION);
            }
            return s;
        }
    }
}