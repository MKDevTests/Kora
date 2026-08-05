#!/usr/bin/env python3
"""Checks that every catalogue key a screen reads actually exists.

`LocalStrings.current.ui.someKey` compiles only if `someKey` is in UiStrings,
and the compiler is the one that says so — three minutes into a build, after a
device install cycle. This says it in a second.

  python scripts/i18n-verify.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / "komelia-ui" / "src"
STRINGS = UI / "commonMain" / "kotlin" / "snd" / "komelia" / "ui" / "strings"

USE = re.compile(r'LocalStrings\.current\.ui\.([A-Za-z0-9_]+)')


def declared_keys() -> set[str]:
    text = (STRINGS / "AppStrings.kt").read_text(encoding="utf-8")
    start = text.index("// region generated-ui-strings")
    end = text.index("// endregion", start)
    return set(re.findall(r'val (\w+): String get\(\)', text[start:end]))


def main() -> None:
    known = declared_keys()
    missing: list[tuple[Path, int, str]] = []
    for path in UI.rglob("*.kt"):
        if "/build/" in path.as_posix():
            continue
        for number, line in enumerate(path.read_text(encoding="utf-8").split("\n"), start=1):
            for key in USE.findall(line):
                if key not in known:
                    missing.append((path.relative_to(ROOT), number, key))

    print(f"{len(known)} keys declared")
    if not missing:
        print("every reference resolves")
        return
    print(f"\n{len(missing)} reference(s) to a key that does not exist:")
    for path, number, key in missing:
        print(f"    {path}:{number}  {key}")
    sys.exit(1)


if __name__ == "__main__":
    main()
