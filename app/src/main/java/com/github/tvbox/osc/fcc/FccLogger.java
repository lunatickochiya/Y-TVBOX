package com.github.tvbox.osc.fcc;

/** Minimal logging abstraction so the FCC core stays pure Java. */
public interface FccLogger {
    void d(String message);

    void w(String message);

    void e(String message);
}
