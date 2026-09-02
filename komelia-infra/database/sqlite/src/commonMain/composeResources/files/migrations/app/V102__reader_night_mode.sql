-- ---------------------------------------------------------------------------
-- Reader night mode (warm tint)
-- ---------------------------------------------------------------------------
-- A blue-light filter for the image reader only. It tints the page, not the
-- app chrome, which is already dark.
--
-- The tint is applied as a colour matrix at draw time rather than as a step in
-- the image processing pipeline: a pipeline step would cost CPU per page and
-- invalidate the image cache on every move of the slider, and the setting has
-- to feel immediate.
--
-- start/end are minutes since midnight. The UI moves them in steps of 15, but
-- the column stays a plain minute count so a finer step later needs no
-- migration. end < start is legal and means the range crosses midnight, which
-- is the normal case for a night schedule.

ALTER TABLE ImageReaderSettings ADD COLUMN night_mode_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ImageReaderSettings ADD COLUMN night_mode_intensity REAL NOT NULL DEFAULT 0.5;
ALTER TABLE ImageReaderSettings ADD COLUMN night_mode_schedule_enabled INTEGER NOT NULL DEFAULT 0;
ALTER TABLE ImageReaderSettings ADD COLUMN night_mode_start_minute INTEGER NOT NULL DEFAULT 1320;
ALTER TABLE ImageReaderSettings ADD COLUMN night_mode_end_minute INTEGER NOT NULL DEFAULT 420;
