package com.github.tvbox.osc.server;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.RegexUtils;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;
import org.json.JSONObject;

import org.greenrobot.eventbus.EventBus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;

/**
 * y-live 的本地 HTTP 服务(默认 9978): 提供手机扫码配置直播源/EPG 源的网页与推送接口.
 *
 * 相比 Y-TVBOX 去掉了点播相关的代理、文件管理、m3u8/dash、上传下载等接口,
 * 只保留: 静态网页资源(GET) + /action 推送(POST) + /dns-query(IJK 的 DoH).
 */
public class RemoteServer extends NanoHTTPD {

    public static int serverPort = 9978;
    private boolean isStarted = false;
    public static Context mContext;
    private DataReceiver mDataReceiver;

    private final ArrayList<RequestProcess> getRequestList = new ArrayList<>();
    private final ArrayList<RequestProcess> postRequestList = new ArrayList<>();

    private static final String PATTERN_ETH_STR = "^eth\\d+$";
    private static final Pattern ETH_PATTERN = Pattern.compile(PATTERN_ETH_STR);

    public RemoteServer(int port, Context context) {
        super(port);
        mContext = context;
        addGetRequestProcess();
        addPostRequestProcess();
    }

    private void addGetRequestProcess() {
        getRequestList.add(new RawRequestProcess(this.mContext, "/", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/index.html", R.raw.index, NanoHTTPD.MIME_HTML));
        getRequestList.add(new RawRequestProcess(this.mContext, "/style.css", R.raw.style, "text/css"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/script.js", R.raw.script, "application/x-javascript"));
        getRequestList.add(new RawRequestProcess(this.mContext, "/favicon.ico", R.drawable.app_icon, "image/x-icon"));
    }

    private void addPostRequestProcess() {
        postRequestList.add(new InputRequestProcess(this));
    }

    @Override
    public void start(int timeout, boolean daemon) throws IOException {
        super.start(timeout, daemon);
        isStarted = true;
        EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_SUCCESS));
    }

    @Override
    public void stop() {
        super.stop();
        isStarted = false;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (session.getMethod() == Method.GET && ("/".equals(session.getUri())
                || "/index.html".equals(session.getUri()) || "/api/config".equals(session.getUri()))) {
            EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_CONNECTION));
        }
        if (!session.getUri().isEmpty()) {
            String fileName = session.getUri().trim();
            if (fileName.indexOf('?') >= 0) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }
            if (session.getMethod() == Method.GET) {
                if ("/api/config".equals(fileName)) {
                    String json = "{\"liveUrl\":" + JSONObject.quote(Hawk.get(HawkConfig.LIVE_URL, ""))
                            + ",\"epgUrl\":" + JSONObject.quote(Hawk.get(HawkConfig.EPG_URL, "")) + "}";
                    return createJSONResponse(Response.Status.OK, json);
                }
                for (RequestProcess process : getRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.getParms(), null);
                    }
                }
                // IJK 的 DoH 本地代理(仅当设置里开了安全DNS时会被请求)
                if (fileName.equals("/dns-query")) {
                    String name = session.getParms().get("name");
                    byte[] rs;
                    try {
                        rs = OkGoHelper.dnsOverHttps.lookupHttpsForwardSync(name);
                    } catch (Throwable th) {
                        rs = new byte[0];
                    }
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/dns-message", new ByteArrayInputStream(rs), rs.length);
                }
            } else if (session.getMethod() == Method.POST) {
                Map<String, String> files = new HashMap<>();
                try {
                    if (session.getHeaders().containsKey("content-type")) {
                        String hd = session.getHeaders().get("content-type");
                        if (hd != null) {
                            // 修正中文乱码问题
                            if (hd.toLowerCase().contains("multipart/form-data") && !hd.toLowerCase().contains("charset=")) {
                                Matcher matcher = RegexUtils.getPattern("[ |\t]*(boundary[ |\t]*=[ |\t]*['|\"]?[^\"^'^;^,]*['|\"]?)", Pattern.CASE_INSENSITIVE).matcher(hd);
                                String boundary = matcher.find() ? matcher.group(1) : null;
                                if (boundary != null) {
                                    session.getHeaders().put("content-type", "multipart/form-data; charset=utf-8; " + boundary);
                                }
                            }
                        }
                    }
                    session.parseBody(files);
                } catch (IOException IOExc) {
                    return createPlainTextResponse(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + IOExc.getMessage());
                } catch (ResponseException rex) {
                    return createPlainTextResponse(rex.getStatus(), rex.getMessage());
                }
                for (RequestProcess process : postRequestList) {
                    if (process.isRequest(session, fileName)) {
                        return process.doResponse(session, fileName, session.getParms(), files);
                    }
                }
            }
        }
        return createPlainTextResponse(Response.Status.NOT_FOUND, "接口不存在");
    }

    public void setDataReceiver(DataReceiver receiver) {
        mDataReceiver = receiver;
    }

    public DataReceiver getDataReceiver() {
        return mDataReceiver;
    }

    public boolean isStarting() {
        return isStarted;
    }

    public String getServerAddress() {
        String ipAddress = getLocalIPAddress(mContext);
        if ("0.0.0.0".equals(ipAddress)) return "";
        return "http://" + ipAddress + ":" + RemoteServer.serverPort + "/";
    }

    public String getLoadAddress() {
        return "http://127.0.0.1:" + RemoteServer.serverPort + "/";
    }

    public static Response createPlainTextResponse(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, text);
    }

    public static Response createJSONResponse(Response.IStatus status, String text) {
        Response response = newFixedLengthResponse(status, "application/json; charset=utf-8", text);
        response.addHeader("Cache-Control", "no-store");
        return response;
    }

    @SuppressLint("DefaultLocale")
    public static String getLocalIPAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int ipAddress = wifiManager == null || wifiManager.getConnectionInfo() == null
                ? 0 : wifiManager.getConnectionInfo().getIpAddress();
        if (ipAddress == 0) {
            try {
                Enumeration<NetworkInterface> enumerationNi = NetworkInterface.getNetworkInterfaces();
                while (enumerationNi.hasMoreElements()) {
                    NetworkInterface networkInterface = enumerationNi.nextElement();
                    String interfaceName = networkInterface.getDisplayName();
                    if (ETH_PATTERN.matcher(interfaceName).matches() || interfaceName.equals("wlan0")) {
                        Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses();
                        while (enumIpAddr.hasMoreElements()) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }
        } else {
            return String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        }
        return "0.0.0.0";
    }
}
