-- Stats settings (1.8.28): libraries kept out of every reading statistic,
-- and the library of each logged event so the local figures can honour it.
-- Existing events start NULL and are filled in lazily from Komga the first
-- time a library is excluded. On the books-baseline sentinel row the column
-- holds the exclusion set the stored count was taken with instead.
ALTER TABLE AppSettings
    ADD COLUMN stats_excluded_library_ids TEXT DEFAULT '[]' NOT NULL;
ALTER TABLE reading_events
    ADD COLUMN library_id TEXT;
