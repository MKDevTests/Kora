-- Local-only series links (never synced to Komga).
-- "Other versions": same work in another language/edition — symmetric group.
CREATE TABLE IF NOT EXISTS SeriesVersions (
    series_id TEXT PRIMARY KEY NOT NULL,
    group_id  TEXT NOT NULL
);

-- "Related series": typed, bidirectional edges (sequel/prequel/spin-off/related).
-- Both directions are stored (e.g. A->B SEQUEL and B->A PREQUEL).
CREATE TABLE IF NOT EXISTS SeriesRelations (
    from_series_id TEXT NOT NULL,
    to_series_id   TEXT NOT NULL,
    relation       TEXT NOT NULL,
    PRIMARY KEY (from_series_id, to_series_id)
);
