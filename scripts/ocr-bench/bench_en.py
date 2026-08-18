#!/usr/bin/env python3
"""One command from a CBZ to the French the reader would show, and a diff.

    python scripts/ocr-bench/bench_en.py "W:/.../volume.cbz"        run it
    python scripts/ocr-bench/bench_en.py <name> --accept            bless the result
    python scripts/ocr-bench/bench_en.py --list                     what is captured

Why this exists. Every piece of this chain already shipped -- run_ocr.py, the
:ocr-bench replay, run_bergamot.sh -- and nothing joined them, so a one-line
change to the word splitter cost a full Android build, an install and forty
pages read by hand before anyone knew whether it helped. That is the reason
this bench exists and the reason it must stay a single command.

    CBZ --(1) run_ocr.py, real PP-OCRv6------> <page>.boxes.json
        --(2) :ocr-bench replay, real Kotlin-> sentences.txt
        --(3) run_bergamot.sh, real en-fr----> french.txt
        --(4) diff against baseline.tsv-----> what changed, and only that

Nothing here reimplements a pipeline stage. Step 2 runs the same Kotlin files
the app compiles and step 3 the same model it downloads, so a result that
disagrees with the tablet is a finding, not a bug in the bench.

Step 1 is cached, which is the whole point: it is the slow one (seconds a page)
and it does not move when Kotlin does. Iterating on the merge, the splitter, the
casing or the phrase book replays steps 2-4 only, which is about a minute.

The diff is what replaces reading. A run prints only the lines whose French
changed since the blessed baseline, so "did that help?" is answered by looking
at ten lines instead of a chapter. Bless the new output with --accept once the
change is judged good; regressions then show up on their own next time.

Runs from Windows: cv2 and rapidocr_onnxruntime are installed there, while
Gradle and the Bergamot binary live in WSL. Both are invoked as they are.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
BENCH = REPO / "_bench-en"
ANDROID_HOME = "/home/mathieu/android-sdk"


def wsl(command: str, quiet: bool = False) -> str:
    """Runs a bash line in WSL and returns its output, failing loudly."""
    done = subprocess.run(
        ["wsl", "-e", "bash", "-lc", command],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if done.returncode != 0:
        sys.exit(f"WSL failed ({done.returncode}):\n{command}\n{done.stdout}\n{done.stderr}")
    if not quiet and done.stdout.strip():
        print(done.stdout.strip()[-2000:])
    return done.stdout


def to_wsl(path: Path) -> str:
    text = str(path).replace("\\", "/")
    return "/mnt/" + text[0].lower() + text[2:]


WSL_REPO = None  # set in main(), once to_wsl exists


def ocr(source: Path, out: Path, pages: int | None, reocr: bool) -> None:
    """Step 1, cached. The only slow one, and the only one Kotlin cannot move."""
    existing = sorted(out.glob("*.boxes.json"))
    if existing and not reocr:
        print(f"[1/4] OCR: {len(existing)} pages already captured (--reocr to redo)")
        return
    out.mkdir(parents=True, exist_ok=True)
    argv = [sys.executable, str(REPO / "scripts" / "ocr-bench" / "run_ocr.py"),
            str(source), "--out", str(out)]
    if pages:
        argv += ["--limit", str(pages)]
    print(f"[1/4] OCR: {source.name} -> {out}")
    done = subprocess.run(argv)
    if done.returncode != 0:
        sys.exit("run_ocr.py failed")


def replay(out: Path) -> Path:
    """Step 2: the real Kotlin, producing exactly what the reader would send.

    Every path is quoted: capture directories are named after the volume and
    volumes have spaces in their names, which turned KORA_BENCH_DIR into a
    command Gradle never saw. The failure was silent because the output was
    piped through grep, whose exit code is the pipeline's -- so a broken run
    looked exactly like a run that found nothing to report. Filtering happens in
    Python now, where a non-zero Gradle still fails.
    """
    print("[2/4] Kotlin pipeline (merge, splitter, casing, phrase book)")
    output = wsl(
        f"cd '{WSL_REPO}' && ANDROID_HOME='{ANDROID_HOME}' "
        f"KORA_BENCH_DIR='{to_wsl(out)}' "
        f"./gradlew :ocr-bench:test --tests '*VolumeReplay*' --rerun-tasks",
        quiet=True,
    )
    for line in output.splitlines():
        if "pages," in line or "widest" in line:
            print(f"      {line.strip()}")
    sentences = out / "sentences.txt"
    if not sentences.is_file():
        sys.exit(f"the replay wrote no sentences.txt in {out}")
    return sentences


def translate(sentences: Path, out: Path) -> Path:
    """Step 3: the same Bergamot model the app downloads."""
    french = out / "french.txt"
    print("[3/4] Bergamot en-fr")
    wsl(
        f"cd '{WSL_REPO}' && ./scripts/ocr-bench/run_bergamot.sh "
        f"'{to_wsl(sentences)}' '{to_wsl(french)}'",
        quiet=True,
    )
    if not french.is_file():
        sys.exit("run_bergamot.sh wrote nothing")
    return french


def lines_of(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").splitlines()


def report(out: Path, sentences: Path, french: Path, accept: bool) -> int:
    """Step 4: only what moved. This is the part that replaces reading a volume."""
    src, fr = lines_of(sentences), lines_of(french)
    if len(src) != len(fr):
        sys.exit(f"{len(src)} sentences but {len(fr)} translations — not aligned")

    current = {s: f for s, f in zip(src, fr) if s.strip()}
    (out / "current.tsv").write_text(
        "".join(f"{s}\t{f}\n" for s, f in current.items()), encoding="utf-8"
    )

    baseline_path = out / "baseline.tsv"
    if not baseline_path.is_file():
        print(f"\n[4/4] no baseline yet — {len(current)} bubbles captured.")
        print(f"      review _bench-en/{out.name}/current.tsv, then --accept")
        if accept:
            shutil.copy(out / "current.tsv", baseline_path)
            print("      baseline blessed")
        return 0

    baseline = {}
    for line in lines_of(baseline_path):
        if "\t" in line:
            key, value = line.split("\t", 1)
            baseline[key] = value

    changed = [(s, baseline[s], f) for s, f in current.items()
               if s in baseline and baseline[s] != f]
    added = [s for s in current if s not in baseline]
    gone = [s for s in baseline if s not in current]

    print(f"\n[4/4] {len(current)} bubbles: {len(changed)} changed, "
          f"{len(added)} new, {len(gone)} gone")
    for source, before, after in changed:
        print(f"\n  src   {source}")
        print(f"  was   {before}")
        print(f"  now   {after}")
    if added:
        print(f"\n  new bubbles (the pipeline now emits these): {len(added)}")
        for s in added[:10]:
            print(f"    {s} -> {current[s]}")
    if gone:
        print(f"\n  bubbles no longer emitted — check none was real dialogue: {len(gone)}")
        for s in gone[:10]:
            print(f"    {s}")

    if accept:
        shutil.copy(out / "current.tsv", baseline_path)
        print("\n  baseline updated")
    elif changed or added or gone:
        print("\n  --accept to make this the new baseline")
    return len(changed)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", nargs="?", help="CBZ, page directory, or a captured name")
    parser.add_argument("--pages", type=int, help="stop after N pages on the first capture")
    parser.add_argument("--reocr", action="store_true", help="redo step 1")
    parser.add_argument("--accept", action="store_true", help="bless the current output")
    parser.add_argument("--list", action="store_true", help="what is already captured")
    args = parser.parse_args()

    global WSL_REPO
    WSL_REPO = to_wsl(REPO)

    if args.list or not args.source:
        if not BENCH.is_dir():
            print("nothing captured yet")
            return
        for d in sorted(p for p in BENCH.iterdir() if p.is_dir()):
            pages = len(list(d.glob("*.boxes.json")))
            blessed = "baseline" if (d / "baseline.tsv").is_file() else "no baseline"
            print(f"  {d.name:40s} {pages:4d} pages  {blessed}")
        return

    source = Path(args.source)
    if source.exists():
        # Spaces out of the capture name as well as quoted in the commands:
        # this name is typed by hand on every replay.
        out = BENCH / source.stem.replace(" ", "-")
        ocr(source, out, args.pages, args.reocr)
    else:
        # A name rather than a path: replay what is already captured. This is
        # the fast path, and the one used while iterating on Kotlin.
        out = BENCH / args.source
        if not out.is_dir():
            sys.exit(f"no capture called {args.source} — run --list")
        print(f"[1/4] OCR: reusing {len(list(out.glob('*.boxes.json')))} captured pages")

    sentences = replay(out)
    french = translate(sentences, out)
    report(out, sentences, french, args.accept)


if __name__ == "__main__":
    main()
