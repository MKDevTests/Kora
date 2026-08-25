-- ---------------------------------------------------------------------------
-- Genre count stored with the similarity index state (v1.8.x)
-- ---------------------------------------------------------------------------
-- The library chips need one number: how many distinct kora:genre:* slugs the
-- library holds. It was produced by reading EVERY row of SeriesSimilarityIndex
-- for that library and JSON-decoding each series' term blob — thousands of
-- decodes to end up with a number under thirty. Measured on the manga library
-- during a library switch: 775 ms of local work, on top of the server calls it
-- runs alongside.
--
-- The count is a pure function of the index, and the index builder already has
-- every term in memory when it writes the rows. So it is computed there once
-- and stored here, and reading it becomes a single indexed row.
--
-- NULL, not 0, means "not known yet": a library indexed by an older build has
-- no stored count, and a library that genuinely holds no genre is a real 0.
-- Telling them apart is what keeps an empty library from paying the slow path
-- on every single switch.
ALTER TABLE SeriesSimilarityIndexState ADD COLUMN genre_count INTEGER;

-- Every read of the index is scoped to one library — the suggestions tab, the
-- genre count, the staleness sweep — and library_id carried no index, so each
-- of them scanned the whole table. The primary key is series_id, which none of
-- those queries filter on.
CREATE INDEX IF NOT EXISTS idx_series_similarity_index_library
    ON SeriesSimilarityIndex (library_id);
