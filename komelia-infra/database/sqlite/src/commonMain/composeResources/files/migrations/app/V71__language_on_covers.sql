-- ---------------------------------------------------------------------------
-- Optional language pill (FR / EN) on series covers (v1.0.21)
-- ---------------------------------------------------------------------------
-- A tiny text badge of the series' language on Home / Library covers, with a
-- size scale and a top-left / bottom-left position. Off by default.

ALTER TABLE AppSettings ADD COLUMN show_language_on_covers BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE AppSettings ADD COLUMN language_badge_scale REAL DEFAULT 1.0 NOT NULL;
ALTER TABLE AppSettings ADD COLUMN language_badge_at_bottom BOOLEAN DEFAULT 0 NOT NULL;
