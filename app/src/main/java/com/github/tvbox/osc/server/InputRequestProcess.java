package com.github.tvbox.osc.server;

import java.util.Map;
import java.net.URI;
import org.json.JSONObject;

import fi.iki.elonen.NanoHTTPD;

/**
 * 响应按键和输入
 */

public class InputRequestProcess implements RequestProcess {
    private final RemoteServer remoteServer;

    public InputRequestProcess(RemoteServer remoteServer) {
        this.remoteServer = remoteServer;
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String fileName) {
        return session.getMethod() == NanoHTTPD.Method.POST && "/action".equals(fileName);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String fileName, Map<String, String> params, Map<String, String> files) {
        if (!"/action".equals(fileName)) {
            return result(NanoHTTPD.Response.Status.NOT_FOUND, false, "接口不存在");
        }
        String action = params == null ? null : params.get("do");
        String value = params == null ? null : params.get("url");
        if (!("live".equals(action) || "epg".equals(action)) || value == null) {
            return result(NanoHTTPD.Response.Status.BAD_REQUEST, false, "请选择直播源或 EPG，并填写地址");
        }
        String url = value.trim();
        // EPG 空字符串表示停用自定义节目单；直播源必须是可下载的 HTTP(S) 列表。
        if (!("epg".equals(action) && url.isEmpty()) && !isHttpUrl(url)) {
            return result(NanoHTTPD.Response.Status.BAD_REQUEST, false, "请输入完整的 http:// 或 https:// 地址");
        }
        DataReceiver receiver = remoteServer.getDataReceiver();
        if (receiver == null) {
            return result(NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE, false, "电视端尚未就绪，请稍后重试");
        }
        try {
            if ("live".equals(action)) receiver.onLiveReceived(url);
            else receiver.onEpgReceived(url);
            return result(NanoHTTPD.Response.Status.OK, true, "已保存到电视，请在电视上查看加载结果");
        } catch (RuntimeException e) {
            return result(NanoHTTPD.Response.Status.INTERNAL_ERROR, false, "保存失败，请稍后重试");
        }
    }

    private boolean isHttpUrl(String url) {
        if (url.length() > 8192) return false;
        try {
            URI uri = new URI(url.replace("{name}", "channel").replace("{date}", "20261018"));
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private NanoHTTPD.Response result(NanoHTTPD.Response.Status status, boolean ok, String message) {
        String json = "{\"ok\":" + ok + ",\"message\":" + JSONObject.quote(message) + "}";
        return RemoteServer.createJSONResponse(status, json);
    }
}
