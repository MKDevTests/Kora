-- ---------------------------------------------------------------------------
-- Local similarity index for "Similar series" (v1.3.x)
-- ---------------------------------------------------------------------------
-- One row per series holding only what scoring needs: its authors, genres,
-- tags, aggregated book tags and publisher. Built by paging the library once
-- (Komga's series list already carries all of it), then kept fresh from the
-- Komga change events.
--
-- The SUMMARIES are deliberately never stored: they are the bulk of the payload
-- and useless to the scorer, and a library here holds several thousand series.
--
-- Terms live as one compact JSON object rather than a row per term: a few
-- thousand small rows load in milliseconds, where the normalised form would be
-- tens of thousands. The inverted index is rebuilt in memory on use.
--
-- Similarity itself is NOT precomputed. Scoring weights are meant to be tuned,
-- and a stored score matrix would be invalidated by every tweak.

CREATE TABLE IF NOT EXISTS SeriesSimilarityIndex (
    series_id  TEXT PRIMARY KEY,
    library_id TEXT NOT NULL,
    title_sort TEXT NOT NULL DEFAULT '',
    terms      TEXT NOT NULL DEFAULT '{}',
    updated_at TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_series_similarity_library
    ON SeriesSimilarityIndex (library_id);

-- Build state per library: drives "is the index usable / stale" without
-- counting rows, and lets a rebuild be offered rather than forced.
CREATE TABLE IF NOT EXISTS SeriesSimilarityIndexState (
    library_id   TEXT PRIMARY KEY,
    built_at     TEXT NOT NULL DEFAULT '',
    series_count INTEGER NOT NULL DEFAULT 0
);
