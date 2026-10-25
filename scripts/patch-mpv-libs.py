#!/usr/bin/env python3
"""Patch the mpv-android AAR so its FFmpeg libs can coexist with nextlib.

The MPV AAR and the nextlib media3 extension both ship libavcodec.so,
libavutil.so, libswresample.so and libswscale.so.  Android packaging rejects
duplicate JNI files with the same name, so the MPV copy gets a private prefix
and every DT_NEEDED/SONAME reference is rewritten with patchelf.

The output is a new AAR containing only arm64-v8a and armeabi-v7a native
libraries (the only ABIs this app builds).

Usage: patch-mpv-libs.py <input.aar> <output.aar>
"""

import os
import shutil
import subprocess
import sys
import tempfile
import zipfile

RENAMES = {
    "libavcodec.so": "libmpv_avcodec.so",
    "libavdevice.so": "libmpv_avdevice.so",
    "libavfilter.so": "libmpv_avfilter.so",
    "libavformat.so": "libmpv_avformat.so",
    "libavutil.so": "libmpv_avutil.so",
    "libswresample.so": "libmpv_swresample.so",
    "libswscale.so": "libmpv_swscale.so",
}
ABIS = ("arm64-v8a", "armeabi-v7a")


def run(*args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL)


def patch_dir(path):
    for base in os.listdir(path):
        file = os.path.join(path, base)
        if not os.path.isfile(file) or not base.endswith(".so"):
            continue
        for old, new in RENAMES.items():
            run("patchelf", "--replace-needed", old, new, file)
        run("patchelf", "--set-soname", RENAMES.get(base, base), file)


def main():
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    source, output = sys.argv[1], sys.argv[2]
    if shutil.which("patchelf") is None:
        print("patchelf is required to build the MPV flavor", file=sys.stderr)
        return 2

    os.makedirs(os.path.dirname(os.path.abspath(output)), exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="mpv-libs-") as tmp:
        with zipfile.ZipFile(source) as archive:
            native = {}
            for abi in ABIS:
                prefix = f"jni/{abi}/"
                entries = [n for n in archive.namelist()
                           if n.startswith(prefix) and n.endswith(".so")]
                if not entries:
                    continue
                work = os.path.join(tmp, abi)
                os.makedirs(work)
                for name in entries:
                    base = os.path.basename(name)
                    with archive.open(name) as src, open(os.path.join(work, base), "wb") as dst:
                        shutil.copyfileobj(src, dst)
                patch_dir(work)
                native[abi] = work

            with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as result:
                for item in archive.infolist():
                    name = item.filename
                    if name.startswith("jni/"):
                        parts = name.split("/", 2)
                        if len(parts) < 3 or parts[1] not in ABIS or not name.endswith(".so"):
                            continue
                        base = parts[2]
                        target = RENAMES.get(base, base)
                        data = open(os.path.join(native[parts[1]], base), "rb").read()
                        result.writestr(f"jni/{parts[1]}/{target}", data)
                    else:
                        result.writestr(item, archive.read(name))
    print(f"patched MPV AAR: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
