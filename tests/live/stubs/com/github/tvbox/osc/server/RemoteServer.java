package com.github.tvbox.osc.server;
import fi.iki.elonen.NanoHTTPD;
public class RemoteServer {
    public DataReceiver receiver;
    public DataReceiver getDataReceiver() { return receiver; }
    public static NanoHTTPD.Response createPlainTextResponse(NanoHTTPD.Response.IStatus status, String text) {
        return NanoHTTPD.newFixedLengthResponse(status, NanoHTTPD.MIME_PLAINTEXT, text);
    }
    public static NanoHTTPD.Response createJSONResponse(NanoHTTPD.Response.IStatus status, String text) {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", text);
    }
}
