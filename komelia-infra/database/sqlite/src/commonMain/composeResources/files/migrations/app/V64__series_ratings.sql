-- Personal star ratings per series (1..5). Local-only — Komga has no
-- user-rating field on its model, so this lives in Kora's SQLite and
-- ships in the backup JSON. One rating per series, replaced on re-rate
-- (PRIMARY KEY on series_id).

CREATE TABLE IF NOT EXISTS series_ratings (
    series_id  TEXT    NOT NULL PRIMARY KEY,
    stars      INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    rated_at   INTEGER NOT NULL
);

-- Used by the optional "Tes top-rated" Home shelf which orders by stars
-- desc, falling back to recency.
CREATE INDEX IF NOT EXISTS idx_series_ratings_stars_time
    ON series_ratings (stars DESC, rated_at DESC);
