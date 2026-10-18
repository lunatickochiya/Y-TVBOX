package com.github.tvbox.osc.server;

import fi.iki.elonen.NanoHTTPD;
import java.util.HashMap;
import java.util.Map;

public class InputRequestProcessTest {
    private static String received;
    public static void main(String[] args) {
        RemoteServer server = new RemoteServer();
        server.receiver = new DataReceiver() {
            public void onLiveReceived(String url) { received = "live:" + url; }
            public void onEpgReceived(String url) { received = "epg:" + url; }
        };
        InputRequestProcess process = new InputRequestProcess(server);
        expect(process, params("live", null), 400);
        expect(process, params("search", "hello"), 400);
        expect(process, params(null, "http://example.com/list.m3u"), 400);
        expect(process, params("live", " "), 400);
        expect(process, params("live", "javascript:alert(1)"), 400);
        expect(process, params("live", "http://"), 400);
        expect(process, params("live", "http://bad host/list"), 400);
        expect(process, params("live", " https://example.com/直播.m3u?token=a&b=2 "), 200);
        if (!"live:https://example.com/直播.m3u?token=a&b=2".equals(received))
            throw new AssertionError("Valid URL was not normalized and dispatched");
        expect(process, params("epg", ""), 200);
        if (!"epg:".equals(received)) throw new AssertionError("Cannot disable EPG");
        expect(process, params("epg", "http://example.com/epg?ch={name}&date={date}"), 200);
        server.receiver = null;
        expect(process, params("live", "http://example.com/list.m3u"), 503);
        System.out.println("PASS: live/EPG request validation, dispatch and unavailable receiver");
    }
    private static Map<String, String> params(String action, String url) {
        Map<String, String> map = new HashMap<>();
        if (action != null) map.put("do", action);
        if (url != null) map.put("url", url);
        return map;
    }
    private static void expect(InputRequestProcess process, Map<String, String> params, int status) {
        NanoHTTPD.Response response = process.doResponse(null, "/action", params, null);
        if (response.getStatus().getRequestStatus() != status)
            throw new AssertionError(params + ": expected HTTP " + status + ", got " + response.getStatus());
    }
}
