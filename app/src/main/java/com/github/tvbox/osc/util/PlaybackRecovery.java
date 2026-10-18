package com.github.tvbox.osc.util;

/** Recovery timing shared by playback events and activity resume. */
public final class PlaybackRecovery {
    private PlaybackRecovery() { }

    public static void schedule(PlaybackTaskQueue tasks, Runnable changeSource, Runnable replay,
                                boolean allowed, boolean ready, boolean failed, int timeoutSetting) {
        tasks.removeCallbacks(changeSource);
        tasks.removeCallbacks(replay);
        if (!allowed || (ready && !failed)) return;
        if (timeoutSetting == 0) tasks.postDelayed(replay, 30000L);
        else tasks.postDelayed(changeSource, failed ? 0 : timeoutSetting * 5000L);
    }
}
