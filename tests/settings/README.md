# Settings regression checks

Run `bash tests/settings/run.sh` with a JDK, or set `JAVAC` and `JAVA` to explicit
compiler/runtime paths. These checks compile the production Java helpers with
Java 8 compatibility and need neither Gradle nor an Android device.

- `HomeSettingsTest` covers changed/unchanged settings, consuming one reload,
  API versus cached content reloads, live-only URL changes and clearing a URL,
  fallback source selection, paused startup and one-time default-live navigation.
- `LatestRequestTest` covers invalidating slow prior API/JAR responses and old
  sort-cache publications before they mutate current state.

The tests exercise decision and ownership boundaries. The Android lifecycle,
HTTP server, native codecs, locale context and parser integration require APK
builds and device verification as described in the repair plan.
