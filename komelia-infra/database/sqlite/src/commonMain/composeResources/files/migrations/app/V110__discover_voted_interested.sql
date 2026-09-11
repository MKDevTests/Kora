-- ---------------------------------------------------------------------------
-- Discover: the voted tier, and an "interested" shelf
-- ---------------------------------------------------------------------------
-- `voted` records which of the source's two recommendation lists produced a
-- suggestion. V109 fixed the scoring but the tier lived only in memory: the
-- table is read back ordered by score alone, so a tag-derived candidate that
-- several seeds happened to name still landed among the voted ones. Measured
-- 2026-09-10 on the real profile: Cos-Chu sat 8th and Hyakka Ryouran 11th and
-- 12th, above Kekkaishi and Shin Angyo Onshi.
--
-- Existing rows default to 1. Everything V109 left behind was written by the
-- corrected pass, and the wrong tier on a handful of rows is cheaper than
-- deleting a table the user has already looked at.
ALTER TABLE DiscoverSuggestions ADD COLUMN voted INTEGER NOT NULL DEFAULT 1;

-- `interested` is the opposite gesture to a dismissal: something to come back
-- to. Unlike a dismissal it needs the whole card, so it is a flag here rather
-- than an id in a side table -- and the pass now deletes only the rows that do
-- NOT carry it, so a kept suggestion survives a refresh that no longer
-- recommends it.
ALTER TABLE DiscoverSuggestions ADD COLUMN interested INTEGER NOT NULL DEFAULT 0;
