#!/usr/bin/env python3
"""Check the exact original Git tree, including a source snapshot without .git.

The snapshot manifest is not trusted by itself: its reconstructed Git tree
must match the tree pinned in this public integration repository.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path, PurePosixPath

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = "AmpySourceSnapshot.json"


def object_sha(kind, data):
    return hashlib.sha1(kind.encode() + b" " + str(len(data)).encode() + b"\0" + data).hexdigest()


def snapshot_tree(source, entries):
    tree, seen = {}, set()
    for entry in entries:
        name, mode = entry["path"], entry["mode"]
        relative = PurePosixPath(name)
        if (relative.is_absolute() or ".." in relative.parts or "\\" in name or
                name in seen or name == MANIFEST or mode not in {"100644", "100755"}):
            raise ValueError("Invalid source snapshot path/mode")
        seen.add(name)
        path = source / name
        if path.is_symlink() or not path.is_file():
            raise ValueError("Source snapshot file missing or symlinked: " + name)
        actual = object_sha("blob", path.read_bytes())
        if actual != entry["sha"]:
            raise ValueError("Source snapshot modified: " + name)
        directory = tree
        for part in relative.parts[:-1]:
            directory = directory.setdefault(part, {})
            if not isinstance(directory, dict):
                raise ValueError("Ambiguous snapshot tree")
        leaf = relative.parts[-1]
        if leaf in directory:
            raise ValueError("Duplicate snapshot tree node")
        directory[leaf] = (mode, actual)
    actual_paths = {str(p.relative_to(source).as_posix()) for p in source.rglob("*") if p.is_file()}
    if actual_paths != seen | {MANIFEST}:
        raise ValueError("Unexpected files in source snapshot; use a fresh snapshot or a clean Git checkout")

    def finish(node):
        body = bytearray()
        for name in sorted(node, key=lambda n: (n + ("/" if isinstance(node[n], dict) else "")).encode()):
            item = node[name]
            mode, sha = ("40000", finish(item)) if isinstance(item, dict) else item
            body.extend(mode.encode() + b" " + name.encode() + b"\0" + bytes.fromhex(sha))
        return object_sha("tree", bytes(body))
    return finish(tree)


def inspect(source, config):
    source = source.resolve()
    if (source / ".git").exists():
        def git(*args):
            return subprocess.run(["git", "-C", str(source), *args], check=True,
                capture_output=True, text=True).stdout.strip()
        if git("rev-parse", "HEAD") != config["source_sha"] or git("rev-parse", "HEAD^{tree}") != config["source_tree_sha"]:
            raise ValueError("Private app checkout is not the pinned source commit/tree")
        if git("status", "--porcelain"):
            raise ValueError("Refusing app source with local changes")
        return "git"
    marker = source / MANIFEST
    if not marker.is_file() or marker.stat().st_size > 1024 * 1024:
        raise ValueError("A pinned Git checkout or integrity-bound source snapshot is required")
    manifest = json.loads(marker.read_text())
    if manifest.get("repository") != config["repository"] or manifest.get("commit") != config["source_sha"]:
        raise ValueError("Wrong source snapshot provenance")
    if snapshot_tree(source, manifest["files"]) != config["source_tree_sha"]:
        raise ValueError("Snapshot tree differs from the pinned original repository")
    return "snapshot"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("source", type=Path)
    args = p.parse_args()
    try:
        config = json.loads((ROOT / "config/ampysandbox-upstream.json").read_text())
        print("AmpybiOS source verified (" + inspect(args.source, config) + "): " + config["source_sha"])
    except (OSError, ValueError, KeyError, subprocess.SubprocessError) as exc:
        p.exit(2, f"Source verification failed: {exc}\n")


if __name__ == "__main__":
    main()
