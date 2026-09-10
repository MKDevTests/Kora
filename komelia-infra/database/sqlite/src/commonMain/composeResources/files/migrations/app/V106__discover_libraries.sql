-- ---------------------------------------------------------------------------
-- Discover: which libraries the taste seed is built from
-- ---------------------------------------------------------------------------
-- Measured on the real catalogue: 37 of the 43 series that seeded the first
-- pass were franco-belgian comics, which MangaUpdates does not carry. They
-- produced nothing and took 37 of the 40 seed slots, so the suggestions could
-- only ever come from the six manga that were left.
--
-- Empty means "every library", which is what an existing install gets: the
-- feature has to keep working for anyone who never opens this setting.
--
-- Only the SEED is restricted. What counts as "already owned", and is therefore
-- never suggested, stays the whole catalogue -- a manga sitting in an unticked
-- library is still a manga the user has.

ALTER TABLE AppSettings ADD COLUMN discover_library_ids TEXT NOT NULL DEFAULT '';
