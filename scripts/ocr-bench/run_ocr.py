#!/usr/bin/env python3
"""Runs the reader's OCR over a whole volume, off the tablet.

Same PP-OCRv6 ONNX files the app downloads and the same settings it applies, so
what comes out is what the tablet would have produced. Recognition is the Python
port of the library the Android side uses, not a reimplementation.

    python scripts/ocr-bench/run_ocr.py volume.cbz
    python scripts/ocr-bench/run_ocr.py pages/ --fast --out out/

Writes one <page>.boxes.json per page: the raw detected lines, before any
merging. Merging is deliberately NOT done here — it runs through the real Kotlin
in run_merge.py, so the bench cannot drift from what ships.
"""
from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

import cv2
import numpy as np

MODELS = Path.home() / "Downloads" / "rapidocr-v6-pack"
PAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif"}

# Mirrors OcrService.android.kt. Every value here has a counterpart there; if one
# side changes the other has to, or the bench stops predicting the tablet.
TEXT_SCORE = 0.6
WIDTH_HEIGHT_RATIO = 8


def build_engine(fast: bool, rec_size: str = "small", limit_side_len: int = 0,
                 limit_type: str = ""):
    from rapidocr_onnxruntime import RapidOCR

    det = MODELS / ("PP-OCRv6_tiny_det_infer.onnx" if fast else "PP-OCRv6_small_det_infer.onnx")
    # small is what ships. medium is the next size up and shares the same
    # character dictionary, so it drops straight in — worth measuring because
    # the recogniser reads 'u' as 'li' often enough to wreck a bubble on its
    # own: soulmate came out solilmate, without came out witholt, and no
    # translator recovers from that.
    rec = MODELS / f"PP-OCRv6_{rec_size}_rec_infer.onnx"
    cls = MODELS / "ch_ppocr_mobile_v2.0_cls_infer.onnx"
    keys = MODELS / "ppocrv6_keys.txt"

    missing = [p.name for p in (det, rec, cls, keys) if not p.exists()]
    if missing:
        sys.exit(
            f"Missing model files in {MODELS}: {', '.join(missing)}\n"
            "Unpack RapidOcrModels-v6.1.zip there, or point MODELS at another copy."
        )

    # The recogniser takes its dictionary either from metadata inside the model
    # or from Rec.keys_path in the package's own config.yaml. Our v6 model
    # carries no metadata, and no keyword argument reaches Rec.keys_path, so the
    # config is patched on the way in. Everything else stays the library's real
    # code path — a reimplementation here is exactly how a bench starts lying.
    import rapidocr_onnxruntime.rapid_ocr_api as api

    original_read_yaml = api.read_yaml

    def read_yaml_with_keys(path):
        config = original_read_yaml(path)
        config["Rec"]["keys_path"] = str(keys)
        return config

    api.read_yaml = read_yaml_with_keys
    try:
        return RapidOCR(
            det_model_path=str(det),
            rec_model_path=str(rec),
            cls_model_path=str(cls),
            text_score=TEXT_SCORE,
            # Off for Latin on the tablet too: the classifier is trained on
            # Chinese and flips short Latin crops end over end ("I'M" -> "W,I").
            use_angle_cls=False,
            width_height_ratio=WIDTH_HEIGHT_RATIO,
            # Detection resizes the page so its long side fits this, then pads
            # to a multiple of 32. Cost follows the pixel count, so it is
            # quadratic in the scale -- and the risk is entirely on the other
            # side: below some point the small balloons stop being found.
            # Zero leaves the library's own default alone.
            # limit_type decides which side the number applies to, and it is
            # the whole experiment: the library default is "min", which only
            # ever scales a page UP when its short side is below the limit.
            # On a 1400x1993 page every value at or below 1400 therefore does
            # nothing at all -- a sweep over those measured the same run four
            # times. "max" caps the long side, which is what actually removes
            # pixels from detection.
            **({"det_limit_side_len": limit_side_len} if limit_side_len else {}),
            **({"det_limit_type": limit_type} if limit_type else {}),
        )
    finally:
        api.read_yaml = original_read_yaml


def sample_background(image: np.ndarray, left: int, top: int, right: int, bottom: int) -> str:
    """Median colour of a ring just outside the box — the same points the reader
    samples to pick the colour it paints the translation panel."""
    height, width = image.shape[:2]
    pad = int(min(right - left, bottom - top) * 0.4)
    pad = max(3, min(pad, 24))

    samples = []

    def take(x: int, y: int) -> None:
        if 0 <= x < width and 0 <= y < height:
            samples.append(image[y, x])

    for i in range(9):
        x = int(left + (right - left) * i / 8)
        take(x, top - pad)
        take(x, bottom + pad)
    for i in range(5):
        y = int(top + (bottom - top) * i / 4)
        take(left - pad, y)
        take(right + pad, y)

    if not samples:
        return "#000000"
    arr = np.array(samples, dtype=np.float32)
    luma = 0.114 * arr[:, 0] + 0.587 * arr[:, 1] + 0.299 * arr[:, 2]  # BGR
    b, g, r = arr[int(np.argsort(luma)[len(luma) // 2])]
    return f"#{int(r):02x}{int(g):02x}{int(b):02x}"


def scan_page(engine, image: np.ndarray) -> list[dict]:
    result, _ = engine(image)
    boxes = []
    for entry in result or []:
        points, text, score = entry[0], entry[1], entry[2]
        xs = [p[0] for p in points]
        ys = [p[1] for p in points]
        left, top = int(min(xs)), int(min(ys))
        right, bottom = int(max(xs)), int(max(ys))
        boxes.append(
            {
                "rect": [left, top, right, bottom],
                "text": text,
                "confidence": round(float(score), 4),
                "background": sample_background(image, left, top, right, bottom),
            }
        )
    return boxes


def iter_pages(source: Path):
    """Yields (name, BGR image) for a cbz/zip, a directory, or a single image."""
    if source.is_dir():
        for path in sorted(source.iterdir()):
            if path.suffix.lower() in PAGE_SUFFIXES:
                image = cv2.imread(str(path))
                if image is not None:
                    yield path.stem, image
        return

    if source.suffix.lower() in PAGE_SUFFIXES:
        image = cv2.imread(str(source))
        if image is None:
            sys.exit(f"Could not read {source}")
        yield source.stem, image
        return

    with zipfile.ZipFile(source) as archive:
        names = sorted(n for n in archive.namelist() if Path(n).suffix.lower() in PAGE_SUFFIXES)
        for name in names:
            raw = np.frombuffer(archive.read(name), dtype=np.uint8)
            image = cv2.imdecode(raw, cv2.IMREAD_COLOR)
            if image is not None:
                yield Path(name).stem, image


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="cbz/zip, a directory of pages, or one image")
    parser.add_argument("--out", type=Path, default=Path("scripts/ocr-bench/out"))
    parser.add_argument("--fast", action="store_true", help="tiny detector, as the Fast switch does")
    parser.add_argument("--rec", choices=("small", "medium"), default="small",
                        help="recogniser size; small is what ships")
    parser.add_argument("--limit", type=int, default=0, help="stop after N pages")
    parser.add_argument("--limit-side-len", type=int, default=0,
                        help="detection input side limit; 0 keeps the default")
    parser.add_argument("--limit-type", choices=("min", "max"), default="",
                        help="which side --limit-side-len applies to")
    args = parser.parse_args()

    if not args.source.exists():
        sys.exit(f"No such file or directory: {args.source}")

    engine = build_engine(args.fast, args.rec, args.limit_side_len, args.limit_type)
    args.out.mkdir(parents=True, exist_ok=True)

    count = 0
    for name, image in iter_pages(args.source):
        boxes = scan_page(engine, image)
        height, width = image.shape[:2]
        payload = {
            "page": name,
            "width": width,
            "height": height,
            "detector": "tiny" if args.fast else "small",
            "recogniser": args.rec,
            "boxes": boxes,
        }
        (args.out / f"{name}.boxes.json").write_text(
            json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8"
        )
        count += 1
        print(f"{name}: {width}x{height}, {len(boxes)} lines")
        if args.limit and count >= args.limit:
            break

    print(f"\n{count} pages -> {args.out}")


if __name__ == "__main__":
    main()
