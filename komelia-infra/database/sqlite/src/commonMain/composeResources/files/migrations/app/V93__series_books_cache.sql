-- ---------------------------------------------------------------------------
-- First page of a series' books, remembered (v1.4.x)
-- ---------------------------------------------------------------------------
-- Opening a series shows nothing where the volumes go until the server answers
-- — one to three seconds against this server, on every visit, for a list that
-- rarely changes between two of them.
--
-- Only the FIRST page, and only for the default sort and no filter: that is
-- what an entry into the series shows. Any other page or ordering is a
-- deliberate request from the user, who can wait for the real answer rather
-- than be shown something else.

CREATE TABLE IF NOT EXISTS SeriesBooksCache (
    series_id  TEXT PRIMARY KEY,
    books_json TEXT NOT NULL DEFAULT '[]',
    page_size  INTEGER NOT NULL DEFAULT 20,
    total_pages INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL DEFAULT ''
);
