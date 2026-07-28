-- ---------------------------------------------------------------------------
-- Per-library scoping for the personal lists (Favorites / Planned) (v1.2.x)
-- ---------------------------------------------------------------------------
-- Both lists store bare series ids with no library information, so filtering
-- them by library used to mean resolving every entry over the network first
-- (one getOneSeries per id) before anything could be filtered out.
--
-- series_library_ids caches seriesId -> libraryId locally, filled in as entries
-- are resolved or added. Filtering then happens BEFORE any network call: the
-- per-library view only resolves that library's entries. It is a cache, not a
-- source of truth — a missing id simply gets resolved once and recorded.
-- JSON object, {"<seriesId>":"<libraryId>"}.
--
-- excluded_library_ids lists libraries kept OUT of the "All" view of both
-- lists (e.g. a "Divers" library you only want to browse on its own tab).
-- They stay reachable by selecting that library explicitly. Shared by
-- Favorites and Planned on purpose. JSON string array.

ALTER TABLE AppSettings ADD COLUMN series_library_ids TEXT DEFAULT '{}' NOT NULL;
ALTER TABLE AppSettings ADD COLUMN excluded_library_ids TEXT DEFAULT '[]' NOT NULL;
