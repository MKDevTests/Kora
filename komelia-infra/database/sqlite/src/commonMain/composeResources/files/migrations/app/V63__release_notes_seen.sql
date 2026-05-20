-- "What's new" modal: track which release the user has acknowledged the
-- notes for, so we only show the modal once per upgrade. Default NULL
-- means "never seen" — existing users upgrading to v1.0.3 will see the
-- v1.0.3 notes on first launch; the field then stores "1.0.3" and the
-- modal stays hidden until v1.0.4.

ALTER TABLE AppSettings
    ADD COLUMN last_seen_release_notes_version TEXT;
