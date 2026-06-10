-- ---------------------------------------------------------------------------
-- Experimental local Ignore List (v1.1.0)
-- ---------------------------------------------------------------------------
-- A purely local, per-server list of series the user has ignored. Ignored
-- series and their books are filtered out of every list client-side; nothing
-- is sent to the server. ignore_list_enabled is the master toggle (off = no
-- filtering, action hidden). ignored_series_ids is a JSON string array.

ALTER TABLE AppSettings ADD COLUMN ignore_list_enabled BOOLEAN DEFAULT 0 NOT NULL;
ALTER TABLE AppSettings ADD COLUMN ignored_series_ids TEXT DEFAULT '[]' NOT NULL;
