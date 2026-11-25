#!/usr/bin/env python3
"""Keep the fork's own resources inside the app-wide ui design spec.

Rules: emoji, all-caps, wording (R17), term (R18), text-size (R07), font-size (R07),
colour-literal (R10), upstream-res (R41).

Usage: fork_resource_check.py [--write-baseline] [--base <git-ref>] [--branch <name>]
Exits 0 when clean, 1 with one line per finding, 2 when the base ref cannot be diffed.
The count rules fail when a file's count is above its baseline, below it (lower the
baseline with --write-baseline) or the file is gone. On sync/upstream-* branches the
count comparison is skipped, because the baseline is rewritten in the same sync PR;
string rules and upstream-res still run.
"""
import argparse
import html
import os
import re
import subprocess
import sys
import unicodedata
from pathlib import Path
from typing import NamedTuple

ROOT = Path(__file__).resolve().parents[2]
RES = "app/src/main/res"
BASELINE = ".github/fork-resource-baseline.txt"
COUNT_RULES = ("text-size", "font-size", "colour-literal")


class Finding(NamedTuple):
    rule: str
    path: str
    line: int
    text: str


# --- string rules ---------------------------------------------------------

_STRING_RE = re.compile(r"<(string|item)(?=[\s>])[^>]*>(.*?)</\1>", re.DOTALL)
_COMMENT_RE = re.compile(r"<!--.*?-->", re.DOTALL)
_WORD_RE = re.compile(r"[^\W\d_]+")
_WORDING_RE = re.compile(r"\b(?:Click|Tap|tap|please|Please)\b|Go to Settings|!")
_TERM_RE = re.compile(
    r"\bWiFi\b|Web UI|WebUI|web GUI|\bconfig\b|whitelist|sync conditions"
    r"|Syncthing for Android|Syncthing Camera|Sync\. Camera"
)


def _is_emoji_char(ch):
    cp = ord(ch)
    return (
        cp >= 0x1F000
        or cp == 0xFE0F
        or 0x2190 <= cp <= 0x21FF
        or unicodedata.category(ch) == "So"
    )


def _is_all_caps(text):
    words = [w for w in _WORD_RE.findall(text) if len(w) >= 3 and w.isupper()]
    if len(words) >= 2:
        return True
    letters = "".join(_WORD_RE.findall(text))
    return len(letters) >= 5 and letters.isupper()


_CDATA_RE = re.compile(r"<!\[CDATA\[(.*?)\]\]>", re.DOTALL)
_UNICODE_ESCAPE_RE = re.compile(r"\\u([0-9a-fA-F]{4})")


def _unescape(raw):
    text = _CDATA_RE.sub(r"\1", raw)
    text = html.unescape(re.sub(r"<[^>]+>", "", text))
    text = _UNICODE_ESCAPE_RE.sub(lambda m: chr(int(m.group(1), 16)), text)
    try:  # join surrogate pairs written as two \uXXXX escapes
        text = text.encode("utf-16", "surrogatepass").decode("utf-16")
    except UnicodeError:
        pass
    return text.replace("\\'", "'").replace('\\"', '"')


def check_strings(path):
    """Check one fork strings file for emoji, all caps, banned wording and terms."""
    path = Path(path)
    source = path.read_text(encoding="utf-8")
    # blank comments out but keep newlines, so line numbers stay right
    source = _COMMENT_RE.sub(lambda m: re.sub(r"[^\n]", " ", m.group(0)), source)
    findings = []
    for m in _STRING_RE.finditer(source):
        text = _unescape(m.group(2))
        line = source.count("\n", 0, m.start(2)) + 1
        shown = str(path)
        if any(_is_emoji_char(c) for c in text):
            findings.append(Finding("emoji", shown, line, text))
        if _is_all_caps(text):
            findings.append(Finding("all-caps", shown, line, text))
        if _WORDING_RE.search(text):
            findings.append(Finding("wording", shown, line, text))
        if _TERM_RE.search(text):
            findings.append(Finding("term", shown, line, text))
    return findings


# --- count rules ----------------------------------------------------------

_TEXT_SIZE_RE = re.compile(r'android:textSize="\s*[0-9.]+\s*(?:sp|dp|px)"')
_HEX_RE = re.compile(r"#[0-9a-fA-F]{6,8}")
_FONT_SIZE_RE = re.compile(r"fontSize\s*=")
_COLOR_KT_RE = re.compile(r"Color\(0x")


def _read(path):
    return path.read_text(encoding="utf-8", errors="replace")


def count_literals(root):
    """Count size and colour literals per (rule, posix path relative to root)."""
    root = Path(root)
    counts = {}

    def add(rule, path, n):
        if n:
            counts[(rule, path.relative_to(root).as_posix())] = n

    res = root / RES
    for layout_dir in sorted(res.glob("layout*")):
        for path in sorted(layout_dir.rglob("*.xml")):
            text = _read(path)
            add("text-size", path, len(_TEXT_SIZE_RE.findall(text)))
            add("colour-literal", path, len(_HEX_RE.findall(text)))
    java = root / "app/src/main/java"
    for path in sorted(java.rglob("*.kt")):
        if "theme" in path.relative_to(java).parts[:-1]:
            continue
        text = _read(path)
        add("font-size", path, len(_FONT_SIZE_RE.findall(text)))
        add("colour-literal", path, len(_COLOR_KT_RE.findall(text)))
    return counts


LOWER_HINT = "lower the baseline with --write-baseline"


def is_sync_branch(branch):
    return (branch or "").startswith("sync/upstream-")


def compare(counts, baseline, branch=""):
    """Findings for counts above their baseline or missing from it, and for counts below it.

    A count below its baseline (or a baseline entry whose file is gone) also fails, so a
    lowered count cannot climb back. On sync/upstream-* branches nothing is compared: the
    baseline is rewritten in the same sync PR.
    """
    if is_sync_branch(branch):
        return []
    findings = []
    for (rule, path), n in sorted(counts.items()):
        allowed = baseline.get((rule, path), 0)
        if n > allowed:
            findings.append(Finding(rule, path, 0, "%d found, baseline allows %d" % (n, allowed)))
        elif n < allowed:
            findings.append(Finding(rule, path, 0, "%d found, baseline has %d: %s" % (n, allowed, LOWER_HINT)))
    for (rule, path), allowed in sorted(baseline.items()):
        if (rule, path) not in counts and allowed > 0:
            findings.append(Finding(rule, path, 0, "0 found, baseline has %d: %s" % (allowed, LOWER_HINT)))
    return findings


def read_baseline(path):
    baseline = {}
    p = Path(path)
    if not p.exists():
        return baseline
    for line in p.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        rule, file, count = line.split("\t")
        baseline[(rule, file)] = int(count)
    return baseline


def write_baseline(path, counts):
    lines = ["%s\t%s\t%d" % (r, f, n) for (r, f), n in sorted(counts.items())]
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


# --- upstream resources ---------------------------------------------------

_UPSTREAM_RES_RE = re.compile(r"(?:^|/)res/values[^/]*/(?:strings|themes)\.xml$")


def changed_files(base):
    out = subprocess.run(
        ["git", "diff", "--name-only", "--no-renames", "%s...HEAD" % base],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout
    return [line.strip() for line in out.splitlines() if line.strip()]


def upstream_res_changed(base, branch):
    """Findings for edits to upstream's strings.xml / themes.xml, except on sync branches."""
    if branch.startswith("sync/upstream-"):
        return []
    return [
        Finding("upstream-res", f, 0, "upstream resource file changed outside a sync branch")
        for f in changed_files(base)
        if _UPSTREAM_RES_RE.search(f)
    ]


# --- entry point ----------------------------------------------------------

def _current_branch():
    env = os.environ.get("GITHUB_HEAD_REF")
    if env:
        return env
    return subprocess.run(
        ["git", "rev-parse", "--abbrev-ref", "HEAD"],
        cwd=ROOT, capture_output=True, text=True, check=True,
    ).stdout.strip()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--write-baseline", action="store_true")
    parser.add_argument("--base", default="origin/main")
    parser.add_argument("--branch", default=None)
    args = parser.parse_args(argv)

    counts = count_literals(ROOT)
    baseline_path = ROOT / BASELINE
    if args.write_baseline:
        write_baseline(baseline_path, counts)
        print("wrote %d lines to %s" % (len(counts), BASELINE))
        return 0

    findings = []
    for path in sorted((ROOT / RES).glob("values*/strings_fork.xml")):
        for f in check_strings(path):
            findings.append(f._replace(path=path.relative_to(ROOT).as_posix()))
    branch = args.branch or _current_branch()
    findings += compare(counts, read_baseline(baseline_path), branch)
    try:
        findings += upstream_res_changed(args.base, branch)
    except subprocess.CalledProcessError:
        print("error: cannot diff against base ref %r (is it fetched?)" % args.base)
        return 2

    for f in findings:
        where = "%s:%d" % (f.path, f.line) if f.line else f.path
        print("%s: [%s] %s" % (where, f.rule, f.text))
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
