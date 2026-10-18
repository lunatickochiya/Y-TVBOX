# Live configuration and backup regression tests

Run with a JDK 17 compiler/runtime and cached Gson, JSON and NanoHTTPD jars:

```bash
JAVAC=/home/urey/.cache/opencode-build/jdk17/bin/javac \
JAVA=/home/urey/.cache/opencode-build/jdk17/bin/java \
bash tests/live/run.sh
```

The runner compiles the production request handler, permission selector and backup
utility into a temporary directory. Android context/preferences and the callback
boundary use lightweight substitutes; URL parsing, NanoHTTPD responses, JSON and
backup directory operations are real.

The request cases cover missing parameters, unknown actions, malformed URLs,
Unicode/query-string preservation, EPG templates, disabling EPG, and unavailable
receivers. Backup cases cover permission changes, discovery of existing private
backups, restoring the exact file, rejection of paths outside the backup folders,
missing external storage, and permissions on Android 10 versus Android 11.

Before the fixes, the request handler crashed on a missing URL and granting access
hid private backups. Playback lifecycle regressions remain in `tests/playback`.
These JVM tests do not exercise Android system permission screens or native decoders.

To verify the actual HTML, CSS and JavaScript in Chromium, install Python
Playwright and its Chromium headless shell, then run `python3 tests/live/web_test.py`.
`PLAYWRIGHT_BROWSERS_PATH` can point to an existing browser installation. The test
serves the shipped page with controlled HTTP replies and checks successful saves,
Unicode and query-string encoding, EPG clearing, failures/timeouts, repeated submits,
slow config requests and responsive layouts. Screenshots go to
`artifacts/live-cleanup/`.
