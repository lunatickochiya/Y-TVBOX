package xyz.doikki.videoplayer.exo;

/** Per-attempt readiness and rendered-output notifications. */
final class PlaybackOutputState {
    interface Listener {
        void onPrepared();
        void onRenderingStart();
    }
    private final Listener listener;
    private boolean active;
    private boolean preparing;
    private boolean firstFrameRendered;
    private boolean renderingStarted;
    PlaybackOutputState(Listener listener) { this.listener = listener; }
    void prepare() {
        reset();
        active = true;
        preparing = true;
    }
    void reset() {
        active = false;
        preparing = false;
        firstFrameRendered = false;
        renderingStarted = false;
    }
    boolean isPreparing() { return preparing; }
    boolean isActive() { return active; }
    boolean isFirstFrameRendered() { return firstFrameRendered; }
    boolean shouldCheckVideoOutput(boolean ready, boolean playWhenReady, boolean hasVideo) {
        return active && !preparing && ready && playWhenReady && hasVideo && !firstFrameRendered;
    }
    void ready(boolean hasVideo) {
        if (!active || !preparing) return;
        preparing = false;
        listener.onPrepared();
        if (firstFrameRendered || !hasVideo) notifyRenderingStart();
    }
    void firstFrame() {
        if (!active) return;
        firstFrameRendered = true;
        if (!preparing) notifyRenderingStart();
    }
    private void notifyRenderingStart() {
        if (!active || renderingStarted) return;
        renderingStarted = true;
        listener.onRenderingStart();
    }
}
