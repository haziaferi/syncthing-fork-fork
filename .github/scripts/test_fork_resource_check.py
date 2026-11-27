"""Tests for fork_resource_check.py (stdlib unittest, fixtures in a temporary directory)."""
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import fork_resource_check as frc


def strings_file(directory, *values):
    """Write a strings_fork.xml holding one <string> per value and return its path."""
    body = "\n".join(
        '    <string name="s%d">%s</string>' % (i, v) for i, v in enumerate(values)
    )
    path = Path(directory) / "strings_fork.xml"
    path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n%s\n</resources>\n' % body,
        encoding="utf-8",
    )
    return path


class StringRuleTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        self.dir = self._tmp.name

    def rules(self, *values):
        return {f.rule for f in frc.check_strings(strings_file(self.dir, *values))}

    def test_emoji_flags_symbols_and_selector(self):
        for value in ("⚠ Careful", "✅ Done", "Next → step", "Heart️"):
            with self.subTest(value=value):
                self.assertIn("emoji", self.rules(value))

    def test_emoji_allows_dot_ellipsis_dashes(self):
        for value in ("Set · needed to restore", "Saving…", "5–10"):
            with self.subTest(value=value):
                self.assertNotIn("emoji", self.rules(value))

    def test_all_caps_flags_shouted_strings(self):
        for value in ("FOLLOW RUN CONDITIONS", "RESTART"):
            with self.subTest(value=value):
                self.assertIn("all-caps", self.rules(value))

    def test_all_caps_allows_acronyms(self):
        for value in ("Show QR code", "VPN over Wi-Fi"):
            with self.subTest(value=value):
                self.assertNotIn("all-caps", self.rules(value))

    def test_terms_case_sensitive(self):
        cases = [
            ("web GUI", True), ("Web GUI", False),
            ("WiFi", True), ("Wi-Fi", False),
            ("config", True), ("configuration", False),
        ]
        for value, flagged in cases:
            with self.subTest(value=value):
                self.assertEqual("term" in self.rules(value), flagged)

    def test_cdata_is_checked(self):
        self.assertIn("wording", self.rules("<![CDATA[Tap here!]]>"))

    def test_unicode_escapes_are_decoded(self):
        self.assertIn("emoji", self.rules("Done \\u2705"))
        self.assertIn("emoji", self.rules("Party \\ud83c\\udf89"))

    def test_string_array_does_not_merge_with_next_string(self):
        path = Path(self.dir) / "strings_fork.xml"
        path.write_text(
            '<resources>\n'
            '<string-array name="a">\n'
            '  <item>Fine</item>\n'
            '</string-array>\n'
            '<string name="b">Tap to fix</string>\n'
            '</resources>\n',
            encoding="utf-8",
        )
        findings = frc.check_strings(path)
        self.assertEqual([(f.rule, f.line, f.text) for f in findings], [("wording", 5, "Tap to fix")])

    def test_wording_flags_tap_and_menu_paths(self):
        for value in ("Tap to fix", "Go to Settings-Troubleshooting"):
            with self.subTest(value=value):
                self.assertIn("wording", self.rules(value))


class CompareTest(unittest.TestCase):
    KEY = ("text-size", "app/src/main/res/layout/a.xml")

    def test_count_may_shrink_not_grow(self):
        baseline = {self.KEY: 3}
        self.assertEqual(frc.compare({self.KEY: 3}, baseline), [])
        # a lower count is progress, but the baseline must be lowered with it
        self.assertEqual(len(frc.compare({self.KEY: 2}, baseline)), 1)
        grown = frc.compare({self.KEY: 4}, baseline)
        self.assertEqual([f.rule for f in grown], ["text-size"])
        new_file = frc.compare({("text-size", "app/src/main/res/layout/new.xml"): 1}, baseline)
        self.assertIn("app/src/main/res/layout/new.xml", [f.path for f in new_file])


    def test_count_below_baseline_fails_with_hint(self):
        found = frc.compare({self.KEY: 2}, {self.KEY: 3})
        self.assertEqual(len(found), 1)
        self.assertIn("--write-baseline", found[0].text)

    def test_baseline_entry_for_missing_file_fails(self):
        found = frc.compare({}, {self.KEY: 3})
        self.assertEqual([(f.rule, f.path) for f in found], [self.KEY])
        self.assertIn("--write-baseline", found[0].text)

    def test_sync_branch_skips_count_comparison(self):
        counts, baseline = {self.KEY: 9}, {self.KEY: 3, ("font-size", "gone.kt"): 1}
        self.assertEqual(frc.compare(counts, baseline, "sync/upstream-2026-12-01"), [])
        self.assertNotEqual(frc.compare(counts, baseline, "feat/x"), [])


class MainTest(unittest.TestCase):
    def test_missing_base_ref_exits_2(self):
        self.assertEqual(frc.main(["--base", "no-such-ref-xyz", "--branch", "feat/x"]), 2)


class UpstreamResTest(unittest.TestCase):
    def test_upstream_res_change_fails_outside_sync_branch(self):
        changed = [
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/values-de/themes.xml",
            "app/src/main/res/values/strings_fork.xml",
        ]
        with mock.patch.object(frc, "changed_files", return_value=changed):
            on_feature = frc.upstream_res_changed("origin/main", "feat/x")
            on_sync = frc.upstream_res_changed("origin/main", "sync/upstream-2026-12-01")
        self.assertEqual(
            sorted(f.path for f in on_feature),
            ["app/src/main/res/values-de/themes.xml", "app/src/main/res/values/strings.xml"],
        )
        self.assertTrue(all(f.rule == "upstream-res" for f in on_feature))
        self.assertEqual(on_sync, [])


class CountLiteralsTest(unittest.TestCase):
    def test_counts_layouts_and_kotlin_outside_theme(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            layout = root / "app/src/main/res/layout"
            layout.mkdir(parents=True)
            (layout / "a.xml").write_text(
                '<TextView android:textSize="14sp" android:textColor="#FF00ff00"/>\n'
                '<TextView android:textSize="12.5dp"/>\n',
                encoding="utf-8",
            )
            kt = root / "app/src/main/java/com/x"
            (kt / "theme").mkdir(parents=True)
            (kt / "A.kt").write_text("Text(fontSize = 12.sp, color = Color(0xFF112233))\n", encoding="utf-8")
            (kt / "theme" / "T.kt").write_text("val s = 1.sp; fontSize = 3; Color(0xFF000000)\n", encoding="utf-8")
            counts = frc.count_literals(root)
        self.assertEqual(counts[("text-size", "app/src/main/res/layout/a.xml")], 2)
        self.assertEqual(counts[("colour-literal", "app/src/main/res/layout/a.xml")], 1)
        self.assertEqual(counts[("font-size", "app/src/main/java/com/x/A.kt")], 1)
        self.assertEqual(counts[("colour-literal", "app/src/main/java/com/x/A.kt")], 1)
        self.assertFalse(any("theme" in path for _, path in counts))


if __name__ == "__main__":
    unittest.main()
