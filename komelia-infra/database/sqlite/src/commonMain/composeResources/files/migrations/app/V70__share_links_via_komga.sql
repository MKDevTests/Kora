-- ---------------------------------------------------------------------------
-- Series links: opt-in sharing via Komga (v1.0.20)
-- ---------------------------------------------------------------------------
-- When enabled, typed series relations are read from (everyone) and written to
-- (admin only) the shared Komga series `links` field, on top of the private
-- local links. Off by default → purely local, current behaviour.

ALTER TABLE AppSettings
    ADD COLUMN share_links_via_komga BOOLEAN DEFAULT 0 NOT NULL;
