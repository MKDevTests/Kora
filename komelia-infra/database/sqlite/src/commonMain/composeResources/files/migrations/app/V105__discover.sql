-- ---------------------------------------------------------------------------
-- Discover: suggestions for series that are NOT in the library
-- ---------------------------------------------------------------------------
-- Everything else in the app suggests from what Komga already holds. This asks
-- MangaUpdates instead, whose per-series recommendations are voted by readers
-- rather than computed — which sidesteps the sparse-metadata problem entirely:
-- a third of the catalogue carries no genre, tag, publisher or author, and no
-- amount of scoring can make something similar to nothing.
--
-- Off by default. It sends identifiers of series the user owns to a third
-- party, and taken together those are a reading profile. Only the taste
-- profile's seed is ever sent, never the catalogue.

ALTER TABLE AppSettings ADD COLUMN discover_enabled INTEGER NOT NULL DEFAULT 0;

-- Which MangaUpdates series a local series is. Resolved once and kept: the
-- link is usually already in the Komga metadata, and where it is not, a title
-- search costs a request that must not be repeated on every pass.
--
-- external_id NULL with a resolved_at set means "looked for, not found" — a
-- distinct state from "never looked", so the scanner can skip it for a while
-- instead of paying for the same miss every week.
CREATE TABLE IF NOT EXISTS DiscoverSourceMap (
    series_id    TEXT PRIMARY KEY,
    source       TEXT NOT NULL DEFAULT 'mangaupdates',
    external_id  TEXT,
    resolved_at  TEXT NOT NULL DEFAULT ''
);

-- What the last pass produced. The tab reads this table and nothing else: a
-- pass costs around a minute of network and must never happen while the user
-- is looking at the screen.
--
-- title, image_url, year and rating all come back inside the recommendation
-- payload, so displaying a suggestion needs no further request. because_of is
-- a JSON array of the local series ids that produced it, for the "because you
-- read X" line.
CREATE TABLE IF NOT EXISTS DiscoverSuggestions (
    external_id  TEXT PRIMARY KEY,
    source       TEXT NOT NULL DEFAULT 'mangaupdates',
    title        TEXT NOT NULL DEFAULT '',
    url          TEXT NOT NULL DEFAULT '',
    image_url    TEXT NOT NULL DEFAULT '',
    year         TEXT NOT NULL DEFAULT '',
    rating       REAL NOT NULL DEFAULT 0,
    score        REAL NOT NULL DEFAULT 0,
    because_of   TEXT NOT NULL DEFAULT '[]',
    updated_at   TEXT NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_discover_score ON DiscoverSuggestions (score DESC);

-- Suggestions the user has waved away. Kept apart from the results table so a
-- refresh can wipe the results without losing the dismissals.
CREATE TABLE IF NOT EXISTS DiscoverDismissed (
    external_id  TEXT PRIMARY KEY,
    dismissed_at TEXT NOT NULL DEFAULT ''
);

-- One row, id 1. When the last pass ran, and what it said if it failed.
CREATE TABLE IF NOT EXISTS DiscoverScanState (
    id           INTEGER PRIMARY KEY CHECK (id = 1),
    last_run_at  TEXT NOT NULL DEFAULT '',
    last_error   TEXT NOT NULL DEFAULT ''
);
