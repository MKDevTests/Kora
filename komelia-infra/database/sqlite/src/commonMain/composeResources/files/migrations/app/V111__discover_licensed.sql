-- ---------------------------------------------------------------------------
-- Discover: what is readable without Japanese
-- ---------------------------------------------------------------------------
-- The source knows two kinds of publisher and no more: measured 2026-09-10 over
-- 58 publisher entries on 36 suggestions, every one was `Original` or
-- `English`. So the only availability it can answer is the English edition --
-- the `licensed` flag and the publishers of that type -- and that is what is
-- stored here. A French edition is NOT derivable from any free, keyless
-- source tried (MangaUpdates, MangaDex, Open Library, Nautiljon, manga-news,
-- MangaCollec): the card offers a Nautiljon search instead of pretending.
ALTER TABLE DiscoverSuggestions ADD COLUMN licensed INTEGER NOT NULL DEFAULT 0;
ALTER TABLE DiscoverSuggestions ADD COLUMN english_publishers TEXT NOT NULL DEFAULT '[]';

-- Off by default, on purpose. With the French edition unknowable, hiding
-- "no English edition" would also hide 7 Seeds, Angel Densetsu, Shin Angyo
-- Onshi, Superior and Mx0 -- all published in French.
ALTER TABLE AppSettings ADD COLUMN discover_hide_unlicensed INTEGER NOT NULL DEFAULT 0;

-- Existing rows would all read as "no English edition" until the next weekly
-- pass. Clearing the throttle makes the next opening refill them; kept cards
-- and the source map are untouched.
UPDATE DiscoverScanState SET last_run_at = '';
