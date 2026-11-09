#!/usr/bin/env python3
"""Bahasa Indonesia coverage check for the Android app.

    python3 tools/i18n_check.py

Lists English UI text in defs/ and ui/ that has no entry in app/src/main/assets/i18n/id.json and exits 1 if any.
UI text is: every I18n.t("…") literal, plus the label-like string literals of the page definitions (anything that
starts with a capital letter and is not an API path, a JSON key or a format name). The dictionary itself is
generated from lestari-client: node tools/i18n-export.js ../lestari-mobile/app/src/main/assets/i18n/id.json
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/id/lestari/mobile"
DICT = json.loads((ROOT / "app/src/main/assets/i18n/id.json").read_text(encoding="utf-8"))

LIT = re.compile(r'"((?:[^"\\]|\\.)*)"')
TRANSLATED = re.compile(r'I18n\.t\(\s*"((?:[^"\\]|\\.)*)"')
NOT_TEXT = {"GET", "POST", "PUT", "PATCH", "DELETE", "UTF-8", "RESET", "Lestari"}


def unescape(s):
    return s.replace('\\"', '"').replace("\\n", "\n")


def ui_text(s):
    if not s or s in NOT_TEXT or "/" in s.split(" ")[0] or "{" in s and "}" not in s:
        return False
    if re.fullmatch(r"[A-Z0-9_ ]+", s):  # codes such as "IDR", "EAN-13" are listed when they need an entry
        return False
    return s[0].isupper() or s[0] in "—▾▸" and len(s) > 3


missing = {}
for path in sorted(SRC.rglob("*.java")):
    if path.parts[-2] not in ("defs", "ui"):
        continue
    for no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        code = line.strip()
        if code.startswith(("//", "*", "/*", "import ")) or "equals(" in code or "setRequestProperty" in code:
            continue
        found = [unescape(m) for m in TRANSLATED.findall(line)]
        if path.parts[-2] == "defs":
            found += [unescape(m) for m in LIT.findall(line) if ui_text(unescape(m))]
        for s in found:
            if s.strip() and s not in DICT:
                missing.setdefault(s, f"{path.relative_to(ROOT)}:{no}")

print(f"{len(DICT)} dictionary entries, {len(missing)} missing")
for s, where in sorted(missing.items()):
    print(f"  missing: {json.dumps(s, ensure_ascii=False)}  ({where})")
sys.exit(1 if missing else 0)
