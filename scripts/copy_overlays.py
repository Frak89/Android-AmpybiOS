#!/usr/bin/env python3
"""Copy overlays from this repository into a synced GrapheneOS tree.
Usage: python3 copy_overlays.py /path/to/grapheneos-tree
It will copy the directory overlays/cheetah/** into the target tree, merging and overwriting files.
"""
from pathlib import Path
import shutil
import sys
import os

def copy_tree(src: Path, dst: Path):
    if not src.exists():
        print(f"Source {src} does not exist", file=sys.stderr)
        sys.exit(2)
    for item in src.rglob('*'):
        rel = item.relative_to(src)
        target = dst.joinpath(rel)
        if item.is_dir():
            target.mkdir(parents=True, exist_ok=True)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(item, target)
            # preserve executable bit if set
            mode = item.stat().st_mode
            os.chmod(target, mode)


def main():
    if len(sys.argv) < 2:
        print("Usage: copy_overlays.py /absolute/path/to/grapheneos-tree", file=sys.stderr)
        sys.exit(2)
    dest = Path(sys.argv[1]).resolve()
    repo_root = Path(__file__).resolve().parent.parent
    overlays = repo_root / 'overlays' / 'cheetah'
    if not overlays.exists():
        print(f"Overlays directory {overlays} not found in repo", file=sys.stderr)
        sys.exit(2)
    print(f"Copying overlays from {overlays} to {dest}")
    copy_tree(overlays, dest)
    print("Overlays copied successfully")

if __name__ == '__main__':
    main()
