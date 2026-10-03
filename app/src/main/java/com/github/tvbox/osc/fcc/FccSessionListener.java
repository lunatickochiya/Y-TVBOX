package com.github.tvbox.osc.fcc;

/** Callbacks from {@link FccSession} to the app layer. */
public interface FccSessionListener {
    /** FCC unicast burst started (first media packet received). */
    void onFccActive();

    /** FCC failed or ended and the session fell back to plain multicast. */
    void onFccFallback(String reason);

    /** No usable stream at all; the caller should fall back to the original URL. */
    void onFatal(String reason);
}
