package com.github.tvbox.osc.util;

import java.util.ArrayList;
import java.util.List;

public final class PlaybackRecoveryTest {
    public static void main(String[] args) {
        pausedPreparingResumesTimeout();
        pausedBufferingResumesTimeoutDespiteViewPlaying();
        pausedErrorResumesRecovery();
        terminalFailureOverridesStaleReadiness();
        readyAndPausedPlaybackDoNotRecover();
        System.out.println("PlaybackRecoveryTest passed");
    }

    private static void pausedPreparingResumesTimeout() {
        Fixture f = new Fixture();
        f.schedule(true, false, false, 2);
        f.schedule(false, false, false, 2);
        check(f.scheduler.pending.isEmpty(), "pause must cancel initial connection timeout");
        f.schedule(true, false, false, 2);
        check(f.scheduler.delay == 10000, "resuming initial preparation must re-arm connection timeout");
        f.scheduler.runNext();
        check(f.changed == 1, "stalled preparation must still recover after pause/resume");
    }

    private static void pausedBufferingResumesTimeoutDespiteViewPlaying() {
        Fixture f = new Fixture();
        f.schedule(true, false, false, 0);
        f.schedule(false, false, false, 0);
        // A view-level PLAYING event does not make the underlying buffering player ready.
        f.schedule(true, false, false, 0);
        check(f.scheduler.delay == 30000, "buffering resume must restore configured replay timeout");
        f.scheduler.runNext();
        check(f.replayed == 1 && f.changed == 0, "timeout-off mode must replay stalled buffering");
    }

    private static void pausedErrorResumesRecovery() {
        Fixture f = new Fixture();
        f.schedule(false, false, true, 2);
        check(f.scheduler.pending.isEmpty(), "background error must not restart playback");
        f.schedule(true, false, true, 2);
        check(f.scheduler.delay == 0, "foreground error must recover without waiting for connection timeout");
        f.scheduler.runNext();
        check(f.changed == 1, "resuming terminal failure must restore recovery");
    }

    private static void readyAndPausedPlaybackDoNotRecover() {
        Fixture f = new Fixture();
        f.schedule(true, false, false, 2);
        f.schedule(true, true, false, 2);
        check(f.scheduler.pending.isEmpty(), "ready playback must cancel connection recovery");
        f.schedule(false, true, false, 2);
        check(f.scheduler.pending.isEmpty(), "paused ready playback must remain idle");
    }

    private static void terminalFailureOverridesStaleReadiness() {
        Fixture f = new Fixture();
        f.schedule(false, true, true, 2);
        f.schedule(true, true, true, 2);
        check(f.scheduler.pending.size() == 1, "resumed terminal failure must recover despite stale readiness");
        f.scheduler.runNext();
        check(f.changed == 1, "completed/error event must take precedence over ready snapshot");
    }

    private static final class Fixture {
        final FakeScheduler scheduler = new FakeScheduler();
        final PlaybackTaskQueue tasks = new PlaybackTaskQueue(scheduler);
        int changed;
        int replayed;
        final Runnable change = () -> changed++;
        final Runnable replay = () -> replayed++;
        void schedule(boolean allowed, boolean ready, boolean failed, int timeout) {
            PlaybackRecovery.schedule(tasks, change, replay, allowed, ready, failed, timeout);
        }
    }

    private static final class FakeScheduler implements PlaybackTaskQueue.Scheduler {
        final List<Runnable> pending = new ArrayList<>();
        long delay;
        @Override public void postDelayed(Runnable task, long delayMs) { pending.add(task); delay = delayMs; }
        @Override public void removeCallbacks(Runnable task) { pending.remove(task); }
        @Override public void clear() { pending.clear(); }
        void runNext() { pending.remove(0).run(); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
