"""AmpyKernel integration tests using a temporary actual git repository.

These tests exercise Git's real patch engine, enforce source-blob and revision
checks, and never alter/download a production kernel.
"""
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from scripts.ampykernel_patch import ROOT, GuardError, run, locate_common, MANIFEST


def git(cwd, *args):
    return subprocess.run(["git", "-C", str(cwd), *args], check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True).stdout.strip()


class PatchTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.src = Path(self.tmp.name) / "common"
        self.src.mkdir()
        git(self.src, "init")
        git(self.src, "config", "user.email", "unit@test.invalid")
        git(self.src, "config", "user.name", "Kernel Test")
        self.cfg = json.loads(MANIFEST.read_text(encoding="utf-8"))
        # In fixture repository the SHA is NOT upstream; a passing test must
        # therefore refuse patch application, even if function names match.

    def test_refuse_wrong_checkout_sha(self):
        for relative in ("kernel/sched/cpufreq_schedutil.c",
                         "drivers/cpufreq/Kconfig"):
            target = self.src / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text("placeholder\n")
        git(self.src, "add", ".")
        git(self.src, "commit", "-m", "test")
        with self.assertRaisesRegex(GuardError, "revision mismatch"):
            run(self.src, "apply")

    def test_refuse_dirty_sources(self):
        self.assertTrue(self.cfg["patches"][0]["default_enabled"] is False)
        self.assertFalse(self.cfg["os_kernel_match_verified"])
        self.assertEqual(len(self.cfg["patches"][0]["source_files"]), 2)
        self.assertIn("CONFIG_AMPYKERNEL_SUGOV_ECO_DEFAULT",
                      (ROOT / self.cfg["patches"][0]["file"]).read_text())

    def test_kconfig_default_n(self):
        patch = (ROOT / self.cfg["patches"][0]["file"]).read_text()
        self.assertIn("+\tdefault n", patch)
        self.assertIn("+\ttunables->rate_limit_us = max(tunables->rate_limit_us, 2000U);", patch)
        self.assertNotIn("CONFIG_THERMAL", patch)
        self.assertNotIn("CONFIG_SECURITY_SELINUX", patch)

    def test_fixture_can_apply_and_revert_with_git(self):
        # Build a minimal strict upstream-style fixture *from the patch context*.
        # This validates patch syntax through git, not the full kernel build.
        text = (ROOT / self.cfg["patches"][0]["file"]).read_text()
        sections = text.split("diff --git a/")[1:]
        self.assertEqual(len(sections), 2)
        for section in sections:
            path = section.split(" b/", 1)[0]
            hunk = section.split("\n@@ ", 1)[1].split("\n", 1)[1]
            lines = []
            for line in hunk.splitlines():
                if line.startswith("diff --git"):
                    break
                if line.startswith(" ") or line.startswith("-"):
                    lines.append(line[1:])
            dest = self.src / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            # Enough lines before the hunk for exact offsets; git matches context.
            start_spec = section.split("@@ -", 1)[1].split(" ", 1)[0]
            hunk_start = int(start_spec.split(",", 1)[0])
            dest.write_text(("\n" * (hunk_start - 1)) + "\n".join(lines) + "\n")
        git(self.src, "add", ".")
        git(self.src, "commit", "-m", "fixture")
        p = ROOT / self.cfg["patches"][0]["file"]
        git(self.src, "apply", "--check", str(p))
        git(self.src, "apply", str(p))
        self.assertIn("config AMPYKERNEL_SUGOV_ECO_DEFAULT",
                      (self.src / "drivers/cpufreq/Kconfig").read_text())
        self.assertIn("CONFIG_AMPYKERNEL_SUGOV_ECO_DEFAULT",
                      (self.src / "kernel/sched/cpufreq_schedutil.c").read_text())
        git(self.src, "apply", "--reverse", "--check", str(p))
        git(self.src, "apply", "--reverse", str(p))
        self.assertEqual(git(self.src, "status", "--porcelain"), "")


if __name__ == "__main__":
    unittest.main()
