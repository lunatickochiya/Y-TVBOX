package com.github.tvbox.osc.util;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Playback UI tasks owned by one activity. Each callback has at most one pending run,
 * and closing the queue also rejects work posted by late network/player callbacks.
 */
public final class PlaybackTaskQueue {
    public interface Scheduler {
        void postDelayed(Runnable task, long delayMs);
        void removeCallbacks(Runnable task);
        void clear();
    }

    private final Scheduler scheduler;
    private final Map<Runnable, Runnable> pending = new IdentityHashMap<>();
    private boolean closed;

    public PlaybackTaskQueue(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void post(Runnable task) { postDelayed(task, 0); }

    public synchronized void postDelayed(final Runnable task, long delayMs) {
        if (closed) return;
        removeCallbacks(task);
        Runnable scheduled = new Runnable() {
            @Override public void run() {
                synchronized (PlaybackTaskQueue.this) {
                    if (closed || pending.get(task) != this) return;
                    pending.remove(task);
                }
                task.run();
            }
        };
        pending.put(task, scheduled);
        scheduler.postDelayed(scheduled, delayMs);
    }

    public synchronized void removeCallbacks(Runnable task) {
        Runnable scheduled = pending.remove(task);
        if (scheduled != null) scheduler.removeCallbacks(scheduled);
    }

    public synchronized void close() {
        closed = true;
        pending.clear();
        scheduler.clear();
    }
}
