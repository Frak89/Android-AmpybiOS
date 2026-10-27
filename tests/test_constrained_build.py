import unittest
from collections import namedtuple
from pathlib import Path
from unittest.mock import patch

from scripts.check_build_host import inspect


class ConstrainedBuildTests(unittest.TestCase):
    def report(self, allow, missing=False):
        usage = namedtuple('Usage', 'total used free')(40 << 30, 20 << 30, 20 << 30)
        with patch('scripts.check_build_host.shutil.disk_usage', return_value=usage), \
             patch('scripts.check_build_host.shutil.which', side_effect=lambda name: None if missing and name == 'repo' else '/bin/tool'), \
             patch('scripts.check_build_host.platform.system', return_value='Linux'), \
             patch('scripts.check_build_host.platform.machine', return_value='x86_64'), \
             patch.object(Path, 'read_text', return_value='MemTotal: 8388608 kB\n'):
            return inspect(Path('/tmp'), allow_constrained=allow)

    def test_default_still_refuses_small_host(self):
        self.assertFalse(self.report(False)['ready'])

    def test_explicit_override_keeps_resource_diagnostics(self):
        report = self.report(True)
        self.assertTrue(report['ready'])
        self.assertTrue(report['resource_override'])
        self.assertEqual(len(report['resource_warnings']), 2)
        self.assertEqual(report['free_storage_gib'], 20)

    def test_override_never_hides_missing_tools(self):
        report = self.report(True, missing=True)
        self.assertFalse(report['ready'])
        self.assertTrue(any('repo' in warning for warning in report['warnings']))


if __name__ == '__main__':
    unittest.main()
