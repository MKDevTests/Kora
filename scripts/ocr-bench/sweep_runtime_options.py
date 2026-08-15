"""Measures the two ONNX knobs that cannot cost recall.

useArena and interOpNumThreads change how the runtime allocates and how it
parallelises across graph nodes. Neither touches what the models see, so the
box count is a check that nothing moved rather than a trade-off column: if it
changes here, the measurement is wrong.

Three repeats and the median, because the first run of any configuration pays
for warm-up and a single timing of a 15-page volume is mostly that.
"""
import statistics
import sys
from pathlib import Path

import cv2

sys.path.insert(0, str(Path("scripts/ocr-bench")))
from run_ocr import build_engine, MODELS  # noqa: E402

PAGES = sorted(Path("C:/Users/mathi/AppData/Local/Temp/claude/ramen167").glob("*.webp"))
IMAGES = [cv2.imread(str(p)) for p in PAGES]


def run(**overrides):
    from rapidocr_onnxruntime import RapidOCR
    import rapidocr_onnxruntime.rapid_ocr_api as api

    keys = MODELS / "ppocrv6_keys.txt"
    original = api.read_yaml

    def patched(path):
        config = original(path)
        config["Rec"]["keys_path"] = str(keys)
        return config

    api.read_yaml = patched
    try:
        engine = RapidOCR(
            det_model_path=str(MODELS / "PP-OCRv6_small_det_infer.onnx"),
            rec_model_path=str(MODELS / "PP-OCRv6_small_rec_infer.onnx"),
            cls_model_path=str(MODELS / "ch_ppocr_mobile_v2.0_cls_infer.onnx"),
            text_score=0.6,
            use_angle_cls=False,
            width_height_ratio=8,
            **overrides,
        )
    finally:
        api.read_yaml = original

    totals, boxes = [], 0
    for image in IMAGES:
        result, elapse = engine(image)
        totals.append(sum(e for e in elapse if e))
        boxes += len(result or [])
    return sum(totals), boxes


CASES = {
    "reference": {},
    "arena on": {"det_use_arena": True, "rec_use_arena": True},
    "interOp 2": {"det_intra_op_num_threads": 4, "rec_intra_op_num_threads": 4,
                  "det_inter_op_num_threads": 2, "rec_inter_op_num_threads": 2},
    "intraOp 8": {"det_intra_op_num_threads": 8, "rec_intra_op_num_threads": 8},
}

if __name__ == "__main__":
    print(f"{'cas':>12} {'volume median':>15} {'boites':>7}")
    for name, overrides in CASES.items():
        runs = [run(**overrides) for _ in range(3)]
        print(f"{name:>12} {statistics.median(r[0] for r in runs):>14.2f}s {runs[0][1]:>7}")
