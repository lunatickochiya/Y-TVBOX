# Standalone playback regressions

Run with a JDK 17 compiler/runtime and a local JVM `org.json` jar:

```bash
JAVAC=/home/urey/.cache/opencode-build/jdk17/bin/javac \
JAVA=/home/urey/.cache/opencode-build/jdk17/bin/java \
bash tests/playback/run.sh
```

`JSON_JAR` can specify the jar explicitly; otherwise the runner locates the copy
already in the Gradle dependency cache. The runner does not invoke Gradle, access
a device or download dependencies. It compiles Java 8 bytecode into a temporary
directory and deletes that directory afterwards.

- `PlaybackTaskQueueTest` exercises the production callback queue with a
  deterministic scheduler: twenty clock starts leave one callback chain,
  cancelled watchdogs lose authority, and destruction rejects pending/late work.
- `PlaybackRecoveryTest` exercises connection recovery across pause/resume while
  preparing, buffering and failed. Resume uses actual player readiness even when
  the view announces PLAYING, restores the configured timeout/replay behavior and
  keeps ready or background playback from starting recovery.
- `LivePlayerManagerTest` runs the production manager against an in-memory Hawk
  storage boundary and small Android/player doubles. It verifies the stored
  decoder and scale after temporary fallback, channel reload and explicit decoder
  selection. JSONObject serialization is real.
- `PlaybackOutputStateTest` exercises the production Exo output state: READY
  announces preparation without inventing a video frame; real first-frame output
  announces rendering once; a fresh preparation resets output evidence; reset
  rejects late output events; audio-only playback does not wait for a video frame.
  Watchdog eligibility requires the actual Exo state to be READY and playing,
  including when the view reports PLAYING during buffering or after a pause.

The original behaviors failed the regressions with `channel switches must leave
one clock callback`, `scale change must preserve saved soft decoder`, and `video
readiness must not announce rendering` before the fixes were applied. These
tests do not load native decoders or an Android Surface; APK/device verification
covers those integration paths separately.
