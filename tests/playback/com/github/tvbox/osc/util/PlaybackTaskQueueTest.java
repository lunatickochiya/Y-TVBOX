package com.github.tvbox.osc.util;

import java.util.ArrayList;
import java.util.List;

public final class PlaybackTaskQueueTest {
    public static void main(String[] args) {
        repeatedStartsKeepOneClock();
        cancelledCallbackCannotRunAfterReplacement();
        closeDiscardsPendingAndLateWork();
        System.out.println("PlaybackTaskQueueTest passed");
    }

    // Removing callback replacement would multiply updates after each channel switch.
    private static void repeatedStartsKeepOneClock() {
        FakeScheduler scheduler = new FakeScheduler();
        PlaybackTaskQueue queue = new PlaybackTaskQueue(scheduler);
        final int[] updates = {0};
        Runnable clock = new Runnable() {
            @Override public void run() {
                updates[0]++;
                queue.postDelayed(this, 1000);
            }
        };
        for (int i = 0; i < 20; i++) queue.post(clock);
        check(scheduler.pending.size() == 1, "channel switches must leave one clock callback");
        for (int i = 0; i < 5; i++) scheduler.runNext();
        check(updates[0] == 5, "clock must update once per tick");
        check(scheduler.pending.size() == 1, "clock must have one recurring chain");
    }

    // A callback already handed to the looper must lose authority when cancelled.
    private static void cancelledCallbackCannotRunAfterReplacement() {
        FakeScheduler scheduler = new FakeScheduler();
        PlaybackTaskQueue queue = new PlaybackTaskQueue(scheduler);
        final int[] calls = {0};
        Runnable task = () -> calls[0]++;
        queue.postDelayed(task, 6000);
        Runnable stale = scheduler.pending.get(0);
        queue.removeCallbacks(task);
        queue.postDelayed(task, 6000);
        stale.run();
        check(calls[0] == 0, "cancelled watchdog must not act on a new playback attempt");
        scheduler.runNext();
        check(calls[0] == 1, "replacement watchdog must still run");
    }

    // Dropping the closed guard would let late network callbacks restart a dead activity.
    private static void closeDiscardsPendingAndLateWork() {
        FakeScheduler scheduler = new FakeScheduler();
        PlaybackTaskQueue queue = new PlaybackTaskQueue(scheduler);
        final int[] calls = {0};
        Runnable task = () -> calls[0]++;
        queue.post(task);
        Runnable stale = scheduler.pending.get(0);
        queue.close();
        stale.run();
        queue.post(task);
        queue.postDelayed(task, 1000);
        queue.close();
        check(calls[0] == 0, "destroyed activity must reject dispatched callbacks");
        check(scheduler.pending.isEmpty(), "destroyed activity must reject late posts");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeScheduler implements PlaybackTaskQueue.Scheduler {
        final List<Runnable> pending = new ArrayList<>();
        @Override public void postDelayed(Runnable task, long delayMs) { pending.add(task); }
        @Override public void removeCallbacks(Runnable task) {
            while (pending.remove(task)) { }
        }
        @Override public void clear() { pending.clear(); }
        void runNext() { pending.remove(0).run(); }
    }
}
