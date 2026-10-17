import struct
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path

from scripts.build_bootanimation import package, inspect_png, validate_archive


def chunk(label, data):
    return struct.pack(">I", len(data)) + label + data + struct.pack(
        ">I", zlib.crc32(label + data) & 0xffffffff
    )


def tiny_png(width=1, height=1):
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    # Single transparent RGBA pixel per row; compact and standards compliant.
    pixels = b"".join(b"\x00" + b"\x00" * (width * 4)
                      for _ in range(height))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b""))


class BootAnimationTests(unittest.TestCase):
    def test_order_descriptor_and_archive_validation(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            for n in (2, 0, 1):
                (root / f"{n}.png").write_bytes(tiny_png())
            output = root / "animation.zip"
            self.assertEqual(package(root, output, 1, 1, 30), 3)
            self.assertEqual(validate_archive(output), (1, 1, 30, 3))
            with zipfile.ZipFile(output) as z:
                self.assertEqual(z.read("desc.txt"), b"1 1 30\np 0 0 part0\n")
                self.assertEqual(z.namelist()[1:],
                                 [f"part0/{i:05d}.png" for i in range(3)])

    def test_missing_frame_is_error(self):
        with tempfile.TemporaryDirectory() as d:
            root = Path(d)
            (root / "0.png").write_bytes(tiny_png())
            (root / "2.png").write_bytes(tiny_png())
            with self.assertRaises(ValueError):
                package(root, root / "out.zip", 1, 1, 30)

    def test_reject_corrupt_frame(self):
        corrupt = bytearray(tiny_png())
        corrupt[25] ^= 1
        with self.assertRaisesRegex(ValueError, "CRC"):
            inspect_png(bytes(corrupt), 1, 1)

    def test_reject_zip_with_path_traversal(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "animation.zip"
            with zipfile.ZipFile(path, "w") as z:
                z.writestr("desc.txt", "1 1 30\np 0 0 part0\n")
                z.writestr("../evil.png", tiny_png())
            with self.assertRaisesRegex(ValueError, "order/name"):
                validate_archive(path)


if __name__ == "__main__":
    unittest.main()
