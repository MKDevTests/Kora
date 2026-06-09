-- ---------------------------------------------------------------------------
-- Genre tab per-(library, genre) overrides (v1.1.0)
-- ---------------------------------------------------------------------------
-- Cover and display-name overrides chosen by the user via long-press on a genre
-- tile. JSON object columns keyed by "<libraryId|all>:<genreSlug>". Default to
-- empty objects, so the tab falls back to the auto cover + curated label.

ALTER TABLE AppSettings ADD COLUMN genre_cover_overrides TEXT DEFAULT '{}' NOT NULL;
ALTER TABLE AppSettings ADD COLUMN genre_label_overrides TEXT DEFAULT '{}' NOT NULL;
