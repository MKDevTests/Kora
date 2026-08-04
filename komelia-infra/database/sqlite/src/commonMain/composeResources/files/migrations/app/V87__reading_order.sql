-- ---------------------------------------------------------------------------
-- Reading order graph (v1.3.x)
-- ---------------------------------------------------------------------------
-- Which series a franchise is read FROM, and the last graph computed for it.
--
-- The flag is the user's decision and is never invalidated. The cached graph is
-- derived data, dropped on any link change; it exists because naming the boxes
-- costs one Komga lookup per series (no id-list query), not because the graph
-- is expensive to compute.

CREATE TABLE IF NOT EXISTS SeriesReadingOrderOriginal (
    series_id TEXT PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS SeriesReadingOrderCache (
    original_series_id TEXT PRIMARY KEY,
    graph              TEXT NOT NULL DEFAULT '{}',
    built_at           TEXT NOT NULL DEFAULT ''
);
