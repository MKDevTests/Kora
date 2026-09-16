-- ---------------------------------------------------------------------------
-- Cards, lists and density (v1.8.23)
-- ---------------------------------------------------------------------------
-- Unread badge style and corner, grid/list for the library series tab,
-- the compact density switch and the launcher icon. Every default is the
-- behaviour before this version, so nothing changes on upgrade.

ALTER TABLE AppSettings
    ADD COLUMN unread_badge_style TEXT DEFAULT 'COUNT' NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN unread_badge_at_start BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN series_list_layout TEXT DEFAULT 'GRID' NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN compact_ui BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE AppSettings
    ADD COLUMN app_icon TEXT DEFAULT 'DEFAULT' NOT NULL;
