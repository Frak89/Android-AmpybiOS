#!/usr/bin/env python3
# generate_boot_frames.py
# Genera una sequenza di PNG numerati per boot animation AmpybiOS
# Requisiti: pip install cairosvg numpy

import argparse
import math
from pathlib import Path
import cairosvg


def make_blob_path(cx, cy, r, points, noise_amp, phase):
    coords = []
    for i in range(points):
        theta = 2 * math.pi * i / points
        perturb = 1.0 + noise_amp * (
            0.6 * math.sin(3 * theta + phase) +
            0.3 * math.sin(7 * theta - 0.5 * phase) +
            0.15 * math.sin(13 * theta + 1.5 * phase)
        )
        rr = r * perturb
        x = cx + rr * math.cos(theta)
        y = cy + rr * math.sin(theta)
        coords.append((x, y))
    d = f"M {coords[0][0]:.3f} {coords[0][1]:.3f}"
    for x, y in coords[1:]:
        d += f" L {x:.3f} {y:.3f}"
    d += " Z"
    return d


def build_svg(width, height, text, palette, blob_params, highlight_pos):
    bg, g1, g2, text_fill = palette
    cx = width / 2
    cy = height * 0.46
    r = min(width, height) * 0.24
    points = blob_params["points"]
    noise_amp = blob_params["noise_amp"]
    phase = blob_params["phase"]
    blob_d = make_blob_path(cx, cy, r, points, noise_amp, phase)

    svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
      <defs>
        <linearGradient id="g" x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stop-color="{g1}"/>
          <stop offset="1" stop-color="{g2}"/>
        </linearGradient>
        <filter id="soft" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="12" result="b"/>
          <feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
        </filter>
        <linearGradient id="textgrad" x1="0" x2="1">
          <stop offset="0" stop-color="#ffffff" stop-opacity="0.95"/>
          <stop offset="0.5" stop-color="#ffffff" stop-opacity="0.55"/>
          <stop offset="1" stop-color="#ffffff" stop-opacity="0.15"/>
        </linearGradient>
      </defs>

      <rect width="100%" height="100%" fill="{bg}"/>

      <g filter="url(#soft)">
        <path d="{blob_d}" fill="rgba(0,0,0,0.12)" transform="translate(0,18)"/>
      </g>

      <path d="{blob_d}" fill="url(#g)" stroke="rgba(255,255,255,0.06)" stroke-width="2" />

      <g opacity="0.12">
        <circle cx="{cx + r*0.35*math.cos(phase)}" cy="{cy + r*0.2*math.sin(phase)}" r="{r*0.8}" fill="#ffffff"/>
      </g>

      <g style="font-family:Inter, Roboto, sans-serif; font-weight:700;">
        <text x="{width/2}" y="{cy + r*0.12}" text-anchor="middle" font-size="{int(r*0.6)}" fill="{text_fill}">
          {text}
        </text>
      </g>

      <defs>
        <clipPath id="textclip">
          <text x="{width/2}" y="{cy + r*0.12}" text-anchor="middle" font-size="{int(r*0.6)}" font-weight="700">{text}</text>
        </clipPath>
      </defs>

      <g clip-path="url(#textclip)">
        <rect x="{highlight_pos - width*0.3}" y="{cy - r*0.5}" width="{width*0.6}" height="{r}" fill="url(#textgrad)" transform="rotate(-18 {width/2} {cy})" opacity="0.95"/>
      </g>
    </svg>'''
    return svg


def main():
    parser = argparse.ArgumentParser(description="Genera frames PNG per il boot animation AmpybiOS")
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--count", type=int, default=120)
    parser.add_argument("--width", type=int, default=1080)
    parser.add_argument("--height", type=int, default=2400)
    parser.add_argument("--text", type=str, default="AmpybiOS")
    args = parser.parse_args()

    out = args.out
    out.mkdir(parents=True, exist_ok=True)

    palette = ("#08101b", "#4b2cff", "#00e6ff", "#f6f7fb")

    for i in range(args.count):
        t = i / args.count
        blob_params = {
            "points": 180,
            "noise_amp": 0.08 + 0.03 * math.sin(2 * math.pi * (t * 2.4)),
            "phase": 2 * math.pi * (t * 2.0)
        }
        highlight_pos = args.width * (-0.5 + 2.0 * t)
        svg = build_svg(args.width, args.height, args.text, palette, blob_params, highlight_pos)
        png_path = out / f"{i}.png"
        cairosvg.svg2png(bytestring=svg.encode("utf-8"), write_to=str(png_path), output_width=args.width, output_height=args.height)
        if i % 10 == 0:
            print(f"Generated {i}/{args.count}")

    print(f"Generated {args.count} frames in {out}")

if __name__ == "__main__":
    main()
