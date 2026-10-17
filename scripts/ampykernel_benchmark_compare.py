#!/usr/bin/env python3
"""Compare two *user-collected* read-only AmpyKernel benchmark snapshots.

The output is only a descriptive report. It does NOT establish causality or
claim battery/thermal improvements without matching workload and repeated runs.
"""
from __future__ import annotations
import argparse
import json
import re
from pathlib import Path

def parse(directory: Path) -> dict:
    gfx = (directory / "gfxinfo.txt").read_text(encoding="utf-8", errors="replace")
    battery = (directory / "battery.txt").read_text(encoding="utf-8", errors="replace")
    def number(pattern: str, source: str) -> int | None:
        m = re.search(pattern, source, re.IGNORECASE | re.MULTILINE)
        return int(m.group(1)) if m else None
    total = number(r"^\s*Total frames rendered:\s*(\d+)\s*$", gfx)
    jank = number(r"^\s*Janky frames:\s*(\d+)", gfx)
    pct = round(100 * jank / total, 2) if total and jank is not None else None
    temp = number(r"^\s*temperature:\s*(\d+)\s*$", battery)
    percent = number(r"^\s*level:\s*(\d+)\s*$", battery)
    context = (directory / "context.txt").read_text(
        encoding="utf-8", errors="replace") if (directory / "context.txt").exists() else ""
    return {"directory": str(directory), "total_frames": total,
            "janky_frames": jank, "janky_percent": pct,
            "battery_percent_snapshot": percent,
            "battery_temperature_c": round(temp / 10, 1) if temp is not None else None,
            "context": context[:2000]}

def compare(baseline: Path, candidate: Path) -> dict:
    base, new = parse(baseline), parse(candidate)
    if base["janky_percent"] is not None and new["janky_percent"] is not None:
        delta = round(new["janky_percent"] - base["janky_percent"], 2)
    else:
        delta = None
    return {"baseline": base, "candidate": new,
            "janky_percentage_points_delta": delta,
            "explanation": ("Negative delta suggests fewer janky frames in this "
                            "sample, but cannot prove a kernel improvement."),
            "limitations": [
                "gfxinfo metrics cover only the requested app/window, not system-wide FPS",
                "Jank counts depend on workload and sample window; use Perfetto FrameTimeline for rigorous comparisons",
                "Battery temperature is a single Android-provided reading, not a measured skin temperature",
                "Two snapshots do not demonstrate energy savings or thermal safety",
                "Only compare matching app version, brightness, workloads and battery conditions"
            ]}

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("baseline", type=Path)
    ap.add_argument("candidate", type=Path)
    ap.add_argument("--out", type=Path)
    args = ap.parse_args()
    try:
        result = compare(args.baseline, args.candidate)
    except OSError as exc:
        ap.exit(2, f"Benchmark input unavailable: {exc}\n")
    data = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.out:
        args.out.write_text(data, encoding="utf-8")
        print(args.out)
    else:
        print(data, end="")

if __name__ == "__main__":
    main()
