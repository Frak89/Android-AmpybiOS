#!/usr/bin/env python3
"""Opt-in, fail-closed GrapheneOS kernel-prebuilt mirror for AmpybiOS.

Run after 'repo init' and BEFORE 'repo sync'. Pinned to the EXACT same Git
commit declared by the verified GrapheneOS release manifest. Never uses
the mirror branch head or mixes kernel binaries from different releases.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
from urllib.parse import urlparse
from xml.etree import ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
CONFIG_PATH = ROOT / "config" / "kernel-mirror.json"
BUILD_PATH = ROOT / "config" / "build.json"
DESTINATION = "ampybios-kernel-mirror.xml"


def generate_manifest(checkout: Path, config: dict, build: dict) -> str:
    if config["baseline_grapheneos_tag"] != build["base_release_tag"]:
        raise ValueError("Kernel mirror baseline does not match pinned GrapheneOS build tag")
    if config["device"] != build["device"]["codename"]:
        raise ValueError("Kernel mirror device mismatch")
    if config["codename"] != build["device"]["kernel_codename"]:
        raise ValueError("Kernel mirror codename mismatch")

    upstream = checkout / ".repo" / "manifests" / "default.xml"
    if not upstream.is_file():
        raise ValueError(f"Run 'repo init' before configuring mirror; missing {upstream}")
    projects = ET.parse(upstream).getroot().findall("project")
    matched = [p for p in projects if p.get("name") == config["upstream_project"]
               and p.get("path") == config["project_path"]]
    if len(matched) != 1:
        raise ValueError("Expected exactly one matching Pantah kernel project")
    baseline = matched[0].get("revision")
    if baseline != config["baseline_revision"]:
        raise ValueError(
            f"Manifest kernel revision {baseline} differs from mirror baseline "
            f"{config['baseline_revision']}; do not mix releases"
        )
    if len(baseline) != 40 or any(c not in "0123456789abcdef" for c in baseline):
        raise ValueError("Manifest must pin a full lowercase Git commit SHA")
    url = urlparse(config["mirror_url"])
    if url.scheme != "https" or url.netloc != "github.com":
        raise ValueError("Mirror must use an HTTPS GitHub repository")
    repo_name = url.path.lstrip("/")
    if repo_name != "Frak89/device_google_pantah-kernels_6.1":
        raise ValueError("Unexpected kernel mirror repository name")

    manifest = ET.Element("manifest")
    ET.SubElement(manifest, "remote", {"name": "ampybios-kernel-mirror",
                                      "fetch": "https://github.com/Frak89/"})
    ET.SubElement(manifest, "remove-project", {"name": config["upstream_project"]})
    ET.SubElement(manifest, "project", {
        "name": "device_google_pantah-kernels_6.1",
        "path": config["project_path"],
        "remote": "ampybios-kernel-mirror",
        "revision": baseline,
        "clone-depth": "1"
    })
    ET.indent(manifest, space="  ")
    return '<?xml version="1.0" encoding="utf-8"?>\n' + ET.tostring(
        manifest, encoding="unicode"
    ) + "\n"


def configure(checkout: Path, *, dry_run: bool = False) -> str:
    checkout = checkout.resolve()
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    build = json.loads(BUILD_PATH.read_text(encoding="utf-8"))
    contents = generate_manifest(checkout, config, build)
    if dry_run:
        return contents
    target = checkout / ".repo" / "local_manifests" / DESTINATION
    if target.exists():
        if target.read_text(encoding="utf-8") != contents:
            raise ValueError(f"Refusing to overwrite modified local manifest {target}")
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(contents, encoding="utf-8")
    return str(target)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", type=Path,
                        help="GrapheneOS repo checkout after repo init, before repo sync")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    try:
        print(configure(args.checkout, dry_run=args.dry_run))
    except (ValueError, OSError, KeyError, ET.ParseError) as exc:
        parser.exit(1, f"Kernel mirror rejected: {exc}\n")
