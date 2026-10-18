package xyz.doikki.videoplayer.exo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class PlaybackOutputStateTest {
    public static void main(String[] args) {
        videoReadyWaitsForFirstFrame();
        frameBeforeReadyStillReportsPreparedFirst();
        newAttemptClearsFrameAndReleaseRejectsLateFrame();
        audioOnlyReadyStartsPlayback();
        watchdogOnlyChecksReadyPlayingVideo();
        System.out.println("PlaybackOutputStateTest passed");
    }

    // Treating STATE_READY as first-frame success would announce video before it exists.
    private static void videoReadyWaitsForFirstFrame() {
        List<String> events = new ArrayList<>();
        PlaybackOutputState state = state(events);
        state.prepare();
        state.ready(true);
        check(events.equals(Arrays.asList("prepared")), "video readiness must not announce rendering");
        check(!state.isFirstFrameRendered(), "ready must not count as a rendered frame");
        state.firstFrame();
        state.firstFrame();
        check(events.equals(Arrays.asList("prepared", "rendering")), "first video frame must start rendering once");
    }

    private static void frameBeforeReadyStillReportsPreparedFirst() {
        List<String> events = new ArrayList<>();
        PlaybackOutputState state = state(events);
        state.prepare();
        state.firstFrame();
        check(events.isEmpty(), "early frame must wait for prepared notification");
        state.ready(true);
        check(events.equals(Arrays.asList("prepared", "rendering")), "prepared must precede rendering");
    }

    private static void newAttemptClearsFrameAndReleaseRejectsLateFrame() {
        List<String> events = new ArrayList<>();
        PlaybackOutputState state = state(events);
        state.prepare();
        state.ready(true);
        state.firstFrame();
        state.prepare();
        check(!state.isFirstFrameRendered(), "new attempt must not inherit previous first frame");
        state.reset();
        state.firstFrame();
        state.ready(true);
        check(!state.isFirstFrameRendered(), "released/reset attempt must reject late frames");
        check(events.equals(Arrays.asList("prepared", "rendering")), "reset attempt must reject late events");
    }

    private static void audioOnlyReadyStartsPlayback() {
        List<String> events = new ArrayList<>();
        PlaybackOutputState state = state(events);
        state.prepare();
        state.ready(false);
        check(events.equals(Arrays.asList("prepared", "rendering")), "audio-only streams must not wait for video");
        check(!state.isFirstFrameRendered(), "audio readiness must not invent a video frame");
    }

    // View-level resume can announce PLAYING while Exo is still buffering.
    private static void watchdogOnlyChecksReadyPlayingVideo() {
        PlaybackOutputState state = state(new ArrayList<>());
        state.prepare();
        state.ready(true);
        check(!state.shouldCheckVideoOutput(false, true, true), "buffering must not arm or fire a black-screen fallback");
        check(!state.shouldCheckVideoOutput(true, false, true), "paused player must not arm or fire fallback");
        check(state.shouldCheckVideoOutput(true, true, true), "ready playing video without a frame needs a check");
        check(!state.shouldCheckVideoOutput(true, true, false), "audio-only playback needs no video check");
        state.firstFrame();
        check(!state.shouldCheckVideoOutput(true, true, true), "rendered video needs no first-frame check");
        state.reset();
        check(!state.shouldCheckVideoOutput(true, true, true), "reset playback needs no check");
    }

    private static PlaybackOutputState state(final List<String> events) {
        return new PlaybackOutputState(new PlaybackOutputState.Listener() {
            @Override public void onPrepared() { events.add("prepared"); }
            @Override public void onRenderingStart() { events.add("rendering"); }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
