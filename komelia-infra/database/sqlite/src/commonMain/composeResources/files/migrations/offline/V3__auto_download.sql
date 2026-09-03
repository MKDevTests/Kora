-- The planner: which series the app may fetch ahead, and how far.
--
-- Bounded by construction. The measured install has at least 78 series in
-- progress; the defaults here take five of them, four volumes deep, which is
-- roughly 1.2 GB against the 4 GB cap V2 introduced. Raising either number is
-- one dropdown, but the app never widens its own scope.

-- Off. Nothing downloads by itself until the user says so.
ALTER TABLE SETTINGS ADD COLUMN auto_download_enabled BOOLEAN NOT NULL DEFAULT 0;

-- How many series the planner may follow, and how many unread volumes it keeps
-- ready in each. The advance is global: a per-library advance was considered
-- and dropped as a setting nobody would ever tune.
ALTER TABLE SETTINGS ADD COLUMN auto_download_max_series INTEGER NOT NULL DEFAULT 5;
ALTER TABLE SETTINGS ADD COLUMN auto_download_books_ahead INTEGER NOT NULL DEFAULT 4;

-- JSON arrays of ids, same shape the app settings use for favourites.
-- An empty library list means every library, which is what a user who has
-- never opened this screen means by saying nothing.
ALTER TABLE SETTINGS ADD COLUMN auto_download_library_ids TEXT NOT NULL DEFAULT '[]';

-- Pinned series come first and ignore the recently-read ordering entirely;
-- excluded series are never taken, however recently they were read. Together
-- they are the per-series bound on top of the "series in progress" rule.
ALTER TABLE SETTINGS ADD COLUMN auto_download_pinned_series_ids TEXT NOT NULL DEFAULT '[]';
ALTER TABLE SETTINGS ADD COLUMN auto_download_excluded_series_ids TEXT NOT NULL DEFAULT '[]';
