-- ---------------------------------------------------------------------------
-- Local per-user Favorites (v1.1.x)
-- ---------------------------------------------------------------------------
-- A purely local, per-server list of series the user marked as favorites. They
-- are gathered in a virtual cross-library "Favorites" section; nothing is sent
-- to the server. JSON string array.

ALTER TABLE AppSettings ADD COLUMN favorite_series_ids TEXT DEFAULT '[]' NOT NULL;
