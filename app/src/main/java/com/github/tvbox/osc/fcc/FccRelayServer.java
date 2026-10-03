package com.github.tvbox.osc.fcc;

import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

/**
 * Tiny local HTTP relay bound to 127.0.0.1. The Android player (IJK/EXO/system)
 * reads the merged MPEG-TS byte stream from it, so no player changes are
 * needed to benefit from FCC.
 */
public final class FccRelayServer extends NanoHTTPD {

    public static final String RELAY_PATH = "/live.ts";

    private final ByteQueue queue;

    public FccRelayServer(ByteQueue queue) {
        super("127.0.0.1", 0);
        this.queue = queue;
    }

    @Override
    public Response serve(IHTTPSession session) {
        if (!RELAY_PATH.equals(session.getUri())) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found");
        }
        InputStream stream = queue.getInputStream();
        Response response = newChunkedResponse(Response.Status.OK, "video/mp2t", stream);
        response.addHeader("Cache-Control", "no-store");
        return response;
    }

    /** Unblocks any pending response thread and ends the stream. */
    public void closeQueue() {
        queue.close();
    }
}
