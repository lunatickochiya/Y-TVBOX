#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
JAVAC="${JAVAC:-javac}"
JAVA="${JAVA:-java}"
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
"$JAVAC" --release 8 -d "$classes" \
  app/src/main/java/com/github/tvbox/osc/util/{HomeSettings,LatestRequest}.java \
  app/src/test/java/com/github/tvbox/osc/util/{HomeSettingsTest,LatestRequestTest}.java
"$JAVA" -cp "$classes" com.github.tvbox.osc.util.HomeSettingsTest
"$JAVA" -cp "$classes" com.github.tvbox.osc.util.LatestRequestTest
