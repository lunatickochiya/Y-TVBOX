package com.github.tvbox.osc.util;

import java.util.HashMap;
import java.util.Map;

/** Exercises the same commit gate used before config/cache/JarLoader mutations. */
public final class LatestRequestTest {
    public static void main(String[] args) {
        LatestRequest requests = new LatestRequest();
        Map<String, String> state = new HashMap<>();
        long slowApi = requests.begin();
        long newApi = requests.begin();
        check(requests.commit(newApi, () -> {
            state.put("source", "new");
            state.put("cache", "new");
            state.put("jar", "new");
        }), "latest response must be applied");
        check(!requests.commit(slowApi, () -> {
            state.put("source", "old");
            state.put("cache", "old");
            state.put("jar", "old");
        }), "slow old response must not mutate parsed config, cache, or loader");
        check("new".equals(state.get("source")) && "new".equals(state.get("cache"))
                && "new".equals(state.get("jar")), "latest API state must survive old response");

        long oldSort = requests.current();
        requests.invalidate();
        state.clear();
        check(!requests.commit(oldSort, () -> state.put("cache", "old-sort")),
                "invalidated config must reject late sort/cache publication");
        check(state.isEmpty(), "old sort must not refill cleared cache");
        check(!requests.isCurrent(newApi), "cancelled response must lose authority");

        LatestRequest jars = new LatestRequest();
        long config = requests.begin();
        long oldJar = jars.begin();
        long newJar = jars.begin();
        requests.commit(config, () -> jars.commit(newJar, () -> state.put("jar", "latest-jar")));
        requests.commit(config, () -> jars.commit(oldJar, () -> state.put("jar", "old-jar")));
        check("latest-jar".equals(state.get("jar")), "jar attempts within one config must also be latest-only");
        requests.invalidate();
        requests.commit(config, () -> jars.commit(newJar, () -> state.put("jar", "cancelled-config")));
        check("latest-jar".equals(state.get("jar")), "new config must invalidate even the latest old jar");
        System.out.println("LatestRequestTest: 8 checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
