#!/usr/bin/env python3
"""Tune the "Similar series" weights against the real library, off the tablet.

Why a bench: the scoring weights (author vs genre vs tag, rarity, the per-author
cap) cannot be judged from the code — only by looking at what they propose for
series you know. Doing that through build-install-look cycles costs ten minutes
a guess; here it costs a second, and the server is only hit once.

Typical session:

    # 1. pull the library into a local file (one pass, ~N/100 requests)
    python bench.py fetch --library BD

    # 2. look at what the current weights propose for series you know
    python bench.py show --library BD --series "Blacksad" --series "Sillage"

    # 3. try something else — no refetch, the index is on disk
    python bench.py show --library BD --series "Blacksad" --genre 1.4 --tag 0.3

    # 4. compare several settings side by side on the same series
    python bench.py sweep --library BD --series "Blacksad" --param genre=0.6,1.0,1.4

Credentials come from KOMGA_URL / KOMGA_USER / KOMGA_PASSWORD (or the flags).
Only the standard, read-only Komga API is used.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from kora_similar import SimilarityEngine, Weights, is_marker_tag, terms_of_series

HERE = Path(__file__).resolve().parent
PAGE_SIZE = 100


# --- Komga -----------------------------------------------------------------


class Komga:
    def __init__(self, url: str, user: str, password: str):
        self.url = url.rstrip("/")
        token = base64.b64encode(f"{user}:{password}".encode()).decode()
        self.headers = {"Authorization": f"Basic {token}", "Accept": "application/json"}

    def get(self, path: str, params: dict | None = None) -> dict:
        query = f"?{urllib.parse.urlencode(params)}" if params else ""
        request = urllib.request.Request(f"{self.url}{path}{query}", headers=self.headers)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read().decode())
        except urllib.error.HTTPError as error:
            body = error.read().decode(errors="replace")[:300]
            raise SystemExit(f"HTTP {error.code} on {path}: {body}") from None
        except urllib.error.URLError as error:
            raise SystemExit(f"Cannot reach {self.url}: {error.reason}") from None

    def libraries(self) -> list[dict]:
        return self.get("/api/v1/libraries")

    def series_page(self, library_id: str, page: int) -> dict:
        return self.get("/api/v1/series", {
            "library_id": library_id,
            "page": page,
            "size": PAGE_SIZE,
            # Same stable order as the app's index builder: page offsets are
            # meaningless without it.
            "sort": "metadata.titleSort,asc",
        })


def index_path(library: str) -> Path:
    slug = re.sub(r"[^a-z0-9]+", "-", library.lower()).strip("-")
    return HERE / f"index_{slug}.json"


def cmd_fetch(args) -> None:
    komga = Komga(args.url, args.user, args.password)
    libraries = komga.libraries()
    match = next((lib for lib in libraries if lib["name"].lower() == args.library.lower()), None)
    if match is None:
        names = ", ".join(lib["name"] for lib in libraries)
        raise SystemExit(f"No library named {args.library!r}. Available: {names}")

    entries = []
    page = 0
    while True:
        payload = komga.series_page(match["id"], page)
        for series in payload["content"]:
            # Same rule as the app: keep the terms, drop the series. The two
            # summaries are the bulk of the payload and nothing scores them.
            entries.append({
                "seriesId": series["id"],
                "title": (series.get("metadata") or {}).get("title") or series.get("name", ""),
                "terms": terms_of_series(series),
            })
        print(f"  page {page + 1}/{payload['totalPages']} — {len(entries)} series", file=sys.stderr)
        if payload["last"] or page >= payload["totalPages"] - 1:
            break
        page += 1

    target = index_path(args.library)
    target.write_text(json.dumps({"library": args.library, "series": entries}, ensure_ascii=False), encoding="utf-8")
    print(f"{len(entries)} series -> {target}")


# --- Scoring ---------------------------------------------------------------


def load_index(library: str) -> list[dict]:
    path = index_path(library)
    if not path.exists():
        raise SystemExit(f"No local index for {library!r}. Run: python bench.py fetch --library {library!r}")
    entries = json.loads(path.read_text(encoding="utf-8"))["series"]
    # Defensive: an index fetched before marker tags were excluded still holds
    # them. Strip on load so an old cache scores like the app instead of
    # silently mistuning the weights.
    for entry in entries:
        entry["terms"]["t"] = [t for t in entry["terms"]["t"] if not is_marker_tag(t)]
    return entries


def weights_from(args) -> Weights:
    weights = Weights()
    for name in ("author", "genre", "tag", "book_tag", "publisher"):
        value = getattr(args, name, None)
        if value is not None:
            setattr(weights, name, value)
    if getattr(args, "max_per_author", None) is not None:
        weights.max_per_author = args.max_per_author
    return weights


def resolve(entries: list[dict], needle: str) -> dict:
    """Series whose title contains [needle], or whose id matches exactly."""
    exact = [e for e in entries if e["seriesId"] == needle]
    if exact:
        return exact[0]
    matches = [e for e in entries if needle.lower() in (e.get("title") or "").lower()]
    if not matches:
        raise SystemExit(f"No series matching {needle!r} in the index")
    if len(matches) > 1:
        titles = ", ".join(m["title"] for m in matches[:8])
        print(f"! {needle!r} matches {len(matches)} series, using the first — {titles}", file=sys.stderr)
    return matches[0]


def titles_of(entries: list[dict]) -> dict[str, str]:
    return {e["seriesId"]: e.get("title") or e["seriesId"] for e in entries}


def describe(feature) -> str:
    return f"{feature.family}:{feature.value}"


def cmd_show(args) -> None:
    entries = load_index(args.library)
    engine = SimilarityEngine(entries, weights_from(args))
    titles = titles_of(entries)
    for needle in args.series:
        source = resolve(entries, needle)
        print(f"\n=== {titles[source['seriesId']]} ===")
        results = engine.similar_to(source["seriesId"], limit=args.limit)
        if not results:
            print("  (nothing — this series carries no scorable term)")
        for rank, result in enumerate(results, start=1):
            why = ", ".join(describe(f) for f in result.reasons[:5])
            print(f"  {rank:2}. {result.score:.3f}  {titles[result.series_id]:<45} {why}")


def cmd_sweep(args) -> None:
    entries = load_index(args.library)
    titles = titles_of(entries)
    name, _, raw_values = args.param.partition("=")
    values = [float(v) for v in raw_values.split(",") if v]
    if not values:
        raise SystemExit("Use --param genre=0.6,1.0,1.4")

    for needle in args.series:
        source = resolve(entries, needle)
        print(f"\n=== {titles[source['seriesId']]} — sweeping {name} ===")
        columns = []
        for value in values:
            weights = weights_from(args)
            setattr(weights, name, value)
            engine = SimilarityEngine(entries, weights)
            columns.append([titles[r.series_id] for r in engine.similar_to(source["seriesId"], limit=args.limit)])
        header = "".join(f"{name}={value:<28}" for value in values)
        print(f"  {header}")
        for rank in range(args.limit):
            row = "".join(f"{(column[rank] if rank < len(column) else ''):<34}" for column in columns)
            print(f"  {row}")


# --- Fidelity fixture ------------------------------------------------------


def cmd_emit_expected(args) -> None:
    """Rescores fixture.json and rewrites fixture_expected.json.

    The Kotlin SimilarityEngineFixtureTest asserts the same numbers, so this is
    what keeps the two implementations honest. Run it after ANY scoring change,
    then run the Kotlin test.
    """
    fixture = json.loads((HERE / "fixture.json").read_text(encoding="utf-8"))
    engine = SimilarityEngine(fixture["series"], Weights())
    expected = {
        query: [
            {
                "seriesId": result.series_id,
                "score": round(result.score, 12),
                "reasons": [describe(f) for f in result.reasons],
            }
            for result in engine.similar_to(query, limit=fixture.get("limit", 5))
        ]
        for query in fixture["queries"]
    }
    out = HERE / "fixture_expected.json"
    out.write_text(json.dumps(expected, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"wrote {out}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    def add_weight_flags(target):
        target.add_argument("--author", type=float)
        target.add_argument("--genre", type=float)
        target.add_argument("--tag", type=float)
        target.add_argument("--book-tag", type=float, dest="book_tag")
        target.add_argument("--publisher", type=float)
        target.add_argument("--max-per-author", type=int, dest="max_per_author")
        target.add_argument("--limit", type=int, default=10)

    fetch = sub.add_parser("fetch", help="download a library's terms into a local index")
    fetch.add_argument("--library", required=True)
    fetch.add_argument("--url", default=os.environ.get("KOMGA_URL", ""))
    fetch.add_argument("--user", default=os.environ.get("KOMGA_USER", ""))
    fetch.add_argument("--password", default=os.environ.get("KOMGA_PASSWORD", ""))
    fetch.set_defaults(func=cmd_fetch)

    show = sub.add_parser("show", help="suggestions for one or more series")
    show.add_argument("--library", required=True)
    show.add_argument("--series", action="append", required=True)
    add_weight_flags(show)
    show.set_defaults(func=cmd_show)

    sweep = sub.add_parser("sweep", help="same series, several values of one weight")
    sweep.add_argument("--library", required=True)
    sweep.add_argument("--series", action="append", required=True)
    sweep.add_argument("--param", required=True, help="e.g. genre=0.6,1.0,1.4")
    add_weight_flags(sweep)
    sweep.set_defaults(func=cmd_sweep)

    emit = sub.add_parser("emit-expected", help="regenerate the cross-check fixture")
    emit.set_defaults(func=cmd_emit_expected)

    args = parser.parse_args()
    if args.command == "fetch" and not (args.url and args.user and args.password):
        raise SystemExit("Set KOMGA_URL / KOMGA_USER / KOMGA_PASSWORD, or pass --url --user --password")
    args.func(args)


if __name__ == "__main__":
    main()
