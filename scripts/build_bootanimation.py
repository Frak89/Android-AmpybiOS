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
import zlib

PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
MAX_FRAMES = 3000
MAX_FRAME_BYTES = 25 * 1024 * 1024


def inspect_png(frame: Path | bytes, width: int, height: int) -> None:
    """Validate PNG structure, CRCs, dimensions and presence of IEND.

    This is intentionally a bounded format check, not a full PNG pixel decoder.
    """
    data = frame.read_bytes() if isinstance(frame, Path) else frame
    if len(data) < 45 or data[:8] != PNG_MAGIC or len(data) > MAX_FRAME_BYTES:
        raise ValueError("Invalid PNG frame signature or file size")
    offset = 8
    found_ihdr = False
    found_idat = False
    found_end = False
    while offset < len(data):
        if offset + 12 > len(data):
            raise ValueError("Truncated PNG chunk")
        chunk_size = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        end = offset + 12 + chunk_size
        if chunk_size > MAX_FRAME_BYTES or end > len(data):
            raise ValueError("PNG chunk outside frame")
        payload = data[offset + 8:end - 4]
        checksum = struct.unpack(">I", data[end - 4:end])[0]
        if zlib.crc32(chunk_type + payload) & 0xffffffff != checksum:
            raise ValueError("PNG chunk CRC failed")
        if not found_ihdr:
            if chunk_type != b"IHDR" or chunk_size != 13:
                raise ValueError("PNG must begin with IHDR")
            w, h, bit_depth, color_type, compression, filtering, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
            if (w, h) != (width, height) or bit_depth not in {1, 2, 4, 8, 16}:
                raise ValueError("PNG dimensions or bit depth are incompatible")
            if color_type not in {0, 2, 3, 4, 6} or compression or filtering or interlace > 1:
                raise ValueError("Unsupported PNG header")
            found_ihdr = True
        elif chunk_type == b"IHDR":
            raise ValueError("Duplicate IHDR chunk")
        if chunk_type == b"IDAT":
            found_idat = True
        if chunk_type == b"IEND":
            if chunk_size != 0 or end != len(data):
                raise ValueError("Bad PNG IEND or trailing data")
            found_end = True
            break
        offset = end
    if not (found_ihdr and found_idat and found_end):
        raise ValueError("Incomplete PNG frame (missing IDAT or IEND)")


def validate_archive(archive: Path) -> tuple[int, int, int, int]:
    """Check an existing bootanimation.zip before including it in a ROM."""
    with zipfile.ZipFile(archive) as z:
        names = z.namelist()
        if len(names) < 2 or names[0] != "desc.txt" or len(names) > MAX_FRAMES + 1:
            raise ValueError("Unexpected bootanimation entries")
        desc = z.read("desc.txt").decode("ascii").splitlines()
        if len(desc) != 2 or desc[1] != "p 0 0 part0":
            raise ValueError("Unsupported animation descriptor")
        dimensions = desc[0].split()
        if len(dimensions) != 3 or not all(x.isascii() and x.isdecimal() for x in dimensions):
            raise ValueError("Malformed animation resolution or FPS")
        w, h, fps = [int(x) for x in dimensions]
        if not (1 <= w <= 8192 and 1 <= h <= 8192 and 1 <= fps <= 120):
            raise ValueError("Invalid animation dimensions or FPS")
        for i, name in enumerate(names[1:]):
            if name != f"part0/{i:05d}.png":
                raise ValueError("Unexpected frame order/name or unsafe ZIP path")
            entry = z.getinfo(name)
            if entry.file_size > MAX_FRAME_BYTES:
                raise ValueError("Frame exceeds byte limit")
            inspect_png(z.read(name), w, h)
        if z.testzip() is not None:
            raise ValueError("Corrupt ZIP contents")
        return w, h, fps, len(names) - 1

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
        inspect_png(frame, width, height)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as z:
        z.writestr("desc.txt", f"{width} {height} {fps}\np 0 0 part0\n")
        for i, frame in enumerate(ordered):
            z.write(frame, f"part0/{i:05d}.png")
    validate_archive(output)
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
