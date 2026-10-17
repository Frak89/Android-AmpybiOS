#!/usr/bin/env python3
"""Safely check, apply or revert an AmpyKernel source patch.

Targets the GrapheneOS/kernel_common-6.1 git checkout INSIDE
kernel_pixel_6.1/common or a standalone checkout. Does NOT download,
compile, flash, alter thermal limits or change Android signing keys.
"""
from __future__ import annotations

import argparse
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "config" / "ampykernel-patchset.json"

class GuardError(Exception):
    pass

def git(cwd: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess:
    try:
        p = subprocess.run(["git", "-C", str(cwd), *args], capture_output=True,
                           text=True, check=False, timeout=60)
    except (OSError, subprocess.TimeoutExpired) as e:
        raise GuardError(f"Git command unavailable or timed out: {e}") from e
    if check and p.returncode:
        raise GuardError(f"Git failed ({p.returncode}): {p.stderr.strip()}")
    return p

def locate_common(checkout: Path) -> Path:
    checkout = checkout.expanduser().resolve()
    for directory in (checkout / "common", checkout):
        if (directory / "kernel/sched/cpufreq_schedutil.c").is_file() and (
                directory / "drivers/cpufreq/Kconfig").is_file():
            top = git(directory, "rev-parse", "--show-toplevel").stdout.strip()
            if Path(top).resolve() != directory.resolve():
                raise GuardError("Expected exact kernel source git root, not parent")
            return directory
    raise GuardError("Expected a full kernel_common-6.1 source checkout")

def check_provenance(common: Path, cfg: dict, *, reverse: bool) -> Path:
    head = git(common, "rev-parse", "HEAD").stdout.strip()
    if head != cfg["source_ref"]:
        raise GuardError(f"Kernel source revision mismatch: {head}; "
                         f"expected {cfg['source_ref']}. Rebase/review required.")
    change = cfg["patches"][0]
    path = ROOT / change["file"]
    if not path.is_file():
        raise GuardError(f"Patch file missing: {path}")
    for source in change["source_files"]:
        relative = source["path"]
        expected = source["blob_sha"]
        # For reversal, verify original HEAD blob, not edited worktree content.
        args = ("rev-parse", f"HEAD:{relative}") if reverse else ("hash-object", relative)
        actual = git(common, *args).stdout.strip()
        if actual != expected:
            raise GuardError(f"{relative} blob mismatch: {actual}; expected {expected}")
    return path

def run(checkout: Path, action: str = "check") -> str:
    if action not in {"check", "apply", "revert"}:
        raise GuardError("Unknown action")
    cfg = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if cfg["source_project"] != "GrapheneOS/kernel_common-6.1":
        raise GuardError("Unexpected source project")
    common = locate_common(checkout)
    reverse = action == "revert"
    patch = check_provenance(common, cfg, reverse=reverse)
    modified = git(common, "status", "--porcelain", "--untracked-files=no").stdout
    if action in {"check", "apply"} and modified:
        raise GuardError("Tracked kernel working tree must be clean")
    if reverse:
        allowed = {x["path"] for x in cfg["patches"][0]["source_files"]}
        changed = set(git(common, "diff", "--name-only").stdout.splitlines())
        if changed != allowed:
            raise GuardError("Refuse reversal: modified paths differ from AmpyKernel patch")
    patch_opts = ["apply", "--whitespace=error"]
    if reverse:
        patch_opts.append("--reverse")
    check_result = git(common, *patch_opts, "--check", str(patch), check=False)
    if check_result.returncode:
        raise GuardError(f"Patch does not apply cleanly: {check_result.stderr.strip()}")
    if action == "check":
        return "OK: patch applicable to clean, exact-source GrapheneOS kernel"
    git(common, *patch_opts, str(patch))
    return ("AmpyKernel patch reverted (tracked source edited)"
            if reverse else
            "AmpyKernel patch applied. Kconfig flag remains disabled by default.")

def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("checkout", type=Path, help="kernel_pixel_6.1 or kernel_common-6.1 checkout")
    mode = p.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="Verify compatibility only (default)")
    mode.add_argument("--apply", action="store_true", help="Apply reviewed patch to matching sources")
    mode.add_argument("--revert", action="store_true", help="Revert exactly this patch")
    args = p.parse_args()
    try:
        print(run(args.checkout, "apply" if args.apply else "revert" if args.revert else "check"))
    except (GuardError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        p.exit(2, f"AmpyKernel refused: {error}\n")

if __name__ == "__main__":
    main()
