"""Faithful Python port of Kora's similarity scoring.

The Kotlin engine lives in
`komelia-domain/core/src/commonMain/kotlin/snd/komelia/similarity/`. This module
mirrors it term for term so the weights can be tuned in seconds here instead of
in build-install-look cycles on the tablet.

The port is not taken on trust: `fixture.json` + `fixture_expected.json` are
scored by BOTH sides, and `SimilarityEngineFixtureTest` fails the Gradle build if
they ever diverge. Change one side, run both.

Kotlin references, kept in the same order as there:
  SeriesTerms.kt / SimilarityWeights.kt / SimilarityEngine.kt / SeriesTermsExtractor.kt
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

# --- SeriesTerms.kt ---------------------------------------------------------

GENRE_PREFIX = "kora:genre:"
TAG_PREFIX = "kora:tag:"


def is_marker_tag(tag: str) -> bool:
    """App-state tags that must never be scored — see SeriesTermsExtractor.

    `nextrelease:<vol>-<date>` is one per series, so its rarity weight is the
    highest of any term: two series sharing a release date would outrank two
    sharing an author. `kora:hidden` means "an admin hid this", not a taste.
    """
    tag = tag.lower()
    return tag.startswith("nextrelease:") or (
        tag.startswith("kora:") and not tag.startswith(GENRE_PREFIX) and not tag.startswith(TAG_PREFIX)
    )

# TermFamily.prefix — namespaced so an author named "Action" can never collide
# with the Action genre.
FAMILY_AUTHOR = "a"
FAMILY_GENRE = "g"
FAMILY_TAG = "t"
FAMILY_BOOK_TAG = "bt"
FAMILY_PUBLISHER = "p"

# SeriesTermsExtractor.ROLE_RANK — most significant first.
ROLE_RANK = ["writer", "artist", "penciller", "inker", "colorist", "letterer", "translator"]


@dataclass(frozen=True)
class Feature:
    family: str
    value: str
    role: str | None = None

    @property
    def key(self) -> str:
        return f"{self.family}:{self.value}"


def features_of(terms: dict) -> list[Feature]:
    """SeriesTerms.features() — ORDER MATTERS: it drives the reasons list."""
    out: list[Feature] = []
    for name, role in (terms.get("a") or {}).items():
        out.append(Feature(FAMILY_AUTHOR, name, role))
    for genre in terms.get("g") or []:
        out.append(Feature(FAMILY_GENRE, genre))
    for tag in terms.get("t") or []:
        out.append(Feature(FAMILY_TAG, tag))
    for tag in terms.get("bt") or []:
        out.append(Feature(FAMILY_BOOK_TAG, tag))
    publisher = terms.get("p")
    if publisher and publisher.strip():
        out.append(Feature(FAMILY_PUBLISHER, publisher))
    return out


def terms_of_series(series: dict) -> dict:
    """KomgaSeries.toSimilarityTerms() — the same split, on the raw Komga JSON."""
    metadata = series.get("metadata") or {}
    books_metadata = series.get("booksMetadata") or {}

    series_tags = [t.strip() for t in (metadata.get("tags") or [])]
    series_tags = [t for t in series_tags if t and not is_marker_tag(t)]

    # dict.fromkeys, not a set: Kotlin's toSet() keeps insertion order, and term
    # order decides the reasons list and the float accumulation order.
    genres = list(dict.fromkeys(
        t[len(GENRE_PREFIX):].lower() for t in series_tags if t.startswith(GENRE_PREFIX)
    ))
    tags = list(dict.fromkeys(t.lower() for t in series_tags if not t.startswith(GENRE_PREFIX)))

    # One entry per author: a name credited under several roles keeps the most
    # significant one.
    by_name: dict[str, list[str]] = {}
    for author in books_metadata.get("authors") or []:
        name = (author.get("name") or "").strip()
        if not name:
            continue
        by_name.setdefault(name, []).append((author.get("role") or "").strip().lower())
    authors = {
        name: min(roles, key=lambda r: ROLE_RANK.index(r) if r in ROLE_RANK else len(ROLE_RANK))
        for name, roles in by_name.items()
    }

    book_tags = list(dict.fromkeys(
        t.strip().lower() for t in (books_metadata.get("tags") or []) if t.strip()
    ))
    publisher = (metadata.get("publisher") or "").strip().lower() or None

    return {"a": authors, "g": genres, "t": tags, "bt": book_tags, "p": publisher}


# --- SimilarityWeights.kt ---------------------------------------------------


@dataclass
class Weights:
    # Settled at the bench: once the per-author cap actually capped, 1.2 and 0.6
    # ranked almost identically, and 0.6 leaves more room for genre/tag matches.
    author: float = 0.6
    genre: float = 1.0
    tag: float = 0.6
    book_tag: float = 0.4
    publisher: float = 0.25
    author_roles: dict[str, float] = field(default_factory=lambda: {
        "writer": 1.0,
        "penciller": 0.9,
        "artist": 0.9,
        "inker": 0.5,
        "colorist": 0.4,
        "letterer": 0.3,
        "translator": 0.2,
    })
    max_per_author: int = 2

    def family_weight(self, feature: Feature) -> float:
        if feature.family == FAMILY_AUTHOR:
            return self.author * self.author_roles.get((feature.role or "").lower(), 1.0)
        if feature.family == FAMILY_GENRE:
            return self.genre
        if feature.family == FAMILY_TAG:
            return self.tag
        if feature.family == FAMILY_BOOK_TAG:
            return self.book_tag
        return self.publisher


def idf(total_series: int, document_frequency: int) -> float:
    """Smoothed rarity weight — the single biggest quality lever in the feature."""
    if total_series <= 0 or document_frequency <= 0:
        return 0.0
    return math.log(1.0 + total_series / document_frequency)


# --- SimilarityEngine.kt ----------------------------------------------------


@dataclass
class SimilarSeries:
    series_id: str
    score: float
    reasons: list[Feature]


class SimilarityEngine:
    """Scores one series against a library. Cosine over rarity-weighted vectors."""

    def __init__(self, series: list[dict], weights: Weights | None = None):
        self.weights = weights or Weights()
        # seriesId -> features, in input order (postings inherit that order).
        self.terms_by_series: dict[str, list[Feature]] = {
            entry["seriesId"]: features_of(entry["terms"]) for entry in series
        }
        self.series_by_term: dict[str, list[str]] = {}
        for series_id, features in self.terms_by_series.items():
            for feature in features:
                self.series_by_term.setdefault(feature.key, []).append(series_id)

        self.total = len(self.terms_by_series)
        self.term_weight: dict[str, float] = {}
        for features in self.terms_by_series.values():
            for feature in features:
                if feature.key not in self.term_weight:
                    df = len(self.series_by_term.get(feature.key, ()))
                    self.term_weight[feature.key] = self.weights.family_weight(feature) * idf(self.total, df)

        self.norms: dict[str, float] = {
            series_id: math.sqrt(sum(self.term_weight.get(f.key, 0.0) ** 2 for f in features))
            for series_id, features in self.terms_by_series.items()
        }

    def similar_to(self, series_id: str, limit: int = 20, exclude: set[str] | None = None) -> list[SimilarSeries]:
        exclude = exclude or set()
        source_features = self.terms_by_series.get(series_id)
        if source_features is None:
            return []
        source_norm = self.norms.get(series_id, 0.0)
        if source_norm <= 0.0:
            return []

        scores: dict[str, float] = {}
        shared: dict[str, list[Feature]] = {}
        for feature in source_features:
            weight = self.term_weight.get(feature.key)
            if weight is None or weight <= 0.0:
                continue
            postings = self.series_by_term.get(feature.key)
            if not postings:
                continue
            # A term carried by nearly everything contributes almost nothing yet
            # costs a full walk of its postings.
            if len(postings) >= self.total:
                continue
            for candidate in postings:
                if candidate == series_id or candidate in exclude:
                    continue
                scores[candidate] = scores.get(candidate, 0.0) + weight * weight
                shared.setdefault(candidate, []).append(feature)

        ranked: list[SimilarSeries] = []
        for candidate, dot in scores.items():
            norm = self.norms.get(candidate, 0.0)
            if norm <= 0.0:
                continue
            reasons = sorted(shared.get(candidate, []), key=lambda f: -self.term_weight.get(f.key, 0.0))
            ranked.append(SimilarSeries(candidate, dot / (source_norm * norm), reasons))
        ranked.sort(key=lambda s: (-s.score, s.series_id))
        return self._cap_per_author(ranked, limit)

    def _cap_per_author(self, ranked: list[SimilarSeries], limit: int) -> list[SimilarSeries]:
        """Cap on the SHARED authors, dropping as soon as one is full.

        Counting every author of the candidate exhausted the cap on names that
        had nothing to do with the match (anthologies credit up to 151 people),
        and requiring ALL of them to be full was equivalent to no cap at all.
        """
        per_author: dict[str, int] = {}
        kept: list[SimilarSeries] = []
        for candidate in ranked:
            shared = [f.value for f in candidate.reasons if f.family == FAMILY_AUTHOR]
            if any(per_author.get(a, 0) >= self.weights.max_per_author for a in shared):
                continue
            for author in shared:
                per_author[author] = per_author.get(author, 0) + 1
            kept.append(candidate)
            if len(kept) >= limit:
                break
        return kept
