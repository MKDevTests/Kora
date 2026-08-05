-- ---------------------------------------------------------------------------
-- Library tab counts (v1.4.x)
-- ---------------------------------------------------------------------------
-- How many collections, read lists and genres a library holds.
--
-- These three numbers decide whether the Genres / Collections / Read lists
-- chips are shown, and they used to be fetched on every entry into a library:
-- measured on the real server, 839 ms for collections, 4.5 s for the genres
-- and 6.9 s for the read lists — so the chips appeared seven seconds after the
-- screen they belong to. Cached in memory only, which is empty on every app
-- start, i.e. exactly when the wait is most visible.
--
-- Persisting them means the chips are painted from the last known state
-- immediately, and the server refresh happens behind them.

CREATE TABLE IF NOT EXISTS LibraryCounts (
    library_id  TEXT PRIMARY KEY,
    collections INTEGER NOT NULL DEFAULT 0,
    read_lists  INTEGER NOT NULL DEFAULT 0,
    genres      INTEGER NOT NULL DEFAULT 0,
    updated_at  TEXT NOT NULL DEFAULT ''
);
