-- ---------------------------------------------------------------------------
-- One-shot migration flag: local Ignore List -> server kora:hidden (v1.1.x)
-- ---------------------------------------------------------------------------
-- True once the admin's local Ignore List has been pushed to the server as
-- kora:hidden tags (the one-time launch prompt). Prevents re-running.

ALTER TABLE AppSettings ADD COLUMN ignore_list_migrated_to_hidden BOOLEAN DEFAULT 0 NOT NULL;
