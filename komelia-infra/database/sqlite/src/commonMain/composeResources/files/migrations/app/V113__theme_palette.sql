-- ---------------------------------------------------------------------------
-- Theme = mode x palette x pure black (v1.8.22)
-- ---------------------------------------------------------------------------
-- Five hand-written colour schemes and a fourteen-entry accent menu become
-- a light/dark/system mode, a seed colour the whole scheme is generated
-- from, and a pure-black switch. The old app_theme values stay parseable
-- (the enum keeps them) so nothing here rewrites app_theme; DARKER was the
-- only theme on #000000, so it turns the new switch on. accent_color is left
-- in place, unread: the seed replaces it.

ALTER TABLE AppSettings
    ADD COLUMN palette_seed TEXT DEFAULT NULL;
ALTER TABLE AppSettings
    ADD COLUMN pure_black BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN dark_at_night BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN dark_night_start INTEGER DEFAULT 1260 NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN dark_night_end INTEGER DEFAULT 420 NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN accent_follows_cover BOOLEAN DEFAULT 1 NOT NULL;

-- Text size (multiplier on the system scale) and the face of the titles,
-- so the serif is a choice rather than the only option.
ALTER TABLE AppSettings
    ADD COLUMN text_scale REAL DEFAULT 1.0 NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN title_font TEXT DEFAULT 'SERIF' NOT NULL;

UPDATE AppSettings
SET pure_black = 1
WHERE app_theme = 'DARKER';
