-- ---------------------------------------------------------------------------
-- Per-series translation glossary
-- ---------------------------------------------------------------------------
-- Terms a series spells its own way, kept out of the translator's hands.
--
-- Two problems share this table. Names the reader flattens: bubbles are
-- lettered in caps, so the text is sentence-cased before translating and a name
-- in the middle of a sentence loses its capital with the rest — MERYL STRIFE
-- goes out as "Meryl strife" and comes back as "Meryl discorde". And terms with
-- a settled wording: The Force is "la Force", Wayne Manor is "le Manoir Wayne",
-- which no small model knows and none of them render the same way twice.
--
-- series_id is empty for entries that apply everywhere. A series-scoped entry
-- with the same source term wins over the global one, which is what lets a name
-- mean one thing in one universe and something else in another.
--
-- Terms are stored one per row rather than as a JSON blob: unlike the
-- similarity index this is edited by hand, a few dozen rows per series at most,
-- and a row is what an editing screen wants to add and delete.

CREATE TABLE IF NOT EXISTS TranslationGlossary (
    series_id   TEXT NOT NULL DEFAULT '',
    source_term TEXT NOT NULL,
    target_term TEXT NOT NULL DEFAULT '',
    created_at  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (series_id, source_term)
);

CREATE INDEX IF NOT EXISTS idx_translation_glossary_series
    ON TranslationGlossary (series_id);
