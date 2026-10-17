#!/usr/bin/env python3
"""Build a deterministic, theme-only AmpyRec demo ZIP from trusted local files.

NO system image, APK, boot binary, arbitrary path or script is packaged.
"""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ALLOWED = ("fusion", "cyberpunk", "futuristic", "minimal")


def build(output: Path, themes: tuple[str, ...] = ("fusion",)) -> Path:
    if not themes or len(themes) != len(set(themes)):
        raise ValueError("Select one or more distinct AmpybiOS themes")
    entries = []
    content = []
    for name in themes:
        if name not in ALLOWED:
            raise ValueError(f"Unknown theme: {name}")
        filename = f"config/themes/{name}.json"
        data = (ROOT / filename).read_bytes()
        if not (2 <= len(data) <= 32 * 1024):
            raise ValueError(f"Invalid theme size: {filename}")
        obj = json.loads(data)
        if obj.get("schema") != 1 or obj.get("id") != name:
            raise ValueError(f"Invalid theme ID/schema: {filename}")
        content.append((filename, data))
        entries.append({"path": filename, "sha256": hashlib.sha256(data).hexdigest()})
    manifest = json.dumps(
        {"schema": 1, "type": "live-theme", "files": entries},
        indent=2, ensure_ascii=False,
    ).encode("utf-8") + b"\n"
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, mode="w") as z:
        for name, data in [("ampyrec.json", manifest), *content]:
            meta = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            meta.compress_type = zipfile.ZIP_DEFLATED
            meta.external_attr = 0o644 << 16
            z.writestr(meta, data, compresslevel=6)
    return output


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("AmpyRec-demo.zip"))
    parser.add_argument("--themes", nargs="+", choices=ALLOWED,
                        default=["fusion"])
    options = parser.parse_args()
    print(build(options.output, tuple(options.themes)))


if __name__ == "__main__":
    main()
