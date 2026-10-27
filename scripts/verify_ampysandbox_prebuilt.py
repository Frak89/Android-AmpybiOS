#!/usr/bin/env python3
"""Verify the existing Python/C++ APK before any ROM integration mutation.

Requires real Android SDK apksigner and aapt on the build host. No signing,
installation, privilege grant, device command or source publication occurs.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(4 * 1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def normalize(value: str) -> str:
    value = re.sub(r"[:\s]", "", value).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Expected a complete SHA256 (64 hexadecimal digits)")
    return value


def tool(name: str, *args: str) -> str:
    executable = shutil.which(name)
    if not executable:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        candidate = Path(sdk)/"build-tools/37.0.0"/name if sdk else None
        if candidate and candidate.is_file() and os.access(candidate, os.X_OK):
            executable = str(candidate)
    if not executable:
        raise ValueError(f"Missing {name}: install Android SDK Build Tools and set ANDROID_HOME")
    result = subprocess.run([executable, *args], capture_output=True, text=True,
                            timeout=90, check=False)
    if result.returncode:
        raise ValueError(f"{name} verification failed: {result.stderr.strip()[:500]}")
    return result.stdout


def verify(apk: Path, expected_sha: str, expected_signer: str) -> dict:
    config = json.loads((ROOT / "config/ampysandbox-upstream.json").read_text())
    apk = apk.resolve()
    expected_sha, expected_signer = normalize(expected_sha), normalize(expected_signer)
    if not apk.is_file() or not 0 < apk.stat().st_size <= 512 * 1024 * 1024:
        raise ValueError("APK missing, empty, or exceeds 512 MiB")
    if digest(apk) != expected_sha:
        raise ValueError("AmpybiOS APK SHA256 mismatch")
    signature = tool("apksigner", "verify", "--verbose", "--print-certs",
                     "--min-sdk-version", str(config["sdk"]), str(apk))
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9:]+)\s*$",
                         signature, re.MULTILINE)
    if len(signers) != 1 or normalize(signers[0]) != expected_signer:
        raise ValueError("AmpybiOS APK signer mismatch or unsupported multiple signers")
    badging = tool("aapt", "dump", "badging", str(apk))
    package = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    target = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.MULTILINE)
    version = re.search(r"\bversionCode='(\d+)'", badging)
    if not package or package[1] != config["package_name"]:
        raise ValueError("Expected Android package io.ampy.bios")
    if not target or int(target[1]) != config["sdk"]:
        raise ValueError("APK target SDK does not match the Android 17 integration")
    if not version or int(version[1]) != config["version_code"]:
        raise ValueError("APK version does not match the pinned AmpybiOS source")
    if re.search(r"^application-debuggable\b", badging, re.MULTILINE):
        raise ValueError("Use a signed release APK, not a debuggable APK, for the ROM")
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        if len(names) > 50000 or len(set(names)) != len(names):
            raise ValueError("Invalid or ambiguous APK archive")
        if "AndroidManifest.xml" not in names or "classes.dex" not in names:
            raise ValueError("Incomplete APK")
        abi = "lib/" + config["required_abi"] + "/"
        if not any(name.startswith(abi) and name.endswith(".so") for name in names):
            raise ValueError("ARM64 native libraries missing: not a Pixel 7 Pro APK")
    return {"schema": 1, "package_name": package[1], "version_code": int(version[1]),
            "target_sdk": int(target[1]), "abi": config["required_abi"],
            "apk_sha256": expected_sha, "signer_sha256": expected_signer,
            "source_repository": config["repository"],
            "configured_source_sha": config["source_sha"],
            "signature_verified": True, "debuggable": False,
            "source_attestation": "Source pin is a build input, not a cryptographic APK source attestation"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--signer-sha256", required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.apk, args.sha256, args.signer_sha256), indent=2))
    except (ValueError, OSError, zipfile.BadZipFile, subprocess.TimeoutExpired) as exc:
        parser.exit(2, f"AmpySandbox rejected: {exc}\n")


if __name__ == "__main__":
    main()
