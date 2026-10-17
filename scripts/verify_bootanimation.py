#!/usr/bin/env python3
"""Validate a generated Android bootanimation.zip without installing it."""
import argparse
from pathlib import Path
from build_bootanimation import validate_archive

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    try:
        w, h, fps, frames = validate_archive(args.archive)
    except (ValueError, OSError, UnicodeError) as error:
        parser.exit(1, f"Invalid animation ZIP: {error}\n")
    print(f"Valid Android animation: {w}x{h} @ {fps}fps, {frames} PNG frames")
