#!/usr/bin/env python3
"""Open the saved live channel and detect playback process crashes on a real TV.

Usage: python3 scripts/check-live-adb.py --serial 192.168.17.226:5555
Requires an installed, configured Y-TVBOX and an already authorized ADB connection.
The TV must be on Y-TVBOX's home tab so that the live shortcut is visible.
"""

import argparse
import pathlib
import re
import subprocess
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--seconds", type=int, default=15)
    parser.add_argument("--log", type=pathlib.Path)
    args = parser.parse_args()
    if args.seconds < 5:
        parser.error("--seconds must be at least 5")
    package = "com.github.ytvbox.osc"
    adb = [args.adb, "-s", args.serial]

    def run(*command):
        return subprocess.check_output(
            adb + list(command), text=True, stderr=subprocess.STDOUT, timeout=40
        )

    # CLEAR_TOP recreates HomeActivity, whose onDestroy terminates this app.
    run("shell", "am", "start", "-n",
        package + "/com.github.tvbox.osc.ui.activity.HomeActivity")
    # Source/spider initialization can temporarily cover the visible shortcuts.
    time.sleep(12)
    deadline = time.monotonic() + 35
    bounds = None
    while time.monotonic() < deadline:
        remote_ui = "/data/local/tmp/ytvbox-live-check.xml"
        run("shell", "uiautomator", "dump", remote_ui)
        root = ET.fromstring(run("shell", "cat", remote_ui))
        for node in root.iter("node"):
            if node.get("resource-id") == package + ":id/tvLive":
                bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                break
        if bounds:
            break
        time.sleep(1)
    if not bounds or len(bounds) != 4:
        raise RuntimeError("Live shortcut unavailable; dismiss dialogs and select the home tab")

    # Some old TVs retain logcat buffers even after logcat -c. Only inspect this run.
    marker = "YTVBOX_LIVE_CHECK_" + uuid.uuid4().hex
    run("shell", "log", "-p", "i", "-t", "YLiveRegression", marker)
    run("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2),
        str((bounds[1] + bounds[3]) // 2))
    time.sleep(args.seconds)
    raw_log = run("logcat", "-d", "-v", "threadtime")
    if marker not in raw_log:
        raise RuntimeError("Log marker lost; playback result cannot be verified")
    log = raw_log.split(marker, 1)[1]
    log_path = args.log or pathlib.Path(tempfile.gettempdir()) / (marker + ".log")
    log_path.write_text(log)
    print("Log:", log_path)
    native_crash = "Fatal signal" in log and ">>> " + package + " <<<" in log
    java_crash = "FATAL EXCEPTION" in log and "Process: " + package in log
    if native_crash or java_crash:
        print("FAIL: live playback crashed Y-TVBOX")
        return 1
    activities = run("shell", "dumpsys", "activity", "activities")
    resumed = next((line.strip() for line in activities.splitlines()
                    if "mResumedActivity" in line), "")
    if "LivePlayActivity" not in resumed or "LivePlayActivity" not in log:
        print("FAIL: saved live channel did not remain open")
        return 1
    print("PASS: saved live channel stayed open without a process crash for",
          args.seconds, "seconds")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
