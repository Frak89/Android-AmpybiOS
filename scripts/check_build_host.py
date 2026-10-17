#!/usr/bin/env python3
"""Non-invasive Linux prerequisites check for a Pixel 7 Pro GrapheneOS build.

This utility never downloads packages or modifies devices. With --json, a
build agent can inspect readiness before syncing massive Android sources.
"""
from __future__ import annotations
import argparse
import json
import os
import platform
import shutil
from pathlib import Path

GiB = 1024 ** 3


def inspect(source: Path, *, minimum_gib: int = 220,
            minimum_memory_gib: int = 32) -> dict:
    source = source.expanduser().resolve()
    ancestor = source
    while not ancestor.exists():
        ancestor = ancestor.parent
    usage = shutil.disk_usage(ancestor)
    memory_bytes = None
    try:
        for line in Path("/proc/meminfo").read_text().splitlines():
            if line.startswith("MemTotal:"):
                memory_bytes = int(line.split()[1]) * 1024
                break
    except (OSError, ValueError, IndexError):
        pass
    cpu_arch = platform.machine().lower()
    tools = ["git", "repo", "python3", "curl", "ssh-keygen", "yarn", "node",
             "rsync", "unzip", "zip", "git-lfs"]
    located = {name: shutil.which(name) is not None for name in tools}
    available_gib = round(usage.free / GiB, 2)
    total_mem = round(memory_bytes / GiB, 2) if memory_bytes is not None else None
    warnings = []
    if os.name != "posix" or platform.system() != "Linux":
        warnings.append("GrapheneOS build requires Linux")
    if cpu_arch not in {"x86_64", "amd64"}:
        warnings.append("GrapheneOS build requires x86_64")
    if usage.free < minimum_gib * GiB:
        warnings.append(f"Insufficient free storage: {available_gib} GiB "
                        f"(recommended >= {minimum_gib} GiB)")
    if memory_bytes is None or memory_bytes < minimum_memory_gib * GiB:
        warnings.append(f"Insufficient RAM: {total_mem} GiB "
                        f"(required >= {minimum_memory_gib} GiB)")
    if not all(located.values()):
        warnings.append("Missing executables: " + ", ".join(
            name for name, ok in located.items() if not ok))
    return {"source_dir": str(source), "mount_checked": str(ancestor),
            "architecture": cpu_arch, "free_storage_gib": available_gib,
            "memory_gib": total_mem, "tools": located, "warnings": warnings,
            "ready": len(warnings) == 0}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="Destination for GrapheneOS checkout")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()
    result = inspect(args.source)
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(f"Device target: cheetah / Pixel 7 Pro")
        print(f"Build destination: {result['source_dir']}")
        print(f"Disk available: {result['free_storage_gib']} GiB")
        print(f"RAM: {result['memory_gib']} GiB")
        print("Readiness: " + ("PASS" if result["ready"] else "FAIL"))
        for warning in result["warnings"]:
            print(f" - {warning}")
    if not result["ready"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
