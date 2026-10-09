#!/usr/bin/env python3
"""Collect Android power evidence using adb; no third-party Python packages needed."""
import argparse
import datetime
import pathlib
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("snapshot", "trace"))
    parser.add_argument("--serial", help="ADB device serial (required when multiple devices are connected)")
    parser.add_argument("--label", default="capture", help="Scenario and module state, e.g. idle-on-before")
    parser.add_argument("--seconds", type=int, default=60, help="Trace duration, 1–300 seconds")
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("power-captures"))
    args = parser.parse_args()
    if not 1 <= args.seconds <= 300:
        parser.error("--seconds must be between 1 and 300")
    if not args.label or any(c not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_" for c in args.label):
        parser.error("--label must contain only ASCII letters, digits, hyphens or underscores")
    adb = ["adb"] + (["-s", args.serial] if args.serial else [])

    def run(*command, **kwargs):
        return subprocess.run(adb + list(command), check=True, **kwargs)

    run("get-state", stdout=subprocess.DEVNULL)
    stamp = datetime.datetime.now().astimezone().strftime("%Y%m%d-%H%M%S-%f")
    folder = args.output / f"{stamp}-{args.label}"
    folder.mkdir(parents=True, exist_ok=False)
    (folder / "session.txt").write_text(
        f"time={datetime.datetime.now().astimezone().isoformat()}\n"
        f"label={args.label}\nmode={args.mode}\nserial={args.serial or 'default'}\n",
        encoding="utf-8",
    )

    def save(name, *command):
        result = subprocess.run(adb + list(command), capture_output=True)
        (folder / name).write_bytes(result.stdout + b"\n" + result.stderr)
        if result.returncode:
            print(f"Warning: {name} unavailable (exit {result.returncode})", file=sys.stderr)

    save("device.txt", "shell", "getprop")
    save("battery.txt", "shell", "dumpsys", "battery")
    save("batterystats.txt", "shell", "dumpsys", "batterystats")
    save("power.txt", "shell", "dumpsys", "power")
    save("cpuinfo.txt", "shell", "dumpsys", "cpuinfo")
    save("processes.txt", "shell", "ps", "-A")
    if args.mode == "trace":
        config = f"""buffers {{ size_kb: 65536 fill_policy: RING_BUFFER }}
duration_ms: {args.seconds * 1000}
data_sources {{ config {{ name: "linux.ftrace" ftrace_config {{
  ftrace_events: "sched/sched_switch"
  ftrace_events: "sched/sched_waking"
  ftrace_events: "power/cpu_frequency"
  ftrace_events: "power/cpu_idle"
  ftrace_events: "power/suspend_resume"
  ftrace_events: "power/wakeup_source_activate"
  ftrace_events: "power/wakeup_source_deactivate"
  atrace_categories: "gfx"
  atrace_categories: "view"
  atrace_categories: "wm"
  atrace_categories: "am"
  atrace_apps: "*"
}} }} }}
data_sources {{ config {{ name: "linux.process_stats" process_stats_config {{
  scan_all_processes_on_start: true
}} }} }}
data_sources {{ config {{ name: "android.power" android_power_config {{
  battery_poll_ms: 1000
  battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT
  battery_counters: BATTERY_COUNTER_CHARGE
  battery_counters: BATTERY_COUNTER_CURRENT
  battery_counters: BATTERY_COUNTER_VOLTAGE
  collect_power_rails: true
}} }} }}
data_sources {{ config {{ name: "android.surfaceflinger.frametimeline" }} }}
"""
        (folder / "config.pbtxt").write_text(config, encoding="utf-8")
        remote = f"/data/misc/perfetto-traces/mhm-{stamp}.perfetto-trace"
        print(f"Recording {args.seconds}s: reproduce the scenario now.", flush=True)
        run("shell", "perfetto", "--txt", "-c", "-", "-o", remote,
            input=config.encode(), timeout=args.seconds + 60)
        run("pull", remote, str(folder / "trace.perfetto-trace"))
        run("shell", "rm", remote)
        save("battery-after.txt", "shell", "dumpsys", "battery")
        save("batterystats-after.txt", "shell", "dumpsys", "batterystats")
    print(f"Saved: {folder.resolve()}")


if __name__ == "__main__":
    try:
        main()
    except (subprocess.SubprocessError, OSError) as error:
        sys.exit(f"Capture failed: {error}")
