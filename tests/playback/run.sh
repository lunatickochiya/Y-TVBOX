#!/usr/bin/env bash
set -eu
task_root=$(cd "$(dirname "$0")/../.." && pwd)
test_out=$(mktemp -d /tmp/ytvbox-playback-tests.XXXXXX)
trap 'rm -rf "$test_out"' EXIT
json_jar=${JSON_JAR:-}
if [ -z "$json_jar" ]; then
    json_jar=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.json/json" -name '*.jar' -print -quit)
fi
if [ ! -f "$json_jar" ]; then
    echo 'Set JSON_JAR to a local org.json JVM jar.' >&2
    exit 1
fi
mapfile -t test_sources < <(find "$task_root/tests/playback" -name '*.java' -print)
"${JAVAC:-javac}" --release 8 -encoding UTF-8 -cp "$json_jar" -d "$test_out" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/util/HawkConfig.java" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/util/PlaybackTaskQueue.java" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/util/PlaybackRecovery.java" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/bean/LivePlayerManager.java" \
    "$task_root/app/src/main/java/xyz/doikki/videoplayer/exo/PlaybackOutputState.java" \
    "${test_sources[@]}"
test_status=0
"${JAVA:-java}" -cp "$test_out:$json_jar" com.github.tvbox.osc.util.PlaybackTaskQueueTest || test_status=$?
"${JAVA:-java}" -cp "$test_out:$json_jar" com.github.tvbox.osc.util.PlaybackRecoveryTest || test_status=$?
"${JAVA:-java}" -cp "$test_out:$json_jar" com.github.tvbox.osc.bean.LivePlayerManagerTest || test_status=$?
"${JAVA:-java}" -cp "$test_out:$json_jar" xyz.doikki.videoplayer.exo.PlaybackOutputStateTest || test_status=$?
exit "$test_status"
