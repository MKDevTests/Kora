-- ---------------------------------------------------------------------------
-- Named, reusable series searches (v1.8.x)
-- ---------------------------------------------------------------------------
-- The filter panel could express a precise search — reading status, language,
-- completion, genres, authors — and then had no way to keep it. Rebuilding the
-- same combination from eight dropdowns is what made those filters go unused.
--
-- filter_json holds a SeriesFilterDto, the exact payload LibrarySeriesFilters
-- already stores. Reusing it means a saved search decodes through the same
-- tolerant path: every field is defaulted, so a search saved today still loads
-- after new criteria are added.
--
-- Scoped per library. A saved search names genres, publishers and authors that
-- only exist in one library, so offering the manga searches while browsing the
-- comics would mostly offer searches that match nothing. The "all libraries"
-- view has no id of its own and stores its searches under ALL_LIBRARIES_KEY.
--
-- position, not name, decides the order: the user reorders without renaming,
-- and two libraries may legitimately hold a search with the same name.
CREATE TABLE IF NOT EXISTS SavedSeriesFilters (
    id          TEXT    NOT NULL PRIMARY KEY,
    library_id  TEXT    NOT NULL,
    name        TEXT    NOT NULL,
    position    INTEGER NOT NULL,
    filter_json TEXT    NOT NULL
);

-- Every read is "the searches of the library I am in", ordered.
CREATE INDEX IF NOT EXISTS idx_saved_series_filters_library
    ON SavedSeriesFilters (library_id, position);
