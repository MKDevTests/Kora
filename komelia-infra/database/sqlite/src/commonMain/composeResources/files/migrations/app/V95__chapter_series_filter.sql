-- ---------------------------------------------------------------------------
-- Chapter series filter, three-way (v1.5.x)
-- ---------------------------------------------------------------------------
-- Replaces the boolean added in V94: hiding chapter series is one of three
-- answers, and "show me only the chapter series" turned out to be the one
-- worth having — it is how you check a chapter release against its volumes.
--
--   ANY            leave every series alone (default)
--   HIDE_CHAPTERS  drop titles ending in "(Chap)"
--   ONLY_CHAPTERS  keep only those
--
-- V94's column is left in place and carried over rather than dropped: SQLite
-- rewrites the whole table to drop a column, and an install that ticked the
-- boolean should keep meaning what it meant.

ALTER TABLE AppSettings
    ADD COLUMN chapter_series_filter TEXT DEFAULT 'ANY' NOT NULL;

UPDATE AppSettings
SET chapter_series_filter = 'HIDE_CHAPTERS'
WHERE hide_chapter_series = 1;
