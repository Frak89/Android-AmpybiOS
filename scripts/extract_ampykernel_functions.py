#!/usr/bin/env python3
"""Extract exact function definitions from a LOCALLY CLONED GrapheneOS kernel.

Input may be GrapheneOS/kernel_pixel_6.1 (with common/), or only
GrapheneOS/kernel_common-6.1. No network calls, no binary rewriting and
no kernel patching. The output contains a manifest and optionally extracted
C function bodies for *inspection*, not independently compilable kernel code.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "config" / "ampykernel-functions.json"
MAX_C_BYTES = 8 * 1024 * 1024


def clean_c(code: str) -> str:
    """Replace comments/string contents by spaces, preserve source offsets."""
    result = list(code)
    mode = "normal"
    i = 0
    while i < len(code):
        char = code[i]
        next_char = code[i + 1] if i + 1 < len(code) else ""
        if mode == "normal":
            if char == "/" and next_char in ("*", "/"):
                mode = "block" if next_char == "*" else "line"
                result[i] = result[i + 1] = " "
                i += 2
                continue
            if char in ('"', "'"):
                mode = "string" if char == '"' else "char"
                result[i] = " "
        elif mode == "line":
            if char == "\n":
                mode = "normal"
            else:
                result[i] = " "
        elif mode == "block":
            if char == "*" and next_char == "/":
                result[i] = result[i + 1] = " "
                mode = "normal"
                i += 2
                continue
            if char != "\n":
                result[i] = " "
        else:
            if char == "\\" and next_char:
                result[i] = result[i + 1] = " "
                i += 2
                continue
            if char == ('"' if mode == "string" else "'"):
                mode = "normal"
            result[i] = " " if char != "\n" else char
        i += 1
    return "".join(result)


def find_function(code: str, symbol: str) -> dict | None:
    """Find a balanced, brace-delimited function definition; ignore calls/prototypes."""
    if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", symbol):
        raise ValueError("Invalid function name")
    masked = clean_c(code)
    for found in re.finditer(r"\b" + re.escape(symbol) + r"\s*\(", masked):
        parenthesis = masked.find("(", found.start())
        depth = 1
        k = parenthesis + 1
        while depth and k < len(masked):
            if masked[k] == "(":
                depth += 1
            elif masked[k] == ")":
                depth -= 1
            k += 1
        if depth:
            continue
        # Recognize only direct function definitions, never call sites or macros.
        while k < len(masked) and masked[k].isspace():
            k += 1
        if k >= len(masked) or masked[k] != "{":
            continue
        body_depth = 1
        j = k + 1
        while body_depth and j < len(masked):
            if masked[j] == "{":
                body_depth += 1
            elif masked[j] == "}":
                body_depth -= 1
            j += 1
        if body_depth:
            raise ValueError(f"Unbalanced C body for {symbol}")
        beginning = code.rfind("\n", 0, found.start()) + 1
        before = code[beginning:found.start()].strip()
        if not before:
            # Signature might put the return type on the preceding line.
            previous_end = max(0, beginning - 1)
            beginning = code.rfind("\n", 0, previous_end) + 1
        start_line = code.count("\n", 0, beginning) + 1
        end_line = code.count("\n", 0, j) + 1
        definition = code[beginning:j]
        return {"start_line": start_line, "end_line": end_line,
                "definition": definition,
                "definition_sha256": hashlib.sha256(definition.encode()).hexdigest()}
    return None


def find_path(checkout: Path, relative: str) -> Path | None:
    # Monorepo with submodule common/, or standalone common Linux tree.
    for prefix in (checkout / "common", checkout):
        candidate = prefix / relative
        if candidate.is_file() and candidate.stat().st_size <= MAX_C_BYTES:
            return candidate
    return None


def inspect(checkout: Path, *, include_bodies: bool = False) -> tuple[dict, dict]:
    cfg = json.loads(CATALOG.read_text(encoding="utf-8"))
    if cfg["device"] != "cheetah":
        raise ValueError("This tool is limited to Pixel 7 Pro")
    try:
        revision = subprocess.check_output(
            ["git", "-C", str(checkout), "rev-parse", "HEAD"],
            stderr=subprocess.DEVNULL, text=True, timeout=10
        ).strip()
    except (OSError, subprocess.SubprocessError):
        revision = None
    records = []
    snippets = {}
    cache = {}
    for item in cfg["functions"]:
        relative = item["file"]
        if relative not in cache:
            path = find_path(checkout, relative)
            if path:
                content = path.read_text(encoding="utf-8", errors="replace")
                cache[relative] = (path, content, hashlib.sha256(
                    path.read_bytes()).hexdigest())
            else:
                cache[relative] = None
        record = {k: v for k, v in item.items() if k != "confirmed_in"}
        source = cache[relative]
        if not source:
            record["status"] = "source_file_missing"
        else:
            path, code, digest = source
            record["source_path"] = str(path.relative_to(checkout))
            record["source_sha256"] = digest
            found = find_function(code, item["symbol"])
            if found:
                record["status"] = "found"
                record["start_line"] = found["start_line"]
                record["end_line"] = found["end_line"]
                record["definition_sha256"] = found["definition_sha256"]
                if include_bodies:
                    snippets[item["symbol"]] = (relative, found["definition"])
            else:
                record["status"] = "function_not_found"
        records.append(record)
    result = {"project": "AmpyKernel", "device": "cheetah",
              "checkout": str(checkout), "kernel_git_revision": revision,
              "reference": cfg["reference"], "functions": records}
    return result, snippets


def save_report(report: dict, snippets: dict, dest: Path, include_bodies: bool) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    (dest / "functions.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    found = sum(i["status"] == "found" for i in report["functions"])
    lines = ["# AmpyKernel — extracted kernel function map", "",
             f"Device: `{report['device']}`; source revision: "
             f"`{report['kernel_git_revision'] or 'unavailable'}`.",
             f"Resolved: **{found}/{len(report['functions'])}** functions.",
             "", "| Subsystem | Function | Source | Lines | Risk | Status |",
             "| --- | --- | --- | --- | --- | --- |"]
    for row in report["functions"]:
        span = (f"{row['start_line']}–{row['end_line']}"
                if row["status"] == "found" else "—")
        lines.append(f"| {row['category']} | `{row['symbol']}` | "
                     f"`{row.get('source_path', row['file'])}` | "
                     f"{span} | {row['risk']} | {row['status']} |")
    lines.extend(["", "Extracted bodies are **upstream reference snippets** only. "
                  "They are not standalone compilable patches.",
                  "Never change the Pixel thermal, power or Verified Boot "
                  "paths without full ROM tests."])
    (dest / "FUNCTIONS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    if include_bodies:
        snippets_folder = dest / "snippets"
        snippets_folder.mkdir(exist_ok=True)
        for name, (path, body) in snippets.items():
            header = ("/* SPDX-License-Identifier: GPL-2.0\n"
                      " * Source: GrapheneOS kernel / " + path + "\n"
                      " * Extracted for review only; NOT standalone compilable.\n"
                      " */\n")
            (snippets_folder / (name + ".c.txt")).write_text(
                header + body + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", type=Path,
                        help="Cloned kernel_pixel_6.1 or kernel_common-6.1")
    parser.add_argument("--out", type=Path,
                        help="Report directory (default: ./ampykernel-extract)")
    parser.add_argument("--include-bodies", action="store_true",
                        help="Also extract reference C bodies for inspection")
    parser.add_argument("--strict", action="store_true",
                        help="Exit nonzero if any requested source function is missing")
    args = parser.parse_args()
    checkout = args.checkout.expanduser().resolve()
    if not checkout.is_dir():
        parser.error(f"Missing checkout: {checkout}")
    target = (args.out or Path.cwd() / "ampykernel-extract").resolve()
    if target == checkout or checkout in target.parents:
        parser.error("Report must be outside the kernel source tree")
    report, snippets = inspect(checkout, include_bodies=args.include_bodies)
    save_report(report, snippets, target, args.include_bodies)
    found = sum(i["status"] == "found" for i in report["functions"])
    total = len(report["functions"])
    print(f"AmpyKernel: indexed {found}/{total} functions; report: {target}")
    if args.strict and found != total:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
