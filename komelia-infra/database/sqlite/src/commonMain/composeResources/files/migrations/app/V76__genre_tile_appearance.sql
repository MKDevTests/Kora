-- ---------------------------------------------------------------------------
-- Experimental Genre tab: per-tile appearance (v1.1.0)
-- ---------------------------------------------------------------------------
-- When genre_tiles_custom_appearance is on, the genre tiles use their own size
-- and text settings instead of inheriting the global card appearance. Only size
-- and text (title below/overlay + show/hide series count) are customizable.

ALTER TABLE AppSettings ADD COLUMN genre_tiles_custom_appearance BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE AppSettings ADD COLUMN genre_tile_width INTEGER DEFAULT 170 NOT NULL;
ALTER TABLE AppSettings ADD COLUMN genre_tile_text_below BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE AppSettings ADD COLUMN genre_tile_show_count BOOLEAN DEFAULT 1 NOT NULL;
