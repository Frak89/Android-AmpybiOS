"""Standard-library extraction tests (no large kernel source needed)."""
import tempfile
import unittest
from pathlib import Path

from scripts.extract_ampykernel_functions import clean_c, find_function, inspect, save_report


class AmpyKernelExtractorTests(unittest.TestCase):
    def test_ignores_comments_calls_and_prototypes(self):
        c = '''// target(int x) { fake(); }
char *note = "target(int x) { bogus(); }";
void target(int x);
void caller(void) { target(1); }
static int target(int x)
{
    if (x) { x++; }
    return x;
}
'''
        found = find_function(c, "target")
        self.assertIsNotNone(found)
        self.assertIn("static int target", found["definition"])
        self.assertIn("return x;", found["definition"])
        self.assertNotIn("void caller", found["definition"])
        self.assertIsNone(find_function(c, "missing"))

    def test_handles_braces_in_comments_and_strings(self):
        src = '''static int handler(void) {
    /* } fake closing brace */
    char *s = "brace: }";
    if (1) { return 42; }
    return 0;
}
'''
        result = find_function(src, "handler")
        self.assertIsNotNone(result)
        self.assertIn("return 0;", result["definition"])
        self.assertTrue(len(result["definition_sha256"]) == 64)

    def test_run_on_partial_checkout_and_output(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            checkout = root / "source"
            p = checkout / "common/kernel/sched"
            p.mkdir(parents=True)
            (p / "cpufreq_schedutil.c").write_text(
                "static bool sugov_should_update_freq(int a) { return a != 0; }",
                encoding="utf-8"
            )
            report, snippets = inspect(checkout, include_bodies=True)
            hits = [x for x in report["functions"] if x["status"] == "found"]
            self.assertTrue(any(x["symbol"] == "sugov_should_update_freq"
                                for x in hits))
            self.assertTrue(any(x["status"] == "source_file_missing"
                                for x in report["functions"]))
            out = root / "output"
            save_report(report, snippets, out, True)
            self.assertTrue((out / "functions.json").is_file())
            self.assertTrue((out / "FUNCTIONS.md").is_file())
            self.assertTrue((out / "snippets/sugov_should_update_freq.c.txt").is_file())

    def test_rejects_invalid_symbol(self):
        with self.assertRaises(ValueError):
            find_function("int f(void) {return 0;}", "../f")


if __name__ == "__main__":
    unittest.main()
