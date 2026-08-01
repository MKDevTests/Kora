-- ---------------------------------------------------------------------------
-- Display options (v1.3.x)
-- ---------------------------------------------------------------------------
-- 1. Tap-to-zoom in the continuous reader. The toggle only existed for the
--    paged reader, so webtoon reading was stuck with double-tap zoom on.
--    Lives with the other reader settings, and defaults to on = the previous
--    behaviour.
--
-- 2. Author roles shown across the app. Komga credits up to eight roles
--    (writer, penciller, inker, colorist, letterer, cover, editor,
--    translator) and the book page prints one row per role. The filter is
--    OFF by default, so nothing changes until the user asks for it; the
--    HIDDEN set is stored rather than the visible one, so a role Komga adds
--    later shows up instead of silently disappearing.

ALTER TABLE ImageReaderSettings
    ADD COLUMN continuous_reader_tap_to_zoom BOOLEAN DEFAULT 1 NOT NULL;

ALTER TABLE AppSettings
    ADD COLUMN author_roles_filter_enabled BOOLEAN DEFAULT 0 NOT NULL;

ALTER TABLE AppSettings
    ADD COLUMN hidden_author_roles TEXT DEFAULT '[]' NOT NULL;
