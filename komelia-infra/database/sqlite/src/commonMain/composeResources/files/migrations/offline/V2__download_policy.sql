-- Download policy: the brakes that make an automatic download safe to build.
--
-- Measured on the real install before writing this: 78 series in progress at
-- the very least (the local keep-reading cache is capped at 20 per library and
-- three of six libraries sit exactly at 20), against 64 MB and a single book
-- downloaded so far. Two books ahead of 78 series is over 15 GB. Every column
-- below exists so that number can never be reached by accident.

-- 0 disables the constraint rather than a separate flag: the user asked for
-- these to stay optional, so the app warns instead of refusing.
ALTER TABLE SETTINGS ADD COLUMN download_wifi_only BOOLEAN NOT NULL DEFAULT 0;
ALTER TABLE SETTINGS ADD COLUMN download_while_charging_only BOOLEAN NOT NULL DEFAULT 0;

-- Megabytes, not bytes: the setting is a slider in GB and an INTEGER holds it
-- without the rounding questions a byte count invites.
ALTER TABLE SETTINGS ADD COLUMN download_storage_limit_mb INTEGER NOT NULL DEFAULT 4096;

-- 0 means never clean by age. The cap-pressure purge is separate and always
-- on: a full cap has to be resolved somehow, and refusing every new download
-- forever is worse than dropping the oldest book already read.
ALTER TABLE SETTINGS ADD COLUMN cleanup_read_after_days INTEGER NOT NULL DEFAULT 0;

-- Off by default, and this is the one that protects the user's own work: a
-- book they downloaded on purpose is not the cleaner's to delete.
ALTER TABLE SETTINGS ADD COLUMN cleanup_include_manual BOOLEAN NOT NULL DEFAULT 0;

-- MANUAL for every row that already exists, which is exactly right: everything
-- downloaded before this migration was downloaded by hand.
ALTER TABLE BOOK ADD COLUMN download_origin TEXT NOT NULL DEFAULT 'MANUAL';
