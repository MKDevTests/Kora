-- ---------------------------------------------------------------------------
-- Local per-user "Planned" series (v1.1.x)
-- ---------------------------------------------------------------------------
-- A purely local, per-server list of series the user wants to read but isn't
-- actively following yet. Independent from Favorites (a series can be both).
-- Gathered in a virtual cross-library "Planned" section; nothing is sent to
-- the server. JSON string array.

ALTER TABLE AppSettings ADD COLUMN planned_series_ids TEXT DEFAULT '[]' NOT NULL;
