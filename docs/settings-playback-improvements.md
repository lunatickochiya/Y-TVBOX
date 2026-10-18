# Settings and playback repair plan

Goal: apply settings once without restarting the process, remove repeated live
playback work and stale output state, then port applicable fixes to every product
branch. Use only USB device `S8UKYPOFOZJN59RC` for runtime verification.

Reference inspected: kukuqi666/TVboxOSC commit `30ac81a`, particularly
`SettingsController.kt`, `MainActivity.kt` and the real first-frame callback in
`CorePlayer.java`. Preserve this project's player choices and API 19 branch.

## Scope and implementation

- Settings: compare normalized persisted configuration snapshots. Apply ordinary
  UI/player/live/DNS preferences without a VOD restart; consume content/API/theme/
  language changes once before controlled Home recreation. Remove process exit
  from normal Home destruction and isolate startup continuations from UI timers.
- Playback: maintain one clock callback chain, cancel work on destruction, reject
  late playback callbacks, clear dimensions/first-frame state per attempt, retain
  the attached Surface during replay and keep temporary decoder fallback out of
  the user's saved channel configuration.
- Branches: `main`, `dev`, `live`, `y-tvbox-android-4.4`. Full Home/
  Settings fixes apply only to full TVBox branches; shared playback fixes apply to
  all. Preserve dev capability tuning and Android 4.4 dependency/API constraints.
- Exclude `live-slim` from all modifications, verification and commits as requested.

## Verification

- [ ] Reproduce Settings change/Back on the existing USB APK; record PID/activity
  transitions and failure evidence before installation of the fixed APK.
- [ ] Run standalone JVM regression programs for settings decisions and playback
  scheduling/state persistence, observing failures before fixes.
- [ ] Build signed `:app:assembleArmeabiGenericNormalRelease` with Java 17.
- [ ] USB test unchanged Settings, one content change, a second unchanged return,
  quick repeated Back, theme/language, live channel switching and rapid exit.
- [ ] Validate settings changes do not kill the process or repeatedly fetch VOD.
- [ ] Validate shared changes on each branch with JVM checks and normal APK build.
- [ ] Review patches independently, resolve findings and commit branch-specific
  ports without replacing product-specific configuration files wholesale.

## Limits

The available USB device reports Android API 23 and 32-bit ARM. It cannot establish
API 19 device behavior or modern arm64 device performance. Claims about reduced
overhead must be tied to removed duplicated scheduling or measured runtime data;
do not claim a decoding FPS improvement without a benchmark.
