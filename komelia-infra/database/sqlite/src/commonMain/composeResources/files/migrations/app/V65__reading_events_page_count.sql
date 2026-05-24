-- ---------------------------------------------------------------------------
-- Reading stats: page count per completion (v1.0.10)
-- ---------------------------------------------------------------------------
-- Records the book's page count at the moment of COMPLETED so we can sum
-- "pages read" stats (lifetime / last 7 / last 30 days) without re-hitting
-- Komga for every event.
--
-- NULLable: rows inserted before this migration have no recorded page
-- count, and the very rare case where the book metadata is unavailable
-- at completion time (offline reader missing media) also stores null.
-- SUM() naturally treats NULL as 0, so old + missing rows just don't
-- contribute to pages-read totals (with no zero-padding gymnastics).

ALTER TABLE reading_events
    ADD COLUMN page_count INTEGER;
