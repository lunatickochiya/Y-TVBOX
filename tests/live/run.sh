#!/usr/bin/env bash
set -eu
task_root=$(cd "$(dirname "$0")/../.." && pwd)
test_out=$(mktemp -d /tmp/ylive-config-tests.XXXXXX)
trap 'rm -rf "$test_out"' EXIT
cache_root=${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1
json_jar=$(find "$cache_root/org.json/json" -name '*.jar' -print -quit)
gson_jar=$(find "$cache_root/com.google.code.gson/gson" -name '*.jar' -print -quit)
http_jar=$(find "$cache_root/com.github.NanoHttpd.nanohttpd/nanohttpd" -name '*.jar' -print -quit)
test_cp="$json_jar:$gson_jar:$http_jar"
mapfile -t test_sources < <(find "$task_root/tests/live" -name '*.java' -print)
"${JAVAC:-javac}" --release 8 -encoding UTF-8 -cp "$test_cp" -d "$test_out" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/server/RequestProcess.java" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/server/DataReceiver.java" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/server/InputRequestProcess.java" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/util/DefaultConfig.java" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/util/FileUtils.java" \
    "$task_root/app/src/main/java/com/github/tvbox/osc/util/LiveBackupUtil.java" \
    "${test_sources[@]}"
test_status=0
"${JAVA:-java}" -cp "$test_out:$test_cp" com.github.tvbox.osc.server.InputRequestProcessTest || test_status=$?
"${JAVA:-java}" -cp "$test_out:$test_cp" com.github.tvbox.osc.util.LiveBackupUtilTest || test_status=$?
exit "$test_status"
