#!/usr/bin/env python3
"""Run the production HMC resolver against official APKs; requires a JDK, not apktool.
Checks static typed references in DEX, not ART Hook execution or device UI behavior.
"""
import argparse
import hashlib
import pathlib
import subprocess
import tempfile


def verify(apks):
    root = pathlib.Path(__file__).resolve().parents[1]
    source = root / "app/src/main/java/com/aritxonly/myhypermodifier/runtime/HyperMusicCoverDexSymbols.java"
    checker = root / "tools/tests/HyperMusicCoverDexCompatibilityCheck.java"
    apk_source = root / "app/src/main/java/com/aritxonly/myhypermodifier/runtime/HyperMusicCoverApkSource.java"
    with tempfile.TemporaryDirectory(prefix="hmc-api-") as temp:
        subprocess.run(["javac", "-d", temp, str(source), str(apk_source), str(checker)], check=True)
        subprocess.run(["java", "-cp", temp,
                        "com.aritxonly.myhypermodifier.HyperMusicCoverDexCompatibilityCheck",
                        *[str(apk.resolve()) for apk in apks]], check=True)
    for apk in apks:
        print(apk.name + " SHA256 " + hashlib.sha256(apk.read_bytes()).hexdigest())


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=pathlib.Path, nargs="+")
    verify(parser.parse_args().apk)
