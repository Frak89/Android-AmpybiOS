#!/usr/bin/env python3
"""Generate original AmpybiOS organic morphing boot artwork.

SVG output is dependency-free; the default PNG output uses CairoSVG.
Artwork animates *after* firmware splash / verified-boot warnings.
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path
from xml.sax.saxutils import escape

PALETTE = {"background": "#060916", "violet": "#9A4CF6",
           "blue": "#316DFA", "cyan": "#18E1EE", "white": "#EFF9FF"}


def make_svg(index: int, count: int, width: int, height: int, title: str) -> str:
    if count <= 0 or not 0 <= index < count:
        raise ValueError("Invalid frame number")
    if not (128 <= width <= 4096 and 128 <= height <= 4096):
        raise ValueError("Canvas dimensions must be 128..4096")
    if not title or len(title) > 64:
        raise ValueError("Title length must be 1..64 characters")
    if any(ord(c) < 32 for c in title):
        raise ValueError("Control characters are not supported in title")

    t = 2 * math.pi * index / count
    cx, cy = width / 2, height / 2
    radius = min(width * 0.27, height * 0.20)
    points = []
    for n in range(128):
        a = n * 2 * math.pi / 128
        r = radius * (1 + .07 * math.sin(5*a + t)
                        + .035 * math.sin(9*a - 2*t)
                        + .018 * math.sin(13*a + 3*t))
        points.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    path = "M " + " L ".join(f"{x:.1f},{y:.1f}" for x,y in points) + " Z"
    shine_x = width * ((index / count) * 1.55 - .25)
    # Light strip moves horizontally across the text.
    safe_title = escape(title)
    text_size = min(width * .074, 82)
    ring = radius + min(width * .048, 48)
    opacity = .45 + .20 * math.sin(t + math.pi/3)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
<defs>
  <linearGradient id="body" x1="0" x2="1" y1="0" y2="1">
    <stop offset="0" stop-color="{PALETTE["blue"]}"/>
    <stop offset=".55" stop-color="{PALETTE["violet"]}"/>
    <stop offset="1" stop-color="{PALETTE["cyan"]}"/>
  </linearGradient>
  <linearGradient id="glint" gradientUnits="userSpaceOnUse"
      x1="{shine_x-140:.1f}" y1="0" x2="{shine_x+140:.1f}" y2="0">
    <stop offset="0" stop-color="#DDF7FF" stop-opacity=".82"/>
    <stop offset=".40" stop-color="#FFFFFF" stop-opacity="1"/>
    <stop offset="1" stop-color="#DDF7FF" stop-opacity=".80"/>
  </linearGradient>
  <mask id="titleMask"><rect width="{width}" height="{height}" fill="#000"/>
     <text x="{cx:.1f}" y="{cy+radius+height*.100:.1f}" text-anchor="middle"
         font-family="sans-serif" font-weight="bold" font-size="{text_size:.1f}"
         letter-spacing="6" fill="#fff">{safe_title}</text>
  </mask>
</defs>
<rect width="{width}" height="{height}" fill="{PALETTE["background"]}"/>
<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{ring:.1f}" stroke="{PALETTE["cyan"]}"
        opacity="{opacity:.2f}" stroke-width="3" fill="none"/>
<path d="{path}" fill="{PALETTE["background"]}" fill-opacity=".65"
      stroke="url(#body)" stroke-width="10"/>
<circle cx="{cx:.1f}" cy="{cy:.1f}" r="{radius*.15:.1f}"
        fill="{PALETTE["cyan"]}" opacity=".18"/>
<text x="{cx:.1f}" y="{cy+radius+height*.100:.1f}" text-anchor="middle"
      font-family="sans-serif" font-weight="bold" font-size="{text_size:.1f}"
      letter-spacing="6" fill="{PALETTE["white"]}">{safe_title}</text>
<g mask="url(#titleMask)">
  <rect x="{shine_x-45:.1f}" y="0" width="90" height="{height}"
        fill="url(#glint)" transform="skewX(-12)"/>
</g>
</svg>
'''


def generate(out: Path, count: int, width: int, height: int, title: str,
             svg_only: bool = False) -> list[Path]:
    if not 1 <= count <= 600:
        raise ValueError("Frame count must be 1..600")
    if not out.exists():
        out.mkdir(parents=True)
    elif any(out.iterdir()):
        raise ValueError("Output directory must be empty (avoids stale animation frames)")
    renderer = None
    if not svg_only:
        try:
            import cairosvg
            renderer = cairosvg
        except ImportError as error:
            raise RuntimeError("PNG output requires: python3 -m pip install cairosvg. "
                               "Use --svg-only for dependency-free artwork.") from error
    outputs = []
    for i in range(count):
        content = make_svg(i, count, width, height, title)
        path = out / f"{i}.{'svg' if svg_only else 'png'}"
        if renderer:
            renderer.svg2png(bytestring=content.encode("utf-8"),
                             write_to=str(path), output_width=width,
                             output_height=height)
        else:
            path.write_text(content, encoding="utf-8")
        outputs.append(path)
    return outputs


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--count", type=int, default=90)
    ap.add_argument("--width", type=int, default=1080)
    ap.add_argument("--height", type=int, default=2400)
    ap.add_argument("--text", default="AmpybiOS")
    ap.add_argument("--svg-only", action="store_true",
                    help="No CairoSVG required; output editable SVG frames")
    args = ap.parse_args()
    files = generate(args.out, args.count, args.width, args.height,
                     args.text, args.svg_only)
    print(f"Generated {len(files)} AmpybiOS frames in {args.out}")


if __name__ == "__main__":
    main()
