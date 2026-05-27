-- ---------------------------------------------------------------------------
-- Image reader: optional minimal UI while reading (v1.0.11)
-- ---------------------------------------------------------------------------
-- When enabled, the image reader's "hidden controls" state is replaced by
-- a slim bottom strip showing only [prev book] [progress slider] [next
-- book] plus the top bar. Tap reveals full controls; tap again returns
-- to the minimal strip. Off by default — legacy behavior is preserved
-- for users who haven't opted in.

ALTER TABLE ImageReaderSettings
    ADD COLUMN keep_progress_bar_visible_while_reading BOOLEAN DEFAULT 0 NOT NULL;
