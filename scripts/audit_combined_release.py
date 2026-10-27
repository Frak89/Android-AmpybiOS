#!/usr/bin/env python3
"""Host-audit outputs of the upstream cheetah signing pipeline, never flash.

An audit is not a physical boot test. The factory ZIP is authenticated against
the owner's supplied SSH public key; OTA metadata/payload hashes and signed
target-files inclusion of the exact approved AmpybiOS APK are inspected.
"""
from __future__ import annotations
import argparse
import base64
import hashlib
import json
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path, PurePosixPath

try:
    from .verify_ampysandbox_prebuilt import digest, normalize, ROOT
except ImportError:
    from verify_ampysandbox_prebuilt import digest, normalize, ROOT


def small(archive, name, limit=65536):
    info = archive.getinfo(name)
    if info.file_size > limit:
        raise ValueError(f"Unexpectedly large metadata: {name}")
    return archive.read(name)


def values(data):
    result = {}
    for line in data.decode("utf-8").splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            if key in result:
                raise ValueError("Duplicate package metadata field")
            result[key] = value
    return result


def archive_hash(archive, name):
    h, total, magic = hashlib.sha256(), 0, b""
    with archive.open(name) as source:
        for chunk in iter(lambda: source.read(4 * 1024 * 1024), b""):
            if not magic:
                magic = chunk[:4]
            h.update(chunk)
            total += len(chunk)
    return h.digest(), total, magic


def unique(archive):
    names = archive.namelist()
    if len(names) > 100000 or len(names) != len(set(names)):
        raise ValueError("Ambiguous archive contents")
    for name in names:
        if name.startswith("/") or ".." in PurePosixPath(name).parts:
            raise ValueError("Unsafe archive path")
    return names


def factory_images(archive):
    names = unique(archive)
    required = {"boot.img", "init_boot.img", "vendor_boot.img",
                "vendor_kernel_boot.img", "vbmeta.img", "dtbo.img"}
    basenames = {PurePosixPath(name).name for name in names}
    if not required.issubset(basenames):
        raise ValueError("Factory image set is incomplete for cheetah")
    metadata = [n for n in names if PurePosixPath(n).name == "android-info.txt"]
    if len(metadata) != 1:
        raise ValueError("Expected one factory android-info.txt")
    board = re.findall(r"^require\s+(?:board|product)=(\S+)\s*$",
                       small(archive, metadata[0]).decode("utf-8"), re.MULTILINE)
    if board != ["cheetah"]:
        raise ValueError("Factory is not exclusively for Pixel 7 Pro / cheetah")
    for name in names:
        if PurePosixPath(name).name in required and archive.getinfo(name).file_size == 0:
            raise ValueError("Empty boot image")


def inspect_factory(path):
    with zipfile.ZipFile(path) as outer:
        names = unique(outer)
        if not any(PurePosixPath(n).name == "flash-all.sh" for n in names):
            raise ValueError("Factory install script missing")
        nested = [n for n in names if re.fullmatch(
            r"(?:image-cheetah-|cheetah-img-)[0-9]+\.zip", PurePosixPath(n).name)]
        if not nested:
            factory_images(outer)
            return
        if len(nested) != 1 or outer.getinfo(nested[0]).file_size > 12 * 1024**3:
            raise ValueError("Unexpected nested image archive")
        # Seekable disk temporary storage, never load multi-GB images into RAM.
        with tempfile.TemporaryFile(dir=path.parent) as copy:
            with outer.open(nested[0]) as source:
                copied = 0
                for chunk in iter(lambda: source.read(4 * 1024 * 1024), b""):
                    copied += len(chunk)
                    if copied > 12 * 1024**3:
                        raise ValueError("Nested image archive exceeds limit")
                    copy.write(chunk)
            copy.seek(0)
            with zipfile.ZipFile(copy) as inner:
                factory_images(inner)


def authenticate_factory(factory, public_key):
    signature = Path(str(factory) + ".sig")
    if not signature.is_file():
        raise ValueError("Signed factory ZIP signature missing")
    key = public_key.read_text().strip().split()
    if len(key) < 2 or key[0] != "ssh-ed25519" or not re.fullmatch(r"[A-Za-z0-9+/=]+", key[1]):
        raise ValueError("Expected the owner's factory Ed25519 public key")
    with tempfile.TemporaryDirectory() as temp:
        allowed = Path(temp) / "allowed_signers"
        allowed.write_text("ampybios " + key[0] + " " + key[1] + "\n")
        with factory.open("rb") as source:
            result = subprocess.run(["ssh-keygen", "-Y", "verify", "-f", str(allowed),
                "-I", "ampybios", "-n", "factory images", "-s", str(signature)],
                stdin=source, capture_output=True, timeout=300, check=False)
        if result.returncode:
            raise ValueError("Factory SSH signature does not match the supplied owner key")


def audit(directory, build_number, public_key, sandbox_lock):
    if not re.fullmatch(r"[0-9]{8,20}", build_number):
        raise ValueError("Invalid build number")
    cfg = json.loads((ROOT / "config/ampysandbox-upstream.json").read_text())
    if sandbox_lock.stat().st_size > 65536:
        raise ValueError("Oversized app verification lock")
    lock = json.loads(sandbox_lock.read_text())
    if (lock.get("package_name") != cfg["package_name"] or
        lock.get("configured_source_sha") != cfg["source_sha"] or
        lock.get("signature_verified") is not True or lock.get("debuggable") is not False):
        raise ValueError("The approved AmpybiOS release APK lock is missing or mismatched")
    expected_apk = normalize(lock["apk_sha256"])
    factory = directory / f"cheetah-install-{build_number}.zip"
    ota = directory / f"cheetah-ota_update-{build_number}.zip"
    targets = directory / "cheetah-target_files.zip"
    for path in (factory, ota, targets, public_key):
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError(f"Release input missing: {path.name}")
    authenticate_factory(factory, public_key)
    inspect_factory(factory)
    with zipfile.ZipFile(targets) as archive:
        names = unique(archive)
        apps = [n for n in names if n.startswith("PRODUCT/app/AmpySandbox/") and n.endswith(".apk")]
        if len(apps) != 1 or archive.getinfo(apps[0]).file_size > 512 * 1024**2:
            raise ValueError("Approved sandbox APK missing from signed target-files")
        actual_apk, _, _ = archive_hash(archive, apps[0])
        if actual_apk.hex() != expected_apk:
            raise ValueError("Sandbox APK changed or was re-signed during the ROM build")
    with zipfile.ZipFile(ota) as archive:
        unique(archive)
        meta = values(small(archive, "META-INF/com/android/metadata"))
        if (meta.get("pre-device") != "cheetah" or meta.get("ota-type") != "AB" or
            meta.get("post-build-incremental") != build_number):
            raise ValueError("OTA device, A/B type or build number mismatch")
        properties = values(small(archive, "payload_properties.txt"))
        payload_hash, size, magic = archive_hash(archive, "payload.bin")
        if (magic != b"CrAU" or properties.get("FILE_SIZE") != str(size) or
            properties.get("FILE_HASH") != base64.b64encode(payload_hash).decode("ascii")):
            raise ValueError("OTA payload length, magic or SHA256 mismatch")
    return {"schema": 1, "device": "cheetah", "build_number": build_number,
            "host_checks_passed": True, "factory_signature_verified": True,
            "approved_sandbox_apk_in_signed_target_files": True,
            "sandbox_apk_sha256": expected_apk, "ota_payload_hash_verified": True,
            "hardware_boot_validated": False,
            "limitations": ["OTA hash/metadata checks do not replace recovery or AVB validation",
                            "No bootloader, modem, camera, radio or physical Pixel test was performed"],
            "artifacts": {p.name: {"sha256": digest(p), "bytes": p.stat().st_size}
                          for p in (factory, ota, targets)}}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--release-dir", type=Path, required=True)
    parser.add_argument("--build-number", required=True)
    parser.add_argument("--factory-public-key", type=Path, required=True)
    parser.add_argument("--sandbox-lock", type=Path, required=True)
    args = parser.parse_args()
    try:
        report = audit(args.release_dir, args.build_number, args.factory_public_key, args.sandbox_lock)
        output = args.release_dir / "AmpyRom-audit.json"
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(json.dumps(report, indent=2))
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, subprocess.SubprocessError) as exc:
        parser.exit(2, f"Release audit failed: {exc}\n")


if __name__ == "__main__":
    main()
