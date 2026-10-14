#!/usr/bin/env python3
"""Exercise APK-backed native image fingerprints and malformed ZIP rejection."""
import pathlib, subprocess, tempfile, zipfile
root = pathlib.Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory() as directory:
    work = pathlib.Path(directory)
    source = work / "test.cpp"
    source.write_text('#include "native_image_file.h"\nint main(int argc, char **argv) { return argc == 2 && matchingNativeImageFile(argv[1], 29382056, 0xa9c299ea503f9171ULL) ? 0 : 1; }\n')
    binary = work / "test"
    subprocess.run(["clang++", "-std=c++17", "-Wall", "-Wextra", "-Werror", "-fsanitize=address,undefined", "-I", str(root / "app/src/main/cpp"), str(source), "-o", str(binary)], check=True)
    apk = next((root / "reference").glob("*7722*.APK"))
    entry = "lib/arm64-v8a/libapp.so"
    data = zipfile.ZipFile(apk).read(entry)
    regular = work / "libapp.so"
    regular.write_bytes(data)
    def check(path, expected):
        result = subprocess.run([str(binary), str(path)])
        assert result.returncode == expected, path
    check(regular, 0)
    check(str(apk) + "!/" + entry, 0)
    check(str(apk) + "!/missing.so", 1)
    for label, content, compression in [("stored", data, zipfile.ZIP_STORED), ("changed", bytes([data[0] ^ 1]) + data[1:], zipfile.ZIP_STORED), ("compressed", data, zipfile.ZIP_DEFLATED)]:
        fixture = work / (label + ".apk")
        with zipfile.ZipFile(fixture, "w", compression=compression) as archive:
            archive.writestr(entry, content)
            archive.comment = b"PK\x05\x06 fake footer"
        check(str(fixture) + "!/" + entry, 0 if label == "stored" else 1)
    truncated = work / "truncated.apk"
    truncated.write_bytes(apk.read_bytes()[:-20])
    check(str(truncated) + "!/" + entry, 1)
    check(work / "missing.apk", 1)
print("Native file / actual launcher APK / stored ZIP / corrupt and compressed rejection: passed")
