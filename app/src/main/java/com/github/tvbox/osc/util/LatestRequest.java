package com.github.tvbox.osc.util;

/** Serializes generation checks with the mutations they authorize. */
public final class LatestRequest {
    private long generation;
    public synchronized long begin() { return ++generation; }
    public synchronized long current() { return generation; }
    public synchronized void invalidate() { generation++; }
    public synchronized boolean isCurrent(long request) { return request == generation; }
    public synchronized boolean commit(long request, Runnable mutation) {
        if (!isCurrent(request)) return false;
        mutation.run();
        return true;
    }
}
