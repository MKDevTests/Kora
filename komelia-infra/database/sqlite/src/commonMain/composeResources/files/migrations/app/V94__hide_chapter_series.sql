-- ---------------------------------------------------------------------------
-- Hide chapter series (v1.5.x)
-- ---------------------------------------------------------------------------
-- When on, series whose title ends with "(Chap)" are kept out of every list:
-- the library grid, the home shelves, search and upcoming releases.
--
-- One setting rather than one per library: the home shelves and search span
-- every library at once, so a per-library switch would have had nothing to read
-- when drawing them.
--
-- Off by default. A filter that hides series nobody asked it to hide is a
-- filter nobody can diagnose.

ALTER TABLE AppSettings
    ADD COLUMN hide_chapter_series INTEGER DEFAULT 0 NOT NULL;
