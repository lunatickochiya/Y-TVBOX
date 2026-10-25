package com.github.catvod.utils;

/**
 * Compatibility holder for modern spiders. The Wex/csp spiders used by the
 * current source do not need host-side JS evaluation; if a spider does call
 * it, fail clearly instead of falling into a native proxy.
 */
public final class Js implements AutoCloseable {

    public String eval(String source) throws Exception {
        return eval(source, "{}");
    }

    public String eval(String source, String options) throws Exception {
        throw new UnsupportedOperationException("Host JS evaluation is not available");
    }

    @Override
    public void close() {
    }
}
