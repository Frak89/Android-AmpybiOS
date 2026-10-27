#!/usr/bin/env python3
"""Validate AmpybiOS development profiles using Python's standard library."""
from pathlib import Path
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
COLOR = re.compile(r"^#[0-9A-Fa-f]{6}$")
REQUIRED_APPS = {"Proton Mail", "Proton Calendar", "Signal", "Bitwarden",
                 "Aegis Authenticator", "Proton VPN", "Organic Maps", "VLC",
                 "Proton Drive", "Accrescent"}

def validate(root: Path = ROOT) -> None:
    def load(path: str):
        with (root / path).open(encoding="utf-8") as f:
            return json.load(f)

    build = load("config/build.json")
    assert build["project"] == "AmpybiOS"
    assert build["device"]["codename"] == "cheetah"
    assert build["android_major"] == 17
    assert build["security"]["default_root"] is False
    sandbox = load("config/ampysandbox-upstream.json")
    assert sandbox["repository"] == "Frak89/AmpybiOS"
    assert sandbox["package_name"] == "io.ampy.bios" and sandbox["module"] == "AmpySandbox"
    assert re.fullmatch(r"[0-9a-f]{40}", sandbox["source_sha"])
    assert re.fullmatch(r"[0-9a-f]{40}", sandbox["source_tree_sha"])
    assert sandbox["sdk"] == 37 and sandbox["required_abi"] == "arm64-v8a"
    assert sandbox["enabled_by_default"] is False
    ota = load("config/ampyota-dev.json")
    assert ota["channel"] == "dev" and ota["device"] == "cheetah"
    assert ota["automatic_check"] and ota["automatic_download"] and ota["automatic_install"]
    assert ota["network"]["wifi_only"] and not ota["network"]["allow_metered_wifi"]
    assert ota["update_server_url"] is None or (ota["update_server_url"].startswith("https://")
        and "grapheneos.org" not in ota["update_server_url"])
    assert ota["verify_update_signature"] and ota["reboot"] == "ask_user"
    privacy = load("config/privacy-apps.json")
    assert privacy["preinstalled_google_apps"] == []
    assert not privacy["google_play_services_preinstalled"]
    assert {app["name"] for app in privacy["requested_preinstalls"]} == REQUIRED_APPS
    performance = load("config/performance-profiles.json")
    assert performance["device"] == "cheetah"
    assert [p["id"] for p in performance["modes"]] == [
        "eco", "balanced", "fusion-extreme"
    ]
    assert [p["max_preview_fps"] for p in performance["modes"]] == [30, 60, 120]
    assert [p["max_glow_percent"] for p in performance["modes"]] == [15, 40, 65]
    assert [p["max_radar_speed_dps"] for p in performance["modes"]] == [45, 180, 360]
    assert performance["security"]["root_required"] is False
    assert performance["security"]["kernel_governor_changes"] is False
    assert performance["defaults"]["profile"] == "fusion-extreme"
    assert performance["defaults"]["display_target_hz"] == 120
    assert performance["defaults"]["standby_saver"] is True
    assert performance["defaults"]["proactive_thermal_adaptation"] is True
    assert performance["display"]["request_is_advisory"] is True
    assert performance["standby_behavior"]["system_power_saver_modified"] is False
    themes = sorted((root / "config/themes").glob("*.json"))
    assert {p.stem for p in themes} == {"fusion", "cyberpunk", "minimal", "futuristic"}
    for path in themes:
        theme = json.loads(path.read_text(encoding="utf-8"))
        assert theme["id"] == path.stem and theme["schema"] == 1
        assert all(COLOR.fullmatch(c) for c in theme["colors"].values())
        assert len(theme["corner_radii_dp"]) == 4
        assert all(isinstance(r, (int, float)) and 0 <= r <= 200 for r in theme["corner_radii_dp"])
        assert 0 <= theme["effects"]["glow_alpha"] <= 1
        assert 0 <= theme["effects"]["radar_speed_dps"] <= 1000

if __name__ == "__main__":
    try:
        validate()
    except (AssertionError, OSError, ValueError, KeyError) as exc:
        print(f"Configuration invalid: {exc!r}", file=sys.stderr)
        sys.exit(1)
    print("AmpybiOS configuration OK")
