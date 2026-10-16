#!/usr/bin/env python3
"""Package numbered PNG boot animation frames into standard Android bootanimation.zip.

This intentionally never changes the firmware / bootloader splash.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import re
import struct
import zipfile

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
MAX_FRAMES = 3000
MAX_FRAME_BYTES = 25 * 1024 * 1024

def package(input_dir: Path, output: Path, width: int, height: int, fps: int) -> int:
    if not (1 <= width <= 8192 and 1 <= height <= 8192 and 1 <= fps <= 120):
        raise ValueError("Invalid width, height, or frame rate")
    frames = list(input_dir.glob("*.png"))
    if not frames:
        raise ValueError("No numbered PNG frames found")
    if len(frames) > MAX_FRAMES or any(not re.fullmatch(r"\d+", p.stem) for p in frames):
        raise ValueError("Frames must be numbered 0.png through N.png (<= 3000)")
    numbers = [int(p.stem) for p in frames]
    if len(set(numbers)) != len(numbers) or set(numbers) != set(range(len(frames))):
        raise ValueError("Frame numbers must start at zero and be contiguous")
    ordered = sorted(frames, key=lambda p: int(p.stem))
    if output.resolve() in {p.resolve() for p in ordered}:
        raise ValueError("Output cannot overwrite an input frame")
    output.parent.mkdir(parents=True, exist_ok=True)
    for frame in ordered:
        if frame.stat().st_size > MAX_FRAME_BYTES:
            raise ValueError(f"Frame too large: {frame.name}")
        with frame.open("rb") as f:
            header = f.read(24)
        if len(header) != 24 or not header.startswith(PNG_MAGIC):
            raise ValueError(f"Invalid PNG header: {frame.name}")
        w, h = struct.unpack(">II", header[16:24])
        if (w, h) != (width, height):
            raise ValueError(f"Frame dimensions {w}x{h} differ from {width}x{height}: {frame.name}")
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as z:
        z.writestr("desc.txt", f"{width} {height} {fps}\np 0 0 part0\n")
        for i, frame in enumerate(ordered):
            z.write(frame, f"part0/{i:05d}.png")
    return len(ordered)

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True, dest="input_dir")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--width", type=int, default=1080)
    parser.add_argument("--height", type=int, default=2400)
    parser.add_argument("--fps", type=int, default=30)
    args = parser.parse_args()
    count = package(args.input_dir, args.output, args.width, args.height, args.fps)
    print(f"Packaged {count} PNG frames to {args.output}")

if __name__ == "__main__":
    main()
