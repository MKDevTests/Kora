#!/usr/bin/env python3
"""Moves hardcoded UI literals into the AppStrings catalogue.

One shared group (UiStrings) rather than one per screen: the same words appear
on five screens ("Download", "Cancel", "Delete"), and a group per area would
mean translating each of them five times and letting them drift apart.

The French comes from `i18n_fr.py`, hand-written. A literal without a
translation is reported and left alone — a half-translated screen is worse than
an English one, and silence would hide it.

  python scripts/i18n-apply.py <area> [<area>...]     # rewrite + regenerate
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from i18n_fr import ENGLISH_OVERRIDES, FRENCH  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / "komelia-ui" / "src"
STRINGS = UI / "commonMain" / "kotlin" / "snd" / "komelia" / "ui" / "strings"

AREAS = {
    "series": ("/ui/series/",),
    "book": ("/ui/book/",),
    "library": ("/ui/library/", "/ui/home/", "/ui/search/"),
    "reader": ("/ui/reader/",),
    "dialogs": ("/ui/dialogs/",),
    "menus": ("/ui/common/menus/",),
    "common": ("/ui/common/",),
    "settings": ("/ui/settings/",),
    # Everything else: login, offline, stats, colour correction, widgets.
    "other": ("/ui/",),
}

PATTERNS = [
    re.compile(r'\bText\(\s*("(?:[^"\\]|\\.)+")\s*[,)]'),
    re.compile(r'\bText\(\s*text\s*=\s*("(?:[^"\\]|\\.)+")'),
    re.compile(r'contentDescription\s*=\s*("(?:[^"\\]|\\.)+")'),
    # Labels handed to a component rather than to Text. Only inside a
    # @Composable: the same names are used to build persisted data elsewhere,
    # where there is no composition to read the catalogue from.
    re.compile(
        r'\b(?:label|title|subtitle|header|description|supportingText|placeholder|hint|text)'
        r'\s*=\s*("(?:[^"\\]|\\.)+")'
    ),
    # First argument of our own wrappers. An allowlist, not every PascalCase
    # call: `Regex("…")`, `MutableStateFlow("")` and exceptions take strings
    # too, and none of them are read by a human.
    re.compile(
        r'\b(?:SettingsScreenContainer|SectionHeader|Tooltip|SwitchWithLabel|CheckboxWithLabel)'
        r'\(\s*("(?:[^"\\]|\\.)+")'
    ),
]

# Index 0..2 are safe anywhere; the parameter-name pattern is not.
COMPOSABLE_ONLY = {3, 4}

BEGIN, END = "    // region generated-ui-strings", "    // endregion"


def key_of(text: str) -> str:
    words = re.findall(r"[A-Za-z0-9]+", text)[:5]
    # A Kotlin identifier cannot start with a digit: "1 pending task" produced
    # `val 1PendingTask`, which the compiler read as a number followed by junk.
    while words and words[0].isdigit():
        words = words[1:]
    if not words:
        return ""
    head, *tail = [w.lower() for w in words]
    return head + "".join(w.capitalize() for w in tail)


def files_of(areas: list[str]) -> list[Path]:
    prefixes = [p for area in areas for p in AREAS[area]]
    out = []
    for path in UI.rglob("*.kt"):
        posix = path.as_posix()
        if "/build/" in posix:
            continue
        if any(p in posix for p in prefixes):
            out.append(path)
    return out


def without_strings(text: str) -> str:
    """Same length, with string literals and comments blanked out.

    Counting braces on the raw source cut a function short at the first `{`
    inside a string or a comment — which is how the whole settings menu ended
    up looking like it was outside any @Composable.
    """
    out = list(text)
    i, n = 0, len(text)
    while i < n:
        char = text[i]
        if char == '"':
            triple = text.startswith('"""', i)
            end = i + (3 if triple else 1)
            while end < n:
                if not triple and text[end] == '\\':
                    end += 2
                    continue
                if triple and text.startswith('"""', end):
                    end += 3
                    break
                if not triple and text[end] == '"':
                    end += 1
                    break
                if not triple and text[end] == '\n':
                    break
                end += 1
            for j in range(i, min(end, n)):
                if out[j] not in '\n':
                    out[j] = ' '
            i = end
        elif text.startswith('//', i):
            end = text.find('\n', i)
            end = n if end == -1 else end
            for j in range(i, end):
                out[j] = ' '
            i = end
        elif text.startswith('/*', i):
            end = text.find('*/', i)
            end = n if end == -1 else end + 2
            for j in range(i, end):
                if out[j] != '\n':
                    out[j] = ' '
            i = end
        else:
            i += 1
    return ''.join(out)


def composable_spans(text: str) -> list[tuple[int, int]]:
    """Character ranges covered by the BODY of @Composable functions.

    Character-based, and parenthesis-aware: the body starts at the first `{`
    outside the parameter list. Counting from the `fun` line instead closed the
    span on the first default lambda in the signature — `onNavigation: (Screen)
    -> Unit = {}` ended the settings menu after seven lines, which is why every
    label in it stayed English.
    """
    masked = without_strings(text)
    spans: list[tuple[int, int]] = []
    for annotation in re.finditer(r'@Composable\b', masked):
        declaration = re.compile(r'\bfun\b').search(masked, annotation.end())
        if not declaration:
            continue
        # Nothing but modifiers and annotations may sit in between.
        between = masked[annotation.end():declaration.start()]
        if re.search(r'[;{}]|\bval\b|\bclass\b', between):
            continue
        depth_paren = 0
        body_start = None
        for index in range(declaration.end(), len(masked)):
            char = masked[index]
            if char == '(':
                depth_paren += 1
            elif char == ')':
                depth_paren -= 1
            elif char == '{' and depth_paren == 0:
                body_start = index
                break
            elif char == '=' and depth_paren == 0 and masked[index - 1] not in '<>=!':
                break  # expression body, no block to scan
        if body_start is None:
            continue
        depth = 0
        for index in range(body_start, len(masked)):
            if masked[index] == '{':
                depth += 1
            elif masked[index] == '}':
                depth -= 1
                if depth == 0:
                    spans.append((body_start, index))
                    break
    return spans


def rewrite(path: Path, keys: dict[str, str]) -> int:
    """Replaces known literals by their catalogue entry. Returns the count."""
    text = path.read_text(encoding="utf-8")
    spans = composable_spans(text)
    edits = []
    for index, pattern in enumerate(PATTERNS):
        for match in pattern.finditer(text):
            literal = match.group(1)
            value = literal[1:-1]
            if value not in keys:
                continue
            if index in COMPOSABLE_ONLY and not any(a <= match.start(1) < b for a, b in spans):
                continue
            edits.append((match.start(1), match.end(1), f"LocalStrings.current.ui.{keys[value]}"))
    if not edits:
        return 0
    # Two patterns can match the SAME literal — `Text(text = "…")` is caught by
    # the Text rule and by the parameter rule. Applying both wrote the
    # replacement over the previous one and left spliced garbage like
    # `noBookmarksYetnt` in the source, so overlaps are dropped here.
    kept: list[tuple[int, int, str]] = []
    for start, end, replacement in sorted(edits):
        if kept and start < kept[-1][1]:
            continue
        kept.append((start, end, replacement))
    for start, end, replacement in reversed(kept):
        text = text[:start] + replacement + text[end:]
    if "import snd.komelia.ui.LocalStrings" not in text and "package snd.komelia.ui\n" not in text:
        lines = text.split("\n")
        last_import = max(i for i, line in enumerate(lines) if line.startswith("import "))
        lines.insert(last_import + 1, "import snd.komelia.ui.LocalStrings")
        text = "\n".join(lines)
    path.write_text(text, encoding="utf-8", newline="\n")
    return len(edits)


def region_of(path: Path) -> str:
    """Only what the generator owns: the rest of EnStrings has the same shape,
    and reading the whole file swallowed every other group into this one."""
    text = path.read_text(encoding="utf-8")
    start = text.index(BEGIN) + len(BEGIN)
    return text[start:text.index(END, start)]


def replace_region(path: Path, body: str) -> None:
    text = path.read_text(encoding="utf-8")
    assert BEGIN in text and END in text, f"markers missing in {path.name}"
    start = text.index(BEGIN) + len(BEGIN)
    end = text.index(END, start)
    path.write_text(text[:start] + "\n" + body + text[end:], encoding="utf-8", newline="\n")


def escape(value: str) -> str:
    """Values travel VERBATIM between the source and the catalogue.

    They were extracted from a Kotlin literal, so `\\n` and `\\"` are already the
    escapes Kotlin wants; re-escaping them turned a line break into the two
    characters backslash-n on screen, and doubled again on every rerun. The
    French translations are written Kotlin-ready for the same reason — this
    checks it rather than trusting it.
    """
    unescaped_quote = re.search(r'(?<!\\)"', value)
    unescaped_dollar = re.search(r'(?<!\\)\$', value)
    assert not unescaped_quote and not unescaped_dollar, f"needs escaping: {value!r}"
    return value


def main() -> None:
    areas = sys.argv[1:]
    if not areas or any(a not in AREAS for a in areas):
        raise SystemExit(__doc__)

    files = files_of(areas)
    found: dict[str, None] = {}
    for path in files:
        text = path.read_text(encoding="utf-8")
        spans = composable_spans(text)
        for index, pattern in enumerate(PATTERNS):
            for match in pattern.finditer(text):
                if index in COMPOSABLE_ONLY and not any(a <= match.start(1) < b for a, b in spans):
                    continue
                found.setdefault(match.group(1)[1:-1], None)

    translatable = {v: key_of(v) for v in found if v in FRENCH and key_of(v)}
    missing = sorted(v for v in found if v not in FRENCH and key_of(v))

    # Everything already in the catalogue stays there: a rerun on another area
    # must not drop the previous one.
    existing = {}
    for line in region_of(STRINGS / "EnStrings.kt").split("\n"):
        m = re.match(r'\s{8}"(\w+)" to "(.*)",$', line) or re.match(r'\s{8}(\w+) = "(.*)",$', line)
        if m and m.group(1) not in ("", None):
            existing[m.group(1)] = m.group(2)

    keys: dict[str, str] = {}
    for value, key in translatable.items():
        candidate, n = key, 2
        while candidate in keys.values() or (candidate in existing and existing[candidate] != escape(value)):
            candidate, n = f"{key}{n}", n + 1
        keys[value] = candidate

    edited = sum(rewrite(path, keys) for path in files)

    by_key = {k: v for v, k in keys.items()}
    for key, value in existing.items():
        by_key.setdefault(key, value)

    # Accessors over a map, NOT constructor parameters: a Dalvik call passes at
    # most 255 argument registers, and a data class of 300 strings makes the
    # verifier reject the class that builds it — the app then dies on startup
    # with "rejected class EnStringsKt".
    fields, en, fr = [], [], []
    for key in sorted(by_key):
        value = by_key[key]
        fields.append(f'    val {key}: String get() = at("{key}")')
        en.append(f'        "{key}" to "{escape(ENGLISH_OVERRIDES.get(value, value))}",')
        fr.append(f'        "{key}" to "{escape(FRENCH.get(value, value))}",')

    replace_region(STRINGS / "AppStrings.kt", "\n".join(fields) + "\n")
    replace_region(STRINGS / "EnStrings.kt", "\n".join(en) + "\n")
    replace_region(STRINGS / "FrStrings.kt", "\n".join(fr) + "\n")

    print(f"{edited} call sites rewritten in {len(files)} files, {len(by_key)} entries in the catalogue")
    if missing:
        print(f"\n{len(missing)} literals left alone (no French yet):")
        for value in missing:
            print(f"    {value!r}: ,")


if __name__ == "__main__":
    main()
