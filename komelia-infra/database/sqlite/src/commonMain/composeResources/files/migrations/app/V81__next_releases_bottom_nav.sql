-- ---------------------------------------------------------------------------
-- "Upcoming releases" bottom-nav shortcut (v1.1.x)
-- ---------------------------------------------------------------------------
-- Optional dedicated bottom-nav button for the new cross-library upcoming
-- releases screen, mirroring the existing stats-in-bottom-nav toggle. Off by
-- default; the screen stays reachable from its Home card either way.

ALTER TABLE AppSettings ADD COLUMN next_releases_in_bottom_nav BOOLEAN DEFAULT 0 NOT NULL;
