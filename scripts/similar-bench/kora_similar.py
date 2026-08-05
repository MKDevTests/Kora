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

# Credits that name nobody — mirrors isJunkAuthorName() in SeriesTermsExtractor.kt.
JUNK_AUTHOR_NAMES = {"n/a", "na", "unknown", "inconnu", "anonyme", "anonymous", "various", "divers", "?", "-", "--"}


def is_junk_author_name(name: str) -> bool:
    """A credit literally named "a" sits on 140 series here — a strong shared
    term naming nobody, which also stole the per-author cap from the real author.
    """
    trimmed = name.strip()
    return len(trimmed) <= 1 or fold_term(trimmed) in JUNK_AUTHOR_NAMES


# Diacritics this library actually contains (French, romanised Japanese macrons,
# a few Slavic names). An explicit table, NOT unicodedata: common Kotlin has no
# normaliser, so the app folds with this exact table and the bench must match it
# character for character.
_ACCENTED = "àáâãäåāăçćčđďèéêëēĕėęěìíîïĩīĭįñńňòóôõöøōŏőùúûüũūŭůýÿŷšśşžźżřŕţťļłğ"
_PLAIN = "aaaaaaaacccddeeeeeeeeeiiiiiiiinnnooooooooouuuuuuuuyyyssszzzrrttllg"
_FOLD = {a: p for a, p in zip(_ACCENTED, _PLAIN)}
_FOLD.update({"œ": "oe", "æ": "ae", "ß": "ss"})


def fold_term(value: str) -> str:
    """Mirrors foldTerm() in SeriesTerms.kt — the inverted index matches on this.

    The manga library spells the same author 126 ways by case alone and 35 more
    by accent; each spelling was scoring as a different person.
    """
    lower = value.lower()
    if lower.isascii():
        return lower
    return "".join(_FOLD.get(c, c) for c in lower)


@dataclass(frozen=True)
class Feature:
    family: str
    value: str
    role: str | None = None

    @property
    def key(self) -> str:
        return f"{self.family}:{fold_term(self.value)}"


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
    # One series credits "Kentaro Miura" AND "Kentarô MIURA": the same key twice,
    # counting that author twice in the score. First spelling wins.
    seen: set[str] = set()
    return [f for f in out if not (f.key in seen or seen.add(f.key))]


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
        if is_junk_author_name(name):
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
    # Settled at the bench; 0.6 was tried and rejected (it dropped "Planètes"
    # from 1st to 18th for Vinland Saga, behind series sharing generic tags).
    author: float = 1.0
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
    # Floor on a profile series' vector length, as a fraction of the library
    # average. Keeps a nearly-untagged series from dictating the whole profile.
    min_source_norm_ratio: float = 0.5
    # How much a profile series own length counts against it when explaining a
    # suggestion. 1.0 = plain cosine, which buries every well-tagged series.
    attribution_norm_exponent: float = 0.5

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


@dataclass
class TasteWeights:
    """Mirrors TasteWeights.kt — how much each kind of evidence says."""

    read: float = 1.0
    in_progress: float = 0.6
    favorite: float = 3.0
    stars: dict[int, float] = field(default_factory=lambda: {5: 3.0, 4: 2.0, 3: 1.0, 2: -1.0, 1: -2.0})
    # "Not interested": judged without reading, weaker than a rating, far from neutral.
    dismissed: float = -1.5


def taste_affinities(evidence: list[dict], weights: TasteWeights | None = None) -> dict[str, float]:
    """Mirrors tasteAffinities() in TasteProfile.kt.

    An unrated series is NOT a zero (most series are never rated, and treating
    that as 0 stars would drag the profile down), and a rating counts even when
    the series is unfinished.
    """
    weights = weights or TasteWeights()
    out: dict[str, float] = {}
    for item in evidence:
        stars = item.get("stars")
        if stars is not None:
            affinity = weights.stars.get(stars, 0.0)
        elif item.get("isFavorite"):
            affinity = weights.favorite
        elif item.get("dismissed"):
            affinity = weights.dismissed
        elif item.get("read"):
            affinity = weights.read
        elif item.get("inProgress"):
            affinity = weights.in_progress
        else:
            affinity = 0.0
        if affinity != 0.0:
            out[item["seriesId"]] = affinity
    return out


def idf(total_series: int, document_frequency: int) -> float:
    """Smoothed rarity weight — the single biggest quality lever in the feature."""
    if total_series <= 0 or document_frequency <= 0:
        return 0.0
    return math.log(1.0 + total_series / document_frequency)


# --- SimilarityEngine.kt ----------------------------------------------------


@dataclass
class SourceAttribution:
    series_id: str
    # Share of the suggestion's positive score this source accounts for, 0..1.
    share: float
    # The terms actually shared with that source, strongest first.
    reasons: list[Feature]


@dataclass
class SimilarSeries:
    series_id: str
    score: float
    reasons: list[Feature]
    # recommend() only: the liked series this pick was extrapolated from.
    because_of: list[SourceAttribution] = field(default_factory=list)


# Enough for the UI to group by the first and fall back to the next.
MAX_ATTRIBUTIONS = 3

# Below this a series is too thin to explain another one.
MIN_SOURCE_TERMS = 3


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
        positives = [n for n in self.norms.values() if n > 0.0]
        self.average_norm = sum(positives) / len(positives) if positives else 0.0
        self.min_source_norm = self.average_norm * self.weights.min_source_norm_ratio

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

    def _source_norm(self, series_id: str) -> float:
        """Own vector length, floored at a fraction of the library average.

        A source's terms enter the profile divided by its own length, so a
        series carrying ONE term puts its entire affinity on that term. 5% of
        the manga library carries a single term, and one of them made
        'Publisher: Kurokawa' the headline reason of nine suggestions out of ten.
        """
        norm = self.norms.get(series_id, 0.0)
        if norm <= 0.0:
            return 0.0
        return max(norm, self.min_source_norm)

    def recommend(
        self,
        affinities: dict[str, float],
        limit: int = 20,
        exclude: set[str] | None = None,
    ) -> list[SimilarSeries]:
        """Series fitting a taste profile — mirrors SimilarityEngine.recommend().

        Negative affinities (a 1-2 star series) push their terms down, so the
        signal lands per TERM rather than per genre: a genre carried by many
        liked series survives, a tag specific to the disliked ones goes negative.
        """
        exclude = exclude or set()
        profile: dict[str, float] = {}
        for series_id, affinity in affinities.items():
            if affinity == 0.0:
                continue
            features = self.terms_by_series.get(series_id)
            norm = self._source_norm(series_id)
            if features is None or norm <= 0.0:
                continue
            for feature in features:
                weight = self.term_weight.get(feature.key, 0.0)
                if weight <= 0.0:
                    continue
                # Divided by the series' own length, or a series with 200 tags
                # would drown fifty others.
                profile[feature.key] = profile.get(feature.key, 0.0) + affinity * weight / norm

        profile_norm = math.sqrt(sum(w * w for w in profile.values()))
        if profile_norm <= 0.0:
            return []

        candidates: set[str] = set()
        for key, weight in profile.items():
            if weight <= 0.0:
                continue
            postings = self.series_by_term.get(key)
            if not postings or len(postings) >= self.total:
                continue
            candidates.update(c for c in postings if c not in exclude)

        ranked: list[SimilarSeries] = []
        for candidate in candidates:
            features = self.terms_by_series.get(candidate)
            norm = self.norms.get(candidate, 0.0)
            if features is None or norm <= 0.0:
                continue
            dot = sum(profile.get(f.key, 0.0) * self.term_weight.get(f.key, 0.0) for f in features)
            if dot <= 0.0:
                continue
            reasons = sorted(
                (f for f in features if profile.get(f.key, 0.0) > 0.0),
                key=lambda f: -(profile.get(f.key, 0.0) * self.term_weight.get(f.key, 0.0)),
            )
            ranked.append(SimilarSeries(candidate, dot / (profile_norm * norm), reasons))
        ranked.sort(key=lambda s: (-s.score, s.series_id))
        kept = self._cap_per_author(ranked, limit)
        for suggestion in kept:
            suggestion.because_of = self._attribute(suggestion.series_id, affinities)
        return kept

    def _attribute(self, candidate_id: str, affinities: dict[str, float]) -> list[SourceAttribution]:
        """Which liked series pulled a candidate in — mirrors _attribute() in Kotlin.

        A candidate's score is a sum over the profile and the profile is a sum
        over the liked series, so it splits back per source exactly and the
        shares are that split. Constant denominators cancel out in the ratio.
        """
        features = self.terms_by_series.get(candidate_id)
        if features is None:
            return []
        by_key = {f.key: f for f in features}
        scored: list[tuple[SourceAttribution, float]] = []
        total = 0.0
        for source_id, affinity in affinities.items():
            if affinity <= 0.0 or source_id == candidate_id:
                continue
            source_features = self.terms_by_series.get(source_id)
            # A series with two tags and no author explains nothing.
            if source_features is None or len(source_features) < MIN_SOURCE_TERMS:
                continue
            norm = self._source_norm(source_id)
            if norm <= 0.0:
                continue
            overlap = 0.0
            shared: list[Feature] = []
            for feature in source_features:
                own = by_key.get(feature.key)
                weight = self.term_weight.get(feature.key, 0.0)
                if own is None or weight <= 0.0:
                    continue
                overlap += weight * weight
                shared.append(own)
            # The imprint is not a taste: sharing only a publisher is the one
            # overlap that must never be presented as a reason.
            if overlap <= 0.0 or all(f.family == FAMILY_PUBLISHER for f in shared):
                continue
            # Not the plain cosine denominator: dividing by the full length
            # makes a source need shared terms proportional to the SQUARE ROOT
            # of its own size to compete, so richly tagged series never explain
            # anything. 0.5 measured on the manga library.
            contribution = affinity * overlap / norm ** self.weights.attribution_norm_exponent
            total += contribution
            shared.sort(key=lambda f: -self.term_weight.get(f.key, 0.0))
            scored.append((SourceAttribution(source_id, 0.0, shared), contribution))
        if total <= 0.0:
            return []
        scored.sort(key=lambda entry: (-entry[1], entry[0].series_id))
        return [
            SourceAttribution(attribution.series_id, contribution / total, attribution.reasons)
            for attribution, contribution in scored[:MAX_ATTRIBUTIONS]
        ]

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
